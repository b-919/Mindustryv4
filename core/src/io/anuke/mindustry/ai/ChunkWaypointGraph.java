package io.anuke.mindustry.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.BooleanArray;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.LongMap;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.mindustry.maps.generation.ChunkManager.WorldChunk;
import io.anuke.mindustry.world.Tile;

import static io.anuke.mindustry.Vars.*;

/**
 * Hierarchical pathfinding (HPA*) over open-world chunks.
 * <p>
 * Each loaded chunk is abstracted into entrance nodes on its borders (midpoints of maximal
 * runs of passable border tiles) plus connectivity components of its interior. Intra-chunk
 * edges connect entrance nodes sharing a component; inter-chunk edges connect entrance nodes
 * of adjacent chunks whose border runs overlap. A coarse A* over this tiny graph produces a
 * corridor of waypoints that units steer along when the fine-grained flow field has no
 * coverage (e.g. targets far outside the 352-tile window).
 * <p>
 * All abstraction data is cached per chunk and invalidated automatically through
 * {@link WorldChunk#pathStamp}, so there is no explicit lifecycle coupling with the
 * ChunkManager. Unloaded or cold-stored chunks simply have no nodes.
 */
public class ChunkWaypointGraph{
    private static final int CSIZE = ChunkManager.CHUNK_SIZE;
    private static final int SIDE_LEFT = 0, SIDE_RIGHT = 1, SIDE_BOTTOM = 2, SIDE_TOP = 3;
    private static final int MAX_NODES_PER_CHUNK = 120;
    private static final int MAX_EXPANSIONS = 3000;
    private static final int MAX_CACHE = 1024;

    private static int nextUid = 1;

    private final LongMap<ChunkNodes> cache = new LongMap<>();

    public void reset(){
        cache.clear();
    }

    /**
     * Finds a coarse chunk-level path. All coordinates are world tile coordinates.
     * Returns packed waypoints ((x << 32) | y), ending with the exact destination tile when
     * the goal chunk is resident, or null if the start is not resident/unreachable.
     * Goals in ungenerated/cold territory still produce a corridor that leads as far as the
     * loaded frontier in the goal direction; re-querying from the unit's new position keeps
     * extending it as more chunks load.
     */
    public LongArray findPath(float fromX, float fromY, float toX, float toY){
        ChunkManager cm = world.isOpenWorld() ? world.chunks() : null;
        if(cm == null) return null;

        //world tile centers sit at tile*tilesize, so round the continuous tile coordinate first
        int sfx = Math.round(fromX), sfy = Math.round(fromY);
        int tfx = Math.round(toX), tfy = Math.round(toY);

        int scx = MathUtils.floor((float)sfx / CSIZE), scy = MathUtils.floor((float)sfy / CSIZE);
        int tcx = MathUtils.floor((float)tfx / CSIZE), tcy = MathUtils.floor((float)tfy / CSIZE);

        WorldChunk startChunk = cm.peekChunk(scx, scy);
        if(startChunk == null || startChunk.tiles == null) return null;

        ChunkNodes start = getNodes(cm, scx, scy);
        if(start == null) return null;

        int slx = clampLocal(sfx - scx * CSIZE), sly = clampLocal(sfy - scy * CSIZE);
        int glx = clampLocal(tfx - tcx * CSIZE), gly = clampLocal(tfy - tcy * CSIZE);

        //solid endpoints (cores, walls, orders onto trees/lakes) snap to the nearest passable tile so
        //attacks on structures and long-range orders still produce a corridor into the destination chunk
        if(start.comp[slx + sly * CSIZE] == 0){
            int[] snapped = nearestPassable(start, slx, sly);
            if(snapped == null) return null;
            slx = snapped[0];
            sly = snapped[1];
        }

        int startNode = nearestNodeInComponent(start, slx, sly);
        if(startNode < 0) return null;

        WorldChunk goalChunk = cm.peekChunk(tcx, tcy);
        ChunkNodes goal = goalChunk != null && goalChunk.tiles != null ? getNodes(cm, tcx, tcy) : null;
        int goalNode = -1;
        if(goal != null){
            if(goal.comp[glx + gly * CSIZE] == 0){
                int[] snapped = nearestPassable(goal, glx, gly);
                if(snapped == null){
                    goal = null;
                }else{
                    glx = snapped[0];
                    gly = snapped[1];
                }
            }
            if(goal != null) goalNode = nearestNodeInComponent(goal, glx, gly);
        }

        if(goal != null && goalNode >= 0 && start == goal && start.nComp[startNode] == goal.nComp[goalNode]){
            LongArray out = new LongArray(2);
            out.add(packWorld(scx, scy, start.nLx[startNode], start.nLy[startNode]));
            out.add(packWorld(tcx, tcy, glx, gly));
            return out;
        }

        return search(cm, start, startNode, goal, goalNode, tcx, tcy, glx, gly);
    }

    private LongArray search(ChunkManager cm, ChunkNodes startNodes, int startNode, ChunkNodes goalNodes, int goalNode,
                             int tcx, int tcy, int glx, int gly){
        Ctx ctx = new Ctx();

        float gxw = tcx * CSIZE + glx + 0.5f, gyw = tcy * CSIZE + gly + 0.5f;

        int startSlot = ctx.slot(startNodes.uid, startNode);
        ctx.keys.items[startSlot] = packKey(startNodes.cx, startNodes.cy);
        ctx.wx.items[startSlot] = (int)nodeWx(startNodes, startNode);
        ctx.wy.items[startSlot] = (int)nodeWy(startNodes, startNode);
        ctx.g.items[startSlot] = 0;
        ctx.push(startSlot, heuristic(nodeWx(startNodes, startNode), nodeWy(startNodes, startNode), gxw, gyw));

        int goalSlot = -1;
        int bestSlot = startSlot;
        float bestH = heuristic(nodeWx(startNodes, startNode), nodeWy(startNodes, startNode), gxw, gyw);
        int expansions = 0;

        while(ctx.heapSize > 0){
            int cur = ctx.pop();
            if(ctx.closed.items[cur]) continue;
            ctx.closed.items[cur] = true;

            if(goalNodes != null && goalNode >= 0 && ctx.uids.items[cur] == goalNodes.uid && ctx.nodes.items[cur] == goalNode){
                goalSlot = cur;
                break;
            }

            float h = ctx.f.items[cur] - ctx.g.items[cur];
            if(h < bestH){
                bestH = h;
                bestSlot = cur;
            }
            if(++expansions > MAX_EXPANSIONS) break;

            expand(ctx, cm, cur, gxw, gyw);
        }

        int target = goalSlot >= 0 ? goalSlot : bestSlot;
        if(target < 0 || (goalSlot < 0 && bestSlot == startSlot)) return null;

        //reconstruct waypoint chain (world tile coords stored per slot)
        LongArray out = new LongArray();
        for(int cur = target; cur != -1; cur = ctx.parent.items[cur]){
            out.add(packWorld(0, 0, ctx.wx.items[cur], ctx.wy.items[cur]));
        }
        out.reverse();

        if(goalSlot >= 0){
            long last = out.peek();
            int lx = (int)(last >> 32), ly = (int)last;
            if(lx != tcx * CSIZE + glx || ly != tcy * CSIZE + gly){
                out.add(packWorld(tcx, tcy, glx, gly));
            }
        }
        return Pathfinder.decimate(out);
    }

    /** Expands implicit edges of the node stored at {@code slot}: same-component entrances + overlapping foreign border runs. */
    private void expand(Ctx ctx, ChunkManager cm, int slot, float gxw, float gyw){
        long key = ctx.keys.items[slot];
        ChunkNodes n = getNodes(cm, keyCx(key), keyCy(key));
        if(n == null) return;

        int node = ctx.nodes.items[slot];
        int cx = n.cx, cy = n.cy;
        float nx = cx * CSIZE + n.nLx[node] + 0.5f, ny = cy * CSIZE + n.nLy[node] + 0.5f;
        float baseG = ctx.g.items[slot];

        //same-chunk connections within the component
        for(int j = 0; j < n.nodeCount; j++){
            if(j == node || n.nComp[j] != n.nComp[node]) continue;
            float mx = cx * CSIZE + n.nLx[j] + 0.5f, my = cy * CSIZE + n.nLy[j] + 0.5f;
            relax(ctx, n, j, slot, baseG + manhattan(nx, ny, mx, my) + 1f, gxw, gyw);
        }

        //cross-border connections to the adjacent chunk's overlapping entrance runs
        //lazily builds the neighbor abstraction so corridors are never cut off
        int side = n.nSide[node];
        int acx = cx + (side == SIDE_LEFT ? -1 : side == SIDE_RIGHT ? 1 : 0);
        int acy = cy + (side == SIDE_BOTTOM ? -1 : side == SIDE_TOP ? 1 : 0);

        ChunkNodes adj = getNodes(cm, acx, acy);
        if(adj == null) return;

        int adjSide = side == SIDE_LEFT ? SIDE_RIGHT : side == SIDE_RIGHT ? SIDE_LEFT :
                      side == SIDE_BOTTOM ? SIDE_TOP : SIDE_BOTTOM;

        for(int j = 0; j < adj.nodeCount; j++){
            if(adj.nSide[j] != adjSide) continue;
            //runs must share at least one border tile
            if(Math.max(n.nStart[node], adj.nStart[j]) > Math.min(n.nEnd[node], adj.nEnd[j])) continue;

            float mx = acx * CSIZE + adj.nLx[j] + 0.5f, my = acy * CSIZE + adj.nLy[j] + 0.5f;
            relax(ctx, adj, j, slot, baseG + manhattan(nx, ny, mx, my) + 1f, gxw, gyw);
        }
    }

    private void relax(Ctx ctx, ChunkNodes owner, int node, int fromSlot, float newG, float gxw, float gyw){
        int s = ctx.slot(owner.uid, node);
        if(newG < ctx.g.items[s]){
            ctx.g.items[s] = newG;
            ctx.parent.items[s] = fromSlot;
            ctx.keys.items[s] = packKey(owner.cx, owner.cy);
            ctx.wx.items[s] = owner.cx * CSIZE + owner.nLx[node];
            ctx.wy.items[s] = owner.cy * CSIZE + owner.nLy[node];
            ctx.push(s, newG + heuristic(ctx.wx.items[s], ctx.wy.items[s], gxw, gyw));
        }
    }

    private static float manhattan(float ax, float ay, float bx, float by){
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }

    private static float heuristic(float ax, float ay, float bx, float by){
        return manhattan(ax, ay, bx, by);
    }

    /** Returns the entrance node of this chunk closest to the local point within the point's connectivity component, or -1 if sealed off. */
    private int nearestNodeInComponent(ChunkNodes n, int lx, int ly){
        short comp = n.comp[lx + ly * CSIZE];
        if(comp == 0) return -1;

        int best = -1, bestD = Integer.MAX_VALUE;
        for(int i = 0; i < n.nodeCount; i++){
            if(n.nComp[i] != comp) continue;
            int d = Math.abs(n.nLx[i] - lx) + Math.abs(n.nLy[i] - ly);
            if(d < bestD){
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** Snaps an impassable local point to the nearest passable tile inside its chunk (ring search), or null when none exists. */
    private int[] nearestPassable(ChunkNodes n, int lx, int ly){
        if(n.comp[lx + ly * CSIZE] != 0) return new int[]{lx, ly};
        for(int r = 1; r < CSIZE; r++){
            for(int dy = -r; dy <= r; dy++){
                for(int dx = -r; dx <= r; dx++){
                    if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int nx = lx + dx, ny = ly + dy;
                    if(nx < 0 || ny < 0 || nx >= CSIZE || ny >= CSIZE) continue;
                    if(n.comp[nx + ny * CSIZE] != 0) return new int[]{nx, ny};
                }
            }
        }
        return null;
    }

    /** Builds or returns the cached abstraction for a chunk; null when the chunk is not resident. */
    private ChunkNodes getNodes(ChunkManager cm, int cx, int cy){
        WorldChunk chunk = cm.peekChunk(cx, cy);
        if(chunk == null || chunk.tiles == null){
            cache.remove(packKey(cx, cy));
            return null;
        }

        ChunkNodes nodes = cache.get(packKey(cx, cy));
        if(nodes != null && nodes.stamp == chunk.pathStamp){
            return nodes;
        }

        nodes = build(cx, cy, chunk);
        if(cache.size >= MAX_CACHE){
            cache.clear();
        }
        cache.put(packKey(cx, cy), nodes);
        return nodes;
    }

    private ChunkNodes build(int cx, int cy, WorldChunk chunk){
        ChunkNodes n = new ChunkNodes(cx, cy);

        //label connected components of passable tiles with BFS
        short[] stack = new short[CSIZE * CSIZE];
        short[] comp = n.comp;
        short compCount = 0;

        for(int i = 0; i < comp.length; i++){
            if(comp[i] != 0 || !passable(chunk.tiles[i])) continue;

            compCount++;
            int head = 0, tail = 0;
            stack[tail++] = (short)i;
            comp[i] = compCount;
            while(head < tail){
                int cur = stack[head++] & 0xFFFF;
                int x = cur % CSIZE, y = cur / CSIZE;

                //neighbors
                for(int d = 0; d < 4; d++){
                    int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0);
                    int ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if(nx < 0 || ny < 0 || nx >= CSIZE || ny >= CSIZE) continue;

                    int ni = nx + ny * CSIZE;
                    if(comp[ni] != 0 || !passable(chunk.tiles[ni])) continue;
                    comp[ni] = compCount;
                    stack[tail++] = (short)ni;
                }
            }
        }
        n.compCount = compCount;

        //entrance extraction: midpoints of maximal passable runs on each border
        scanSide(n, chunk, SIDE_LEFT, true);
        scanSide(n, chunk, SIDE_RIGHT, true);
        scanSide(n, chunk, SIDE_BOTTOM, false);
        scanSide(n, chunk, SIDE_TOP, false);

        n.stamp = chunk.pathStamp;
        return n;
    }

    private void scanSide(ChunkNodes n, WorldChunk chunk, int side, boolean vertical){
        for(int o = 0; o < CSIZE; ){
            int fx, fy; //fixed coordinate of the border line
            if(vertical){
                fx = side == SIDE_LEFT ? 0 : CSIZE - 1;
                fy = o;
            }else{
                fx = o;
                fy = side == SIDE_BOTTOM ? 0 : CSIZE - 1;
            }

            if(!passable(chunk.tiles[fx + fy * CSIZE])){
                o++;
                continue;
            }

            int start = o;
            while(o < CSIZE){
                int px = vertical ? fx : o, py = vertical ? o : fy;
                if(!passable(chunk.tiles[px + py * CSIZE])) break;
                o++;
            }
            int end = o - 1;

            if(n.nodeCount >= MAX_NODES_PER_CHUNK) break;

            int idx = n.nodeCount++;
            n.nLx[idx] = (short)(vertical ? fx : (start + end) / 2);
            n.nLy[idx] = (short)(vertical ? (start + end) / 2 : fy);
            n.nComp[idx] = n.comp[n.nLx[idx] + n.nLy[idx] * CSIZE];
            n.nSide[idx] = (byte)side;
            n.nStart[idx] = (short)start;
            n.nEnd[idx] = (short)end;
        }
    }

    /**
     * Neutral coarse passability: natural walls/cliffs block, liquid floors are avoided
     * (most coarse traffic is ground units), everything else counts as traversable.
     * Breakable structures count as open terrain here - enemies chew through them anyway -
     * and the fine flow field still governs movement inside the window.
     */
    private boolean passable(Tile tile){
        return !tile.solid() && !tile.floor().isLiquid;
    }

    private static long packKey(int cx, int cy){
        return ((long)cx << 32) | (cy & 0xFFFFFFFFL);
    }

    private static int keyCx(long key){
        return (int)(key >> 32);
    }

    private static int keyCy(long key){
        return (int)key;
    }

    private static long packWorld(int cx, int cy, int lx, int ly){
        int x = cx * CSIZE + lx, y = cy * CSIZE + ly;
        return ((long)x << 32) | (y & 0xFFFFFFFFL);
    }

    private static float nodeWx(ChunkNodes n, int node){
        return n.cx * CSIZE + n.nLx[node] + 0.5f;
    }

    private static float nodeWy(ChunkNodes n, int node){
        return n.cy * CSIZE + n.nLy[node] + 0.5f;
    }

    private static int clampLocal(int v){
        return v < 0 ? 0 : (v >= CSIZE ? CSIZE - 1 : v);
    }

    /** Per-chunk hierarchical abstraction. */
    private static class ChunkNodes{
        final int uid = nextUid++;
        final int cx, cy;
        int stamp = -1;
        /** connectivity component per tile (+1; 0 = impassable) */
        final short[] comp = new short[CSIZE * CSIZE];
        @SuppressWarnings("unused")
        short compCount;
        int nodeCount;
        final short[] nLx = new short[MAX_NODES_PER_CHUNK], nLy = new short[MAX_NODES_PER_CHUNK];
        final short[] nComp = new short[MAX_NODES_PER_CHUNK];
        final byte[] nSide = new byte[MAX_NODES_PER_CHUNK];
        final short[] nStart = new short[MAX_NODES_PER_CHUNK], nEnd = new short[MAX_NODES_PER_CHUNK];

        ChunkNodes(int cx, int cy){
            this.cx = cx;
            this.cy = cy;
        }
    }

    /** Scratch state for one A* search over chunk entrance nodes. */
    private static class Ctx{
        final LongMap<Integer> slots = new LongMap<>();
        final LongArray uids = new LongArray();
        final LongArray keys = new LongArray();
        final IntArray nodes = new IntArray();
        final IntArray wx = new IntArray(), wy = new IntArray();
        final FloatArray g = new FloatArray();
        final FloatArray f = new FloatArray();
        final IntArray parent = new IntArray();
        final BooleanArray closed = new BooleanArray();
        int[] heap = new int[64];
        int heapSize = 0;

        int slot(long uid, int node){
            Integer boxed = slots.get(uid << 8 | node);
            if(boxed != null) return boxed;
            int s = uids.size;
            slots.put(uid << 8 | node, s);
            uids.add(uid);
            keys.add(0);
            nodes.add(node);
            wx.add(0);
            wy.add(0);
            g.add(Float.MAX_VALUE);
            f.add(Float.MAX_VALUE);
            parent.add(-1);
            closed.add(false);
            return s;
        }

        void push(int slot, float priority){
            f.items[slot] = priority;
            if(heapSize >= heap.length){
                heap = java.util.Arrays.copyOf(heap, heap.length * 2);
            }
            heap[heapSize++] = slot;
            siftUp(heapSize - 1);
        }

        int pop(){
            int root = heap[0];
            heapSize--;
            if(heapSize > 0){
                heap[0] = heap[heapSize];
                siftDown(0);
            }
            return root;
        }

        private void siftUp(int i){
            int v = heap[i];
            float fv = f.items[v];
            while(i > 0){
                int p = (i - 1) / 2;
                int pv = heap[p];
                if(f.items[pv] <= fv) break;
                heap[i] = pv;
                i = p;
            }
            heap[i] = v;
        }

        private void siftDown(int i){
            int v = heap[i];
            float fv = f.items[v];
            while(true){
                int c = i * 2 + 1;
                if(c >= heapSize) break;
                if(c + 1 < heapSize && f.items[heap[c + 1]] < f.items[heap[c]]) c++;
                if(f.items[heap[c]] >= fv) break;
                heap[i] = heap[c];
                i = c;
            }
            heap[i] = v;
        }
    }
}
