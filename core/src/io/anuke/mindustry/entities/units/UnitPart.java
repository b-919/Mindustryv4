package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * A data-driven extra visual drawn on a unit (propeller, wing, etc.).
 * <p>
 * Parts live on {@link UnitType#parts} and are drawn by {@link UnitRenderer}. The spin phase is
 * accumulated on the unit ({@link BaseUnit#partTime}) so instances never allocate per frame.
 */
public class UnitPart{
    /** Region name, resolved to {@link #region} during {@link UnitType#load()}. */
    public final String regionName;
    public TextureRegion region;
    /** Local offset from the unit centre; rotated with the body. */
    public float x, y;
    /** Extra sprite rotation in degrees, added to the spin. */
    public float rotation;
    /** Whether to also draw a mirrored copy at {@code (-x, y)}. */
    public boolean mirror;
    /** Spin speed in degrees per second; 0 for a static part. */
    public float spin;
    /** If mirrored, whether the mirror copy spins the opposite way. */
    public boolean alternateSpin = true;
    /** Whether the sprite itself rotates with the body (propellers usually do not). */
    public boolean rotateWithBody = false;
    /** Sinusoidal rotation amplitude in degrees (wings); 0 for none. */
    public float wobbleAmplitude;
    /** Sinusoidal rotation period scale, used with {@link #wobbleAmplitude}. */
    public float wobbleScl = 1f;
    /** Whether this part is drawn behind the body (wings) rather than on top. */
    public boolean behind = false;

    public UnitPart(String regionName, float x, float y){
        this.regionName = regionName;
        this.x = x;
        this.y = y;
    }
}
