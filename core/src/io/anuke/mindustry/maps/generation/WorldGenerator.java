package io.anuke.mindustry.maps.generation;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.OreBlocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.maps.MapTileData;
import io.anuke.mindustry.maps.MapTileData.TileDataMarker;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.missions.Mission;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.blocks.OreBlock;
import io.anuke.ucore.noise.RidgedPerlin;
import io.anuke.ucore.noise.Simplex;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.ucore.util.Structs;

import static io.anuke.mindustry.Vars.*;


public class WorldGenerator{
    private static final int baseSeed = 0;
    private int oreIndex = 0;

    public Simplex sim = new Simplex(baseSeed);
    public Simplex sim2 = new Simplex(baseSeed + 1);
    public Simplex sim3 = new Simplex(baseSeed + 2);
    public RidgedPerlin rid = new RidgedPerlin(baseSeed + 4, 1);
    public SeedRandom random = new SeedRandom(baseSeed + 3);

    public float elevationDensity = 6.1f;
    public float temperatureScale = 1100f;
    public float lakeFactor = 0.15f;
    public float ridgeScale = 400f;
    public float treeDensity = 0.0145f;
    public float deadTreeDensity = 0.0075f;
    public float frozenTreeDensity = 0.0045f;
    public float oreThreshold = 0.23f;
    public float oreThreshold2 = 0.32f;
    public boolean genOres = true;

    private GenResult result = new GenResult();
    private ObjectMap<Block, Block> decoration;

    public WorldGenerator(){
        decoration = Structs.map(
            Blocks.grass, Blocks.shrub,
            Blocks.stone, Blocks.rock,
            Blocks.ice, Blocks.icerock,
            Blocks.snow, Blocks.icerock,
            Blocks.blackstone, Blocks.blackrock
        );
    }

    /**Loads raw map tile data into a Tile[][] array, setting up multiblocks, cliffs and ores. */
    public void loadTileData(Tile[][] tiles, MapTileData data, boolean genOres, int seed, String tech){
        data.position(0, 0);
        TileDataMarker marker = data.newDataMarker();

        for(int y = 0; y < data.height(); y++){
            for(int x = 0; x < data.width(); x++){
                data.read(marker);

                tiles[x][y] = new Tile(x, y, marker.floor, (short)(marker.wall == Blocks.blockpart.id ? 0 : marker.wall), marker.rotation, marker.team, marker.elevation);
            }
        }

        prepareTiles(tiles);

        generateOres(tiles, seed, genOres, null, tech);
    }

    /**'Prepares' a tile array by:<br>
    * - setting up multiblocks<br>
    * - updating cliff data<br>
    * - removing ores on cliffs<br>
    * Usually used before placing structures on a tile array.*/
    public void prepareTiles(Tile[][] tiles){

        //find multiblocks
        LongArray multiblocks = new LongArray();

        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                Tile tile = tiles[x][y];

                if(tile.block().isMultiblock()){
                    multiblocks.add(tile.packedPosition());
                }
            }
        }

        //place multiblocks now
        for(int i = 0; i < multiblocks.size; i++){
            long pos = multiblocks.get(i);

            int x = (int)(pos >> 32);
            int y = (int)(pos & 0xFFFFFFFFL);

            Block result = tiles[x][y].block();
            Team team = tiles[x][y].getTeam();

            int offsetx = -(result.size - 1) / 2;
            int offsety = -(result.size - 1) / 2;

            for(int dx = 0; dx < result.size; dx++){
                for(int dy = 0; dy < result.size; dy++){
                    int worldx = dx + offsetx + x;
                    int worldy = dy + offsety + y;
                    if(!(worldx == x && worldy == y)){
                        Tile toplace = world.tile(worldx, worldy);
                        if(toplace != null){
                            toplace.setLinked((byte) (dx + offsetx), (byte) (dy + offsety));
                            toplace.setTeam(team);
                        }
                    }
                }
            }
        }

        //update cliffs, occlusion data
        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                Tile tile = tiles[x][y];

                tile.updateOcclusion();

                //fix things on cliffs that shouldn't be
                if(tile.block() != Blocks.air && tile.hasCliffs() && !tile.block().isMultiblock() && tile.block() != Blocks.blockpart){
                    tile.setBlock(Blocks.air);
                }

                //remove ore veins on cliffs
                if(tile.floor() instanceof OreBlock && tile.hasCliffs()){
                    tile.setFloor(((OreBlock)tile.floor()).base);
                }
            }
        }
    }

    public void playRandomMap(){
        ui.loadLogic(() -> {
            world.setSector(null);
            logic.reset();

            int sx = (short)Mathf.range(Short.MAX_VALUE/2);
            int sy = (short)Mathf.range(Short.MAX_VALUE/2);
            int width = 380;
            int height = 380;
            Array<GridPoint2> spawns = new Array<>();
            Array<Item> ores = Item.getAllOres(state.techTree);

            if(state.mode.isPvp){
                int scaling = 10;
                spawns.add(new GridPoint2(width/scaling, height/scaling));
                spawns.add(new GridPoint2(width - 1 - width/scaling, height - 1 - height/scaling));
            }else{
                spawns.add(new GridPoint2(width/2, height/2));
            }

            Tile[][] tiles = world.createTiles(width, height);
            world.setMap(new Map("Generated Map", width, height));
            world.beginMapLoad();

            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    generateTile(result, sx, sy, x, y, true, spawns, ores);
                    tiles[x][y] = new Tile(x, y, result.floor.id, result.wall.id, (byte)0, (byte)0, result.elevation);
                }
            }

            spawnTrees(tiles);

            prepareTiles(tiles);

            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    Tile tile = tiles[x][y];

                    byte elevation = tile.getElevation();

                    for(GridPoint2 point : Geometry.d4){
                        if(!Structs.inBounds(x + point.x, y + point.y, width, height)) continue;
                        if(tiles[x + point.x][y + point.y].getElevation() < elevation){

                            if(sim2.octaveNoise2D(1, 1, 1.0 / 8, x, y) > 0.8){
                                tile.setElevation(-1);
                            }
                            break;
                        }
                    }
                }
            }

            world.setBlock(tiles[spawns.get(0).x][spawns.get(0).y], StorageBlocks.core, Team.blue);

            if(state.mode.isPvp){
                world.setBlock(tiles[spawns.get(1).x][spawns.get(1).y], StorageBlocks.core, Team.red);
            }else if(state.mode == GameMode.customAttackMode){
                world.applyCustomAttackFortress();
            }

            world.endMapLoad();
            logic.play();
        });
    }

    public void generateOres(Tile[][] tiles, long seed, boolean genOres, Array<Item> usedOres, String tech){
        oreIndex = 0;

        if(genOres){
            //ores bound to another tech tree never spawn here
            Array<Item> allowed = Item.getAllOres(tech);

            Array<OreEntry> ores = new Array<>();
            for(Item item : usedOres == null ? allowed : usedOres){
                if(!item.genOre || !item.belongsToTech(tech)) continue;
                ores.add(new OreEntry(item, oreFrequency(item), seed));
            }

            for(int x = 0; x < tiles.length; x++){
                for(int y = 0; y < tiles[0].length; y++){

                    Tile tile = tiles[x][y];

                    if(!tile.floor().hasOres || tile.hasCliffs() || tile.block() != Blocks.air){
                        continue;
                    }

                    for(int i = ores.size - 1; i >= 0; i--){
                        OreEntry entry = ores.get(i);
                        if(entry.noise.octaveNoise2D(1, 0.7, 1f / (4 + entry.index * 2), x, y) / 4f +
                        Math.abs(0.5f - entry.noise.octaveNoise2D(2, 0.7, 1f / (50 + entry.index * 2), x, y)) > 0.48f &&
                        Math.abs(0.5f - entry.noise.octaveNoise2D(1, 1, 1f / (55 + entry.index * 4), x, y)) > 0.22f){
                            tile.setFloor((Floor) OreBlocks.get(tile.floor(), entry.item));
                            break;
                        }
                    }
                }
            }
        }
    }

    public void generateMap(Tile[][] tiles, Sector sector){
        int width = tiles.length, height = tiles[0].length;
        SeedRandom rnd = new SeedRandom(sector.getSeed());
        Generation gena = new Generation(sector, tiles, tiles.length, tiles[0].length, rnd);
        Array<GridPoint2> spawnpoints = sector.currentMission().getSpawnPoints(gena);
        Array<Item> ores = world.sectors.getOres(sector.x, sector.y);

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                GenResult result = generateTile(this.result, sector.x, sector.y, x, y, true, spawnpoints, ores);
                Tile tile = new Tile(x, y, result.floor.id, result.wall.id, (byte)0, (byte)0, result.elevation);
                tiles[x][y] = tile;
            }
        }

        spawnTrees(tiles);

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[x][y];

                byte elevation = tile.getElevation();

                for(GridPoint2 point : Geometry.d4){
                    if(!Structs.inBounds(x + point.x, y + point.y, width, height)) continue;
                    if(tiles[x + point.x][y + point.y].getElevation() < elevation){

                        if(sim2.octaveNoise2D(1, 1, 1.0 / 8, x, y) > 0.8){
                            tile.setElevation(-1);
                        }
                        break;
                    }
                }
            }
        }

        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                Tile tile = tiles[x][y];

                tile.updateOcclusion();
            }
        }

        Generation gen = new Generation(sector, tiles, tiles.length, tiles[0].length, random);

        for(Mission mission : sector.missions){
            mission.generate(gen);
        }

        prepareTiles(tiles);
    }

    /**Spawns trees on fully generated terrain: trees on grass, dead trees on sand, both away from water. */
    public void spawnTrees(Tile[][] tiles){
        int width = tiles.length, height = tiles[0].length;

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[x][y];
                if(tile.block() != Blocks.air){
                    continue;
                }

                Block floor = tile.floor();
                if(floor instanceof OreBlock){
                    floor = ((OreBlock) floor).base;
                }

                if(floor == Blocks.grass){
                    if(random.chance(treeDensity) && hasWaterNear(tiles, x, y, 3.5f)){
                        tile.setBlock(Blocks.tree);
                    }
                }else if(floor == Blocks.sand){
                    if(random.chance(deadTreeDensity) && hasWaterNear(tiles, x, y, 7f)){
                        tile.setBlock(Blocks.deadTree);
                    }
                }
            }
        }
    }

    private boolean hasWaterNear(Tile[][] tiles, int x, int y, float radius){
        int r = (int) Math.ceil(radius);
        for(int dx = -r; dx <= r; dx++){
            for(int dy = -r; dy <= r; dy++){
                if(dx * dx + dy * dy > radius * radius){
                    continue;
                }
                int xx = x + dx, yy = y + dy;
                if(!Structs.inBounds(xx, yy, tiles.length, tiles[0].length)){
                    continue;
                }
                Block floor = tiles[xx][yy].floor();
                if(floor == Blocks.water || floor == Blocks.deepwater || floor == Blocks.infectedWater || floor == Blocks.infectedDeepWater){
                    return false;
                }
            }
        }
        return true;
    }

    public GenResult generateTile(int sectorX, int sectorY, int localX, int localY, boolean detailed){
        return generateTile(result, sectorX, sectorY, localX, localY, detailed, null, null);
    }

    /**
     * Gets the generation result from a specific sector at specific coordinates.
     * @param result where to put the generation results
     * @param sectorX X of the sector in terms of sector coordinates
     * @param sectorY Y of the sector in terms of sector coordinates
     * @param localX X in terms of local sector tile coordinates
     * @param localY Y in terms of local sector tile coordinates
     * @param detailed whether the tile result is 'detailed' (e.g. previews should not be detailed)
     * @param spawnpoints list of player spawnpoints, can be null
     * @return the GenResult passed in with its values modified
     */
    public GenResult generateTile(GenResult result, int sectorX, int sectorY, int localX, int localY, boolean detailed, Array<GridPoint2> spawnpoints, Array<Item> ores){
        int x = sectorX * sectorSize + localX + Short.MAX_VALUE;
        int y = sectorY * sectorSize + localY + Short.MAX_VALUE;

        Block floor;
        Block wall = Blocks.air;

        double ridge = rid.getValue(x, y, 1f / ridgeScale);
        double iceridge = rid.getValue(x+99999, y, 1f / 300f) + sim3.octaveNoise2D(2, 1f, 1f/14f, x, y)/11f;
        double elevation = elevationOf(x, y, detailed);
        double temp =
            + sim3.octaveNoise2D(detailed ? 12 : 9, 0.6, 1f / temperatureScale, x - 120, y);
        double lake = sim2.octaveNoise2D(1, 1, 1f / 110f, x, y);

        elevation -= Math.pow(lake + lakeFactor, 5);

        int lerpDst = 20;
        lerpDst *= lerpDst;
        float minDst = Float.MAX_VALUE;

        if(detailed && spawnpoints != null){
            for(GridPoint2 p : spawnpoints){
                float dst = Vector2.dst2(p.x, p.y, localX, localY);
                minDst = Math.min(minDst, dst);

                if(dst < lerpDst){
                    float targetElevation = Math.max(0.86f, (float)elevationOf(sectorX * sectorSize + p.x + Short.MAX_VALUE, sectorY * sectorSize + p.y + Short.MAX_VALUE, true));
                    elevation = Mathf.lerp((float)elevation, targetElevation, Mathf.clamp(1.5f*(1f-(dst / lerpDst))));
                }
            }
        }

        if(elevation < 0.7){
            floor = Blocks.deepwater;
        }else if(elevation < 0.79){
            floor = Blocks.water;
        }else if(elevation < 0.85){
            floor = Blocks.sand;
        }else if (elevation < 2.5 && temp > 0.5){
            floor = Blocks.sand;
        }else if(temp < 0.42){
            floor = Blocks.snow;
        }else if(temp < 0.5){
            floor = Blocks.stone;
        }else if(temp < 0.6){
            floor = Blocks.grass;
        }else if(temp + ridge/2f < 0.8 || elevation < 1.3){
            floor = Blocks.blackstone;

            if(iceridge > 0.25 && minDst > lerpDst/1.5f){
                elevation ++;
            }
        }else if(minDst > lerpDst/1.5f){
            floor = Blocks.lava;
        }else{
            floor = Blocks.blackstone;
        }

        if(elevation > 3.3 && iceridge > 0.25 && temp < 0.6f){
            elevation ++;
            floor = Blocks.ice;
        }

        if(((Floor)floor).liquidDrop != null){
            elevation = 0;
        }

        if(detailed && wall == Blocks.air && decoration.containsKey(floor) && random.chance(0.03)){
            wall = decoration.get(floor);
        }

        if(detailed && wall == Blocks.air && (floor == Blocks.snow || floor == Blocks.ice) && random.chance(frozenTreeDensity)){
            wall = Blocks.frozenTree;
        }

        if(ores != null && genOres && ((Floor) floor).hasOres){
            int offsetX = x - 4, offsetY = y + 23;
            for(int i = ores.size - 1; i >= 0; i--){
                Item entry = ores.get(i);
                if(Math.abs(0.5f - sim.octaveNoise2D(2, 0.7, 1f / (50 + i * 2), offsetX, offsetY)) > oreThreshold &&
                Math.abs(0.5f - sim2.octaveNoise2D(1, 1, 1f / (40 + i * 4), offsetX, offsetY)) > oreThreshold2){
                    floor = OreBlocks.get(floor, entry);
                    break;
                }
            }
        }

        result.wall = wall;
        result.floor = floor;
        result.elevation = (byte) Math.max(elevation, 0);
        return result;
    }

    double elevationOf(int x, int y, boolean detailed){
        double ridge = rid.getValue(x, y, 1f / ridgeScale);
        return sim.octaveNoise2D(detailed ? 7 : 5, 0.62, 1f / 800, x, y) * elevationDensity - 1 - ridge;
    }

    public static class GenResult{
        public Block floor, wall;
        public byte elevation;
    }

    private static float oreFrequency(Item item){
        if(item == Items.thorium) return 0.26f;
        if(item == Items.titanium) return 0.27f;
        if(item == Items.lead || item == Items.chromium || item == Items.iron || item == Items.uranium) return 0.28f;
        if(item == Items.coal) return 0.284f;
        if(item == Items.scrap) return 0.342f;
        return 0.3f;
    }

    public class OreEntry{
        final float frequency;
        final Item item;
        final Simplex noise;
        final RidgedPerlin ridge;
        final int index;

        OreEntry(Item item, float frequency, long seed){
            this.frequency = frequency;
            this.item = item;
            this.noise = new Simplex(seed + oreIndex);
            this.ridge = new RidgedPerlin((int)(seed + oreIndex), 2);
            this.index = oreIndex++;
        }
    }
}
