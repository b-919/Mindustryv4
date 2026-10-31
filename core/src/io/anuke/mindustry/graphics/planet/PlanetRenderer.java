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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.Planets;
import io.anuke.mindustry.graphics.Bloom;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.PlanetMap.Region;
import io.anuke.mindustry.maps.campaign.PlanetOrbit;
import io.anuke.mindustry.maps.campaign.PlanetVisual;
import io.anuke.mindustry.maps.campaign.SolarSystemDefinition;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.noise.Simplex;
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

    //multi-body space scene
    //TODO needs refactor multiple scene using same stuff, keeping like this rn bc is the best way i found
    private SolarSystemDefinition spaceSystem;
    private PlanetModel spaceModel;
    private SunMesh spaceSun;
    private int spaceSectorVersion = -1;
    private final Array<SpaceBody> spaceBodies = new Array<>();
    private final Array<SpaceOrbit> spaceOrbits = new Array<>();
    private final ObjectMap<PlanetDefinition, SpaceBody> spaceBodyMap = new ObjectMap<>();
    private final ObjectMap<PlanetDefinition, Float> spaceOrbitAngles = new ObjectMap<>();
    private final ObjectMap<PlanetDefinition, Float> spaceRotationAngles = new ObjectMap<>();
    private final ObjectMap<String, Float> pendingOrbitAngles = new ObjectMap<>();
    private final ObjectMap<String, Float> pendingRotationAngles = new ObjectMap<>();
    private final Vector3 spaceLight = new Vector3();
    private final Vector3 spaceTmp = new Vector3();
    private final Matrix4 orbitTransform = new Matrix4();
    private final Simplex spaceNoise = new Simplex(0x5e7f31);
    private float spaceTime;

    private static class SpaceOrbit{
        final OrbitMesh mesh;
        final PlanetDefinition parent;

        SpaceOrbit(OrbitMesh mesh, PlanetDefinition parent){
            this.mesh = mesh;
            this.parent = parent;
        }
    }

    /** One renderable body in the space scene. */
    private static class SpaceBody{
        PlanetDefinition def;
        final Vector3 position = new Vector3();
        final Matrix4 transform = new Matrix4();
        GenericMesh surface;
        PlanetSurfaceMesh emissiveSurface;
        CloudMesh[] clouds = {};
        AtmosphereMesh atmosphere;
        float scale = 1f;
        boolean sun;
    }

    /** Whether the next composited frame is allowed to use the PS1/jitter post effects. */
    private boolean postProcessing = true;

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
        return postProcessing && ps1Shader != null && Settings.getBool("planetps1");
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
                              float rotLon, float rotLat, float zoom, Sector selected, float orbitTime){
        postProcessing = true;
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

        model.system.configure(planet, orbitTime);

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

    /** Renders the scene from a free 3D camera (detached space mode). */
    public void renderFree(float x, float y, float width, float height, PlanetDefinition planet,
                           float camX, float camY, float camZ, float yaw, float pitch){
        postProcessing = true;
        if(!shaders.isReady()){
            shaders.build();
        }

        if(width <= 2f || height <= 2f || planet == null || !shaders.isReady()){
            return;
        }

        if(currentPlanet != planet || model == null || world.sectors.version() != sectorVersion){
            rebuildPlanetModels(planet);
        }
        if(model == null){
            return;
        }

        int w = Math.max(1, (int)width);
        int h = Math.max(1, (int)height);

        gridAlpha = 0f;
        sectorAlpha = 0f;
        cloudAlpha = 1f;
        atmosphereAlpha = 1f;

        resizeBuffer(w, h);
        camera.updateFree(w, h, camX, camY, camZ, yaw, pitch, 58f);

        time += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        cloudRotation += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);

        fillParams(x, y, width, height, planet, null);

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
    }

    /** Renders the scene into the internal framebuffer and returns its texture, without drawing to screen. */
    public Texture renderToTexture(float width, float height, PlanetDefinition planet, float lon, float lat, float zoom){
        return renderTexture(width, height, planet, true, lon, lat, zoom, 0f, 0f, 0f, 0f, 0f);
    }

    /** Free-camera variant of {@link #renderToTexture}. */
    public Texture renderFreeToTexture(float width, float height, PlanetDefinition planet,
                                       float camX, float camY, float camZ, float yaw, float pitch){
        return renderTexture(width, height, planet, false, 0f, 0f, 0f, camX, camY, camZ, yaw, pitch);
    }

    /**
     * Builds the selected solar system's bodies. Only this group is ever created, so systems
     * that happen to share coordinates never render unless they are selected.
     */
    private boolean ensureSpaceScene(SolarSystemDefinition system){
        Planets.load();

        if(system == null) system = Planets.defaultSystem();
        if(system == null || system.bodies.size == 0) return false;

        if(spaceSystem != system || world.sectors.version() != spaceSectorVersion){
            disposeSpaceScene();
            spaceSystem = system;
            buildSpaceScene(system);
            spaceSectorVersion = world.sectors.version();
            applyBloomSettings();
        }

        return spaceBodies.size > 0;
    }

    private void buildSpaceScene(SolarSystemDefinition system){
        PlanetDefinition sun = system.sun();
        PlanetDefinition primary = system.primary();
        if(primary != null && primary.isSun){
            primary = null;
        }

        //positions are filled in by updateSpacePositions(); the primary ends up at the origin
        if(primary != null){
            if(primary.campaign != null && primary.generator != null){
                spaceModel = PlanetModel.build(primary, shaders);
            }

            if(spaceModel != null){
                addSpaceBody(primary, Vector3.Zero, primary.radius, spaceModel.terrain,
                    spaceModel.terrain, spaceModel.clouds, spaceModel.atmosphere, false);
            }else{
                BodyMesh mesh = BodyMesh.create(primary, 17, shaders.planet);
                addSpaceBody(primary, Vector3.Zero, 1f, mesh, null, null, null, false);
            }
        }

        if(sun != null){
            spaceSun = SunMesh.create(sun.visual, spaceNoise, shaders.sun);
            addSpaceBody(sun, Vector3.Zero, 1f, spaceSun, null, null, null, true);
        }

        int index = 0;
        for(PlanetDefinition body : system.bodies){
            if(body == primary || body == sun) continue;

            if(body.isSun){
                SunMesh mesh = SunMesh.create(body.visual, spaceNoise, shaders.sun);
                addSpaceBody(body, Vector3.Zero, 1f, mesh, null, null, null, true);
            }else{
                BodyMesh mesh = BodyMesh.create(body, 31 + index * 7, shaders.planet);
                addSpaceBody(body, Vector3.Zero, 1f, mesh, null, null, null, false);
            }
            index++;
        }

        initSpaceAngles();
        updateSpacePositions();
        buildSpaceOrbits();
    }

    private void initSpaceAngles(){
        spaceOrbitAngles.clear();
        spaceRotationAngles.clear();

        for(SpaceBody body : spaceBodies){
            Float savedOrbit = pendingOrbitAngles.get(body.def.name);
            Float savedRotation = pendingRotationAngles.get(body.def.name);

            spaceOrbitAngles.put(body.def, savedOrbit != null ? PlanetOrbit.normalizeAngle(savedOrbit)
                : PlanetOrbit.normalizeAngle(body.def.orbitStart + body.def.orbitSpeed * spaceTime));
            spaceRotationAngles.put(body.def, savedRotation != null ? PlanetOrbit.normalizeAngle(savedRotation)
                : PlanetOrbit.normalizeAngle(body.def.rotationSpeed * spaceTime));
        }
    }

    /**
     * Advances the stored angles instead of recomputing them from time. Changing a speed only
     * affects future motion, so setting it to 0 freezes the body where it currently is.
     */
    private void advanceSpaceAngles(float delta){
        for(SpaceBody body : spaceBodies){
            float orbit = spaceOrbitAngles.get(body.def, body.def.orbitStart) + body.def.orbitSpeed * delta;
            spaceOrbitAngles.put(body.def, PlanetOrbit.normalizeAngle(orbit));

            float rotation = spaceRotationAngles.get(body.def, 0f) + body.def.rotationSpeed * delta;
            spaceRotationAngles.put(body.def, PlanetOrbit.normalizeAngle(rotation));
        }
    }

    /** Recomputes every body's position/rotation for the current orbit time. */
    private void updateSpacePositions(){
        if(spaceSystem == null) return;

        //the space scene is heliocentric: the sun sits at the origin and planets orbit it
        PlanetDefinition sun = spaceSystem.sun();
        ObjectMap<PlanetDefinition, Vector3> positions = new ObjectMap<>();
        PlanetOrbit.positionsCentered(spaceSystem, spaceOrbitAngles, sun, positions);

        for(SpaceBody body : spaceBodies){
            Vector3 pos = positions.get(body.def);
            if(pos == null) continue;

            body.position.set(pos);
            body.transform.setToTranslation(pos);
            if(!body.sun){
                body.transform.rotate(Vector3.Y, spaceRotationAngles.get(body.def, 0f));
            }
            body.transform.scale(body.scale, body.scale, body.scale);
        }

        Vector3 sunPos = sun == null ? null : positions.get(sun);
        Vector3 primaryPos = spaceSystem.primary() == null ? null : positions.get(spaceSystem.primary());
        if(sunPos != null && primaryPos != null){
            spaceLight.set(sunPos).sub(primaryPos).nor();
        }else if(sunPos != null && !sunPos.isZero()){
            spaceLight.set(sunPos).nor();
        }
    }

    private SpaceBody addSpaceBody(PlanetDefinition def, Vector3 position, float scale, GenericMesh surface,
                                   PlanetSurfaceMesh emissiveSurface, CloudMesh[] clouds,
                                   AtmosphereMesh atmosphere, boolean sun){
        SpaceBody body = new SpaceBody();
        body.def = def;
        body.position.set(position);
        body.transform.setToTranslation(position).scale(scale, scale, scale);
        body.surface = surface;
        body.emissiveSurface = emissiveSurface;
        body.clouds = clouds == null ? new CloudMesh[0] : clouds;
        body.atmosphere = atmosphere;
        body.scale = scale;
        body.sun = sun;

        spaceBodies.add(body);
        spaceBodyMap.put(def, body);
        return body;
    }

    private void buildSpaceOrbits(){
        for(SpaceBody body : spaceBodies){
            PlanetDefinition def = body.def;
            if(!def.displayOrbit || def.parent == null) continue;

            SpaceBody parent = spaceBodyMap.get(def.parent);
            if(parent == null) continue;

            //build around the origin; the ring is translated to the parent every frame
            Vector3 base = new Vector3();
            Vector3 tangent = new Vector3();
            PlanetOrbit.orbitBasis(def, base, tangent);
            float radius = PlanetOrbit.orbitRadius(def);

            spaceOrbits.add(new SpaceOrbit(
                OrbitMesh.createLocal(radius, base, tangent, def.visual.orbitColor, shaders.overlay),
                def.parent));
        }
    }

    private float bodyRadius(PlanetDefinition body){
        if(body == null) return 1f;
        return body.isSun ? Math.max(0.3f, body.visual.sunRadius) : Math.max(0.3f, body.radius);
    }

    /** @return the world position of a body in the selected system, or null if it is not rendered. */
    public Vector3 spacePosition(SolarSystemDefinition system, PlanetDefinition body){
        if(!ensureSpaceScene(system) || body == null){
            return null;
        }

        updateSpacePositions();
        SpaceBody entry = spaceBodyMap.get(body);
        return entry == null ? null : entry.position;
    }

    /**
     * Computes a camera pose that orbits the given body. The resulting direction looks at the
     * body's centre, so the caller only needs to convert it to yaw/pitch for the free camera.
     */
    public boolean spaceOrbitPose(SolarSystemDefinition system, PlanetDefinition body, float lon, float lat,
                                  float zoom, Vector3 outPos, Vector3 outDir){
        return spaceOrbitPose(system, body, lon, lat, zoom, false, outPos, outDir);
    }

    /**
     * @param coRotate keep the camera fixed relative to the planet surface while it rotates
     */
    public boolean spaceOrbitPose(SolarSystemDefinition system, PlanetDefinition body, float lon, float lat,
                                  float zoom, boolean coRotate, Vector3 outPos, Vector3 outDir){
        Vector3 target = spacePosition(system, body);
        if(target == null){
            return false;
        }

        float radius = bodyRadius(body);
        float distance = Mathf.clamp(3.65f * radius * zoom, 1.2f, 200f);
        float cy = MathUtils.cos(lat), sy = MathUtils.sin(lat);
        float cx = MathUtils.cos(lon), sx = MathUtils.sin(lon);

        outPos.set(target.x + distance * cy * cx, target.y + distance * sy, target.z + distance * cy * sx);

        if(coRotate){
            float rotation = spaceRotationAngles.get(body, 0f);
            if(rotation != 0f){
                spaceTmp.set(outPos).sub(target).rotate(Vector3.Y, rotation);
                outPos.set(target).add(spaceTmp);
            }
        }

        outDir.set(target).sub(outPos).nor();
        return true;
    }

    /** Computes a camera pose that frames the whole selected system. */
    public boolean spaceSystemPose(SolarSystemDefinition system, Vector3 outPos, Vector3 outDir){
        if(!ensureSpaceScene(system) || spaceBodies.size == 0){
            return false;
        }

        updateSpacePositions();

        Vector3 min = new Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vector3 max = new Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

        for(SpaceBody body : spaceBodies){
            float radius = bodyRadius(body.def);
            if(body.sun && body.def.visual.sunGlows.size > 0){
                radius = Math.max(radius, body.def.visual.sunGlows.peek().radius);
            }

            min.x = Math.min(min.x, body.position.x - radius);
            min.y = Math.min(min.y, body.position.y - radius);
            min.z = Math.min(min.z, body.position.z - radius);
            max.x = Math.max(max.x, body.position.x + radius);
            max.y = Math.max(max.y, body.position.y + radius);
            max.z = Math.max(max.z, body.position.z + radius);
        }

        Vector3 center = new Vector3(min).add(max).scl(0.5f);
        float radius = 0f;
        for(SpaceBody body : spaceBodies){
            radius = Math.max(radius, body.position.dst(center) + bodyRadius(body.def));
        }

        float distance = Math.max(4f, radius / (float)Math.tan(Math.toRadians(29f)) * 1.15f);
        outPos.set(center.x, center.y + radius * 0.18f, center.z + distance);
        outDir.set(center).sub(outPos).nor();
        return true;
    }

    /** Sets the shared orbit/rotation clock, used to keep the in-game background in sync. */
    public void setSpaceTime(float time){
        spaceTime = time;
    }

    /** Restores saved orbit/rotation angles by planet name. */
    public void setSpaceAngles(ObjectMap<String, Float> orbitAngles, ObjectMap<String, Float> rotationAngles){
        pendingOrbitAngles.clear();
        pendingRotationAngles.clear();

        for(ObjectMap.Entry<String, Float> entry : orbitAngles){
            pendingOrbitAngles.put(entry.key, entry.value);
        }
        for(ObjectMap.Entry<String, Float> entry : rotationAngles){
            pendingRotationAngles.put(entry.key, entry.value);
        }

        if(spaceSystem != null && spaceBodies.size > 0){
            for(SpaceBody body : spaceBodies){
                Float orbit = pendingOrbitAngles.get(body.def.name);
                Float rotation = pendingRotationAngles.get(body.def.name);
                if(orbit != null) spaceOrbitAngles.put(body.def, PlanetOrbit.normalizeAngle(orbit));
                if(rotation != null) spaceRotationAngles.put(body.def, PlanetOrbit.normalizeAngle(rotation));
            }
            updateSpacePositions();
        }
    }

    /** Copies the current accumulated angles out, keyed by planet name. */
    public void copySpaceAnglesTo(ObjectMap<String, Float> orbitAngles, ObjectMap<String, Float> rotationAngles){
        orbitAngles.clear();
        rotationAngles.clear();

        for(SpaceBody body : spaceBodies){
            Float orbit = spaceOrbitAngles.get(body.def);
            Float rotation = spaceRotationAngles.get(body.def);
            if(orbit != null) orbitAngles.put(body.def.name, orbit);
            if(rotation != null) rotationAngles.put(body.def.name, rotation);
        }
    }

    /** Renders the selected solar system's full scene from a free camera. */
    public void renderSpace(SolarSystemDefinition system, float x, float y, float width, float height,
                            float camX, float camY, float camZ, float yaw, float pitch, boolean postEffects){
        postProcessing = postEffects;

        if(!shaders.isReady()){
            shaders.build();
        }
        if(width <= 2f || height <= 2f || !shaders.isReady() || !ensureSpaceScene(system)){
            return;
        }

        int w = Math.max(1, (int)width);
        int h = Math.max(1, (int)height);

        gridAlpha = 0f;
        sectorAlpha = 0f;
        cloudAlpha = 1f;
        atmosphereAlpha = 1f;

        resizeBuffer(w, h);
        camera.updateFree(w, h, camX, camY, camZ, yaw, pitch, 58f);

        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        time += delta;
        cloudRotation += delta;
        spaceTime += delta;
        advanceSpaceAngles(delta);
        updateSpacePositions();

        fillParams(x, y, width, height, spaceSystem == null ? null : spaceSystem.primary(), null);
        params.light.set(spaceLight);

        boolean drawing = Graphics.drawing();
        boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);

        if(drawing){
            Graphics.end();
        }

        boolean bloomed = false;
        try{
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            renderSpaceScene(w, h);
            bloomed = renderSpaceBloom(w, h);
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
    }

    /** Renders the space scene into the internal framebuffer and returns its texture. */
    public Texture renderSpaceToTexture(SolarSystemDefinition system, float width, float height,
                                        float camX, float camY, float camZ, float yaw, float pitch){
        postProcessing = false;

        if(!shaders.isReady()){
            shaders.build();
        }
        if(width <= 2f || height <= 2f || !shaders.isReady() || !ensureSpaceScene(system)){
            return null;
        }

        int w = Math.max(1, (int)width);
        int h = Math.max(1, (int)height);

        gridAlpha = 0f;
        sectorAlpha = 0f;
        cloudAlpha = 1f;
        atmosphereAlpha = 1f;

        resizeBuffer(w, h);
        camera.updateFree(w, h, camX, camY, camZ, yaw, pitch, 58f);

        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        time += delta;
        cloudRotation += delta;
        advanceSpaceAngles(delta);
        updateSpacePositions();

        fillParams(0f, 0f, width, height, spaceSystem == null ? null : spaceSystem.primary(), null);
        params.light.set(spaceLight);

        boolean drawing = Graphics.drawing();

        if(drawing){
            Graphics.end();
        }

        try{
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            renderSpaceScene(w, h);
        }finally{
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            if(drawing){
                Graphics.begin();
            }
        }

        return buffer.getColorBufferTexture();
    }

    private void renderSpaceScene(int w, int h){
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
            for(SpaceBody body : spaceBodies){
                if(!body.sun) body.surface.render(params, body.transform);
            }
            for(SpaceBody body : spaceBodies){
                if(body.sun) body.surface.render(params, body.transform);
            }

            //sun bloom shells
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            Gdx.gl.glDepthMask(false);
            for(SpaceBody body : spaceBodies){
                if(body.sun && body.surface instanceof SunMesh){
                    ((SunMesh)body.surface).renderGlows(params, body.transform);
                }
            }
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            Gdx.gl.glDepthMask(false);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            for(SpaceOrbit orbit : spaceOrbits){
                SpaceBody parent = spaceBodyMap.get(orbit.parent);
                if(parent == null) continue;
                orbitTransform.setToTranslation(parent.position);
                orbit.mesh.render(params, orbitTransform);
            }

            //clouds, then the atmosphere haze over them
            for(SpaceBody body : spaceBodies){
                if(body.clouds.length > 0){
                    Gdx.gl.glEnable(GL20.GL_CULL_FACE);
                    Gdx.gl.glCullFace(GL20.GL_BACK);
                    Gdx.gl.glDepthMask(false);
                    for(CloudMesh layer : body.clouds){
                        layer.render(params, body.transform);
                    }
                    Gdx.gl.glDisable(GL20.GL_CULL_FACE);
                }

                if(body.atmosphere != null){
                    Gdx.gl.glDepthMask(false);
                    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                    body.atmosphere.render(params, body.transform);
                    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                }
            }

            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        }finally{
            buffer.end();
        }
    }

    private boolean renderSpaceBloom(int width, int height){
        if(bloom == null || !bloom.isReady() || emissionBuffer == null) return false;

        emissionBuffer.begin();
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glCullFace(GL20.GL_BACK);

        //depth-only occluders; suns are excluded so they can still be drawn additively below
        Gdx.gl.glColorMask(false, false, false, false);
        for(SpaceBody body : spaceBodies){
            if(!body.sun) body.surface.render(params, body.transform);
        }
        Gdx.gl.glColorMask(true, true, true, true);

        //lava emission into the bloom source
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        for(SpaceBody body : spaceBodies){
            if(body.emissiveSurface != null){
                body.emissiveSurface.renderEmissive(params, body.transform, shaders.emissive);
            }
        }
        Gdx.gl.glDepthFunc(GL20.GL_LESS);

        //sun surface additive glow
        for(SpaceBody body : spaceBodies){
            if(body.sun) body.surface.render(params, body.transform);
        }
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

    private Texture renderTexture(float width, float height, PlanetDefinition planet, boolean attached,
                                  float lon, float lat, float zoom,
                                  float camX, float camY, float camZ, float yaw, float pitch){
        postProcessing = true;
        if(!shaders.isReady()){
            shaders.build();
        }
        if(width <= 2f || height <= 2f || planet == null || !shaders.isReady()){
            return null;
        }
        if(currentPlanet != planet || model == null || world.sectors.version() != sectorVersion){
            rebuildPlanetModels(planet);
        }
        if(model == null){
            return null;
        }

        int w = Math.max(1, (int)width);
        int h = Math.max(1, (int)height);
        resizeBuffer(w, h);

        if(attached){
            updateZoomVisibility(zoom);
            camera.update(w, h, lon, lat, zoom);
        }else{
            gridAlpha = 0f;
            sectorAlpha = 0f;
            cloudAlpha = 1f;
            atmosphereAlpha = 1f;
            camera.updateFree(w, h, camX, camY, camZ, yaw, pitch, 58f);
        }

        time += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        cloudRotation += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        fillParams(0f, 0f, width, height, planet, null);

        boolean drawing = Graphics.drawing();

        if(drawing){
            Graphics.end();
        }

        try{
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            renderScene(w, h);
        }finally{
            Gdx.gl.glDisable(GL20.GL_CULL_FACE);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            if(drawing){
                Graphics.begin();
            }
        }

        return buffer.getColorBufferTexture();
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
        params.cloudDelta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        params.cloudSpeedMultiplier = planet == null ? 1f : planet.cloudRotationMultiplier;
        params.gridAlpha = gridAlpha;
        params.sectorAlpha = sectorAlpha;
        params.cloudAlpha = cloudAlpha;
        params.atmosphereAlpha = atmosphereAlpha;
        params.projection.set(camera.cam.combined);
        params.light.set(model != null ? model.system.light : spaceModel == null ? Vector3.Zero : spaceModel.system.light);
        params.camPos.set(camera.cam.position);
        params.camDir.set(camera.cam.direction);

        int jitter = postProcessing ? Settings.getInt("planetps1jitter", 0) : 0;
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
            for(int i = 0; i < model.orbits.length; i++){
                Vector3 parentPos = model.orbitParents[i] == null ? null : model.system.positionOf(model.orbitParents[i]);
                orbitTransform.setToTranslation(parentPos == null ? Vector3.Zero : parentPos);
                model.orbits[i].render(params, orbitTransform);
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
        PlanetVisual visual;
        if(model != null){
            visual = model.system.sun.visual;
        }else if(spaceSystem != null && spaceSystem.sun() != null){
            visual = spaceSystem.sun().visual;
        }else if(spaceModel != null){
            visual = Planets.sun == null ? spaceModel.planet.visual : Planets.sun.visual;
        }else{
            visual = currentPlanet == null ? null : currentPlanet.visual;
        }
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

    private void disposeSpaceScene(){
        if(spaceModel != null){
            spaceModel.dispose();
            spaceModel = null;
        }

        //spaceModel owns the primary terrain/clouds/atmosphere; only dispose standalone meshes here
        for(SpaceBody body : spaceBodies){
            if(body.surface instanceof BodyMesh || body.surface instanceof SunMesh){
                body.surface.dispose();
            }
        }

        spaceBodies.clear();
        spaceBodyMap.clear();
        spaceSun = null;

        for(SpaceOrbit orbit : spaceOrbits){
            orbit.mesh.dispose();
        }
        spaceOrbits.clear();
        spaceSystem = null;
        spaceSectorVersion = -1;
    }

    @Override
    public void dispose(){
        disposePlanet();
        disposeSpaceScene();
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
