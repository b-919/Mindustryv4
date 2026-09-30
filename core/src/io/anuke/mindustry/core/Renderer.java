package io.anuke.mindustry.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.TimeUtils;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.bullet.BulletType;
import io.anuke.mindustry.entities.effect.GroundEffectEntity;
import io.anuke.mindustry.entities.effect.GroundEffectEntity.GroundEffect;
import io.anuke.mindustry.entities.effect.Lightning;
import io.anuke.mindustry.entities.effect.Puddle;
import io.anuke.mindustry.entities.traits.BelowLiquidTrait;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.entities.traits.MinerTrait;
import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.graphics.*;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.production.*;
import io.anuke.ucore.util.Tmp;
import io.anuke.mindustry.world.blocks.defense.ForceProjector.ShieldEntity;
import io.anuke.ucore.core.*;
import io.anuke.ucore.entities.EntityDraw;
import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.entities.impl.EffectEntity;
import io.anuke.ucore.entities.trait.DrawTrait;
import io.anuke.ucore.entities.trait.PosTrait;
import io.anuke.ucore.entities.trait.Entity;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.function.Predicate;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Hue;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.graphics.Surface;
import io.anuke.ucore.modules.RendererModule;
import io.anuke.ucore.scene.utils.Cursors;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Pooling;
import io.anuke.ucore.util.Translator;

import static io.anuke.mindustry.Vars.*;
import static io.anuke.ucore.core.Core.batch;
import static io.anuke.ucore.core.Core.camera;

public class Renderer extends RendererModule{
    public final Surface effectSurface;
    public final Surface lightSurface;
    /** Off-screen buffer holding vertically mirrored, tinted copies of blocks and units, composited onto water. */
    public final Surface reflectSurface;
    /** Emission buffer for selective bloom: only bloom-contributing objects are drawn here. */
    public final Surface bloomSurface;
    public final BlockRenderer blocks = new BlockRenderer();
    public final MinimapRenderer minimap = new MinimapRenderer();
    public final OverlayRenderer overlays = new OverlayRenderer();
    public final FogRenderer fog = new FogRenderer();
    public final WeatherRenderer weather = new WeatherRenderer();

    private Bloom bloom;
    private boolean lastBloom;

    /**
     * Target camera scale (pixels per world unit) that the player has asked for, and the scale
     * that is actually used for rendering. The two are lerped toward each other every frame, which
     * is what makes zooming smooth. Both are floats: fractional scale means smooth zooming at any
     * resolution instead of snapping between integers.
     */
    public float targetscale = baseCameraScale, camerascale = baseCameraScale;
    /** Most zoomed-in limit, and most zoomed-out limit, before the settings multipliers. */
    private final float minZoom = baseCameraScale * 0.5f, maxZoom = baseCameraScale * 1.25f;
    /** Smallest cursor lead the camera will ever use, in world units. */
    private static final float minAimRange = 4f * tilesize;
    /** How much of the visible height the cursor lead may grow to when zoomed way out. */
    private static final float aimRangeFactor = 0.25f;
    /** When true the camera is controlled by hand and does not follow the player. Set every frame
     *  by the input handler, which is the only thing that knows if the player is panning. */
    public boolean detached = false;
    /** Screen shake offset applied around the draw call so it never accumulates into the position. */
    private final Vector2 camShakeOffset = new Vector2();
    /** How strong the shake reads, relative to the intensity the shake was triggered with. */
    private static final float shakeScale = 0.75f;
    /** How fast the current shake's intensity decays, so that it always reaches 0 as time runs out. */
    private float shakeReduction, lastShakeDuration;
    private Rectangle rect = new Rectangle(), rect2 = new Rectangle();
    private Vector2 avgPosition = new Translator();
    private Color ambient = new Color();

    /**
     * Water reflection sprites re-drawn mirrored, then washed toward the tint configured on
     * Shaders.water during composite. When true, unit draw() calls skip internally drawn shadows so
     * reflections stay clean.
     */
    public static boolean captureReflections = false;
    private static final float reflectionGroundGap = 3f;
    private static final float reflectionFlyerGap = 10f;

    /** Public accessor for the ground reflection gap, used by blocks that need to
     *  override their transform during capture. */
    public static float reflectionGroundGap(){ return reflectionGroundGap; }
    /** Caps error logging from the reflection capture pass so a persistently broken
     * draw() can't flood the console every frame. */
    private int reflectErrors;
    private boolean loggedReflectCounts;

    private boolean currentFlying;
    private Team currentTeam;
    private final Predicate<BaseUnit> unitFlyingFilter = u -> (u.isFlying() || u.highAltitude) == currentFlying && !u.isDead();
    private final Predicate<BaseUnit> unitFlyingTeamFilter = u -> (u.isFlying() || u.highAltitude) == currentFlying && u.getTeam() == currentTeam;
    private final Predicate<Player> playerFlyingFilter = p -> (p.isFlying() || p.highAltitude) == currentFlying && p.getTeam() == currentTeam;

    /** How far (in tiles) beyond the screen lights are still drawn, so big lights don't pop in/out at the edges.
     * Kept slightly above the largest light radius in the game (the fusion shockwave), while lights further away
     * are culled per-light against the visible area below. */ // hehe optimizations hehe hehe i hate java
    private static final int lightMargin = 48;
    /** Light radius of a player, used to cull player lights. */
    private static final float playerLightRadius = 140f;
    /** Default light radius of units, used to cull unit lights. */
    private static final float unitLightRadius = 60f;
    /** Visible area in world units, used to cull lights that can't be seen. */
    private final Rectangle lightRect = new Rectangle();
    /** Last applied render scale setting, used to detect changes and rebuild the surfaces. */
    private int lastRenderScale;
    /** Last applied surface scale, which also changes as the camera zooms. */
    private int lastSurfaceScale = -1;

    public Renderer(){
        Core.batch = new SpriteBatch(4096);

        Lines.setCircleVertices(14);

        Shaders.init();

        //the float scale is the source of truth, so the int mirror starts at its rounded value
        Core.cameraScale = Math.max(1, Math.round(baseCameraScale));
        Effects.setEffectProvider((effect, color, x, y, rotation, data) -> {
            if(effect == Fx.none) return;
            if(Settings.getBool("effects")){
                Rectangle view = rect.setSize(camera.viewportWidth, camera.viewportHeight)
                        .setCenter(camera.position.x, camera.position.y);
                Rectangle pos = rect2.setSize(effect.size).setCenter(x, y);

                if(view.overlaps(pos)){

                    if(!(effect instanceof GroundEffect)){
                        EffectEntity entity = Pooling.obtain(EffectEntity.class, EffectEntity::new);
                        entity.effect = effect;
                        entity.color = color;
                        entity.rotation = rotation;
                        entity.data = data;
                        if(data instanceof BlockFx.SmokeData){
                            BlockFx.SmokeData smoke = (BlockFx.SmokeData) data;
                            entity.lifetime = smoke.pathLength() / smoke.speed + smoke.fade;
                        }
                        entity.id++;
                        entity.set(x, y);
                        if(data instanceof Entity){
                            entity.setParent((Entity) data);
                        }
                        threads.runGraphics(() -> effectGroup.add(entity));
                    }else{
                        GroundEffectEntity entity = Pooling.obtain(GroundEffectEntity.class, GroundEffectEntity::new);
                        entity.effect = effect;
                        entity.color = color;
                        entity.rotation = rotation;
                        entity.id++;
                        entity.data = data;
                        entity.set(x, y);
                        if(data instanceof Entity){
                            entity.setParent((Entity) data);
                        }
                        threads.runGraphics(() -> groundEffectGroup.add(entity));
                    }
                }
            }
        });

        Cursors.cursorScaling = 3;
        Cursors.outlineColor = Color.valueOf("444444");

        Cursors.arrow = Cursors.loadCursor("cursor");
        Cursors.hand = Cursors.loadCursor("hand");
        Cursors.ibeam = Cursors.loadCursor("ibar");
        Cursors.restoreCursor();
        Cursors.loadCustom("drill");
        Cursors.loadCustom("unload");

        clearColor = new Color(0f, 0f, 0f, 1f);

        Settings.defaults("renderer", 100);
        Settings.defaults("fogofwar", true);
        lastRenderScale = Settings.getInt("renderer", 100);

        effectSurface = Graphics.createSurface(renderScale());
        pixelSurface = Graphics.createSurface(renderScale());
        lightSurface = Graphics.createSurface(renderScale());
        reflectSurface = Graphics.createSurface(renderScale());
        bloomSurface = Graphics.createSurface(renderScale());

        //the surfaces were just built at this scale, so the first update must not rebuild them
        lastSurfaceScale = renderScale();

        Settings.defaults("bloom", true);
        Settings.defaults("bloomintensity", 10);
        Settings.defaults("bloomblur", 2);
        Settings.defaults("bloomthreshold", 15);

        Settings.defaults("showweather", true);

        lastBloom = Settings.getBool("bloom");

        rebuildPost();
    }

    @Override
    public void init(){
    }

    @Override
    public void update(){
        //TODO hack, find source of this bug
        Color.WHITE.set(1f, 1f, 1f, 1f);

        checkPostSettings();
        checkRendererSettings();

        updateScale();
        updateCameraViewport();
        Lod.update();

        if(state.is(State.menu)){
            Graphics.clear(Color.BLACK);
        }else{
            Vector2 position = averagePosition();

            if(Float.isNaN(camera.position.x) || Float.isNaN(camera.position.y) || Float.isInfinite(camera.position.x) || Float.isInfinite(camera.position.y)){
                //a NaN position would silently break every bounds check for the rest of the session
                camera.position.set(players[0].x, players[0].y, 0f);
            }

            //while the camera is detached the input handler owns the position, so following the
            //player here would immediately undo every pan
            if(!detached){
                if(players[0].isDead()){
                    TileEntity core = players[0].getClosestCore();
                    if(core != null && players[0].spawner == -1){
                        smoothCamera(core.x, core.y, 0.08f);
                    }else{
                        smoothCamera(position.x + 0.0001f, position.y + 0.0001f, 0.08f);
                    }
                }else if(!mobile){
                    //the offset avoids float equality drift when the position is snapped to a tile
                    followCamera(position.x + 0.0001f, position.y + 0.0001f);
                }
            }

            if(!world.isOpenWorld()){
                //in open world the position is unbounded, negative coordinates are valid
                clampCamera(0f, 0f, world.width() * tilesize, world.height() * tilesize);
            }

            //shake is applied as an offset that is removed again after drawing, so it can never
            //drift the real camera position
            updateShakeOffset();
            camera.position.x += camShakeOffset.x;
            camera.position.y += camShakeOffset.y;

            try{
                if(snapCamera){
                    camera.position.set((int) camera.position.x, (int) camera.position.y, 0);
                }

                draw();
            }finally{
                //a frame that throws must not leave the offset baked into the camera position
                camera.position.x -= camShakeOffset.x;
                camera.position.y -= camShakeOffset.y;
            }
        }

        if(!ui.chatfrag.chatOpen()){
            renderer.record(); //this only does something if GdxGifRecorder is on the class path, which it usually isn't
        }
    }

    /**
     * Lerps the actual camera scale toward the target set by the player. The scale is applied to
     * the render surfaces and to the mirror of it in {@link Core#cameraScale}, but only when it
     * crosses a whole step, so a smooth zoom doesn't reallocate a framebuffer every frame.
     */
    private void updateScale(){
        float dest = Mathf.clamp(targetscale, minScale(), maxScale());
        if(dest != targetscale){
            targetscale = dest;
        }

        camerascale = Mathf.lerpDelta(camerascale, dest, 0.1f);
        if(Mathf.in(camerascale, dest, 0.001f)){
            camerascale = dest;
        }

        //the surface scale is fractional too, so it changes with the zoom even though the mirrored
        //int does not; both are handled together in the settings check below
        int scale = Math.max(1, Math.round(camerascale));
        if(scale != Core.cameraScale){
            Core.cameraScale = scale;
            for(Player player : players){
                control.input(player.playerIndex).resetCursor();
            }
        }
    }

    /** Sets the camera's visible area from the current scale.*/
    private void updateCameraViewport(){
        camera.zoom = 1f;

        float viewWidth = Gdx.graphics.getWidth() / camerascale;
        float viewHeight = Gdx.graphics.getHeight() / camerascale;

        if(!Mathf.in(camera.viewportWidth, viewWidth, 0.01f) || !Mathf.in(camera.viewportHeight, viewHeight, 0.01f)){
            camera.viewportWidth = viewWidth;
            camera.viewportHeight = viewHeight;
        }
    }

    /** Smoothly (or instantly) moves the camera toward a target.*/
    private void followCamera(float x, float y){
        if(Settings.getBool("smoothcamera")){
            float limit = aimRange();
            float ax = Mathf.clamp((Gdx.input.getX() - Gdx.graphics.getWidth() / 2f) / camerascale, -limit, limit) * 0.5f;
            float ay = Mathf.clamp(-(Gdx.input.getY() - Gdx.graphics.getHeight() / 2f) / camerascale, -limit, limit) * 0.5f;
            x += ax;
            y += ay;

            camera.position.x = Mathf.lerpDelta(camera.position.x, x, 0.08f);
            camera.position.y = Mathf.lerpDelta(camera.position.y, y, 0.08f);
        }else{
            camera.position.set(x, y, 0f);
        }
    }

    /** How far, in world units, the smooth camera may lean toward the cursor */
    private float aimRange(){
        return Math.max(minAimRange, camera.viewportHeight * aimRangeFactor);
    }

    /**
     * Screen shake. The intensity decays at a rate derived from the shake's own duration, so a
     * long shake fades out smoothly instead of being cut off, and the resulting offset is stored
     * rather than added to the position directly.
     */
    private void updateShakeOffset(){
        if(shaketime <= 0f){
            shakeIntensity = 0f;
            shakeReduction = 0f;
            lastShakeDuration = 0f;
            camShakeOffset.setZero();
            return;
        }
        if(lastShakeDuration <= 0f || shaketime > lastShakeDuration){
            lastShakeDuration = shaketime;
            shakeReduction = shakeIntensity / Math.max(shaketime, 0.0001f);
        }

        float intensity = shakeIntensity * (Settings.getInt("screenshake", 4) / 4f) * shakeScale;
        camShakeOffset.set(Mathf.range(intensity), Mathf.range(intensity));

        shakeIntensity = Mathf.clamp(shakeIntensity - shakeReduction * Timers.delta(), 0f, 100f);
        shaketime -= Timers.delta();

        if(shaketime <= 0f){
            shaketime = 0f;
            shakeIntensity = 0f;
            shakeReduction = 0f;
            lastShakeDuration = 0f;
        }
    }

    @Override
    public void draw(){
        PerfCounter.render.begin();

        camera.update();
        if(Float.isNaN(Core.camera.position.x) || Float.isNaN(Core.camera.position.y)){
            Core.camera.position.set(players[0].x, players[0].y, 0f);
        }

        if(bloom != null){
            bloom.setBloomIntensity(Settings.getInt("bloomintensity") / 10f);
            bloom.setThreshold(Settings.getInt("bloomthreshold") / 100f);
            bloom.blurPasses = Settings.getInt("bloomblur");
        }

        Graphics.clear(clearColor);

        batch.setProjectionMatrix(camera.combined);

        Graphics.surface(pixelSurface, false);

        Graphics.clear(clearColor);

        blocks.drawFloor();

        weather.drawUnder();

        drawAndInterpolate(groundEffectGroup, e -> e instanceof BelowLiquidTrait);
        drawAndInterpolate(puddleGroup);
        drawAndInterpolate(groundEffectGroup, e -> !(e instanceof BelowLiquidTrait));
        control.input(0).drawUnderUnitsAndBlocks();

        blocks.processBlocks();
        blocks.drawShadows();
        for(Team team : Team.all){
            if(blocks.isTeamShown(team)){
                boolean outline = team != players[0].getTeam() && team != Team.none;

                if(outline){
                    Shaders.outline.color.set(team.color);
                    Shaders.outline.color.a = 0.8f;
                    Graphics.beginShaders(Shaders.outline);
                }

                blocks.drawTeamBlocks(Layer.block, team);

                if(outline){
                    Graphics.endShaders();
                }
            }
        }
        blocks.skipLayer(Layer.block);

        Graphics.shader(Shaders.blockbuild, false);
        blocks.drawBlocks(Layer.placement);
        Graphics.shader();

        blocks.drawBlocks(Layer.overlay);

        drawAllTeams(false);

        blocks.skipLayer(Layer.turret);
        blocks.drawBlocks(Layer.tree);
        blocks.drawBlocks(Layer.laser);

        drawFlyerShadows();

        drawAllTeams(true);

        drawAndInterpolate(bulletGroup);
        drawAndInterpolate(effectGroup);

        overlays.drawBottom();
        drawAndInterpolate(playerGroup, p -> true, Player::drawBuildRequests);

        Graphics.beginShaders(Shaders.shield);
        shieldGroup.forEach(s -> {
            Shaders.shield.teamColor.set(s.getTeam().color);
            Shaders.shield.apply();
            s.draw();
            batch.flush();
        });
        shieldGroup.forEach(s -> {
            Shaders.shield.teamColor.set(s.getTeam().color);
            Shaders.shield.apply();
            s.drawOver();
            batch.flush();
        });
        Graphics.endShaders();
        Draw.color();

        overlays.drawTop();

        drawReflections();

        // render bloom sources into the emission buffer for selective bloom
        if(bloom != null && Settings.getBool("bloom")){
            drawBloomSources();
        }

        boolean postActive = bloom != null && Settings.getBool("bloom");

        if((weather.isDayNight() ? weather.cycleDarkness() : state.darkness) > 0.01f){
            drawLights();
            drawLightmap();
            Graphics.surface();
            batch.end();
            // fog must run before bloom so pixelSurface contains the fogged scene
            if(showFog) fog.draw();
            if(postActive){
                drawPost();
            }else if(!showFog){
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                batch.setProjectionMatrix(camera.combined);
                batch.begin();
                blitPixelSurface();
                batch.end();
            }
        }else if(showFog){
            Graphics.surface();
            batch.end();
            fog.draw();
            if(postActive) drawPost();
        }else if(postActive){
            Graphics.surface();
            batch.end();
            drawPost();
        }else{
            Graphics.flushSurface();
            batch.end();
        }

        Graphics.beginCam();
        EntityDraw.setClip(false);
        weather.drawOver();
        drawAndInterpolate(playerGroup, p -> !p.isDead() && !p.isLocal, Player::drawName);
        EntityDraw.setClip(true);
        Graphics.end();
        Draw.color();

        PerfCounter.render.end();
    }

    public void drawLights(){
        //fill the lightmap with the ambient light level. light sources get added on top
        float darkness = weather.isDayNight() ? weather.cycleDarkness() : state.darkness;
        if(weather.isDayNight()){
            darkness += Mathf.random(-1f, 1f) * 0.002f;
        }
        float light = Math.max(0f, Math.min(1f, 1f - darkness));
        ambient.set(light, light, light, 1f);

        Graphics.surface(lightSurface, false, true);
        Graphics.clear(ambient);
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        //draw lights over a wider area than the visible blocks
        int avgx = Mathf.scl(camera.position.x, tilesize);
        int avgy = Mathf.scl(camera.position.y, tilesize);
        int rangex = (int)(camera.viewportWidth / tilesize / 2) + 2;
        int rangey = (int)(camera.viewportHeight / tilesize / 2) + 2;

        int minx, miny, maxx, maxy;
        if(world.isOpenWorld()){
            minx = avgx - rangex - lightMargin;
            miny = avgy - rangey - lightMargin;
            maxx = avgx + rangex + lightMargin;
            maxy = avgy + rangey + lightMargin;
        }else{
            minx = Math.max(avgx - rangex - lightMargin, 0);
            miny = Math.max(avgy - rangey - lightMargin, 0);
            maxx = Math.min(world.width() - 1, avgx + rangex + lightMargin);
            maxy = Math.min(world.height() - 1, avgy + rangey + lightMargin);
        }

        lightRect.set(camera.position.x - camera.viewportWidth / 2f,
                camera.position.y - camera.viewportHeight / 2f,
                camera.viewportWidth, camera.viewportHeight);

        Shaders.light.type = 0;
        Graphics.shader(Shaders.light);

        //Blocks
        for(int x = minx; x <= maxx; x++){
            for(int y = miny; y <= maxy; y++){
                Tile tile = world.peekTile(x, y);
                if(tile != null && tile.block() != Blocks.air){
                    Block block = tile.block();
                    float radius = Math.max(block.lightRadius() * tilesize, block.layerLightRadius * tilesize) + tilesize * 2f;
                    if(lightVisible(tile.drawx(), tile.drawy(), radius)){
                        block.drawLight(tile);
                        block.drawLayerLight(tile);
                    }
                }
                // liquid floor light emission (lava, slag, cryofluid)
                if(tile != null && tile.block() == Blocks.air
                    && tile.floor().liquidDrop != null && tile.floor().liquidDrop.emitLight
                    && lightVisible(tile.drawx(), tile.drawy(), tilesize * 3f)){
                    Draw.color(tile.floor().liquidDrop.color);
                    Shaders.light.region = Draw.region("circle");
                    Draw.alpha(0.35f);
                    Draw.rect("circle", tile.drawx(), tile.drawy(), tilesize * 4f, tilesize * 4f);
                    Draw.alpha(0.18f);
                    Draw.rect("circle", tile.drawx(), tile.drawy(), tilesize * 4f, tilesize * 4f);
                }
            }
        }

        //Bullets
        for(Entity entity : bulletGroup.all()){
            if(entity instanceof Bullet){
                Bullet bullet = (Bullet) entity;
                BulletType type = bullet.getBulletType();
                if(lightVisible(bullet.x, bullet.y, Math.max(type.lightRadius, bullet.lightRadius))){
                    type.drawLight(bullet);
                }
            }else if(entity instanceof Lightning){
                Lightning l = (Lightning) entity;
                float fade = l.fout();
                if(fade > 0.01f && lightVisible(l.x, l.y, 30f * fade)){
                    Draw.color(l.color);
                    Shaders.light.region = Draw.region("circle");
                    Draw.alpha(fade * 0.5f);
                    Draw.rect("circle", l.x, l.y, 60f * fade, 60f * fade);
                    Draw.alpha(fade * 0.25f);
                    Draw.rect("circle", l.x, l.y, 60f * fade, 60f * fade);
                }
            }
        }
        //Units
        for(EntityGroup<? extends BaseUnit> group : unitGroups){
            for(BaseUnit unit : group.all()){
                if(!unit.isDead() && lightVisible(unit.x, unit.y, Math.max(unit.lightRadius, unitLightRadius))){
                    unit.drawLight();
                }
            }
        }

        //Players
        for(Player player : playerGroup.all()){
            if(!player.isDead() && lightVisible(player.x, player.y, playerLightRadius)){
                player.drawLight();
            }
        }

        //mining lasers
        for(EntityGroup<? extends BaseUnit> group : unitGroups){
            for(BaseUnit unit : group.all()){
                if(unit.isDead()) continue;
                if(unit instanceof BuilderTrait){
                    BuilderTrait builder = (BuilderTrait) unit;
                    Tile mineTile = builder.getMineTile();
                    if(mineTile != null){
                        float tx = mineTile.worldx(), ty = mineTile.worldy();
                        if(lightVisible(tx, ty, 12f)){
                            Draw.color(Palette.accent);
                            Shaders.light.region = Draw.region("circle");
                            Draw.alpha(0.4f);
                            Draw.rect("circle", tx, ty, 24f, 24f);
                            Draw.alpha(0.2f);
                            Draw.rect("circle", tx, ty, 24f, 24f);
                        }
                    }
                }
            }
        }
        for(Player player : playerGroup.all()){
            if(!player.isDead() && player instanceof BuilderTrait){
                BuilderTrait builder = (BuilderTrait) player;
                Tile mineTile = builder.getMineTile();
                if(mineTile != null){
                    float tx = mineTile.worldx(), ty = mineTile.worldy();
                    if(lightVisible(tx, ty, 12f)){
                        Draw.color(Palette.accent);
                        Shaders.light.region = Draw.region("circle");
                        Draw.alpha(0.4f);
                        Draw.rect("circle", tx, ty, 24f, 24f);
                        Draw.alpha(0.2f);
                        Draw.rect("circle", tx, ty, 24f, 24f);
                    }
                }
            }
        }

        //Effects
        for(Entity entity : effectGroup.all()){
            if(entity instanceof EffectEntity){
                drawEffectLight((EffectEntity) entity);
            }
        }
        for(DrawTrait entity : groundEffectGroup.all()){
            if(entity instanceof EffectEntity){
                drawEffectLight((EffectEntity) entity);
            }
        }

        //puddles with light-emitting liquids
        for(Entity entity : puddleGroup.all()){
            if(entity instanceof Puddle){
                Puddle p = (Puddle) entity;
                if(p.getLiquid() != null && p.getLiquid().emitLight && lightVisible(p.x, p.y, 14f)){
                    float f = Mathf.clamp(p.getAmount() / 46f);
                    if(f > 0.01f){
                        Draw.color(p.getLiquid().color);
                        Shaders.light.region = Draw.region("circle");
                        Draw.alpha(f * 0.3f);
                        Draw.rect("circle", p.x, p.y, f * 28f, f * 28f);
                        Draw.alpha(f * 0.15f);
                        Draw.rect("circle", p.x, p.y, f * 28f, f * 28f);
                    }
                }
            }
        }

        Graphics.shader();
        Draw.color();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Graphics.surface();
    }

    /** Whether a light at (x, y) with the given radius (in world units) overlaps the visible area. */
    private boolean lightVisible(float x, float y, float radius){
        return x + radius >= lightRect.x && x - radius <= lightRect.x + lightRect.width &&
                y + radius >= lightRect.y && y - radius <= lightRect.y + lightRect.height;
    }

    private void drawEffectLight(EffectEntity entity){
        Effects.Effect effect = entity.effect;
        if(effect == null) return;

        boolean emit = entity.emitLight != null ? entity.emitLight : effect.emitLight;
        float radius = entity.lightRadius < 0 ? effect.lightRadius : entity.lightRadius;
        float opacity = entity.lightOpacity < 0 ? effect.lightOpacity : entity.lightOpacity;
        Color color = entity.lightColor != null ? entity.lightColor : effect.lightColor;

        if(!emit) return;

        float fin = entity.fin();
        float fade;
        if(entity instanceof GroundEffectEntity && ((GroundEffect) effect).isStatic){
            fade = 1f;
        }else{
            fade = Mathf.clamp(fin < 0.2f ? fin / 0.2f : (1f - fin) / 0.8f, 0f, 1f);
        }

        radius *= fade;
        opacity *= fade;

        if(radius > 0.001f && opacity > 0.001f){
            if(!lightVisible(entity.x, entity.y, radius)) return;
            Draw.color(color);
            Shaders.light.region = Draw.region("circle");
            Draw.alpha(opacity);
            Draw.rect("circle", entity.x, entity.y, radius * 2, radius * 2);
            Draw.alpha(opacity * 0.5f);
            Draw.rect("circle", entity.x, entity.y, radius * 2, radius * 2);
        }
    }

    /** Renders bloom-contributing objects (additive glow, effects, bullets, shields, unit flares,
     *  block bloom parts, liquid glow) into {@code bloomSurface} for the selective bloom pass. */
    private void drawBloomSources(){
        Graphics.surface(bloomSurface, false, false);
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Graphics.shader();

        int avgx = Mathf.scl(camera.position.x, tilesize);
        int avgy = Mathf.scl(camera.position.y, tilesize);
        int rangex = (int)(camera.viewportWidth / tilesize / 2) + 2;
        int rangey = (int)(camera.viewportHeight / tilesize / 2) + 2;

        int minx, miny, maxx, maxy;
        if(world.isOpenWorld()){
            minx = avgx - rangex;
            miny = avgy - rangey;
            maxx = avgx + rangex;
            maxy = avgy + rangey;
        }else{
            minx = Math.max(avgx - rangex, 0);
            miny = Math.max(avgy - rangey, 0);
            maxx = Math.min(world.width() - 1, avgx + rangex);
            maxy = Math.min(world.height() - 1, avgy + rangey);
        }

        // block bloom + liquid floor glow (single tile loop)
        for(int x = minx; x <= maxx; x++){
            for(int y = miny; y <= maxy; y++){
                Tile tile = world.peekTile(x, y);
                if(tile == null) continue;

                Block block = tile.block();
                if(block.hasBloom){
                    block.drawBloom(tile);
                }

                // liquid floor glow
                if(block == Blocks.air && tile.floor().liquidDrop != null && tile.floor().liquidDrop.emitLight){
                    Draw.color(tile.floor().liquidDrop.color);
                    Draw.alpha(0.15f);
                    Draw.rect("circle", tile.drawx(), tile.drawy(), tilesize * 3f, tilesize * 3f);
                    Draw.color();
                }
            }
        }

        // effects that emit light (merged effectGroup + groundEffectGroup)
        for(Entity entity : effectGroup.all()){
            if(entity instanceof EffectEntity){
                drawEffectBloom((EffectEntity) entity);
            }
        }
        for(DrawTrait entity : groundEffectGroup.all()){
            if(entity instanceof EffectEntity){
                drawEffectBloom((EffectEntity) entity);
            }
        }

        // bullets with bloom enabled
        for(Entity entity : bulletGroup.all()){
            if(entity instanceof Bullet){
                Bullet bullet = (Bullet) entity;
                BulletType type = bullet.getBulletType();
                if(type.bloom){
                    type.drawBloom(bullet);
                }
            }else if(entity instanceof Lightning){
                Lightning l = (Lightning) entity;
                float fade = l.fout();
                if(fade > 0.01f){
                    Draw.color(l.color, Color.WHITE, fade);
                    Lines.stroke(fade * 6f);
                    Draw.alpha(0.4f);
                    float lx = l.x, ly = l.y;
                    for(int i = 0; i < l.lines.size; i++){
                        PosTrait v = l.lines.get(i);
                        Lines.line(lx, ly, v.getX(), v.getY());
                        lx = v.getX();
                        ly = v.getY();
                    }
                    Draw.reset();
                }
            }
        }

        // units mining laser + trail (merged into single iteration)
        for(EntityGroup<? extends BaseUnit> group : unitGroups){
            for(BaseUnit unit : group.all()){
                if(unit.isDead()) continue;

                // mining laser bloom
                if(unit instanceof MinerTrait){
                    MinerTrait miner = (MinerTrait) unit;
                    if(miner.isMining()){
                        miner.drawMining(unit);
                    }
                }
                if(unit instanceof BuilderTrait){
                    BuilderTrait builder = (BuilderTrait) unit;
                    if(builder.getMineTile() != null){
                        builder.drawMining(unit);
                    }
                }

                // flying unit trail bloom (skip biomass)
                if(unit instanceof FlyingUnit
                    && !unit.getClass().getSimpleName().startsWith("Biomass")){
                    FlyingUnit fu = (FlyingUnit) unit;
                    if(fu.type != null){
                        fu.trail.draw(fu.type.trailColor, fu.type.engineSize);
                        if(fu.type.engineMirror){
                            fu.trail2.draw(fu.type.trailColor, fu.type.engineSize);
                        }
                    }
                }
            }
        }

        // players mining + trail + mech bloom
        for(Player player : playerGroup.all()){
            if(player.isDead()) continue;

            // mining laser bloom
            if(player instanceof BuilderTrait){
                BuilderTrait builder = (BuilderTrait) player;
                if(builder.getMineTile() != null){
                    builder.drawMining(player);
                }
            }

            // trail bloom
            if(player.mech.flying || player.boostHeat > 0.001f){
                player.trail.draw(
                    Hue.mix(player.mech.trailColor, player.mech.trailColorTo, player.mech.flying ? 0f : player.boostHeat, Tmp.c1),
                    5f * (player.isFlying() ? 1f : player.boostHeat));
            }

            // mech bloom
            player.mech.drawBloom(player);
        }

        // puddle bloom for light-emitting liquids
        for(Entity entity : puddleGroup.all()){
            if(entity instanceof Puddle){
                ((Puddle) entity).drawBloom();
            }
        }

        // shield bloom
        for(ShieldEntity shield : shieldGroup.all()){
            shield.drawBloom();
        }

        // restore
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Graphics.shader();
        Graphics.surface();
    }

    private void drawEffectBloom(EffectEntity ee){
        Effects.Effect effect = ee.effect;
        if(effect != null && effect.emitLight){
            float fin = ee.fin();
            float fade = Mathf.clamp(fin < 0.2f ? fin / 0.2f : (1f - fin) / 0.8f, 0f, 1f);
            if(fade > 0.001f){
                Draw.color(ee.color);
                Draw.alpha(fade * 0.3f);
                float sz = effect.size * 0.5f * fade;
                Draw.rect("circle", ee.x, ee.y, sz, sz);
                Draw.color();
            }
        }
    }

    // multiplies the scene (pixelSurface) by the lightmap (lightSurface): scene * (ambient + light)
    private void drawLightmap(){
        batch.flush();
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, pixelSurface.width(), pixelSurface.height()));
        batch.draw(lightSurface.texture(), 0, 0, pixelSurface.width(), pixelSurface.height(), 0, 0, 1, 1);
        batch.flush();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setProjectionMatrix(camera.combined);
    }

    private void blitPixelSurface(){
        batch.draw(pixelSurface.texture(),
                camera.position.x - camera.viewportWidth / 2,
                camera.position.y + camera.viewportHeight / 2,
                camera.viewportWidth, -camera.viewportHeight);
    }

    //applies the bloom chain (threshold -> blur -> photographic combine) to the composed
    //scene and draws the result to the screen. The batch must not be drawing when this is called (crash)
    private void drawPost(){
        if(bloom == null || !bloom.isReady()){
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            blitPixelSurface();
            batch.end();
            return;
        }

        bloom.render(pixelSurface.texture(), bloomSurface.texture());

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    /** Water Reflections: re-draws visible blocks, units, bullets and effects vertically
     * mirrored around their own base into reflectSurface. The water cache layer composites this
     * buffer over water tiles (masked by reflection alpha, distorted by the same wave noise,
     * washed toward the water color sampled from the scene) next frame. Captured with the plain pipeline so sprite
     * alpha stays intact; shadows are skipped via Renderer.captureReflections.
     * Each object is drawn in isolation so a single bad draw can never corrupt the rest
     * of the capture or leave the surface/transform stack unbalanced. */
    private void drawReflections(){
        int avgx = Mathf.scl(camera.position.x, tilesize);
        int avgy = Mathf.scl(camera.position.y, tilesize);
        int rangex = (int)(camera.viewportWidth / tilesize / 2) + 2;
        int rangey = (int)(camera.viewportHeight / tilesize / 2) + 2;

        float halfW = camera.viewportWidth / 2f;
        float halfH = camera.viewportHeight / 2f;

        Graphics.surface(reflectSurface, true, false);

        Matrix4 mat = batch.getTransformMatrix();
        captureReflections = true;

        try{
            int blockCount = 0, layeredBlocks = 0;

            for(int x = avgx - rangex; x <= avgx + rangex; x++){
                for(int y = avgy - rangey; y <= avgy + rangey; y++){
                    Tile tile = world.peekTile(x, y);
                    if(tile == null || tile.block() == Blocks.air) continue;
                    blockCount++;

                    Block block = tile.block();
                    float ax = tile.drawx();
                    float ay = tile.drawy() - block.size * tilesize / 2f;
                    Draw.color();
                    float yScl = block.reflectionFlip ? -block.reflectYdisplace : block.reflectYdisplace;
                    mat.setToTranslation(ax, ay, 0f)
                       .scale(block.reflectXdisplace, yScl, 1f)
                       .translate(-ax, -ay, 0f);
                    batch.setTransformMatrix(mat);

                    try{
                        block.draw(tile);

                        if(block.layer != null && block.isLayer(tile)){
                            block.drawLayer(tile);
                            layeredBlocks++;
                        }

                        if(block.layer2 != null && block.isLayer2(tile)){
                            block.drawLayer2(tile);
                        }
                    }catch(Throwable t){
                        logReflectError(t);
                    }
                }
            }

            int units = 0, players = 0, bullets = 0, effects = 0;

            Shaders.mix.color.set(Color.WHITE);
            Graphics.shader(Shaders.mix, true);

            for(EntityGroup<? extends BaseUnit> group : unitGroups){
                for(BaseUnit unit : group.all()){
                    if(unit.isDead()) continue;
                    try{
                        float gap = unit.isFlying() ? reflectionFlyerGap : reflectionGroundGap;
                        drawReflected(unit, gap, false, halfW, halfH, mat);
                        units++;
                    }catch(Throwable t){
                        logReflectError(t);
                    }
                }
            }
            for(Player player : playerGroup.all()){
                if(player.isDead()) continue;
                try{
                    float gap;
                    if(player.isFlying() && (player.mech == null || player.mech.flying)){
                        gap = reflectionFlyerGap;
                    }else{
                        gap = reflectionGroundGap +
                            (reflectionFlyerGap - reflectionGroundGap) * player.boostHeat;
                    }
                    drawReflected(player, gap, false, halfW, halfH, mat);
                    players++;
                }catch(Throwable t){
                    logReflectError(t);
                }
            }

            Graphics.shader();
            bullets += drawReflected(bulletGroup, false, halfW, halfH, mat);
            effects += drawReflected(effectGroup, false, halfW, halfH, mat);
            effects += drawReflected(groundEffectGroup, false, halfW, halfH, mat);

            for(EntityGroup<? extends BaseUnit> group : unitGroups){
                for(BaseUnit unit : group.all()){
                    if(unit.isDead()) continue;
                    try{
                        float gap = unit.isFlying() ? reflectionFlyerGap : reflectionGroundGap;
                        drawReflectedOver(unit, gap, false, halfW, halfH, mat);
                    }catch(Throwable t){
                        logReflectError(t);
                    }
                }
            }
            for(Player player : playerGroup.all()){
                if(player.isDead()) continue;
                try{
                    float gap;
                    if(player.isFlying() && (player.mech == null || player.mech.flying)){
                        gap = reflectionFlyerGap;
                    }else{
                        gap = reflectionGroundGap +
                            (reflectionFlyerGap - reflectionGroundGap) * player.boostHeat;
                    }
                    drawReflectedOver(player, gap, false, halfW, halfH, mat);
                }catch(Throwable t){
                    logReflectError(t);
                }
            }

            if(!loggedReflectCounts){
                loggedReflectCounts = true;
                Log.info("[reflect] captured blocks={0} (layered={1}), units={2}, players={3}, bullets={4}, effects={5}",
                    blockCount, layeredBlocks, units, players, bullets, effects);
            }
        }finally{
            captureReflections = false;

            mat.idt();
            batch.setTransformMatrix(mat);

            Graphics.surface();

            Draw.color();
        }
    }

    private void logReflectError(Throwable t){
        if(reflectErrors++ < 5) Log.err(t);
    }

    private <T extends DrawTrait> int drawReflected(EntityGroup<T> group, boolean flip, float halfW, float halfH, Matrix4 mat){
        int count = 0;
        for(T entity : group.all()){
            try{
                drawReflected(entity, reflectionGroundGap, flip, halfW, halfH, mat);
                count++;
            }catch(Throwable t){
                logReflectError(t);
            }
        }
        return count;
    }

    private <T extends DrawTrait> void drawReflected(T entity, float gap, boolean flip, float halfW, float halfH, Matrix4 mat){
        if(Math.abs(entity.getX() - camera.position.x) > halfW + tilesize * 8 ||
           Math.abs(entity.getY() - camera.position.y) > halfH + tilesize * 8){
            return;
        }

        Draw.color();
        if(flip){
            mat.setToTranslation(0f, (entity.getY() - gap) * 2f, 0f).scale(1f, -1f, 1f);
        }else{
            mat.setToTranslation(0f, -2f * gap, 0f);
        }
        batch.setTransformMatrix(mat);
        entity.draw();
    }

    /** Same transform as drawReflected but calls drawOver() — used for engine trails and
     *  other over-layer content that FlyingUnit/Player render outside their main draw(). */
    private <T extends DrawTrait> void drawReflectedOver(T entity, float gap, boolean flip, float halfW, float halfH, Matrix4 mat){
        if(Math.abs(entity.getX() - camera.position.x) > halfW + tilesize * 8 ||
           Math.abs(entity.getY() - camera.position.y) > halfH + tilesize * 8){
            return;
        }

        if(!(entity instanceof BaseUnit) && !(entity instanceof Player)) return;

        Draw.color();
        if(flip){
            mat.setToTranslation(0f, (entity.getY() - gap) * 2f, 0f).scale(1f, -1f, 1f);
        }else{
            mat.setToTranslation(0f, -2f * gap, 0f);
        }
        batch.setTransformMatrix(mat);

        if(entity instanceof BaseUnit){
            ((BaseUnit)entity).drawOver();
        }else if(entity instanceof Player){
            ((Player)entity).drawOver();
        }
    }

    private void drawFlyerShadows(){
        Graphics.surface(effectSurface, true, false);

        float trnsX = -12, trnsY = -13;

        for(EntityGroup<? extends BaseUnit> group : unitGroups){
            if(!group.isEmpty()){
                drawAndInterpolate(group, unit -> unit.isFlying() && !unit.isDead(), baseUnit -> baseUnit.drawShadow(trnsX, trnsY));
            }
        }

        if(!playerGroup.isEmpty()){
            drawAndInterpolate(playerGroup, unit -> unit.isFlying() && !unit.isDead(), player -> player.drawShadow(trnsX, trnsY));
        }

        Draw.color(0, 0, 0, 0.15f);
        Graphics.flushSurface();
        Draw.color();
    }

    private void drawAllTeams(boolean flying){
        currentFlying = flying;

        for(Team team : Team.all){
            EntityGroup<BaseUnit> group = unitGroups[team.ordinal()];

            if(group.isEmpty() && playerGroup.count(p -> (p.isFlying() || p.highAltitude) == flying && p.getTeam() == team) == 0) continue;

            if(flying && group.count(p -> p.isFlying() || p.highAltitude) == 0 &&
                    playerGroup.count(p -> (p.isFlying() || p.highAltitude) && p.getTeam() == team) == 0) continue;

            currentTeam = team;

            drawAndInterpolate(unitGroups[team.ordinal()], unitFlyingFilter, Unit::drawUnder);
            drawAndInterpolate(playerGroup, playerFlyingFilter, Unit::drawUnder);

            Shaders.outline.color.set(team.color);
            Shaders.mix.color.set(Color.WHITE);

            Graphics.beginShaders(Shaders.outline);
            Graphics.shader(Shaders.mix, true);
            drawAndInterpolate(unitGroups[team.ordinal()], unitFlyingFilter, Unit::drawAll);
            drawAndInterpolate(playerGroup, playerFlyingFilter, Unit::drawAll);
            Graphics.shader();
            blocks.drawTeamBlocks(Layer.turret, team);
            Graphics.endShaders();

            drawAndInterpolate(unitGroups[team.ordinal()], unitFlyingFilter, Unit::drawOver);
            drawAndInterpolate(playerGroup, playerFlyingFilter, Unit::drawOver);
        }
    }

    public <T extends DrawTrait> void drawAndInterpolate(EntityGroup<T> group){
        drawAndInterpolate(group, t -> true, DrawTrait::draw);
    }

    public <T extends DrawTrait> void drawAndInterpolate(EntityGroup<T> group, Predicate<T> toDraw){
        drawAndInterpolate(group, toDraw, DrawTrait::draw);
    }

    public <T extends DrawTrait> void drawAndInterpolate(EntityGroup<T> group, Predicate<T> toDraw, Consumer<T> drawer){
        EntityDraw.drawWith(group, toDraw, drawer);
    }

    @Override
    public void resize(int width, int height){
        float lastX = camera.position.x, lastY = camera.position.y;
        super.resize(width, height);
        for(Player player : players){
            control.input(player.playerIndex).resetCursor();
        }
        //super.resize() derives the viewport from the integer Core.cameraScale, which loses
        //precision; re-derive it from the float scale so the view doesn't jump
        updateCameraViewport();
        camera.update();
        camera.position.set(lastX, lastY, 0f);

        effectSurface.onResize();
        pixelSurface.onResize();
        lightSurface.onResize();
        reflectSurface.onResize();
        bloomSurface.onResize();

        rebuildPost();
    }

    @Override
    public void dispose(){
        fog.dispose();
        effectSurface.dispose();
        pixelSurface.dispose();
        lightSurface.dispose();
        reflectSurface.dispose();
        bloomSurface.dispose();
        if(bloom != null) bloom.dispose();
    }

    private void checkPostSettings(){
        boolean bloomOn = Settings.getBool("bloom");
        if(bloomOn != lastBloom){
            lastBloom = bloomOn;
            rebuildPost();
        }
    }

    /** Rebuilds all surfaces to match the current camera scale and render scale setting. */
    private void applyScale(){
        for(Surface surface : Graphics.getSurfaces()){
            surface.setScale(renderScale());
        }
        //the surface scale is derived from the scale, so a zoom that crosses a whole step has to
        //rescale the surfaces; recording the step stops a smooth zoom from doing it every frame
        lastRenderScale = Settings.getInt("renderer", 100);
    }

    /** The scale factor used by the render surfaces. Larger = smaller surfaces.
     *  A render scale below 100% renders at a lower resolution and upscales to the screen, cutting GPU fill rate. */
    private int renderScale(){
        return Math.max(1, Math.round(camerascale * 100f / Math.max(Settings.getInt("renderer", 100), 1)));
    }

    /** Detects render scale changes and rebuilds the surfaces when it changes. */
    private void checkRendererSettings(){
        showFog = Settings.getBool("fogofwar");

        int rs = Settings.getInt("renderer", 100);
        if(rs != lastRenderScale || renderScale() != lastSurfaceScale){
            applyScale();
            lastRenderScale = rs;
        }
    }

    /** Rebuilds the bloom effect to match the current screen size and settings. Safe to call between frames. */
    public void rebuildPost(){
        if(bloom != null){
            bloom.dispose();
            bloom = null;
        }

        if(!Settings.getBool("bloom")) return;

        int width = Math.max(Gdx.graphics.getWidth(), 1);
        int height = Math.max(Gdx.graphics.getHeight(), 1);

        bloom = new Bloom(width, height);
        bloom.setThreshold(Settings.getInt("bloomthreshold") / 100f);
        bloom.setOriginalIntensity(1f);
        bloom.setBloomIntensity(Settings.getInt("bloomintensity") / 10f);
        bloom.blurPasses = Settings.getInt("bloomblur");
    }

    public Vector2 averagePosition(){
        avgPosition.setZero();
        int count = 0;

        for(Player player : players){
            if(player.isLocal){
                avgPosition.add(player.x, player.y);
                count++;
            }
        }

        if(count > 0){
            avgPosition.scl(1f / count);
        }
        return avgPosition;
    }

    public void scaleCamera(float amount){
        targetscale *= (amount / 4f) + 1f;
        clampScale();
    }

    public void setScale(float scale){
        targetscale = scale;
        clampScale();
    }

    public float getScale(){
        return targetscale;
    }

    public float getDisplayScale(){
        return camerascale;
    }

    public float minScale(){
        return Math.max(1f, minZoom / zoomLimit("minzoomingamemultiplier"));
    }

    public float maxScale(){
        return Math.max(minScale(), maxZoom * zoomLimit("maxzoomingamemultiplier"));
    }

    private static float zoomLimit(String name){
        return Math.max(1, Settings.getInt(name, 100)) / 100f;
    }

    public void clampScale(){
        //in open world there is no map edge to look past, so only the settings limits apply
        targetscale = Mathf.clamp(targetscale, minScale(), maxScale());
    }

    public void takeMapScreenshot(){
        int w = world.width() * tilesize, h = world.height() * tilesize;
        int memory = w * h * 4 / 1024 / 1024;

        if(memory >= (mobile ? 65 : 120)){
            ui.showInfo(Bundles.format("text.screenshot.invalid", memory));
            return;
        }

        float vpW = Core.camera.viewportWidth, vpH = Core.camera.viewportHeight;
        float px = Core.camera.position.x, py = Core.camera.position.y;
        int pw = pixelSurface.width(), ph = pixelSurface.height();
        boolean lastShowFog = showFog;
        boolean lodDisable = Lod.disable;

        showFog = false;
        disableUI = true;
        //a full-map render is the one place where all LOD detail should be drawn, no matter how small it gets
        Lod.disable = true;
        pixelSurface.setSize(w, h, true);
        Graphics.getEffectSurface().setSize(w, h, true);
        Core.camera.viewportWidth = w;
        Core.camera.viewportHeight = h;
        Core.camera.position.set(w / 2f, h / 2f, 0f);

        try{
            draw();
        }finally{
            showFog = lastShowFog;
            disableUI = false;
            Lod.disable = lodDisable;
            Core.camera.viewportWidth = vpW;
            Core.camera.viewportHeight = vpH;
            Core.camera.position.set(px, py, 0f);
        }

        pixelSurface.getBuffer().begin();
        byte[] lines = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        for(int i = 0; i < lines.length; i+= 4){
            lines[i + 3] = (byte)255;
        }
        pixelSurface.getBuffer().end();

        Pixmap fullPixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);

        BufferUtils.copy(lines, 0, fullPixmap.getPixels(), lines.length);
        FileHandle file = screenshotDirectory.child("screenshot-" + TimeUtils.millis() + ".png");
        PixmapIO.writePNG(file, fullPixmap);
        fullPixmap.dispose();

        pixelSurface.setSize(pw, ph, false);
        Graphics.getEffectSurface().setSize(pw, ph, false);

        ui.showInfoFade(Bundles.format("text.screenshot", file.toString()));
    }

}
