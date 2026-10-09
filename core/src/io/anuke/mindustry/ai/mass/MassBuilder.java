package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Queue;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Log;

import static io.anuke.mindustry.Vars.world;

/** Core initialization, the vein building lines and the pathfinding between them */
public class MassBuilder{
    static ObjectSet<Tile> initializedCores = new ObjectSet<>();
    static Array<BuildingLine> activeLines = new Array<>();
    //every tile occupied by a line or its start, mirroring the old per-line containsTile check for area clearing
    private static final ObjectSet<Tile> lineOccupied = new ObjectSet<>();

    public static void update(ObjectSet<Tile> cores){
        // Remove tracking for destroyed cores
        ObjectSet.ObjectSetIterator<Tile> it = initializedCores.iterator();
        while(it.hasNext){
            Tile tile = it.next();
            if(!cores.contains(tile)){
                it.remove();
                MassEconomy.forgetCore(tile);
            }
        }

        // Remove lines belonging to destroyed cores btw some line will keep respawning after the core is destroyed, for this destroy all cores/hives
        for(int i = activeLines.size - 1; i >= 0; i--){
            BuildingLine line = activeLines.get(i);
            if(!cores.contains(line.core)){
                unmarkLine(line);
                activeLines.removeIndex(i);
            }
        }

        for(Tile core : cores){
            if(!initializedCores.contains(core)){
                startBuilding(core);
                if(MassAI.debug) Log.info("[MassAI] core initialized at ({0},{1})", core.x, core.y);
                initializedCores.add(core);
            }
        }

        for(int i = activeLines.size - 1; i >= 0; i--){
            activeLines.get(i).update();
        }
    }

    static void startBuilding(Tile core){
        for(int i = 0; i < 4; i++){
            activeLines.add(new BuildingLine(core, i));
        }
    }

    static void markLineTile(Tile t){
        if(t != null) lineOccupied.add(t);
    }

    private static void unmarkLine(BuildingLine line){
        lineOccupied.remove(world.tile(line.startX, line.startY));
        for(PathTile pt : line.tiles){
            lineOccupied.remove(pt.tile);
        }
    }

    /**True when the footprint is empty, placeable and free of any lines.*/
    static boolean isAreaClear(Tile center, int size){
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile t = world.tile(center.x + offset + dx, center.y + offset + dy);
                if(t == null || t.block() != Blocks.air || t.floor().isLiquid || !t.floor().placeableOn) return false;

                // check if there is no line or building, THIS TO PREVENT TURRETS SPAWNING ON LINES AND CUTTING THE FULL FLOW OF RESOURCES
                if(lineOccupied.contains(t)) return false;
            }
        }
        return true;
    }

    static Array<Tile> findPath(Tile start, Tile target){
        Queue<Tile> queue = new Queue<>();
        ObjectMap<Tile, Tile> parents = new ObjectMap<>();
        queue.addLast(start);
        parents.put(start, null);

        while(!queue.isEmpty()){
            Tile curr = queue.removeFirst();
            if(curr.x == target.x && curr.y == target.y){
                Array<Tile> path = new Array<>();
                while(curr != start){
                    path.add(curr);
                    curr = parents.get(curr);
                }
                path.reverse();
                return path;
            }

            for(int i = 0; i < 4; i++){
                Tile next = curr.getNearby(Geometry.d4[i]);
                if(next != null && !parents.containsKey(next) && MassUtil.isPassable(next)){
                    parents.put(next, curr);
                    queue.addLast(next);
                    if(parents.size > MassAIConfig.BFS_MAX_NODES) return null;
                }
            }
        }
        return null;
    }

    static Array<Tile> findPathToAnyLine(Tile target){
        Queue<Tile> queue = new Queue<>();
        ObjectMap<Tile, Tile> parents = new ObjectMap<>();
        queue.addLast(target);
        parents.put(target, null);

        while(!queue.isEmpty()){
            Tile curr = queue.removeFirst();

            if(curr.block() == DistributionBlocks.veins && curr.getTeam() == Team.themass){
                boolean isLine = false;
                for(BuildingLine line : activeLines){
                    if(line.containsTile(curr)){
                        isLine = true;
                        break;
                    }
                }

                if(isLine){
                    Array<Tile> p = new Array<>();
                    Tile node = curr;
                    while(node != null){
                        p.add(node);
                        node = parents.get(node);
                    }
                    return p;
                }
            }

            for(int i = 0; i < 4; i++){
                Tile next = curr.getNearby(Geometry.d4[i]);
                if(next != null && !parents.containsKey(next) && MassUtil.isPassable(next)){
                    parents.put(next, curr);
                    queue.addLast(next);
                    if(parents.size > MassAIConfig.BFS_MAX_NODES) return null;
                }
            }
        }
        return null;
    }

    public static void reset(){
        initializedCores.clear();
        activeLines.clear();
        lineOccupied.clear();
    }
}