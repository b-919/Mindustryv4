package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.content.Planets;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.PlanetOrbit;
import io.anuke.mindustry.maps.campaign.SolarSystemDefinition;

/** World-space layout of the solar system body that is currently being displayed. */
public class SolarSystem{
    public final Vector3 light = new Vector3();
    public final Vector3 sunPosition = new Vector3();
    public final Vector3 moonPosition = new Vector3();
    public final Matrix4 sunTransform = new Matrix4();
    public final Matrix4 moonTransform = new Matrix4();

    public PlanetDefinition sun, moon;
    /** Body currently placed at the origin of the scene. */
    public PlanetDefinition displayed;

    public void configure(PlanetDefinition planet){
        configure(planet, 0f);
    }

    public void configure(PlanetDefinition planet, float time){
        displayed = planet;
        SolarSystemDefinition system = planet.system == null ? Planets.defaultSystem() : planet.system;
        if(system != null){
            sun = system.sun();
            moon = null;
            for(int i = 0; i < system.bodies.size; i++){
                PlanetDefinition body = system.bodies.get(i);
                if(!body.isSun && body.parent == planet){
                    moon = body;
                    break;
                }
            }

            ObjectMap<PlanetDefinition, Vector3> positions = new ObjectMap<>();
            PlanetOrbit.positions(system, time, positions);

            Vector3 sunPos = sun == null ? null : positions.get(sun);
            Vector3 moonPos = moon == null ? null : positions.get(moon);

            sunPosition.set(sunPos == null ? Vector3.Zero : sunPos);
            moonPosition.set(moonPos == null ? Vector3.Zero : moonPos);
        }else{
            sun = Planets.sun == null ? planet : Planets.sun;
            moon = Planets.moon == null ? planet : Planets.moon;

            sunPosition.set(planet.visual.sunDirection).nor().scl(planet.visual.sunDistance);
            moonPosition.set(moon.visual.moonPosition);
        }

        light.set(PlanetOrbit.localSunDirection(planet, time, new Vector3()));

        sunTransform.setToTranslation(sunPosition);
        moonTransform.setToTranslation(moonPosition);
    }

    /** Fills {@code out} with the bodies that currently have a known scene position. */
    public void collectBodies(Array<PlanetDefinition> out){
        out.clear();
        if(displayed != null) out.add(displayed);
        if(sun != null && sun != displayed) out.add(sun);
        if(moon != null && moon != displayed) out.add(moon);
    }

    /** @return the world position of a body in the current scene, or null if it has none. */
    public Vector3 positionOf(PlanetDefinition body){
        if(body == displayed) return Vector3.Zero;
        if(body == sun) return sunPosition;
        if(body == moon) return moonPosition;
        return null;
    }
}
