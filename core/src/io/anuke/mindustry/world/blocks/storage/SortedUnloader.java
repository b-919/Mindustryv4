package io.anuke.mindustry.world.blocks.storage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.SelectionTrait;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.scene.ui.layout.Table;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.content;

public class SortedUnloader extends Unloader implements SelectionTrait{
    protected float speed = 1f;

    public SortedUnloader(String name){
        super(name);
        configurable = true;
        drawCached = true;
        drawDynamic = true;
    }

    @Remote(targets = Loc.both, called = Loc.both, forward = true)
    public static void setSortedUnloaderItem(Player player, Tile tile, Item item){
        SortedUnloaderEntity entity = tile.entity();
        entity.sortItem = item;
    }

    public boolean canUnload(Tile tile, Item item){
        return canUnload(tile, item, true);
    }

    private boolean canUnload(Tile tile, Item item, boolean checkNeighbors){
        boolean hasProvider = false;
        boolean hasReceiver = false;

        Array<Tile> proximity = tile.entity.proximity();
        for(int i = 0; i < proximity.size; i++){
            Tile other = proximity.get(i);
            if(other.getTeam() != tile.getTeam()) continue;
            Tile in = Edges.getFacingEdge(tile, other);
            boolean canLoad = other.block().acceptItem(item, other, in) && canDump(tile, other, item);
            boolean canUnload = false;
            if(other.block() instanceof SortedUnloader){
                if(checkNeighbors){
                    canUnload = ((SortedUnloader)other.block()).canUnload(other, item, false);
                }
            }else{
                canUnload = other.block().canUnload(other, item);
            }
            hasProvider |= canUnload;
            hasReceiver |= canLoad;
            if(hasProvider && hasReceiver) return true;
        }
        return false;
    }

    @Override
    public void update(Tile tile){
        SortedUnloaderEntity entity = tile.entity();

        entity.unloadTimer += entity.delta();
        if(entity.unloadTimer < speed) return;

        Item item = null;

        if(entity.sortItem != null){
            if(canUnload(tile, entity.sortItem)){
                item = entity.sortItem;
            }
        }else{
            for(int i = 0; i < content.items().size; i++){
                int id = (entity.rotations + i + 1) % content.items().size;
                Item possible = content.items().get(id);
                if(canUnload(tile, possible)){
                    item = possible;
                    break;
                }
            }
        }

        if(item != null){
            entity.rotations = item.id;
            Tile source = null;
            Tile dest = null;

            Array<Tile> proximity = tile.entity.proximity();
            for(int i = 0; i < proximity.size; i++){
                Tile other = proximity.get(i);
                if(other.getTeam() != tile.getTeam()) continue;
                if(source == null && other.block().canUnload(other, item)){
                    source = other;
                }
                Tile in = Edges.getFacingEdge(tile, other);
                if(dest == null && other.block().acceptItem(item, other, in) && canDump(tile, other, item)){
                    dest = other;
                }
                if(source != null && dest != null) break;
            }

            if(source != null && dest != null && source != dest){
                source.entity.items.remove(item, 1);
                dest.block().handleItem(item, dest, Edges.getFacingEdge(tile, dest));
                entity.unloadTimer -= speed;
            }else{
                entity.unloadTimer = Math.min(entity.unloadTimer, speed);
            }
        }else{
            entity.unloadTimer = Math.min(entity.unloadTimer, speed);
        }
    }

    @Override
    public void draw(Tile tile){
        drawCached(tile);
        drawDynamic(tile);
    }

    @Override
    public void drawDynamic(Tile tile){
        SortedUnloaderEntity entity = tile.entity();

        Draw.color(entity.sortItem == null ? Color.WHITE : entity.sortItem.color);
        Draw.rect("blank", tile.worldx(), tile.worldy(), 2f, 2f);
        Draw.color();
    }

    @Override
    public void buildTable(Tile tile, Table table){
        SortedUnloaderEntity entity = tile.entity();
        buildItemTable(table, true, () -> entity.sortItem, item -> Call.setSortedUnloaderItem(null, tile, item));
    }

    @Override
    public TileEntity newEntity(){
        return new SortedUnloaderEntity();
    }

    public static class SortedUnloaderEntity extends TileEntity{
        public Item sortItem = null;
        public float unloadTimer = 0f;
        public int rotations = 0;

        @Override
        public void writeConfig(DataOutput stream) throws IOException{
            stream.writeByte(sortItem == null ? -1 : sortItem.id);
        }

        @Override
        public void readConfig(DataInput stream) throws IOException{
            byte id = stream.readByte();
            sortItem = id == -1 ? null : content.items().get(id);
        }

        @Override
        public Object config(){
            return sortItem;
        }

        @Override
        public void configured(Object config){
            if(config instanceof Item){
                sortItem = (Item)config;
            }else if(config == null){
                sortItem = null;
            }
        }
    }
}
