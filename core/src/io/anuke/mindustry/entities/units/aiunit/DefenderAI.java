package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.mindustry.gen.Call;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

/** Defends a leader building and returns to it when idle. */
public class DefenderAI extends CommandAI{
    static final float followDistance = 80f;
    static final float maxLeash = 500f;

    protected TileEntity leader;
    protected boolean returning;
    protected float despawnTimer;
    protected float despawnTime = 10f;

    public DefenderAI(BaseUnit unit){
        super(unit);
    }

    public void setLeader(TileEntity leader){
        this.leader = leader;
    }

    public TileEntity getLeader(){
        return leader;
    }

    @Override
    public void updateUnit(){
        FlyingUnit flying = (FlyingUnit)unit;

        if(leader == null || leader.isDead()){
            unit.damage(99999f);
            return;
        }

        if(returning){
            updateReturning(flying);
            return;
        }
        TargetTrait attacker = leader.lastDamager instanceof TargetTrait && !((TargetTrait)leader.lastDamager).isDead()
                ? (TargetTrait)leader.lastDamager : null;

        TargetTrait last = unit.target;
        unit.target = leader;
        if(last == null){
            flying.circle(60f);
        }
        unit.target = last;

        if(attacker != null && unit.distanceTo(attacker) < maxLeash){
            unit.target = attacker;
        }else if(unit.distanceTo(leader) < followDistance){
            unit.targetClosest();
        }else{
            unit.target = null;
        }

        if(unit.target != null){
            flying.attack(50f);
            despawnTimer = 0f;

            if(Mathf.angNear(unit.angleTo(unit.target), unit.rotation, 15f) && unit.distanceTo(unit.target) < unit.getWeapon().getAmmo().getRange()){
                unit.shootAt(unit.target);
            }
        }

        if(unit.target == null){
            despawnTimer += Timers.delta();
            if(despawnTimer > 60f * despawnTime){
                returning = true;
            }
        }
        if(unit.target == null && attacker == null && unit.distanceTo(leader) < 8f){
            Call.onDefenseDroneFade(unit);
        }

        if(unit.distanceTo(leader) > maxLeash){
            unit.damage(99999f);
        }
    }

    private void updateReturning(FlyingUnit flying){
        unit.target = leader;
        flying.moveTo(0f);
        unit.target = null;

        if(leader.lastDamager instanceof TargetTrait && !((TargetTrait)leader.lastDamager).isDead()
                && unit.distanceTo(leader.lastDamager) < maxLeash){
            returning = false;
            return;
        }

        if(unit.distanceTo(leader) < 8f){
            Call.onDefenseDroneFade(unit);
        }
    }

    @Override
    public void removed(BaseUnit unit){
        if(leader != null){
            leader.defenseDronesCount--;
        }
    }
}
