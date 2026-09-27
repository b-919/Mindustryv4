package io.anuke.mindustry.core;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.ai.BlockIndexer;
import io.anuke.mindustry.ai.Pathfinder;
import io.anuke.mindustry.ai.WaveSpawner;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.game.EventType.TileChangeEvent;
import io.anuke.mindustry.game.EventType.WorldLoadEvent;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.io.MapIO;
import io.anuke.mindustry.maps.*;
import io.anuke.mindustry.maps.generation.FortressGenerator;
import io.anuke.mindustry.maps.generation.Generation;
import io.anuke.mindustry.maps.generation.WorldGenerator;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.OreBlock;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.EntityQuery;
import io.anuke.ucore.modules.Module;
import io.anuke.ucore.util.*;

import static io.anuke.mindustry.Vars.*;

public class World extends Module{
    private static final int customAttackCorePlacementAttempts = 250;
    public final Maps maps = new Maps();
    public final Sectors sectors = new Sectors();
    public final WorldGenerator generator = new WorldGenerator();
    public final BlockIndexer indexer = new BlockIndexer();
    public final WaveSpawner spawner = new WaveSpawner();
    public final Pathfinder pathfinder = new Pathfinder();

    private Map currentMap;
    private Sector currentSector;
    private Tile[][] tiles;
    private ChunkManager chunkManager;
    private boolean openWorldMode = false;

    private Array<Tile> tempTiles = new ThreadArray<>();
    private boolean generating, invalidMap;

    public World(){
        maps.load();
    }

    @Override
    public void init(){
        sectors.load();
    }

    @Override
    public void dispose(){
        maps.dispose();
    }

    public boolean isInvalidMap(){
        return invalidMap;
    }

    public boolean solid(int x, int y){
        Tile tile = tile(x, y);

        return tile == null || tile.solid();
    }

    public boolean passable(int x, int y){
        Tile tile = tile(x, y);

        return tile != null && tile.passable();
    }

    public boolean wallSolid(int x, int y){
        Tile tile = tile(x, y);
        return tile == null || tile.block().solid;
    }

    public boolean isAccessible(int x, int y){
        return !wallSolid(x, y - 1) || !wallSolid(x, y + 1) || !wallSolid(x - 1, y) || !wallSolid(x + 1, y);
    }

    public Map getMap(){
        return currentMap;
    }

    public Sector getSector(){
        return currentSector;
    }

    public void setSector(Sector currentSector){
        this.currentSector = currentSector;
    }

    public void setMap(Map map){
        this.currentMap = map;
    }

    public boolean isOpenWorld(){
        return openWorldMode;
    }

    public ChunkManager chunks(){
        return chunkManager;
    }

    public void beginOpenWorld(long seed){
        beginOpenWorld(seed, null);
    }

    public void beginOpenWorld(long seed, String saveName){
        openWorldMode = true;
        chunkManager = new ChunkManager(seed, saveName);
        int worldSize = ChunkManager.CHUNK_SIZE * (ChunkManager.RENDER_RADIUS * 2 + 1);
        createTiles(worldSize, worldSize);
        EntityQuery.resizeTree(0, 0, worldSize * tilesize, worldSize * tilesize);

        setMap(new Map("Open World", new MapMeta(0, new ObjectMap<>(), worldSize, worldSize, null), true, () -> null));
    }

    public void endOpenWorld(){
        openWorldMode = false;
        chunkManager = null;
    }

    public int width(){
        if(openWorldMode){
            return ChunkManager.CHUNK_SIZE * (ChunkManager.RENDER_RADIUS * 2 + 1);
        }
        return tiles == null ? 0 : tiles.length;
    }

    public int height(){
        if(openWorldMode){
            return ChunkManager.CHUNK_SIZE * (ChunkManager.RENDER_RADIUS * 2 + 1);
        }
        return tiles == null ? 0 : tiles[0].length;
    }

    public long toPacked(int x, int y){
        return ((long)x << 32) | (y & 0xFFFFFFFFL);
    }

    public Tile tile(long packed){
        int x = (int)(packed >> 32);
        int y = (int)(packed & 0xFFFFFFFFL);
        return tile(x, y);
    }

    public Tile tile(int i){
        int x = i % width();
        int y = i / width();
        return tile(x, y);
    }

    public Tile tile(int x, int y){
        if(openWorldMode){
            return chunkManager.getTileSafe(x, y);
        }
        if(tiles == null){
            return null;
        }
        if(!Structs.inBounds(x, y, tiles)) return null;
        return tiles[x][y];
    }

    public Tile rawTile(int x, int y){
        if(openWorldMode){
            return chunkManager.getTileSafe(x, y);
        }
        return tiles[x][y];
    }

    public Tile peekTile(int x, int y){
        if(openWorldMode){
            if(chunkManager == null) return null;
            return chunkManager.peekTile(x, y);
        }
        if(tiles == null || !Structs.inBounds(x, y, tiles)) return null;
        return tiles[x][y];
    }

    public Tile tileWorld(float x, float y){
        return tile(Mathf.scl2(x, tilesize), Mathf.scl2(y, tilesize));
    }

    public int toTile(float coord){
        return Mathf.scl2(coord, tilesize);
    }

    public Tile[][] getTiles(){
        return tiles;
    }

    private void clearTileEntities(){
        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                if(tiles[x][y] != null && tiles[x][y].entity != null){
                    tiles[x][y].entity.remove();
                }
            }
        }
    }

    /**
     * Resizes the tile array to the specified size and returns the resulting tile array.
     * Only use for loading saves!
     */
    public Tile[][] createTiles(int width, int height){
        if(tiles != null){
            clearTileEntities();

            if(tiles.length != width || tiles[0].length != height){
                tiles = new Tile[width][height];
            }
        }else{
            tiles = new Tile[width][height];
        }

        return tiles;
    }

    /**
     * Call to signify the beginning of map loading.
     * TileChangeEvents will not be fired until endMapLoad().
     */
    public void beginMapLoad(){
        generating = true;
    }

    /**Call to signal the beginning of loading the map with a custom set of tiles.*/
    public void beginMapLoad(Tile[][] tiles){
        this.tiles = tiles;
        generating = true;
    }

    /**
     * Call to signify the end of map loading. Updates tile occlusions and sets up physics for the world.
     * A WorldLoadEvent will be fire.
     */
    public void endMapLoad(){
        if(openWorldMode){
            generating = false;
            Events.fire(new WorldLoadEvent());
            return;
        }

        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                Tile tile = tiles[x][y];
                if(tile == null) continue;
                tile.updateOcclusion();

                if(tile.floor() instanceof OreBlock && tile.hasCliffs()){
                    tile.setFloor(((OreBlock) tile.floor()).base);
                }

                if(tile.entity != null){
                    tile.entity.updateProximity();
                }
            }
        }

        EntityQuery.resizeTree(0, 0, tiles.length * tilesize, tiles[0].length * tilesize);

        generating = false;
        Events.fire(new WorldLoadEvent());
    }

    public boolean isGenerating(){
        return generating;
    }

    /**Loads up a sector map. This does not call play(), but calls reset().*/
    public void loadSector(Sector sector){
        currentSector = sector;
        state.difficulty = sectors.getDifficulty(sector);
        state.mode = sector.currentMission().getMode();
        state.resetCustomSettings();
        Timers.mark();
        Timers.mark();

        logic.reset();

        beginMapLoad();

        int width = sectorSize, height = sectorSize;

        Tile[][] tiles = createTiles(width, height);

        Map map = new Map("Sector " + sector.x + ", " + sector.y, new MapMeta(0, new ObjectMap<>(), width, height, null), true, () -> null);
        setMap(map);

        EntityQuery.resizeTree(0, 0, width * tilesize, height * tilesize);

        generator.generateMap(tiles, sector);

        endMapLoad();
    }

    public void loadMap(Map map){
        currentSector = null;
        beginMapLoad();
        this.currentMap = map;

        int width = map.meta.width, height = map.meta.height;

        createTiles(width, height);

        EntityQuery.resizeTree(0, 0, width * tilesize, height * tilesize);

        try{
            String tech = map.meta.tags.get("tech", "");
            state.techTree = tech.isEmpty() || tech.equals(io.anuke.mindustry.game.TechTree.defaultTech) ? null : tech;

            generator.loadTileData(tiles, MapIO.readTileData(map, true), map.meta.hasOreGen(), Mathf.random(99999), state.techTree);
            state.darkness = Float.parseFloat(map.meta.tags.get("darkness", "0"));

            if(!headless && renderer != null){
                renderer.weather.setRain(map.meta.tags.get("rain", "0").equals("1"));
            }
        } catch(Exception e){
            Log.err(e);
            if(!headless){
                ui.showError("$text.map.invalid");
                threads.runDelay(() -> state.set(State.menu));
                invalidMap = true;
            }
            generating = false;
            return;
        }

        if(state.mode == GameMode.customAttackMode){
            applyCustomAttackFortress();
        }

        endMapLoad();

        invalidMap = false;

        if(!headless){
            if(state.teams.get(players[0].getTeam()).cores.size == 0){
                ui.showError("$text.map.nospawn");
                invalidMap = true;
            }else if(state.mode.isPvp){
                invalidMap = true;
                for(Team team : Team.all){
                    if(state.teams.get(team).cores.size != 0 && team != players[0].getTeam()){
                        invalidMap = false;
                    }
                }
                if(invalidMap){
                    ui.showError("$text.map.nospawn.pvp");
                }
            }
        }else{
            invalidMap = false;
        }

        if(invalidMap) threads.runDelay(() -> state.set(State.menu));

    }

    public void notifyChanged(Tile tile){
        if(!generating){
            threads.runDelay(() -> Events.fire(new TileChangeEvent(tile)));
        }
    }

    public void applyCustomAttackFortress(){
        Tile blueCore = null;
        Array<Tile> enemyCores = new Array<>();
        EnumSet<Team> enemyTeams = state.teams.enemiesOf(defaultTeam);

        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                Tile tile = tiles[x][y];
                if(tile == null || tile.block() != StorageBlocks.core) continue;

                if(tile.getTeam() == defaultTeam){
                    if(blueCore == null){
                        blueCore = tile;
                    }
                }else if(enemyTeams.contains(tile.getTeam())){
                    enemyCores.add(tile);
                }
            }
        }

        if(blueCore == null){
            return;
        }

        Generation generation = new Generation(null, tiles, width(), height(), new SeedRandom(Mathf.random(99999)));
        if(enemyCores.size == 0){
            Tile generatedEnemyCore = findCustomAttackEnemyCore(generation, blueCore);
            if(generatedEnemyCore == null){
                return;
            }

            placeCustomAttackEnemyCore(generation, generatedEnemyCore, state.enemyTeam);
            enemyCores.add(generatedEnemyCore);
        }

        for(Tile enemyCore : enemyCores){
            new FortressGenerator().generate(generation, enemyCore.getTeam(), blueCore.x, blueCore.y, enemyCore.x, enemyCore.y);
        }
    }

    private Tile findCustomAttackEnemyCore(Generation generation, Tile playerCore){
        float minDistance = 650f;

        for(int i = 0; i < customAttackCorePlacementAttempts; i++){
            Tile tile = generation.tile(generation.random.nextInt(generation.width), generation.random.nextInt(generation.height));
            if(!canPlaceCustomAttackEnemyCore(tile, playerCore)){
                continue;
            }

            float dst = Vector2.dst(playerCore.drawx(), playerCore.drawy(), tile.drawx(), tile.drawy());
            if(dst >= minDistance){
                return tile;
            }
        }

        for(int x = 0; x < generation.width; x++){
            for(int y = 0; y < generation.height; y++){
                Tile tile = generation.tile(x, y);
                if(!canPlaceCustomAttackEnemyCore(tile, playerCore)){
                    continue;
                }

                float dst = Vector2.dst(playerCore.drawx(), playerCore.drawy(), tile.drawx(), tile.drawy());
                if(dst >= minDistance){
                    return tile;
                }
            }
        }

        return null;
    }

    private boolean canPlaceCustomAttackEnemyCore(Tile tile, Tile playerCore){
        return tile != null && tile != playerCore && !tile.floor().isLiquid;
    }

    private void placeCustomAttackEnemyCore(Generation generation, Tile tile, Team team){
        generation.setBlock(tile.x, tile.y, StorageBlocks.core, team);
        Tile placed = generation.tile(tile.x, tile.y);
        if(placed != null){
            state.teams.get(team).cores.add(placed);
        }
    }

    public void removeBlock(Tile tile){
        if(!tile.block().isMultiblock() && !tile.isLinked()){
            tile.setBlock(Blocks.air);
        }else{
            Tile target = tile.target();
            Array<Tile> removals = target.getLinkedTiles(tempTiles);
            for(Tile toremove : removals){
                //note that setting a new block automatically unlinks it
                if(toremove != null) toremove.setBlock(Blocks.air);
            }
        }
    }

    public void setBlock(Tile tile, Block block, Team team){
        tile.setBlock(block, team);
        if(block.isMultiblock()){
            int offsetx = -(block.size - 1) / 2;
            int offsety = -(block.size - 1) / 2;

            for(int dx = 0; dx < block.size; dx++){
                for(int dy = 0; dy < block.size; dy++){
                    int worldx = dx + offsetx + tile.x;
                    int worldy = dy + offsety + tile.y;
                    if(!(worldx == tile.x && worldy == tile.y)){
                        Tile toplace = world.tile(worldx, worldy);
                        if(toplace != null){
                            toplace.setLinked((byte) (dx + offsetx), (byte) (dy + offsety));
                            toplace.setTeam(team);
                        }
                    }
                }
            }
        }
    }

    public int transform(int packed, int oldWidth, int oldHeight, int newWidth, int shiftX, int shiftY){
        int x = packed % oldWidth;
        int y = packed / oldWidth;
        if(!Structs.inBounds(x, y, oldWidth, oldHeight)) return -1;
        x += shiftX;
        y += shiftY;
        return y*newWidth + x;
    }

    public long transform(long packed, int oldWidth, int oldHeight, int newWidth, int shiftX, int shiftY){
        int x = (int)(packed >> 32);
        int y = (int)(packed & 0xFFFFFFFFL);
        if(!Structs.inBounds(x, y, oldWidth, oldHeight)) return -1L;
        x += shiftX;
        y += shiftY;
        return ((long)x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * Raycast, but with world coordinates.
     */
    public GridPoint2 raycastWorld(float x, float y, float x2, float y2){
        return raycast(Mathf.scl2(x, tilesize), Mathf.scl2(y, tilesize),
                Mathf.scl2(x2, tilesize), Mathf.scl2(y2, tilesize));
    }

    /**
     * Input is in block coordinates, not world coordinates.
     *
     * @return null if no collisions found, block position otherwise.
     */
    public GridPoint2 raycast(int x0f, int y0f, int x1, int y1){
        int x0 = x0f;
        int y0 = y0f;
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx - dy;
        int e2;
        while(true){

            if(!passable(x0, y0)){
                return Tmp.g1.set(x0, y0);
            }
            if(x0 == x1 && y0 == y1) break;

            e2 = 2 * err;
            if(e2 > -dy){
                err = err - dy;
                x0 = x0 + sx;
            }

            if(e2 < dx){
                err = err + dx;
                y0 = y0 + sy;
            }
        }
        return null;
    }

    public void raycastEachWorld(float x0, float y0, float x1, float y1, Raycaster cons){
        raycastEach(toTile(x0), toTile(y0), toTile(x1), toTile(y1), cons);
    }

    public void raycastEach(int x0f, int y0f, int x1, int y1, Raycaster cons){
        int x0 = x0f;
        int y0 = y0f;
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx - dy;
        int e2;
        while(true){

            if(cons.accept(x0, y0)) break;
            if(x0 == x1 && y0 == y1) break;

            e2 = 2 * err;
            if(e2 > -dy){
                err = err - dy;
                x0 = x0 + sx;
            }

            if(e2 < dx){
                err = err + dx;
                y0 = y0 + sy;
            }
        }
    }

    public interface Raycaster{
        boolean accept(int x, int y);
    }
}
