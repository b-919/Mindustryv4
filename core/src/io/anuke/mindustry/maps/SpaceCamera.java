package io.anuke.mindustry.maps;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.content.Planets;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.SolarSystemDefinition;

/** Camera pose for the 3D space/planet scene. */
public class SpaceCamera{
    public static final String PREFIX = "spacecamera.";

    /** Whether the camera is locked to the planet. */
    public boolean attached = true;
    /** Name of the solar system group rendered by this camera. */
    public String system;
    /** Name of the planet the camera is attached to or looking at. */
    public String planet;
    /** Whether the space preview should use the PS1 post shaders. */
    public boolean px1 = true;

    /** Stored accumulated orbit/rotation angles, keyed by planet name. */
    public final ObjectMap<String, Float> planetOrbitAngles = new ObjectMap<>();
    public final ObjectMap<String, Float> planetRotationAngles = new ObjectMap<>();

    /** Free-camera position, in scene units. */
    public float x, y, z = 4f;
    /** Free-camera yaw/pitch, in radians. */
    public float yaw, pitch;
    /** Attached-camera orbit angles and zoom. */
    public float lon, lat, zoom = 1f;

    public static boolean has(ObjectMap<String, String> tags){
        return tags != null && tags.containsKey(PREFIX + "planet");
    }

    public static SpaceCamera read(ObjectMap<String, String> tags){
        if(!has(tags)) return null;

        SpaceCamera camera = new SpaceCamera();
        camera.system = tags.get(PREFIX + "system");
        camera.planet = tags.get(PREFIX + "planet");
        camera.px1 = !"0".equals(tags.get(PREFIX + "px1", "1"));
        camera.attached = !"0".equals(tags.get(PREFIX + "attached", "1"));
        camera.x = getFloat(tags, "x", camera.x);
        camera.y = getFloat(tags, "y", camera.y);
        camera.z = getFloat(tags, "z", camera.z);
        camera.yaw = getFloat(tags, "yaw", camera.yaw);
        camera.pitch = getFloat(tags, "pitch", camera.pitch);
        camera.lon = getFloat(tags, "lon", camera.lon);
        camera.lat = getFloat(tags, "lat", camera.lat);
        camera.zoom = getFloat(tags, "zoom", camera.zoom);
        camera.applyPlanetOverrides(tags);
        return camera;
    }

    private void applyPlanetOverrides(ObjectMap<String, String> tags){
        SolarSystemDefinition system = solarSystem();
        if(system == null) return;

        for(PlanetDefinition body : system.bodies){
            String prefix = "def." + body.name + ".";
            body.orbitStart = getFloat(tags, prefix + "orbitStart", body.orbitStart);
            body.orbitTilt = getFloat(tags, prefix + "orbitTilt", body.orbitTilt);
            body.orbitSpeed = getFloat(tags, prefix + "orbitSpeed", body.orbitSpeed);
            body.rotationSpeed = getFloat(tags, prefix + "rotationSpeed", body.rotationSpeed);
            body.cloudRotationMultiplier = getFloat(tags, prefix + "cloudMultiplier", body.cloudRotationMultiplier);
            body.orbitDistance = getFloat(tags, prefix + "orbitDistance", body.orbitDistance);

            Float orbitAngle = parseFloat(tags.get(PREFIX + prefix + "orbitAngle"));
            Float rotationAngle = parseFloat(tags.get(PREFIX + prefix + "rotationAngle"));
            if(orbitAngle != null) planetOrbitAngles.put(body.name, orbitAngle);
            if(rotationAngle != null) planetRotationAngles.put(body.name, rotationAngle);
        }
    }

    public void write(ObjectMap<String, String> tags){
        tags.put(PREFIX + "system", system == null ? "" : system);
        tags.put(PREFIX + "planet", planet == null ? "" : planet);
        tags.put(PREFIX + "px1", px1 ? "1" : "0");
        tags.put(PREFIX + "attached", attached ? "1" : "0");
        tags.put(PREFIX + "x", Float.toString(x));
        tags.put(PREFIX + "y", Float.toString(y));
        tags.put(PREFIX + "z", Float.toString(z));
        tags.put(PREFIX + "yaw", Float.toString(yaw));
        tags.put(PREFIX + "pitch", Float.toString(pitch));
        tags.put(PREFIX + "lon", Float.toString(lon));
        tags.put(PREFIX + "lat", Float.toString(lat));
        tags.put(PREFIX + "zoom", Float.toString(zoom));

        SolarSystemDefinition system = solarSystem();
        if(system != null){
            for(PlanetDefinition body : system.bodies){
                String prefix = PREFIX + "def." + body.name + ".";
                tags.put(prefix + "orbitStart", Float.toString(body.orbitStart));
                tags.put(prefix + "orbitTilt", Float.toString(body.orbitTilt));
                tags.put(prefix + "orbitSpeed", Float.toString(body.orbitSpeed));
                tags.put(prefix + "rotationSpeed", Float.toString(body.rotationSpeed));
                tags.put(prefix + "cloudMultiplier", Float.toString(body.cloudRotationMultiplier));
                tags.put(prefix + "orbitDistance", Float.toString(body.orbitDistance));

                Float orbitAngle = planetOrbitAngles.get(body.name);
                Float rotationAngle = planetRotationAngles.get(body.name);
                if(orbitAngle != null) tags.put(prefix + "orbitAngle", Float.toString(orbitAngle));
                if(rotationAngle != null) tags.put(prefix + "rotationAngle", Float.toString(rotationAngle));
            }
        }
    }

    private static Float parseFloat(String value){
        if(value == null) return null;
        try{
            return Float.parseFloat(value);
        }catch(Exception e){
            return null;
        }
    }

    /** The solar system selected by this camera. */
    public SolarSystemDefinition solarSystem(){
        return Planets.system(system);
    }

    /** The selected body, resolved inside the selected solar system. */
    public PlanetDefinition planetDefinition(){
        SolarSystemDefinition system = solarSystem();
        if(system == null) return null;

        for(PlanetDefinition def : system.bodies){
            if(def.name != null && def.name.equals(planet)){
                return def;
            }
        }
        return system.primary();
    }

    /** Bodies available to the selected solar system. */
    public Array<PlanetDefinition> definitions(){
        SolarSystemDefinition system = solarSystem();
        return system == null ? new Array<>() : system.bodies;
    }

    /** All solar systems that can be selected. */
    public static Array<SolarSystemDefinition> systems(){
        return Planets.systems();
    }

    private static float getFloat(ObjectMap<String, String> tags, String key, float def){
        try{
            String value = tags.get(PREFIX + key);
            return value == null ? def : Float.parseFloat(value);
        }catch(Exception e){
            return def;
        }
    }
}
