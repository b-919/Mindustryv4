package io.anuke.mindustry.content;

import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Mech;

/**
 * Player unit data. These content entries are appended after the normal unit list so old unit
 * content ids stay stable. They are hidden and not spawnable; they exist so the player entity can
 * share the unit data model without a second content type.
 */
public class PlayerUnitTypes implements ContentList{
    private static Mech[] mechs;

    @Override
    public void load(){
        mechs = new Mech[]{
                Mechs.alpha, Mechs.delta, Mechs.tau, Mechs.omega,
                Mechs.dart, Mechs.javelin, Mechs.trident, Mechs.glaive,
                Mechs.debugGround, Mechs.debugAir
        };

        for(Mech mech : mechs){
            attach(mech);
        }
    }

    private void attach(Mech mech){
        UnitType type = new UnitType(mech.name, Player.class, Player::new){
            @Override
            public boolean isHidden(){
                return true;
            }
        };

        type.isFlying = mech.flying;
        type.health = 200f;
        type.hitsize = mech.hitsize;
        type.speed = mech.speed;
        type.maxVelocity = mech.maxSpeed;
        type.drag = mech.drag;
        type.mass = mech.mass;
        type.armor = mech.armor;
        type.itemCapacity = mech.itemCapacity;
        type.weapon = mech.weapon;
        type.setWeapons(mech.weapons);
        type.isHealer = mech.isHealer;
        type.buildPower = mech.buildPower;
        type.minePower = mech.mineSpeed;
        type.carryWeight = mech.carryWeight;
        type.drillPower = mech.drillPower;
        type.boostSpeed = mech.boostSpeed;
        type.spawnsInSiegeMode = false;
        type.unitCost = 0;
        type.playerControllable = true;
        type.rtsAIControllable = false;

        //player bodies use the mech sprite set and mech-only draw data
        type.mechBody = true;
        type.iconRegionName = "mech-icon-" + mech.name;
        type.cellTrnsY = mech.cellTrnsY;
        type.shake = mech.shake;
        type.turnCursor = mech.turnCursor;
        type.trailColor.set(mech.trailColor);
        type.trailColorTo.set(mech.trailColorTo);
        type.healTurretOffsetX = mech.healTurretOffsetX;
        type.healTurretOffsetY = mech.healTurretOffsetY;

        mech.unitType = type;
    }

    @Override
    public ContentType type(){
        return ContentType.unit;
    }
}
