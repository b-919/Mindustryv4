package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.entities.units.UnitCommand;
import io.anuke.mindustry.world.Tile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;

/** Serialization for the Mass AI */
public class MassSave{
    private MassSave(){}

    public static void write(DataOutputStream stream) throws IOException{
        stream.writeInt(MassBuilder.initializedCores.size);
        for(Tile core : MassBuilder.initializedCores){
            stream.writeLong(core.packedPosition());
        }

        stream.writeInt(MassBuilder.activeLines.size);
        for(BuildingLine line : MassBuilder.activeLines){
            line.write(stream);
        }

        stream.writeInt(MassEconomy.activeSubsections.size);
        for(SubSection sub : MassEconomy.activeSubsections){
            sub.write(stream);
        }

        stream.writeFloat(MassEconomy.spawnTimer);
        stream.writeFloat(MassDefense.turretTimer);
        stream.writeFloat(MassDefense.nextTurretTime);
        stream.writeFloat(MassDefense.damageTurretTimer);
        stream.writeFloat(MassDefense.nextDamageTurretTime);
        stream.writeFloat(MassInfection.nextInfectionTime);

        stream.writeInt(MassDefense.pendingBuilds.size);
        for(PendingBuild build : MassDefense.pendingBuilds){
            build.write(stream);
        }

        for(int boost : MassEconomy.oreBoosts){
            stream.writeInt(boost);
        }

        stream.writeInt(MassEconomy.coreExpanded.size);
        for(ObjectMap.Entry<Tile, Boolean> entry : MassEconomy.coreExpanded.entries()){
            stream.writeLong(entry.key.packedPosition());
            stream.writeBoolean(entry.value);
        }

        stream.writeInt(MassSquads.currentCommand.ordinal());
        stream.writeFloat(MassInfection.commandTimer);
        stream.writeFloat(MassSquads.enemyNearbyTimer);
        stream.writeBoolean(MassUtil.enemyNearby);
    }

    public static void read(DataInputStream stream) throws IOException{
        int coreCount = stream.readInt();
        MassBuilder.initializedCores.clear();
        for(int i = 0; i < coreCount; i++){
            MassBuilder.initializedCores.add(world.tile(stream.readLong()));
        }

        int lineCount = stream.readInt();
        MassBuilder.activeLines.clear();
        for(int i = 0; i < lineCount; i++){
            MassBuilder.activeLines.add(BuildingLine.read(stream));
        }

        int subCount = stream.readInt();
        MassEconomy.activeSubsections.clear();
        for(int i = 0; i < subCount; i++){
            MassEconomy.activeSubsections.add(SubSection.read(stream));
        }

        MassEconomy.spawnTimer = stream.readFloat();
        MassDefense.turretTimer = stream.readFloat();
        MassDefense.nextTurretTime = stream.readFloat();
        MassDefense.damageTurretTimer = stream.readFloat();
        MassDefense.nextDamageTurretTime = stream.readFloat();
        MassInfection.nextInfectionTime = stream.readFloat();
        MassInfection.disableGrace = true;

        int pendingCount = stream.readInt();
        MassDefense.pendingBuilds.clear();
        for(int i = 0; i < pendingCount; i++){
            MassDefense.pendingBuilds.add(PendingBuild.read(stream));
        }

        for(int i = 0; i < MassEconomy.oreBoosts.length; i++){
            MassEconomy.oreBoosts[i] = stream.readInt();
        }

        int expandedCount = stream.readInt();
        MassEconomy.coreExpanded.clear();
        for(int i = 0; i < expandedCount; i++){
            MassEconomy.coreExpanded.put(world.tile(stream.readLong()), stream.readBoolean());
        }

        MassSquads.currentCommand = UnitCommand.values()[stream.readInt()];
        MassInfection.commandTimer = stream.readFloat();
        MassSquads.enemyNearbyTimer = stream.readFloat();
        MassSquads.onLoaded();
        MassUtil.enemyNearby = stream.readBoolean();

        //turret counts are rebuilt lazily; ensure the stale cache from a previous world is discarded
        MassDefense.invalidateTurretCount();
    }
}