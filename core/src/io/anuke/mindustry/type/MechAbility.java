package io.anuke.mindustry.type;

import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.units.abilities.Ability;

/**
 * A reusable behaviour attached to a {@link Mech}.
 * <p>
 * Extends the shared {@link Ability} base so mech and unit abilities share one class hierarchy.
 * The mech-specific hooks take a {@link Player}; the base {@link Ability} hooks remain available
 * for the common lifecycle.
 */
public abstract class MechAbility extends Ability{
    public void load(Mech mech){
    }

    public void update(Player player){
    }

    public void onLand(Player player){
    }

    public void draw(Player player){
    }

    public void drawOver(Player player){
    }

    public void drawBloom(Player player){
    }

    public float getExtraArmor(Player player){
        return 0f;
    }

    public float spreadX(Player player){
        return 0f;
    }

    public float getRotationAlpha(Player player){
        return 1f;
    }

    public boolean canShoot(Player player){
        return true;
    }
}
