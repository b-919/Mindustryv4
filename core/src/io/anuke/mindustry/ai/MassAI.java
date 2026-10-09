package io.anuke.mindustry.ai;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.mass.MassBuilder;
import io.anuke.mindustry.ai.mass.MassDefense;
import io.anuke.mindustry.ai.mass.MassEconomy;
import io.anuke.mindustry.ai.mass.MassInfection;
import io.anuke.mindustry.ai.mass.MassSave;
import io.anuke.mindustry.ai.mass.MassSquads;
import io.anuke.mindustry.ai.mass.MassUtil;
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

    static{
        Events.on(WorldLoadEvent.class, event -> {
            MassInfection.reset();
            MassBuilder.reset();
            MassEconomy.reset();
            MassDefense.reset();
            MassSquads.reset();
            MassUtil.invalidate();
        });
    }

    public static void write(DataOutputStream stream) throws IOException{
        MassSave.write(stream);
    }

    public static void read(DataInputStream stream) throws IOException{
        MassSave.read(stream);
    }

    public static void update(){
        if(Net.client()) return;
        if(Vars.state.isPaused() || Vars.state.teams == null) return;
        if(Settings.getBool("massai-debug", false) != debug){
            setDebug(Settings.getBool("massai-debug", false));
        }

        ObjectSet<Tile> cores = Vars.state.teams.get(Team.themass).cores;

        cores = MassInfection.update(cores);
        MassBuilder.update(cores);
        MassDefense.update();
        MassEconomy.update(cores);
        MassSquads.update(cores);
    }

    public static void spawnInitialHive(){
        MassInfection.spawnInitialHive();
    }

    public static void trySpawnTurret(boolean fromDamage, boolean targetAir, float targetX, float targetY){
        MassDefense.trySpawnTurret(fromDamage, targetAir, targetX, targetY);
    }

    public static void onDamage(float x, float y, Entity attacker){
        MassInfection.onDamage();
        MassSquads.notifyAttack(x, y, attacker);
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