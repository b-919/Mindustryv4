package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.world;

/** Infection countdown, initial hive spawning and the grace period. */
public class MassInfection{
    static float nextInfectionTime = 0;
    //global mission timer, drives the grace period; raised to skip grace when the base is damaged
    static float commandTimer = 0;
    static boolean disableGrace = false;

    /**Ticks the infection countdown; when it reaches zero an initial hive is spawned. Returns the (possibly refreshed) core set.*/
    public static ObjectSet<Tile> update(ObjectSet<Tile> cores){
        if(Vars.state.allowMassInfection && cores.size == 0){
            nextInfectionTime -= Timers.delta();
            //System.out.println("Timer time is " + nextInfectionTime);
            if(nextInfectionTime <= 0){
                nextInfectionTime = Mathf.random(5f, 40f) * 60f * 60f;
                spawnInitialHive();
                if(MassAI.debug) Log.info("[MassAI] infection timer reached zero, spawning initial hive");
                cores = Vars.state.teams.get(Team.themass).cores;
            }
        }
        return cores;
    }

    public static void spawnInitialHive(){
        if(Vars.state.teams.get(Team.themass).cores.size > 0) return;

        Tile playerCore = null;
        for(Player player : Vars.players){
            if(player != null && player.getClosestCore() != null){
                playerCore = player.getClosestCore().tile;
                break;
            }
        }
        if(playerCore == null) return;

        Tile spawn = null;
        float maxDist = 0;

        for(int i = 0; i < MassAIConfig.INITIAL_HIVE_ATTEMPTS; i++){
            int x = Mathf.random(world.width() - 1);
            int y = Mathf.random(world.height() - 1);
            Tile tile = world.tile(x, y);

            if(tile != null && tile.block() == Blocks.air && !tile.floor().solid && !MassUtil.isNearEnemyCore(tile)){
                float dist = Mathf.dst(x - playerCore.x, y - playerCore.y);
                if(dist > maxDist){
                    maxDist = dist;
                    spawn = tile;
                }
            }
        }

        if(spawn != null){
            world.setBlock(spawn, StorageBlocks.hive, Team.themass);
            if(MassAI.debug) Log.info("[MassAI] initial hive spawned at ({0},{1})", spawn.x, spawn.y);
            if(!Vars.headless){
                String msg = Mathf.chance(0.01) ? Bundles.get("text.biomass.alert.rare") : Bundles.get("text.biomass.alert");
                Vars.ui.hudfrag.showBiomassAlert(msg);
            }
        }
    }

    /**Raises the grace timer so the grace period is skipped after damage.*/
    public static void onDamage(){
        float grace = getGraceTime();
        if(commandTimer < grace){
            commandTimer = grace;
            if(MassAI.debug) Log.info("[MassAI] onDamage: grace skipped, commandTimer set to {0}", commandTimer);
        }
    }

    public static float getGraceTime(){
        if(Vars.state == null || Vars.state.difficulty == null) return 5f * 60f * 60f;
        return MassAIConfig.graceMinutes(Vars.state.difficulty) * 60f * 60f;
    }

    public static boolean isGracePeriod(){
        if(disableGrace) return false;
        return commandTimer < getGraceTime();
    }

    public static float getInfectionTime(){
        return nextInfectionTime;
    }

    public static void setInfectionTime(float time){
        nextInfectionTime = time;
    }

    public static void reset(){
        commandTimer = 0;
        disableGrace = false;
        if(nextInfectionTime <= 0){ // only initialize if not already set by loading
            nextInfectionTime = Mathf.random(5f, 40f) * 60f * 60f;
        }
    }
}