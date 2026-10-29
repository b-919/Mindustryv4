package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongMap;
import io.anuke.mindustry.graphics.mesh.PlanetGrid;
import io.anuke.mindustry.graphics.mesh.PlanetGrid.Corner;
import io.anuke.mindustry.graphics.mesh.PlanetGrid.Ptile;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.Sectors;

/**Client-side map of a planet's sector regions.*/

public class PlanetMap{
    public final PlanetDefinition definition;
    public final PlanetGrid grid;
    public final Region[] regions;

    private final Region[] regionByTile;
    private final LongMap<Region> byCoord = new LongMap<>();
    private final LongMap<Region> sectorCache = new LongMap<>();
    private final Vector3 tmp = new Vector3();

    public PlanetMap(PlanetDefinition definition){
        this.definition = definition;
        this.grid = PlanetGrid.create(Math.max(1, definition.subdivisions));
        this.regions = new Region[grid.tiles.length];
        this.regionByTile = new Region[grid.tiles.length];

        for(int i = 0; i < grid.tiles.length; i++){
            Region region = new Region(grid.tiles[i], i);
            regions[i] = region;
            regionByTile[grid.tiles[i].id] = region;
        }

        assignCoordinates();

        for(Region region : regions){
            Ptile[] tiles = region.tile.tiles;
            region.neighbors = new Region[tiles.length];
            for(int i = 0; i < tiles.length; i++){
                region.neighbors[i] = regionByTile[tiles[i].id];
            }

            float minDot = 1f;
            for(Corner corner : region.tile.corners){
                minDot = Math.min(minDot, region.center.dot(corner.v));
            }
            region.angularRadius = (float)Math.acos(MathUtils.clamp(minDot, -1f, 1f)) * 1.12f;
        }
    }

    /** Creates any missing sector objects for all regions. Existing sectors are reused. */
    public void ensureSectors(Sectors sectors){
        for(Region region : regions){
            Sector sector = sectors.get(region.x, region.y);
            if(sector == null){
                sector = sectors.createSectorSilent(region.x, region.y);
            }
            region.sector = sector;
        }
    }

    /** Finds the region that owns a sector coordinate, falling back to the closest region. */
    public Region regionFor(Sector sector){
        if(sector == null) return null;
        long key = key(sector.x, sector.y);
        Region region = byCoord.get(key);
        if(region != null) return region;

        region = sectorCache.get(key);
        if(region == null){
            region = nearest(dirFor(sector.x, sector.y));
            if(region != null) sectorCache.put(key, region);
        }
        return region;
    }

    /** Finds the region whose center direction is closest to the supplied direction. */
    public Region nearest(Vector3 direction){
        Region best = null;
        float bestDot = -2f;

        for(Region region : regions){
            float dot = region.center.dot(direction);
            if(dot > bestDot){
                bestDot = dot;
                best = region;
            }
        }
        return best;
    }

    /** Projects a ray hit on the planet onto the nearest sector region. */
    public Region regionAt(Vector3 hitDirection){
        tmp.set(hitDirection).nor();
        Region region = nearest(tmp);
        if(region == null) return null;

        float dot = MathUtils.clamp(region.center.dot(tmp), -1f, 1f);
        if(Math.acos(dot) > region.angularRadius){
            return null;
        }
        return region;
    }

    /** @return whether this sector can currently be entered. */
    public boolean isUnlocked(Sector sector){
        if(sector == null) return false;
        if(sector.complete || (sector.x == 0 && sector.y == 0)) return true;

        Region region = regionFor(sector);
        if(region == null) return true;

        for(Region neighbor : region.neighbors){
            if(neighbor.sector != null && neighbor.sector.complete){
                return true;
            }
        }
        return false;
    }

    /** Converts a unit-sphere direction to the sector grid coordinates used by the world generator. */
    public int[] directionToSector(Vector3 direction){
        return coordFor(direction);
    }

    public Vector3 directionForSector(int x, int y){
        return dirFor(x, y);
    }

    private void assignCoordinates(){
        Array<int[]> pins = generator().sectorPresets();

        if(pins != null){
            for(int[] pin : pins){
                Vector3 direction = dirFor(pin[0], pin[1]);
                Region best = null;
                float bestDot = -2f;

                for(Region region : regions){
                    if(region.pinned) continue;
                    float dot = region.center.dot(direction);
                    if(dot > bestDot){
                        bestDot = dot;
                        best = region;
                    }
                }

                if(best != null){
                    best.pinned = true;
                    best.x = pin[0];
                    best.y = pin[1];
                    byCoord.put(key(best.x, best.y), best);
                }
            }
        }

        for(Region region : regions){
            if(region.pinned) continue;

            int[] coord = coordFor(region.center);
            if(place(region, coord[0], coord[1])) continue;

            boolean placed = false;
            for(int radius = 1; radius <= 12 && !placed; radius++){
                for(int dy = -radius; dy <= radius && !placed; dy++){
                    for(int dx = -radius; dx <= radius && !placed; dx++){
                        if(Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
                        if(place(region, coord[0] + dx, coord[1] + dy)){
                            placed = true;
                        }
                    }
                }
            }

            if(!placed){
                // Extremely unlikely; scan the full grid for a free coordinate.
                outer:
                for(int y = -definition.gridLatitude / 2; y < definition.gridLatitude / 2; y++){
                    for(int x = -definition.gridLongitude / 2; x < definition.gridLongitude / 2; x++){
                        if(place(region, x, y)){
                            placed = true;
                            break outer;
                        }
                    }
                }
            }
        }
    }

    private boolean place(Region region, int x, int y){
        if(x < -definition.gridLongitude / 2 || x >= definition.gridLongitude / 2) return false;
        if(y < -definition.gridLatitude / 2 || y >= definition.gridLatitude / 2) return false;

        long key = key(x, y);
        if(byCoord.containsKey(key)) return false;

        region.x = x;
        region.y = y;
        byCoord.put(key, region);
        return true;
    }

    private int[] coordFor(Vector3 direction){
        float lon = MathUtils.atan2(direction.z, direction.x);
        float lat = (float)Math.asin(MathUtils.clamp(direction.y, -1f, 1f));

        int x = (int)Math.floor((lon + MathUtils.PI) / MathUtils.PI2 * definition.gridLongitude) - definition.gridLongitude / 2;
        int y = (int)Math.floor((lat + MathUtils.PI / 2f) / MathUtils.PI * definition.gridLatitude) - definition.gridLatitude / 2;

        x = MathUtils.clamp(x, -definition.gridLongitude / 2, definition.gridLongitude / 2 - 1);
        y = MathUtils.clamp(y, -definition.gridLatitude / 2, definition.gridLatitude / 2 - 1);
        return new int[]{x, y};
    }

    private Vector3 dirFor(int x, int y){
        float lon = ((x + definition.gridLongitude / 2f + 0.5f) / definition.gridLongitude) * MathUtils.PI2 - MathUtils.PI;
        float lat = ((y + definition.gridLatitude / 2f + 0.5f) / definition.gridLatitude) * MathUtils.PI - MathUtils.PI / 2f;
        float cosLat = MathUtils.cos(lat);

        return new Vector3(cosLat * MathUtils.cos(lon), MathUtils.sin(lat), cosLat * MathUtils.sin(lon)).nor();
    }

    private CampaignSectorGenerator generator(){
        return CampaignRegistry.generator(definition.campaign);
    }

    private static long key(int x, int y){
        return (((long)x) << 32) | (y & 0xffffffffL);
    }

    public static class Region{
        public final Ptile tile;
        public final Vector3 center = new Vector3();
        public final int index;

        public int x, y;
        public float angularRadius;
        public boolean pinned;
        public Sector sector;
        public Region[] neighbors;

        public Region(Ptile tile, int index){
            this.tile = tile;
            this.index = index;
            this.center.set(tile.v).nor();
        }

        public boolean hasSector(){
            return sector != null;
        }
    }
}
