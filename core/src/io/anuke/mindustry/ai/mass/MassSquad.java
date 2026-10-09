package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.units.UnitOrderType;
import io.anuke.mindustry.world.Tile;

/**One persistent squad: preset composition plus the current order and per-squad state.*/
class MassSquad{
    final int id;
    final SquadPreset preset;

    MassOrder order = MassOrder.PATROL_HIVES;
    MassOrder previousPatrol = MassOrder.PATROL_HIVES;
    boolean defensive;
    boolean giveUpPatrol;
    Unit pursuitTarget;
    float pursuitTimer;
    int memberCount;
    float cx, cy;

    float nextOrderTime;
    float nextTargetRefresh;
    float nodeTimer;
    int nodeIndex;
    int nodeCount;
    final float[] patrolX = new float[3];
    final float[] patrolY = new float[3];

    Tile attackTile;
    Unit attackUnit;

    UnitOrderType issuedOrder = UnitOrderType.none;
    float issuedX, issuedY;

    MassSquad(int id, SquadPreset preset){
        this.id = id;
        this.preset = preset;
    }
}