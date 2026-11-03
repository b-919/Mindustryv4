package io.anuke.mindustry.world.blocks.production;

import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.storage.HiveBlock;
import io.anuke.mindustry.world.consumers.ConsumeItem;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

public class HiveCrafter extends GenericCrafter{

    public float evoSearchRadius = 15f;

    public HiveCrafter(String name){
        super(name);
    }

    @Override
    public void update(Tile tile){
        GenericCrafterEntity entity = tile.entity();

        if(entity.cons.valid() && tile.entity.items.get(output) < itemCapacity){

            int evo = HiveBlock.getMaxEvolutionNearby(tile, evoSearchRadius);
            float evoSpeed = HiveBlock.getEvolutionSpeedMultiplier(evo);

            entity.progress += 1f / craftTime * entity.delta() * evoSpeed;
            entity.totalProgress += entity.delta();
            entity.warmup = Mathf.lerpDelta(entity.warmup, 1f, 0.02f);
            entity.ambientSoundEnabled = true;
            if(Mathf.chance(Timers.delta() * updateEffectChance))
                Effects.effect(updateEffect, entity.x + Mathf.range(size * 4f), entity.y + Mathf.range(size * 4));
        }else{
            entity.warmup = Mathf.lerp(entity.warmup, 0f, 0.02f);
            entity.ambientSoundEnabled = false;
        }

        if(entity.progress >= 1f){

            if(consumes.has(ConsumeItem.class)) tile.entity.items.remove(consumes.item(), consumes.itemAmount());

            useContent(tile, output);

            for(int i = 0; i < itemOutputAmount; i++){
                offloadNear(tile, output);
            }
            Effects.effect(craftEffect, tile.drawx(), tile.drawy());
            entity.progress = 0f;
        }

        if(tile.entity.timer.get(timerDump, 5)){
            tryDump(tile, output);
        }
    }
}
