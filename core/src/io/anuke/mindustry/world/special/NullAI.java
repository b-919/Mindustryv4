package io.anuke.mindustry.world.special;

import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.ai.AIController;

public class NullAI extends AIController{
    public NullAI(BaseUnit unit){
        super(unit);
    }

    @Override
    public void updateUnit(){
    }
}
