package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.utils.LongArray;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;

import java.util.Arrays;

import static io.anuke.mindustry.Vars.tilesize;
import static io.anuke.mindustry.Vars.world;

/**
 * Shared path state and A* implementation for ground-based units.
 * <p>
 * One instance lives on every {@link BaseUnit}; ground, tank and leg units use it for
 * order paths and combat paths. The chunk/HPA fallback remains in {@link BaseUnit} because
 * it is also used by flying units and by combat steering.
 */
public class UnitPath{
    private static final int defaultMoveMaxNodes = 1100;
    private static final int defaultOrderMaxNodes = 1500;
    private static final int defaultMoveRepathDelay = 15;
    private static final int defaultOrderRepathDelay = 30;

    public final LongArray orderPath = new LongArray();
    public int orderPathCursor;
    public int orderPathRepath;

    public final LongArray movePath = new LongArray();
    public int movePathCursor;
    public int movePathRepath;
    public float movePathTargetX, movePathTargetY;

    private final LongArray astarRev = new LongArray();
    private final Translator moveVec = new Translator();

    //Primitive A* scratch. Arrays are lazily allocated and reused across searches so the hot
    //path allocates nothing and never boxes. Node ids are dense ints; positions are packed longs.
    private long[] nodePos;
    private int[] nodeParent;
    private float[] nodeG;
    private boolean[] nodeClosed;
    private int nodeCount;

    //open-addressing packed-long -> node id map (-1 = empty).
    private long[] mapKeys;
    private int[] mapVals;
    private int mapMask;

    //binary min-heap of node ids, keyed by f-score then node id.
    private int[] heapIds;
    private float[] heapKeys;
    private int heapSize;

    private int moveMaxNodes = defaultMoveMaxNodes;
    private int orderMaxNodes = defaultOrderMaxNodes;
    private int moveRepathDelay = defaultMoveRepathDelay;
    private int orderRepathDelay = defaultOrderRepathDelay;

    /** Applies the tighter node budgets used by tanks. */
    public void setTankLimits(){
        moveMaxNodes = 400;
        orderMaxNodes = 700;
    }

    public void clearOrder(){
        orderPath.clear();
        orderPathCursor = 0;
        orderPathRepath = 0;
    }

    public void clearMove(){
        movePath.clear();
        movePathCursor = 0;
        movePathRepath = 0;
    }

    /** Clears both routes; used when the unit is removed or its goal changes completely. */
    public void clear(){
        clearOrder();
        clearMove();
    }

    /** Follows the current order using tile A*; falls back to the cached chunk route. */
    public void followOrderPath(BaseUnit unit){
        Tile start = world.tileWorld(unit.x, unit.y);
        Tile goal = world.tileWorld(unit.getOrderX(), unit.getOrderY());

        if(start == null || goal == null){
            moveOrHoldOrder(unit, goal);
            return;
        }

        goal = findPassableGoal(unit, goal);
        if(start == goal){
            return;
        }

        if(orderPathRepath <= 0 || orderPath.size == 0 || orderPathCursor >= orderPath.size){
            buildOrderPath(unit, start, goal);
            orderPathRepath = orderRepathDelay;
        }else{
            orderPathRepath--;
        }

        if(orderPath.size == 0 || orderPathCursor >= orderPath.size){
            if(unit.steerAlongChunkPath(goal.worldx(), goal.worldy())){
                return;
            }
            moveOrHoldOrder(unit, goal);
            return;
        }

        Tile waypoint = world.tile(orderPath.get(orderPathCursor));
        if(waypoint == null){
            buildOrderPath(unit, start, goal);
            if(orderPath.size == 0){
                moveOrHoldOrder(unit, goal);
                return;
            }
            waypoint = world.tile(orderPath.get(orderPathCursor));
            if(waypoint == null){
                moveOrHoldOrder(unit, goal);
                return;
            }
        }

        if(Mathf.dst(unit.x - waypoint.worldx(), unit.y - waypoint.worldy()) <= tilesize * 0.4f
            && (orderPathCursor + 1 >= orderPath.size || unit.hopClear(world.tile(orderPath.get(orderPathCursor + 1))))){
            orderPathCursor++;
            if(orderPathCursor >= orderPath.size){
                moveOrHoldOrder(unit, goal);
                return;
            }
            waypoint = world.tile(orderPath.get(orderPathCursor));
            if(waypoint == null){
                moveOrHoldOrder(unit, goal);
                return;
            }
        }

        if(!unit.hopClear(waypoint)){
            orderPathRepath = 0;
            recenter(unit);
            return;
        }

        unit.moveTo(waypoint.worldx(), waypoint.worldy());
    }

    /** Moves toward a world position using tile A*; used by combat movement. */
    public void moveWithPathfinding(BaseUnit unit, float targetX, float targetY){
        Tile start = world.tileWorld(unit.x, unit.y);
        Tile goal = world.tileWorld(targetX, targetY);

        if(start == null || goal == null){
            moveOrHoldMove(unit, targetX, targetY);
            return;
        }

        goal = findPassableGoal(unit, goal);
        if(start == goal) return;

        boolean needsRepath = movePathRepath <= 0 || movePath.size == 0 || movePathCursor >= movePath.size ||
                Mathf.dst(movePathTargetX - targetX, movePathTargetY - targetY) > tilesize * 3;

        if(needsRepath){
            buildMovePath(unit, start, goal);
            movePathTargetX = targetX;
            movePathTargetY = targetY;
            movePathRepath = moveRepathDelay;
        }else{
            movePathRepath--;
        }

        if(movePath.size == 0 || movePathCursor >= movePath.size){
            moveOrHoldMove(unit, targetX, targetY);
            return;
        }

        Tile waypoint = world.tile(movePath.get(movePathCursor));
        if(waypoint == null){
            moveOrHoldMove(unit, targetX, targetY);
            return;
        }

        if(Mathf.dst(unit.x - waypoint.worldx(), unit.y - waypoint.worldy()) <= tilesize * 0.4f
            && (movePathCursor + 1 >= movePath.size || unit.hopClear(world.tile(movePath.get(movePathCursor + 1))))){
            movePathCursor++;
            if(movePathCursor >= movePath.size){
                moveOrHoldMove(unit, targetX, targetY);
                return;
            }
            waypoint = world.tile(movePath.get(movePathCursor));
            if(waypoint == null){
                moveOrHoldMove(unit, targetX, targetY);
                return;
            }
        }

        if(unit.stuckTime > 8f){
            clearMove();
            float jitterAngle = Mathf.atan2(targetX - unit.x, targetY - unit.y) + Mathf.range(120f);
            unit.getVelocity().add(moveVec.trns(jitterAngle, unit.type.speed * 0.7f * Timers.delta()));
            if(!unit.isAiming()){
                unit.rotation = Mathf.slerpDelta(unit.rotation, unit.getVelocity().angle(), unit.type.rotatespeed);
            }
            return;
        }

        if(!unit.hopClear(waypoint)){
            movePathRepath = 0;
            recenter(unit);
            return;
        }

        unit.moveTo(waypoint.worldx(), waypoint.worldy());
    }

    /** Straight-line fallback for an order: only used when the body can actually traverse it. */
    private void moveOrHoldOrder(BaseUnit unit, Tile goal){
        if(goal != null && unit.hopClear(goal)){
            unit.moveTo(goal.worldx(), goal.worldy());
            return;
        }
        orderPathRepath = 0;
        recenter(unit);
    }

    /** Straight-line fallback for combat movement: only used when the body can traverse it. */
    private void moveOrHoldMove(BaseUnit unit, float wx, float wy){
        if(unit.hopClear(wx, wy)){
            unit.moveTo(wx, wy);
            return;
        }
        movePathRepath = 0;
        recenter(unit);
    }

    /** Re-aligns on the current tile so the next repath starts from a known, safe spot. */
    public void recenter(BaseUnit unit){
        Tile here = world.tileWorld(unit.x, unit.y);
        if(here != null && Mathf.dst(unit.x - here.worldx(), unit.y - here.worldy()) > 1f){
            unit.moveTo(here.worldx(), here.worldy());
        }
    }

    private Tile findPassableGoal(BaseUnit unit, Tile goal){
        if(orderPassable(unit, goal)) return goal;
        for(int r = 1; r <= 3; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    Tile t = world.tile(goal.x + dx, goal.y + dy);
                    if(t != null && orderPassable(unit, t)) return t;
                }
            }
        }
        return goal;
    }

    private boolean orderPassable(BaseUnit unit, Tile tile){
        if(tile.solid() && !(tile.breakable() && tile.target().getTeam() != unit.getTeam())) return false;
        return tile.floor().drownTime <= 0f;
    }

    private void buildMovePath(BaseUnit unit, Tile start, Tile goal){
        movePath.clear();
        movePathCursor = 0;

        if(start == goal) return;

        long startPos = start.packedPosition();
        long goalPos = goal.packedPosition();

        search(unit, start, goal, startPos, goalPos, moveMaxNodes, movePath);
    }

    private void buildOrderPath(BaseUnit unit, Tile start, Tile goal){
        orderPath.clear();
        orderPathCursor = 0;

        if(start == goal){
            return;
        }

        long startPos = start.packedPosition();
        long goalPos = goal.packedPosition();

        search(unit, start, goal, startPos, goalPos, orderMaxNodes, orderPath);
    }

    /**Runs the tile A* from {@code start} to {@code goal}, writing the packed waypoints into {@code out}.*/
    private void search(BaseUnit unit, Tile start, Tile goal, long startPos, long goalPos, int maxNodes, LongArray out){
        prepareAstar(start, goal, startPos);

        int expanded = 0;
        while(heapSize > 0 && expanded < maxNodes){
            int node = popHeap();
            if(nodeClosed[node]) continue;
            nodeClosed[node] = true;

            long current = nodePos[node];
            if(current == goalPos){
                reconstruct(out, startPos, goalPos);
                return;
            }

            expandNode(unit, node, current, goal);
            expanded++;
        }
    }

    private void prepareAstar(Tile start, Tile goal, long startPos){
        nodeCount = 0;
        heapSize = 0;

        if(mapVals == null){
            ensureMapCapacity(1);
        }else{
            Arrays.fill(mapVals, -1);
        }

        int startId = addNode(startPos);
        nodeG[startId] = 0f;
        pushHeap(startId, (Math.abs(start.x - goal.x) + Math.abs(start.y - goal.y)) * 10f);
    }

    /**Expands a node's 8-neighbourhood, updating g/parent and re-pushing improved nodes.*/
    private void expandNode(BaseUnit unit, int node, long currentPos, Tile goal){
        Tile currentTile = world.tile(currentPos);
        if(currentTile == null) return;

        float currentG = nodeG[node];

        for(int sx = -1; sx <= 1; sx++){
            for(int sy = -1; sy <= 1; sy++){
                if(sx == 0 && sy == 0) continue;
                int nx = currentTile.x + sx, ny = currentTile.y + sy;
                Tile next = world.tile(nx, ny);
                if(next == null || !orderPassable(unit, next)) continue;
                if(sx != 0 && sy != 0 && (world.solid(currentTile.x + sx, currentTile.y) || world.solid(currentTile.x, currentTile.y + sy))){
                    continue;
                }

                long nextPos = next.packedPosition();
                int nextNode = getMap(nextPos);
                if(nextNode == -1){
                    nextNode = addNode(nextPos);
                }else if(nodeClosed[nextNode]){
                    continue;
                }

                float stepCost = (sx == 0 || sy == 0 ? 10f : 14f) + (int)(next.cost * 2f);
                float tentativeG = currentG + stepCost;
                if(tentativeG < nodeG[nextNode]){
                    nodeG[nextNode] = tentativeG;
                    nodeParent[nextNode] = node;
                    float heuristic = (Math.abs(next.x - goal.x) + Math.abs(next.y - goal.y)) * 10f;
                    pushHeap(nextNode, tentativeG + heuristic);
                }
            }
        }
    }

    /**Writes the parent chain from {@code goalPos} back to {@code startPos}, start-ward first and excluding start.*/
    private void reconstruct(LongArray out, long startPos, long goalPos){
        astarRev.clear();
        long cur = goalPos;
        while(cur != startPos){
            astarRev.add(cur);
            int id = getMap(cur);
            cur = nodePos[nodeParent[id]];
        }

        for(int i = astarRev.size - 1; i >= 0; i--){
            out.add(astarRev.get(i));
        }
    }

    private int addNode(long pos){
        ensureNodeCapacity(nodeCount + 1);
        int id = nodeCount++;
        nodePos[id] = pos;
        nodeParent[id] = -1;
        nodeG[id] = Float.MAX_VALUE;
        nodeClosed[id] = false;
        putMap(pos, id);
        return id;
    }

    private int hash(long key){
        long h = key * 0x9E3779B97F4A7C15L;
        return (int)(h ^ (h >>> 32)) & mapMask;
    }

    private void putMap(long key, int value){
        ensureMapCapacity(nodeCount);
        int i = hash(key);
        while(mapVals[i] != -1){
            if(mapKeys[i] == key){
                mapVals[i] = value;
                return;
            }
            i = (i + 1) & mapMask;
        }
        mapKeys[i] = key;
        mapVals[i] = value;
    }

    private int getMap(long key){
        int i = hash(key);
        while(mapVals[i] != -1){
            if(mapKeys[i] == key) return mapVals[i];
            i = (i + 1) & mapMask;
        }
        return -1;
    }

    private void ensureNodeCapacity(int required){
        if(nodePos != null && required <= nodePos.length) return;
        int cap = Math.max(64, Integer.highestOneBit(required) << 1);
        nodePos = nodePos == null ? new long[cap] : Arrays.copyOf(nodePos, cap);
        nodeParent = nodeParent == null ? new int[cap] : Arrays.copyOf(nodeParent, cap);
        nodeG = nodeG == null ? new float[cap] : Arrays.copyOf(nodeG, cap);
        nodeClosed = nodeClosed == null ? new boolean[cap] : Arrays.copyOf(nodeClosed, cap);
    }

    private void ensureMapCapacity(int requiredEntries){
        int requiredSlots = requiredEntries * 2;
        if(mapMask > 0 && requiredSlots <= mapMask + 1) return;
        int slots = Math.max(64, Integer.highestOneBit(requiredSlots) << 1);
        long[] oldKeys = mapKeys;
        int[] oldVals = mapVals;
        mapKeys = new long[slots];
        mapVals = new int[slots];
        Arrays.fill(mapVals, -1);
        mapMask = slots - 1;
        if(oldKeys != null){
            for(int i = 0; i < oldKeys.length; i++){
                if(oldVals[i] != -1) putMap(oldKeys[i], oldVals[i]);
            }
        }
    }

    private void pushHeap(int node, float key){
        ensureHeapCapacity(heapSize + 1);
        int i = heapSize++;
        while(i > 0){
            int p = (i - 1) >> 1;
            if(heapKeys[p] < key || (heapKeys[p] == key && heapIds[p] <= node)) break;
            heapIds[i] = heapIds[p];
            heapKeys[i] = heapKeys[p];
            i = p;
        }
        heapIds[i] = node;
        heapKeys[i] = key;
    }

    private int popHeap(){
        int root = heapIds[0];
        int size = --heapSize;
        if(size > 0){
            int last = heapIds[size];
            float lastKey = heapKeys[size];
            int i = 0;
            while(true){
                int c = i * 2 + 1;
                if(c >= size) break;
                int right = c + 1;
                if(right < size && (heapKeys[right] < heapKeys[c] || (heapKeys[right] == heapKeys[c] && heapIds[right] < heapIds[c]))){
                    c = right;
                }
                if(heapKeys[c] > lastKey || (heapKeys[c] == lastKey && heapIds[c] >= last)) break;
                heapIds[i] = heapIds[c];
                heapKeys[i] = heapKeys[c];
                i = c;
            }
            heapIds[i] = last;
            heapKeys[i] = lastKey;
        }
        return root;
    }

    private void ensureHeapCapacity(int required){
        if(heapIds != null && required <= heapIds.length) return;
        int cap = Math.max(64, Integer.highestOneBit(required) << 1);
        heapIds = heapIds == null ? new int[cap] : Arrays.copyOf(heapIds, cap);
        heapKeys = heapKeys == null ? new float[cap] : Arrays.copyOf(heapKeys, cap);
    }
}
