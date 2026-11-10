package io.anuke.mindustry.entities.units;

import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.world;

/**
 * Controller for the shared flying archetype.
 * <p>
 * Ported from the former {@code FlyingUnit.attack/patrol/pursue/retreat/hold} states. Custom
 * flying subclasses keep {@link CommandAI} so their own states continue to run unchanged.
 */
public class FlyingAI extends CommandAI{
    public enum Mode{
        attack, patrol, pursue, retreat, hold
    }

    protected Mode mode = Mode.attack;

    public FlyingAI(BaseUnit unit){
        super(unit);
    }

    protected FlyingUnit flying(){
        return (FlyingUnit)unit;
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

        if(Units.invalidateTarget(unit.target, unit.getTeam(), unit.x, unit.y)){
            unit.target = null;
        }

        if(retarget()){
            unit.targetClosest();

            if(unit.target == null) unit.targetClosestEnemyFlag(BlockFlag.producer);
            if(unit.target == null) unit.targetClosestEnemyFlag(BlockFlag.turret);
            if(unit.target == null) unit.targetClosestEnemyFlag(BlockFlag.target);

            if(unit.target == null && unit.isCommanded() && unit.getCommand() != UnitCommand.attack){
                unit.onCommand(unit.getCommand());
            }
        }else if(unit.target != null){
            //keep custom flying attack overrides (missiles, mosquitoes) on their designed hit-and-run distance
            flying().attack(unit.getType().attackLength);
        }else{
            unit.target = unit.getClosestCore();
            flying().moveTo(Math.max(unit.getType().range, 120f));
        }
    }

    protected void patrol(){
        if(retarget()){
            unit.targetClosestAllyFlag(BlockFlag.comandCenter);
            unit.targetClosest();

            if(unit.target != null && !Units.invalidateTarget(unit.target, unit.getTeam(), unit.x, unit.y)){
                setMode(Mode.pursue);
                return;
            }

            if(unit.target == null) unit.target = unit.getClosestCore();
        }

        if(unit.target != null){
            flying().circle(60f + Mathf.absin(Timers.time() + unit.id * 23525, 70f, 1200f));
        }
    }

    protected void pursue(){
        if(Units.invalidateTarget(unit.target, unit.getTeam(), unit.x, unit.y) || unit.distanceTo(unit.target) > unit.getType().pursueRange){
            unit.target = null;
            unit.onCommand(unit.getCommand());
        }else{
            flying().attack(unit.getType().attackLength);
        }
    }

    protected void retreat(){
        if(unit.health >= unit.maxHealth()){
            if(unit.isCommanded()){
                unit.onCommand(unit.getCommand());
            }else{
                setMode(Mode.attack);
            }
        }

        if(retarget()){
            unit.target = unit.getClosestCore();
            Unit healer = Units.getClosest(unit.getTeam(), unit.x, unit.y, unit.getType().healRange, healerPred);
            Tile repair = Geometry.findClosest(unit.x, unit.y, world.indexer.getAllied(unit.getTeam(), BlockFlag.repair));
            if(repair != null && unit.health < unit.maxHealth()) unit.target = repair.entity;
            if(healer != null && repair == null && unit.health < unit.maxHealth()) unit.target = healer;
            if(unit.target == null) unit.target = unit.getClosestCore();
        }

        if(unit.target == unit.getClosestCore()){
            flying().circle(60f + Mathf.absin(Timers.time() + unit.id * 23525, 70f, 1200f));
        }else{
            flying().circle(45f + Mathf.randomSeed(unit.id) * 80);
        }
    }

    protected void hold(){
        unit.getVelocity().scl(0.9f);
        if(retarget()){
            unit.targetClosest();
        }
    }
}
