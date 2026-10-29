package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.maps.generation.WorldGenerator;

/**
 * Per-planet campaign generator.
 *
 * A planet generator owns both halves of content generation:
 * - the campaign sector layout/missions ({@link CampaignSectorGenerator}),
 * - the terrain generator used for sector maps and for sampling the 3D planet mesh.
 *
 * Add a new planet by subclassing this, implementing its sector methods and returning
 * its terrain generator. Register it with {@link CampaignRegistry} and assign it to a
 * {@link PlanetDefinition}.
 */
public abstract class PlanetGenerator implements CampaignSectorGenerator{
    /** Terrain generator for this planet. Never null. */
    public abstract WorldGenerator terrain();
    /** Global height multiplier applied to this generator's height field. */
    public float meshHeightIntensity = 1f;
    /** Size in tiles of one playable sector map. */
    public int mapSectorSize = 256;
    /** Fixed world-tile scale used for planet mesh sampling; independent of map sector size. */
    public int planetSampleSize = 256;
    /** Set by the planet model before mesh sampling; null outside mesh generation. */
    protected PlanetDefinition planet;
    protected PlanetMap map;
    /** Provides the planet/map context used by the terrain sampling methods. */
    public void attach(PlanetDefinition planet, PlanetMap map){
        this.planet = planet;
        this.map = map;
    }

    /** Size in tiles of one sector map on this planet. */
    public int sectorSize(){
        WorldGenerator terrain = terrain();
        if(terrain != null){
            terrain.sectorSize = mapSectorSize;
        }
        return mapSectorSize;
    }

    /** Height offset in planet radii at a surface direction. */
    public float getHeight(Vector3 direction){
        return 0f;
    }

    /** Albedo color at a surface direction. */
    public void getColor(Vector3 direction, Color out){
        out.set(Color.WHITE);
    }

    /** Emissive color at a surface direction. */
    public void getEmissiveColor(Vector3 direction, Color out){
        out.set(0f, 0f, 0f, 0f);
    }

    /** Whether the surface at this direction is liquid (used to exclude glow). */
    public boolean isLiquid(Vector3 direction){
        return false;
    }
}
