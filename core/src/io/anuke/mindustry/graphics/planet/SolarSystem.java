package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.Planets;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;

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
        displayed = planet;
        sun = Planets.sun == null ? planet : Planets.sun;
        moon = Planets.moon == null ? planet : Planets.moon;

        light.set(planet.visual.sunDirection).nor();
        sunPosition.set(light).scl(planet.visual.sunDistance);
        moonPosition.set(moon.visual.moonPosition);

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
