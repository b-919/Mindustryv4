package io.anuke.mindustry.entities.units;

import io.anuke.ucore.util.Bundles;

/**
 * A toggleable behaviour flag that can be combined with any command.
 * <p>
 * Stance ids are bit positions in a {@code long}; this keeps the runtime state allocation-free
 * and avoids the boxed collections that {@code ObjectSet} would need.
 */
public enum UnitStance{
    stop("cancel", false),
    holdFire("none", true),
    pursueTarget("right", true),
    patrol("refresh", true),
    ram("rightOpen", true),
    boost("up", true),
    holdPosition("effect", true),
    follow("players", true);

    public final String name;
    public final String icon;
    public final boolean toggle;
    public final long bit;

    UnitStance(String icon, boolean toggle){
        this.name = name().toLowerCase();
        this.icon = icon;
        this.toggle = toggle;
        this.bit = 1L << ordinal();
    }

    public String localized(){
        return Bundles.get("stance." + name);
    }
}
