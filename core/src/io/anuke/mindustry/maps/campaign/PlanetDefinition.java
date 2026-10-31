package io.anuke.mindustry.maps.campaign;

/** Defines one campaign body (sun, planet, moon, etc.). */
public class PlanetDefinition{
    public final String name;
    public final String campaign;
    public final float colorR, colorG, colorB;
    public final int gridLongitude, gridLatitude;
    public final int subdivisions;

    /** Solar system this body belongs to. Set when the body is added to its system. */
    public SolarSystemDefinition system;
    /** Parent body in the solar system. Null for the root sun. */
    public PlanetDefinition parent;
    /** Render radius in world units. */
    public float radius = 1f;

    // orbit controller.
    /** Starting offset around the orbit axis, in degrees. */
    public float orbitStart = 0f;
    /** Orbit plane tilt in degrees. 0 orbits around the parent's Y axis. */
    public float orbitTilt = 0f;
    /** Orbit angular speed in degrees per second. Negative reverses the direction. */
    public float orbitSpeed = 0f;
    /** Planet self-rotation in degrees per second. 0.25 = one 24-minute day/night cycle. */
    public float rotationSpeed = 0.25f;
    /** Multiplier for cloud-layer rotation. 0 stops clouds, negative reverses them. */
    public float cloudRotationMultiplier = 1f;
    /** Orbit radius override. 0 derives the radius from sunDistance/moonPosition/orbitPosition. */
    public float orbitDistance = 0f;
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
