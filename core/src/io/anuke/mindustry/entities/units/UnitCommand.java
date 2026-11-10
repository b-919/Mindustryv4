package io.anuke.mindustry.entities.units;

import io.anuke.ucore.function.Function;
import io.anuke.ucore.util.Bundles;

/**
 * A command that can be issued to a unit.
 * <p>
 * The first three values are the original {@code CommandCenter} commands and keep their ordinals
 * so existing saves and network packets remain valid. Additional commands are appended.
 */
public enum UnitCommand{
    attack("right", true),
    retreat("left", true),
    patrol("refresh", true),
    move("move", false),
    mine("production", false),
    build("hammer", false),
    repair("modeSurvival", false),
    assist("players", false),
    stop("cancel", false);

    public final String icon;
    public final boolean commandCenter;
    /** Controller selected while this command is active; null means the unit's default AI. */
    public Function<BaseUnit, UnitController> controller;
    private final String localized;

    UnitCommand(String icon, boolean commandCenter){
        this.icon = icon;
        this.commandCenter = commandCenter;
        this.controller = null;
        this.localized = Bundles.get("command." + name());
    }

    public UnitCommand setController(Function<BaseUnit, UnitController> controller){
        this.controller = controller;
        return this;
    }

    public String localized(){
        return localized;
    }

    public static UnitCommand byOrdinal(int ordinal){
        UnitCommand[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : attack;
    }
}
