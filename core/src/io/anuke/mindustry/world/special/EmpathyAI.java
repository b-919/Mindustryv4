package io.anuke.mindustry.world.special;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.mindustry.entities.units.ai.*;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.gen.Call;

class EmpathyAI{
    protected float time = 0f;
    protected float aiUsages = 0f;

    protected EmpathyUnit unit;
    protected boolean attack = false, quickSwap = false;
    protected Array<PassiveSoundLoop> sounds;

    protected float autoParryTime = 0f;
    static boolean parryFound = false;

    EmpathyAI set(EmpathyUnit u){
        unit = u;
        return this;
    }

    //used by other ais to reset attacks or movements
    void reset(){
        //
    }

    void init(){
    }

    void update(){
    }

    void updatePassive(){
    }
    @SuppressWarnings("unchecked")
    void updateAutoParry(){
        if(autoParryTime <= 0f){
            parryFound = false;
            Utils.scanCone((QuadTree<Bullet>)Groups.bullet.tree(), unit.x, unit.y, unit.rotation, 25f, 60f, b -> {
                if(b.team != unit.team){
                    parryFound = true;
                }
            });
            Utils.scanCone((QuadTree<Unit>)Groups.unit.tree(), unit.x, unit.y, unit.rotation, 25f, 60f, u -> {
                if(u.team != unit.team && (u instanceof TimedKillc || u.controller() instanceof MissileAI)){
                    parryFound = true;
                }
            });

            if(parryFound){
                autoParryTime = 40f;
                unit.parry();
            }
        }else{
            autoParryTime -= Timers.delta();
        }
    }

    boolean bulletHellOverride(){
        return true;
    }
    boolean teleSwapCompatible(){
        return false;
    }
    boolean canTeleport(){
        return true;
    }

    float effectiveDistance(){
        return 400f;
    }

    boolean shouldDraw(){
        return false;
    }
    boolean overrideDraw(){
        return false;
    }
    boolean canKnockback(){
        return true;
    }
    void draw(){
        //
    }

    boolean updateAttackAI(){
        return true;
    }
    boolean updateMovementAI(){
        return true;
    }
    boolean canTrail(){
        return true;
    }

    float weight(){
        return 10f;
    }

    void end(){
    }
    void endOnce(){}

    void updateSounds(){
        if(sounds != null){
            for(PassiveSoundLoop s : sounds){
                s.update();
            }
        }
    }
    int addSound(Sound sound){
        if(sounds == null) sounds = new Array<>();
        sounds.add(new PassiveSoundLoop(sound));
        return sounds.size - 1;
    }
    void addSoundConstant(Sound sound){
        if(sounds == null) sounds = new Array<>();
        sounds.add(new PassiveSoundLoop(sound, true, false));
    }
    void addSoundDoppler(Sound sound){
        if(sounds == null) sounds = new Array<>();
        sounds.add(new PassiveSoundLoop(sound, true, true));
    }

    void stopSounds(){
        if(sounds != null){
            for(PassiveSoundLoop s : sounds){
                s.stop();
            }
        }
    }

    Teamc getTarget(){
        return null;
    }
    void setTarget(Teamc target){

    }

    void retarget(){

    }
}
