package io.anuke.mindustry.world.blocks.distribution;

import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockGroup;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.content;

public class StackRouter extends Block{
    public float speed = 1f;

    public StackRouter(String name){
        super(name);
        update = true;
        rotate = true;
        solid = true;
        hasItems = true;
        itemCapacity = 10;
        group = BlockGroup.transportation;
        reflectYdisplace = 0.35f;
        drawCached = true;
        drawDynamic = true;
    }

    @Override
    public void update(Tile tile){
        StackRouterEntity entity = tile.entity();

        if(!entity.unloading){
            if(entity.current != null && entity.items.get(entity.current) > 0){
                entity.progress += entity.delta() / speed * 2f;
                if(entity.progress >= 1f){
                    Tile target = getTileTarget(tile, entity.current);
                    if(target != null){
                        target.block().handleItem(entity.current, target, Edges.getFacingEdge(tile, target));
                        entity.items.remove(entity.current, 1);
                        entity.progress = 0f;
                        if(entity.items.get(entity.current) == 0){
                            entity.current = null;
                        }
                    }else{
                        entity.progress = Mathf.clamp(entity.progress, 0, 0.99f);
                    }
                }
            }else{
                entity.progress = 0f;
            }
        }

        if(!entity.unloading && entity.current != null && entity.items.total() >= itemCapacity){
            entity.unloadTimer += entity.delta();
            if(entity.unloadTimer >= speed){
                entity.unloading = true;
                entity.unloadTimer = 0f;
            }
        }else if(!entity.unloading){
            entity.unloadTimer = 0f;
        }
        if(entity.unloading && entity.current != null){
            Tile target = getTileTarget(tile, entity.current);
            while(target != null && entity.items.get(entity.current) > 0){
                target.block().handleItem(entity.current, target, Edges.getFacingEdge(tile, target));
                entity.items.remove(entity.current, 1);
                target = getTileTarget(tile, entity.current);
            }

            if(entity.items.get(entity.current) == 0){
                entity.current = null;
                entity.unloading = false;
            }
        }

        if((entity.current == null || entity.items.get(entity.current) == 0) && entity.items.total() > 0){
            entity.current = firstItem(tile);
        }

        if(entity.items.total() == 0){
            entity.unloading = false;
            entity.current = null;
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        StackRouterEntity entity = tile.entity();
        if(entity.unloading) return false;
        if(entity.current != null && item != entity.current) return false;
        if(entity.items.total() >= itemCapacity) return false;
        return source.relativeTo(tile.x, tile.y) == tile.getRotation();
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        super.handleItem(item, tile, source);
        StackRouterEntity entity = tile.entity();
        entity.current = item;
        entity.progress = -1f;
        entity.noSleep();
    }

    Tile getTileTarget(Tile tile, Item item){
        if(item == null) return null;
        Array<Tile> proximity = tile.entity.proximity();
        int counter = tile.getDump();
        for(int i = 0; i < proximity.size; i++){
            Tile other = proximity.get((i + counter) % proximity.size);
            int rel = tile.relativeTo(other.x, other.y);
            if(rel == (tile.getRotation() + 2) % 4){
                tile.setDump((byte)((tile.getDump() + 1) % proximity.size));
                continue;
            }
            if(other.block().acceptItem(item, other, Edges.getFacingEdge(tile, other))){
                tile.setDump((byte)((tile.getDump() + 1) % proximity.size));
                return other;
            }
            tile.setDump((byte)((tile.getDump() + 1) % proximity.size));
        }
        return null;
    }

    Item firstItem(Tile tile){
        for(int i = 0; i < content.items().size; i++){
            Item item = content.item(i);
            if(tile.entity.items.has(item)) return item;
        }
        return null;
    }

    @Override
    public void draw(Tile tile){
        drawCached(tile);
        drawDynamic(tile);
    }

    @Override
    public void drawCached(Tile tile){
        super.draw(tile);
    }

    @Override
    public void drawDynamic(Tile tile){
        StackRouterEntity entity = tile.entity();
        if(entity.current != null){
            float fill = (float)entity.items.total() / itemCapacity;
            Draw.color(entity.current.color);
            Draw.rect("blank", tile.worldx(), tile.worldy(), 2f + fill * 4f, 2f + fill * 4f);
            Draw.color();
        }
    }

    @Override
    public TileEntity newEntity(){
        return new StackRouterEntity();
    }

    public static class StackRouterEntity extends TileEntity{
        public Item current;
        public boolean unloading;
        public float progress;
        public float unloadTimer;

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeByte(current == null ? -1 : current.id);
            stream.writeBoolean(unloading);
            stream.writeFloat(progress);
            stream.writeFloat(unloadTimer);
        }

        @Override
        public void read(DataInput stream) throws IOException{
            byte id = stream.readByte();
            current = id == -1 ? null : content.item(id & 0xFF);
            unloading = stream.readBoolean();
            progress = stream.readFloat();
            unloadTimer = stream.readFloat();
        }
    }
}
