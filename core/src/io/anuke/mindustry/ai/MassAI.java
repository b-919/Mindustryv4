package io.anuke.mindustry.ai;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.mass.*;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.game.EventType.WorldLoadEvent;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.trait.Entity;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Mathf;
import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.net.Net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.unitGroups;

//welcome to the hell, this is the entrance where all the shit passes good luck figuring out this class
public class MassAI{
    public static boolean debug = true;
    /**True when the mass has no cores and its runtime state has already been dropped.*/
    private static boolean massIdle = true;

    static{
        Events.on(WorldLoadEvent.class, event -> {
            MassInfection.reset();
            resetRuntime();
            massIdle = true;
        });
    }

    public static void write(DataOutputStream stream) throws IOException{
        MassSave.write(stream);
    }

    /**Reads the mass state; {@code format} selects the layout (see {@link MassSave}).*/
    public static void read(DataInputStream stream, int format) throws IOException{
        MassSave.read(stream, format);
        //let the next update validate the loaded state and drop it if the save has no hive left
        massIdle = false;
    }

    public static void update(){
        if(Net.client()) return;
        if(Vars.state.isPaused() || Vars.state.teams == null) return;
        if(Settings.getBool("massai-debug", false) != debug){
            setDebug(Settings.getBool("massai-debug", false));
        }

        ObjectSet<Tile> cores = Vars.state.teams.get(Team.themass).cores;

        //the infection countdown is the only mass system allowed to run while there is no hive
        cores = MassInfection.update(cores);

        if(cores.size == 0){
            if(!massIdle){
                massIdle = true;
                resetRuntime();
            }
            return;
        }
        massIdle = false;

        MassAdrenaline.update(cores);
        MassIntel.update();
        MassBuilder.update(cores);
        MassDefense.update();
        MassEconomy.update(cores);
        MassSquads.update(cores);
    }

    /**Drops all live Mass subsystem state without touching the infection countdown.*/
    private static void resetRuntime(){
        MassBuilder.reset();
        MassEconomy.reset();
        MassDefense.reset();
        MassAdrenaline.reset();
        MassIntel.reset();
        MassSquads.reset();
        MassUtil.invalidate();
    }

    public static void spawnInitialHive(){
        MassInfection.spawnInitialHive();
    }

    public static void trySpawnTurret(boolean fromDamage, boolean targetAir, float targetX, float targetY){
        MassDefense.trySpawnTurret(fromDamage, targetAir, targetX, targetY);
    }

    public static void onDamage(float x, float y, Entity attacker){
        MassInfection.onDamage();
        MassIntel.recordDamage(x, y);
        MassSquads.notifyAttack(x, y, attacker);
    }

    /**Called when a mass unit (not a tile) takes damage; lets patrol squads retaliate.*/
    public static void onUnitDamage(Unit unit, Entity attacker){
        MassSquads.notifyUnitDamaged(unit, attacker);
    }

    public static float getGraceTime(){
        return MassInfection.getGraceTime();
    }

    public static boolean isGracePeriod(){
        return MassInfection.isGracePeriod();
    }

    public static float getInfectionTime(){
        return MassInfection.getInfectionTime();
    }

    public static void setInfectionTime(float time){
        MassInfection.setInfectionTime(time);
    }

    public static float randomInfectionTime(){
        return MassInfection.randomInfectionTime();
    }

    public static void setDebug(boolean enabled){
        debug = enabled;
        Settings.putBool("massai-debug", enabled);
        Log.info("[MassAI] debug={0}", enabled);
    }

    public static void drawDebugOverlay(){
        if(!debug || Vars.headless || Vars.state == null) return;

        IntMap<Array<BaseUnit>> bySquad = new IntMap<>();
        for(BaseUnit unit : unitGroups[Team.themass.ordinal()].all()){
            if(unit == null || !unit.isAdded() || unit.isDead()) continue;
            int squadId = MassSquads.unitSquadAssignments.get(unit, -1);
            if(squadId < 0) continue;
            Array<BaseUnit> list = bySquad.get(squadId);
            if(list == null){
                list = new Array<>();
                bySquad.put(squadId, list);
            }
            list.add(unit);
        }

        Lines.stroke(1.2f);
        for(IntMap.Entry<Array<BaseUnit>> entry : bySquad.entries()){
            int squadId = entry.key;
            Array<BaseUnit> units = entry.value;
            if(units.size == 0) continue;

            float cx = 0f, cy = 0f;
            for(BaseUnit unit : units){
                cx += unit.x;
                cy += unit.y;
            }
            cx /= units.size;
            cy /= units.size;

            Draw.color(Color.valueOf("7efcff"));
            Lines.circle(cx, cy, 8f + Mathf.absin(Timers.time(), 6f, 2f));

            Draw.color(Color.valueOf("7efcff"));
            Lines.stroke(2f);
            Lines.line(cx - 4f, cy, cx + 4f, cy);
            Lines.line(cx, cy - 4f, cx, cy + 4f);
            Lines.stroke(1.2f);

            float[] target = {cx, cy};
            MassSquads.squadTarget(squadId, target);
            if(Mathf.dst(cx - target[0], cy - target[1]) > 1f){
                Draw.color(Color.valueOf("fff27e"));
                Lines.line(cx, cy, target[0], target[1]);
                Lines.circle(target[0], target[1], 3f);
            }

            for(BaseUnit unit : units){
                Lines.line(cx, cy, unit.x, unit.y);
                Draw.color(Color.WHITE);
                Draw.text("SQ " + squadId + " " + MassSquads.orderName(squadId), unit.x, unit.y + 11f);
                Draw.color(Color.valueOf("7efcff"));
            }
        }
        Draw.reset();
    }
}