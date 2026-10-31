package io.anuke.mindustry.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.graphics.planet.PlanetRenderer;
import io.anuke.mindustry.maps.SpaceCamera;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.SolarSystemDefinition;
import io.anuke.mindustry.ui.dialogs.FloatingDialog;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.Element;
import io.anuke.ucore.scene.event.InputEvent;
import io.anuke.ucore.scene.event.InputListener;
import io.anuke.ucore.scene.event.Touchable;
import io.anuke.ucore.scene.style.Drawable;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.Slider;
import io.anuke.ucore.scene.ui.layout.Stack;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Strings;

import static io.anuke.mindustry.Vars.ui;

/** Editor for the 3D space/planet scene camera stored in map metadata. */
public class SpaceCameraDialog extends FloatingDialog{
    /** Set while this editor is open so the map view does not also consume WASD for panning. */
    public static boolean isOpen;

    private final MapEditor editor;
    private final PlanetRenderer renderer = new PlanetRenderer();

    private SpaceCamera camera;
    private PlanetDefinition planet;
    private final Preview preview;
    private Joystick joystick;
    private final Vector3 pose = new Vector3();
    private final Vector3 dir = new Vector3();

    public SpaceCameraDialog(MapEditor editor){
        super("Space Camera");
        this.editor = editor;
        this.preview = new Preview();
        setBackground((Drawable)null);
        margin(0);
        defaults().space(0);
        getTitleTable().setVisible(false);
        getButtonTable().setVisible(false);
        content().margin(0);

        keyDown(key -> {
            if(key == Keys.ESCAPE || key == Keys.BACK){
                Gdx.app.postRunnable(this::hide);
            }
        });

        shown(this::setup);
        hidden(() -> isOpen = false);
    }

    private void setup(){
        isOpen = true;

        camera = SpaceCamera.read(editor.getTags());
        boolean created = camera == null;
        if(created){
            camera = new SpaceCamera();
            camera.attached = false;
        }

        SolarSystemDefinition system = camera.solarSystem();
        camera.system = system == null ? null : system.name;

        planet = camera.planetDefinition();
        if(planet != null){
            camera.planet = planet.name;
        }

        renderer.setSpaceAngles(camera.planetOrbitAngles, camera.planetRotationAngles);

        if(created){
            frameSystem();
        }

        buildUi();
    }

    private void buildUi(){
        content().clear();
        content().margin(0);
        joystick = null;

        Stack stack = new Stack();
        stack.add(preview);

        Table overlay = new Table();
        overlay.setTouchable(Touchable.childrenOnly);
        overlay.top();

        Table topRow = new Table();
        topRow.setTouchable(Touchable.childrenOnly);
        topRow.addButton("System: " + systemName(), this::showSystemSelector)
            .size(230f, 52f).pad(4f)
            .update(b -> b.setText("System: " + systemName()));

        Table top = new Table();
        top.setTouchable(Touchable.childrenOnly);
        for(PlanetDefinition def : camera.definitions()){
            top.addButton(def.name, () -> {
                planet = def;
                camera.planet = def.name;
                resetToPlanet();
                buildUi();
            }).size(150f, 52f).pad(4f);
        }

        ScrollPane bodyScroll = new ScrollPane(top, "horizontal");
        bodyScroll.setScrollingDisabled(false, true);
        bodyScroll.setFadeScrollBars(false);

        topRow.add(bodyScroll).expandX().height(60f).fillX();
        topRow.add().width(230f);
        overlay.add(topRow).expandX().padTop(12f).row();

        overlay.add().expand().row();

        Table bottom = new Table();
        bottom.setTouchable(Touchable.childrenOnly);
        bottom.addButton(camera.attached ? "Orbit Body" : "Free Camera", this::toggleMode)
            .size(150f, 52f).pad(4f)
            .update(b -> b.setText(camera.attached ? "Orbit Body" : "Free Camera"));
        bottom.addButton("Solar System View", this::frameSystem).size(190f, 52f).pad(4f);
        bottom.addButton("Back", this::hide).size(150f, 52f).pad(4f);
        bottom.addButton("Focus Body", this::resetToPlanet).size(150f, 52f).pad(4f);
        bottom.addButton("Save", () -> {
            renderer.copySpaceAnglesTo(camera.planetOrbitAngles, camera.planetRotationAngles);
            camera.write(editor.getTags());
            ui.editor.resetSaved();
            ui.showInfoFade("Space camera saved.");
        }).size(130f, 52f).pad(4f);

        overlay.add(bottom).expandX().padBottom(12f);

        stack.add(overlay);
        stack.add(buildControlsOverlay());
        if(isMobile()) stack.add(buildMobileOverlay());
        content().add(stack).grow();
    }

    private boolean isMobile(){
        return io.anuke.mindustry.Vars.mobile || io.anuke.mindustry.Vars.testMobile;
    }

    /** Touch joystick for Android, placed above the bottom camera buttons. */
    private Table buildMobileOverlay(){
        Table root = new Table();
        root.setTouchable(Touchable.childrenOnly);
        root.bottom().left();

        joystick = new Joystick();
        root.add(joystick).size(Joystick.SIZE).padLeft(24f).padBottom(84f);

        return root;
    }

    private Table buildControlsOverlay(){
        Table root = new Table();
        root.setTouchable(Touchable.childrenOnly);
        root.right();
        root.add(buildPlanetControls()).center().padRight(12f);
        return root;
    }

    private Table buildPlanetControls(){
        Table controls = new Table();
        controls.setTouchable(Touchable.childrenOnly);

        controls.addButton("PX1 Render: " + (camera.px1 ? "On" : "Off"), () -> {
            camera.px1 = !camera.px1;
            buildUi();
        }).size(240f, 46f).padBottom(8f).row();

        if(planet == null){
            return controls;
        }

        addSpeedControl(controls, "Planet Rotation", -5f, 5f, 0.05f,
            () -> planet.rotationSpeed, v -> planet.rotationSpeed = v);
        addSpeedControl(controls, "Orbit Speed", -2f, 2f, 0.01f,
            () -> planet.orbitSpeed, v -> planet.orbitSpeed = v);

        if(planet.visual.clouds.size > 0){
            addSpeedControl(controls, "Cloud Rotation Multiplier", -4f, 4f, 0.1f,
                () -> planet.cloudRotationMultiplier, v -> planet.cloudRotationMultiplier = v);
        }

        return controls;
    }

    private void addSpeedControl(Table table, String label, float min, float max, float step,
                                 java.util.function.Supplier<Float> getter, Consumer<Float> setter){
        table.add(label).left().padTop(4).row();

        Table row = new Table();
        row.setTouchable(Touchable.childrenOnly);

        Slider slider = new Slider(min, max, step, false);
        //the step can be smaller than one pixel, so give zero a small magnetic zone
        slider.setSnapToValues(new float[]{0f}, Math.max(step, (max - min) * 0.02f));
        slider.moved(setter);
        slider.setValue(getter.get());

        row.add(slider).width(175f).padRight(6f);
        row.add(new Label(() -> Strings.toFixed(getter.get(), 2))).width(55f).right();

        table.add(row).width(240f).left().row();
    }

    private void showSystemSelector(){
        FloatingDialog dialog = new FloatingDialog("Solar System");
        dialog.addCloseButton();

        Table table = new Table();
        for(SolarSystemDefinition system : SpaceCamera.systems()){
            table.addButton(system.name, () -> {
                selectSystem(system);
                dialog.hide();
            }).size(300f, 48f).pad(3f).row();
        }

        ScrollPane pane = new ScrollPane(table);
        dialog.content().add(pane)
            .size(340f, Math.min(520f, 60f * Math.max(1, SpaceCamera.systems().size)))
            .pad(8f);
        dialog.show();
    }

    private void selectSystem(SolarSystemDefinition system){
        if(system == null) return;

        camera.system = system.name;
        planet = system.primary();
        camera.planet = planet == null ? null : planet.name;
        camera.attached = false;
        frameSystem();
        buildUi();
    }

    private String systemName(){
        SolarSystemDefinition system = camera == null ? null : camera.solarSystem();
        return system == null ? "None" : system.name;
    }

    private void toggleMode(){
        if(camera.attached){
            detachFromPlanet();
        }else{
            attachToPlanet();
        }
    }

    private void clearTags(){
        String[] keys = {"system", "planet", "px1", "attached", "x", "y", "z", "yaw", "pitch", "lon", "lat", "zoom"};
        for(String key : keys){
            editor.getTags().remove(SpaceCamera.PREFIX + key);
        }
        camera = new SpaceCamera();
        camera.attached = false;
        frameSystem();
    }

    private void frameSystem(){
        camera.attached = false;

        SolarSystemDefinition system = camera.solarSystem();
        if(system != null && renderer.spaceSystemPose(system, pose, dir)){
            camera.x = pose.x;
            camera.y = pose.y;
            camera.z = pose.z;
            camera.yaw = (float)Math.atan2(dir.z, dir.x);
            camera.pitch = (float)Math.asin(Mathf.clamp(dir.y, -1f, 1f));
        }else{
            camera.x = -4.5f;
            camera.y = 5.5f;
            camera.z = 17f;
            lookAt(-4.5f, 0f, 0f);
        }
    }

    private void attachToPlanet(){
        camera.attached = true;
        camera.lon = 0.55f;
        camera.lat = 0.28f;
        camera.zoom = 1.8f;
    }

    private void resetToPlanet(){
        if(planet == null) return;

        if(camera.attached){
            camera.lon = 0.55f;
            camera.lat = 0.28f;
            camera.zoom = 1.8f;
        }else if(renderer.spaceOrbitPose(camera.solarSystem(), planet, 0.55f, 0.28f, 1.8f, pose, dir)){
            camera.x = pose.x;
            camera.y = pose.y;
            camera.z = pose.z;
            camera.yaw = (float)Math.atan2(dir.z, dir.x);
            camera.pitch = (float)Math.asin(Mathf.clamp(dir.y, -1f, 1f));
        }
    }

    private void detachFromPlanet(){
        if(planet != null && renderer.spaceOrbitPose(camera.solarSystem(), planet, camera.lon, camera.lat, camera.zoom,
            true, pose, dir)){
            camera.x = pose.x;
            camera.y = pose.y;
            camera.z = pose.z;
            camera.yaw = (float)Math.atan2(dir.z, dir.x);
            camera.pitch = (float)Math.asin(Mathf.clamp(dir.y, -1f, 1f));
        }
        camera.attached = false;
    }

    private void lookAt(float tx, float ty, float tz){
        float dx = tx - camera.x;
        float dy = ty - camera.y;
        float dz = tz - camera.z;

        camera.yaw = (float)Math.atan2(dz, dx);
        camera.pitch = (float)Math.atan2(dy, Math.sqrt(dx * dx + dz * dz));
    }

    private class Preview extends Element{
        private float lastX, lastY;
        private float pendingScroll;

        Preview(){
            setTouchable(Touchable.enabled);

            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                    lastX = x;
                    lastY = y;
                    if(io.anuke.ucore.core.Core.scene != null){
                        io.anuke.ucore.core.Core.scene.setKeyboardFocus(Preview.this);
                    }
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    float dx = x - lastX, dy = y - lastY;
                    lastX = x;
                    lastY = y;

                    if(camera == null) return;

                    if(camera.attached){
                        float factor = 0.01f * camera.zoom;
                        camera.lon += dx * factor * MathUtils.cos(camera.lat);
                        camera.lat = Mathf.clamp(camera.lat - dy * factor, -1.4f, 1.4f);
                    }else{
                        camera.yaw += dx * 0.005f;
                        camera.pitch = Mathf.clamp(camera.pitch - dy * 0.005f, -1.5f, 1.5f);
                    }
                }

                @Override
                public boolean scrolled(InputEvent event, float x, float y, int amount){
                    pendingScroll += amount * 0.08f;
                    return true;
                }
            });
        }

        @Override
        public void draw(){
            if(camera == null) return;

            applyScroll();
            applyMovement();
            applyJoystick();

            Vector2 pos = localToStageCoordinates(new Vector2(0f, 0f));

            Draw.color(0.02f, 0.03f, 0.06f, 1f);
            Fill.crect(pos.x, pos.y, getWidth(), getHeight());
            Draw.reset();

            try{
                SolarSystemDefinition system = camera.solarSystem();

                if(camera.attached){
                    if(planet == null || !renderer.spaceOrbitPose(system, planet, camera.lon, camera.lat, camera.zoom,
                        true, pose, dir)){
                        return;
                    }

                    float yaw = (float)Math.atan2(dir.z, dir.x);
                    float pitch = (float)Math.asin(Mathf.clamp(dir.y, -1f, 1f));
                    renderer.renderSpace(system, pos.x, pos.y, getWidth(), getHeight(),
                        pose.x, pose.y, pose.z, yaw, pitch, camera.px1);
                }else{
                    renderer.renderSpace(system, pos.x, pos.y, getWidth(), getHeight(),
                        camera.x, camera.y, camera.z, camera.yaw, camera.pitch, camera.px1);
                }
            }catch(Throwable ignored){
                //shader or mesh failure: keep the panel blank instead of crashing the editor
            }
        }

        private void applyScroll(){
            if(pendingScroll == 0f || camera == null) return;

            if(camera.attached){
                camera.zoom = Mathf.clamp(camera.zoom + pendingScroll, 0.4f, 3.5f);
            }else{
                float cp = MathUtils.cos(camera.pitch);
                float move = pendingScroll * 3f;
                camera.x += cp * MathUtils.cos(camera.yaw) * move;
                camera.y += MathUtils.sin(camera.pitch) * move;
                camera.z += cp * MathUtils.sin(camera.yaw) * move;
            }
            pendingScroll = 0f;
        }

        private void applyMovement(){
            boolean forward = Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP);
            boolean back = Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN);
            boolean left = Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT);
            boolean right = Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT);
            boolean up = Gdx.input.isKeyPressed(Keys.E);
            boolean down = Gdx.input.isKeyPressed(Keys.Q);

            if(!(forward || back || left || right || up || down)) return;

            //WASD always drives the free camera
            if(camera.attached){
                detachFromPlanet();
            }

            float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
            float speed = 8f * dt;
            if(Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT)){
                speed *= 3f;
            }

            float moveForward = (forward ? 1 : 0) - (back ? 1 : 0);
            float moveRight = (right ? 1 : 0) - (left ? 1 : 0);
            float moveUp = (up ? 1 : 0) - (down ? 1 : 0);

            float cp = MathUtils.cos(camera.pitch);
            float fx = cp * MathUtils.cos(camera.yaw);
            float fy = MathUtils.sin(camera.pitch);
            float fz = cp * MathUtils.sin(camera.yaw);
            float rx = -MathUtils.sin(camera.yaw);
            float rz = MathUtils.cos(camera.yaw);

            camera.x += (fx * moveForward + rx * moveRight) * speed;
            camera.y += (fy * moveForward + moveUp) * speed;
            camera.z += (fz * moveForward + rz * moveRight) * speed;
        }

        /** Applies the mobile joystick: free-camera movement, or orbit-body zoom in orbit mode. */
        private void applyJoystick(){
            if(joystick == null || camera == null || !joystick.isActive()) return;

            float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.05f);
            float jx = joystick.getKnobPercentX();
            float jy = joystick.getKnobPercentY();

            if(camera.attached){
                //orbit mode the joystick only zooms toward/away from the selected body
                float dz = Joystick.DEADZONE;
                float amount = Math.abs(jy) <= dz ? 0f : (Math.abs(jy) - dz) / (1f - dz);
                if(amount <= 0f) return;

                //up zooms in toward the body, down pulls away
                camera.zoom = Mathf.clamp(camera.zoom - Math.signum(jy) * amount * 1.4f * dt, 0.4f, 3.5f);
                return;
            }

            float mag = (float)Math.sqrt(jx * jx + jy * jy);
            float dz = Joystick.DEADZONE;
            if(mag <= dz) return;

            //near the center is slow, near the border is fast
            float strength = Mathf.clamp((mag - dz) / (1f - dz), 0f, 1f);
            float speed = 10f * strength * dt;

            //up on the stick moves the camera in the direction it is facing
            float forward = jy;
            float right = jx;

            float cp = MathUtils.cos(camera.pitch);
            float fx = cp * MathUtils.cos(camera.yaw);
            float fy = MathUtils.sin(camera.pitch);
            float fz = cp * MathUtils.sin(camera.yaw);
            float rx = -MathUtils.sin(camera.yaw);
            float rz = MathUtils.cos(camera.yaw);

            camera.x += (fx * forward + rx * right) * speed;
            camera.y += fy * forward * speed;
            camera.z += (fz * forward + rz * right) * speed;
        }
    }

    /** Simple circular on-screen joystick for mobiles */
    private static class Joystick extends Element{
        static final float SIZE = 176f;
        static final float DEADZONE = 0.12f;
        private static final float BASE_RADIUS = SIZE / 2f - 8f;
        private static final float KNOB_RADIUS = 34f;

        private int pointer = -1;
        private float percentX, percentY;
        private Vector2 tmp = new Vector2();

        Joystick(){
            setTouchable(Touchable.enabled);

            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointerId, int button){
                    if(pointer != -1) return false;
                    pointer = pointerId;
                    update(x, y);
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointerId){
                    if(pointerId == pointer){
                        update(x, y);
                    }
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointerId, int button){
                    if(pointerId == pointer){
                        pointer = -1;
                        percentX = 0f;
                        percentY = 0f;
                        tmp.set(0f, 0f);
                    }
                }
            });
        }

        boolean isActive(){
            return pointer != -1;
        }

        float getKnobPercentX(){
            return percentX;
        }

        float getKnobPercentY(){
            return percentY;
        }

        private void update(float x, float y){
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float dx = x - cx, dy = y - cy;
            float length = (float)Math.sqrt(dx * dx + dy * dy);
            float max = BASE_RADIUS;

            if(length > max && length > 0f){
                dx = dx / length * max;
                dy = dy / length * max;
                length = max;
            }

            percentX = length <= 0f ? 0f : dx / max;
            percentY = length <= 0f ? 0f : dy / max;

            float visualLength = Math.min(length, max - KNOB_RADIUS * 0.5f);
            if(length > 0f){
                tmp.set(dx / length * visualLength, dy / length * visualLength);
            }else{
                tmp.set(0f, 0f);
            }
        }

        @Override
        public Element hit(float x, float y, boolean touchable){
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float dx = x - cx, dy = y - cy;
            return dx * dx + dy * dy <= BASE_RADIUS * BASE_RADIUS ? this : null;
        }

        @Override
        public void draw(){
            Vector2 pos = localToStageCoordinates(new Vector2(0f, 0f));
            float cx = pos.x + getWidth() / 2f;
            float cy = pos.y + getHeight() / 2f;

            Draw.color(0.05f, 0.07f, 0.1f, 0.55f);
            Fill.circle(cx, cy, BASE_RADIUS);

            Draw.color(1f, 1f, 1f, 0.35f);
            Lines.stroke(2.5f);
            Lines.circle(cx, cy, BASE_RADIUS);

            Draw.color(1f, 1f, 1f, 0.12f);
            Lines.stroke(1.5f);
            Lines.circle(cx, cy, BASE_RADIUS * 0.45f);

            float kx = cx + tmp.x, ky = cy + tmp.y;
            Draw.color(1f, 0.83f, 0.5f, 0.9f);
            Fill.circle(kx, ky, KNOB_RADIUS);

            Draw.color(1f, 1f, 1f, 0.75f);
            Lines.stroke(2f);
            Lines.circle(kx, ky, KNOB_RADIUS);

            Draw.reset();
        }
    }
}
