package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.Team;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.unitGroups;

/** Lightweight battlefield intelligence for the mass.*/
public class MassIntel{
    private static float airRatio = MassAIConfig.PERIODIC_TURRET_AIR_CHANCE;
    private static int enemyCount;
    private static boolean hasComposition;

    private static float threatX, threatY, threatHeat;

    private static float timer;

    private MassIntel(){}

    public static void reset(){
        airRatio = MassAIConfig.PERIODIC_TURRET_AIR_CHANCE;
        enemyCount = 0;
        hasComposition = false;
        threatX = 0f;
        threatY = 0f;
        threatHeat = 0f;
        timer = 0f;
    }

    public static void update(){
        timer -= Timers.delta();
        if(timer <= 0f){
            timer = MassAIConfig.INTEL_INTERVAL;
            scanComposition();
        }
        if(threatHeat > 0f){
            threatHeat = Math.max(0f, threatHeat - Timers.delta() / MassAIConfig.THREAT_DECAY_TIME);
        }
    }

    private static void scanComposition(){
        int air = 0, total = 0, scanned = 0;
        outer:
        for(Team team : Team.all){
            if(team == Team.themass || team == Team.none) continue;
            for(BaseUnit unit : unitGroups[team.ordinal()].all()){
                if(unit == null || !unit.isAdded() || unit.isDead()) continue;
                if(scanned++ >= MassAIConfig.INTEL_SCAN_MAX) break outer;
                UnitType type = unit.getType();
                if(type == null) continue;
                if(type.isFlying) air++;
                total++;
            }
        }
        enemyCount = total;
        hasComposition = total > 0;
        if(total > 0){
            airRatio = (float) air / total;
        }
    }

    /**Records a hit for the threat map (world coordinates).*/
    public static void recordDamage(float worldx, float worldy){
        threatX = worldx;
        threatY = worldy;
        threatHeat = Math.min(1f, threatHeat + MassAIConfig.THREAT_HEAT_PER_HIT);
    }

    /**Fraction of scanned enemy units that are airborne (0..1). Falls back to the configured default.*/
    public static float enemyAirRatio(){
        return hasComposition ? airRatio : MassAIConfig.PERIODIC_TURRET_AIR_CHANCE;
    }

    /**True when a recent hotspot is hot enough to bias defense toward it.*/
    public static boolean hotspot(float[] out){
        if(threatHeat < MassAIConfig.THREAT_HOTSPOT_MIN) return false;
        out[0] = threatX;
        out[1] = threatY;
        return true;
    }

    public static int enemyCount(){
        return enemyCount;
    }

    public static float threatHeat(){
        return threatHeat;
    }
    //public static boolean hasComposition(){
     //   return hasComposition;
    //}
}
