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
    /** Whether orbit rings are rendered. */
    public boolean orbits = true;

    /** Stored accumulated orbit/rotation angles, keyed by planet name. */
    public final ObjectMap<String, Float> planetOrbitAngles = new ObjectMap<>();
    public final ObjectMap<String, Float> planetRotationAngles = new ObjectMap<>();

    private final ObjectMap<String, BodyOverrides> overrides = new ObjectMap<>();
    private static final ObjectMap<PlanetDefinition, Defaults> defaultValues = new ObjectMap<>();

    private static class Defaults{
        float orbitStart, orbitTilt, orbitSpeed, rotationSpeed, cloudRotationMultiplier, orbitDistance;
    }

    private static class BodyOverrides{
        boolean hasOrbitStart, hasOrbitTilt, hasOrbitSpeed, hasRotationSpeed, hasCloudMultiplier, hasOrbitDistance;
        float orbitStart, orbitTilt, orbitSpeed, rotationSpeed, cloudRotationMultiplier, orbitDistance;
    }

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
        camera.orbits = !"0".equals(tags.get(PREFIX + "orbits", "1"));
        camera.attached = !"0".equals(tags.get(PREFIX + "attached", "1"));
        camera.x = getFloat(tags, "x", camera.x);
        camera.y = getFloat(tags, "y", camera.y);
        camera.z = getFloat(tags, "z", camera.z);
        camera.yaw = getFloat(tags, "yaw", camera.yaw);
        camera.pitch = getFloat(tags, "pitch", camera.pitch);
        camera.lon = getFloat(tags, "lon", camera.lon);
        camera.lat = getFloat(tags, "lat", camera.lat);
        camera.zoom = getFloat(tags, "zoom", camera.zoom);
        camera.readPlanetOverrides(tags);
        return camera;
    }

    private void readPlanetOverrides(ObjectMap<String, String> tags){
        overrides.clear();
        SolarSystemDefinition system = solarSystem();
        if(system == null) return;

        for(PlanetDefinition body : system.bodies){
            String prefix = PREFIX + "def." + body.name + ".";
            BodyOverrides override = new BodyOverrides();
            boolean any = false;
            Float value;

            value = parseFloat(tags.get(prefix + "orbitStart"));
            if(value != null){
                override.orbitStart = value;
                override.hasOrbitStart = true;
                any = true;
            }
            value = parseFloat(tags.get(prefix + "orbitTilt"));
            if(value != null){
                override.orbitTilt = value;
                override.hasOrbitTilt = true;
                any = true;
            }
            value = parseFloat(tags.get(prefix + "orbitSpeed"));
            if(value != null){
                override.orbitSpeed = value;
                override.hasOrbitSpeed = true;
                any = true;
            }
            value = parseFloat(tags.get(prefix + "rotationSpeed"));
            if(value != null){
                override.rotationSpeed = value;
                override.hasRotationSpeed = true;
                any = true;
            }
            value = parseFloat(tags.get(prefix + "cloudMultiplier"));
            if(value != null){
                override.cloudRotationMultiplier = value;
                override.hasCloudMultiplier = true;
                any = true;
            }
            value = parseFloat(tags.get(prefix + "orbitDistance"));
            if(value != null){
                override.orbitDistance = value;
                override.hasOrbitDistance = true;
                any = true;
            }

            if(any){
                overrides.put(body.name, override);
            }

            Float orbitAngle = parseFloat(tags.get(prefix + "orbitAngle"));
            Float rotationAngle = parseFloat(tags.get(prefix + "rotationAngle"));
            if(orbitAngle != null) planetOrbitAngles.put(body.name, orbitAngle);
            if(rotationAngle != null) planetRotationAngles.put(body.name, rotationAngle);
        }
    }

    /** Applies this camera's saved planet overrides to the shared definitions. */
    public void applyOverrides(){
        restoreDefaults();

        SolarSystemDefinition system = solarSystem();
        if(system == null) return;

        for(PlanetDefinition body : system.bodies){
            defaults(body);
        }

        for(PlanetDefinition body : system.bodies){
            BodyOverrides override = overrides.get(body.name);
            if(override == null) continue;

            if(override.hasOrbitStart) body.orbitStart = override.orbitStart;
            if(override.hasOrbitTilt) body.orbitTilt = override.orbitTilt;
            if(override.hasOrbitSpeed) body.orbitSpeed = override.orbitSpeed;
            if(override.hasRotationSpeed) body.rotationSpeed = override.rotationSpeed;
            if(override.hasCloudMultiplier) body.cloudRotationMultiplier = override.cloudRotationMultiplier;
            if(override.hasOrbitDistance) body.orbitDistance = override.orbitDistance;
        }
    }

    /** Restores every planet definition to the values it had before any camera overrides. */
    public static void restoreDefaults(){
        for(ObjectMap.Entry<PlanetDefinition, Defaults> entry : defaultValues){
            PlanetDefinition body = entry.key;
            Defaults value = entry.value;

            body.orbitStart = value.orbitStart;
            body.orbitTilt = value.orbitTilt;
            body.orbitSpeed = value.orbitSpeed;
            body.rotationSpeed = value.rotationSpeed;
            body.cloudRotationMultiplier = value.cloudRotationMultiplier;
            body.orbitDistance = value.orbitDistance;
        }
    }

    private static Defaults defaults(PlanetDefinition body){
        Defaults value = defaultValues.get(body);
        if(value == null){
            value = new Defaults();
            value.orbitStart = body.orbitStart;
            value.orbitTilt = body.orbitTilt;
            value.orbitSpeed = body.orbitSpeed;
            value.rotationSpeed = body.rotationSpeed;
            value.cloudRotationMultiplier = body.cloudRotationMultiplier;
            value.orbitDistance = body.orbitDistance;
            defaultValues.put(body, value);
        }
        return value;
    }

    public void write(ObjectMap<String, String> tags){
        tags.put(PREFIX + "system", system == null ? "" : system);
        tags.put(PREFIX + "planet", planet == null ? "" : planet);
        tags.put(PREFIX + "px1", px1 ? "1" : "0");
        tags.put(PREFIX + "orbits", orbits ? "1" : "0");
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
