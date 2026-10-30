package io.anuke.mindustry.world.blocks.units;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.world.BarType;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.consumers.Consume;
import io.anuke.mindustry.world.consumers.ConsumeItems;
import io.anuke.mindustry.world.meta.BlockBar;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.values.UnitValue;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.style.TextureRegionDrawable;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.EnumSet;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

import static io.anuke.mindustry.sounds.Sounds.blockPlace;
import static io.anuke.mindustry.Vars.tilesize;

public class UnitFactoryAdvanced extends Block{
    protected float gracePeriodMultiplier = 45f;
    protected float speedupTime = 60f * 60f * 20;
    protected float maxSpeedup = 2f;

    /** Array of the Units that the fabric can craft */
    public UnitType[] types;
    /** Array of arrays of Itemstacks made to define the amount of recipes that this fabric uses*/
    public ItemStack[][] consumerStacks;
    /** Array of the multiple Producing times for every Unit Type**/
    public float[] producerTimes;

    public int[] maxSpawn;
    public Sound buildUnitSound;
    public String buildUnitSoundName;
    protected float launchVelocity = 0f;
    protected TextureRegion topRegion;
    public UnitFactoryAdvanced(String name){
        super(name);
        configurable = true;
        update = true;
        hasPower = true;
        hasItems = true;
        solid = false;
        hasBloom = true;
        itemCapacity = 10;
        flags = EnumSet.of(BlockFlag.producer, BlockFlag.target);
        consumes.power(0);
        setAmbientSound("loopUnitBuilding", 0.09f);
        setBuildUnitSound("unitCreate");
        drawCached = true;
        drawDynamic = true;
    }

    public void setBuildUnitSound(String name){
        buildUnitSoundName = name;
        buildUnitSound = Sounds.get(name);
    }

    @Remote(targets = Loc.both, called = Loc.both, forward = true)
    public static void setUnitNumber(Player player,Tile tile ,int unitNumber){
        if(!(tile.entity instanceof UnitFactoryAdvancedEntity) || !(tile.block() instanceof UnitFactoryAdvanced)) return;

        UnitFactoryAdvancedEntity entity = tile.entity();
        UnitFactoryAdvanced factory = (UnitFactoryAdvanced)tile.block();
        int UnitDataNumber = factory.cUnitNumber(unitNumber);

        entity.unitNumber = UnitDataNumber;
        entity.unitSource = factory.types[UnitDataNumber];
        entity.buildTime = 0f;
        entity.ambientSoundEnabled = false;

    }
    @Remote(called = Loc.server)
    public static void onUnitFactoryAdvancedSpawn(Tile tile, int spawns){

        if(!(tile.entity instanceof UnitFactoryAdvancedEntity) || !(tile.block() instanceof UnitFactoryAdvanced)) return;

        UnitFactoryAdvancedEntity entity = tile.entity();
        UnitFactoryAdvanced factory = (UnitFactoryAdvanced) tile.block();
        UnitType selectedType = factory.getSelectedType(entity);

        entity.buildTime = 0f;
        entity.spawned[entity.unitNumber] = spawns;

        Effects.shake(2f, 3f, entity);
        Effects.effect(BlockFx.producesmoke, tile.drawx(), tile.drawy());

        if(!Net.client() && selectedType != null){
            BaseUnit unit = selectedType.create(tile.getTeam());
            unit.setSpawner(tile);
            unit.set(tile.drawx() + Mathf.range(4), tile.drawy() + Mathf.range(4));
            unit.add();
            unit.getVelocity().y = factory.launchVelocity;
        }
    }


    @Override
    public void load(){
        super.load();

        topRegion = Draw.region(name + "-top");
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        super.setStats();

        if(types != null && consumerStacks != null && producerTimes != null && types.length > 0){
            stats.add(BlockStat.outputUnit, new UnitValue(types, consumerStacks, producerTimes, maxSpawn));
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        bars.add(new BlockBar(BarType.production, true, tile -> {
            UnitFactoryAdvancedEntity entity = tile.entity();
            float selectedProduceTime = getSelectedProduceTime(entity);
            return selectedProduceTime <= 0f ? 0f : entity.buildTime / selectedProduceTime;
        }));
        bars.remove(BarType.inventory);
        addItemBars();
    }

    public void unitRemoved(Tile tile, Unit unit){
        UnitFactoryAdvanced.UnitFactoryAdvancedEntity entity = tile.entity();
        for (int i = 0; i < types.length; i++){
            UnitType type = types[i];
            if(((BaseUnit) unit).getType().equals(type)){
                entity.spawned[i]--;
                entity.spawned[i] = Math.max(entity.spawned[i], 0);
            }
        }

    }
    @Override
    public void buildTable(Tile tile, Table table){
        UnitFactoryAdvancedEntity entity = tile.entity();
        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        Table cont = new Table();
        if(this.types == null || this.types.length == 0) return;

        for (int i = 0; i < this.types.length; i++) {
            UnitType type = this.types[i];
            final int f = i;
            ImageButton button = cont.addImageButton("white","clear-toggle", 24,
                    ()->Call.setUnitNumber(null, tile, f)).size(38).group(group).get();
            button.getStyle().imageUp = new TextureRegionDrawable(type.iconRegion);
            button.setChecked(entity.unitNumber == f);
            if(i % 4 == 3){
                cont.row();
            }
        }

        table.add(cont);
    }

    @Override
    public TextureRegion[] getIcon(){
        return new TextureRegion[]{
            Draw.region(name),
            Draw.region(name + "-top")
        };
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
        UnitFactoryAdvancedEntity entity = tile.entity();
        UnitType selectedType = getSelectedType(entity);
        if(selectedType == null) return;

        TextureRegion region = selectedType.iconRegion;
        float selectedProduceTime = getSelectedProduceTime(entity);

        Shaders.build.region = region;
        Shaders.build.progress = selectedProduceTime <= 0f ? 0f : entity.buildTime / selectedProduceTime;
        Shaders.build.color.set(Palette.accent);
        Shaders.build.color.a = entity.speedScl;
        Shaders.build.time = -entity.time / 10f;

        Graphics.shader(Shaders.build, false);
        Shaders.build.apply();
        Draw.rect(region, tile.drawx(), tile.drawy());
        Graphics.shader();

        Draw.color(Palette.accent);
        Draw.alpha(entity.speedScl);

        Lines.lineAngleCenter(
                tile.drawx() + Mathf.sin(entity.time, 6f, Vars.tilesize / 2f * size - 2f),
                tile.drawy(),
                90,
                size * Vars.tilesize - 4f);

        Draw.reset();

        Draw.rect(topRegion, tile.drawx(), tile.drawy());
    }

    @Override
    public void drawBloom(Tile tile){
        UnitFactoryAdvancedEntity entity = tile.entity();
        float selectedProduceTime = getSelectedProduceTime(entity);

        if(selectedProduceTime <= 0f || entity.speedScl <= 0f) return;

        Draw.color(Palette.accent);
        Draw.alpha(entity.speedScl * 0.3f);
        Draw.rect("circle", tile.drawx(), tile.drawy(), size * tilesize * 1.5f, size * tilesize * 1.5f);

        Draw.color(Palette.accent);
        Draw.alpha(entity.speedScl * 0.8f);
        Lines.stroke(2f);
        Lines.lineAngleCenter(
                tile.drawx() + Mathf.sin(entity.time, 6f, Vars.tilesize / 2f * size - 2f),
                tile.drawy(),
                90,
                size * Vars.tilesize - 4f);

        Draw.color(Color.WHITE);
        Draw.alpha(entity.speedScl * 0.5f);
        Lines.stroke(1f);
        Lines.lineAngleCenter(
                tile.drawx() + Mathf.sin(entity.time, 6f, Vars.tilesize / 2f * size - 2f),
                tile.drawy(),
                90,
                size * Vars.tilesize - 4f);

        Draw.reset();
    }

    @Override
    public void update(Tile tile){
        UnitFactoryAdvancedEntity entity = tile.entity();
        UnitType selectedType = getSelectedType(entity);
        float selectedProduceTime = getSelectedProduceTime(entity);
        entity.unitSource = selectedType;
        if(entity.spawned[entity.unitNumber] >= maxSpawn[entity.unitNumber]){
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
            entity.ambientSoundEnabled = false;
            return;
        }

        entity.time += entity.delta() * entity.speedScl;
        if(tile.isEnemyCheat()){
            entity.warmup += entity.delta();
            maxSpawn[entity.unitNumber] = 100000;
        }

        if(selectedProduceTime <= 0f){
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
            entity.ambientSoundEnabled = false;
            return;
        }

        if(!tile.isEnemyCheat()){
            //player-made spawners have default behavior

            if(hasAllRequirements(entity.items, entity) && hasRequirements(entity.items, entity.buildTime / selectedProduceTime, entity) && validConsumers(entity)){

                entity.buildTime += entity.delta();
                entity.speedScl = Mathf.lerpDelta(entity.speedScl, 1f, 0.05f);
                entity.ambientSoundEnabled = true;
            }else{
                entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
                entity.ambientSoundEnabled = false;
            }
            //check if grace period had passed
        }else if(entity.warmup > selectedProduceTime*gracePeriodMultiplier * Vars.state.difficulty.spawnerScaling){
            float speedMultiplier = Math.min(0.1f + (entity.warmup - selectedProduceTime * gracePeriodMultiplier * Vars.state.difficulty.spawnerScaling) / speedupTime, maxSpeedup);
            //otherwise, it's an enemy, cheat by not requiring resources
            entity.buildTime += entity.delta() * speedMultiplier;
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 1f, 0.05f);
            entity.ambientSoundEnabled = true;
        }else{
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
            entity.ambientSoundEnabled = false;
        }

        if(selectedProduceTime > 0f && entity.buildTime >= selectedProduceTime){
            entity.buildTime = 0f;

            Call.onUnitFactoryAdvancedSpawn(tile, entity.spawned[entity.unitNumber] +1);
            Sound sound = buildUnitSound;
            if(Vars.soundController != null && sound != null){
                Vars.soundController.at(sound, tile.drawx(), tile.drawy(), 1f, 0.2f);}
            if(selectedType != null){
                useContent(tile, selectedType);
            }

            for(ItemStack stack : getSelectedConsumerStacks(entity)){
                entity.items.remove(stack.item, stack.amount);
            }
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        UnitFactoryAdvancedEntity entity = tile.entity();
        for(ItemStack stack : getSelectedConsumerStacks(entity)){
            if(item == stack.item && tile.entity.items.get(item) < stack.amount * 2){
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        UnitFactoryAdvancedEntity entity = tile.entity();
        for(ItemStack stack : getSelectedConsumerStacks(entity)){
            if(item == stack.item){
                return stack.amount * 2;
            }
        }
        return 0;
    }

    @Override
    public TileEntity newEntity(){
        UnitFactoryAdvancedEntity entity = new UnitFactoryAdvancedEntity();
        if(types != null && types.length > 0){
            entity.unitNumber = 0;
            entity.spawned = new int[types.length];
            Arrays.fill(entity.spawned, 0);
            entity.unitSource = types[entity.unitNumber];
        }
        return entity;
    }

    protected boolean hasRequirements(ItemModule inv, float fraction, UnitFactoryAdvancedEntity entity){
        for(ItemStack stack : getSelectedConsumerStacks(entity)){
            if(!inv.has(stack.item, (int) (fraction * stack.amount))){
                return false;
            }
        }
        return true;
    }

    protected boolean hasAllRequirements(ItemModule inv, UnitFactoryAdvancedEntity entity){
        for(ItemStack stack : getSelectedConsumerStacks(entity)){
            if(!inv.has(stack.item, stack.amount)){
                return false;
            }
        }
        return true;
    }

    protected boolean validConsumers(UnitFactoryAdvancedEntity entity){
        for(Consume consume : consumes.all()){
            if(consume instanceof ConsumeItems || consume.isOptional()) continue;
            if(!consume.valid(this, entity)){
                return false;
            }
        }
        return true;
    }

    protected int cUnitNumber(int unitNumber){
        if(types == null || types.length == 0) return 0;
        return Math.max(0, Math.min(unitNumber, types.length - 1));
    }

    protected UnitType getSelectedType(UnitFactoryAdvancedEntity entity){
        if(types == null || types.length == 0) return null;
        return types[cUnitNumber(entity.unitNumber)];
    }

    protected float getSelectedProduceTime(UnitFactoryAdvancedEntity entity){
        if(producerTimes == null || producerTimes.length == 0) return 0f;
        return producerTimes[cUnitNumber(entity.unitNumber)];
    }

    protected ItemStack[] getSelectedConsumerStacks(UnitFactoryAdvancedEntity entity){
        if(consumerStacks == null || consumerStacks.length == 0) return new ItemStack[0];
        return consumerStacks[cUnitNumber(entity.unitNumber)];
    }

    public ItemStack[] getRequirements(Tile tile){
        if(!(tile.entity instanceof UnitFactoryAdvancedEntity)) return new ItemStack[0];
        return getSelectedConsumerStacks(tile.entity());
    }

    public boolean hasAllRequirements(Tile tile){
        if(!(tile.entity instanceof UnitFactoryAdvancedEntity)) return false;
        return hasAllRequirements(tile.entity.items, tile.entity());
    }

    protected void addItemBars(){
        if(consumerStacks == null) return;

        ObjectSet<Item> shown = new ObjectSet<>();

        for(ItemStack[] stacks : consumerStacks){
            if(stacks == null) continue;

            for(ItemStack stack : stacks){
                if(stack == null || !shown.add(stack.item)) continue;
                bars.add(new BlockBar(BarType.inventory, true, tile -> (float)tile.entity.items.get(stack.item) / itemCapacity));
            }
        }
    }

    public static class UnitFactoryAdvancedEntity extends TileEntity{
        public float buildTime;
        public float time;
        public float speedScl;
        public float warmup; //only for enemy spawners
        /** Int that handles what unit of the Array to use*/
        public int unitNumber;
        public int[] spawned;

        public UnitType unitSource;


        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeFloat(buildTime);
            stream.writeFloat(warmup);
            stream.writeInt(spawned[unitNumber]);

        }

        @Override
        public void read(DataInput stream) throws IOException{
            buildTime = stream.readFloat();
            warmup = stream.readFloat();
            spawned[unitNumber] = stream.readInt();

        }
        @Override
        public void writeConfig(DataOutput stream) throws IOException{
            stream.writeByte(unitNumber);
        }
        @Override
        public void readConfig(DataInput stream) throws IOException{
            unitNumber = stream.readByte();
            if(tile != null && tile.block() instanceof UnitFactoryAdvanced){
                UnitFactoryAdvanced factory = (UnitFactoryAdvanced)tile.block();
                if(factory.types != null && factory.types.length > 0){
                    unitNumber = factory.cUnitNumber(unitNumber);
                    unitSource = factory.types[unitNumber];
                }
            }
        }

        @Override
        public Object config(){
            return (int)unitNumber;
        }

        @Override
        public void configured(Object config){
            if(config instanceof Integer){
                unitNumber = (Integer)config;
                if(tile != null && tile.block() instanceof UnitFactoryAdvanced){
                    UnitFactoryAdvanced factory = (UnitFactoryAdvanced)tile.block();
                    if(factory.types != null && factory.types.length > 0 && unitNumber >= 0 && unitNumber < factory.types.length){
                        unitSource = factory.types[unitNumber];
                    }
                }
            }
        }
    }
}
