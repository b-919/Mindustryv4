package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.entities.traits.TargetTrait;

/**
 * Controller used by RTS-controlled units.
 * <p>
 * It owns the command queue, the current command and the stance mask, and forwards the resulting
 * order to the legacy {@link BaseUnit} order fields until the archetype AIs are fully migrated.
 */
public class CommandAI extends AIController{
    public final Vector2 targetPos = new Vector2();
    public final Array<Vector2> positionQueue = new Array<>(4);
    public final Array<TargetTrait> targetQueue = new Array<>(4);

    public TargetTrait attackTarget;
    public UnitCommand command;
    public long stances;
    public boolean hasTargetPos;

    public CommandAI(BaseUnit unit){
        super(unit);
    }

    @Override
    public void updateUnit(){
        unit.updateDefaultAI();
    }

    public UnitCommand currentCommand(){
        return command == null ? UnitCommand.attack : command;
    }

    public void command(UnitCommand command){
        this.command = command;
    }

    public boolean hasStance(UnitStance stance){
        return (stances & stance.bit) != 0L;
    }

    public void setStance(UnitStance stance, boolean enabled){
        if(enabled){
            stances |= stance.bit;
        }else{
            stances &= ~stance.bit;
        }
    }

    public void commandPosition(float x, float y){
        targetPos.set(x, y);
        hasTargetPos = true;
        attackTarget = null;
        unit.setDirectTarget(null);
        unit.orderMove(x, y);
    }

    public void commandAttackMove(float x, float y){
        targetPos.set(x, y);
        hasTargetPos = true;
        attackTarget = null;
        unit.setDirectTarget(null);
        unit.onCommand(UnitCommand.attack);
        unit.orderAttackMove(x, y);
    }

    public void commandTarget(TargetTrait target){
        attackTarget = target;
        if(target != null){
            targetPos.set(target.getX(), target.getY());
            hasTargetPos = true;
            //leave hold/retreat states so the unit actually engages the ordered target
            unit.onCommand(UnitCommand.attack);
        }
        unit.setDirectTarget(target);
        unit.orderAttackTarget(targetPos.x, targetPos.y);
        this.attackTarget = target;
    }

    public void clearCommands(){
        targetPos.setZero();
        hasTargetPos = false;
        attackTarget = null;
        positionQueue.clear();
        targetQueue.clear();
        unit.setDirectTarget(null);
        unit.clearOrder();
    }

    /** Called by {@link BaseUnit#orderMove(float, float)} without re-entering the unit. */
    protected void orderMove(float x, float y){
        targetPos.set(x, y);
        hasTargetPos = true;
        attackTarget = null;
    }

    /** Called by {@link BaseUnit#setDirectTarget(TargetTrait)} without re-entering the unit. */
    protected void orderTarget(TargetTrait target){
        attackTarget = target;
        if(target != null){
            targetPos.set(target.getX(), target.getY());
            hasTargetPos = true;
        }
    }

    /** Called by {@link BaseUnit#clearOrder()} without re-entering the unit. */
    protected void orderCleared(){
        hasTargetPos = false;
        attackTarget = null;
    }

    @Override
    public void removed(BaseUnit unit){
        clearCommands();
    }
}
