package io.anuke.mindustry.world.blocks.units;

import com.badlogic.gdx.audio.Sound;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.distribution.TrainRail;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.util.Mathf;

public class TrainCrafter extends UnitFactory{

    public TrainCrafter(String name){
        super(name);
        rotate = true;
        size = 2;
        type = UnitTypes.trainEngine;
        produceTime = 420f;
        maxSpawn = 99;
        setBuildUnitSound("unitCreate");
    }

    @Remote(called = Loc.server)
    public static void onTrainCrafterSpawn(Tile tile){
        if(!(tile.block() instanceof TrainCrafter) || !(tile.entity instanceof UnitFactoryEntity)) return;

        TrainCrafter block = (TrainCrafter) tile.block();
        UnitFactoryEntity entity = tile.entity();
        Tile rail = tile.getNearby(tile.getRotation());

        if(rail == null){
            entity.buildTime = 0f;
            return;
        }

        rail = rail.target();
        if(!(rail.block() instanceof TrainRail)){
            entity.buildTime = 0f;
            return;
        }

        entity.buildTime = 0f;
        entity.spawned++;
        entity.ambientSoundEnabled = false;

        Effects.shake(2f, 3f, entity);
        Effects.effect(BlockFx.producesmoke, tile.drawx(), tile.drawy());

        if(!Net.client()){
            BaseUnit unit = block.type.create(tile.getTeam());
            unit.setSpawner(tile);
            unit.set(rail.drawx(), rail.drawy());
            unit.rotation = tile.getRotation() * 90f;
            unit.add();
        }
    }

    @Override
    public void update(Tile tile){
        UnitFactoryEntity entity = tile.entity();
        Tile rail = tile.getNearby(tile.getRotation());
        boolean validRail = rail != null && rail.target().block() instanceof TrainRail;

        entity.time += entity.delta() * entity.speedScl;

        if(entity.spawned >= maxSpawn || !validRail){
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
            entity.ambientSoundEnabled = false;
            return;
        }

        if(hasRequirements(entity.items, entity.buildTime / produceTime) && entity.cons.valid()){
            entity.buildTime += entity.delta();
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 1f, 0.05f);
            entity.ambientSoundEnabled = true;
        }else{
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
            entity.ambientSoundEnabled = false;
        }

        if(entity.buildTime >= produceTime){
            Call.onTrainCrafterSpawn(tile);
            Sound sound = buildUnitSound;
            if(Vars.soundController != null && sound != null){
                Vars.soundController.at(sound, tile.drawx(), tile.drawy(), 1f, 0.2f);
            }
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
    public TileEntity newEntity(){
        return new UnitFactoryEntity();
    }
}
