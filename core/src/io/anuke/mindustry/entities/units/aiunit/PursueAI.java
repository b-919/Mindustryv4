package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.entities.Units;

/** Ground controller for biomass swarms. */
public class PursueAI extends GroundAI{
    public PursueAI(BaseUnit unit){
        super(unit);
    }

    @Override
    protected void patrol(){
        if(retarget()){
            unit.targetClosest();

            if(unit.target != null && !Units.invalidateTarget(unit.target, unit.getTeam(), unit.x, unit.y)){
                setMode(Mode.pursue);
                return;
            }
        }

        super.patrol();
    }

    protected void pursue(){
        if(Units.invalidateTarget(unit.target, unit.getTeam(), unit.x, unit.y) || unit.distanceTo(unit.target) > unit.getType().pursueRange){
            unit.target = null;
            unit.onCommand(unit.getCommand());
        }else if(unit.distanceTo(unit.target) > unit.getWeapon().getAmmo().getRange() * 0.8f){
            unit.moveTo(unit.target.getX(), unit.target.getY());
        }
    }

    @Override
    public void updateUnit(){
        if(unit.updateOrder()){
            return;
        }

        switch(mode){
            case attack:
                attack();
                break;
            case patrol:
                patrol();
                break;
            case pursue:
                pursue();
                break;
            case retreat:
                retreat();
                break;
            case hold:
                hold();
                break;
        }
    }
}
