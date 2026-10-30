package io.anuke.mindustry.world.blocks.production;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.consumers.ConsumeItem;
import io.anuke.ucore.graphics.Draw;

public class Fracker extends SolidPump{
    protected final float itemUseTime = 100f;

    protected TextureRegion liquidRegion;
    protected TextureRegion rotatorRegion;
    protected TextureRegion topRegion;

    public Fracker(String name){
        super(name);
        hasItems = true;
        itemCapacity = 20;
        singleLiquid = false;

        consumes.require(ConsumeItem.class);
    }

    @Override
    public void load(){
        super.load();

        liquidRegion = Draw.region(name + "-liquid");
        rotatorRegion = Draw.region(name + "-rotator");
        topRegion = Draw.region(name + "-top");
    }

    @Override
    public void draw(Tile tile){
        drawCached(tile);
        drawDynamic(tile);
    }

    @Override
    public void drawDynamic(Tile tile){
        FrackerEntity entity = tile.entity();

        drawLiquid(tile);
        Draw.rect(rotatorRegion, tile.drawx(), tile.drawy(), entity.pumpTime);
        Draw.rect(topRegion, tile.drawx(), tile.drawy());
    }

    @Override
    public Liquid liquidFor(Tile tile){
        if(tile.entity == null || tile.entity.liquids == null) return null;
        return tile.entity.liquids.get(result) > 0.001f ? result : null;
    }

    @Override
    public void drawLiquid(Tile tile){
        Liquid liquid = liquidFor(tile);
        if(liquid == null){
            Draw.color();
            return;
        }

        Draw.color(liquid.color);
        Draw.alpha(tile.entity.liquids.get(result) / liquidCapacity);
        Draw.rect(liquidRegion, tile.drawx(), tile.drawy());
        Draw.color();
    }

    @Override
    public TextureRegion[] getIcon(){
        return new TextureRegion[]{Draw.region(name), Draw.region(name + "-rotator"), Draw.region(name + "-top")};
    }

    @Override
    public void update(Tile tile){
        FrackerEntity entity = tile.entity();
        Item item = consumes.item();

        while(entity.accumulator >= itemUseTime && entity.items.has(item, 1)){
            entity.items.remove(item, 1);
            entity.accumulator -= itemUseTime;
        }

        if(entity.cons.valid() && entity.accumulator < itemUseTime){
            super.update(tile);
            entity.accumulator += entity.delta();
        }else{
            tryDumpLiquid(tile, result);
        }
    }

    @Override
    public TileEntity newEntity(){
        return new FrackerEntity();
    }

    @Override
    public float typeLiquid(Tile tile){
        return tile.entity.liquids.get(result);
    }

    public static class FrackerEntity extends SolidPumpEntity{
        public float accumulator;
    }
}
