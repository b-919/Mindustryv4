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
    //vein tiles (or reserved air) where resources enter the network: line starts and drill/generator mouths
    private static final ObjectSet<Tile> veinEntrances = new ObjectSet<>();
    private static final Array<Tile> replacedVeinScratch = new Array<>();
    private static final Array<Tile> repairSequenceScratch = new Array<>();
    //reused BFS scratch; the mass logic runs on a single thread and never nests these calls
    private static final Queue<Tile> bfsQueue = new Queue<>();
    private static final ObjectMap<Tile, Tile> bfsParents = new ObjectMap<>();

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
            BuildingLine line = activeLines.get(i);
            line.update();
            //dead lines stop being growers; retire them but keep the start tile as a vein anchor
            if(line.dead){
                unmarkLine(line, true);
                activeLines.removeIndex(i);
            }
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

    /**Drops a reserved tile unless it is still a vein owned by the mass (possibly by another line).*/
    static void unmarkLineTile(Tile t){
        if(t == null) return;
        if(t.block() == DistributionBlocks.veins && t.getTeam() == Team.themass) return;
        lineOccupied.remove(t);
    }

    /**Reserves a vein mouth (line start or drill/generator neighbour) so turrets never cover it.*/
    static void markVeinEntrance(Tile tile){
        if(tile != null) veinEntrances.add(tile);
    }

    static boolean isVeinEntrance(Tile tile){
        return tile != null && veinEntrances.contains(tile);
    }

    /**Mass veins that a block footprint is about to overwrite.*/
    static Array<Tile> collectVeins(Tile origin, int size){
        replacedVeinScratch.clear();
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile t = world.tile(origin.x + offset + dx, origin.y + offset + dy);
                if(t != null && t.getTeam() == Team.themass && t.block() == DistributionBlocks.veins){
                    replacedVeinScratch.add(t);
                }
            }
        }
        return replacedVeinScratch;
    }

    /**Reroutes lines and subsections that lost vein tiles to a newly placed block.*/
    static void repairVeins(Array<Tile> replaced){
        if(replaced == null || replaced.size == 0) return;

        //these tiles are no longer veins, even if they came from a subsection path
        for(Tile t : replaced){
            lineOccupied.remove(t);
        }

        for(int i = 0; i < activeLines.size; i++){
            repairLine(activeLines.get(i), replaced);
        }

        for(int i = MassEconomy.activeSubsections.size - 1; i >= 0; i--){
            SubSection sub = MassEconomy.activeSubsections.get(i);
            if(sub == null || sub.failed || sub.destroying) continue;

            boolean hit = false;
            for(Tile t : sub.path){
                if(replaced.contains(t, true)){
                    hit = true;
                    break;
                }
            }
            if(!hit) continue;

            if(sub.tryReconnect()) continue;
            sub.destroying = true;
            if(sub.targetOre != null) MassEconomy.boostOre(sub.targetOre);
        }
    }

    private static void repairLine(BuildingLine line, Array<Tile> replaced){
        int guard = line.tiles.size + 4;
        while(guard-- > 0){
            int start = -1, end = -1;
            for(int i = 0; i < line.tiles.size; i++){
                if(replaced.contains(line.tiles.get(i).tile, true)){
                    if(start == -1) start = i;
                    end = i;
                }else if(start != -1){
                    break;
                }
            }
            if(start == -1) return;

            Tile prev = start > 0 ? line.tiles.get(start - 1).tile : world.tile(line.startX, line.startY);
            Tile next = end + 1 < line.tiles.size ? line.tiles.get(end + 1).tile : null;

            for(int i = start; i <= end; i++){
                lineOccupied.remove(line.tiles.get(i).tile);
            }

            if(prev == null || next == null || prev.block() != DistributionBlocks.veins){
                truncateLine(line, start);
                return;
            }

            Array<Tile> bypass = findRepairPath(prev, next);
            if(bypass == null){
                //cannot go around the new block: keep the upstream half, drop the orphaned downstream half
                truncateLine(line, start);
                return;
            }

            Array<Tile> seq = repairSequenceScratch;
            seq.clear();
            for(int i = 0; i < start; i++) seq.add(line.tiles.get(i).tile);
            for(int i = 0; i < bypass.size; i++){
                Tile t = bypass.get(i);
                if(t == next) break;
                seq.add(t);
            }
            for(int i = end + 1; i < line.tiles.size; i++) seq.add(line.tiles.get(i).tile);

            Array<PathTile> updated = new Array<>();
            Tile previous = null;
            for(int i = 0; i < seq.size; i++){
                Tile tile = seq.get(i);
                int rot;
                if(previous == null){
                    rot = line.tiles.get(0).rotation;
                }else{
                    rot = tile.relativeTo(previous.x, previous.y);
                    if(tile.block() != DistributionBlocks.veins){
                        tile.setBlock(DistributionBlocks.veins, Team.themass, rot);
                        markLineTile(tile);
                    }else{
                        tile.setRotation((byte) rot);
                    }
                }
                updated.add(new PathTile(tile, rot));
                previous = tile;
            }

            line.tiles = updated;
            if(line.targetLength < updated.size) line.targetLength = updated.size;
            //indices in these maps refer to the old tile list; drop them after a repair
            line.attempts.clear();
            line.gaveUp.clear();
        }
    }

    private static void truncateLine(BuildingLine line, int from){
        for(int i = line.tiles.size - 1; i >= from; i--){
            lineOccupied.remove(line.tiles.get(i).tile);
            line.tiles.removeIndex(i);
        }
        line.targetLength = Math.min(line.targetLength, line.tiles.size);
        line.attempts.clear();
        line.gaveUp.clear();
    }

    /**BFS through air only so a repair never steals or rotates another line's veins.*/
    private static Array<Tile> findRepairPath(Tile start, Tile target){
        bfsQueue.clear();
        bfsParents.clear();
        bfsQueue.addLast(start);
        bfsParents.put(start, null);

        while(!bfsQueue.isEmpty()){
            Tile curr = bfsQueue.removeFirst();
            if(curr.x == target.x && curr.y == target.y){
                Array<Tile> path = new Array<>();
                Tile node = curr;
                while(node != start){
                    path.add(node);
                    node = bfsParents.get(node);
                }
                path.reverse();
                return path;
            }

            for(int i = 0; i < 4; i++){
                Tile next = curr.getNearby(Geometry.d4[i]);
                if(next == null || bfsParents.containsKey(next)) continue;

                boolean endpoint = next.x == target.x && next.y == target.y;
                if(!endpoint && (next.block() != Blocks.air || MassUtil.isNearEnemyCore(next))) continue;

                bfsParents.put(next, curr);
                bfsQueue.addLast(next);
                if(bfsParents.size > MassAIConfig.BFS_MAX_NODES) return null;
            }
        }
        return null;
    }

    static boolean isMassVein(Tile tile){
        return tile != null && lineOccupied.contains(tile);
    }

    private static void unmarkLine(BuildingLine line){
        unmarkLine(line, false);
    }

    private static void unmarkLine(BuildingLine line, boolean keepStart){
        if(!keepStart){
            Tile start = world.tile(line.startX, line.startY);
            lineOccupied.remove(start);
            veinEntrances.remove(start);
        }
        for(PathTile pt : line.tiles){
            lineOccupied.remove(pt.tile);
        }
    }

    /**True when the footprint is placeable and air, except for mass veins that the turret may sit on.*/
    static boolean isTurretClear(Tile center, int size){
        int offset = -(size - 1) / 2;
        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile t = world.tile(center.x + offset + dx, center.y + offset + dy);
                if(t == null || t.floor().isLiquid || !t.floor().placeableOn) return false;
                //never cover the mouths where resources enter the vein network
                if(isVeinEntrance(t)) return false;
                if(t.getTeam() == Team.themass && t.block() == DistributionBlocks.veins) continue;
                if(t.block() != Blocks.air) return false;
            }
        }
        return true;
    }

    static Array<Tile> findPath(Tile start, Tile target){
        bfsQueue.clear();
        bfsParents.clear();
        bfsQueue.addLast(start);
        bfsParents.put(start, null);

        while(!bfsQueue.isEmpty()){
            Tile curr = bfsQueue.removeFirst();
            if(curr.x == target.x && curr.y == target.y){
                Array<Tile> path = new Array<>();
                while(curr != start){
                    path.add(curr);
                    curr = bfsParents.get(curr);
                }
                path.reverse();
                return path;
            }

            for(int i = 0; i < 4; i++){
                Tile next = curr.getNearby(Geometry.d4[i]);
                if(next != null && !bfsParents.containsKey(next) && MassUtil.isPassable(next)){
                    bfsParents.put(next, curr);
                    bfsQueue.addLast(next);
                    if(bfsParents.size > MassAIConfig.BFS_MAX_NODES) return null;
                }
            }
        }
        return null;
    }

    static Array<Tile> findPathToAnyLine(Tile target){
        bfsQueue.clear();
        bfsParents.clear();
        bfsQueue.addLast(target);
        bfsParents.put(target, null);

        while(!bfsQueue.isEmpty()){
            Tile curr = bfsQueue.removeFirst();

            if(curr.block() == DistributionBlocks.veins && curr.getTeam() == Team.themass && lineOccupied.contains(curr)){
                Array<Tile> p = new Array<>();
                Tile node = curr;
                while(node != null){
                    p.add(node);
                    node = bfsParents.get(node);
                }
                return p;
            }

            for(int i = 0; i < 4; i++){
                Tile next = curr.getNearby(Geometry.d4[i]);
                if(next != null && !bfsParents.containsKey(next) && MassUtil.isPassable(next)){
                    bfsParents.put(next, curr);
                    bfsQueue.addLast(next);
                    if(bfsParents.size > MassAIConfig.BFS_MAX_NODES) return null;
                }
            }
        }
        return null;
    }

    /**BFS across mass veins only; true when a tile reserved by a line is reachable from {@code target}.*/
    static boolean isVeinConnected(Tile target){
        bfsQueue.clear();
        bfsParents.clear();
        bfsQueue.addLast(target);
        bfsParents.put(target, null);

        while(!bfsQueue.isEmpty()){
            Tile curr = bfsQueue.removeFirst();

            if(curr != target && curr.block() == DistributionBlocks.veins
                && curr.getTeam() == Team.themass && lineOccupied.contains(curr)) return true;

            for(int i = 0; i < 4; i++){
                Tile next = curr.getNearby(Geometry.d4[i]);
                if(next == null || bfsParents.containsKey(next)) continue;
                if(next.block() != DistributionBlocks.veins || next.getTeam() != Team.themass) continue;
                bfsParents.put(next, curr);
                bfsQueue.addLast(next);
            }
        }
        return false;
    }

    public static void reset(){
        initializedCores.clear();
        activeLines.clear();
        lineOccupied.clear();
        veinEntrances.clear();
        replacedVeinScratch.clear();
        repairSequenceScratch.clear();
    }
}