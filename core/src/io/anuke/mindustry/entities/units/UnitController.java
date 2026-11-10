package io.anuke.mindustry.entities.units;

import io.anuke.ucore.entities.trait.Entity;

/**
 * Owns the decision-making for one unit.
 * <p>
 * Controllers are created when a unit is initialised and replaced when the unit is read from a
 * save or switched by a command. They never own the body state themselves; they only set targets,
 * movement intent and weapon intent on the unit.
 */
public interface UnitController{
    void unit(BaseUnit unit);

    BaseUnit unit();

    void updateUnit();

    default void hit(Entity source){
    }

    default void removed(BaseUnit unit){
    }

    default void afterRead(BaseUnit unit){
    }

    default boolean keepState(){
        return false;
    }
}
