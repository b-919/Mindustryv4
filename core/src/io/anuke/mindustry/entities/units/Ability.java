package io.anuke.mindustry.entities.units;

/**
 * A reusable passive behaviour attached to a unit type.
 * <p>
 * Definitions live on {@link UnitType}; each unit receives a copy at spawn so timers are isolated.
 * Only {@link #data} is intended for network synchronisation.
 */
public abstract class Ability implements Cloneable{
    public boolean display = true;
    /** Single synced value; abilities that need more state should not add fields here. */
    public float data;

    public void init(UnitType type){
    }

    public void created(BaseUnit unit){
    }

    public void update(BaseUnit unit){
    }

    public void draw(BaseUnit unit){
    }

    public void death(BaseUnit unit){
    }

    public Ability copy(){
        try{
            return (Ability)clone();
        }catch(CloneNotSupportedException e){
            throw new RuntimeException(e);
        }
    }
}
