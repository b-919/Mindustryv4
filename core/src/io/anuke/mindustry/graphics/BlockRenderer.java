package io.anuke.mindustry.graphics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.game.EventType.TileChangeEvent;
import io.anuke.mindustry.game.EventType.WorldLoadGraphicsEvent;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Surface;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.*;
import static io.anuke.ucore.core.Core.camera;

public class BlockRenderer{
    private final static int initialRequests = 32 * 32;
    private final static int expandr = 6;
    private static final int teamCount = Team.all.length;
    private static final int layerCount = Layer.values().length;

    private FloorRenderer floorRenderer;

    private Array<BlockRequest> requests = new Array<>(true, initialRequests, BlockRequest.class);
    private boolean[] teamChecks = new boolean[teamCount];
    private int lastCamX = -99, lastCamY = -99, lastRangeX, lastRangeY;
    private Layer lastLayer;
    private int requestidx = 0;
    private int iterateidx = 0;
    /** Stable counting-sort scratch space, one bucket per layer. */
    private final int[] layerCounts = new int[layerCount];
    private final int[] layerStarts = new int[layerCount];
    private final int[] layerPositions = new int[layerCount];
    private BlockRequest[] sortedRequests = new BlockRequest[initialRequests];
    private Surface shadows = Graphics.createSurface().setSize(2, 2);
    /** Currently allocated shadow surface size; may exceed the required size to avoid reallocating per zoom step. */
    private int shadowAllocW = 2, shadowAllocH = 2;
    /** World position the shadow FBO content was last rendered with; must match the projection origin exactly. */
    private float shadowOriginX, shadowOriginY;
    private Array<Tile> visibleTiles = new Array<>();
    private boolean blocksDirty = false;

    public BlockRenderer(){
        floorRenderer = new FloorRenderer();

        for(int i = 0; i < requests.size; i++){
            requests.set(i, new BlockRequest());
        }

        Events.on(WorldLoadGraphicsEvent.class, event -> {
            lastCamY = lastCamX = -99;
        });

        Events.on(TileChangeEvent.class, event -> {
            threads.runGraphics(() -> {
                int avgx = Mathf.scl(camera.position.x, tilesize);
                int avgy = Mathf.scl(camera.position.y, tilesize);
                int rangex = (int) (camera.viewportWidth / tilesize / 2) + 2;
                int rangey = (int) (camera.viewportHeight / tilesize / 2) + 2;

                if(Math.abs(avgx - event.tile.x) <= rangex && Math.abs(avgy - event.tile.y) <= rangey){
                    blocksDirty = true;
                }
            });
        });
    }

    public void drawShadows(){
        Draw.color(0, 0, 0, 0.15f);
        //draw the FBO anchored at its exact render origin; snapping independently of the
        //projection center desyncs content from display and clips shadows inside the camera
        Draw.rect(shadows.texture(), shadowOriginX, shadowOriginY, shadows.width(), -shadows.height());
        Draw.color();
    }

    public boolean isTeamShown(Team team){
        return teamChecks[team.ordinal()];
    }

    public Array<Tile> getVisibleTiles(){
        return visibleTiles;
    }

    /**Process all blocks to draw, simultaneously updating the block shadow framebuffer when camera moves.*/
    public void processBlocks(){
        iterateidx = 0;
        lastLayer = null;

        int avgx = Mathf.scl(camera.position.x, tilesize);
        int avgy = Mathf.scl(camera.position.y, tilesize);

        int rangex = (int) (camera.viewportWidth / tilesize / 2) + 2;
        int rangey = (int) (camera.viewportHeight / tilesize / 2) + 2;

        boolean cameraMoved = avgx != lastCamX || avgy != lastCamY || lastRangeX != rangex || lastRangeY != rangey;

        if(!cameraMoved && !blocksDirty) return;

        blocksDirty = false;
        visibleTiles.clear();

        java.util.Arrays.fill(teamChecks, false);
        requestidx = 0;

        int minx, miny, maxx, maxy;
        if(world.isOpenWorld()){
            minx = avgx - rangex - expandr;
            miny = avgy - rangey - expandr;
            maxx = avgx + rangex + expandr;
            maxy = avgy + rangey + expandr;
        }else{
            minx = Math.max(avgx - rangex - expandr, 0);
            miny = Math.max(avgy - rangey - expandr, 0);
            maxx = Math.min(world.width() - 1, avgx + rangex + expandr);
            maxy = Math.min(world.height() - 1, avgy + rangey + expandr);
        }

        float halfW = camera.viewportWidth / 2f;
        float halfH = camera.viewportHeight / 2f;
        int shadowPad = 3;
        int shMinX = MathUtils.floor((camera.position.x - halfW) / tilesize) - shadowPad;
        int shMaxX = MathUtils.floor((camera.position.x + halfW) / tilesize) + shadowPad;
        int shMinY = MathUtils.floor((camera.position.y - halfH) / tilesize) - shadowPad;
        int shMaxY = MathUtils.floor((camera.position.y + halfH) / tilesize) + shadowPad;

        float boxMinX = shMinX * tilesize, boxMaxX = (shMaxX + 1) * tilesize;
        float boxMinY = shMinY * tilesize, boxMaxY = (shMaxY + 1) * tilesize;
        int rawW = (int)(boxMaxX - boxMinX), rawH = (int)(boxMaxY - boxMinY);
        int shadowW = (rawW + 127) / 128 * 128;
        int shadowH = (rawH + 127) / 128 * 128;

        Graphics.end();
        //grow the shadow surface with slack and only shrink when it is far too large: recreating a
        //framebuffer on every zoom step is a driver-level hitch
        if(shadows.width() < shadowW || shadows.height() < shadowH || shadowW * 3 / 2 < shadowAllocW || shadowH * 3 / 2 < shadowAllocH){
            shadowAllocW = Math.max(shadowW, 128);
            shadowAllocH = Math.max(shadowH, 128);
            shadows.setSize(shadowAllocW, shadowAllocH);
        }
        shadowOriginX = (boxMinX + boxMaxX) / 2f;
        shadowOriginY = (boxMinY + boxMaxY) / 2f;
        Core.batch.getProjectionMatrix().setToOrtho2D(
            shadowOriginX - shadowAllocW / 2f,
            shadowOriginY - shadowAllocH / 2f,
            shadowAllocW, shadowAllocH);
        Graphics.surface(shadows);

        for(int x = minx; x <= maxx; x++){
            for(int y = miny; y <= maxy; y++){
                boolean expanded = (Math.abs(x - avgx) > rangex || Math.abs(y - avgy) > rangey);
                boolean inShadowZone = x >= shMinX && x <= shMaxX && y >= shMinY && y <= shMaxY;
                Tile tile = world.peekTile(x, y);

                if(tile != null){
                    Block block = tile.block();
                    Team team = tile.getTeam();

                    if(inShadowZone && block != Blocks.air && world.isAccessible(x, y)){
                        tile.block().drawShadow(tile);
                        visibleTiles.add(tile);
                    }

                    if(block != Blocks.air){
                        if(!expanded){
                            addRequest(tile, Layer.block);
                            teamChecks[team.ordinal()] = true;
                        }

                        if(block.expanded || !expanded){
                            if(block.layer != null && block.isLayer(tile)){
                                addRequest(tile, block.layer);
                            }

                            if(block.layer2 != null && block.isLayer2(tile)){
                                addRequest(tile, block.layer2);
                            }
                        }
                    }
                }
            }
        }

        Graphics.surface();
        Graphics.end();
        Core.batch.setProjectionMatrix(camera.combined);
        Graphics.begin();

        sortRequests();

        lastCamX = avgx;
        lastCamY = avgy;
        lastRangeX = rangex;
        lastRangeY = rangey;
    }

    /** Stable counting sort by layer ordinal: there are only a handful of layers, so this is O(n)
     *  and avoids object comparisons (and TimSort overhead) on every camera tile step. */
    private void sortRequests(){
        java.util.Arrays.fill(layerCounts, 0);

        BlockRequest[] items = requests.items;

        for(int i = 0; i < requestidx; i++){
            layerCounts[items[i].layer.ordinal()]++;
        }

        int sum = 0;
        for(int i = 0; i < layerCount; i++){
            layerStarts[i] = sum;
            layerPositions[i] = sum;
            sum += layerCounts[i];
        }

        if(sortedRequests.length < requestidx){
            sortedRequests = new BlockRequest[Math.max(requestidx, requests.size)];
        }

        for(int i = 0; i < requestidx; i++){
            BlockRequest req = items[i];
            sortedRequests[layerPositions[req.layer.ordinal()]++] = req;
        }

        System.arraycopy(sortedRequests, 0, items, 0, requestidx);
    }

    /** @return the index just past the last request that belongs to this layer or a lower one. */
    private int layerEnd(int ordinal){
        int end = 0;
        for(int i = 0; i <= ordinal; i++){
            end = layerStarts[i] + layerCounts[i];
        }
        return end;
    }

    /** Whether any visible floor chunk contains content for this cache layer. Unknown chunks
     *  report true so nothing is skipped before the floor cache has been built. */
    public boolean isLayerVisible(CacheLayer layer){
        return floorRenderer.isLayerVisible(layer);
    }

    public int getRequests(){
        return requestidx;
    }

    public void drawBlocks(Layer stopAt){
        int end = layerEnd(stopAt.ordinal());
        BlockRequest[] items = requests.items;

        for(; iterateidx < end; iterateidx++){

            BlockRequest req = items[iterateidx];

            if(req.layer != lastLayer){
                if(lastLayer != null) layerEnds(lastLayer);
                layerBegins(req.layer);
            }

            Block block = req.tile.block();

            if(req.layer == Layer.block){
                block.draw(req.tile);
            }else if(req.layer == block.layer){
                block.drawLayer(req.tile);
            }else if(req.layer == block.layer2){
                block.drawLayer2(req.tile);
            }

            lastLayer = req.layer;
        }
    }

    public void drawTeamBlocks(Layer layer, Team team){
        int index = this.iterateidx;
        int end = layerEnd(layer.ordinal());
        BlockRequest[] items = requests.items;

        for(; index < end; index++){

            BlockRequest req = items[index];
            if(req.tile.getTeam() != team) continue;

            Block block = req.tile.block();

            if(req.layer == Layer.block){
                block.draw(req.tile);
            }else if(req.layer == block.layer){
                block.drawLayer(req.tile);
            }else if(req.layer == block.layer2){
                block.drawLayer2(req.tile);
            }

        }
    }

    public void skipLayer(Layer stopAt){
        int end = layerEnd(stopAt.ordinal());

        if(iterateidx < end){
            iterateidx = end;
        }
    }

    public void beginFloor(){
        floorRenderer.beginDraw();
    }

    public void endFloor(){
        floorRenderer.endDraw();
    }

    public void drawFloor(){
        floorRenderer.drawFloor();
    }

    private void layerBegins(Layer layer){
    }

    private void layerEnds(Layer layer){
    }

    private void addRequest(Tile tile, Layer layer){
        if(requestidx >= requests.size){
            requests.add(new BlockRequest());
        }
        BlockRequest r = requests.get(requestidx);
        r.tile = tile;
        r.layer = layer;
        requestidx++;
    }

    private class BlockRequest implements Comparable<BlockRequest>{
        Tile tile;
        Layer layer;

        @Override
        public int compareTo(BlockRequest other){
            return layer.compareTo(other.layer);
        }

        @Override
        public String toString(){
            return tile.block().name + ":" + layer.toString();
        }
    }
}
