package gaia.cu9.ari.gaiaorbit.data;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

import gaia.cu9.ari.gaiaorbit.GaiaSky;
import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.event.IObserver;
import gaia.cu9.ari.gaiaorbit.scenegraph.AbstractPositionEntity;
import gaia.cu9.ari.gaiaorbit.scenegraph.Constellation;
import gaia.cu9.ari.gaiaorbit.scenegraph.SceneGraphNode;
import gaia.cu9.ari.gaiaorbit.scenegraph.octreewrapper.AbstractOctreeWrapper;
import gaia.cu9.ari.gaiaorbit.util.I18n;
import gaia.cu9.ari.gaiaorbit.util.Logger;
import gaia.cu9.ari.gaiaorbit.util.tree.LoadStatus;
import gaia.cu9.ari.gaiaorbit.util.tree.OctreeNode;

/**
 * Contains the infrastructure common to all multifile octree loaders which
 * streams data on-demand.
 * 
 * @author tsagrista
 *
 */
public abstract class StreamingOctreeLoader implements IObserver, ISceneGraphLoader {
    /**
     * Default load queue size in octants
     */
    protected static final int LOAD_QUEUE_MAX_SIZE = 100000;

    public static StreamingOctreeLoader instance;

    /** Current number of stars that are loaded **/
    protected int nLoadedStars = 0;
    /** Max number of stars loaded at once **/
    protected final int maxLoadedStars;

    /** The octant loading queue **/
    protected Queue<OctreeNode> toLoadQueue = null;

    /** Whether loading is paused or not **/
    protected boolean loadingPaused = false;

    /**
     * This queue is sorted ascending by access date, so that we know which
     * element to release if needed (oldest)
     **/
    protected Queue<OctreeNode> toUnloadQueue;

    /** Loaded octant ids, for logging **/
    protected long[] loadedIds;
    protected int loadedObjects;
    protected int maxLoadedIds, idxLoadedIds;

    /** Load status of the different levels of detail **/
    protected LoadStatus[] lodStatus = new LoadStatus[50];

    protected String metadata, particles;

    /** Daemon thread that gets the data loading requests and serves them **/
    protected DaemonLoader daemon;

    public StreamingOctreeLoader() {
        maxLoadedStars = 10000000;

        toLoadQueue = new ArrayBlockingQueue<OctreeNode>(LOAD_QUEUE_MAX_SIZE);
        toUnloadQueue = new ArrayBlockingQueue<OctreeNode>(LOAD_QUEUE_MAX_SIZE);

        maxLoadedIds = 50;
        idxLoadedIds = 0;
        loadedIds = new long[maxLoadedIds];

        EventManager.instance.subscribe(this, Events.DISPOSE, Events.PAUSE_BACKGROUND_LOADING, Events.RESUME_BACKGROUND_LOADING);
    }

    @Override
    public void initialize(String[] files) throws RuntimeException {
        if (files == null || files.length < 2) {
            throw new RuntimeException("Error loading octree files: " + files.length);
        }
        particles = files[0];
        metadata = files[1];
    }

    @Override
    public Array<? extends SceneGraphNode> loadData() throws FileNotFoundException {
        AbstractOctreeWrapper octreeWrapper = loadOctreeData();
        /**
         * INITIALIZE DAEMON LOADER THREAD
         */
        daemon = new DaemonLoader(octreeWrapper, this);
        daemon.setDaemon(true);
        daemon.setName("daemon-octree-loader");
        daemon.setPriority(Thread.MIN_PRIORITY);
        daemon.start();

        /**
         * INITIALIZE TIMER TO FLUSH THE QUEUE AT REGULAR INTERVALS
         */
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                flushLoadQueue();
            }

        }, 1000, 2000);

        // Add octreeWrapper to result list and return
        Array<SceneGraphNode> result = new Array<SceneGraphNode>(1);
        result.add(octreeWrapper);

        Logger.info(this.getClass().getSimpleName(), I18n.bundle.format("notif.catalog.init", octreeWrapper.root.countObjects()));

        return result;
    }

    protected void addLoadedInfo(long id, int nobjects) {
        if (idxLoadedIds >= maxLoadedIds) {
            flushLoadedIds();
        }
        loadedIds[idxLoadedIds++] = id;
        loadedObjects += nobjects;
    }

    protected void flushLoadedIds() {
        if (idxLoadedIds > 0) {
            String str = "[" + loadedIds[0] + ", ..., " + loadedIds[idxLoadedIds - 1] + "]";
            Logger.info(I18n.bundle.format("notif.octantsloaded", loadedObjects, idxLoadedIds, str));

            idxLoadedIds = 0;
            loadedObjects = 0;
        }

    }

    /**
     * Loads the nodes and the octree
     * 
     * @return
     */
    protected abstract AbstractOctreeWrapper loadOctreeData();

    /**
     * Adds the octant to the load queue
     * 
     * @param octant
     */
    public static void queue(OctreeNode octant) {
        if (instance != null) {
            instance.addToQueue(octant);
        }
    }

    /**
     * Moves the octant to the end of the unload queue
     * 
     * @param octant
     */
    public static void touch(OctreeNode octant) {
        if (instance != null) {
            instance.touchOctant(octant);
        }
    }

    public void addToQueue(OctreeNode octant) {
        // Add only if there is room
        if (toLoadQueue.size() < LOAD_QUEUE_MAX_SIZE - 1) {
            toLoadQueue.add(octant);
            octant.setStatus(LoadStatus.QUEUED);
        }
    }

    /** Adds a list of octants to the queue to be loaded **/
    public void addToQueue(OctreeNode... octants) {
        for (OctreeNode octant : octants) {
            addToQueue(octant);
        }

    }

    /** Puts it at the end of the toUnloadQueue **/
    public void touchOctant(OctreeNode octant) {
        if (toUnloadQueue.contains(octant)) {
            toUnloadQueue.remove(octant);
        }
        toUnloadQueue.offer(octant);
    }

    /**
     * Tells the loader to start loading the octants in the queue.
     */
    public void flushLoadQueue() {
        if (!daemon.awake && !toLoadQueue.isEmpty() && !loadingPaused) {
            EventManager.instance.post(Events.BACKGROUND_LOADING_INFO);
            daemon.interrupt();
        }
    }

    /**
     * Loads all the levels of detail until the given one.
     * 
     * @param lod
     *            The level of detail to load.
     * @param octreeWrapper
     *            The octree wrapper.
     * @throws IOException
     */
    public void loadLod(final Integer lod, final AbstractOctreeWrapper octreeWrapper) throws IOException {
        loadOctant(octreeWrapper.root, octreeWrapper, lod);
    }

    /**
     * Loads the data of the given octant and its children down to the given
     * level
     * 
     * @param octant
     *            The octant to load.
     * @param octreeWrapper
     *            The octree wrapper.
     * @param level
     *            The depth to load
     * @throws IOException
     */
    public void loadOctant(final OctreeNode octant, final AbstractOctreeWrapper octreeWrapper, Integer level) throws IOException {
        if (level >= 0) {
            loadOctant(octant, octreeWrapper, false);
            if (octant.children != null) {
                for (OctreeNode child : octant.children) {
                    if (child != null && child.nObjects > 0)
                        loadOctant(child, octreeWrapper, level - 1);
                }
            }
        }
    }

    /**
     * Loads the objects of the given octants
     * 
     * @param octants
     *            The list holding the octants to load.
     * @param octreeWrapper
     *            The octree wrapper.
     * @return The actual number of loaded octants
     * @throws IOException
     */
    public int loadOctants(final Array<OctreeNode> octants, final AbstractOctreeWrapper octreeWrapper) throws IOException {
        int loaded = 0;
        for (OctreeNode octant : octants)
            if (loadOctant(octant, octreeWrapper, true))
                loaded++;
        flushLoadedIds();
        return loaded;
    }

    /**
     * Unloads the given octant
     * 
     * @param octant
     * @param octreeWrapper
     */
    public void unloadOctant(OctreeNode octant, final AbstractOctreeWrapper octreeWrapper) {
        synchronized (octant) {
            Array<AbstractPositionEntity> objects = octant.objects;
            if (objects != null) {
                for (AbstractPositionEntity object : objects) {
                    object.octant = null;
                    octreeWrapper.removeParenthood(object);
                    // Aux info
                    if (GaiaSky.instance != null && GaiaSky.instance.sg != null)
                        GaiaSky.instance.sg.removeNodeAuxiliaryInfo(object);

                    nLoadedStars -= object.getStarCount();

                    // Stop thread in star groups
                    object.dispose();

                }
            }
            objects.clear();
            octant.setStatus(LoadStatus.NOT_LOADED);

        }
    }

    /**
     * Loads the data of the given octant
     * 
     * @param octant
     *            The octant to load.
     * @param octreeWrapper
     *            The octree wrapper.
     * @param fullinit
     *            Whether to fully initialise the objects (on-demand load) or
     *            not (startup)
     * @return True if the octant was loaded, false otherwise
     * @throws IOException
     */
    public abstract boolean loadOctant(final OctreeNode octant, final AbstractOctreeWrapper octreeWrapper, boolean fullinit) throws IOException;

    /**
     * The daemon loader thread.
     * 
     * @author Toni Sagrista
     *
     */
    protected static class DaemonLoader extends Thread {
        public boolean awake;
        public boolean running;

        private StreamingOctreeLoader loader;
        private AbstractOctreeWrapper octreeWrapper;
        private Array<OctreeNode> toLoad;

        public DaemonLoader(AbstractOctreeWrapper aow, StreamingOctreeLoader loader) {
            this.awake = false;
            this.running = true;
            this.loader = loader;
            this.octreeWrapper = aow;
            this.toLoad = new Array<OctreeNode>();
        }

        public void stopExecution() {
            running = false;
        }

        @Override
        public void run() {
            while (running) {
                /** ----------- PROCESS OCTANTS ----------- **/
                while (!instance.toLoadQueue.isEmpty()) {
                    toLoad.clear();
                    while (instance.toLoadQueue.peek() != null) {
                        OctreeNode octant = (OctreeNode) instance.toLoadQueue.poll();
                        toLoad.add(octant);
                    }

                    // Load octants if any
                    if (toLoad.size > 0) {
                        Logger.debug(I18n.bundle.format("notif.loadingoctants", toLoad.size));
                        try {
                            int loaded = loader.loadOctants(toLoad, octreeWrapper);

                            Logger.debug(I18n.bundle.format("notif.loadingoctants.finished", loaded));
                        } catch (Exception e) {
                            Logger.error(I18n.bundle.get("notif.loadingoctants.fail"));
                        }
                    }

                    // Release resources if needed
                    while (loader.nLoadedStars >= loader.maxLoadedStars) {
                        // Get first in queue (unaccessed for the longest time)
                        // and release it
                        OctreeNode octant = loader.toUnloadQueue.poll();
                        if (octant != null && octant.getStatus() == LoadStatus.LOADED) {
                            loader.unloadOctant(octant, octreeWrapper);
                        }
                    }

                    Gdx.app.postRunnable(() -> {
                        // Update constellations :S
                        Constellation.updateConstellations();
                    });

                }

                /** ----------- SLEEP UNTIL INTERRUPTED ----------- **/
                try {
                    awake = false;
                    Thread.sleep(Long.MAX_VALUE - 8);
                } catch (InterruptedException e) {
                    // New data!
                    awake = true;
                }
            }
        }
    }

    @Override
    public void notify(Events event, Object... data) {
        switch (event) {
        case PAUSE_BACKGROUND_LOADING:
            loadingPaused = true;
            Logger.info(this.getClass().getSimpleName(), "Background data loading thread paused");
            break;
        case RESUME_BACKGROUND_LOADING:
            loadingPaused = false;
            Logger.info(this.getClass().getSimpleName(), "Background data loading thread resumed");
            break;
        case DISPOSE:
            if (daemon != null) {
                daemon.stopExecution();
            }
            break;
        default:
            break;
        }

    }

}
