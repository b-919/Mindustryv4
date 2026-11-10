package io.anuke.mindustry.entities.units;

import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.ucore.function.Predicate;
import io.anuke.ucore.util.Timer;

/**
 * Base controller shared by all AI units.
 * <p>
 * During the migration this class delegates to the unit's existing state machine. New behaviour
 * should be added here or in a subclass instead of a new {@code UnitState} object, so that the
 * legacy states can be deleted one archetype at a time.
 */
public abstract class AIController implements UnitController{
    protected static final int timerTarget = 0;
    protected static final int timerRetreat = 1;
    protected static final int timerFallback = 2;

    protected BaseUnit unit;
    protected TargetTrait target;
    protected final Timer timer = new Timer(6);
    /**Reused healer-search predicate; a field so the update path never allocates a closure.*/
    protected final Predicate<Unit> healerPred = this::isHealerAlly;

    public AIController(BaseUnit unit){
        this.unit = unit;
    }

    @Override
    public void unit(BaseUnit unit){
        this.unit = unit;
    }

    @Override
    public BaseUnit unit(){
        return unit;
    }

    @Override
    public void updateUnit(){
        unit.updateDefaultAI();
    }

    /** Returns true when the target should be re-selected this tick. */
    protected boolean retarget(){
        if(unit.getOrderType() == UnitOrderType.attackTarget) return false;
        return timer.get(timerTarget, 20f);
    }

    /**True when {@code other} is a healer unit distinct from this controller's unit.*/
    protected boolean isHealerAlly(Unit other){
        return other.isHealer() && other != unit;
    }

    protected boolean invalid(TargetTrait target){
        return Units.invalidateTarget(target, unit);
    }

    public TargetTrait target(){
        return target;
    }

    public void target(TargetTrait target){
        this.target = target;
    }
}
