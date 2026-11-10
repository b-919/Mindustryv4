package io.anuke.mindustry.entities.units;

import io.anuke.ucore.core.Timers;

/** Regenerates a fixed amount of health per tick. */
public class RegenAbility extends Ability{
    public float amount = 1f;

    public RegenAbility(){
    }

    public RegenAbility(float amount){
        this.amount = amount;
    }

    @Override
    public void update(BaseUnit unit){
        if(unit.health < unit.maxHealth()){
            unit.healBy(amount * Timers.delta());
        }
    }
}
