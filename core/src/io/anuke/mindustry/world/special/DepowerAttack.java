package io.anuke.mindustry.world.special;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.gen.Call;

public class DepowerAttack extends AttackAI{
    float reload = 0f;

    @Override
    void updatePassive(){
        if(reload > 0) reload -= Timers.delta();
    }

    @Override
    float weight(){
        return reload <= 0f && EmpathyDamage.excludeSeq.size > 3 ? 500000f : -1f;
    }

    @Override
    void update(){
        if(reload <= 0f){
            for(Unit u : EmpathyDamage.excludeSeq){
                FlameFX.empathyDepowered.at(u.x, u.y, u.hitSize / 2f);
            }
            EmpathyDamage.clearExclude();
            FlameFX.endFlash.at(unit.x, unit.y, 1.5f);

            reload = 2.5f * 60f;
            unit.randAI(true, true);
        }
    }
}
