package io.anuke.mindustry.graphics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.*;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.graphics.planet.PlanetRenderer;
import io.anuke.mindustry.maps.SpaceCamera;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.SolarSystemDefinition;
import io.anuke.ucore.core.*;
import io.anuke.ucore.graphics.*;
import io.anuke.ucore.util.*;

/** Draws the main menu background from a shader or a saved 3D space scene. */
public class MenuBackground{
    private static final String modeShader = "shader";
    private static final String modeSpace = "space";

    public static final String shaderDefault = "default";
    private static final String settingsMode = "background-mode";
    private static final String settingsShader = "background-shader";
    private static final String settingsCamera = "background-space-camera";

    private final ObjectMap<String, String> cameraTags = new ObjectMap<>();
    private final Vector3 cameraPos = new Vector3();
    private final Vector3 cameraDir = new Vector3();

    private PlanetRenderer spaceRenderer;
    private SpaceCamera spaceCamera;
    private Shaders.CustomShader customShader;
    private boolean shaderFailed;
    private FileHandle shaderDirectory;

    public MenuBackground(){
        refresh();
    }

    private String mode(){
        return Settings.getString(settingsMode, modeShader);
    }

    public String shaderName(){
        return Settings.getString(settingsShader, shaderDefault);
    }

    public boolean isSpace(){
        return modeSpace.equals(mode());
    }

    public boolean isDefaultShader(){
        return shaderDefault.equals(shaderName());
    }

    public boolean shaderFailed(){
        return shaderFailed;
    }

    private FileHandle shaderDirectory(){
        if(shaderDirectory == null){
            shaderDirectory = Vars.dataDirectory.child("background/");
        }
        if(!shaderDirectory.exists()){
            shaderDirectory.mkdirs();
        }
        return shaderDirectory;
    }

    public Array<FileHandle> customShaders(){
        Array<FileHandle> out = new Array<>();
        for(FileHandle file : shaderDirectory().list()){
            if(!file.isDirectory() && isShaderFile(file)){
                out.add(file);
            }
        }
        return out;
    }

    private void refresh(){
        loadCustomShader();
        loadCameraTags();
        spaceCamera = null;
    }
    public void setShader(String name){
        Settings.putString(settingsMode, modeShader);
        Settings.putString(settingsShader, name);
        Settings.save();
        loadCustomShader();
    }

    public void setSpace(){
        Settings.putString(settingsMode, modeSpace);
        Settings.save();
        loadCameraTags();
        spaceCamera = null;
    }

    public void importShader(FileHandle file){
        FileHandle target = shaderDirectory().child(file.name());
        if(!file.path().equals(target.path())){
            file.copyTo(target);
        }
        setShader(target.name());
    }

    public void removeShader(String name){
        FileHandle file = shaderDirectory().child(name);
        if(file.exists()){
            file.delete();
        }

        if(name.equals(shaderName())){
            setShader(shaderDefault);
        }else{
            loadCustomShader();
        }
    }

    public ObjectMap<String, String> cameraTags(){
        if(cameraTags.size == 0){
            loadCameraTags();
        }
        return cameraTags;
    }

    public void saveCamera(ObjectMap<String, String> tags){
        StringBuilder builder = new StringBuilder();
        for(ObjectMap.Entry<String, String> entry : tags){
            if(entry.value == null) continue;
            builder.append(entry.key).append('\n').append(entry.value).append('\n');
        }

        Settings.putString(settingsCamera, builder.toString());
        Settings.putString(settingsMode, modeSpace);
        Settings.save();
        loadCameraTags();
        spaceCamera = null;
    }

    public void draw(float x, float y, float width, float height){
        if(isSpace()){
            drawSpace(x, y, width, height);
        }else{
            drawShader(x, y, width, height);
        }
    }

    private void drawShader(float x, float y, float width, float height){
        Draw.colorl(0.1f);
        Fill.crect(x, y, width, height);

        Shader shader = Shaders.menu;
        if(!isDefaultShader()){
            if(customShader == null && !shaderFailed){
                loadCustomShader();
            }

            if(customShader != null && !shaderFailed){
                shader = customShader;
                Draw.color();
            }else{
                Draw.color(Palette.accent);
            }
        }else{
            Draw.color(Palette.accent);
        }

        Graphics.shader(shader);
        Fill.crect(x, y, width, height);
        Graphics.shader();
        Draw.color();
    }

    private void drawSpace(float x, float y, float width, float height){
        Draw.colorl(0.1f);
        Fill.crect(x, y, width, height);

        if(!ensureSpaceRenderer()) return;

        SpaceCamera camera = spaceCamera();
        if(camera == null) return;

        camera.applyOverrides();
        try{
            spaceRenderer.renderOrbits = camera.orbits;

            SolarSystemDefinition system = camera.solarSystem();
            PlanetDefinition planet = camera.planetDefinition();
            if(system == null || planet == null) return;

            Texture texture;
            if(camera.attached){
                if(!spaceRenderer.spaceOrbitPose(system, planet, camera.lon, camera.lat, camera.zoom,
                    true, cameraPos, cameraDir)){
                    return;
                }

                float yaw = (float)Math.atan2(cameraDir.z, cameraDir.x);
                float pitch = (float)Math.asin(Mathf.clamp(cameraDir.y, -1f, 1f));
                texture = spaceRenderer.renderSpaceToTexture(system, width, height,
                    cameraPos.x, cameraPos.y, cameraPos.z, yaw, pitch, camera.px1);
            }else{
                texture = spaceRenderer.renderSpaceToTexture(system, width, height,
                    camera.x, camera.y, camera.z, camera.yaw, camera.pitch, camera.px1);
            }

            if(texture == null) return;

            Draw.color();
            Core.batch.draw(texture, x, y + height, width, -height);
        }catch(Throwable t){
            Log.err("Failed to render the menu space background; falling back to the shader.");
            Log.err(t);
            disableSpace();
        }finally{
            SpaceCamera.restoreDefaults();
        }
    }

    private void disableSpace(){
        if(spaceRenderer != null){
            spaceRenderer.dispose();
            spaceRenderer = null;
        }
        spaceCamera = null;
        Settings.putString(settingsMode, modeShader);
        Settings.save();
    }

    private boolean ensureSpaceRenderer(){
        if(spaceRenderer == null){
            try{
                spaceRenderer = new PlanetRenderer();
            }catch(Throwable t){
                Log.err("Failed to create the menu space renderer; falling back to the shader.");
                Log.err(t);
                disableSpace();
                return false;
            }
        }
        return true;
    }

    private SpaceCamera spaceCamera(){
        if(spaceCamera == null){
            spaceCamera = SpaceCamera.read(cameraTags);
            if(spaceCamera == null){
                spaceCamera = defaultCamera();
            }
        }
        return spaceCamera;
    }

    private SpaceCamera defaultCamera(){
        Array<SolarSystemDefinition> systems = SpaceCamera.systems();
        if(systems.size == 0) return null;

        SolarSystemDefinition system = systems.first();
        SpaceCamera camera = new SpaceCamera();
        camera.system = system.name;
        PlanetDefinition planet = system.primary();
        camera.planet = planet == null ? null : planet.name;
        camera.attached = false;
        camera.px1 = true;

        if(spaceRenderer != null && spaceRenderer.spaceSystemPose(system, cameraPos, cameraDir)){
            camera.x = cameraPos.x;
            camera.y = cameraPos.y;
            camera.z = cameraPos.z;
            camera.yaw = (float)Math.atan2(cameraDir.z, cameraDir.x);
            camera.pitch = (float)Math.asin(Mathf.clamp(cameraDir.y, -1f, 1f));
        }else{
            camera.x = -4.5f;
            camera.y = 5.5f;
            camera.z = 17f;
            camera.yaw = (float)Math.atan2(-17f, 0f);
            camera.pitch = (float)Math.atan2(-5.5f, 17f);
        }

        return camera;
    }

    private void loadCustomShader(){
        String name = shaderName();
        shaderFailed = false;

        if(shaderDefault.equals(name)){
            if(customShader != null){
                customShader.dispose();
                customShader = null;
            }
            return;
        }

        if(customShader != null){
            customShader.dispose();
            customShader = null;
        }

        FileHandle file = shaderDirectory().child(name);
        if(!file.exists()){
            shaderFailed = true;
            return;
        }

        customShader = new Shaders.CustomShader(file.readString());
        shaderFailed = customShader.error != null;
        if(shaderFailed){
            customShader.dispose();
            customShader = null;
        }
    }

    private void loadCameraTags(){
        cameraTags.clear();
        String stored = Settings.getString(settingsCamera, "");
        String[] lines = stored.split("\n");

        for(int i = 0; i + 1 < lines.length; i += 2){
            if(lines[i].length() == 0) continue;
            cameraTags.put(lines[i], lines[i + 1]);
        }
    }

    private static boolean isShaderFile(FileHandle file){
        String extension = file.extension();
        return extension.equalsIgnoreCase("fragment") || extension.equalsIgnoreCase("frag")
                || extension.equalsIgnoreCase("glsl");
    }
}
