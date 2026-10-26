package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Interpolation;
import io.anuke.ucore.util.Mathf;
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
import io.anuke.mindustry.graphics.*;

public class SwordBarrageAttack extends AttackAI{
    Array<SwordSpawner> spawners = new Array<>();
    float reload = 0f;

    @Override
    void reset(){
        reload = 0f;
    }

    @Override
    boolean teleSwapCompatible(){
        return true;
    }

    @Override
    void updatePassive(){
        spawners.removeAll(s -> {
            s.update();
            return s.time >= 4 * 60f;
        });
        reload -= Timers.delta();
    }

    @Override
    void draw(){
        for(SwordSpawner s : spawners){
            s.draw();
        }
    }

    @Override
    void update(){
        if(reload <= 0f && unit.getTarget() != null && unit.within(unit.getTarget(), 600f)){
            for(int i = 0; i < 4; i++){
                float r = Mathf.random(360f);
                Vector2 v = Tmp.v1.trns(r, unit.dst(unit.getTarget())).add(unit.getTarget());
                SwordSpawner s = new SwordSpawner();
                s.x = v.x;
                s.y = v.y;
                s.rotation = r + 180f;
                s.fromDecoy = unit.isDecoy();
                spawners.add(s);
                FlameSounds.empathySquareCharge.at(v.x, v.y, 2.5f);
            }

            reload = 2.5f * 60f;
            unit.randAI(true, unit.health < 50f);
        }
        //reload -= Timers.delta();
    }

    @Override
    boolean shouldDraw(){
        return !spawners.isEmpty();
    }

    class SwordSpawner{
        float x, y, rotation;
        float time, reload;
        boolean fromDecoy;

        void update(){
            if(time > 16f && time < (4f * 60f) - 16f){
                if(reload <= 0f){
                    Tmp.v1.trns(rotation - 90f, Mathf.range(30f)).add(x, y);
                    FlameBullets.sword.create(unit, unit.team, Tmp.v1.x, Tmp.v1.y, rotation);
                    FlameSounds.empathySquareShoot.at(Tmp.v1, Mathf.random(0.9f, 1.1f) * 2.5f);
                    reload = fromDecoy ? 10f : 5f;
                }
                reload -= Timers.delta();
            }
            time += Timers.delta();
        }
        void draw(){
            float f = Interpolation.pow2.apply(Mathf.clamp(time / 16f) * Mathf.clamp(((4f * 60) - time) / 16f));
            float rot = time * 2;
            Draw.color(FlamePal.empathy);
            //Fill.circle(x, y, 20f * f);
            Drawf.tri(x, y, 15f * f, 55f, rotation + 90f);
            Drawf.tri(x, y, 15f * f, 55f, rotation - 90f);

            Lines.stroke(2f * f);
            Lines.beginLine();
            for(int i = 0; i < 24; i++){
                float ang = (360f / 24) * i + rot;
                GraphicUtils.draw3D(x, y, -75f, 0f, -rotation - 90f, v -> v.trns(ang, 90f), Lines::linePoint);
            }
            Lines.endLine(true);
            Lines.beginLine();
            for(int i = 0; i < 7; i++){
                //float ang = (360f / 5) * i + rot;
                float ang = 360f * (1f / 7f) * i * 2 + rot;
                GraphicUtils.draw3D(x, y, -75f, 0f, -rotation - 90f, v -> v.trns(ang, 90f), Lines::linePoint);
            }
            Lines.endLine(true);
        }
    }
}
