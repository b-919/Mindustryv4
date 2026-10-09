package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.world;

/** Shared helpers for the Mass AI: proximity checks, small scans and cached enemy core positions. */
public final class MassUtil{
    private MassUtil(){}

    //enemy core positions, refreshed at most once per game tick
    private static final Array<Tile> enemyCores = new Array<>();
    private static float enemyCoreRadius;
    private static float enemyCoresTime = -1f;

    private static final Rectangle enemyScanRect = new Rectangle();
    /**Result of the last enemy scan; kept for save compatibility with the old shared flag.*/
    static boolean enemyNearby;

    public static void invalidate(){
        enemyCores.clear();
        enemyCoresTime = -1f;
        enemyNearby = false;
    }

    private static void refreshEnemyCores(){
        float time = Timers.time();
        if(enemyCoresTime == time) return;
        enemyCores.clear();
        enemyCoreRadius = Vars.state.mode.enemyCoreBuildRadius / Vars.tilesize;
        for(Team team : Team.all){
            if(team == Team.themass || team == Team.none) continue;
            for(Tile core : Vars.state.teams.get(team).cores){
                enemyCores.add(core);
            }
        }
        enemyCoresTime = time;
    }

    /**Access to the cached enemy core list (refreshed at most once per tick).*/
    static Array<Tile> getEnemyCores(){
        refreshEnemyCores();
        return enemyCores;
    }

    static boolean isNearEnemyCore(Tile tile){
        if(Vars.state == null || Vars.state.teams == null) return false;
        refreshEnemyCores();
        for(int i = 0; i < enemyCores.size; i++){
            Tile core = enemyCores.get(i);
            if(Mathf.dst(tile.x - core.x, tile.y - core.y) < enemyCoreRadius) return true;
        }
        return false;
    }

    /**Scans the square around each core once; result is kept in {@link #enemyNearby}.*/
    static boolean anyEnemyNearCores(ObjectSet<Tile> cores){
        for(Tile core : cores){
            enemyScanRect.setSize(MassAIConfig.ENEMY_SCAN_SIZE).setCenter(core.worldx(), core.worldy());
            enemyNearby = false;
            Units.getNearbyEnemies(Team.themass, enemyScanRect, u -> enemyNearby = true);
            if(enemyNearby) return true;
        }
        return false;
    }

    static boolean isNearMassCore(Tile tile, float radius){
        if(Vars.state.teams == null) return false;
        for(Tile core : Vars.state.teams.get(Team.themass).cores){
            if(Mathf.dst(tile.x - core.x, tile.y - core.y) < radius) return true;
        }
        return false;
    }

    static boolean isNearEdge(Tile tile, int distance){
        return tile.x < distance || tile.y < distance || tile.x > world.width() - distance || tile.y > world.height() - distance;
    }

    static Tile findClosestCore(Tile tile, Team team){
        Tile closest = null;
        float minDst = Float.MAX_VALUE;
        for(Tile core : Vars.state.teams.get(team).cores){
            float dst = Mathf.dst(tile.x - core.x, tile.y - core.y);
            if(dst < minDst){
                minDst = dst;
                closest = core;
            }
        }
        return closest;
    }

    /**Nearest core of any team other than {@code from} and {@code Team.none}.*/
    static Tile nearestEnemyCore(Tile tile, Team from){
        Tile enemyCore = null;
        float minDst = Float.MAX_VALUE;
        for(Team team : Team.all){
            if(team == from || team == Team.none) continue;
            for(Tile core : Vars.state.teams.get(team).cores){
                float dst = Mathf.dst(tile.x - core.x, tile.y - core.y);
                if(dst < minDst){
                    minDst = dst;
                    enemyCore = core;
                }
            }
        }
        return enemyCore;
    }

    static int countNearbyCores(Tile near){
        int count = 0;
        float radius = MassAIConfig.CORE_PROXIMITY_TILES;
        for(Tile core : Vars.state.teams.get(Team.themass).cores){
            if(Mathf.dst(core.x - near.x, core.y - near.y) <= radius){
                count++;
            }
        }
        return count;
    }

    static boolean hasOresNearby(Tile tile, int radius){
        for(Item item : MassAIConfig.TARGET_ORES){
            Tile ore = Vars.world.indexer.findClosestOre(tile.worldx(), tile.worldy(), item);
            if(ore != null && Mathf.dst(tile.x - ore.x, tile.y - ore.y) < radius) return true;
        }
        return false;
    }

    static boolean isPassable(Tile tile){
        return (tile.block() == Blocks.air || (tile.getTeam() == Team.themass && tile.block() == DistributionBlocks.veins)) && !isNearEnemyCore(tile);
    }

    static boolean isValid2x2(int x, int y, Item item){
        for(int dx = 0; dx < 2; dx++){
            for(int dy = 0; dy < 2; dy++){
                Tile t = world.tile(x + dx, y + dy);
                if(t == null || t.floor().drops == null || t.floor().drops.item != item || isNearEnemyCore(t)) return false;

                Block block = t.block();
                if(block != Blocks.air && !(t.getTeam() == Team.themass && block == DistributionBlocks.veins)){
                    return false;
                }
            }
        }
        return true;
    }
}
