package io.anuke.mindustry.world.blocks.units;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.fx.BlockFx;
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
import io.anuke.mindustry.world.consumers.ConsumeItems;
import io.anuke.mindustry.world.meta.BlockBar;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.mindustry.world.meta.values.UnitValue;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.EnumSet;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.tilesize;

public class UnitFactory extends Block{
    protected float gracePeriodMultiplier = 40f;
    protected float speedupTime = 60f * 60f * 20;
    protected float maxSpeedup = 2f;

    protected UnitType type;
    protected float produceTime = 1000f;
    protected float launchVelocity = 0f;
    protected TextureRegion topRegion;
    public int maxSpawn = 10;
    public Sound buildUnitSound;
    public String buildUnitSoundName;

    public UnitFactory(String name){
        super(name);
        hasBloom = true;
        update = true;
        hasPower = true;
        hasItems = true;
        solid = false;
        itemCapacity = 10;
        flags = EnumSet.of(BlockFlag.producer, BlockFlag.target);
        setAmbientSound("loopUnitBuilding", 0.09f);
        setBuildUnitSound("unitCreate");

        consumes.require(ConsumeItems.class);
        drawCached = true;
        drawDynamic = true;
    }

    public void setBuildUnitSound(String name){
        buildUnitSoundName = name;
        buildUnitSound = Sounds.get(name);
    }

    @Remote(called = Loc.server)
    public static void onUnitFactorySpawn(Tile tile, int spawns){
        if(!(tile.entity instanceof UnitFactoryEntity) || !(tile.block() instanceof UnitFactory)) return;

        UnitFactoryEntity entity = tile.entity();
        UnitFactory factory = (UnitFactory) tile.block();

        entity.buildTime = 0f;
        entity.spawned = spawns;
        entity.ambientSoundEnabled = false;

        Effects.shake(2f, 3f, entity);
        Effects.effect(BlockFx.producesmoke, tile.drawx(), tile.drawy());

        if(!Net.client()){
            BaseUnit unit = factory.type.create(tile.getTeam());
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

        if(type != null){
            stats.add(BlockStat.outputUnit, new UnitValue(type, consumes.items(), produceTime));
        }
        stats.add(BlockStat.maxUnits, maxSpawn, StatUnit.none);
    }

    @Override
    public void setBars(){
        super.setBars();

        bars.add(new BlockBar(BarType.production, true, tile -> tile.<UnitFactoryEntity>entity().buildTime / produceTime));
        bars.add(new BlockBar(BarType.production, true, tile -> (float) tile.<UnitFactoryEntity>entity().spawned / maxSpawn));
        bars.remove(BarType.inventory);
    }
    @Override
    public void unitRemoved(Tile tile, Unit unit){
        UnitFactoryEntity entity = tile.entity();
        entity.spawned--;
        entity.spawned = Math.max(entity.spawned, 0);
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
        UnitFactoryEntity entity = tile.entity();
        TextureRegion region = type.iconRegion;

        Shaders.build.region = region;
        Shaders.build.progress = entity.buildTime / produceTime;
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
        UnitFactoryEntity entity = tile.entity();
        if(entity.speedScl <= 0f) return;

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
        UnitFactoryEntity entity = tile.entity();

        entity.time += entity.delta() * entity.speedScl;

        if(entity.spawned >= maxSpawn){
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
            entity.ambientSoundEnabled = false;
            return;
        }

        if(tile.isEnemyCheat()){
            entity.warmup += entity.delta();
            maxSpawn = 10000;
        }

        if(!tile.isEnemyCheat()){
            //player-made spawners have default behavior

            if(hasRequirements(entity.items, entity.buildTime / produceTime) && entity.cons.valid()){

                entity.buildTime += entity.delta();
                entity.speedScl = Mathf.lerpDelta(entity.speedScl, 1f, 0.05f);
                entity.ambientSoundEnabled = true;
            }else{
                entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
                entity.ambientSoundEnabled = false;
            }
            //check if grace period had passed
        }else if(entity.warmup > produceTime*gracePeriodMultiplier * Vars.state.difficulty.spawnerScaling){
            float speedMultiplier = Math.min(0.1f + (entity.warmup - produceTime * gracePeriodMultiplier * Vars.state.difficulty.spawnerScaling) / speedupTime, maxSpeedup);
            //otherwise, it's an enemy, cheat by not requiring resources
            entity.buildTime += entity.delta() * speedMultiplier;
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 1f, 0.05f);
            entity.ambientSoundEnabled = true;
        }else{
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
            entity.ambientSoundEnabled = false;
        }

        if(entity.buildTime >= produceTime){
            entity.buildTime = 0f;

            Call.onUnitFactorySpawn(tile, entity.spawned +1);
            Sound sound = buildUnitSound;
            if(Vars.soundController != null && sound != null){
                Vars.soundController.at(sound, tile.drawx(), tile.drawy(), 1f, 0.2f);}
            useContent(tile, type);
            for(ItemStack stack : consumes.items()){
                entity.items.remove(stack.item, stack.amount);
            }
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        for(ItemStack stack : consumes.items()){
            if(item == stack.item && tile.entity.items.get(item) < stack.amount * 2){
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        for(ItemStack stack : consumes.items()){
            if(item == stack.item){
                return stack.amount * 2;
            }
        }
        return 0;
    }

    @Override
    public TileEntity newEntity(){
        return new UnitFactoryEntity();
    }

    protected boolean hasRequirements(ItemModule inv, float fraction){
        for(ItemStack stack : consumes.items()){
            if(!inv.has(stack.item, (int) (fraction * stack.amount))){
                return false;
            }
        }
        return true;
    }

    public static class UnitFactoryEntity extends TileEntity{
        public float buildTime;
        public float time;
        public float speedScl;
        public float warmup; //only for enemy spawners
        public int spawned;

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeFloat(buildTime);
            stream.writeFloat(warmup);
            stream.writeInt(spawned);
        }

        @Override
        public void read(DataInput stream) throws IOException{
            buildTime = stream.readFloat();
            warmup = stream.readFloat();
            spawned = stream.readInt();
        }
    }
}
