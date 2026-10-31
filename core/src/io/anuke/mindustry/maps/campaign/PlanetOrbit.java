package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.PlanetMap.Region;
import io.anuke.ucore.util.Mathf;

/**
 * Orbit/rotation math shared by the campaign planet view and the space scene.
 * <p>
 * Every body's base position comes from its definition ({@code sunDirection}, {@code sunDistance}
 * or {@code orbitPosition}). The orbit controller then rotates that base
 * position around the parent, so {@code orbitStart = orbitSpeed = orbitTilt = 0} keeps the old
 * static layout exactly.
 */
public class PlanetOrbit{
    /** Position of a body relative to its parent, using its time-based orbit. */
    public static Vector3 offset(PlanetDefinition body, PlanetDefinition parent, float time, Vector3 out){
        return offsetAtAngle(body, parent, body.orbitStart + body.orbitSpeed * time, out);
    }

    /** Position of a body relative to its parent at an explicit orbit angle in degrees. */
    public static Vector3 offsetAtAngle(PlanetDefinition body, PlanetDefinition parent, float angleDegrees, Vector3 out){
        Vector3 base = new Vector3();
        Vector3 tangent = new Vector3();
        orbitBasis(body, parent, base, tangent);

        float distance = distance(body, parent);
        float angle = angleDegrees * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);

        out.set(base).scl(cos)
            .add(tangent.x * sin, tangent.y * sin, tangent.z * sin)
            .scl(distance);
        return out;
    }

    /**
     * The orbit plane basis. {@code base} is the position at angle 0; {@code tangent} is the
     * direction of motion in the plane, perpendicular to {@code base}. Both are unit vectors,
     * and the circle is always centered on the parent.
     */
    public static void orbitBasis(PlanetDefinition body, Vector3 base, Vector3 tangent){
        orbitBasis(body, body.parent, base, tangent);
    }

    public static void orbitBasis(PlanetDefinition body, PlanetDefinition parent, Vector3 base, Vector3 tangent){
        baseDirection(body, parent, base);
        if(base.isZero()){
            base.set(1f, 0f, 0f);
        }
        base.nor();

        tangent.set(base).crs(Vector3.Y);
        if(tangent.isZero()){
            tangent.set(1f, 0f, 0f);
        }else{
            tangent.nor();
        }
        if(body.orbitTilt != 0f){
            tangent.rotate(base, body.orbitTilt);
        }
    }

    public static float orbitRadius(PlanetDefinition body){
        return distance(body, body.parent);
    }

    /** Positions relative to the system's primary body, used by the sector/planet view. */
    public static void positions(SolarSystemDefinition system, float time, ObjectMap<PlanetDefinition, Vector3> out){
        positionsCentered(system, time, system == null ? null : system.primary(), out);
    }

    /**
     * Fills {@code out} with every body's position in the system, translated so {@code center}
     * sits at the origin. The space scene uses the Sun here; the sector view uses the planet.
     */
    public static void positionsCentered(SolarSystemDefinition system, float time, PlanetDefinition center,
                                         ObjectMap<PlanetDefinition, Vector3> out){
        ObjectMap<PlanetDefinition, Float> angles = new ObjectMap<>();
        if(system != null){
            for(PlanetDefinition body : system.bodies){
                angles.put(body, normalizeAngle(body.orbitStart + body.orbitSpeed * time));
            }
        }
        positionsCentered(system, angles, center, out);
    }

    /** Fills positions using explicit per-body orbit angles in degrees. */
    public static void positionsCentered(SolarSystemDefinition system, ObjectMap<PlanetDefinition, Float> angles,
                                         PlanetDefinition center, ObjectMap<PlanetDefinition, Vector3> out){
        out.clear();
        if(system == null) return;

        ObjectMap<PlanetDefinition, Vector3> absolute = new ObjectMap<>();
        for(PlanetDefinition body : system.bodies){
            absolutePosition(body, system, angles, absolute);
        }

        Vector3 centerPos = center == null ? Vector3.Zero : absolute.get(center);
        if(centerPos == null) centerPos = Vector3.Zero;

        for(PlanetDefinition body : system.bodies){
            Vector3 pos = absolute.get(body);
            out.put(body, new Vector3(pos == null ? Vector3.Zero : pos).sub(centerPos));
        }
    }

    private static Vector3 absolutePosition(PlanetDefinition body, SolarSystemDefinition system,
                                            ObjectMap<PlanetDefinition, Float> angles,
                                            ObjectMap<PlanetDefinition, Vector3> cache){
        Vector3 cached = cache.get(body);
        if(cached != null) return cached;

        Vector3 pos;
        if(body.parent == null){
            pos = new Vector3();
        }else{
            Vector3 parent = absolutePosition(body.parent, system, angles, cache);
            float angle = angles.get(body, body.orbitStart);
            pos = new Vector3(parent).add(offsetAtAngle(body, body.parent, angle, new Vector3()));
        }

        cache.put(body, pos);
        return pos;
    }

    /** Direction from the primary planet to its sun, in the planet's local (unrotated) frame. */
    public static Vector3 localSunDirection(PlanetDefinition planet, float time, Vector3 out){
        sunDirection(planet, time, out);

        float rotation = rotationAngle(planet, time);
        if(rotation != 0f){
            out.rotate(Vector3.Y, -rotation);
        }
        return out;
    }

    /** Direction from the primary planet to its sun, including the planet's orbit. */
    public static Vector3 sunDirection(PlanetDefinition planet, float time, Vector3 out){
        if(planet == null){
            return out.set(0f, 0f, 1f);
        }

        SolarSystemDefinition system = planet.system;
        PlanetDefinition sun = system == null ? null : system.sun();
        if(sun == null || planet.parent == null || planet == sun){
            return out.set(planet.visual.sunDirection).nor();
        }

        Vector3 offset = offset(planet, planet.parent, time, new Vector3());
        return out.set(offset).scl(-1f).nor();
    }

    /** Keeps accumulated angles in a small range so slow movement keeps its fractional precision. */
    public static float normalizeAngle(float angle){
        angle %= 360f;
        return angle < 0f ? angle + 360f : angle;
    }

    /** Self-rotation angle of a planet, in degrees. */
    public static float rotationAngle(PlanetDefinition planet, float time){
        return planet == null ? 0f : normalizeAngle(planet.rotationSpeed * time);
    }

    /**
     * How lit a sector is at the given time. 0 is the dark side, 1 is fully facing the sun.
     * The sector normal is in planet-local space, so the local sun direction already contains
     * the planet's self-rotation.
     */
    public static float sectorLight(PlanetDefinition planet, Sector sector, float time){
        if(planet == null || sector == null) return 1f;

        PlanetMap map = CampaignRegistry.planetMap(planet.campaign);
        if(map == null) return 1f;

        Region region = map.regionFor(sector);
        if(region == null) return 1f;

        Vector3 sun = localSunDirection(planet, time, new Vector3());
        float dot = Mathf.clamp(region.center.dot(sun), -1f, 1f);
        float t = Mathf.clamp((dot + 0.15f) / 0.30f, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static void baseDirection(PlanetDefinition body, PlanetDefinition parent, Vector3 out){
        SolarSystemDefinition system = body.system;

        if(system != null && body == system.primary() && parent == system.sun() && parent != null){
            out.set(body.visual.sunDirection).scl(-1f);
        }else if(parent != null && parent == (system == null ? null : system.primary())
            && !body.visual.moonPosition.isZero()){
            out.set(body.visual.moonPosition);
        }else if(!body.visual.orbitPosition.isZero()){
            out.set(body.visual.orbitPosition);
        }else{
            out.set(1f, 0f, 0f);
        }
    }

    private static float distance(PlanetDefinition body, PlanetDefinition parent){
        if(body.orbitDistance > 0f){
            return body.orbitDistance;
        }

        SolarSystemDefinition system = body.system;
        if(system != null && body == system.primary() && parent == system.sun()){
            return Math.max(1f, body.visual.sunDistance);
        }
        if(parent != null && parent == (system == null ? null : system.primary())
            && !body.visual.moonPosition.isZero()){
            return body.visual.moonPosition.len();
        }
        if(!body.visual.orbitPosition.isZero()){
            return body.visual.orbitPosition.len();
        }
        return 2.5f;
    }

    private PlanetOrbit(){}
}
