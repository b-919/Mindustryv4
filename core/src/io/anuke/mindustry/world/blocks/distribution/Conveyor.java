package io.anuke.mindustry.world.blocks.distribution;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.graphics.Layer;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Autotiler;
import io.anuke.mindustry.world.meta.BlockGroup;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class Conveyor extends Block implements Autotiler{
    public static final float itemSpace = 0.135f * 2.2f;
    public static final float offsetScl = 128f * 3f;
    public static final float minmove = 1f / (Short.MAX_VALUE - 2);
    public static ItemPos drawpos = new ItemPos();
    public static ItemPos pos1 = new ItemPos();
    public static ItemPos pos2 = new ItemPos();
    public final Translator tr1 = new Translator();
    public final Translator tr2 = new Translator();
    public Block junctionReplacement, bridgeReplacement;

    private TextureRegion[][] regions = new TextureRegion[7][4];
    public int animationframe = 4;

    protected float speed = 0f;
    protected float carryCapacity = 8f;

    protected Conveyor(String name){
        super(name);
        rotate = true;
        update = true;
        layer = Layer.overlay;
        group = BlockGroup.transportation;
        hasItems = true;
        autoSleep = true;
        itemCapacity = 4;
        noSideBlend = false;
        reflectYdisplace = 0.25f;
        setAmbientSound("loopConveyor", 0.03f, 4);
    }

    @Override
    public boolean canUnload(Tile tile, Item item){
        return false;
    }

    @Override
    public void init(){
        super.init();
        if(junctionReplacement == null) junctionReplacement = DistributionBlocks.junction;
        if(bridgeReplacement == null) bridgeReplacement = DistributionBlocks.itemBridge;
    }

    @Override
    public Block getReplacement(int x, int y, int rotation){
        return getReplacement(x, y, rotation, null);
    }

    @Override
    public Block getReplacement(int x, int y, int rotation, Array<int[]> plans){
        if(junctionReplacement == null) return null;

        Tile tile = world.tile(x, y);
        if(tile == null || tile.block() == Blocks.air || !(tile.block() instanceof Conveyor)) return null;
        if(Mathf.mod(tile.getRotation() - rotation, 2) != 1) return null;

        if(plans != null){
            boolean hasFront = false, hasBack = false;
            for(int[] plan : plans){
                if(plan[0] == x + Geometry.d4[rotation].x && plan[1] == y + Geometry.d4[rotation].y){
                    Block b = getBlockAtPlan(plan);
                    hasFront = b instanceof Conveyor || b instanceof Junction;
                }
                if(plan[0] == x + Geometry.d4[Mathf.mod(rotation - 2, 4)].x && plan[1] == y + Geometry.d4[Mathf.mod(rotation - 2, 4)].y){
                    Block b = getBlockAtPlan(plan);
                    hasBack = b instanceof Conveyor || b instanceof Junction;
                }
            }

            return hasFront && hasBack ? junctionReplacement : null;
        }

        return null;
    }

    private Block getBlockAtPlan(int[] plan){
        Tile tile = world.tile(plan[0], plan[1]);
        if(tile == null) return Blocks.air;
        return tile.block();
    }

    @Override
    public void handlePlacementLine(Array<int[]> plans){
        if(plans.size == 0) return;

        boolean hasJunction = junctionReplacement != null;

        if(plans.size > 1){
            int[] first = plans.get(0);
            int[] second = plans.get(1);
            int dx = Math.abs(first[0] - second[0]);
            int dy = Math.abs(first[1] - second[1]);
            boolean isHorizontal = dx > dy;
            boolean rotIsHorizontal = first[2] % 2 == 0;
            if(isHorizontal != rotIsHorizontal) return;
        }

        int startX = plans.get(0)[0], startY = plans.get(0)[1];
        int endX = plans.get(plans.size - 1)[0], endY = plans.get(plans.size - 1)[1];
        if(startX != endX && startY != endY) return;

        if(hasJunction){
            for(int i = 0; i < plans.size; i++){
                int[] plan = plans.get(i);
                Tile tile = world.tile(plan[0], plan[1]);
                if(tile != null && tile.block() instanceof Conveyor
                    && Mathf.mod(tile.getRotation() - plan[2], 2) == 1){
                    int frontX = plan[0] + Geometry.d4[plan[2]].x;
                    int frontY = plan[1] + Geometry.d4[plan[2]].y;
                    int backX = plan[0] - Geometry.d4[plan[2]].x;
                    int backY = plan[1] - Geometry.d4[plan[2]].y;
                    boolean hasFront = false, hasBack = false;
                    for(int j = 0; j < plans.size; j++){
                        int[] other = plans.get(j);
                        if(other[0] == frontX && other[1] == frontY) hasFront = true;
                        if(other[0] == backX && other[1] == backY) hasBack = true;
                    }
                    if(hasFront && hasBack){
                        plan[3] = -2;
                    }
                }
            }
        }

        if(bridgeReplacement != null){
            Array<int[]> result = new Array<>();

            for(int i = 0; i < plans.size;){
                int[] cur = plans.get(i);
                result.add(cur);

                boolean curPlaceable = cur[3] == -2 || isPlanPlaceable(cur);

                if(i < plans.size - 1 && curPlaceable && cur[3] != -2 && !isPlanPlaceable(plans.get(i + 1))){
                    boolean wereSame = true;

                    for(int j = i + 1; j < plans.size; j++){
                        int[] other = plans.get(j);

                        if(other[3] == -2){
                            for(int k = i + 1; k <= j; k++){
                                if(k < plans.size) result.add(plans.get(k));
                            }
                            i = j + 1;
                            break;
                        }

                        if(!bridgePositionsValid(cur[0], cur[1], other[0], other[1])){
                            for(int k = i + 1; k < j; k++){
                                result.add(plans.get(k));
                            }
                            i = j;
                            break;
                        }else if(isPlanPlaceable(other)){
                            if(wereSame){
                                i++;
                                break;
                            }else{
                                cur[3] = -1;
                                other[3] = -1;
                                i = j;
                                break;
                            }
                        }

                        Tile t = world.tile(other[0], other[1]);
                        if(t != null && !(t.block() instanceof Conveyor)){
                            wereSame = false;
                        }

                        if(j == plans.size - 1){
                            for(int k = i + 1; k <= j; k++){
                                if(k < plans.size) result.add(plans.get(k));
                            }
                            i = plans.size;
                            break;
                        }
                    }

                    if(i == plans.size) break;
                    continue;
                }else{
                    i++;
                }
            }

            plans.clear();
            plans.addAll(result);
        }
    }

    private boolean isPlanPlaceable(int[] plan){
        Tile tile = world.tile(plan[0], plan[1]);
        if(tile == null) return false;

        if(tile.hasCliffs() || !tile.floor().placeableOn) return false;

        int rotation = plan[2];

        if(tile.block() == Blocks.air || tile.block().alwaysReplace) return true;
        if(tile.block() instanceof Conveyor && Mathf.mod(tile.getRotation() - rotation, 2) != 1) return true;
        return false;
    }

    private static int compareItems(long a, long b){
        pos1.set(a, ItemPos.packShorts);
        pos2.set(b, ItemPos.packShorts);
        return Float.compare(pos1.y, pos2.y);
    }

    private boolean bridgePositionsValid(int x1, int y1, int x2, int y2){
        if(x1 != x2 && y1 != y2) return false;
        int dist = Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
        return dist > 0 && dist <= 4;
    }

    @Override
    public void setBars(){}

    @Override
    public void setStats(){
        super.setStats();
        stats.add(BlockStat.itemSpeed, speed * 60, StatUnit.pixelsSecond);
    }

    @Override
    public void load(){
        super.load();
        for(int i = 0; i < regions.length; i++){
            for(int j = 0; j < 4; j++){
                regions[i][j] = Draw.region(name + "-" + i + "-" + j);
            }
        }
    }

    @Override
    public void drawShadow(Tile tile){
        //fixes build block crash
        if(!(tile.entity instanceof ConveyorEntity)){
            super.drawShadow(tile);
            return;
        }

        ConveyorEntity entity = tile.entity();

        if(entity.blendshadowrot == -1){
            super.drawShadow(tile);
        }else{
            Draw.rect("shadow-corner", tile.drawx(), tile.drawy(), (tile.getRotation() + 3 + entity.blendshadowrot) * 90);
        }
    }

    @Override
    public void draw(Tile tile){
        ConveyorEntity entity = tile.entity();
        byte rotation = tile.getRotation();

        int frame = entity.clogHeat <= 0.5f ? (int) (((Timers.time() * speed * 8f * entity.timeScale)) % animationframe) : 0;
        Draw.rect(regions[Mathf.clamp(entity.blendbits, 0, regions.length - 1)][Mathf.clamp(frame, 0, animationframe - 1)], tile.drawx(), tile.drawy(),
            tilesize * entity.blendsclx, tilesize * entity.blendscly, rotation*90);
    }

    @Override
    public void onProximityUpdate(Tile tile){
        super.onProximityUpdate(tile);

        ConveyorEntity entity = tile.entity();
        int[] bits = buildBlending(tile, tile.getRotation(), true);
        entity.blendbits = bits[0];
        entity.blendsclx = bits[1];
        entity.blendscly = bits[2];
        entity.blendshadowrot = -1;

        if((bits[3] & (1 << 1)) != 0 && (bits[3] & (1 << 3)) == 0){
            entity.blendshadowrot = 0;
        }else if((bits[3] & (1 << 3)) != 0 && (bits[3] & (1 << 1)) == 0){
            entity.blendshadowrot = 1;
        }
    }

    @Override
    public boolean canReplace(Block other){
        return super.canReplace(other) && !(other instanceof StackConveyor);
    }

    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock){
        Tile other = world.tile(otherx, othery);
        return other != null && otherblock.outputsItems()
            && (facing(tile.x, tile.y, rotation, otherx, othery) || !otherblock.rotate || facing(otherx, othery, otherrot, tile.x, tile.y));
    }

    public boolean blends(Tile tile, int direction){
        return Autotiler.super.blends(tile, tile.getRotation(), direction);
    }

    @Override
    public TextureRegion[] getIcon(){
        if(icon == null){
            icon = new TextureRegion[]{Draw.region(name + "-0-0")};
        }
        return super.getIcon();
    }

    @Override
    public void drawLayer(Tile tile){
        ConveyorEntity entity = tile.entity();

        byte rotation = tile.getRotation();

        try{

            for(int i = 0; i < entity.convey.size; i++){
                ItemPos pos = drawpos.set(entity.convey.get(i), ItemPos.drawShorts);

                if(pos.item == null) continue;

                tr1.trns(rotation * 90, tilesize, 0);
                tr2.trns(rotation * 90, -tilesize / 2f, pos.x * tilesize / 2f);

                Draw.rect(pos.item.region,
                        (int) (tile.x * tilesize + tr1.x * pos.y + tr2.x),
                        (int) (tile.y * tilesize + tr1.y * pos.y + tr2.y), itemSize, itemSize);
            }

        }catch(IndexOutOfBoundsException e){
            Log.err(e);
        }
    }

    @Override
    public void unitOn(Tile tile, Unit unit){
        ConveyorEntity entity = tile.entity();

        entity.noSleep();

        float speed = this.speed * tilesize / 2.3f;
        float centerSpeed = 0.1f;
        float centerDstScl = 3f;
        float tx = Geometry.d4[tile.getRotation()].x, ty = Geometry.d4[tile.getRotation()].y;

        float centerx = 0f, centery = 0f;

        if(Math.abs(tx) > Math.abs(ty)){
            centery = Mathf.clamp((tile.worldy() - unit.y) / centerDstScl, -centerSpeed, centerSpeed);
            if(Math.abs(tile.worldy() - unit.y) < 1f) centery = 0f;
        }else{
            centerx = Mathf.clamp((tile.worldx() - unit.x) / centerDstScl, -centerSpeed, centerSpeed);
            if(Math.abs(tile.worldx() - unit.x) < 1f) centerx = 0f;
        }

        if(entity.convey.size * itemSpace < 0.9f){
            unit.getVelocity().add((tx * speed + centerx) * entity.delta(), (ty * speed + centery) * entity.delta());
        }
    }

    @Override
    public void update(Tile tile){
        ConveyorEntity entity = tile.entity();
        entity.minitem = 1f;
        int minremove = Integer.MAX_VALUE;

        for(int i = entity.convey.size - 1; i >= 0; i--){
            long value = entity.convey.get(i);
            ItemPos pos = pos1.set(value, ItemPos.updateShorts);

            //..this should never happen, but in case it does, remove it and stop here
            if(pos.item == null){
                entity.convey.removeValue(value);
                break;
            }

            float nextpos = (i == entity.convey.size - 1 ? 100f : pos2.set(entity.convey.get(i + 1), ItemPos.updateShorts).y) - itemSpace;
            float maxmove = Math.min(nextpos - pos.y, speed * entity.delta());

            if(maxmove > minmove){
                pos.y += maxmove;
                if(Mathf.in(pos.x, 0, 0.1f)){
                    pos.x = 0f;
                }
                pos.x = Mathf.lerpDelta(pos.x, 0, 0.1f);
            }else{
                pos.x = Mathf.lerpDelta(pos.x, pos.seed / offsetScl, 0.1f);
            }

            pos.y = Mathf.clamp(pos.y);

            if(pos.y >= 0.9999f && offloadDir(tile, pos.item)){
                Tile next = tile.getNearby(tile.getRotation());
                if(next.block() instanceof Conveyor){
                    ConveyorEntity othere = next.entity();

                    ItemPos ni = pos2.set(othere.convey.get(othere.lastInserted), ItemPos.updateShorts);

                    if(next.getRotation() == tile.getRotation()){
                        ni.x = pos.x;
                    }
                    othere.convey.set(othere.lastInserted, ni.pack());
                }
                minremove = Math.min(i, minremove);
                tile.entity.items.remove(pos.item, 1);
            }else{
                value = pos.pack();

                if(pos.y < entity.minitem)
                    entity.minitem = pos.y;
                entity.convey.set(i, value);
            }
        }

        if(entity.minitem < itemSpace){
            entity.clogHeat = Mathf.lerpDelta(entity.clogHeat, 1f, 0.02f);
            entity.ambientSoundEnabled = false;
        }else{
            entity.clogHeat = Mathf.lerpDelta(entity.clogHeat, 0f, 1f);
            entity.ambientSoundEnabled = true;
        }

        if(entity.items.total() == 0){
            entity.sleep();

        }else{
            entity.noSleep();
        }
        entity.ambientSoundEnabled = true;
        if(minremove != Integer.MAX_VALUE) entity.convey.truncate(minremove);
    }



    @Override
    public boolean isAccessible(){
        return true;
    }

    @Override
    public int removeStack(Tile tile, Item item, int amount){
        ConveyorEntity entity = tile.entity();
        entity.noSleep();
        int removed = 0;

        for(int j = 0; j < amount; j++){
            for(int i = 0; i < entity.convey.size; i++){
                long val = entity.convey.get(i);
                ItemPos pos = pos1.set(val, ItemPos.drawShorts);
                if(pos.item == item){
                    entity.convey.removeValue(val);
                    entity.items.remove(item, 1);
                    removed++;
                    break;
                }
            }
        }
        return removed;
    }

    @Override
    public void getStackOffset(Item item, Tile tile, Translator trns){
        trns.trns(tile.getRotation() * 90 + 180f, tilesize / 2f);
    }

    @Override
    public int acceptStack(Item item, int amount, Tile tile, Unit source){
        ConveyorEntity entity = tile.entity();
        return Math.min((int)(entity.minitem / itemSpace), amount);
    }

    @Override
    public void handleStack(Item item, int amount, Tile tile, Unit source){
        ConveyorEntity entity = tile.entity();

        for(int i = amount - 1; i >= 0; i--){
            long result = ItemPos.packItem(item, 0f, i * itemSpace, (byte) Mathf.random(255));
            entity.convey.insert(0, result);
            entity.items.add(item, 1);
        }

        entity.noSleep();
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        int direction = source == null ? 0 : Math.abs(source.relativeTo(tile.x, tile.y) - tile.getRotation());
        float minitem = tile.<ConveyorEntity>entity().minitem;
        return (((direction == 0) && minitem > itemSpace) ||
                ((direction % 2 == 1) && minitem > 0.52f)) && (source == null || !(source.block().rotate && (source.getRotation() + 2) % 4 == tile.getRotation()));
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        byte rotation = tile.getRotation();

        int ch = Math.abs(source.relativeTo(tile.x, tile.y) - rotation);
        int ang = ((source.relativeTo(tile.x, tile.y) - rotation));

        float pos = ch == 0 ? 0 : ch % 2 == 1 ? 0.5f : 1f;
        float y = (ang == -1 || ang == 3) ? 1 : (ang == 1 || ang == -3) ? -1 : 0;

        ConveyorEntity entity = tile.entity();
        entity.noSleep();
        long result = ItemPos.packItem(item, y * 0.9f, pos, (byte) Mathf.random(255));

        tile.entity.items.add(item, 1);

        for(int i = 0; i < entity.convey.size; i++){
            if(compareItems(result, entity.convey.get(i)) < 0){
                entity.convey.insert(i, result);
                entity.lastInserted = (byte)i;
                return;
            }
        }

        //this item must be greater than anything there...
        entity.convey.add(result);
        entity.lastInserted = (byte)(entity.convey.size-1);
    }

    @Override
    public Array<Object> getDebugInfo(Tile tile){
        ConveyorEntity entity = tile.entity();
        Array<Object> arr = super.getDebugInfo(tile);
        arr.addAll(Array.with(
                "clogHeat", entity.clogHeat,
                "sleeping", entity.isSleeping()
        ));
        return arr;
    }

    @Override
    public void removed(Tile tile){
        super.removed(tile);
    }
    @Override
    public void placed(Tile tile){
        ConveyorEntity entity = tile.entity();
        entity.ambientSoundEnabled = true;
        super.placed(tile);
    }

    @Override
    public boolean shouldPlayAmbientSoundCondition(Tile tile){
        return tile.entity != null;
    }

    @Override
    public TileEntity newEntity(){
        return new ConveyorEntity();
    }
    public static class ConveyorEntity extends TileEntity{

        LongArray convey = new LongArray();
        byte lastInserted;
        float minitem = 1;

        int blendshadowrot = -1;
        int blendbits;
        int blendsclx, blendscly;

        float clogHeat = 0f;

        @Override
        public void onDeath(){
            super.onDeath();
        }

        @Override
        public void removed(){
            super.removed();
        }

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeInt(convey.size);

            for(int i = 0; i < convey.size; i++){
                stream.writeInt(ItemPos.toInt(convey.get(i)));
            }
        }

        @Override
        public void read(DataInput stream) throws IOException{
            convey.clear();
            int amount = stream.readInt();
            convey.ensureCapacity(Math.min(amount, 10));

            for(int i = 0; i < amount; i++){
                convey.add(ItemPos.toLong(stream.readInt()));
            }
        }
    }

    //Container class. Do not instantiate.
    static class ItemPos{
        public static short[] writeShort = new short[4];
        public static byte[] writeByte = new byte[4];

        public static short[] packShorts = new short[4];
        public static short[] drawShorts = new short[4];
        public static short[] updateShorts = new short[4];

        Item item;
        float x, y;
        byte seed;

        private ItemPos(){
        }

        static long packItem(Item item, float x, float y, byte seed){
            short[] shorts = packShorts;
            shorts[0] = (short) item.id;
            shorts[1] = (short) (x * Short.MAX_VALUE);
            shorts[2] = (short) ((y - 1f) * Short.MAX_VALUE);
            shorts[3] = seed;
            return Bits.packLong(shorts);
        }

        static int toInt(long value){
            short[] values = Bits.getShorts(value, writeShort);

            short itemid = values[0];
            float x = values[1] / (float) Short.MAX_VALUE;
            float y = ((float) values[2]) / Short.MAX_VALUE + 1f;
            byte seed = (byte) values[3];

            byte[] bytes = writeByte;
            bytes[0] = (byte) itemid;
            bytes[1] = (byte) (x * 127);
            bytes[2] = (byte) (y * 255 - 128);
            bytes[3] = seed;

            return Bits.packInt(bytes);
        }

        static long toLong(int value){
            byte[] values = Bits.getBytes(value, writeByte);

            byte itemid = values[0];
            float x = values[1] / 127f;
            float y = ((int) values[2] + 128) / 255f;
            byte seed = values[3];

            short[] shorts = writeShort;
            shorts[0] = (short) itemid;
            shorts[1] = (short) (x * Short.MAX_VALUE);
            shorts[2] = (short) ((y - 1f) * Short.MAX_VALUE);
            shorts[3] = seed;
            return Bits.packLong(shorts);
        }

        ItemPos set(long lvalue, short[] values){
            Bits.getShorts(lvalue, values);

            if(values[0] >= content.items().size || values[0] < 0)
                item = null;
            else
                item = content.items().get(values[0]);

            x = values[1] / (float) Short.MAX_VALUE;
            y = ((float) values[2]) / Short.MAX_VALUE + 1f;
            seed = (byte) values[3];
            return this;
        }

        long pack(){
            return packItem(item, x, y, seed);
        }
    }
}
