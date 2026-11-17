package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.mindustry.gen.Call;
import io.anuke.ucore.util.Mathf;

/** Follows a player leader and assists with combat. */
public class FollowAI extends CommandAI{
    static final float followDistance = 80f;

    protected Player leader;

    public FollowAI(BaseUnit unit){
        super(unit);
    }

    public void setLeader(Player leader){
        this.leader = leader;
    }

    public Player getPlayerLeader(){
        return leader;
    }

    @Override
    public void updateUnit(){
        FlyingUnit flying = (FlyingUnit)unit;

        if(leader == null || leader.isDead() || !leader.isAdded()){
            unit.damage(99999f);
            return;
        }

        TargetTrait last = unit.target;
        unit.target = leader;

        if(last == null){
            flying.circle(leader.isShooting ? 60f : 0f);
        }

        unit.target = last;
        if(unit.distanceTo(leader) < followDistance){
            unit.targetClosest();
        }else{
            unit.target = null;
        }

        if(unit.target != null){
            flying.attack(50f);

            if(Mathf.angNear(unit.angleTo(unit.target), unit.rotation, 15f) && unit.distanceTo(unit.target) < unit.getWeapon().getAmmo().getRange()){
                unit.shootAt(unit.target);
            }
        }

        if(!leader.isShooting && unit.distanceTo(leader) < 8f){
            Call.onAlphaDroneFade(unit);
        }
    }
}
