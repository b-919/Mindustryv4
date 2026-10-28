package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.world.Tile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;

/**
 * Serialization for the Mass AI.
 *
 * <p>Three layouts are supported; the outer save version (see {@code Save17#massSaveVersion}) decides
 * which one to read:</p>
 *
 * <ul>
 *     <li>{@link #FORMAT_LEGACY legacy} - no header, no response timers, ends with the old
 *     global-command tail ({@code currentCommand}, grace, idle timer, nearby flag).</li>
 *     <li>{@link #FORMAT_HEADERED format 4} - header int, response timers, still has the legacy tail.
 *     Introduced on 2026-10-21.</li>
 *     <li>{@link #SAVE_VERSION format 5} - header int, response timers, legacy tail removed.
 *     Written by every new save.</li>
 * </ul>
 */
public class MassSave{
    /**Current layout version; written as the first int of the mass block.*/
    public static final int SAVE_VERSION = 5;
    public static final int FORMAT_HEADERED = 4;
    /**Saves written before the header existed.*/
    public static final int FORMAT_LEGACY = 0;

    private MassSave(){}

    public static void write(DataOutputStream stream) throws IOException{
        stream.writeInt(SAVE_VERSION);

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
        stream.writeFloat(MassDefense.responseTimer);
        stream.writeFloat(MassDefense.nextResponseTime);
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

        stream.writeFloat(MassInfection.commandTimer);
    }

    /**
     * Reads the mass block using the layout selected by {@code format}.
     *
     * @param format {@link #FORMAT_LEGACY} for saves without a mass header, otherwise the outer-version
     *               selected format ({@link #FORMAT_HEADERED} or {@link #SAVE_VERSION}). The stream's own
     *               header is validated against it.
     */
    public static void read(DataInputStream stream, int format) throws IOException{
        int version = format;

        if(format >= FORMAT_HEADERED){
            int header = stream.readInt();
            if(header != format){
                throw new IOException("Unsupported Mass AI save format " + header + " (expected " + format + ")");
            }
            version = header;
        }

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
        if(version >= FORMAT_HEADERED){
            MassDefense.responseTimer = stream.readFloat();
            MassDefense.nextResponseTime = stream.readFloat();
        }
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

        //legacy layouts ended with global-command bookkeeping that no longer drives behavior
        if(version < SAVE_VERSION){
            stream.readInt(); //legacy currentCommand ordinal
        }
        MassInfection.commandTimer = stream.readFloat();
        if(version < SAVE_VERSION){
            stream.readFloat(); //legacy enemyNearbyTimer
            stream.readBoolean(); //legacy enemyNearby flag
        }

        MassSquads.onLoaded();

        //turret counts are rebuilt lazily; ensure the stale cache from a previous world is discarded
        MassDefense.invalidateTurretCount();
    }
}
