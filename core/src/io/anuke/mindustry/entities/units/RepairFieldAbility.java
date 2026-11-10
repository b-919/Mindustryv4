package io.anuke.mindustry.entities.units;

import io.anuke.mindustry.content.fx.UnitFx;
import io.anuke.mindustry.entities.Units;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;

/**
 * Periodically heals nearby damaged allies.
 * <p>
 * This replaces the hard-coded healing loop in {@code GroundHealUnit} and the tau mech hook.
 */
public class RepairFieldAbility extends Ability{
    public float amount = 1f;
    public float reload = 60f;
    public float range = 60f;
    public boolean parentizeEffects = false;

    private float timer;

    public RepairFieldAbility(){
    }

    public RepairFieldAbility(float amount, float reload, float range){
        this.amount = amount;
        this.reload = reload;
        this.range = range;
    }

    @Override
    public void update(BaseUnit unit){
        timer += Timers.delta();
        if(timer < reload) return;
        timer = 0f;

        Units.getNearby(unit.getTeam(), unit.x, unit.y, range, other -> {
            if(other == unit || other.health >= other.maxHealth()) return;
            other.healBy(amount);
            if(!parentizeEffects) Effects.effect(UnitFx.heal, other);
        });
    }
}
