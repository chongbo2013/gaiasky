package gaia.cu9.ari.gaiaorbit.render.system;

import java.util.List;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import gaia.cu9.ari.gaiaorbit.GaiaSky;
import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.event.IObserver;
import gaia.cu9.ari.gaiaorbit.render.IRenderable;
import gaia.cu9.ari.gaiaorbit.scenegraph.CelestialBody;
import gaia.cu9.ari.gaiaorbit.scenegraph.ICamera;
import gaia.cu9.ari.gaiaorbit.scenegraph.SceneGraphNode.RenderGroup;
import gaia.cu9.ari.gaiaorbit.util.Constants;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;
import gaia.cu9.ari.gaiaorbit.util.Logger;
import gaia.cu9.ari.gaiaorbit.util.coord.AstroUtils;

public class PixelRenderSystem extends ImmediateRenderSystem implements IObserver {
    private final float BRIGHTNESS_FACTOR;

    boolean starColorTransit = false;
    Vector3 aux;
    int additionalOffset, pmOffset;

    public PixelRenderSystem(RenderGroup rg, int priority, float[] alphas) {
        super(rg, priority, alphas);
        EventManager.instance.subscribe(this, Events.TRANSIT_COLOUR_CMD, Events.ONLY_OBSERVED_STARS_CMD);
        BRIGHTNESS_FACTOR = Constants.webgl ? 15f : 4f;
        updatePointSize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

    }

    protected void updatePointSize(int width, int height) {
        GlobalConf.runtime.STAR_POINT_SIZE = GlobalConf.runtime.STRIPPED_FOV_MODE ? 2 : 9;

        // Factor POINT_SIZE with resolution
        float baseResolution = (float) Math.sqrt(1280 * 1280 + 720 * 720);
        float currentResolution = (float) Math.sqrt(width * width + height * height);
        float factor = currentResolution / baseResolution;
        GlobalConf.runtime.STAR_POINT_SIZE = Constants.webgl ? Math.min(15, Math.max(4, GlobalConf.runtime.STAR_POINT_SIZE * factor)) : Math.min(15, Math.max(3, GlobalConf.runtime.STAR_POINT_SIZE * factor * Gdx.graphics.getDensity()));

        GlobalConf.runtime.STAR_POINT_SIZE_BAK = GlobalConf.runtime.STAR_POINT_SIZE;
    }

    @Override
    protected void initShaderProgram() {
        // Initialise renderer
        if (Gdx.app.getType() == ApplicationType.WebGL)
            lineProgram = new ShaderProgram(Gdx.files.internal("shader/point.vertex.glsl"), Gdx.files.internal("shader/point.fragment.wgl.glsl"));
        else
            lineProgram = new ShaderProgram(Gdx.files.internal("shader/point.vertex.glsl"), Gdx.files.internal("shader/point.fragment.glsl"));
        if (!lineProgram.isCompiled()) {
            Logger.error(this.getClass().getName(), "Point shader compilation failed:\n" + lineProgram.getLog());
        }
        lineProgram.begin();
        lineProgram.setUniformf("u_pointAlphaMin", GlobalConf.scene.POINT_ALPHA_MIN);
        lineProgram.setUniformf("u_pointAlphaMax", GlobalConf.scene.POINT_ALPHA_MAX);
        lineProgram.end();

    }

    @Override
    protected void initVertices() {
        meshes = new MeshData[1];
        curr = new MeshData();
        meshes[0] = curr;

        aux = new Vector3();

        /** Init renderer **/
        maxVertices = 3000000;

        VertexAttribute[] attribs = buildVertexAttributes();
        curr.mesh = new Mesh(false, maxVertices, 0, attribs);

        curr.vertices = new float[maxVertices * (curr.mesh.getVertexAttributes().vertexSize / 4)];
        curr.vertexSize = curr.mesh.getVertexAttributes().vertexSize / 4;
        curr.colorOffset = curr.mesh.getVertexAttribute(Usage.ColorPacked) != null ? curr.mesh.getVertexAttribute(Usage.ColorPacked).offset / 4 : 0;
        pmOffset = curr.mesh.getVertexAttribute(Usage.Tangent) != null ? curr.mesh.getVertexAttribute(Usage.Tangent).offset / 4 : 0;
        additionalOffset = curr.mesh.getVertexAttribute(Usage.Generic) != null ? curr.mesh.getVertexAttribute(Usage.Generic).offset / 4 : 0;
    }

    @Override
    public void renderStud(List<IRenderable> renderables, ICamera camera) {
        if (POINT_UPDATE_FLAG) {
            // Reset variables
            curr.clear();

            int size = renderables.size();
            for (int i = 0; i < size; i++) {
                // 2 FPS gain
                CelestialBody cb = (CelestialBody) renderables.get(i);
                float[] col = starColorTransit ? cb.ccTransit : cb.ccPale;

                // COLOR
                curr.vertices[curr.vertexIdx + curr.colorOffset] = Color.toFloatBits(col[0], col[1], col[2], cb.opacity);

                // SIZE
                curr.vertices[curr.vertexIdx + additionalOffset] = cb.getRadius();
                curr.vertices[curr.vertexIdx + additionalOffset + 1] = (float) cb.THRESHOLD_POINT();

                // VERTEX
                aux.set((float) cb.pos.x, (float) cb.pos.y, (float) cb.pos.z);
                //cb.transform.getTranslationf(aux);
                final int idx = curr.vertexIdx;
                curr.vertices[idx] = aux.x;
                curr.vertices[idx + 1] = aux.y;
                curr.vertices[idx + 2] = aux.z;

                // PROPER MOTION
                curr.vertices[curr.vertexIdx + pmOffset] = (float) cb.getPmX() * 0f;
                curr.vertices[curr.vertexIdx + pmOffset + 1] = (float) cb.getPmY() * 0f;
                curr.vertices[curr.vertexIdx + pmOffset + 2] = (float) cb.getPmZ() * 0f;

                curr.vertexIdx += curr.vertexSize;
            }
            // Put flag down
            POINT_UPDATE_FLAG = false;
        }
        if (Gdx.app.getType() == ApplicationType.Desktop) {
            // Enable gl_PointCoord
            Gdx.gl20.glEnable(34913);
            // Enable point sizes
            Gdx.gl20.glEnable(0x8642);
        }
        lineProgram.begin();
        lineProgram.setUniformMatrix("u_projModelView", camera.getCamera().combined);
        lineProgram.setUniformf("u_camPos", camera.getCurrent().getPos().setVector3(aux));
        lineProgram.setUniformf("u_fovFactor", camera.getFovFactor());
        lineProgram.setUniformf("u_alpha", alphas[0]);
        lineProgram.setUniformf("u_starBrightness", GlobalConf.scene.STAR_BRIGHTNESS * BRIGHTNESS_FACTOR);
        lineProgram.setUniformf("u_pointSize", camera.getNCameras() == 1 ? GlobalConf.runtime.STAR_POINT_SIZE : GlobalConf.runtime.STAR_POINT_SIZE * 10);
        lineProgram.setUniformf("u_t", (float) AstroUtils.getMsSinceJ2000(GaiaSky.instance.time.getTime()));
        curr.mesh.setVertices(curr.vertices, 0, curr.vertexIdx);
        curr.mesh.render(lineProgram, ShapeType.Point.getGlType());
        lineProgram.end();

    }

    protected VertexAttribute[] buildVertexAttributes() {
        Array<VertexAttribute> attribs = new Array<VertexAttribute>();
        attribs.add(new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE));
        attribs.add(new VertexAttribute(Usage.Tangent, 3, "a_pm"));
        attribs.add(new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
        attribs.add(new VertexAttribute(Usage.Generic, 4, "a_additional"));

        VertexAttribute[] array = new VertexAttribute[attribs.size];
        for (int i = 0; i < attribs.size; i++)
            array[i] = attribs.get(i);
        return array;
    }

    @Override
    public void notify(Events event, Object... data) {
        if (event == Events.TRANSIT_COLOUR_CMD) {
            starColorTransit = (boolean) data[1];
            POINT_UPDATE_FLAG = true;
        } else if (event == Events.ONLY_OBSERVED_STARS_CMD) {
            POINT_UPDATE_FLAG = true;
        }
    }

    @Override
    public void resize(int w, int h) {
        updatePointSize(w, h);
    }
}
