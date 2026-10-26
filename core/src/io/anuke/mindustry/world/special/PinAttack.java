package io.anuke.mindustry.world.special;

import com.badlogic.gdx.math.Interpolation;
import io.anuke.ucore.util.Mathf;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.mindustry.entities.*;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.gen.Call;

public class PinAttack extends AttackAI{
    float reloadTime = 0f;
    float barrageTime = 0f, spawnTimer = 0f;
    float bulletHellTime = 120f;
    int barrages = 0;

    float targetX = 0f;
    float targetY = 0f;

    @Override
    void reset(){
        reloadTime = 0f;
        barrageTime = 0f;
        spawnTimer = 0f;
        bulletHellTime = 0f;
        barrages = 0;
    }

    @Override
    boolean teleSwapCompatible(){
        return true;
    }

    @Override
    void update(){
        Teamc target = unit.getTarget();
        if(target != null){
            targetX = target.getX();
            targetY = target.getY();

            if(unit.within(target, 620f)){
                if(reloadTime <= 0f){
                    reloadTime = Mathf.random(60f, 120f);
                    barrageTime = Mathf.random(15f, 60f);
                }
                if(bulletHellTime <= 0f){
                    bulletHellTime = 5f * 60f;
                    EmpathyBulletSpawner.create(unit, unit.x, unit.y, 50f, 2.5f * 60f, e -> {
                        if(e.datas[0] >= 5f){
                            for(int i = 0; i < 10; i++){
                                float ang = (360f / 10f) * i + MathUtils.sinDeg(e.datas[1]) * 90f;
                                FlameBullets.pin.create(unit, unit.team, e.x, e.y, ang);
                            }
                            Sounds.shootMissileLarge.at(e.x, e.y, 2f);
                            e.datas[1] += 10f;
                            e.datas[0] = 0f;
                        }
                        e.datas[0] += Timers.delta();
                    });
                }
                unit.rotate(2f, unit.angleTo(target), 15f);
            }
        }

        spawnTimer = Math.max(0f, spawnTimer - Timers.delta());
        if(barrageTime > 0){
            if(spawnTimer <= 0f){
                Vector2 v = Tmp.v1.trns(unit.rotation, -40f, Mathf.range(40f)).add(unit);

                float vx = 0f;
                float vy = 0f;
                if(target instanceof Hitboxc h){
                    vx = h.deltaX();
                    vy = h.deltaY();
                }

                Vector2 in = Tmp.v2.set(targetX, targetY);
                if(Mathf.chance(0.5f)) in = Predict.intercept(v.x, v.y, targetX, targetY, vx, vy, 10f);

                FlameBullets.pin.create(unit, unit.team, v.x, v.y, v.angleTo(in));
                Sounds.shootMissileLarge.at(v.x, v.y, 2f);

                spawnTimer = unit.isDecoy() ? 6f : 3f;
            }
            barrageTime -= Timers.delta();
            if(barrageTime <= 0f){
                barrages++;
                if(barrages >= 4 || quickSwap || unit.useLethal()){
                    barrages = 0;
                    //reloadTime = 0f;
                    unit.randAI(true, unit.health < 50f);
                }
            }
        }else{
            reloadTime = Math.max(0f, reloadTime - Timers.delta());
        }
    }

    @Override
    void updatePassive(){
        bulletHellTime -= Timers.delta();
    }

    @Override
    void endOnce(){
        reset();
    }
}
