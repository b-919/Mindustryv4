package io.anuke.mindustry.type;

import io.anuke.mindustry.entities.Player;

/**
 * A reusable behaviour attached to a {@link Mech}.
 * <p>
 * This is the player-side counterpart of {@link io.anuke.mindustry.entities.units.Ability}. It keeps
 * the player save format unchanged while removing hard-coded behaviour from {@code Mechs.java}.
 */
public interface MechAbility{
    default void load(Mech mech){
    }

    default void update(Player player){
    }

    default void onLand(Player player){
    }

    default void draw(Player player){
    }

    default void drawOver(Player player){
    }

    default void drawBloom(Player player){
    }

    default float getExtraArmor(Player player){
        return 0f;
    }

    default float spreadX(Player player){
        return 0f;
    }

    default float getRotationAlpha(Player player){
        return 1f;
    }

    default boolean canShoot(Player player){
        return true;
    }
}
