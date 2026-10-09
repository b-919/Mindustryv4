package io.anuke.mindustry.ai.mass;

/**Order assigned to a mass squad. Re-rolled periodically; ATTACK_BASE is the fallback when a target becomes invalid.*/
enum MassOrder{
    ATTACK_BASE, ATTACK_PRODUCTION, ATTACK_UNITS, ATTACK_UNIT_PRODUCTION,
    PATROL_OUTSIDE_HIVES, PATROL_HIVES, PURSUE, DEFEND_HIVE;

    static final MassOrder[] all = values();
}