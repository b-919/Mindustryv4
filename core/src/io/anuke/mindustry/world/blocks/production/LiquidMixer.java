package io.anuke.mindustry.world.blocks.production;

import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.BarType;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.LiquidBlock;
import io.anuke.mindustry.world.consumers.ConsumeLiquid;
import io.anuke.mindustry.world.meta.BlockBar;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.mindustry.world.modules.LiquidModule;
import io.anuke.ucore.graphics.Draw;

public class LiquidMixer extends LiquidBlock{
    protected Liquid outputLiquid;
    protected float liquidPerItem = 50f;

    public LiquidMixer(String name){
        super(name);
        hasItems = true;
        rotate = false;
        solid = true;
        singleLiquid = false;
        outputsLiquid = true;
        drawCached = true;
        drawDynamic = true;
    }

    @Override
    public void init(){
        super.init();

        produces.set(outputLiquid);
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.liquidOutput, outputLiquid);
        stats.add(BlockStat.liquidOutputSpeed, 60f * consumes.get(ConsumeLiquid.class).used(), StatUnit.liquidSecond);
    }

    @Override
    public void setBars(){
        super.setBars();

        bars.remove(BarType.liquid);
        bars.add(new BlockBar(BarType.liquid, true, tile -> tile.entity.liquids.get(consumes.liquid()) / liquidCapacity));
        bars.add(new BlockBar(BarType.liquid, true, tile -> tile.entity.liquids.get(outputLiquid) / liquidCapacity));
    }

    @Override
    public boolean shouldConsume(Tile tile){
        return tile.entity.liquids.get(outputLiquid) < liquidCapacity;
    }

    @Override
    public void update(Tile tile){
        LiquidMixerEntity entity = tile.entity();

        if(tile.entity.cons.valid()){
            float use = Math.min(consumes.get(ConsumeLiquid.class).used() * entity.delta(), liquidCapacity - entity.liquids.get(outputLiquid));
            entity.accumulator += use;
            entity.liquids.add(outputLiquid, use);
            entity.ambientSoundEnabled = true;
            for(int i = 0; i < (int) (entity.accumulator / liquidPerItem); i++){
                if(!entity.items.has(consumes.item())) break;
                entity.items.remove(consumes.item(), 1);
                entity.accumulator -= liquidPerItem;
            }
        } else entity.ambientSoundEnabled = false;
        tryDumpLiquid(tile, outputLiquid);
    }

    @Override
    public void draw(Tile tile){
        drawCached(tile);
        drawDynamic(tile);
    }

    @Override
    public void drawCached(Tile tile){
        int rotation = rotate ? tile.getRotation() * 90 : 0;
        Draw.rect(bottomRegion, tile.drawx(), tile.drawy(), rotation);
    }

    @Override
    public void drawDynamic(Tile tile){
        int rotation = rotate ? tile.getRotation() * 90 : 0;
        drawLiquid(tile);
        Draw.rect(topRegion, tile.drawx(), tile.drawy(), rotation);
    }

    @Override
    public Liquid liquidFor(Tile tile){
        if(tile.entity == null || tile.entity.liquids == null) return null;
        return tile.entity.liquids.get(outputLiquid) > 0.001f ? outputLiquid : null;
    }

    @Override
    public void drawLiquid(Tile tile){
        Liquid liquid = liquidFor(tile);
        if(liquid == null){
            Draw.color();
            return;
        }

        Draw.color(liquid.color);
        Draw.alpha(tile.entity.liquids.get(outputLiquid) / liquidCapacity);
        Draw.rect(liquidRegion, tile.drawx(), tile.drawy());
        Draw.color();
    }

    @Override
    public TileEntity newEntity(){
        return new LiquidMixerEntity();
    }

    static class LiquidMixerEntity extends TileEntity{
        float accumulator;
    }
}
