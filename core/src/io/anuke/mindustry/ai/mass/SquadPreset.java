package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.entities.units.UnitType;

/**Defines the composition of a squad: its size, domain and the unit types it accepts.*/
enum SquadPreset{
    /**Flexible opener squads; air and ground are kept separate.*/
    LOW_GROUND(5, new UnitType[]{UnitTypes.evilDagger}, null, false, false),
    LOW_AIR(5, new UnitType[]{UnitTypes.evilWraith}, null, true, false),
    DEMOLITION_AIR(4, new UnitType[]{UnitTypes.FlyingExplosiveBiomass}, null, true, false),
    DEMOLITION_GROUND(4, new UnitType[]{UnitTypes.explosiveBiomass}, null, false, false),
    HEAVY_ASSAULT(5, new UnitType[]{UnitTypes.evilDagger, UnitTypes.evilTanky}, new int[]{3, 2}, false, false),
    ARTILLERY_AIR(2, new UnitType[]{UnitTypes.acidMosquito}, new int[]{2}, true, false),
    ARTILLERY_GROUND(2, new UnitType[]{UnitTypes.artilleryBiomass}, new int[]{2}, false, false),
    ULTRA(5, new UnitType[]{UnitTypes.exterminatorBiomass, UnitTypes.artilleryBiomass}, new int[]{3, 2}, false, false),
    /**Air-only strike wing; hunts enemy factories and producers instead of cores.*/
    AIR_STRIKE(4, new UnitType[]{UnitTypes.evilWraith, UnitTypes.FlyingExplosiveBiomass}, new int[]{2, 2}, true, true),
    /**Cheap disposable air swarm; fast, ignores terrain and harasses production.*/
    SWARM(6, new UnitType[]{UnitTypes.evilSwarmDrone}, new int[]{6}, true, true),
    /**Ground siege line; slow but grinds structures with sustained fire.*/
    SIEGE(4, new UnitType[]{UnitTypes.evilTanky, UnitTypes.artilleryBiomass}, new int[]{2, 2}, false, false);

    final int size;
    final UnitType[] types;
    final int[] wantCounts;
    /**True when every unit in this squad is airborne. Air and ground squads never mix. */
    final boolean air;
    /**True when this role prioritizes enemy factories/producers over cores.*/
    final boolean antiProduction;

    SquadPreset(int size, UnitType[] types, int[] wantCounts, boolean air, boolean antiProduction){
        this.size = size;
        this.types = types;
        this.wantCounts = wantCounts;
        this.air = air;
        this.antiProduction = antiProduction;
    }

    boolean accepts(UnitType type){
        if(type.isFlying != air) return false;
        for(UnitType t : types){
            if(t == type) return true;
        }
        return false;
    }

    /**How many more units of this type the squad wants, or 0 when the type is not accepted/full. Flexible presets
     *  (no per-type counts) fill up to their size.*/
    int want(UnitType type, int membersOfType, int totalMembers){
        if(!accepts(type)) return 0;
        if(wantCounts == null){
            return Math.max(0, size - totalMembers);
        }
        for(int i = 0; i < types.length; i++){
            if(types[i] == type){
                return Math.max(0, wantCounts[i] - membersOfType);
            }
        }
        return 0;
    }

    /**True when the squad can take one more loosely-assigned unit of this type.*/
    boolean canAcceptFill(UnitType type, int totalMembers){
        return accepts(type) && totalMembers < size;
    }
}
