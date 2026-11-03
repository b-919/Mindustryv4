package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.Team;
import io.anuke.ucore.core.Timers;

import static io.anuke.mindustry.Vars.unitGroups;

/** Lightweight battlefield intelligence for the mass.*/
public class MassIntel{
    private static float airRatio = MassAIConfig.periodicTurretAirChance;
    private static int enemyCount;

    private static float threatX, threatY, threatHeat;

    private static float timer;

    private MassIntel(){}

    public static void reset(){
        airRatio = MassAIConfig.periodicTurretAirChance;
        enemyCount = 0;
        threatX = 0f;
        threatY = 0f;
        threatHeat = 0f;
        timer = 0f;
    }

    public static void update(){
        timer -= Timers.delta();
        if(timer <= 0f){
            timer = MassAIConfig.intelInterval;
            scanComposition();
        }
        if(threatHeat > 0f){
            threatHeat = Math.max(0f, threatHeat - Timers.delta() / MassAIConfig.threatDecayTime);
        }
    }

    private static void scanComposition(){
        int air = 0, total = 0, scanned = 0;
        outer:
        for(Team team : Team.all){
            if(team == Team.themass || team == Team.none) continue;
            for(BaseUnit unit : unitGroups[team.ordinal()].all()){
                if(unit == null || !unit.isAdded() || unit.isDead()) continue;
                if(scanned++ >= MassAIConfig.intelScanMax) break outer;
                UnitType type = unit.getType();
                if(type == null) continue;
                if(type.isFlying) air++;
                total++;
            }
        }
        enemyCount = total;
        airRatio = total > 0 ? (float) air / total : MassAIConfig.periodicTurretAirChance;
    }

    /**Records a hit for the threat map (world coordinates).*/
    public static void recordDamage(float worldx, float worldy){
        threatX = worldx;
        threatY = worldy;
        threatHeat = Math.min(1f, threatHeat + MassAIConfig.threatHeatPerHit);
    }

    /**Fraction of scanned enemy units that are airborne (0..1).*/
    public static float enemyAirRatio(){
        return airRatio;
    }

    /**True when a recent hotspot is hot enough to bias defense toward it.*/
    public static boolean hotspot(float[] out){
        if(threatHeat < MassAIConfig.threatHotspotMin) return false;
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
}
