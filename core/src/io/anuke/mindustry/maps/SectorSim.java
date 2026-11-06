package io.anuke.mindustry.maps;

import com.badlogic.gdx.utils.*;
import io.anuke.mindustry.core.LaunchManager;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.io.TypeSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

/** Persistent background simulation state for a captured sector. */
public class SectorSim{
    /** Items stored in the sector's cores while it is not loaded. */
    public ObjectIntMap<Item> items = new ObjectIntMap<>();
    /** Average core input, in items per second. */
    public ObjectFloatMap<Item> production = new ObjectFloatMap<>();
    /** Average launch-pad output, in items per second. */
    public ObjectFloatMap<Item> export = new ObjectFloatMap<>();
    /** Average items received from landing pads, in items per second. */
    public ObjectFloatMap<Item> imports = new ObjectFloatMap<>();
    /** Items stored in the sector's largest core graph. */
    public ObjectIntMap<Item> stored = new ObjectIntMap<>();
    /** Combined core storage capacity. */
    public int capacity;

    private transient ObjectFloatMap<Item> pendingProduction = new ObjectFloatMap<>();
    private transient ObjectFloatMap<Item> pendingExport = new ObjectFloatMap<>();

    /** Registers the settings serializer for sim data. Called once from Vars. */
    public static void registerSerializer(){
        Settings.setSerializer(SectorSim.class, new TypeSerializer<SectorSim>(){
            @Override
            public void write(DataOutput stream, SectorSim object) throws IOException{
                stream.writeByte(3);
                stream.writeInt(object.capacity);

                stream.writeInt(object.items.size);
                for(ObjectIntMap.Entry<Item> entry : object.items.entries()){
                    stream.writeInt(entry.key.id);
                    stream.writeInt(entry.value);
                }

                writeFloats(stream, object.production);
                writeFloats(stream, object.export);
                writeFloats(stream, object.imports);

                stream.writeInt(object.stored.size);
                for(ObjectIntMap.Entry<Item> entry : object.stored.entries()){
                    stream.writeInt(entry.key.id);
                    stream.writeInt(entry.value);
                }
            }

            @Override
            public SectorSim read(DataInput stream) throws IOException{
                int version = stream.readByte();
                SectorSim sim = new SectorSim();
                sim.capacity = stream.readInt();

                int size = stream.readInt();
                for(int i = 0; i < size; i++){
                    Item item = content.item(stream.readInt());
                    int amount = stream.readInt();
                    if(item != null && amount > 0){
                        sim.items.put(item, amount);
                    }
                }

                readFloats(stream, sim.production);
                readFloats(stream, sim.export);
                if(version >= 2){
                    readFloats(stream, sim.imports);
                }
                if(version >= 3){
                    int storedSize = stream.readInt();
                    for(int i = 0; i < storedSize; i++){
                        Item item = content.item(stream.readInt());
                        int amount = stream.readInt();
                        if(item != null && amount > 0){
                            sim.stored.put(item, amount);
                        }
                    }
                }
                sim.capacity = Math.max(sim.capacity, sim.totalItems());
                return sim;
            }
        });
    }

    private static void writeFloats(DataOutput stream, ObjectFloatMap<Item> map) throws IOException{
        stream.writeInt(map.size);
        for(ObjectFloatMap.Entry<Item> entry : map.entries()){
            stream.writeInt(entry.key.id);
            stream.writeFloat(entry.value);
        }
    }

    private static void readFloats(DataInput stream, ObjectFloatMap<Item> map) throws IOException{
        int size = stream.readInt();
        for(int i = 0; i < size; i++){
            Item item = content.item(stream.readInt());
            float value = stream.readFloat();
            if(item != null && value > 0f){
                map.put(item, value);
            }
        }
    }

    public int get(Item item){
        return items.get(item, 0);
    }

    public int totalItems(){
        int total = 0;
        for(ObjectIntMap.Entry<Item> entry : items.entries()){
            total += entry.value;
        }
        return total;
    }

    /** Adds items up to the stored capacity. Returns the amount actually added. */
    public int add(Item item, int amount){
        int added = Math.min(amount, Math.max(capacity - totalItems(), 0));
        if(added > 0){
            items.put(item, get(item) + added);
        }
        return added;
    }

    /** Copies the active sector's core contents and capacity into this simulation. */
    public void captureWorld(){
        items.clear();
        stored.clear();
        capacity = 0;

        ItemModule largest = null;
        int largestTotal = -1;

        for(Tile tile : state.teams.get(state.playerTeam).cores){
            if(tile == null || tile.entity == null || tile.entity.items == null) continue;

            capacity += tile.block().itemCapacity;
            tile.entity.items.forEach((item, amount) -> items.put(item, items.get(item, 0) + (int)amount));
            int total = tile.entity.items.total();
            if(total > largestTotal){
                largestTotal = total;
                largest = tile.entity.items;
            }
        }
        if(largest != null){
            largest.forEach((item, amount) -> {
                if(amount > 0) stored.put(item, (int)amount);
            });
        }

        capacity = Math.max(capacity, totalItems());
    }

    /** Restores the simulated core contents when a sector is loaded. */
    public void applyToWorld(){
        if(capacity <= 0) return;

        Tile target = null;

        for(Tile tile : state.teams.get(state.playerTeam).cores){
            if(tile == null || tile.entity == null || tile.entity.items == null) continue;

            tile.entity.items.clear();
            if(target == null || tile.packedPosition() < target.packedPosition()){
                target = tile;
            }
        }

        if(target == null) return;

        for(ObjectIntMap.Entry<Item> entry : items.entries()){
            target.entity.items.add(entry.key, entry.value);
        }
    }

    /** Advances background production and exports by a number of seconds.
     * Export is rate-based, so captured launch pads keep feeding the campaign pool even when their sector is not loaded. */
    public void simulate(float seconds, LaunchManager launchManager){
        if(seconds <= 0f) return;

        for(ObjectFloatMap.Entry<Item> entry : production.entries()){
            Item item = entry.key;
            float amount = entry.value * seconds + pendingProduction.get(item, 0f);
            int whole = (int)amount;

            if(whole > 0){
                add(item, whole);
                amount -= whole;
            }

            pendingProduction.put(item, Math.min(amount, 1f));
        }

        int space = Math.max(launchManager.getCapacity() - launchManager.getTotalItems(), 0);

        for(ObjectFloatMap.Entry<Item> entry : export.entries()){
            Item item = entry.key;

            if(space <= 0){
                pendingExport.put(item, 0f);
                continue;
            }

            float amount = entry.value * seconds + pendingExport.get(item, 0f);
            int whole = Math.min((int)amount, space);

            if(whole > 0){
                launchManager.addItems(item, whole);
                space -= whole;

                int stored = get(item);
                if(stored > 0){
                    if(stored <= whole){
                        items.remove(item, 0);
                    }else{
                        items.put(item, stored - whole);
                    }
                }

                amount -= whole;
            }

            pendingExport.put(item, Math.min(amount, 1f));
        }
    }
}
