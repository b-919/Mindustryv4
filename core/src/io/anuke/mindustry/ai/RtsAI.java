package io.anuke.mindustry.ai;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams.TeamData;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemType;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BuildBlock;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.ucore.util.Timer;


import static io.anuke.mindustry.Vars.*;

public class RtsAI{
    private final TeamData data;
    private final Team team;
    private final Timer timer = new Timer(6);

    private static final int timerSquad = 0;
    private static final int timerSpawn = 1;
    private static final int timerDefend = 2;
    private static final int timerCluster = 3;
    private static final int timerRepath = 4;
    private static final int timerFill = 5;

    private static final int squadInterval = 60 * 4;
    private static final int spawnInterval = 60 * 8;
    private static final int defendInterval = 60 * 2;
    private static final int clusterInterval = 60 * 2;
    private static final int repathInterval = 60 * 10;
    private static final int fillInterval = 60 * 5;

    public RtsAI(TeamData data){
        this.data = data;
        this.team = data.team;
    }

    public void update(){
        if(data.cores.size == 0) return;

        if(timer.get(timerFill, fillInterval)){
            fillCores();
        }

        if(timer.get(timerSpawn, spawnInterval)){
            trySpawnCoreUnits();
        }

        if(timer.get(timerDefend, defendInterval)){
            checkDefense();
        }

        if(timer.get(timerCluster, clusterInterval)){
            reformClusters();
        }

        if(timer.get(timerSquad, squadInterval)){
            assignAttackSquads();
        }

        if(timer.get(timerRepath, repathInterval)){
            reassignIdleUnits();
        }
    }

    private void fillCores(){
        for(Tile core : data.cores){
            if(core.entity == null) continue;
            ItemModule items = core.entity.items;
            for(Item item : content.items()){
                if(item.type == ItemType.material || item.type == ItemType.resource){
                    int cap = core.block().itemCapacity;
                    items.set(item, Math.min(items.get(item) + 100, cap));
                }
            }
        }
    }

    private void assignAttackSquads(){
        Array<Tile> targets = findEnemyTargets();
        if(targets.size == 0) return;

        Tile bestTarget = null;
        float bestScore = Float.MAX_VALUE;
        Tile coreTile = data.cores.first();
        float coreX = coreTile.drawx(), coreY = coreTile.drawy();

        for(Tile target : targets){
            if(target.block() instanceof BuildBlock) continue;
            float d = target.drawx() - coreX;
            float d2 = target.drawy() - coreY;
            float score = d * d + d2 * d2;

            if(target.block().flags != null && target.block().flags.contains(BlockFlag.target)){
                score *= 0.8f;
            }
            if(target.block().flags != null && target.block().flags.contains(BlockFlag.turret)){
                score *= 0.9f;
            }

            if(score < bestScore){
                bestScore = score;
                bestTarget = target;
            }
        }

        if(bestTarget == null) return;

        float targetX = bestTarget.drawx(), targetY = bestTarget.drawy();

        int assigned = 0;
        for(BaseUnit unit : unitGroups[team.ordinal()].all()){
            if(unit.isDead() || !unit.isAdded()) continue;
            if(!unit.isRTSAIControllable) continue;
            if(unit.hasOrder()) continue;

            float dx = unit.x - coreX, dy = unit.y - coreY;
            float dist = dx * dx + dy * dy;

            if(dist < 800 * 800 && assigned < 30){
                unit.orderAttackMove(targetX, targetY);
                assigned++;
            }
        }
    }

    private void checkDefense(){
        ObjectSet<Tile> damagedSet = world.indexer.getDamaged(team);
        if(damagedSet.size == 0) return;

        Tile mostDamaged = null;
        float minHealth = Float.MAX_VALUE;

        for(Tile tile : damagedSet){
            if(tile.entity == null || tile.block() instanceof BuildBlock) continue;
            float hp = tile.entity.health / (float)tile.block().health;
            if(hp < minHealth){
                minHealth = hp;
                mostDamaged = tile;
            }
        }

        if(mostDamaged == null) return;

        float dx = mostDamaged.drawx(), dy = mostDamaged.drawy();

        for(BaseUnit unit : unitGroups[team.ordinal()].all()){
            if(unit.isDead() || !unit.isAdded()) continue;
            if(!unit.isRTSAIControllable) continue;
            if(unit.hasOrder()) continue;

            float ux = unit.x - dx, uy = unit.y - dy;
            if(ux * ux + uy * uy < 500 * 500){
                unit.orderAttackMove(dx, dy);
            }
        }
    }

    private void trySpawnCoreUnits(){
        for(Tile core : data.cores){
            if(core.entity == null) continue;
            if(world.indexer.getAllied(team, BlockFlag.producer).size == 0) continue;

            int unitCount = 0;
            for(BaseUnit u : unitGroups[team.ordinal()].all()){
                if(!u.isDead() && u.isRTSAIControllable) unitCount++;
            }

            if(unitCount > 20) return;
            return;
        }
    }

    private void reassignIdleUnits(){
        Tile coreTile = data.cores.first();
        float coreX = coreTile.drawx(), coreY = coreTile.drawy();
        Array<Tile> targets = findEnemyTargets();
        if(targets.size == 0) return;

        Tile target = targets.first();
        float tx = target.drawx(), ty = target.drawy();

        for(BaseUnit unit : unitGroups[team.ordinal()].all()){
            if(unit.isDead() || !unit.isAdded()) continue;
            if(!unit.isRTSAIControllable) continue;
            if(unit.hasOrder()) continue;

            float dx = unit.x - coreX, dy = unit.y - coreY;
            if(dx * dx + dy * dy < 400 * 400){
                unit.orderAttackMove(tx, ty);
            }
        }
    }

    private void reformClusters(){
        Tile coreTile = data.cores.first();
        float coreX = coreTile.drawx(), coreY = coreTile.drawy();

        int idleCount = 0;
        for(BaseUnit unit : unitGroups[team.ordinal()].all()){
            if(unit.isDead() || !unit.isAdded()) continue;
            if(!unit.isRTSAIControllable) continue;
            if(!unit.hasOrder()) idleCount++;
        }

        if(idleCount < 5) return;

        Array<Tile> targets = findEnemyTargets();
        if(targets.size == 0) return;

        Tile target = targets.random();
        float tx = target.drawx(), ty = target.drawy();

        int sent = 0;
        for(BaseUnit unit : unitGroups[team.ordinal()].all()){
            if(unit.isDead() || !unit.isAdded()) continue;
            if(!unit.isRTSAIControllable) continue;
            if(unit.hasOrder()) continue;
            if(sent >= idleCount / 2) break;

            float dx = unit.x - coreX, dy = unit.y - coreY;
            if(dx * dx + dy * dy < 600 * 600){
                unit.orderAttackMove(tx, ty);
                sent++;
            }
        }
    }

    private Array<Tile> findEnemyTargets(){
        Array<Tile> result = new Array<>();
        for(Team enemy : state.teams.enemiesOf(team)){
            if(!state.teams.isActive(enemy)) continue;
            for(Tile core : state.teams.get(enemy).cores){
                result.add(core);
                int cx = core.x, cy = core.y;
                for(int dx = -8; dx <= 8; dx++){
                    for(int dy = -8; dy <= 8; dy++){
                        Tile t = world.tile(cx + dx, cy + dy);
                        if(t != null && t.block().flags != null &&
                            (t.block().flags.contains(BlockFlag.producer) || t.block().flags.contains(BlockFlag.target))){
                            result.add(t);
                        }
                    }
                }
            }
        }
        return result;
    }
}
