package io.anuke.mindustry.world.blocks.distribution;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Autotiler;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.mindustry.world.meta.BlockGroup;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.mindustry.graphics.Layer;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class StackConveyor extends Block implements Autotiler{
    protected static final int stateMove = 0, stateLoad = 1, stateUnload = 2;

    protected TextureRegion[] regions = new TextureRegion[5];
    protected TextureRegion baseRegion;
    protected TextureRegion stackRegion;

    public float baseEfficiency = 0f;
    public float speed = 0.03f;
    public boolean outputRouter = true;
    public float recharge = 2f;

    public StackConveyor(String name){
        super(name);
        rotate = true;
        update = true;
        hasItems = true;
        itemCapacity = 10;
        group = BlockGroup.transportation;
        layer = Layer.overlay;
        ambientSound = Sounds.loopConveyor;
        ambientSoundVolume = 0.004f;
        reflectYdisplace = 0.25f;
        drawCached = true;
        drawDynamic = false;
    }

    @Override
    public boolean canUnload(Tile tile, Item item){
        StackConveyorEntity e = tile.entity();
        return e.state != stateLoad && e.items.has(item);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(BlockStat.itemSpeed, itemCapacity * speed * 60f, StatUnit.itemsSecond);
    }

    @Override
    public void load(){
        super.load();
        baseRegion = Draw.region(name + "-0", Draw.region("clear"));
        for(int i = 0; i < regions.length; i++){
            regions[i] = Draw.region(name + "-" + i, baseRegion);
        }
        stackRegion = Draw.region(name + "-stack", Draw.region("clear"));
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock){
        Tile other = world.tile(otherx, othery);
        if(other == null) return false;

        return otherblock.outputsItems()
            && (facing(tile.x, tile.y, rotation, otherx, othery) || !otherblock.rotate || facing(otherx, othery, otherrot, tile.x, tile.y));
    }

    @Override
    public void onProximityUpdate(Tile tile){
        super.onProximityUpdate(tile);
        StackConveyorEntity e = tile.entity();
        int lastState = e.state;

        e.state = stateMove;
        Tile front = tile.getNearby(tile.getRotation());
        if(front != null) front = front.target();
        Tile back = tile.getNearby((tile.getRotation() + 2) % 4);
        if(back != null) back = back.target();

        boolean frontStack = front != null && front.block() instanceof StackConveyor;
        boolean backStack = back != null && back.block() instanceof StackConveyor;
        boolean backUnload = backStack && back.<StackConveyorEntity>entity().state == stateUnload;

        if(frontStack && (!backStack || backUnload)){
            e.state = stateLoad;
        }
        if(outputRouter && !frontStack){
            e.state = stateUnload;
        }
        if(!outputRouter && !frontStack){
            e.state = stateUnload;
        }

        int[] bits = buildBlending(tile, tile.getRotation(), true);
        e.blendbits = bits[0];
        e.blendsclx = bits[1];
        e.blendscly = bits[2];

        if(e.state == stateLoad){
            Array<Tile> proximity = tile.entity.proximity();
            for(int i = 0; i < proximity.size; i++){
                Tile near = proximity.get(i);
                if(near.block() instanceof StackConveyor
                    && near.relativeTo(tile.x, tile.y) == near.getRotation()){
                    e.state = stateMove;
                    break;
                }
            }
        }

        if(lastState != e.state){
            Array<Tile> proximity = tile.entity.proximity();
            for(int i = 0; i < proximity.size; i++){
                Tile near = proximity.get(i);
                near.block().onProximityUpdate(near);
            }
        }
        recache(tile);
    }

    @Override
    public void draw(Tile tile){
        StackConveyorEntity e = tile.entity();
        TextureRegion reg = regions[Mathf.clamp(e.blendbits, 0, regions.length - 1)];
        Draw.rect(reg, tile.drawx(), tile.drawy(), tilesize * e.blendsclx, tilesize * e.blendscly, tile.getRotation() * 90f);
    }

    @Override
    public void drawLayer(Tile tile){
        StackConveyorEntity e = tile.entity();
        Tile from = world.tile(e.link);
        Item item = e.lastItem >= 0 && e.lastItem < content.items().size ? content.item(e.lastItem) : null;
        if(from != null && item != null){
            float progress = recharge <= 0.0001f ? 1f : Mathf.clamp(1f - e.cooldown / recharge);
            float tx = Mathf.lerp(from.drawx(), tile.drawx(), progress);
            float ty = Mathf.lerp(from.drawy(), tile.drawy(), progress);
            Draw.rect(stackRegion, tx, ty, tile.getRotation() * 90f);
            Draw.rect(item.region, tx, ty, itemSize, itemSize);
        }
    }

    @Override
    public void update(Tile tile){
        StackConveyorEntity e = tile.entity();
        float eff = enabled(tile) ? (1f + baseEfficiency) : 1f;

        if(e.cooldown > 0f){
            e.cooldown = Mathf.clamp(e.cooldown - speed * eff * e.delta(), 0f, recharge);
        }

        if(e.link == -1 || e.cooldown > 0f) return;
        if(e.lastItem < 0 || e.lastItem >= content.items().size || !e.items.has(content.item(e.lastItem))){
            Item first = firstItem(e.items);
            e.lastItem = first == null ? -1 : first.id;
        }
        if(e.lastItem == -1) return;
        if(e.items.total() < itemCapacity) return;

        Item item = content.item(e.lastItem);

        if(e.state == stateUnload){
            while(tryDumpStack(tile, item, e)){
                if(!e.items.has(item)){
                    e.lastItem = -1;
                    e.link = -1;
                    Effects.effect(Fx.placeBlock, tile.drawx(), tile.drawy(), size);
                    break;
                }
            }
        }else{
            Tile front = tile.getNearby(tile.getRotation());
            if(front != null) front = front.target();
            if(front != null && front.block() instanceof StackConveyor){
                StackConveyorEntity fe = front.entity();
                if(front.getTeamID() == tile.getTeamID() && fe.link == -1 && (!fe.items.has(item) || fe.items.total() == 0) && fe.items.total() + e.items.total() <= front.block().itemCapacity){
                    fe.items.addAll(e.items);
                    fe.lastItem = e.lastItem;
                    fe.link = tile.packedPosition();
                    fe.cooldown = recharge;
                    e.items.clear();
                    e.link = -1;
                    e.lastItem = -1;
                    e.cooldown = recharge;
                }
            }
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        StackConveyorEntity e = tile.entity();
        if(source == tile) return e.items.total() < itemCapacity && (!e.items.has(item) || e.items.total() == 0);
        if(e.cooldown > recharge - 1f) return false;
        if(e.items.total() >= itemCapacity) return false;
        if(e.items.total() > 0 && !e.items.has(item)) return false;
        int dir = source.relativeTo(tile.x, tile.y);
        if(dir == -1) return false;
        return dir != tile.getRotation();
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        StackConveyorEntity e = tile.entity();
        if(e.items.total() == 0){
            e.link = source == null ? tile.packedPosition() : source.packedPosition();
            e.cooldown = recharge;
            Effects.effect(Fx.placeBlock, tile.drawx(), tile.drawy(), size);
        }
        super.handleItem(item, tile, source);
        e.lastItem = item.id;
    }

    @Override
    public int acceptStack(Item item, int amount, Tile tile, io.anuke.mindustry.entities.Unit source){
        StackConveyorEntity e = tile.entity();
        if(e.items.total() > 0 && !e.items.has(item)) return 0;
        return super.acceptStack(item, amount, tile, source);
    }

    @Override
    public int removeStack(Tile tile, Item item, int amount){
        StackConveyorEntity e = tile.entity();
        int removed = super.removeStack(tile, item, amount);
        if(e.items.total() == 0){
            e.link = -1;
            e.lastItem = -1;
        }
        return removed;
    }

    protected boolean enabled(Tile tile){
        return tile.entity != null && (tile.entity.cons == null || tile.entity.cons.valid());
    }

    protected Item firstItem(ItemModule items){
        for(int i = 0; i < content.items().size; i++){
            Item item = content.item(i);
            if(items.has(item)) return item;
        }
        return null;
    }

    protected boolean tryDumpStack(Tile tile, Item item, StackConveyorEntity e){
        if(e.items.get(item) <= 0) return false;

        Tile other = tile.getNearby(tile.getRotation());
        if(other != null) other = other.target();
        if(other == null) return false;

        Tile in = Edges.getFacingEdge(tile, other);
        if(other.getTeamID() == tile.getTeamID() && other.block().acceptItem(item, other, in) && canDump(tile, other, item)){
            other.block().handleItem(item, other, in);
            e.items.remove(item, 1);
            return true;
        }
        return false;
    }

    @Override
    public TileEntity newEntity(){
        return new StackConveyorEntity();
    }

    public static class StackConveyorEntity extends TileEntity{
        public int state = stateMove;
        public int blendbits;
        public int blendsclx = 1, blendscly = 1;
        public long link = -1;
        public float cooldown;
        public int lastItem = -1;

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeInt(state);
            stream.writeInt(blendbits);
            stream.writeInt(blendsclx);
            stream.writeInt(blendscly);
            stream.writeLong(link);
            stream.writeFloat(cooldown);
            stream.writeInt(lastItem);
        }

        @Override
        public void read(DataInput stream) throws IOException{
            state = stream.readInt();
            blendbits = stream.readInt();
            blendsclx = stream.readInt();
            blendscly = stream.readInt();
            link = stream.readLong();
            cooldown = stream.readFloat();
            lastItem = stream.readInt();
        }
    }
}
