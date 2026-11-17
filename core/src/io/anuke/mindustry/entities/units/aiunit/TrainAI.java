package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;

/** Controller for rail trains. Unfinished */
public class TrainAI extends CommandAI{
    public TrainAI(BaseUnit unit){
        super(unit);
    }

    @Override
    public void updateUnit(){
        ((MainTrain)unit).moveOnRails();
    }
}
