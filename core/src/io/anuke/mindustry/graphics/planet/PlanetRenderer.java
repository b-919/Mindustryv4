package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.graphics.Bloom;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.PlanetMap.Region;
import io.anuke.mindustry.maps.campaign.PlanetVisual;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.world;

/**
 * Renders a planet as a real 3D scene: geodesic terrain, sector regions, atmosphere and a
 * space background. The scene is rendered into a depth-enabled framebuffer and then composited
 * with the normal uCore sprite batch, so no 3D state leaks into the UI.
 * This class only orchestrates: it owns the camera, framebuffers, bloom, skybox and draw
 * order. All geometry and per-body shader state live in {@link PlanetModel} and the mesh
 * classes under this package.
 */
public class PlanetRenderer implements Disposable{
    private final PlanetShaders shaders = new PlanetShaders(Vars.mobile);
    private final PlanetCamera camera = new PlanetCamera();
    private final PlanetParams params = new PlanetParams();
    private final PlanetHover hover = new PlanetHover();
    private final PlanetMarkers markers = new PlanetMarkers();
    private final PlanetTmp tmp = new PlanetTmp();
    private final Matrix4 identity = new Matrix4();

    private FrameBuffer buffer;
    private FrameBuffer emissionBuffer;
    private ShaderProgram ps1Shader;
    private Bloom bloom;
    private SpriteBatch bufferBatch;
    private Texture starTexture;
    private final PlanetSkybox skybox = new PlanetSkybox();

    private PlanetModel model;
    private PlanetDefinition currentPlanet;
    private int sectorVersion = -1;

    private float gridRadius = 1.10f;
    private float atmosphereRadius = 1.24f;
    private float gridAlpha = 1f, cloudAlpha = 0f, atmosphereAlpha = 0f, sectorAlpha = 1f;
    private float cloudRotation;
    private float time;

    public PlanetRenderer(){
        bufferBatch = new SpriteBatch();
        starTexture = createStarTexture();
        shaders.build();

        Settings.defaults("planetps1", false);
        Settings.defaults("planetps1size", 3);

        ps1Shader = loadPostShader();
    }

    private ShaderProgram loadPostShader(){
        try{
            ShaderProgram program = new ShaderProgram(
                Gdx.files.internal("shaders/ps1.vertex").readString(),
                Gdx.files.internal("shaders/ps1.fragment").readString());

            if(!program.isCompiled()){
                Gdx.app.error("Planet3D", "PS1 shader failed: " + program.getLog());
                program.dispose();
                return null;
            }
            return program;
        }catch(Exception e){
            Gdx.app.error("Planet3D", "Could not load PS1 shader.", e);
            return null;
        }
    }

    private boolean ps1Active(){
        return ps1Shader != null && Settings.getBool("planetps1");
    }

    /** Rebuilds all geometry for a planet. This is intentionally client-side only. */
    public void rebuildPlanetModels(PlanetDefinition planet){
        if(planet == null){
            disposePlanet();
            return;
        }
        if(currentPlanet == planet && model != null && world.sectors.version() == sectorVersion){
            model.map.ensureSectors(world.sectors);
            return;
        }

        disposePlanet();
        currentPlanet = planet;
        model = PlanetModel.build(planet, shaders);
        sectorVersion = world.sectors.version();

        if(model != null){
            gridRadius = model.gridRadius;
            atmosphereRadius = model.atmosphereRadius;
        }

        applyBloomSettings();
    }

    /**
     * Renders the planet and returns the sector under the mouse, if any.
     *
     * @param x screen-space x of the element
     * @param y screen-space y of the element (bottom-left origin)
     */
    public PlanetHover render(float x, float y, float width, float height, PlanetDefinition planet,
                              float rotLon, float rotLat, float zoom, Sector selected){
        hover.reset();
        if(!shaders.isReady()){
            shaders.build();
        }

        if(width <= 2f || height <= 2f || planet == null || !shaders.isReady()){
            if(planet != null && !shaders.isReady()){
                throw new IllegalStateException("Planet shaders are not compiled.");
            }
            return hover;
        }

        if(currentPlanet != planet || model == null || world.sectors.version() != sectorVersion){
            rebuildPlanetModels(planet);
        }
        if(model == null){
            return hover;
        }

        int w = Math.max(1, (int)width);
        int h = Math.max(1, (int)height);

        updateZoomVisibility(zoom);
        resizeBuffer(w, h);
        camera.update(w, h, rotLon, rotLat, zoom);
        updateHover(x, y, width, height, selected);

        time += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        cloudRotation += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);

        fillParams(x, y, width, height, planet, selected);

        boolean drawing = Graphics.drawing();
        boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);

        if(drawing){
            Graphics.end();
        }

        boolean bloomed = false;
        try{
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            renderScene(w, h);
            bloomed = renderBloom(w, h);
        }finally{
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            if(scissor){
                Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            }
            if(drawing){
                Graphics.begin();
            }
        }

        if(!bloomed){
            drawFinal(buffer.getColorBufferTexture(), x, y, width, height);
        }

        markers.draw(model.map, model.regionHeights, model.gridLift, sectorAlpha, camera,
            x, y, width, height, selected, hover.sector);
        return hover;
    }

    /** Draws the final planet image, optionally through the PS1 pixelation/quantization shader. */
    private void drawFinal(Texture texture, float x, float y, float width, float height){
        boolean ps1 = ps1Active();
        TextureFilter oldMin = texture.getMinFilter();
        TextureFilter oldMag = texture.getMagFilter();

        if(ps1){
            int pixelSize = Mathf.clamp(Settings.getInt("planetps1size", 3), 1, 16);

            ps1Shader.begin();
            ps1Shader.setUniformi("u_texture", 0);
            ps1Shader.setUniformf("u_resolution", Math.max(1f, width / pixelSize), Math.max(1f, height / pixelSize));
            ps1Shader.setUniformf("u_levels", 24f);
            ps1Shader.end();

            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            Core.batch.setShader(ps1Shader);
        }

        Draw.color(Color.WHITE);
        Draw.alpha(1f);
        Core.batch.draw(texture, x, y + height, width, -height);
        Draw.reset();

        if(ps1){
            Core.batch.setShader(null);
            texture.setFilter(oldMin, oldMag);
        }
    }

    private void fillParams(float x, float y, float width, float height, PlanetDefinition planet, Sector selected){
        params.planet = planet;
        params.selected = selected;
        params.x = x;
        params.y = y;
        params.width = width;
        params.height = height;
        params.time = time;
        params.cloudRotation = cloudRotation;
        params.gridAlpha = gridAlpha;
        params.sectorAlpha = sectorAlpha;
        params.cloudAlpha = cloudAlpha;
        params.atmosphereAlpha = atmosphereAlpha;
        params.projection.set(camera.cam.combined);
        params.light.set(model.system.light);
        params.camPos.set(camera.cam.position);
        params.camDir.set(camera.cam.direction);

        int jitter = Settings.getInt("planetps1jitter", 0);
        if(jitter > 0){
            float pixelSize = Math.max(1f, jitter * 2f);
            params.jitter = 1f;
            params.jitterResolutionX = Math.max(1f, width / pixelSize);
            params.jitterResolutionY = Math.max(1f, height / pixelSize);
        }else{
            params.jitter = 0f;
        }
    }

    private void updateZoomVisibility(float zoom){
        float t = Mathf.clamp((zoom - 1.0f) / 0.45f, 0f, 1f);
        t = t * t * (3f - 2f * t);
        gridAlpha = 1f - t;
        sectorAlpha = gridAlpha;

        float c = Mathf.clamp((zoom - 0.72f) / 0.55f, 0f, 1f);
        c = c * c * (3f - 2f * c);
        cloudAlpha = c;
        atmosphereAlpha = c;
    }

    private void renderScene(int w, int h){
        buffer.begin();
        try{
            Gdx.gl.glViewport(0, 0, w, h);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glClearColor(0.008f, 0.012f, 0.025f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

            if(skybox.isReady()){
                skybox.render(camera.cam);
            }else{
                bufferBatch.setProjectionMatrix(tmp.ortho.setToOrtho2D(0, 0, w, h));
                bufferBatch.begin();
                bufferBatch.draw(starTexture, 0, 0, w, h);
                bufferBatch.end();
            }

            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glCullFace(GL20.GL_BACK);
            Gdx.gl.glFrontFace(GL20.GL_CCW);

            //opaque bodies
            model.moon.render(params, model.system.moonTransform);
            model.sun.render(params, model.system.sunTransform);

            //sun bloom
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            Gdx.gl.glDepthMask(false);
            model.sun.renderGlows(params, model.system.sunTransform);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            model.terrain.render(params, identity);

            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            Gdx.gl.glDepthMask(false);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            model.overlay.renderGrid(params);
            for(OrbitMesh orbit : model.orbits){
                orbit.render(params, identity);
            }

            model.overlay.buildGeometry(params.selected, hover.sector, time);
            model.overlay.renderSectors(params);

            //clouds are drawn before the atmosphere so the atmosphere haze sits over them
            Gdx.gl.glEnable(GL20.GL_CULL_FACE);
            Gdx.gl.glCullFace(GL20.GL_BACK);
            Gdx.gl.glDepthMask(false);
            if(cloudAlpha > 0.01f){
                for(CloudMesh layer : model.clouds){
                    layer.render(params, identity);
                }
            }
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);

            Gdx.gl.glDepthMask(false);
            if(atmosphereAlpha > 0.01f){
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                model.atmosphere.render(params, identity);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }

            if(sectorAlpha > 0.01f){
                model.overlay.buildArcs(params.selected);
                model.overlay.renderArcs(params);
            }

            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        }finally{
            buffer.end();
        }
    }

    private boolean renderBloom(int width, int height){
        if(bloom == null || !bloom.isReady() || emissionBuffer == null) return false;
        emissionBuffer.begin();
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);

        //depth-only occluders
        Gdx.gl.glColorMask(false, false, false, false);
        model.terrain.render(params, identity);
        model.moon.render(params, model.system.moonTransform);
        Gdx.gl.glColorMask(true, true, true, true);

        //lava emission into the bloom source
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        model.terrain.renderEmissive(params, identity, shaders.emissive);
        Gdx.gl.glDepthFunc(GL20.GL_LESS);

        //sun surface additive glow
        model.sun.render(params, model.system.sunTransform);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        emissionBuffer.end();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        if(ps1Active()){
            int pixelSize = Mathf.clamp(Settings.getInt("planetps1size", 3), 1, 16);
            bloom.setPS1(true, Math.max(1f, width / pixelSize), Math.max(1f, height / pixelSize));
        }else{
            bloom.setPS1(false, 0f, 0f);
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        bloom.render(buffer.getColorBufferTexture(), emissionBuffer.getColorBufferTexture());
        return true;
    }

    private void updateHover(float x, float y, float width, float height, Sector selected){
        if(model == null) return;

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();

        if(mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height){
            return;
        }

        float top = Gdx.graphics.getHeight() - y - height;
        float mouseTop = Gdx.graphics.getHeight() - mouseY;

        camera.cam.unproject(tmp.near.set(mouseX, mouseTop, 0f), x, top, width, height);
        camera.cam.unproject(tmp.far.set(mouseX, mouseTop, 1f), x, top, width, height);
        tmp.ray.origin.set(tmp.near);
        tmp.ray.direction.set(tmp.far).sub(tmp.near).nor();

        if(!Intersector.intersectRaySphere(tmp.ray, Vector3.Zero, gridRadius, tmp.hit)){
            if(!Intersector.intersectRaySphere(tmp.ray, Vector3.Zero, atmosphereRadius, tmp.hit)){
                return;
            }
        }

        Region region = model.map.regionAt(tmp.hit);
        if(region == null || region.sector == null) return;

        hover.sector = region.sector;
        hover.selected = hover.sector == selected;

        tmp.project.set(region.center).scl(1f + (model.regionHeights == null ? 0f : model.regionHeights[region.index]) + model.gridLift);
        camera.cam.project(tmp.project, x, y, width, height);
        hover.x = tmp.project.x;
        hover.y = tmp.project.y;
    }

    private void resizeBuffer(int width, int height){
        if(buffer != null && buffer.getWidth() == width && buffer.getHeight() == height){
            return;
        }

        if(buffer != null){
            buffer.dispose();
        }
        if(emissionBuffer != null){
            emissionBuffer.dispose();
        }

        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        buffer.getColorBufferTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);

        emissionBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        emissionBuffer.getColorBufferTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);

        PlanetVisual visual = currentPlanet == null ? null : currentPlanet.visual;

        if(bloom == null){
            bloom = new Bloom(width, height);
            bloom.setOriginalIntensity(1f);
        }else{
            bloom.resize(width, height);
        }

        applyBloomSettings();
    }

    /** Applies content-driven bloom values; safe to call when switching planets. */
    private void applyBloomSettings(){
        if(bloom == null) return;

        //the bloom source is the sun, so its visual owns these values
        PlanetVisual visual = model != null ? model.system.sun.visual
            : (currentPlanet == null ? null : currentPlanet.visual);
        bloom.blurPasses = visual == null ? 4 : visual.bloomBlurPasses;
        bloom.setThreshold(visual == null ? 0.85f : Mathf.clamp(visual.bloomThreshold, 0f, 0.99f));
        bloom.setBloomIntensity(visual == null ? 2.2f : visual.bloomIntensity);
    }

    private Texture createStarTexture(){ // todo this is actually not used
        Pixmap pixmap = new Pixmap(1024, 512, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.006f, 0.009f, 0.02f, 1f);
        pixmap.fill();

        for(int i = 0; i < 1400; i++){
            float brightness = 0.25f + MathUtils.random() * 0.75f;
            int size = MathUtils.randomBoolean(0.08f) ? 2 : 1;
            pixmap.setColor(brightness * 0.8f, brightness * 0.85f, brightness, 1f);
            int x = MathUtils.random(0, pixmap.getWidth() - 1);
            int y = MathUtils.random(0, pixmap.getHeight() - 1);
            pixmap.fillRectangle(x, y, size, size);
        }

        //a subtle band of denser stars
        for(int i = 0; i < 500; i++){
            float brightness = 0.12f + MathUtils.random() * 0.4f;
            pixmap.setColor(brightness * 0.7f, brightness * 0.8f, brightness, 1f);
            int x = MathUtils.random(0, pixmap.getWidth() - 1);
            int y = (int)(pixmap.getHeight() * 0.5f + MathUtils.random(-90f, 90f));
            pixmap.fillRectangle(x, Mathf.clamp(y, 0, pixmap.getHeight() - 1), 1, 1);
        }

        Texture texture = new Texture(pixmap);
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private void disposePlanet(){
        if(model != null){
            model.dispose();
            model = null;
        }
        currentPlanet = null;
    }

    @Override
    public void dispose(){
        disposePlanet();
        shaders.dispose();

        if(buffer != null) buffer.dispose();
        if(bloom != null) bloom.dispose();
        if(emissionBuffer != null) emissionBuffer.dispose();
        if(ps1Shader != null) ps1Shader.dispose();
        if(bufferBatch != null) bufferBatch.dispose();
        if(starTexture != null) starTexture.dispose();
        skybox.dispose();

        buffer = null;
        bloom = null;
        emissionBuffer = null;
        ps1Shader = null;
        bufferBatch = null;
        starTexture = null;
    }
}
