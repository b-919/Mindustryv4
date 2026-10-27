package io.anuke.mindustry.world.blocks.distribution;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.LiquidBlocks;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Autotiler;
import io.anuke.mindustry.world.blocks.LiquidBlock;
import io.anuke.mindustry.world.modules.LiquidModule;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class Conduit extends LiquidBlock implements Autotiler{
    protected final int timerFlow = timers++;

    public Block junctionReplacement, bridgeReplacement;

    protected TextureRegion[] topRegions = new TextureRegion[7];
    protected TextureRegion[] botRegions = new TextureRegion[7];

    public Conduit(String name){
        super(name);
        rotate = true;
        solid = false;
        floating = true;
        reflectYdisplace = 0.25f;
    }

    @Override
    public void init(){
        super.init();
        if(junctionReplacement == null) junctionReplacement = LiquidBlocks.liquidJunction;
        if(bridgeReplacement == null) bridgeReplacement = LiquidBlocks.bridgeConduit;
    }

    @Override
    public Block getReplacement(int x, int y, int rotation, Array<int[]> plans){
        if(junctionReplacement == null) return null;

        Tile tile = world.tile(x, y);
        if(tile == null || tile.block() == Blocks.air || !(tile.block() instanceof Conduit)) return null;
        if(Mathf.mod(tile.getRotation() - rotation, 2) != 1) return null;

        if(plans != null){
            boolean hasFront = false, hasBack = false;
            for(int[] plan : plans){
                if(plan[0] == x + Geometry.d4[rotation].x && plan[1] == y + Geometry.d4[rotation].y){
                    Block b = world.tile(plan[0], plan[1]) != null ? world.tile(plan[0], plan[1]).block() : Blocks.air;
                    hasFront = b instanceof Conduit || b instanceof LiquidJunction;
                }
                if(plan[0] == x + Geometry.d4[Mathf.mod(rotation - 2, 4)].x && plan[1] == y + Geometry.d4[Mathf.mod(rotation - 2, 4)].y){
                    Block b = world.tile(plan[0], plan[1]) != null ? world.tile(plan[0], plan[1]).block() : Blocks.air;
                    hasBack = b instanceof Conduit || b instanceof LiquidJunction;
                }
            }

            return hasFront && hasBack ? junctionReplacement : null;
        }

        return null;
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
                if(tile != null && tile.block() instanceof Conduit
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
                        if(t != null && !(t.block() instanceof Conduit)){
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
        if(tile.block() instanceof Conduit && Mathf.mod(tile.getRotation() - rotation, 2) != 1) return true;
        return false;
    }

    private boolean bridgePositionsValid(int x1, int y1, int x2, int y2){
        if(x1 != x2 && y1 != y2) return false;
        int dist = Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
        return dist > 0 && dist <= 4;
    }

    @Override
    public void load(){
        super.load();

        liquidRegion = Draw.region("conduit-liquid");
        for(int i = 0; i < topRegions.length; i++){
            topRegions[i] = Draw.region(name + "-top-" + i);
            botRegions[i] = Draw.region("conduit-bottom-" + i);
        }
    }

    @Override
    public void drawShadow(Tile tile){
        ConduitEntity entity = tile.entity();

        if(entity.blendshadowrot == -1){
            super.drawShadow(tile);
        }else{
            Draw.rect("shadow-corner", tile.drawx(), tile.drawy(), (tile.getRotation() + 3 + entity.blendshadowrot) * 90);
        }
    }

    @Override
    public void onProximityUpdate(Tile tile){
        super.onProximityUpdate(tile);

        ConduitEntity entity = tile.entity();
        int[] bits = buildBlending(tile, tile.getRotation(), true);
        int mask = bits[3];
        entity.blendbits = 0;
        entity.blendshadowrot = -1;

        if((mask & ((1 << 2) | (1 << 1) | (1 << 3))) == ((1 << 2) | (1 << 1) | (1 << 3))){
            entity.blendbits = 3;
        }else if((mask & ((1 << 1) | (1 << 3))) == ((1 << 1) | (1 << 3))){
            entity.blendbits = 6;
        }else if((mask & ((1 << 1) | (1 << 2))) == ((1 << 1) | (1 << 2))){
            entity.blendbits = 2;
        }else if((mask & ((1 << 3) | (1 << 2))) == ((1 << 3) | (1 << 2))){
            entity.blendbits = 4;
        }else if((mask & (1 << 1)) != 0){
            entity.blendbits = 5;
            entity.blendshadowrot = 0;
        }else if((mask & (1 << 3)) != 0){
            entity.blendbits = 1;
            entity.blendshadowrot = 1;
        }
    }

    @Override
    public boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, io.anuke.mindustry.world.Block otherblock){
        Tile other = io.anuke.mindustry.Vars.world.tile(otherx, othery);
        return other != null && otherblock.hasLiquids && otherblock.outputsLiquid &&
            (facing(tile.x, tile.y, rotation, otherx, othery) || !otherblock.rotate || facing(otherx, othery, otherrot, tile.x, tile.y));
    }

    @Override
    public void draw(Tile tile){
        ConduitEntity entity = tile.entity();
        int rotation = tile.getRotation() * 90;

        Draw.colorl(0.34f);
        Draw.rect(botRegions[entity.blendbits], tile.drawx(), tile.drawy(), rotation);

        drawLiquid(tile);

        Draw.rect(topRegions[entity.blendbits], tile.drawx(), tile.drawy(), rotation);
    }

    @Override
    public void drawLiquid(Tile tile){
        Liquid liquid = liquidFor(tile);
        if(liquid == null){
            Draw.color();
            return;
        }

        ConduitEntity entity = tile.entity();

        Draw.color(liquid.color);
        Draw.alpha(entity.smoothLiquid);
        Draw.rect(botRegions[entity.blendbits], tile.drawx(), tile.drawy(), tile.getRotation() * 90);
        Draw.color();
    }

    @Override
    public void update(Tile tile){
        ConduitEntity entity = tile.entity();
        entity.smoothLiquid = Mathf.lerpDelta(entity.smoothLiquid, entity.liquids.total() / liquidCapacity, 0.05f);

        if(tile.entity.liquids.total() > 0.001f && tile.entity.timer.get(timerFlow, 1)){
            tryMoveLiquid(tile, tile.getNearby(tile.getRotation()), true, tile.entity.liquids.current());
            entity.noSleep();
        }else{
            entity.sleep();
        }
    }

    @Override
    public TextureRegion[] getIcon(){
        if(icon == null){
            icon = new TextureRegion[]{Draw.region("conduit-bottom"), Draw.region(name + "-top-0")};
        }
        return icon;
    }

    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        tile.entity.noSleep();
        return super.acceptLiquid(tile, source, liquid, amount) && ((2 + source.relativeTo(tile.x, tile.y)) % 4 != tile.getRotation());
    }

    @Override
    public TileEntity newEntity(){
        return new ConduitEntity();
    }

    public static class ConduitEntity extends TileEntity{
        public float smoothLiquid;

        byte blendbits;
        int blendshadowrot;

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeFloat(smoothLiquid);
        }

        @Override
        public void read(DataInput stream) throws IOException{
            smoothLiquid = stream.readFloat();
        }
    }
}
