package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.graphics.planet.SunGlowDef;

/** All renderer-facing visual values for a planet body. */
public class PlanetVisual{
    public int terrainDivisions = 6;
    public int cloudDivisions = 5;
    /** Outer atmosphere shell radius, in planet radii. */
    public float atmosphereRadius = 1.24f;
    /** Inner atmosphere shell radius, in planet radii. */
    public float atmosphereInnerRadius = 1f;
    /** Scales the zoom-based atmosphere fade; 1 = full, 0 = invisible. */
    public float atmosphereAlpha = 1f;
    /** Scattering brightness multiplier used by the atmosphere shader. */
    public float atmosphereIntensity = 0.35f;
    /** Mesh subdivisions for the atmosphere shell. */
    public int atmosphereDivisions = 3;
    public float gridLift = 0.035f;
    public float fillLift = 0.030f;
    public float lineLift = 0.034f;
    public float arcLift = 0.070f;
    /** Color/alpha of the sector grid lines. */
    public final Color gridColor = new Color(0.82f, 0.86f, 0.95f, 0.30f);
    /** 0..1 brightness cutoff for bloom sources; higher means only brighter pixels bloom. */
    public float bloomThreshold = 0.85f;
    /** Additive strength of the blurred bloom. */
    public float bloomIntensity = 2.2f;
    /** Number of separable blur passes; higher spreads the halo further. */
    public int bloomBlurPasses = 2;

    /** Whether captured/attack sectors get emissive team-colored glow on their land tiles. */
    public boolean sectorGlow = true;
    /** Emissive strength of sector glow. */
    public float sectorGlowAlpha = 0.45f;
    /** 3D noise scale for the patchy sector glow; higher = smaller blobs. */
    public float sectorGlowScale = 9f;
    /** Noise cutoff; higher = less of each sector glows. */
    public float sectorGlowThreshold = 0.58f;
    /** Fade width at the glow patch edges. */
    public float sectorGlowSoftness = 0.18f;
    /** Player-base glow color; defaults to the player team color. */
    public final Color sectorPlayerGlow = new Color(0.3f, 0.55f, 1f, 1f);
    /** Enemy/attack-sector glow color. */
    public final Color sectorEnemyGlow = new Color(1f, 0.22f, 0.18f, 1f);
    /** Sectors whose terrain is already infected. */
    public final Color sectorInfectedGlow = new Color(0.72f, 0.25f, 1f, 1f);

    /** Sun surface color ramp; the upper end is what feeds the bloom threshold. */
    public final Color sunLowColor = new Color(1f, 0.42f, 0.06f, 1f);
    public final Color sunHighColor = new Color(1f, 0.95f, 0.55f, 1f);
    /** Mesh subdivisions for the sun and its glow shells. */
    public int sunDivisions = 3;
    public final Vector3 sunDirection = new Vector3(0.82f, 0.34f, 0.48f).nor();
    public float sunDistance = 20f;
    public float sunRadius = 2f;
    public final Vector3 moonPosition = new Vector3();
    public float moonRadius = 0.55f;
    public final Vector3 orbitPosition = new Vector3();
    public final Color moonColor = new Color(0.55f, 0.62f, 0.78f, 1f);
    public final Color orbitColor = new Color(0.62f, 0.74f, 1f, 0.12f);
    public final Color atmosphereColor = new Color(0.36f, 0.55f, 0.95f, 1f);
    public final Color sunColor = new Color(1f, 0.62f, 0.18f, 1f);
    public final Array<CloudDef> clouds = new Array<>();
    public final Array<SunGlowDef> sunGlows = new Array<>();

    /*public PlanetVisual(){
        clouds.add(new CloudDef(11, 1.13f, 0.08f, 0.38f, new Color(0.780f, 0.957f, 1f, 0.78f)));
        clouds.add(new CloudDef(1, 1.16f, 0.24f, 0.41f, new Color(0.671f, 0.918f, 0.984f, 0.72f)));
        sunGlows.add(new SunGlowDef(2.55f, 0.18f));
        sunGlows.add(new SunGlowDef(3.60f, 0.07f));
    }*/
}
