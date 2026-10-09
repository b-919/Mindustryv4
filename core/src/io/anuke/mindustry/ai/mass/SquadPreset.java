package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.entities.units.UnitType;

/**Defines the composition of a squad: its size and the unit types it accepts.*/
enum SquadPreset{
    /**Flexible opener squad; fills with whatever cheap ground/air biomass is available.*/
    //todo lacks of squad types
    LOW(5, new UnitType[]{UnitTypes.evilDagger, UnitTypes.evilWraith}),
    DEMOLITION_AIR(4, new UnitType[]{UnitTypes.FlyingExplosiveBiomass}),
    DEMOLITION_GROUND(4, new UnitType[]{UnitTypes.explosiveBiomass}),
    HEAVY_ASSAULT(5, new UnitType[]{UnitTypes.evilDagger, UnitTypes.evilTanky}, new int[]{3, 2}),
    ARTILLERY(4, new UnitType[]{UnitTypes.acidMosquito, UnitTypes.artilleryBiomass}, new int[]{2, 2}),
    ULTRA(5, new UnitType[]{UnitTypes.exterminatorBiomass, UnitTypes.artilleryBiomass}, new int[]{3, 2}),
    /**Air-only strike wing; hunts enemy factories and producers instead of cores.*/
    AIR_STRIKE(4, new UnitType[]{UnitTypes.evilWraith, UnitTypes.FlyingExplosiveBiomass}, new int[]{2, 2}, true, true),
    /**Cheap disposable air swarm; fast, ignores terrain and harasses production.*/
    SWARM(6, new UnitType[]{UnitTypes.evilSwarmDrone}, new int[]{6}, true, true),
    /**Ground siege line; slow but grinds structures with sustained fire.*/
    SIEGE(4, new UnitType[]{UnitTypes.evilTanky, UnitTypes.artilleryBiomass}, new int[]{2, 2});

    //static final SquadPreset[] all = values();

    final int size;
    final UnitType[] types;
    final int[] wantCounts;
    /**True when every unit in this squad is airborne.*/
    final boolean air;
    /**True when this role prioritizes enemy factories/producers over cores.*/
    final boolean antiProduction;

    SquadPreset(int size, UnitType[] types){
        this(size, types, null, false, false);
    }

    SquadPreset(int size, UnitType[] types, int[] wantCounts){
        this(size, types, wantCounts, false, false);
    }

    SquadPreset(int size, UnitType[] types, int[] wantCounts, boolean air, boolean antiProduction){
        this.size = size;
        this.types = types;
        this.wantCounts = wantCounts;
        this.air = air;
        this.antiProduction = antiProduction;
    }

    boolean accepts(UnitType type){
        for(UnitType t : types){
            if(t == type) return true;
        }
        return false;
    }

    /**How many more units of this type the squad wants, or 0 when the type is not accepted/full. Flexible presets
     *  (no per-type counts) fill up to their size, with LOW accepting whatever cheap biomass is available.*/
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