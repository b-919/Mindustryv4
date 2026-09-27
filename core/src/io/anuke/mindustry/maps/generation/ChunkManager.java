package io.anuke.mindustry.maps.generation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.LongSet;
import com.badlogic.gdx.utils.TimeUtils;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.OreBlocks;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.EventType.TileChangeEvent;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.blocks.BlockPart;
import io.anuke.mindustry.world.blocks.OreBlock;
import io.anuke.mindustry.world.blocks.storage.CoreBlock;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.noise.RidgedPerlin;
import io.anuke.ucore.noise.Simplex;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.ucore.util.Strings;

import static io.anuke.mindustry.Vars.*;

public class ChunkManager{
    public static final int CHUNK_SIZE = 32;
    public static final int LOAD_RADIUS = 3;
    public static final int RENDER_RADIUS = 5;
    public static final int UNLOAD_CHECK_INTERVAL = 120;
    /** Frozen chunks beyond this Chebyshev chunk distance are compressed into cold storage. */
    public static final int COLD_RADIUS = RENDER_RADIUS * 2 + 2;
    /** Ticks between cold-storage sweeps. */
    public static final int COLD_CHECK_INTERVAL = 600;
    /** Max chunks generated, disk-loaded, or woken from cold storage proactively per tick; prevents frame spikes when crossing chunk borders. */
    private static final int MAX_CHUNK_LOADS_PER_TICK = 1;
    /** Network clients drop chunks beyond this Chebyshev distance to the local player to bound memory (Minetest-style). Far chunks are re-fetched from the server on return. */
    public static final int CLIENT_EVICT_RADIUS = RENDER_RADIUS + 2;

    /** Chunks farther than this Chebyshev chunk distance from every player are never generated on demand.
     * Far tile access returns the synthetic ocean tile instead, so build/extend systems (e.g. far AI base
     * expansion) cannot force runaway generation outside the playable ring. Covers {@link #LOAD_RADIUS}
     * and the network PUSH ({@code RENDER_RADIUS+1}) / {@link #CLIENT_EVICT_RADIUS} radii. */
    public static final int GENERATE_RADIUS = RENDER_RADIUS + 2;

    private final LongMap<WorldChunk> loadedChunks = new LongMap<>();
    private final LongSet coreChunks = new LongSet();
    private final LongArray sweepScratch = new LongArray();
    /** Scratch chunk centers of every player on this machine (host local player + connected clients); drives load/wake/unload/cold sweeps. */
    private final Array<int[]> playerCenters = new Array<>(false, 8, int[].class);
    private final long worldSeed;
    private final Simplex sim;
    private final Simplex sim2;
    private final Simplex sim3;
    private final RidgedPerlin rid;
    private final SeedRandom random;
    private final Simplex[] oreNoises;
    /** Ores allowed by the active tech tree, refreshed when the tree changes. */
    private final Array<Item> activeOres = new Array<>();
    /** Index into {@link #oreNoises} for each entry of {@link #activeOres}; keeps noise tied to the ore, not its position in the filtered list. */
    private final IntArray activeOreNoise = new IntArray();
    private String activeOreTech = null;

    private int lastUnloadCheck = 0;
    private int lastColdCheck = 0;
    private boolean generatingChunk = false;
    private String saveName;
    private final OpenWorldSaveManager saveManager = new OpenWorldSaveManager();

    /** When true this manager is a network client: chunks are supplied by the server and never regenerated locally. */
    private boolean netMode = false;
    /** Chunk keys already requested from the server but not yet received; prevents request spam. */
    private final LongSet requestedChunks = new LongSet();

    /** Incremented every time a new chunk is loaded/generated, used by systems tracking chunk changes. */
    public int chunkLoadCounter = 0;

    /** Open world debug stats: milliseconds spent generating/loading chunks (see the "openworld-debug" setting). */
    public static float lastChunkGenMs = 0f;
    public static float totalChunkGenMs = 0f;
    public static int chunksGenerated = 0;

    /** Tiles restored from disk that are already fog-discovered; swapped/drained by the FogRenderer. */
    private Array<Tile> discoverFill = new Array<>(), discoverDrain = new Array<>();
    /** Synthetic view-range blocks restored from disk; swapped/drained by the FogRenderer. */
    private Array<Tile> viewFill = new Array<>(), viewDrain = new Array<>();

    public ChunkManager(long seed){
        this(seed, null);
    }

    public ChunkManager(long seed, String saveName){
        this.worldSeed = seed;
        this.saveName = saveName;
        this.sim = new Simplex(seed);
        this.sim2 = new Simplex(seed + 1);
        this.sim3 = new Simplex(seed + 2);
        this.rid = new RidgedPerlin((int)(seed + 4), 1);
        this.random = new SeedRandom(seed + 3);
        int oreCount = Item.getAllOres().size;
        this.oreNoises = new Simplex[oreCount];
        for(int i = 0; i < oreCount; i++){
            this.oreNoises[i] = new Simplex(seed + 100 + i);
        }

        //mark chunks with player-made changes as modified so they are never unloaded unsaved
        Events.on(TileChangeEvent.class, event -> {
            if(generatingChunk || !world.isOpenWorld() || world.chunks() != this) return;
            int cx = MathUtils.floor((float)event.tile.x / CHUNK_SIZE);
            int cy = MathUtils.floor((float)event.tile.y / CHUNK_SIZE);
            WorldChunk chunk = loadedChunks.get(packKey(cx, cy));
            if(chunk != null){
                chunk.modified = true;
                chunk.pathStamp++;
                if(Net.server()) chunk.netDirty = true;
            }
        });
    }

    public long getSeed(){
        return worldSeed;
    }

    private void updateActiveOres(){
        String tech = state.techTree == null ? "" : state.techTree;
        if(activeOreTech != null && activeOreTech.equals(tech)) return;

        activeOreTech = tech;
        activeOres.clear();
        activeOreNoise.clear();

        int index = 0;
        for(Item item : content.items()){
            if(!item.genOre) continue;
            if(item.belongsToTech(state.techTree)){
                activeOres.add(item);
                activeOreNoise.add(index);
            }
            index++;
        }
    }

    private Simplex oreNoise(int slot){
        if(oreNoises.length == 0) return sim;
        int index = activeOreNoise.get(slot);
        return index >= 0 && index < oreNoises.length ? oreNoises[index] : oreNoises[0];
    }

    public boolean isNetMode(){
        return netMode;
    }

    public void setNetMode(boolean netMode){
        this.netMode = netMode;
    }

    /** Returns the set of chunk payloads that have changed since the last successful push to clients, and clears the dirty flags. */
    public Array<byte[]> takeDirtyChunkPayloads(){
        Array<byte[]> result = new Array<>();
        for(WorldChunk chunk : loadedChunks.values()){
            if(chunk.tiles != null && chunk.netDirty){
                byte[] payload = saveManager.serializeChunk(chunk);
                if(payload != null){
                    result.add(payload);
                    chunk.netDirty = false;
                }
            }
        }
        return result;
    }

    /** Serializes a single chunk by world coordinate; generates it on demand if not loaded. Returns null if it could not be produced. */
    public byte[] serializeChunkAt(int cx, int cy){
        WorldChunk chunk = loadedChunks.get(packKey(cx, cy));
        if(chunk == null){
            chunk = getOrCreateChunk(cx, cy);
        }
        if(chunk == null) return null;
        if(chunk.coldData != null || chunk.frozen){
            wakeChunk(chunk);
        }
        if(chunk.tiles == null) return null;
        return saveManager.serializeChunk(chunk);
    }

    /** Installs a chunk received from the server (or loaded from disk) into the loaded set, replacing any existing one. */
    public void installChunk(ChunkManager.WorldChunk chunk){
        if(chunk == null || chunk.tiles == null) return;
        long key = packKey(chunk.cx, chunk.cy);

        WorldChunk old = loadedChunks.get(key);
        Tile[] prev = old != null && old.tiles != null ? old.tiles : null;
        if(old != null && old != chunk){
            //detach old entities before replacing
            for(int i = 0; i < old.tiles.length; i++){
                TileEntity entity = old.tiles[i].entity;
                if(entity != null && entity.getGroup() != null){
                    entity.remove();
                }
            }
        }

        chunk.restored = true;
        chunk.modified = true;
        chunk.netDirty = false;
        chunk.frozen = false;
        chunk.coldData = null;
        chunk.generated = true;
        chunk.pathStamp++;

        loadedChunks.put(key, chunk);
        requestedChunks.remove(key);
        chunkLoadCounter++;

        generatingChunk = true;
        try{
            updateChunkAndNeighborCliffs(chunk.cx, chunk.cy);
        }finally{
            generatingChunk = false;
        }
        world.indexer.indexChunk(chunk);
        world.pathfinder.onChunkLive(chunk.cx, chunk.cy);

        for(int i = 0; i < chunk.tiles.length; i++){
            Tile tile = chunk.tiles[i];
            tile.updateOcclusion();
            if(tile.entity != null && !tile.entity.isDead()){
                tile.entity.updateProximity();
            }
            if(tile.block() instanceof CoreBlock){
                state.teams.get(tile.getTeam()).cores.add(tile);
            }
        }

        if(prev != null){
            Tile[] tiles = chunk.tiles;
            int n = Math.min(prev.length, tiles.length);
            for(int i = 0; i < n; i++){
                Tile a = prev[i], b = tiles[i];
                if(a == b) continue;
                if(a.block() != b.block() || a.floor() != b.floor() || a.getTeam() != b.getTeam()
                    || a.getElevation() != b.getElevation() || a.getRotation() != b.getRotation()){
                    Events.fire(new TileChangeEvent(b));
                }
            }
        }
    }

    public static long packKey(int cx, int cy){
        return ((long)cx << 32) | (cy & 0xFFFFFFFFL);
    }

    public static int keyCx(long key){
        return (int)(key >> 32);
    }

    public static int keyCy(long key){
        return (int)key;
    }

    public void registerCoreChunks(int cx, int cy){
        for(int dx = -1; dx <= 1; dx++){
            for(int dy = -1; dy <= 1; dy++){
                coreChunks.add(packKey(cx + dx, cy + dy));
            }
        }
    }

    public WorldChunk getOrCreateChunk(int cx, int cy){
        long key = packKey(cx, cy);
        WorldChunk chunk = loadedChunks.get(key);
        if(chunk == null){
            if(generatingChunk){
                return null;
            }

            //network clients never regenerate terrain;
            if(netMode){
                requestChunk(cx, cy);
                return null;
            }
            if(!canAutoGenerate(cx, cy)){
                return null;
            }
            double pedro = Double.MAX_VALUE;
            long start = TimeUtils.nanoTime();
            if(saveName != null){
                chunk = saveManager.loadChunk(saveName, cx, cy);
            }

            if(chunk == null){
                chunk = new WorldChunk(cx, cy);
                chunk.tiles = new Tile[CHUNK_SIZE * CHUNK_SIZE];
                generatingChunk = true;
                try{
                    generateChunkTerrain(chunk);
                }finally{
                    generatingChunk = false;
                }
                chunk.generated = true;
                chunk.pathStamp++;
            }else{
                queueRestoredTiles(chunk);
                chunk.restored = true;
                chunk.pathStamp++;
            }

            loadedChunks.put(key, chunk);
            chunkLoadCounter++;
            if(Net.server()) chunk.netDirty = true;
            updateChunkAndNeighborCliffs(cx, cy);
            world.indexer.indexChunk(chunk);
            world.pathfinder.onChunkLive(cx, cy);
            reportChunkGenerated(cx, cy, TimeUtils.timeSinceNanos(start) / 1000000f);
        }
        chunk.lastAccessFrame = (long)Timers.time();
        return chunk;
    }

    /** Tracks chunk creation statistics and, with the "openworld-debug" setting enabled, reports each chunk
     * creation to the log and, on a local game, to the chat. */
    private void reportChunkGenerated(int cx, int cy, float ms){
        lastChunkGenMs = ms;
        totalChunkGenMs += ms;
        chunksGenerated++;

        if(!Settings.getBool("openworld-debug", false)) return;

        float avg = totalChunkGenMs / Math.max(1, chunksGenerated);
        Log.info("OpenWorld: chunk [{0}, {1}] ready in {2}ms (avg {3}ms, count {4})",
            cx, cy, Strings.toFixed(ms, 1), Strings.toFixed(avg, 1), chunksGenerated);
        if(!headless && ui != null){
            ui.chatfrag.addMessage("[cyan]OpenWorld: chunk [white][" + cx + ", " + cy + "] in [white]" +
                Strings.toFixed(ms, 1) + "ms [gray](avg " + Strings.toFixed(avg, 1) + "ms)", null);
        }
    }

    /** Queues a request to the server for the given chunk (deduplicated). Client-side only. */
    public void requestChunk(int cx, int cy){
        if(!netMode || requestedChunks.contains(packKey(cx, cy))) return;
        requestedChunks.add(packKey(cx, cy));
    }

    /** Returns and clears all chunk keys currently awaiting server data. Client-side only. */
    public Array<long[]> pollChunkRequests(){
        Array<long[]> result = new Array<>();
        for(LongSet.LongSetIterator it = requestedChunks.iterator(); it.hasNext; ){
            long key = it.next();
            result.add(new long[]{keyCx(key), keyCy(key)});
        }
        requestedChunks.clear();
        result.shrink();
        return result;
    }

    /** Queues restored fog data from a disk-loaded chunk so the FogRenderer can paint it without full-window rescans. */
    private void queueRestoredTiles(WorldChunk chunk){
        if(headless || !world.isOpenWorld()) return;
        for(int i = 0; i < chunk.tiles.length; i++){
            Tile tile = chunk.tiles[i];
            if(tile.discovered()){
                discoverFill.add(tile);
            }
            if(tile.block().synthetic() && tile.block().viewRange > 0){
                viewFill.add(tile);
            }
        }
    }

    /** Called on the graphics thread; returns tiles queued by the logic thread since the last call. */
    public Array<Tile> swapDiscoverQueue(){
        Array<Tile> t = discoverFill;
        discoverFill = discoverDrain;
        discoverDrain = t;
        return t;
    }

    /** Called on the graphics thread; returns view-range blocks queued by the logic thread since the last call. */
    public Array<Tile> swapViewQueue(){
        Array<Tile> t = viewFill;
        viewFill = viewDrain;
        viewDrain = t;
        return t;
    }

    /** Flags the chunk containing this tile as modified, so it is kept in memory and saved. */
    public void markModified(int worldX, int worldY){
        int cx = MathUtils.floor((float)worldX / CHUNK_SIZE);
        int cy = MathUtils.floor((float)worldY / CHUNK_SIZE);
        WorldChunk chunk = loadedChunks.get(packKey(cx, cy));
        if(chunk != null) chunk.modified = true;
    }

    /** Returns a tile from an already-loaded chunk without triggering chunk generation. Returns null if the chunk is not loaded. */
    public Tile peekTile(int worldX, int worldY){        int cx = MathUtils.floor((float)worldX / CHUNK_SIZE);
        int cy = MathUtils.floor((float)worldY / CHUNK_SIZE);
        WorldChunk chunk = loadedChunks.get(packKey(cx, cy));
        if(chunk == null || chunk.tiles == null){
            return null;
        }
        int lx = worldX - cx * CHUNK_SIZE;
        int ly = worldY - cy * CHUNK_SIZE;
        if(lx < 0 || lx >= CHUNK_SIZE || ly < 0 || ly >= CHUNK_SIZE){
            return null;
        }
        return chunk.tile(lx, ly);
    }

    public String getSaveName(){
        return saveName;
    }

    public void setSaveName(String name){
        this.saveName = name;
    }

    public OpenWorldSaveManager getSaveManager(){
        return saveManager;
    }

    /** Updates cliffs/occlusion for a newly loaded chunk and recomputes cliff borders on neighbors. */
    private void updateChunkAndNeighborCliffs(int cx, int cy){
        WorldChunk chunk = loadedChunks.get(packKey(cx, cy));
        if(chunk == null || chunk.tiles == null) return;

        updateActiveOres();

        generatingChunk = true;
        try{
            for(int i = 0; i < chunk.tiles.length; i++){
                chunk.tiles[i].updateOcclusion();
            }

            for(int d = 0; d < 4; d++){
                int ncx = cx + Geometry.d4[d].x;
                int ncy = cy + Geometry.d4[d].y;
                WorldChunk neighbor = loadedChunks.get(packKey(ncx, ncy));
                if(neighbor == null || neighbor.tiles == null) continue;

                int ndx = Geometry.d4[d].x;
                int ndy = Geometry.d4[d].y;

                //only the neighbor strip adjacent to the new chunk is affected by it
                for(int s = 0; s < CHUNK_SIZE; s++){
                    int sx = ndx != 0 ? (ndx > 0 ? 0 : CHUNK_SIZE - 1) : s;
                    int sy = ndy != 0 ? (ndy > 0 ? 0 : CHUNK_SIZE - 1) : s;
                    neighbor.tiles[sx + sy * CHUNK_SIZE].updateOcclusion();
                }
            }

            boolean cleanTerrain = !chunk.restored && !chunk.modified;

            if(cleanTerrain){
                generateOresForChunk(chunk);

                for(int i = 0; i < chunk.tiles.length; i++){
                    Tile tile = chunk.tiles[i];
                    if(tile.floor() instanceof OreBlock && tile.hasCliffs()){
                        tile.setFloor(((OreBlock)tile.floor()).base);
                    }
                    if(tile.block() != Blocks.air && tile.hasCliffs() && !tile.block().isMultiblock() && !(tile.block() instanceof BlockPart)){
                        tile.setBlock(Blocks.air);
                    }
                }
            }

            for(int d = 0; d < 4; d++){
                int ncx = cx + Geometry.d4[d].x;
                int ncy = cy + Geometry.d4[d].y;
                WorldChunk neighbor = loadedChunks.get(packKey(ncx, ncy));
                if(neighbor == null || neighbor.tiles == null) continue;

                int ndx = Geometry.d4[d].x;
                int ndy = Geometry.d4[d].y;

                boolean cleanNeighbor = !neighbor.restored && !neighbor.modified;

                for(int lx = 0; lx < CHUNK_SIZE; lx++){
                    for(int ly = 0; ly < CHUNK_SIZE; ly++){
                        boolean onBorder = (ndx != 0 && (ndx > 0 ? lx == 0 : lx == CHUNK_SIZE - 1))
                                         || (ndy != 0 && (ndy > 0 ? ly == 0 : ly == CHUNK_SIZE - 1));
                        if(!onBorder) continue;

                        Tile tile = neighbor.tiles[lx + ly * CHUNK_SIZE];
                        int wx = neighbor.cx * CHUNK_SIZE + lx;
                        int wy = neighbor.cy * CHUNK_SIZE + ly;

                        if(tile.floor() instanceof OreBlock){
                            Floor base = ((OreBlock)tile.floor()).base;
                            if(tile.floor() != base) tile.setFloor(base);
                        }

                        if(cleanNeighbor && tile.floor() instanceof Floor && ((Floor)tile.floor()).hasOres
                            && !tile.hasCliffs() && tile.block() == Blocks.air){
                            int ox = wx + Short.MAX_VALUE;
                            int oy = wy + Short.MAX_VALUE;
                            Floor baseFloor = tile.floor();
                            for(int i = activeOres.size - 1; i >= 0; i--){
                                Item entry = activeOres.get(i);
                                int slot = activeOreNoise.get(i);
                                Simplex noise = oreNoise(i);
                                if(noise.octaveNoise2D(1, 0.7, 1f / (4 + slot * 2), ox, oy) / 4f +
                                    Math.abs(0.5f - noise.octaveNoise2D(2, 0.7, 1f / (50 + slot * 2), ox, oy)) > 0.48f &&
                                    Math.abs(0.5f - noise.octaveNoise2D(1, 1, 1f / (55 + slot * 4), ox, oy)) > 0.22f){
                                    Floor oreFloor = (Floor) OreBlocks.get(baseFloor, entry);
                                    if(tile.floor() != oreFloor) tile.setFloor(oreFloor);
                                    break;
                                }
                            }
                        }

                        if(cleanNeighbor && tile.block() != Blocks.air && tile.hasCliffs() && !tile.block().isMultiblock() && !(tile.block() instanceof BlockPart)){
                            tile.setBlock(Blocks.air);
                        }
                    }
                }
            }
        }finally{
            generatingChunk = false;
        }
    } // god you need to see how this bugged out like hell before

    private void generateOresForChunk(WorldChunk chunk){
        // needs to be called after cliff generation if not the ore will override the cliff block since ores doesn't have cliffs
        int worldStartX = chunk.cx * CHUNK_SIZE;
        int worldStartY = chunk.cy * CHUNK_SIZE;
        updateActiveOres();
        Array<Item> ores = activeOres;

        for(int lx = 0; lx < CHUNK_SIZE; lx++){
            for(int ly = 0; ly < CHUNK_SIZE; ly++){
                int wx = worldStartX + lx;
                int wy = worldStartY + ly;
                int x = wx + Short.MAX_VALUE;
                int y = wy + Short.MAX_VALUE;
                Tile tile = chunk.tiles[lx + ly * CHUNK_SIZE];

                if(!tile.floor().hasOres || tile.hasCliffs() || tile.block() != Blocks.air) continue;

                Floor baseFloor = tile.floor();
                for(int i = ores.size - 1; i >= 0; i--){
                    Item entry = ores.get(i);
                    int slot = activeOreNoise.get(i);
                    Simplex noise = oreNoise(i);
                    if(noise.octaveNoise2D(1, 0.7, 1f / (4 + slot * 2), x, y) / 4f +
                        Math.abs(0.5f - noise.octaveNoise2D(2, 0.7, 1f / (50 + slot * 2), x, y)) > 0.48f &&
                        Math.abs(0.5f - noise.octaveNoise2D(1, 1, 1f / (55 + slot * 4), x, y)) > 0.22f){
                        tile.setFloor((Floor) OreBlocks.get(baseFloor, entry));
                        break;
                    }
                }
            }
        }
    }

    // rebuild tile entities and occlusion after loading from disk need to be called after all chunks are loaded
    public void rebuildAfterLoad(){
        Array<WorldChunk> snapshot = loadedChunks.values().toArray();
        for(WorldChunk chunk : snapshot){
            if(chunk.tiles == null) continue;
            for(int i = 0; i < chunk.tiles.length; i++){
                chunk.tiles[i].rebuildEntity();
            }
        }
        for(WorldChunk chunk : snapshot){
            if(chunk.tiles == null) continue;
            for(int i = 0; i < chunk.tiles.length; i++){
                Tile tile = chunk.tiles[i];
                tile.updateOcclusion();
                if(tile.entity != null){
                    tile.entity.updateProximity();
                }
            }
        }
        for(WorldChunk chunk : snapshot){
            if(chunk.tiles == null) continue;
            for(int i = 0; i < chunk.tiles.length; i++){
                Tile tile = chunk.tiles[i];
                if(tile.block() instanceof CoreBlock){
                    state.teams.get(tile.getTeam()).cores.add(tile);
                }
            }
        }
    }

    /** Save all loaded chunks to disk. */
    public void saveAllChunks(){
        if(saveName == null) return;
        int count = 0;
        for(WorldChunk chunk : loadedChunks.values()){
            if(chunk.coldData != null){
                //already serialized; write the payload as-is
                saveManager.saveRawChunk(saveName, chunk.cx, chunk.cy, chunk.coldData);
                count++;
            }else if(chunk.generated && chunk.tiles != null){
                saveManager.saveChunk(saveName, chunk);
                count++;
            }
        }
        if(!headless && players[0] != null){
            saveManager.saveEntities(saveName);
        }
        OpenWorldSaveManager.OpenWorldMeta meta = saveManager.readMeta(saveName);
        if(meta == null){
            meta = new OpenWorldSaveManager.OpenWorldMeta();
            meta.name = saveName;
            meta.seed = worldSeed;
            meta.dateCreated = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date()); // fix
        }
        meta.chunksSaved = count;
        if(!headless && players[0] != null){
            meta.playerX = players[0].x;
            meta.playerY = players[0].y;
            meta.playerTeam = players[0].getTeam().ordinal();
        }
        saveManager.writeMeta(saveName, meta);
    }

    public Tile getTile(int worldX, int worldY){
        int cx = MathUtils.floor((float)worldX / CHUNK_SIZE);
        int cy = MathUtils.floor((float)worldY / CHUNK_SIZE);
        long key = packKey(cx, cy);
        WorldChunk chunk = loadedChunks.get(key);
        if(chunk == null){
            if(generatingChunk){
                return null;
            }
            chunk = getOrCreateChunk(cx, cy);
            if(chunk == null) return null;
        }
        if(chunk.tiles == null){
            return null;
        }
        int lx = worldX - cx * CHUNK_SIZE;
        int ly = worldY - cy * CHUNK_SIZE;
        if(lx < 0 || lx >= CHUNK_SIZE || ly < 0 || ly >= CHUNK_SIZE){
            return null;
        }
        return chunk.tile(lx, ly);
    }

    public Tile getTileSafe(int worldX, int worldY){
        Tile tile = getTile(worldX, worldY);
        if(tile != null) return tile;
        return Tile.createRaw(worldX, worldY, (Floor)Blocks.deepwater, Blocks.air, (byte)0);
    }

    public boolean isChunkLoaded(int cx, int cy){
        return loadedChunks.containsKey(packKey(cx, cy));
    }

    /** Returns the chunk if it is resident (frozen counts), without generating anything. Null for unloaded or cold-stored chunks. */
    public WorldChunk peekChunk(int cx, int cy){
        return loadedChunks.get(packKey(cx, cy));
    }

    public boolean isChunkActive(int cx, int cy){
        WorldChunk chunk = loadedChunks.get(packKey(cx, cy));
        return chunk != null && chunk.active;
    }

    public int getLoadedChunkCount(){
        return loadedChunks.size;
    }

    public Iterable<WorldChunk> getLoadedChunks(){
        return loadedChunks.values();
    }

    public void update(){
        if(players.length == 0 || (players[0] == null && playerGroup.size() == 0)) return;

        if(netMode){
            updateClient();
            return;
        }

        playerCenters.clear();
        for(int i = 0; i < playerGroup.size(); i++){
            Player player = playerGroup.all().get(i);
            if(player == null) continue;
            playerCenters.add(new int[]{MathUtils.floor(player.x / (CHUNK_SIZE * tilesize)), MathUtils.floor(player.y / (CHUNK_SIZE * tilesize))});
        }
        if(playerCenters.size == 0 && players.length > 0 && players[0] != null){
            playerCenters.add(new int[]{MathUtils.floor(players[0].x / (CHUNK_SIZE * tilesize)), MathUtils.floor(players[0].y / (CHUNK_SIZE * tilesize))});
        }
        if(playerCenters.size == 0) return;

        //load and wake chunks nearest-first across all players, a limited number per tick to avoid lag spikes
        int budget = MAX_CHUNK_LOADS_PER_TICK;
        outer:
        for(int r = 0; r <= LOAD_RADIUS; r++){
            for(int i = 0; i < playerCenters.size; i++){
                int pcx = playerCenters.items[i][0];
                int pcy = playerCenters.items[i][1];
                for(int dx = -r; dx <= r; dx++){
                    for(int dy = -r; dy <= r; dy++){
                        if(Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                        long key = packKey(pcx + dx, pcy + dy);
                        WorldChunk chunk = loadedChunks.get(key);
                        if(chunk == null){
                            getOrCreateChunk(pcx + dx, pcy + dy);
                            if(--budget <= 0) break outer;
                        }else if(chunk.frozen || chunk.coldData != null){
                            wakeChunk(chunk);
                            if(--budget <= 0) break outer;
                        }
                    }
                }
            }
        }

        markActiveChunks();

        //chunks containing units or players that wandered into frozen territory resume simulation immediately
        for(WorldChunk chunk : loadedChunks.values()){
            if(chunk.active && (chunk.frozen || chunk.coldData != null)){
                wakeChunk(chunk);
            }
        }

        int now = (int)Timers.time();
        if(now - lastUnloadCheck > UNLOAD_CHECK_INTERVAL){
            lastUnloadCheck = now;
            unloadDistantChunks();
        }
        if(now - lastColdCheck > COLD_CHECK_INTERVAL){
            lastColdCheck = now;
            coldSweep();
        }
    }

    /** Network-client chunk handling: chunks come from the server (push); they are evicted beyond the view
     * radius to bound memory and re-requested only when the local player approaches evicted territory. */
    private void updateClient(){
        int playerCX = MathUtils.floor(players[0].x / (CHUNK_SIZE * tilesize));
        int playerCY = MathUtils.floor(players[0].y / (CHUNK_SIZE * tilesize));
        for(int dx = -RENDER_RADIUS; dx <= RENDER_RADIUS; dx++){
            for(int dy = -RENDER_RADIUS; dy <= RENDER_RADIUS; dy++){
                if(!loadedChunks.containsKey(packKey(playerCX + dx, playerCY + dy))){
                    getOrCreateChunk(playerCX + dx, playerCY + dy);
                }
            }
        }
        markActiveChunks();

        int now = (int)Timers.time();
        if(now - lastUnloadCheck > UNLOAD_CHECK_INTERVAL){
            lastUnloadCheck = now;
            clientEvict(playerCX, playerCY);
        }
    }

    private void markActiveChunks(){
        for(WorldChunk chunk : loadedChunks.values()){
            chunk.active = false;
        }

        for(TileEntity entity : tileGroup.all()){
            if(entity.tile == null) continue;
            int cx = MathUtils.floor((float)entity.tile.x / CHUNK_SIZE);
            int cy = MathUtils.floor((float)entity.tile.y / CHUNK_SIZE);
            long key = packKey(cx, cy);
            WorldChunk chunk = loadedChunks.get(key);
            if(chunk != null) chunk.active = true;
        }

        for(Team team : Team.all){
            for(BaseUnit unit : unitGroups[team.ordinal()].all()){
                int cx = MathUtils.floor(unit.x / (CHUNK_SIZE * tilesize));
                int cy = MathUtils.floor(unit.y / (CHUNK_SIZE * tilesize));
                long key = packKey(cx, cy);
                WorldChunk chunk = loadedChunks.get(key);
                if(chunk != null) chunk.active = true;
            }
        }

        for(int i = 0; i < playerGroup.size(); i++){
            Player player = playerGroup.all().get(i);
            if(player == null) continue;
            int cx = MathUtils.floor(player.x / (CHUNK_SIZE * tilesize));
            int cy = MathUtils.floor(player.y / (CHUNK_SIZE * tilesize));
            long key = packKey(cx, cy);
            WorldChunk chunk = loadedChunks.get(key);
            if(chunk != null) chunk.active = true;
        }
    }

    /**
     * Freezes chunks far from the player: tiles stay resident for rendering/minimap/fog,
     * but their tile entities are detached from the logic group so they cost zero update time.
     * Waking is instant since no data is destroyed. Runs every {@link #UNLOAD_CHECK_INTERVAL} ticks.
     */
    private void unloadDistantChunks(){
        for(WorldChunk chunk : loadedChunks.values()){
            int dist = minDistToAnyPlayer(chunk.cx, chunk.cy);

            if(dist <= LOAD_RADIUS) continue;
            if(chunk.active) continue;
            if(coreChunks.contains(packKey(chunk.cx, chunk.cy))) continue;

            if(dist > RENDER_RADIUS + 2){
                freezeChunk(chunk);
            }
        }
    }

    /** Compresses frozen chunks far outside every player's render window into compact byte payloads, freeing their object graphs. Unmodified pristine chunks are dropped outright - their terrain regenerates deterministically from the seed. */
    private void coldSweep(){
        sweepScratch.clear();

        for(LongMap.Entry<WorldChunk> entry : loadedChunks.entries()){
            WorldChunk chunk = entry.value;
            if(!chunk.frozen || chunk.tiles == null) continue;

            int dist = minDistToAnyPlayer(chunk.cx, chunk.cy);
            if(dist <= COLD_RADIUS) continue;
            if(chunk.active) continue;
            if(coreChunks.contains(packKey(chunk.cx, chunk.cy))) continue;

            sweepScratch.add(entry.key);
        }

        for(int i = 0; i < sweepScratch.size; i++){
            WorldChunk chunk = loadedChunks.get(sweepScratch.items[i]);
            if(chunk == null || !chunk.frozen || chunk.tiles == null || chunk.coldData != null) continue;

            if(chunk.modified){
                byte[] payload = saveManager.serializeChunk(chunk);
                if(payload == null) continue; //serialization failed; keep resident rather than risk loss
                chunk.tiles = null;
                chunk.frozen = false;
                chunk.coldData = payload;
            }else{
                loadedChunks.remove(sweepScratch.items[i]);
            }
        }
    }

    /** Chebyshev distance from a chunk to the closest player center; keeps retention near any player, not just player 0. */
    private int minDistToAnyPlayer(int cx, int cy){
        int min = Integer.MAX_VALUE;
        for(int i = 0; i < playerCenters.size; i++){
            int dist = Math.max(Math.abs(cx - playerCenters.items[i][0]), Math.abs(cy - playerCenters.items[i][1]));
            if(dist < min) min = dist;
        }
        return min;
    }

    private boolean canAutoGenerate(int cx, int cy){
        if(generatingChunk) return true;
        ensurePlayerCenters();
        if(playerCenters.size == 0) return true;
        for(int i = 0; i < playerCenters.size; i++){
            if(Math.max(Math.abs(cx - playerCenters.items[i][0]), Math.abs(cy - playerCenters.items[i][1])) <= GENERATE_RADIUS){
                return true;
            }
        }
        return false;
    }

    /** Populates player centers from the current entity groups if {@link #update()} has not run yet this tick
     * Does nothing when they are already known. */
    private void ensurePlayerCenters(){
        if(playerCenters.size > 0) return;
        for(int i = 0; i < playerGroup.size(); i++){
            Player player = playerGroup.all().get(i);
            if(player != null){
                playerCenters.add(new int[]{MathUtils.floor(player.x / (CHUNK_SIZE * tilesize)), MathUtils.floor(player.y / (CHUNK_SIZE * tilesize))});
            }
        }
        if(playerCenters.size == 0 && players.length > 0 && players[0] != null){
            playerCenters.add(new int[]{MathUtils.floor(players[0].x / (CHUNK_SIZE * tilesize)), MathUtils.floor(players[0].y / (CHUNK_SIZE * tilesize))});
        }
    }

    /** Network-client eviction: chunks far enough from the player are dropped to bound memory (Minetest-style).
     * They are re-fetched from the server when the player returns. */
    private void clientEvict(int playerCX, int playerCY){
        sweepScratch.clear();
        for(LongMap.Entry<WorldChunk> entry : loadedChunks.entries()){
            WorldChunk chunk = entry.value;
            int dist = Math.max(Math.abs(chunk.cx - playerCX), Math.abs(chunk.cy - playerCY));
            if(dist <= CLIENT_EVICT_RADIUS) continue;
            if(chunk.active) continue;
            if(coreChunks.contains(entry.key)) continue;
            sweepScratch.add(entry.key);
        }

        for(int i = 0; i < sweepScratch.size; i++){
            long key = sweepScratch.items[i];
            WorldChunk chunk = loadedChunks.get(key);
            if(chunk == null || chunk.tiles == null) continue;
            for(int j = 0; j < chunk.tiles.length; j++){
                TileEntity entity = chunk.tiles[j].entity;
                if(entity != null && entity.getGroup() != null){
                    entity.remove();
                }
            }
            loadedChunks.remove(key);
            requestedChunks.remove(key);
        }
    }

    /** Detaches all resident tile entities from the logic group; the chunk stops updating entirely but stays renderable. */
    private void freezeChunk(WorldChunk chunk){
        if(chunk.frozen || chunk.tiles == null) return;

        for(int i = 0; i < chunk.tiles.length; i++){
            TileEntity entity = chunk.tiles[i].entity;
            if(entity != null && entity.getGroup() != null){
                entity.remove();
            }
        }
        chunk.frozen = true;
    }

    /** Re-attaches resident tile entities to the logic group. */
    private void thawChunk(WorldChunk chunk){
        if(!chunk.frozen) return;
        chunk.frozen = false;
        if(chunk.tiles == null) return;

        for(int i = 0; i < chunk.tiles.length; i++){
            TileEntity entity = chunk.tiles[i].entity;
            //entities put to sleep before freezing were detached by design; leave them asleep
            if(entity != null && !entity.isDead() && entity.getGroup() == null && !entity.isSleeping()){
                entity.add();
            }
        }
    }

    /** Brings a frozen or cold-stored chunk back to full simulation. */
    private void wakeChunk(WorldChunk chunk){
        if(chunk.coldData != null && !materializeColdChunk(chunk)){
            return;
        }
        thawChunk(chunk);
    }

    /** Rebuilds live tiles/entities from a cold-stored payload in place. Returns false if there was nothing to restore. */
    private boolean materializeColdChunk(WorldChunk chunk){
        if(chunk.coldData == null) return false;

        WorldChunk restored = saveManager.deserializeChunk(chunk.coldData);
        chunk.coldData = null;

        if(restored != null && restored.tiles != null){
            chunk.tiles = restored.tiles;
            chunk.restored = true;
            chunk.generated = true;
        }else{
            Log.err("Failed to restore cold-stored chunk at {0}, {1}; regenerating", chunk.cx, chunk.cy);
            chunk.tiles = new Tile[CHUNK_SIZE * CHUNK_SIZE];
            chunk.restored = false;
            generatingChunk = true;
            try{
                generateChunkTerrain(chunk);
            }finally{
                generatingChunk = false;
            }
            chunk.generated = true;
        }

        chunk.pathStamp++;
        updateChunkAndNeighborCliffs(chunk.cx, chunk.cy);
        world.indexer.indexChunk(chunk);
        world.pathfinder.onChunkLive(chunk.cx, chunk.cy);

        //re-link machines to their neighbors now that all tiles are back
        for(int i = 0; i < chunk.tiles.length; i++){
            TileEntity entity = chunk.tiles[i].entity;
            if(entity != null && !entity.isDead()){
                entity.updateProximity();
            }
        }
        return true;
    }

    private void generateChunkTerrain(WorldChunk chunk){
        int worldStartX = chunk.cx * CHUNK_SIZE;
        int worldStartY = chunk.cy * CHUNK_SIZE;

        for(int lx = 0; lx < CHUNK_SIZE; lx++){
            for(int ly = 0; ly < CHUNK_SIZE; ly++){
                int wx = worldStartX + lx;
                int wy = worldStartY + ly;

                chunk.tiles[lx + ly * CHUNK_SIZE] = generateTileAt(wx, wy);
            }
        }

        applySlopes(chunk, worldStartX, worldStartY);

        for(int lx = 0; lx < CHUNK_SIZE; lx++){
            for(int ly = 0; ly < CHUNK_SIZE; ly++){
                int wx = worldStartX + lx;
                int wy = worldStartY + ly;
                Tile tile = chunk.tiles[lx + ly * CHUNK_SIZE];

                if(tile.block() != Blocks.air) continue;

                Block floor = tile.floor();
                if(floor instanceof OreBlock){
                    floor = ((OreBlock) floor).base;
                }

                if(floor == Blocks.grass){
                    if(random.chance(0.0145f) && hasWaterNear(wx, wy, 3.5f)){
                        tile.setBlock(Blocks.tree);
                    }
                }else if(floor == Blocks.sand){
                    if(random.chance(0.0075f) && hasWaterNear(wx, wy, 7f)){
                        tile.setBlock(Blocks.deadTree);
                    }
                }
            }
        }
    }

    private boolean hasWaterNear(int wx, int wy, float radius){
        int r = (int) Math.ceil(radius);
        for(int dx = -r; dx <= r; dx++){
            for(int dy = -r; dy <= r; dy++){
                if(dx * dx + dy * dy > radius * radius) continue;
                Tile t = getTile(wx + dx, wy + dy);
                if(t != null){
                    Block f = t.floor();
                    if(f == Blocks.water || f == Blocks.deepwater){
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void applySlopes(WorldChunk chunk, int worldStartX, int worldStartY){
        int pad = CHUNK_SIZE + 2;
        byte[] heights = new byte[pad * pad];

        for(int x = 0; x < pad; x++){
            for(int y = 0; y < pad; y++){
                heights[x + y * pad] = sampleTerrain(worldStartX + x - 1, worldStartY + y - 1).elevation;
            }
        }

        for(int lx = 0; lx < CHUNK_SIZE; lx++){
            for(int ly = 0; ly < CHUNK_SIZE; ly++){
                Tile tile = chunk.tiles[lx + ly * CHUNK_SIZE];
                int elevation = tile.getElevation();

                if(elevation <= 0) continue;

                boolean lowerNeighbor = false;
                for(int d = 0; d < 4 && !lowerNeighbor; d++){
                    int hx = lx + 1 + Geometry.d4[d].x;
                    int hy = ly + 1 + Geometry.d4[d].y;
                    if(heights[hx + hy * pad] < elevation){
                        lowerNeighbor = true;
                    }
                }

                if(lowerNeighbor && sim2.octaveNoise2D(1, 1, 1.0 / 8,
                    worldStartX + lx + Short.MAX_VALUE, worldStartY + ly + Short.MAX_VALUE) > 0.8){
                    tile.setElevation(-1);
                }
            }
        }
    }

    private Tile generateTileAt(int wx, int wy){
        TerrainSample sample = sampleTerrain(wx, wy);

        Block wall = Blocks.air;

        if(decoration.containsKey(sample.floor) && random.chance(0.03)){
            wall = decoration.get(sample.floor);
        }

        if(wall == Blocks.air && (sample.floor == Blocks.snow || sample.floor == Blocks.ice) && random.chance(0.0045)){
            wall = Blocks.frozenTree;
        }

        return Tile.createRaw(wx, wy, (Floor)sample.floor, wall, sample.elevation);
    }

    private TerrainSample sampleTerrain(int wx, int wy){
        int x = wx + Short.MAX_VALUE;
        int y = wy + Short.MAX_VALUE;

        double ridge = rid.getValue(x, y, 1f / 400f);
        double iceridge = rid.getValue(x + 99999, y, 1f / 300f) + sim3.octaveNoise2D(2, 1f, 1f / 14f, x, y) / 11f;
        double elevation = sim.octaveNoise2D(7, 0.62, 1f / 800, x, y) * 6.1 - 1 - ridge;
        double temp = sim3.octaveNoise2D(12, 0.6, 1f / 1100f, x - 120, y);
        double lake = sim2.octaveNoise2D(1, 1, 1f / 110f, x, y);

        elevation -= Math.pow(lake + 0.15f, 5);

        Block floor;

        if(elevation < 0.7){
            floor = Blocks.deepwater;
        }else if(elevation < 0.79){
            floor = Blocks.water;
        }else if(elevation < 0.85){
            floor = Blocks.sand;
        }else if(elevation < 2.5 && temp > 0.5){
            floor = Blocks.sand;
        }else if(temp < 0.42){
            floor = Blocks.snow;
        }else if(temp < 0.5){
            floor = Blocks.stone;
        }else if(temp < 0.6){
            floor = Blocks.grass;
        }else if(temp + ridge / 2f < 0.8 || elevation < 1.3){
            floor = Blocks.blackstone;
            if(iceridge > 0.25){
                elevation++;
            }
        }else{
            floor = Blocks.lava;
        }

        if(elevation > 3.3 && iceridge > 0.25 && temp < 0.6f){
            elevation++;
            floor = Blocks.ice;
        }

        if(((Floor)floor).liquidDrop != null){
            elevation = 0;
        }

        return new TerrainSample(floor, (byte)Math.max(elevation, 0));
    }

    private static class TerrainSample{
        final Block floor;
        final byte elevation;

        TerrainSample(Block floor, byte elevation){
            this.floor = floor;
            this.elevation = elevation;
        }
    }

    private static final com.badlogic.gdx.utils.ObjectMap<io.anuke.mindustry.world.Block, io.anuke.mindustry.world.Block> decoration;

    static{
        decoration = new com.badlogic.gdx.utils.ObjectMap<>();
        decoration.put(Blocks.grass, Blocks.shrub);
        decoration.put(Blocks.stone, Blocks.rock);
        decoration.put(Blocks.ice, Blocks.icerock);
        decoration.put(Blocks.snow, Blocks.icerock);
        decoration.put(Blocks.blackstone, Blocks.blackrock);
    }

    public static class WorldChunk{
        public final int cx, cy;
        public Tile[] tiles;
        public boolean generated = false;
        public boolean active = false;
        /** Set when the player changes something in this chunk; modified chunks are never unloaded unsaved. */
        public boolean modified = false;
        /** True when this chunk's tiles came from disk or cold storage instead of fresh generation; restored chunks are never terrain-cleaned, since that would delete player builds on elevated ground. */
        public boolean restored = false;
        /** True when resident tile entities are detached from the logic group; the chunk costs zero update time and wakes instantly. */
        public boolean frozen = false;
        /** Non-null when this chunk is compressed into cold storage; tiles/entities are freed and restored transparently on access. */
        public byte[] coldData = null;
        /** Set when this chunk's tiles or entities changed and it needs to be re-sent to connected clients. Cleared after a successful push. */
        public boolean netDirty = false;
        /** Bumped whenever this chunk's tile data mutates or is replaced; used by AI caches (e.g. the chunk waypoint graph) for invalidation. */
        public int pathStamp = 0;
        public long lastAccessFrame = 0;

        public WorldChunk(int cx, int cy){
            this.cx = cx;
            this.cy = cy;
        }

        public Tile tile(int localX, int localY){
            return tiles[localX + localY * CHUNK_SIZE];
        }
    }
}
