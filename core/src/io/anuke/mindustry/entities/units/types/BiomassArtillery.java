package io.anuke.mindustry.entities.units.types;

import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.BiomassGroundUnit;

public class BiomassArtillery extends BiomassGroundUnit {


    @Override
    protected void patrol(){
        if(Units.invalidateTarget(target, this)){
            super.patrol();
        }
    }
    @Override
    protected void moveToEnemyCore(){
        if(Units.invalidateTarget(target, this)){
            super.moveToEnemyCore();
        }
    }
}
