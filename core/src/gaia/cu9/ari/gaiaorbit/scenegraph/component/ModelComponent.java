package gaia.cu9.ari.gaiaorbit.scenegraph.component;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

import gaia.cu9.ari.gaiaorbit.data.AssetBean;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;
import gaia.cu9.ari.gaiaorbit.util.I18n;
import gaia.cu9.ari.gaiaorbit.util.Logger;
import gaia.cu9.ari.gaiaorbit.util.ModelCache;
import gaia.cu9.ari.gaiaorbit.util.Pair;

public class ModelComponent implements Disposable {
    public boolean forceinit = false;
    private static ColorAttribute ambient;
    /**
     * Light never changes
     */
    private Boolean staticLight = false;

    static {
        ambient = new ColorAttribute(ColorAttribute.AmbientLight, (float) GlobalConf.scene.AMBIENT_LIGHT, (float) GlobalConf.scene.AMBIENT_LIGHT, (float) GlobalConf.scene.AMBIENT_LIGHT, 1f);
    }

    public static void toggleAmbientLight(boolean on) {
        if (on) {
            ambient.color.set(.7f, .7f, .7f, 1f);
        } else {
            ambient.color.set((float) GlobalConf.scene.AMBIENT_LIGHT, (float) GlobalConf.scene.AMBIENT_LIGHT, (float) GlobalConf.scene.AMBIENT_LIGHT, 1f);
        }
    }

    /**
     * Sets the ambient light
     * 
     * @param level
     *            Ambient light level between 0 and 1
     */
    public static void setAmbientLight(float level) {
        ambient.color.set(level, level, level, 1f);
    }

    public ModelInstance instance;
    public Environment env;
    /** Directional light **/
    public DirectionalLight dlight;

    public Map<String, Object> params;

    public String type, modelFile;

    public double scale = 1d;
    public boolean culling = true;
    private boolean initialised, loading;
    private boolean useColor = true;

    private AssetManager manager;
    private float[] cc;

    /**
     * COMPONENTS
     */
    // Texture
    public TextureComponent tc;

    public ModelComponent() {
        this(true);
    }

    public ModelComponent(Boolean initEnvironment) {
        if (initEnvironment) {
            env = new Environment();
            env.set(ambient);
            // Direction from Sun to Earth
            dlight = new DirectionalLight();
            dlight.color.set(1f, 0f, 0f, 1f);
            env.add(dlight);
        }
    }

    public void initialize() {
        if (modelFile != null && Gdx.files.internal(modelFile).exists()) {
            AssetBean.addAsset(modelFile, Model.class);
        }

        if (forceinit || !GlobalConf.scene.LAZY_TEXTURE_INIT && tc != null) {
            tc.initialize();
        }
    }

    public void doneLoading(AssetManager manager, Matrix4 localTransform, float[] cc) {
        this.manager = manager;
        this.cc = cc;

        Model model = null;
        Map<String, Material> materials = null;

        if (staticLight) {
            // Remove dir and global ambient. Add ambient
            //env.remove(dlight);
            // Ambient
            ColorAttribute alight = new ColorAttribute(ColorAttribute.AmbientLight, .6f, .6f, .6f, 1f);
            env.set(alight);
        }

        if (modelFile != null && manager.isLoaded(modelFile)) {
            // Model comes from file (probably .obj or .g3db)
            model = manager.get(modelFile, Model.class);
            materials = new HashMap<String, Material>();
            if (model.materials.size == 0) {
                Material material = new Material();
                model.materials.add(material);
                materials.put("base", material);
            } else {
                if (model.materials.size > 1)
                    for (int i = 0; i < model.materials.size; i++) {
                        materials.put("base" + i, model.materials.get(i));
                    }
                else
                    materials.put("base0", model.materials.first());
            }

        } else if (type != null) {
            // We create the model
            Pair<Model, Map<String, Material>> pair = ModelCache.cache.getModel(type, params, Usage.Position | Usage.Normal | Usage.TextureCoordinates);
            model = pair.getFirst();
            materials = pair.getSecond();
        } else {
            // Data error!
            Logger.error(new RuntimeException("The 'model' element must contain either a 'type' or a 'model' attribute"));
        }
        // Clear base material
        if (materials.containsKey("base"))
            materials.get("base").clear();

        // INITIALIZE MATERIAL
        if (forceinit || !GlobalConf.scene.LAZY_TEXTURE_INIT && tc != null) {
            tc.initMaterial(manager, materials, cc, culling);
        }

        // CREATE MAIN MODEL INSTANCE
        instance = new ModelInstance(model, localTransform);

        // Initialised
        initialised = !GlobalConf.scene.LAZY_TEXTURE_INIT;
        // Loading
        loading = false;
    }

    /**
     * Initialises the texture if it is not initialised yet
     */
    public void touch() {
        if (GlobalConf.scene.LAZY_TEXTURE_INIT && !initialised) {

            if (tc != null) {
                if (!loading) {
                    Logger.info(I18n.bundle.format("notif.loading", tc.base));
                    tc.initialize(manager);
                    // Set to loading
                    loading = true;
                } else if (tc.isFinishedLoading(manager)) {
                    Gdx.app.postRunnable(() -> {
                        tc.initMaterial(manager, instance, cc, culling);
                    });

                    // Set to initialised
                    initialised = true;
                    loading = false;
                }
            } else {
                // Use color if necessary
                if (cc != null && useColor) {
                    // Regular mesh, we use the color
                    int n = instance.materials.size;
                    for (int i = 0; i < n; i++) {
                        Material material = instance.materials.get(i);
                        if (material.get(TextureAttribute.Ambient) == null && material.get(TextureAttribute.Diffuse) == null) {
                            material.set(new ColorAttribute(ColorAttribute.Diffuse, cc[0], cc[1], cc[2], cc[3]));
                            material.set(new ColorAttribute(ColorAttribute.Ambient, cc[0], cc[1], cc[2], cc[3]));
                            if (!culling)
                                material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));
                        }
                    }
                }
                // Set to initialised
                initialised = true;
                loading = false;
            }

        }

    }

    public void addDirectionalLight(float r, float g, float b, float x, float y, float z) {
        DirectionalLight dl = new DirectionalLight();
        dl.set(r, g, b, x, y, z);
        env.add(dl);
    }

    public void dispose() {
        if (instance != null && instance.model != null)
            instance.model.dispose();
    }

    public void setTransparency(float alpha) {
        if (instance != null) {
            int n = instance.materials.size;
            for (int i = 0; i < n; i++) {
                Material mat = instance.materials.get(i);
                BlendingAttribute ba = null;
                if (mat.has(BlendingAttribute.Type)) {
                    ba = (BlendingAttribute) mat.get(BlendingAttribute.Type);
                } else {
                    ba = new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    mat.set(ba);
                }
                ba.opacity = alpha;
            }
        }
    }

    public void setTransparencyColor(float alpha) {
        if (instance != null) {
            int n = instance.materials.size;
            for (int i = 0; i < n; i++) {
                ((ColorAttribute) instance.materials.get(i).get(ColorAttribute.Diffuse)).color.a = alpha;
            }
        }
    }

    /**
     * Sets the type of the model to construct.
     * 
     * @param type
     *            The type. Currently supported types are
     *            sphere|cylinder|ring|disc.
     */
    public void setType(String type) {
        this.type = type;
    }

    public void setTexture(TextureComponent tc) {
        this.tc = tc;
    }

    /**
     * Sets the model file path (this must be a .g3db, .g3dj or .obj).
     * 
     * @param model
     */
    public void setModel(String model) {
        this.modelFile = model;
    }

    public void setStaticlight(String staticLight) {
        this.staticLight = Boolean.valueOf(staticLight);
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public void setScale(Double scale) {
        this.scale = scale;
    }

    public void setScale(Long scale) {
        this.scale = scale;
    }

    public void setCulling(String culling) {
        try {
            this.culling = Boolean.parseBoolean(culling);
        } catch (Exception e) {
        }
    }

    public void setCulling(Boolean culling) {
        this.culling = culling;
    }

    public void setUsecolor(String usecolor) {
        try {
            this.useColor = Boolean.parseBoolean(usecolor);
        } catch (Exception e) {
        }
    }

}
