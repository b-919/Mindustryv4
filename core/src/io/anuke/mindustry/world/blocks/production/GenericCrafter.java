package io.anuke.mindustry.world.blocks.production;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.io.SaveFileVersion;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.BarType;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.consumers.ConsumeItem;
import io.anuke.mindustry.world.consumers.ConsumeItemFilter;
import io.anuke.mindustry.world.consumers.ConsumeItems;
import io.anuke.mindustry.world.consumers.ConsumeLiquid;
import io.anuke.mindustry.world.meta.BlockBar;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.content;
import static io.anuke.mindustry.Vars.tilesize;

public class GenericCrafter extends Block{
    protected final int timerDump = timers++;
    protected final int timerSmoke = timers++;
    protected final int timerContentCheck = timers++;

    protected Item output;
    /** item output of this block*/
    public int itemOutputAmount = 1;
    /**Optional. Liquid produced by this block. Set hasLiquids to true when using.*/
    protected Liquid outputLiquid;
    protected float outputLiquidAmount;
    protected float craftTime = 80;
    protected Effect craftEffect = BlockFx.purify;
    protected Effect updateEffect = Fx.none;
    protected float updateEffectChance = 0.04f;

    /**Controls smoke emission while this block is active. Null (default): smelters emit smoke, regular crafters do not.
     * True: emits smoke even for regular crafters. False: never emits smoke, even for smelters.*/
    protected Boolean smoke = null;

    /**if true, this block behaves as a smelter: it heats up (via fuel or power), emits smoke and draws a glowing flame. all other smelter settings below are only used then.*/
    protected boolean smelter = false;

    //smelter settings
    protected float minFlux = 0.2f;
    protected int fluxNeeded = 1;
    protected float fluxSpeedMult = 0.75f;
    protected float baseFluxChance = 0.25f;
    protected boolean useFlux = false;

    /**how long a single unit of fuel burns for, in ticks. only used when an optional item is consumed.*/
    protected float burnDuration = 50f;
    protected Effect burnEffect = BlockFx.fuelburn;

    //smoke settings
    protected Effect smokeEffect = BlockFx.smokes;
    protected Color smokeColor = Color.valueOf("6d6d6d");
    protected float smokeLength = 20f;
    protected float smokeDirection = 180f;
    protected float smokeSize = 3f;
    protected float smokeInterval = 32f;
    protected float smokeRandomness = 1f;
    protected float smokeShadowAlpha = 0.12f;

    //power-based heating settings
    protected float heatUpTime = 80f;
    protected float minHeat = 0.5f;
    protected float burnEffectChance = 0.01f;

    protected Color flameColor = Color.valueOf("ffb879");
    protected TextureRegion topRegion;

    public GenericCrafter(String name){
        super(name);
        hasBloom = true;
        hasLiquidLayer = true;
        setAmbientSound("loopMachine", 0.09f);
        update = true;
        solid = true;
        health = 60;
        drawCached = true;
        drawDynamic = true;
    }

    @Override
    public void load(){
        super.load();
        if(smelter) topRegion = Draw.region(name + "-top", Draw.getClearRegion());
    }

    @Override
    public void setBars(){
        super.setBars();

        if(smelter){
            bars.remove(BarType.inventory);

            for(ItemStack item : consumes.items()){
                bars.add(new BlockBar(BarType.inventory, true, tile -> (float) tile.entity.items.get(item.item) / itemCapacity));
            }
        }else if(consumes.has(ConsumeItem.class)){
            bars.replace(new BlockBar(BarType.inventory, true,
                    tile -> (float) tile.entity.items.get(consumes.item()) / itemCapacity));
        }
    }

    @Override
    public void init(){
        super.init();

        if(smelter){
            if(!consumes.has(ConsumeItems.class)){
                throw new IllegalArgumentException("Smelter block '" + name + "' must define its inputs via consumes.items(...)!");
            }

            for(ItemStack item : consumes.items()){
                if(item.item.fluxiness >= minFlux && useFlux){
                    throw new IllegalArgumentException("'" + name + "' has input item '" + item.item.name + "', which is a flux, when useFlux is enabled. To prevent ambiguous item use, either remove this flux item from the inputs, or set useFlux to false.");
                }
            }
        }

        if(outputLiquid != null){
            outputsLiquid = true;
        }

        if(outputLiquid != null){
            produces.set(outputLiquid);
        }else if(output != null){
            produces.set(output);
        }
    }

    @Override
    public void setStats(){
        super.setStats();

        if(smelter){
            if(hasFuel()){
                stats.remove(BlockStat.boostItem);
                stats.add(BlockStat.inputFuel, new ItemStack(consumes.item(), consumes.itemAmount()));
                stats.add(BlockStat.fuelBurnTime, burnDuration / 60f, StatUnit.seconds);
            }
            if(output != null){
                stats.add(BlockStat.outputItem, new ItemStack(output, itemOutputAmount));
            }
            if(outputLiquid != null){
                stats.add(BlockStat.liquidOutput, outputLiquid);
            }
            stats.add(BlockStat.craftSpeed, 60f / craftTime * itemOutputAmount, StatUnit.itemsSecond);
            stats.add(BlockStat.craftTime, craftTime / (60f * itemOutputAmount), StatUnit.seconds);
            stats.add(BlockStat.inputItemCapacity, itemCapacity, StatUnit.items);
            stats.add(BlockStat.outputItemCapacity, itemCapacity, StatUnit.items);
        }else{
            stats.add(BlockStat.craftSpeed, 60f / craftTime * itemOutputAmount, StatUnit.itemsSecond);
            stats.add(BlockStat.craftTime, craftTime / (60f * itemOutputAmount), StatUnit.seconds);
            if(output != null){
                stats.add(BlockStat.outputItem, new ItemStack(output, itemOutputAmount));
            }
            if(outputLiquid != null){
                stats.add(BlockStat.liquidOutput, outputLiquid);
            }
        }
    }

    @Override
    public void draw(Tile tile){
        drawCached(tile);
        drawDynamic(tile);
    }

    @Override
    public void drawCached(Tile tile){
        Draw.rect(name(), tile.drawx(), tile.drawy());
    }

    @Override
    public void drawDynamic(Tile tile){
        if(smelter){
            GenericCrafterEntity entity = tile.entity();

            //draw glowing center
            if(entity.heat > 0f && flameColor.a > 0.001f){
                float g = 0.3f;
                float r = 0.06f;
                float cr = Mathf.random(0.1f);

                Draw.alpha(((1f - g) + Mathf.absin(Timers.time(), 8f, g) + Mathf.random(r) - r) * entity.heat);

                Draw.tint(flameColor);
                Fill.circle(tile.drawx(), tile.drawy(), 3f + Mathf.absin(Timers.time(), 5f, 2f) + cr);
                Draw.color(1f, 1f, 1f, entity.heat);
                Draw.rect(topRegion, tile.drawx(), tile.drawy());
                Fill.circle(tile.drawx(), tile.drawy(), 1.9f + Mathf.absin(Timers.time(), 5f, 1f) + cr);

                Draw.color();
            }
        }

        if(!hasLiquids) return;

        drawLiquid(tile);
    }

    @Override
    public void drawLiquid(Tile tile){
        if(!hasLiquids || tile.entity == null || tile.entity.liquids == null) return;

        Liquid liquid = liquidFor(tile);
        if(liquid == null){
            Draw.color();
            return;
        }

        Draw.color(liquid.color);
        Draw.alpha(tile.entity.liquids.total() / liquidCapacity);
        Draw.rect("blank", tile.drawx(), tile.drawy(), 2, 2);
        Draw.color();
    }

    @Override
    public void drawLayerLight(Tile tile){
        if(!smelter){
            super.drawLayerLight(tile);
            return;
        }

        GenericCrafterEntity entity = tile.entity();
        if(entity.heat <= 0f || flameColor.a <= 0.001f) return;

        //pulse the light along with the smelter's flame effect
        float g = 0.3f;
        float pulse = (1f - g) + Mathf.absin(Timers.time(), 8f, g);
        drawLight(tile.drawx(), tile.drawy(), layerLightRadius * tilesize,
                layerLightOpacity * entity.heat * Mathf.clamp(pulse), flameColor);
    }

    @Override
    public TextureRegion[] getIcon(){
        return new TextureRegion[]{Draw.region(name)};
    }

    @Override
    public void drawBloom(Tile tile){
        if(!smelter) return;

        GenericCrafterEntity entity = tile.entity();
        if(entity.heat <= 0f || flameColor.a <= 0.001f) return;

        float g = 0.3f;
        float r = 0.06f;
        float cr = Mathf.random(0.1f);

        Draw.alpha(((1f - g) + Mathf.absin(Timers.time(), 8f, g) + Mathf.random(r) - r) * entity.heat);
        Draw.tint(flameColor);
        Fill.circle(tile.drawx(), tile.drawy(), 3f + Mathf.absin(Timers.time(), 5f, 2f) + cr);
        Draw.color(1f, 1f, 1f, entity.heat);
        Fill.circle(tile.drawx(), tile.drawy(), 1.9f + Mathf.absin(Timers.time(), 5f, 1f) + cr);

        if(topRegion != null){
            Draw.color(1f, 1f, 1f, entity.heat * 0.8f);
            Draw.rect(topRegion, tile.drawx(), tile.drawy());
        }
        Draw.color();
    }

    @Override
    public void update(Tile tile){
        GenericCrafterEntity entity = tile.entity();

        if(smelter){
            updateSmelter(tile, entity);
        }else{
            updateCrafter(tile, entity);
        }
    }

    private void updateCrafter(Tile tile, GenericCrafterEntity entity){
        if(entity.cons.valid()
                && (output == null || tile.entity.items.get(output) < itemCapacity)
                && (outputLiquid == null || entity.liquids.total() < liquidCapacity)){

            entity.progress += 1f / craftTime * entity.delta();
            entity.totalProgress += entity.delta();
            entity.warmup = Mathf.lerpDelta(entity.warmup, 1f, 0.02f);
            entity.ambientSoundEnabled = true;
            if(Mathf.chance(Timers.delta() * updateEffectChance))
                Effects.effect(updateEffect, entity.x + Mathf.range(size * 4f), entity.y + Mathf.range(size * 4));
            if(smoke == Boolean.TRUE && entity.timer.get(timerSmoke, smokeInterval)){
                Effects.effect(smokeEffect, tile.drawx() + Mathf.range(2f), tile.drawy() + Mathf.range(2f), 0f,
                        new BlockFx.SmokeData(smokeColor, smokeLength * size, smokeDirection, smokeSize * size, smokeRandomness, smokeShadowAlpha));
            }
        }else{
            entity.warmup = Mathf.lerp(entity.warmup, 0f, 0.02f);
            entity.ambientSoundEnabled = false;
        }

        if(entity.progress >= 1f){

            if(consumes.has(ConsumeItem.class)){
                tile.entity.items.remove(consumes.item(), consumes.itemAmount());
            }else if(getConsumedItem(entity) != null){
                tile.entity.items.remove(getConsumedItem(entity), 1);
            }

            if(output != null){
                useContent(tile, output);

                for(int i = 0; i < itemOutputAmount; i++){
                    offloadNear(tile, output);
                }
            }

            if(outputLiquid != null){
                handleLiquid(tile, tile, outputLiquid, outputLiquidAmount);
                if(entity.liquids.currentAmount() > 0f && entity.timer.get(timerContentCheck, 10)){
                    useContent(tile, outputLiquid);
                }
            }

            if(output != null){
                Effects.effect(craftEffect, tile.drawx(), tile.drawy());
            }
            entity.progress = 0f;
        }

        if(output != null && tile.entity.timer.get(timerDump, 5)){
            tryDump(tile, output);
        }

        if(outputLiquid != null){
            tryDumpLiquid(tile, entity.liquids.current());
        }
    }

    private void updateSmelter(Tile tile, GenericCrafterEntity entity){
        if(output != null && entity.timer.get(timerDump, 5) && entity.items.has(output)){
            tryDump(tile, output);
        }

        if(hasFuel()){
            //add fuel
            if(entity.consumed(ConsumeItem.class) && entity.burnTime <= 0f){
                entity.items.remove(consumes.item(), 1);
                entity.burnTime += burnDuration;
                Effects.effect(burnEffect, entity.x + Mathf.range(2f), entity.y + Mathf.range(2f));
                entity.ambientSoundEnabled = true;
            }

            //decrement burntime
            if(entity.burnTime > 0){
                entity.burnTime -= entity.delta();
                entity.heat = Mathf.lerpDelta(entity.heat, 1f, 0.02f);
                entity.ambientSoundEnabled = true;
                if(smoke != Boolean.FALSE && entity.timer.get(timerSmoke, smokeInterval)){
                    Effects.effect(smokeEffect, tile.drawx() + Mathf.range(2f), tile.drawy() + Mathf.range(2f), 0f,
                            new BlockFx.SmokeData(smokeColor, smokeLength * size, smokeDirection, smokeSize * size, smokeRandomness, smokeShadowAlpha));
                }
            }else{
                entity.heat = Mathf.lerpDelta(entity.heat, 0f, 0.02f);
                entity.ambientSoundEnabled = false;
            }
        }else{
            //heat it up if there's enough power
            if(entity.cons.valid()){
                entity.heat += 1f / heatUpTime * entity.delta();
                entity.ambientSoundEnabled = true;
                if(Mathf.chance(entity.delta() * burnEffectChance))
                    Effects.effect(burnEffect, entity.x + Mathf.range(size * 4f), entity.y + Mathf.range(size * 4));
                if(smoke != Boolean.FALSE && entity.heat > minHeat && entity.timer.get(timerSmoke, smokeInterval)){
                    float smokeSpawn = 2f * (1f + (size - 1f) * 0.5f);
                    Effects.effect(smokeEffect, tile.drawx() + Mathf.range(smokeSpawn), tile.drawy() + Mathf.range(smokeSpawn), 0f,
                            new BlockFx.SmokeData(smokeColor, smokeLength * size, smokeDirection, smokeSize * size, smokeRandomness, smokeShadowAlpha));
                }
            }else{
                entity.heat -= 1f / heatUpTime * Timers.delta();
                entity.ambientSoundEnabled = false;
            }

            entity.heat = Mathf.clamp(entity.heat);
            entity.time += entity.heat * entity.delta();
        }

        //make sure it has all the items
        if(!entity.cons.valid()){
            return;
        }

        float baseSmeltSpeed = 1f;
        for(Item item : content.items()){
            if(item.fluxiness >= minFlux && tile.entity.items.get(item) > 0){
                baseSmeltSpeed = fluxSpeedMult;
                break;
            }
        }

        entity.craftTime += entity.delta();

        if((output != null && entity.items.get(output) >= itemCapacity) //output full
                || (hasFuel() ? entity.burnTime <= 0 : entity.heat <= minHeat) //not hot enough
                || entity.craftTime < craftTime * baseSmeltSpeed){ //not yet time
            return;
        }

        entity.craftTime = 0f;

        boolean consumeInputs = true;

        if(useFlux){
            //remove flux materials if present
            for(Item item : content.items()){
                if(item.fluxiness >= minFlux && tile.entity.items.get(item) >= fluxNeeded){
                    tile.entity.items.remove(item, fluxNeeded);

                    //chance of not consuming inputs if flux material present
                    consumeInputs = !Mathf.chance(item.fluxiness * baseFluxChance);
                    break;
                }
            }
        }

        if(consumeInputs){
            for(ItemStack item : consumes.items()){
                entity.items.remove(item.item, item.amount);
            }
        }

        if(output != null){
            for(int i = 0; i < itemOutputAmount; i++){
                offloadNear(tile, output);
            }
        }

        if(outputLiquid != null){
            handleLiquid(tile, tile, outputLiquid, outputLiquidAmount);
        }

        Effects.effect(craftEffect, flameColor, tile.drawx(), tile.drawy());
    }

    /**Returns whether this block runs on fuel instead of power. Only valid in smelter mode.*/
    private boolean hasFuel(){
        return smelter && consumes.has(ConsumeItem.class) && consumes.get(ConsumeItem.class).isOptional();
    }

    @Override
    public TileEntity newEntity(){
        return new GenericCrafterEntity();
    }

    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        return itemCapacity;
    }

    /**Returns the item currently being consumed, or null if none is available.*/
    Item getConsumedItem(TileEntity entity){
        if(consumes.has(ConsumeItem.class)) return consumes.item();
        if(consumes.has(ConsumeItemFilter.class)){
            ConsumeItemFilter filter = consumes.get(ConsumeItemFilter.class);
            for(Item item : content.items()){
                if(entity.items.has(item) && filter.accepts(item)) return item;
            }
        }
        return null;
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        if(smelter){
            for(ItemStack stack : consumes.items()){
                if(stack.item == item){
                    return tile.entity.items.get(item) < itemCapacity;
                }
            }

            if(hasFuel() && item == consumes.item()){
                return tile.entity.items.get(item) < itemCapacity;
            }

            return useFlux && item.fluxiness >= minFlux && tile.entity.items.get(item) < itemCapacity;
        }

        if(consumes.has(ConsumeItemFilter.class)){
            return consumes.<ConsumeItemFilter>get(ConsumeItemFilter.class).accepts(item) &&
                    tile.entity.items.get(item) < getMaximumAccepted(tile, item);
        }
        return super.acceptItem(item, tile, source);
    }

    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        //blocks that only output liquids (and consume no liquid input) must not be fed liquids through conduits
        if(outputLiquid != null && !consumes.has(ConsumeLiquid.class)){
            return false;
        }
        return super.acceptLiquid(tile, source, liquid, amount);
    }

    public static class GenericCrafterEntity extends TileEntity{
        public float progress;
        public float totalProgress;
        public float warmup;
        public float burnTime;
        public float heat;
        public float craftTime;
        public float time;

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeFloat(progress);
            stream.writeFloat(warmup);
            stream.writeFloat(burnTime);
            stream.writeFloat(heat);
            stream.writeFloat(craftTime);
            stream.writeFloat(time);
        }

        @Override
        public void read(DataInput stream) throws IOException{
            progress = stream.readFloat();
            warmup = stream.readFloat();

            if(SaveFileVersion.currentVersion >= 19){
                burnTime = stream.readFloat();
                heat = stream.readFloat();
                craftTime = stream.readFloat();
                time = stream.readFloat();
            }else if(tile != null && tile.block() instanceof GenericCrafter && ((GenericCrafter) tile.block()).smelter && !((GenericCrafter) tile.block()).hasFuel()){
                //legacy power smelters only saved their heat value
                heat = stream.readFloat();
            }
        }
    }
}
