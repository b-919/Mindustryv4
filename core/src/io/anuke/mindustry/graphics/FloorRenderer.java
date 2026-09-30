package io.anuke.mindustry.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.TimeUtils;
import io.anuke.mindustry.game.EventType.TileChangeEvent;
import io.anuke.mindustry.game.EventType.WorldLoadGraphicsEvent;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.graphics.CacheBatch;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.tilesize;
import static io.anuke.mindustry.Vars.world;

public class FloorRenderer{
    private final static int chunksize = 30;
    private static final int numLayers = CacheLayer.values().length;

    private Chunk[][] cache;
    private boolean[][] dirty;
    private CacheBatch cbatch;
    private boolean[] usedLayers = new boolean[numLayers];
    private int chunksx, chunksy;
    private boolean initialized = false;
    private LongMap<Chunk> openWorldCache = new LongMap<>();
    private LongArray incompleteChunks = new LongArray();
    private int lastSeenChunkLoads = -1;
    private long lastIncompleteCheck;
    private boolean recacheTilePending;

    public FloorRenderer(){
        Events.on(WorldLoadGraphicsEvent.class, event -> {
            reload();
        });
        Events.on(TileChangeEvent.class, event -> {
            recacheTile(event.tile.x, event.tile.y);
        });
    }

    public void recacheTile(int x, int y){
        if(dirty == null) return;
        if(world.isOpenWorld()){
            int cx = (int)Math.floor((float)x / chunksize);
            int cy = (int)Math.floor((float)y / chunksize);
            long key = ChunkManager.packKey(cx, cy);
            Chunk chunk = openWorldCache.get(key);
            if(chunk != null){
                chunk.allDirty = true;
            }
            return;
        }
        int cx = x / chunksize;
        int cy = y / chunksize;
        if(cx >= 0 && cy >= 0 && cx < dirty.length && cy < dirty[0].length){
            dirty[cx][cy] = true;
        }
    }

    public void drawFloor(){
        if(world.isOpenWorld()){
            drawFloorOpenWorld();
            return;
        }
        if(cache == null) return;

        OrthographicCamera camera = Core.camera;

        int minx = Math.max((int)((camera.position.x - camera.viewportWidth / 2f) / (chunksize * tilesize)), 0);
        int miny = Math.max((int)((camera.position.y - camera.viewportHeight / 2f) / (chunksize * tilesize)), 0);
        int maxx = Math.min(Mathf.ceil((camera.position.x + camera.viewportWidth / 2f) / (chunksize * tilesize)), chunksx);
        int maxy = Math.min(Mathf.ceil((camera.position.y + camera.viewportHeight / 2f) / (chunksize * tilesize)), chunksy);

        CacheLayer[] layers = CacheLayer.values();

        Graphics.end();

        boolean anyDirty = false;
        for(int x = minx; x < maxx; x++){
            for(int y = miny; y < maxy; y++){
                if(dirty[x][y]){
                    anyDirty = true;
                    break;
                }
            }
            if(anyDirty) break;
        }

        if(anyDirty){
            cbatch.clear();
            for(int x = 0; x < chunksx; x++){
                for(int y = 0; y < chunksy; y++){
                    dirty[x][y] = true;
                }
            }
            for(int x = minx; x < maxx; x++){
                for(int y = miny; y < maxy; y++){
                    dirty[x][y] = false;
                    cacheChunk(x, y);
                }
            }
        }

        beginDraw();

        for(int i = 0; i < numLayers; i++){
            CacheLayer layer = layers[i];

            layer.begin();

            for(int x = minx; x < maxx; x++){
                for(int y = miny; y < maxy; y++){
                    Chunk chunk = cache[x][y];
                    if(chunk == null || chunk.caches[i] == -1) continue;
                    cbatch.drawCache(chunk.caches[i]);
                }
            }

            layer.end();
        }

        endDraw();
        Graphics.begin();
    }

    private void drawFloorOpenWorld(){
        if(cbatch == null) return;

        OrthographicCamera camera = Core.camera;

        int minx = (int)Math.floor((camera.position.x - camera.viewportWidth / 2f) / (chunksize * tilesize));
        int miny = (int)Math.floor((camera.position.y - camera.viewportHeight / 2f) / (chunksize * tilesize));
        int maxx = Mathf.ceil((camera.position.x + camera.viewportWidth / 2f) / (chunksize * tilesize));
        int maxy = Mathf.ceil((camera.position.y + camera.viewportHeight / 2f) / (chunksize * tilesize));

        CacheLayer[] layers = CacheLayer.values();

        Graphics.end();

        //re-cache chunks that were built against unloaded terrain once it becomes available
        refreshIncompleteChunks();

        //incrementally build cache chunks around the view so panning doesn't trigger full rebuilds
        precacheChunks(minx, miny, maxx, maxy);

        boolean anyDirty = recacheTilePending;
        recacheTilePending = false;
        for(int x = minx; x <= maxx && !anyDirty; x++){
            for(int y = miny; y <= maxy && !anyDirty; y++){
                long key = ChunkManager.packKey(x, y);
                Chunk chunk = openWorldCache.get(key);
                if(chunk == null || chunk.allDirty) anyDirty = true;
            }
        }

        if(anyDirty){
            cbatch.clear();

            //drop cached chunks far outside the view to bound memory use
            int margin = 6;
            LongArray toEvict = new LongArray();
            for(LongMap.Entry<Chunk> entry : openWorldCache.entries()){
                int cx = ChunkManager.keyCx(entry.key);
                int cy = ChunkManager.keyCy(entry.key);
                if(cx < minx - margin || cx > maxx + margin || cy < miny - margin || cy > maxy + margin){
                    toEvict.add(entry.key);
                }
            }
            for(int i = 0; i < toEvict.size; i++){
                openWorldCache.remove(toEvict.items[i]);
            }

            for(Chunk chunk : openWorldCache.values()){
                java.util.Arrays.fill(chunk.caches, -1);
                chunk.allDirty = true;
            }

            for(int x = minx; x <= maxx; x++){
                for(int y = miny; y <= maxy; y++){
                    long key = ChunkManager.packKey(x, y);
                    Chunk chunk = openWorldCache.get(key);
                    if(chunk == null){
                        chunk = new Chunk();
                        openWorldCache.put(key, chunk);
                    }
                    chunk.allDirty = false;
                    if(cacheChunkOpenWorld(x, y, chunk)){
                        incompleteChunks.add(key);
                    }
                }
            }
        }

        beginDraw();

        for(int i = 0; i < numLayers; i++){
            CacheLayer layer = layers[i];

            layer.begin();

            for(int x = minx; x <= maxx; x++){
                for(int y = miny; y <= maxy; y++){
                    long key = ChunkManager.packKey(x, y);
                    Chunk chunk = openWorldCache.get(key);
                    if(chunk == null || chunk.caches[i] == -1) continue;
                    cbatch.drawCache(chunk.caches[i]);
                }
            }

            layer.end();
        }

        endDraw();
        Graphics.begin();
    }

    /** Makes sure a cache entry exists for this floor chunk; returns true if it was built now. */
    private boolean ensureCached(int x, int y){
        long key = ChunkManager.packKey(x, y);
        if(openWorldCache.containsKey(key)) return false;
        Chunk chunk = new Chunk();
        openWorldCache.put(key, chunk);
        chunk.allDirty = false;
        if(cacheChunkOpenWorld(x, y, chunk)){
            incompleteChunks.add(key);
        }
        return true;
    }

    /** Builds up to a few cache chunks per frame in a ring around the view, so moving never hits uncached chunks. */
    private void precacheChunks(int minx, int miny, int maxx, int maxy){
        final int pad = 2;
        int budget = 1;

        for(int r = 1; r <= pad && budget > 0; r++){
            int rx0 = minx - r, rx1 = maxx + r, ry0 = miny - r, ry1 = maxy + r;

            for(int x = rx0; x <= rx1 && budget > 0; x++){
                if(ensureCached(x, ry0)) budget--;
                if(budget > 0 && ensureCached(x, ry1)) budget--;
            }
            for(int y = ry0 + 1; y <= ry1 - 1 && budget > 0; y++){
                if(ensureCached(rx0, y)) budget--;
                if(budget > 0 && ensureCached(rx1, y)) budget--;
            }
        }
    }

    /** Re-caches chunks that were built while their terrain was not loaded yet. Throttled to avoid rebuild storms. */
    private void refreshIncompleteChunks(){
        if(world.chunks() == null) return;
        if(world.chunks().chunkLoadCounter == lastSeenChunkLoads) return;
        if(TimeUtils.millis() - lastIncompleteCheck < 250) return;

        lastSeenChunkLoads = world.chunks().chunkLoadCounter;
        lastIncompleteCheck = TimeUtils.millis();

        boolean anyComplete = false;
        for(int i = incompleteChunks.size - 1; i >= 0; i--){
            long key = incompleteChunks.items[i];
            Chunk chunk = openWorldCache.get(key);
            if(chunk == null){
                incompleteChunks.removeIndex(i);
                continue;
            }
            if(isRegionLoaded(ChunkManager.keyCx(key), ChunkManager.keyCy(key))){
                chunk.allDirty = true;
                anyComplete = true;
                incompleteChunks.removeIndex(i);
            }
        }

        //a full rebuild happens via the normal dirty path when drawing
        if(anyComplete){
            recacheTilePending = true;
        }
    }

    /** Samples corners + center of a floor cache block to detect unloaded terrain (cache blocks straddle world chunks). */
    private boolean isRegionLoaded(int cx, int cy){
        int startX = cx * chunksize, startY = cy * chunksize;
        int endX = startX + chunksize - 1, endY = startY + chunksize - 1;
        return world.peekTile(startX, startY) != null && world.peekTile(endX, startY) != null
            && world.peekTile(startX, endY) != null && world.peekTile(endX, endY) != null
            && world.peekTile(startX + chunksize / 2, startY + chunksize / 2) != null;
    }

    private boolean cacheChunkOpenWorld(int cx, int cy, Chunk chunk){
        java.util.Arrays.fill(usedLayers, false);

        int startX = cx * chunksize;
        int startY = cy * chunksize;
        int endX = startX + chunksize;
        int endY = startY + chunksize;

        boolean incomplete = false;

        for(int tilex = startX; tilex < endX; tilex++){
            for(int tiley = startY; tiley < endY; tiley++){
                //peek: never generate chunks from the render thread
                Tile tile = world.peekTile(tilex, tiley);
                if(tile != null){
                    usedLayers[tile.floor().cacheLayer.ordinal()] = true;
                }else{
                    incomplete = true;
                }
            }
        }

        CacheLayer[] layers = CacheLayer.values();
        for(int i = 0; i < numLayers; i++){
            if(usedLayers[i]){
                cacheChunkLayerOpenWorld(cx, cy, chunk, layers[i]);
            }
        }

        return incomplete;
    }

    private void cacheChunkLayerOpenWorld(int cx, int cy, Chunk chunk, CacheLayer layer){
        Graphics.useBatch(cbatch);
        cbatch.begin();

        int startX = cx * chunksize;
        int startY = cy * chunksize;
        int endX = startX + chunksize;
        int endY = startY + chunksize;

        for(int tilex = startX; tilex < endX; tilex++){
            for(int tiley = startY; tiley < endY; tiley++){
                //peek: never generate chunks from the render thread
                Tile tile = world.peekTile(tilex, tiley);

                if(tile == null) continue;

                Floor floor = tile.floor();

                if(floor.cacheLayer == layer){
                    floor.draw(tile);
                }else if(floor.cacheLayer.ordinal() < layer.ordinal()){
                    floor.drawNonLayer(tile);
                }
            }
        }

        cbatch.end();
        Graphics.popBatch();
        chunk.caches[layer.ordinal()] = cbatch.getLastCache();
    }

    public void invalidateOpenWorldCache(){
        for(Chunk chunk : openWorldCache.values()){
            chunk.allDirty = true;
        }
    }

    public void beginDraw(){
        if(cbatch == null) return;

        cbatch.setProjectionMatrix(Core.camera.combined);
        cbatch.beginDraw();

        Gdx.gl.glEnable(GL20.GL_BLEND);
    }

    public void endDraw(){
        if(cbatch == null) return;

        cbatch.endDraw();
    }

    // unused code, relic from the past rn not deleted bc cna be usefull in posterior
    /*
    public void drawLayer(CacheLayer layer){
        if(cache == null) return;

        OrthographicCamera camera = Core.camera;

        int minx = Math.max((int)((camera.position.x - camera.viewportWidth / 2f) / (chunksize * tilesize)), 0);
        int miny = Math.max((int)((camera.position.y - camera.viewportHeight / 2f) / (chunksize * tilesize)), 0);
        int maxx = Math.min(Mathf.ceil((camera.position.x + camera.viewportWidth / 2f) / (chunksize * tilesize)), chunksx);
        int maxy = Math.min(Mathf.ceil((camera.position.y + camera.viewportHeight / 2f) / (chunksize * tilesize)), chunksy);

        int layerOrd = layer.ordinal();

        for(int x = minx; x < maxx; x++){
            for(int y = miny; y < maxy; y++){
                if(dirty[x][y]){
                    dirty[x][y] = false;
                    cacheChunk(x, y);
                }
            }
        }

        layer.begin();

        for(int x = minx; x < maxx; x++){
            for(int y = miny; y < maxy; y++){
                Chunk chunk = cache[x][y];
                if(chunk == null || chunk.caches[layerOrd] == -1) continue;
                cbatch.drawCache(chunk.caches[layerOrd]);
            }
        }

        layer.end();
    }
    */

    private void cacheChunk(int cx, int cy){
        Chunk chunk = cache[cx][cy];
        if(chunk == null){
            chunk = cache[cx][cy] = new Chunk();
        }
        java.util.Arrays.fill(chunk.caches, -1);

        java.util.Arrays.fill(usedLayers, false);

        int startX = cx * chunksize;
        int startY = cy * chunksize;
        int endX = Math.min(startX + chunksize, world.width());
        int endY = Math.min(startY + chunksize, world.height());

        for(int tilex = startX; tilex < endX; tilex++){
            for(int tiley = startY; tiley < endY; tiley++){
                Tile tile = world.rawTile(tilex, tiley);
                if(tile != null){
                    usedLayers[tile.floor().cacheLayer.ordinal()] = true;
                }
            }
        }

        CacheLayer[] layers = CacheLayer.values();
        for(int i = 0; i < numLayers; i++){
            if(usedLayers[i]){
                cacheChunkLayer(cx, cy, chunk, layers[i]);
            }
        }
    }

    private void cacheChunkLayer(int cx, int cy, Chunk chunk, CacheLayer layer){
        Graphics.useBatch(cbatch);
        cbatch.begin();

        int startX = cx * chunksize;
        int startY = cy * chunksize;
        int endX = Math.min(startX + chunksize, world.width());
        int endY = Math.min(startY + chunksize, world.height());

        for(int tilex = startX; tilex < endX; tilex++){
            for(int tiley = startY; tiley < endY; tiley++){
                Tile tile = world.rawTile(tilex, tiley);

                if(tile == null) continue;

                Floor floor = tile.floor();

                if(floor.cacheLayer == layer){
                    floor.draw(tile);
                }else if(floor.cacheLayer.ordinal() < layer.ordinal()){
                    floor.drawNonLayer(tile);
                }
            }
        }

        cbatch.end();
        Graphics.popBatch();
        chunk.caches[layer.ordinal()] = cbatch.getLastCache();
    }

    public void reload(){
        if(cbatch != null) cbatch.dispose();

        if(world.isOpenWorld()){
            int worldSize = ChunkManager.CHUNK_SIZE * (ChunkManager.RENDER_RADIUS * 2 + 1);
            chunksx = worldSize / chunksize;
            chunksy = worldSize / chunksize;
            cache = null;
            dirty = null;
            cbatch = new CacheBatch(worldSize * worldSize * numLayers);
            openWorldCache.clear();
            incompleteChunks.clear();
            lastSeenChunkLoads = -1;
        }else{
            chunksx = Mathf.ceil((float)(world.width()) / chunksize);
            chunksy = Mathf.ceil((float)(world.height()) / chunksize);
            cache = new Chunk[chunksx][chunksy];
            dirty = new boolean[chunksx][chunksy];
            cbatch = new CacheBatch(world.width() * world.height() * numLayers);
            openWorldCache.clear();

            for(int x = 0; x < chunksx; x++){
                for(int y = 0; y < chunksy; y++){
                    dirty[x][y] = true;
                }
            }
        }

        initialized = true;

        Log.info("Floor cache allocated: {0}x{1} chunks", chunksx, chunksy);
    }

    private class Chunk{
        int[] caches = new int[numLayers];
        boolean allDirty = true;
    }
}
