package io.anuke.mindustry.maps;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import io.anuke.annotations.Annotations.Serialize;
import io.anuke.mindustry.game.Saves.SaveSlot;
import io.anuke.mindustry.game.SpawnGroup;
import io.anuke.mindustry.maps.missions.*;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.ucore.util.Bits;

import static io.anuke.mindustry.Vars.control;
import static io.anuke.mindustry.Vars.headless;

@Serialize
public class Sector{
    private static final Mission victoryMission = new VictoryMission();

    /**Position on the map, can be positive or negative.*/
    public short x, y;
    /**Whether this sector has already been completed.*/
    public boolean complete;
    /**Whether this sector's terrain has been infected by biomass. Persisted across sessions.*/
    public boolean infected;
    /**Stored campaign-view lighting: 0 = dark side, 1 = fully facing the sun.*/
    public float light = 1f;
    /**Slot ID of this sector's save. -1 means no save has been created.*/
    public int saveID = -1;
    /**Num of missions in this sector that have been completed so far.*/
    public int completedMissions;

    /**Display texture. Needs to be disposed.*/
    public transient Texture texture;
    /**Missions of this sector-- what needs to be accomplished to unlock it.*/
    public transient Array<Mission> missions = new Array<>();
    /**Enemies spawned at this sector.*/
    public transient Array<SpawnGroup> spawns;
    /**Difficulty of the sector, measured by calculating distance from origin and applying scaling.*/
    public transient int difficulty;
    /**Items the player starts with on this sector.*/
    public transient Array<ItemStack> startingItems;
    /**Whether this sector represents a linear campaign level rather than an open-world sector.*/
    public transient boolean linear;
    /**Name of the linear campaign this level belongs to. Null for open-world sectors.*/
    public transient String linearCampaign;
    /**Index of the level within its linear campaign. -1 for open-world sectors.*/
    public transient int linearLevelIndex = -1;
    /**Custom display name for the level's map. Null for open-world sectors.*/
    public transient String mapName;

    public Mission getDominantMission(){
        for(Mission mission : missions){
            if(mission instanceof WaveMission || mission instanceof BattleMission){
                return mission;
            }
        }

        for(Mission mission : missions){
            if(mission instanceof BlockMission){
                return mission;
            }
        }
        return missions.first();
    }

    public Mission currentMission(){
        return completedMissions >= missions.size ? victoryMission : missions.get(completedMissions);
    }

    public boolean isInfectable(){
        return currentMission().isInfectable();
    }

    /** Whether this sector is an attack sector with an enemy base. */
    public boolean isAttack(){
        return currentMission().isAttack();
    }

    /** Whether the player has a base here (captured or has a save). */
    public boolean hasBase(){
        return complete || hasSave();
    }

    /** Whether this sector has an enemy base and is not player-owned. */
    public boolean hasEnemyBase(){
        return !hasBase() && isAttack();
    }

    public boolean isInfected(){
        return infected || currentMission().isInfected();
    }

    public int getSeed(){
        return Bits.packInt(x, y);
    }

    public SaveSlot getSave(){
        return !hasSave() ? null : control.saves.getByID(saveID);
    }

    public boolean hasSave(){
        return !headless && control.saves.getByID(saveID) != null;
    }

    public int packedPosition(){
        return Bits.packInt(x, y);
    }
}
