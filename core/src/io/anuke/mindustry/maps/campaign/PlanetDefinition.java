package io.anuke.mindustry.maps.campaign;

/** Defines one campaign body (sun, planet, moon, etc.). */
public class PlanetDefinition{
    public final String name;
    public final String campaign;
    public final float colorR, colorG, colorB;
    public final int gridLongitude, gridLatitude;
    public final int subdivisions;

    /** Parent body in the solar system. Null for the root sun. */
    public PlanetDefinition parent;
    /** Render radius in world units. */
    public float radius = 1f;
    /** Whether this body is a star (unlit/bloomed). */
    public boolean isSun;
    /** Whether this body's orbit ring around its parent is rendered. */
    public boolean displayOrbit = true;
    /** Campaign/terrain generator for this body. Null for visual-only bodies. */
    public PlanetGenerator generator;
    /** Renderer/visual configuration. Content classes such as Planets fill this in. */
    public final PlanetVisual visual = new PlanetVisual();

    public PlanetDefinition(String name, String campaign, float colorR, float colorG, float colorB, int gridLongitude, int gridLatitude, int subdivisions){
        this.name = name;
        this.campaign = campaign;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.gridLongitude = gridLongitude;
        this.gridLatitude = gridLatitude;
        this.subdivisions = subdivisions;
    }
}
