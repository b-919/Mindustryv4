package io.anuke.mindustry.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.TimeUtils;
import io.anuke.mindustry.game.EventType.TileChangeEvent;
import io.anuke.mindustry.game.EventType.WorldLoadEvent;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.mindustry.maps.generation.ChunkManager.WorldChunk;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.*;


public class Pathfinder{
    private long maxUpdate = TimeUtils.millisToNanos(2);
    private PathData[] paths;
    private ChunkWaypointGraph graph;
    public volatile long editStamp = 0;

    //packed cell layout
    private static final int B_SOLID = 1;
    private static final int B_BREAKABLE = 1 << 1;
    private static final int TEAM_SHIFT = 2;
    private static final int COST_SHIFT = 7;
    private static final int CELL_BLOCKED = B_SOLID;

    public Pathfinder(){
        Events.on(WorldLoadEvent.class, event -> clear());
        Events.on(TileChangeEvent.class, event -> {
            if(Net.client() || paths == null) return;
            editStamp++;

            Tile tile = event.tile;
            for(int i = 0; i < paths.length; i++){
                PathData path = ensurePath(i);
                if(path == null) continue;

                int lx = tile.x - path.offsetX;
                int ly = tile.y - path.offsetY;
                if(lx < 0 || ly < 0 || lx >= path.size || ly >= path.sizeY) continue;

                path.tileData[lx + ly * path.size] = packCell(tile);
                path.dirty = true;
            }
        });
    }

    /**
     * Returns ready-to-use path data for an active team. Teams that became active after
     * world load (e.g. waveTeam in open world) get their arrays built lazily on first need;
     * inactive teams return null.
     */
    private PathData ensurePath(int i){
        Team team = Team.all[i];
        if(!state.teams.isActive(team)) return null;

        PathData path = paths[i];
        if(path == null || path.tileData == null){
            path = createFor(team);
        }
        return path;
    }

    public void activateTeamPath(Team team){
        createFor(team);
    }

    /**
     * Refreshes packed cells for a chunk that just became resident (generated, loaded from
     * disk, or cold-restored). Without this, fields permanently treat streamed terrain as
     * blocked since bulk tile creation fires no events.
     */
    public void onChunkLive(int cx, int cy){
        if(Net.client() || paths == null) return;
        invalidateMemo();

        int wx0 = cx * ChunkManager.CHUNK_SIZE, wy0 = cy * ChunkManager.CHUNK_SIZE;
        int cs = ChunkManager.CHUNK_SIZE;

        for(int i = 0; i < paths.length; i++){
            PathData path = ensurePath(i);
            if(path == null) continue;

            int x0 = Math.max(wx0, path.offsetX);
            int x1 = Math.min(wx0 + cs, path.offsetX + path.size);
            int y0 = Math.max(wy0, path.offsetY);
            int y1 = Math.min(wy0 + cs, path.offsetY + path.sizeY);
            if(x0 >= x1 || y0 >= y1) continue;

            for(int x = x0; x < x1; x++){
                for(int y = y0; y < y1; y++){
                    path.tileData[(x - path.offsetX) + (y - path.offsetY) * path.size] = packCell(fastTile(x, y));
                }
            }
            path.dirty = true;
        }
    }

    /**
     * Coarse chunk-level waypoints (packed tile coords) for long-distance open-world travel.
     * Returns null when unavailable or unreachable; callers should fall back to direct steering.
     */
    public LongArray findChunkPath(float fromWorldX, float fromWorldY, float toWorldX, float toWorldY){
        if(!world.isOpenWorld()) return null;
        if(graph == null){
            graph = new ChunkWaypointGraph();
        }
        return graph.findPath(fromWorldX / tilesize, fromWorldY / tilesize, toWorldX / tilesize, toWorldY / tilesize);
    }

    public void recenter(int playerTX, int playerTY){
        if(paths == null) return;

        for(int i = 0; i < paths.length; i++){
            PathData path = ensurePath(i);
            if(path == null) continue;

            int halfGrid = path.size / 2;
            int gridCenterX = path.offsetX + halfGrid;
            int gridCenterY = path.offsetY + halfGrid;

            int dx = playerTX - gridCenterX;
            int dy = playerTY - gridCenterY;

            if(Math.abs(dx) > halfGrid / 2 || Math.abs(dy) > halfGrid / 2){
                rebuildWindow(path, playerTX - halfGrid, playerTY - halfGrid);
            }
        }
    }

    public void update(){
        if(Net.client() || paths == null) return;

        for(int i = 0; i < paths.length; i++){
            Team team = Team.all[i];

            PathData path = ensurePath(i);
            if(path == null) continue;

            if(path.dirty && path.heapSize == 0){
                java.util.Arrays.fill(path.weights, Float.MAX_VALUE);
                seedTargets(team, path);
                path.dirty = false;
            }

            updateFrontier(team, maxUpdate);
        }
    }

    public Tile getTargetTile(Team team, Tile tile){
        PathData path = paths[team.ordinal()];
        if(path == null || tile == null) return tile;

        if(!path.hasComplete) return tile;

        float[] values = path.completeWeights;

        int ax = tile.x - path.offsetX;
        int ay = tile.y - path.offsetY;
        if(ax < 0 || ay < 0 || ax >= path.size || ay >= path.sizeY) return tile;

        int pos = ax + ay * path.size;
        float value = values[pos];

        Tile target = null;
        float tl = 0f;

        for(int d = 0; d < 8; d++){
            int dx, dy;
            switch(d){
                case 0: dx = 0; dy = -1; break;
                case 1: dx = 0; dy = 1; break;
                case 2: dx = -1; dy = 0; break;
                case 3: dx = 1; dy = 0; break;
                case 4: dx = -1; dy = -1; break;
                case 5: dx = 1; dy = -1; break;
                case 6: dx = -1; dy = 1; break;
                default: dx = 1; dy = 1; break;
            }

            int nx = tile.x + dx, ny = tile.y + dy;

            int bx = nx - path.offsetX;
            int by = ny - path.offsetY;
            if(bx < 0 || by < 0 || bx >= path.size || by >= path.sizeY) continue;

            int npos = bx + by * path.size;
            float v = values[npos];

            if(v >= value || Float.MAX_VALUE - v < 1f) continue;
            if(target != null && v >= tl) continue;

            if(dx != 0 && dy != 0){
                int c1 = cellAt(path, tile.x + dx, tile.y);
                int c2 = cellAt(path, tile.x, tile.y + dy);
                if(isSolid(c1) || isSolid(c2)) continue;
            }

            Tile other = peekWorldTile(nx, ny);
            if(other == null || other.solid()) continue;

            target = other;
            tl = v;
        }

        if(target == null || tl == Float.MAX_VALUE) return tile;

        return target;
    }

    public float getValueforTeam(Team team, int x, int y){
        if(paths == null || team.ordinal() >= paths.length || paths[team.ordinal()] == null) return 0;
        PathData path = paths[team.ordinal()];
        if(path.weights == null) return 0;
        int ax = x - path.offsetX;
        int ay = y - path.offsetY;
        if(ax < 0 || ay < 0 || ax >= path.size || ay >= path.sizeY) return Float.MAX_VALUE;
        float[] source = path.hasComplete ? path.completeWeights : path.weights;
        return source[ax + ay * path.size];
    }

    private boolean isSolid(int cellData){
        return (cellData & B_SOLID) != 0;
    }

    private boolean passable(int cellData, int teamOrdinal){
        if((cellData & B_SOLID) == 0) return true;
        return (cellData & B_BREAKABLE) != 0 && ((cellData >> TEAM_SHIFT) & 0x1F) != teamOrdinal;
    }

    private float costOf(int cellData){
        int cost = (cellData >> COST_SHIFT) & 0xFF;
        return Math.max(cost, 1);
    }

    private int cellAt(PathData path, int worldX, int worldY){
        int lx = worldX - path.offsetX;
        int ly = worldY - path.offsetY;
        if(lx < 0 || ly < 0 || lx >= path.size || ly >= path.sizeY) return CELL_BLOCKED;
        return path.tileData[lx + ly * path.size];
    }

    private int packCell(Tile tile){
        if(tile == null) return CELL_BLOCKED;

        int data = 0;
        if(tile.solid()) data |= B_SOLID;
        if(tile.breakable()) data |= B_BREAKABLE;

        Tile head = tile.target();
        data |= (head == null ? 0 : head.getTeam().ordinal()) << TEAM_SHIFT;

        int cost = tile.cost & 0xFF;
        if(cost < 1) cost = 1;
        data |= cost << COST_SHIFT;

        return data;
    }


    private WorldChunk memoChunk;
    private int memoCx = Integer.MIN_VALUE, memoCy, memoStamp;

    private void invalidateMemo(){
        memoCx = Integer.MIN_VALUE;
        memoChunk = null;
    }

    private Tile fastTile(int wx, int wy){
        if(!world.isOpenWorld()){
            return world.tile(wx, wy);
        }

        ChunkManager chunks = world.chunks();
        if(chunks == null) return null;

        int cx = MathUtils.floor((float)wx / ChunkManager.CHUNK_SIZE);
        int cy = MathUtils.floor((float)wy / ChunkManager.CHUNK_SIZE);

        WorldChunk chunk;
        if(cx == memoCx && cy == memoCy){
            chunk = memoChunk;
        }else{
            chunk = chunks.peekChunk(cx, cy);
            memoChunk = chunk;
            memoCx = cx;
            memoCy = cy;
            memoStamp = chunk == null ? -1 : chunk.pathStamp;
        }

        if(chunk == null || chunk.tiles == null) return null;
        if(chunk.pathStamp != memoStamp){
            chunk = chunks.peekChunk(cx, cy);
            memoChunk = chunk;
            memoStamp = chunk == null ? -1 : chunk.pathStamp;
            if(chunk == null || chunk.tiles == null) return null;
        }

        int lx = wx - cx * ChunkManager.CHUNK_SIZE;
        int ly = wy - cy * ChunkManager.CHUNK_SIZE;
        if(lx < 0 || ly < 0 || lx >= ChunkManager.CHUNK_SIZE || ly >= ChunkManager.CHUNK_SIZE) return null;
        return chunk.tiles[lx + ly * ChunkManager.CHUNK_SIZE];
    }

    /** Resolves a tile for gradient-following output without ever generating chunks. */
    private Tile peekWorldTile(int wx, int wy){
        return fastTile(wx, wy);
    }

    /**Clears all path data and rebuilds fields for active teams.*/
    private void clear(){
        paths = new PathData[Team.all.length];
        invalidateMemo();
        if(graph != null) graph.reset();

        for(Team team : Team.all){
            paths[team.ordinal()] = new PathData();
            if(state.teams.isActive(team)){
                createFor(team);
            }
        }

        world.spawner.checkAllQuadrants();
    }

    private PathData createFor(Team team){
        PathData path = paths[team.ordinal()];
        if(path == null){
            path = new PathData();
            paths[team.ordinal()] = path;
        }
        path.resetArrays();

        for(int y = 0; y < path.sizeY; y++){
            for(int x = 0; x < path.size; x++){
                int idx = x + y * path.size;
                path.tileData[idx] = packCell(fastTile(x + path.offsetX, y + path.offsetY));
                path.weights[idx] = Float.MAX_VALUE;
            }
        }
        path.completeWeights = path.weights.clone();
        path.hasComplete = false;
        path.dirty = false;
        path.heapSize = 0;

        seedTargets(team, path);
        updateFrontier(team, -1); //blocking initial flood
        System.arraycopy(path.weights, 0, path.completeWeights, 0, path.weights.length);
        path.hasComplete = true;
        return path;
    }

    /** Rebuilds the window around a new origin, preserving overlapping weights as an interim readable snapshot. */
    private void rebuildWindow(PathData path, int newOffX, int newOffY){
        int size = path.size;
        int sizeY = path.sizeY;
        int oldOffX = path.offsetX, oldOffY = path.offsetY;

        int[] newData = new int[size * sizeY];
        float[] newWeights = new float[size * sizeY];

        for(int y = 0; y < sizeY; y++){
            for(int x = 0; x < size; x++){
                int idx = x + y * size;
                newData[idx] = packCell(fastTile(x + newOffX, y + newOffY));
                newWeights[idx] = Float.MAX_VALUE;
            }
        }

        //copy the overlap region so units have sane weights while the refresh flood runs
        int copyX0 = Math.max(newOffX, oldOffX);
        int copyY0 = Math.max(newOffY, oldOffY);
        int copyX1 = Math.min(newOffX + size, oldOffX + size);
        int copyY1 = Math.min(newOffY + sizeY, oldOffY + sizeY);

        for(int wy = copyY0; wy < copyY1; wy++){
            int srcRow = (wy - oldOffY) * size + (copyX0 - oldOffX);
            int dstRow = (wy - newOffY) * size + (copyX0 - newOffX);
            System.arraycopy(path.weights, srcRow, newWeights, dstRow, Math.max(0, copyX1 - copyX0));
        }

        path.tileData = newData;
        path.weights = newWeights;
        path.offsetX = newOffX;
        path.offsetY = newOffY;
        path.completeWeights = newWeights.clone();
        path.hasComplete = true;
        path.dirty = true;
        path.heapSize = 0;
    }

    /**
     * Fine-grained A* over an active team's window snapshot. Works in both classic and
     * open world mode. Returns packed world tile coords from start to goal (exclusive of
     * start), or null when no route exists within the expansion budget.
     */
    public LongArray findUnitPath(Team team, float fromWorldX, float fromWorldY, float toWorldX, float toWorldY){
        if(paths == null) return null;
        PathData path = paths[team.ordinal()];
        if(path == null || path.tileData == null || !path.hasComplete) return null;

        int size = path.size;
        int sizeY = path.sizeY;
        //tile centers live at tile*tilesize (World.tileWorld uses scl2/round); floor() here
        //shifted every start/goal half a tile and made paths begin or end on a corner tile
        int sx = Mathf.scl2(fromWorldX, tilesize) - path.offsetX;
        int sy = Mathf.scl2(fromWorldY, tilesize) - path.offsetY;
        int gx = Mathf.scl2(toWorldX, tilesize) - path.offsetX;
        int gy = Mathf.scl2(toWorldY, tilesize) - path.offsetY;

        if(sx < 0 || sy < 0 || sx >= size || sy >= sizeY) return null;

        //snap goal into bounds
        gx = Mathf.clamp(gx, 0, size - 1);
        gy = Mathf.clamp(gy, 0, sizeY - 1);

        int start = sx + sy * size, goal = gx + gy * size;
        if(start == goal) return null;

        int teamOrdinal = team.ordinal();

        LongMap<Long> cameFrom = new LongMap<>();
        LongMap<Float> gScore = new LongMap<>();
        //binary min-heap keyed by f-score; parallel arrays store node positions and keys
        IntArray heap = new IntArray();
        FloatArray fKeys = new FloatArray();

        long startPacked = start, goalPacked = goal;
        gScore.put(startPacked, 0f);

        //push start
        {
            float h0 = Math.abs(sx - gx) + Math.abs(sy - gy);
            heap.add(start);
            fKeys.add(h0);
            siftHeapUp(heap, fKeys, heap.size - 1);
        }

        float deepBias = 0.02f;

        int expanded = 0;
        int maxExpansions = 12000;

        int bestSlot = start;
        float bestH = Math.abs(sx - gx) + Math.abs(sy - gy);
        LongArray result = null;

        while(heap.size > 0 && expanded++ < maxExpansions){
            //extract min
            int current = heap.get(0);
            int last = heap.pop();
            float lastKey = fKeys.pop();
            if(heap.size > 0){
                heap.set(0, last);
                fKeys.set(0, lastKey);
                siftHeapDown(heap, fKeys, 0);
            }

            if(current == goalPacked){
                result = new LongArray();
                long c = current;
                while(c != startPacked){
                    int cx = (int)(c % size) + path.offsetX;
                    int cy = (int)(c / size) + path.offsetY;
                    result.add(((long)cx << 32) | (cy & 0xFFFFFFFFL));
                    c = cameFrom.get(c, c);
                }
                result.reverse();
                break;
            }

            int px = current % size, py = current / size;

            float curH = Math.abs(px - gx) + Math.abs(py - gy);
            if(curH < bestH){
                bestH = curH;
                bestSlot = current;
            }

            for(int d = 0; d < 4; d++){
                int nx = px + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int ny = py + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if(nx < 0 || ny < 0 || nx >= size || ny >= sizeY) continue;

                int npos = nx + ny * size;
                int cell = path.tileData[npos];
                boolean isGoal = npos == goalPacked;
                if(!isGoal && !passable(cell, teamOrdinal)) continue;

                float step = costOf(cell);
                float ng = gScore.get(current, Float.MAX_VALUE) + step;
                //diagonals not used keeps paths wall-safe without corner checks
                if(ng < gScore.get(npos, Float.MAX_VALUE)){
                    cameFrom.put(npos, (long)current);
                    gScore.put(npos, ng);
                    float hf = Math.abs(nx - gx) + Math.abs(ny - gy);
                    heap.add(npos);
                    fKeys.add(ng + hf + ng * deepBias);
                    siftHeapUp(heap, fKeys, heap.size - 1);
                }
            }
        }

        if(result != null) return decimate(result);
        if(bestSlot == start) return null;

        result = new LongArray();
        long c = bestSlot;
        while(c != startPacked){
            int cx = (int)(c % size) + path.offsetX;
            int cy = (int)(c / size) + path.offsetY;
            result.add(((long)cx << 32) | (cy & 0xFFFFFFFFL));
            c = cameFrom.get(c, c);
        }
        result.reverse();
        return decimate(result);
    }

    static LongArray decimate(LongArray in){
        if(in == null || in.size <= 2) return in;

        LongArray out = new LongArray(Math.max(2, in.size / 2));
        out.add(in.items[0]);

        for(int i = 1; i < in.size - 1; i++){
            long a = out.peek();
            long b = in.items[i];
            long c = in.items[i + 1];
            int ax = (int)(a >> 32), ay = (int)a;
            int bx = (int)(b >> 32), by = (int)b;
            int cx = (int)(c >> 32), cy = (int)c;

            int e1x = bx - ax, e1y = by - ay;
            int e2x = cx - bx, e2y = cy - by;

            //sub-cell or two-tile noise between consecutive points
            if(e1x == 0 && e1y == 0 || e1x * e1x + e1y * e1y < 4) continue;
            //same heading: collinear midpoints don't change direction, drop them
            long cross = (long)e1x * e2y - (long)e1y * e2x;
            if(Math.abs(cross) < 24) continue;

            out.add(b);
        }

        out.add(in.peek());
        return out;
    }

    private static void siftHeapUp(IntArray heap, FloatArray keys, int i){
        while(i > 0){
            int p = (i - 1) / 2;
            if(keys.get(p) <= keys.get(i)) break;
            heap.swap(p, i);
            keys.swap(p, i);
            i = p;
        }
    }

    private static void siftHeapDown(IntArray heap, FloatArray keys, int i){
        int n = heap.size;
        while(true){
            int c = i * 2 + 1;
            if(c >= n) break;
            if(c + 1 < n && keys.get(c + 1) < keys.get(c)) c++;
            if(keys.get(c) >= keys.get(i)) break;
            heap.swap(c, i);
            keys.swap(c, i);
            i = c;
        }
    }

    /**
     * Finds a corridor of passable cells (packed world tile coords) leading from the unit's
     * position toward the cell closest (euclidean) to the goal within the connected component,
     * for cases where no complete route exists ("unreachable"/too-far targets). Returning a
     * waypoint stream instead of a single hop lets units keep contouring around large obstacle
     * clusters - each consumed corridor is re-queried from the new position. Returns null when
     * the unit is already at the closest reachable spot or no path data is available.
     */
    public LongArray findFallbackWaypoint(Team team, float fromWorldX, float fromWorldY, float toWorldX, float toWorldY){
        if(paths == null) return null;
        PathData path = paths[team.ordinal()];
        if(path == null || path.tileData == null || !path.hasComplete) return null;

        int size = path.size;
        int sizeY = path.sizeY;
        int sx = Mathf.scl2(fromWorldX, tilesize) - path.offsetX;
        int sy = Mathf.scl2(fromWorldY, tilesize) - path.offsetY;
        float gx = Mathf.scl2(toWorldX, tilesize) - path.offsetX + 0.5f;
        float gy = Mathf.scl2(toWorldY, tilesize) - path.offsetY + 0.5f;

        if(sx < 0 || sy < 0 || sx >= size || sy >= sizeY) return null;

        int start = sx + sy * size;
        int teamOrdinal = team.ordinal();

        Bits visited = new Bits(size * sizeY);
        int[] parent = new int[size * sizeY];
        java.util.Arrays.fill(parent, -1);
        IntArray queue = new IntArray();
        queue.add(start);
        visited.set(start);

        float sdx = sx + 0.5f - gx, sdy = sy + 0.5f - gy;
        float bestDist = sdx * sdx + sdy * sdy;
        int bestCell = -1;

        int maxCells = 8000;
        int head = 0;
        while(head < queue.size && maxCells-- > 0){
            int current = queue.get(head++);

            float cdx = current % size + 0.5f - gx, cdy = current / size + 0.5f - gy;
            float curDist = cdx * cdx + cdy * cdy;
            if(curDist < bestDist){
                bestDist = curDist;
                bestCell = current;
            }

            int px = current % size, py = current / size;
            for(int d = 0; d < 4; d++){
                int nx = px + (d == 0 ? 1 : d == 1 ? -1 : 0);
                int ny = py + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if(nx < 0 || ny < 0 || nx >= size || ny >= sizeY) continue;

                int npos = nx + ny * size;
                if(visited.get(npos)) continue;

                //walk through breakable blocks the waypoint may sit against an enemy wall
                if(!passable(path.tileData[npos], teamOrdinal)) continue;

                visited.set(npos);
                parent[npos] = current;
                queue.add(npos);
            }
        }

        if(bestCell < 0 || bestCell == start) return null;

        LongArray out = new LongArray();
        int cur = bestCell;
        int hops = 0;
        while(cur != -1 && cur != start && hops++ < 48){
            int wx = cur % size + path.offsetX;
            int wy = cur / size + path.offsetY;
            out.add(((long)wx << 32) | (wy & 0xFFFFFFFFL));
            cur = parent[cur];
        }
        out.reverse();
        return decimate(out);
    }

    private void seedTargets(Team team, PathData path){
        for(Tile other : world.indexer.getEnemy(team, BlockFlag.target)){
            if(other == null) continue;

            int ox = other.x - path.offsetX;
            int oy = other.y - path.offsetY;
            if(ox < 0 || oy < 0 || ox >= path.size || oy >= path.sizeY) continue;

            //seed even sealed-in targets neighbors relax toward them through breakable walls
            int idx = ox + oy * path.size;
            path.weights[idx] = 0;
            heapPush(path, idx, 0f);
        }
    }

    private void updateFrontier(Team team, long nsToRun){
        PathData path = paths[team.ordinal()];
        int teamOrdinal = team.ordinal();
        int size = path.size;
        int sizeY = path.sizeY;

        boolean hadAny = path.heapSize > 0;
        long start = TimeUtils.nanoTime();
        int counter = 0;

        while(path.heapSize > 0){
            int pos = heapPop(path);

            if(path.weights[pos] < path.heapVals[pos]){
                continue;
            }

            float cost = path.weights[pos];
            if(cost < Float.MAX_VALUE){
                int x = pos % size;
                int y = pos / size;

                for(int d = 0; d < 4; d++){
                    int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if(nx < 0 || ny < 0 || nx >= size || ny >= sizeY) continue;

                    int npos = nx + ny * size;
                    int cell = path.tileData[npos];
                    if(!passable(cell, teamOrdinal)) continue;

                    float nw = cost + costOf(cell);
                    if(nw < path.weights[npos]){
                        path.weights[npos] = nw;
                        heapPush(path, npos, nw);
                    }
                }
            }

            if(nsToRun >= 0 && (++counter) >= 200){
                counter = 0;
                if(TimeUtils.timeSinceNanos(start) >= nsToRun){
                    return;
                }
            }
        }

        //frontier drained
        if(hadAny){
            System.arraycopy(path.weights, 0, path.completeWeights, 0, path.weights.length);
            path.hasComplete = true;
        }
    }


    private void heapPush(PathData path, int pos, float weight){
        if(path.heapSize >= path.heap.length){
            int newCap = path.heap.length * 2;
            int[] nh = new int[newCap];
            System.arraycopy(path.heap, 0, nh, 0, path.heapSize);
            path.heap = nh;
        }
        path.heapVals[pos] = weight;

        int[] h = path.heap;
        float[] keys = path.heapVals;

        int i = path.heapSize++;
        while(i > 0){
            int p = (i - 1) / 2;
            if(keys[h[p]] <= weight) break;
            h[i] = h[p];
            i = p;
        }
        h[i] = pos;
    }

    /** Pops the minimum-weight cell position. */
    private int heapPop(PathData path){
        int[] h = path.heap;
        float[] keys = path.heapVals;

        int root = h[0];
        path.heapSize--;

        if(path.heapSize > 0){
            int lastPos = h[path.heapSize];
            float lastKey = keys[lastPos];

            int i = 0;
            while(true){
                int c = i * 2 + 1;
                if(c >= path.heapSize) break;
                if(c + 1 < path.heapSize && keys[h[c + 1]] < keys[h[c]]) c++;
                if(keys[h[c]] >= lastKey) break;
                h[i] = h[c];
                i = c;
            }
            h[i] = lastPos;
        }

        return root;
    }

    class PathData{
        int size;
        int sizeY;
        int offsetX, offsetY;
        float[] weights;
        float[] completeWeights;
        boolean hasComplete;
        boolean dirty;
        int[] tileData;
        int[] heap = new int[512];
        float[] heapVals = new float[512];
        int heapSize = 0;

        PathData(){
            size = world.width();
            sizeY = world.isOpenWorld() ? size : world.height();

            if(world.isOpenWorld()){
                int playerTX = players.length > 0 && players[0] != null
                        ? Mathf.scl2(players[0].x, tilesize) : 0;
                int playerTY = players.length > 0 && players[0] != null
                        ? Mathf.scl2(players[0].y, tilesize) : 0;
                offsetX = playerTX - size / 2;
                offsetY = playerTY - size / 2;
            }else{
                offsetX = 0;
                offsetY = 0;
            }
        }

        void resetArrays(){
            weights = new float[size * sizeY];
            completeWeights = new float[size * sizeY];
            tileData = new int[size * sizeY];
            heapVals = new float[size * sizeY];
            heap = new int[512];
            heapSize = 0;
            dirty = false;
            hasComplete = false;
        }
    }
}
