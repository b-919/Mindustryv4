package io.anuke.mindustry.entities.units;

import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.util.Geometry;

import static io.anuke.mindustry.Vars.world;

/**
 * Controller for the shared ground, legs and tank archetypes.
 * <p>
 * This is a direct port of the former {@code GroundUnit.attack/patrol/retreat/hold} states.
 * Custom ground subclasses keep the legacy state machine via {@link CommandAI} until their
 * behaviour has been ported.
 */
public class GroundAI extends CommandAI{
    public enum Mode{
        attack, patrol, retreat, hold
    }

    protected Mode mode = Mode.attack;

    public GroundAI(BaseUnit unit){
        super(unit);
    }

    public void setMode(Mode mode){
        if(this.mode == mode) return;
        this.mode = mode;
        if(mode == Mode.retreat || mode == Mode.attack){
            unit.target = null;
        }
    }

    public boolean isRetreating(){
        return mode == Mode.retreat;
    }

    public void command(UnitCommand command){
        setMode(command == UnitCommand.retreat ? Mode.retreat :
                command == UnitCommand.attack ? Mode.attack :
                command == UnitCommand.patrol ? Mode.patrol :
                Mode.attack);
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
            case retreat:
                retreat();
                break;
            case hold:
                hold();
                break;
        }
    }

    protected void attack(){
        if(unit.health < unit.maxHealth() * 0.5f && timer.get(timerRetreat, 30f)){
            Tile repair = Geometry.findClosest(unit.x, unit.y, world.indexer.getAllied(unit.getTeam(), BlockFlag.repair));
            Unit healer = Units.getClosest(unit.getTeam(), unit.x, unit.y, unit.getType().healRange, healerPred);
            if(repair != null && unit.distanceTo(repair) < unit.getType().healRange){
                setMode(Mode.retreat);
                return;
            }else if(healer != null){
                setMode(Mode.retreat);
                return;
            }
        }

        if(unit.getOrderType() == UnitOrderType.attackTarget && unit.target != null){
            if(unit.distanceTo(unit.target) > unit.engageRange()){
                unit.path.moveWithPathfinding(unit, unit.target.getX(), unit.target.getY());
            }else{
                unit.getVelocity().scl(0.85f);
            }
            return;
        }

        TileEntity core = unit.getClosestEnemyCore();

        if(unit.target == null && core != null){
            unit.target = core;
        }

        if(unit.target == null && timer.get(timerFallback, 20f)){
            unit.targetClosestEnemyFlag(BlockFlag.producer);
            if(unit.target == null) unit.targetClosestEnemyFlag(BlockFlag.turret);
            if(unit.target == null) unit.targetClosestEnemyFlag(BlockFlag.target);
        }

        if(core != null && unit.distanceTo(core) > unit.getWeapon().getAmmo().getRange() * 0.5f){
            unit.moveToEnemyCore();
        }else if(core == null && unit.target != null){
            unit.path.moveWithPathfinding(unit, unit.target.getX(), unit.target.getY());
        }
    }

    protected void patrol(){
        TileEntity core = unit.getClosestCore();
        if(core != null){
            if(unit.distanceTo(core) > 400f){
                unit.moveAwayFromCore();
            }else{
                unit.patrol();
            }
        }
    }

    protected void retreat(){
        if(unit.health >= unit.maxHealth()){
            unit.path.clearMove();
            if(unit.isCommanded()){
                unit.onCommand(unit.getCommand());
            }else{
                setMode(Mode.attack);
            }
            return;
        }

        if(retarget() || unit.target == null || (unit.target instanceof TileEntity && (((TileEntity)unit.target).getTile() == null || ((TileEntity)unit.target).getTile().target().block().flags == null || !((TileEntity)unit.target).getTile().target().block().flags.contains(BlockFlag.repair))) || (unit.target instanceof Unit && !((Unit)unit.target).isHealer())){
            Unit healer = Units.getClosest(unit.getTeam(), unit.x, unit.y, unit.getType().healRange, healerPred);
            Tile repair = Geometry.findClosest(unit.x, unit.y, world.indexer.getAllied(unit.getTeam(), BlockFlag.repair));
            unit.path.clearMove();
            if(repair != null) unit.target = repair.entity();
            else if(healer != null) unit.target = healer;
            else unit.target = unit.getClosestCore();
        }

        if(unit.target != null){
            float dst = unit.distanceTo(unit.target);
            if(dst > 7f){
                if(unit.target instanceof TileEntity && ((TileEntity)unit.target).getTile() != null && ((TileEntity)unit.target).getTile().target().block().flags != null && ((TileEntity)unit.target).getTile().target().block().flags.contains(BlockFlag.repair)){
                    unit.path.moveWithPathfinding(unit, unit.target.getX(), unit.target.getY());
                }else if(unit.target instanceof Unit && ((Unit)unit.target).isHealer()){
                    if(dst > unit.getType().healRange){
                        unit.moveToHome();
                    }else{
                        unit.path.moveWithPathfinding(unit, unit.target.getX(), unit.target.getY());
                    }
                }else{
                    unit.moveToHome();
                }
            }else{
                unit.getVelocity().setZero();
            }
        }else{
            unit.moveToHome();
        }
    }

    protected void hold(){
        unit.getVelocity().scl(0.9f);
        if(retarget()){
            unit.targetClosest();
        }
    }
}
