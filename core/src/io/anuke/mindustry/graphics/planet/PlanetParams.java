package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;

/** Per-frame state shared by the planet renderer and every mesh in the scene. */
public class PlanetParams{
    /** Body currently being displayed. */
    public PlanetDefinition planet;
    /** Sector highlighted by the UI, if any. */
    public Sector selected;

    /** Screen-space viewport of the render target. */
    public float x, y, width, height;
    /** Camera orbit angles and zoom. */
    public float rotLon, rotLat, zoom;

    /** Animation clock in seconds. */
    public float time;
    /** Accumulated cloud rotation in radians. */
    public float cloudRotation;

    /** Zoom-dependent alphas. */
    public float gridAlpha = 1f, sectorAlpha = 1f, cloudAlpha = 0f, atmosphereAlpha = 0f;

    /** Projection matrix for this frame (camera.combined snapshot). */
    public final Matrix4 projection = new Matrix4();
    /** Vertex jitter amount and screen-space grid resolution. */
    public float jitter;
    public float jitterResolutionX = 1f, jitterResolutionY = 1f;
    /** Direction *towards* the light source, in world space. */
    public final Vector3 light = new Vector3();
    public final Vector3 camPos = new Vector3();
    public final Vector3 camDir = new Vector3();
}
