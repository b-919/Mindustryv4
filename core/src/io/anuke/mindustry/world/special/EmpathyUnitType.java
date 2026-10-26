package io.anuke.mindustry.world.special;

import io.anuke.mindustry.entities.units.UnitType;

public class EmpathyUnitType extends UnitType{
    public EmpathyUnitType(String name){
        super(name, EmpathyUnit.class, EmpathyUnit::new);
        isFlying = true;
        hitsize = 7;
        drag = 0.07f;

        health = 100f;

        engineSize = -1f;
    }

    @Override
    public void load(){
        super.load();
        EmpathyRegions.load();
    }
}
