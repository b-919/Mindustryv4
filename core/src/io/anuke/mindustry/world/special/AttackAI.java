package io.anuke.mindustry.world.special;

import io.anuke.mindustry.entities.traits.TargetTrait;

public class AttackAI extends EmpathyAI{
    AttackAI(){
        attack = true;
    }

    @Override
    TargetTrait getTarget(){
        return unit.getTarget();
    }
}
