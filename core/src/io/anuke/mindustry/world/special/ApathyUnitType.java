package io.anuke.mindustry.world.special;

import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.entities.units.UnitType;

public class ApathyUnitType extends UnitType{
    public Array<ShiftHandler> handlers = new Array<>();

    public ApathyUnitType(String name){
        super(name, ApathyIUnit.class, ApathyIUnit::new);
        health = 12000000;
        armor = 60;
        speed = 0.45f;
        controller = ApathyIAI::new;
    }

    @Override
    public void load(){
        super.load();
        for(ShiftHandler h : handlers){
            h.load();
        }
    }
}
