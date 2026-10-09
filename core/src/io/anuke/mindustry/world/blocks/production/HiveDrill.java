package io.anuke.mindustry.world.blocks.production;

import io.anuke.mindustry.ai.mass.MassAdrenaline;
import io.anuke.mindustry.ai.mass.MassAIConfig;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.storage.HiveBlock;
import io.anuke.mindustry.world.consumers.ConsumeLiquid;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

public class HiveDrill extends Drill{

    public float evoSearchRadius = 15f;

    public HiveDrill(String name){
        super(name);
    }

    @Override
    public void update(Tile tile){
        DrillEntity entity = tile.entity();

        if(entity.dominantItem == null){
            oreCount.clear();
            itemArray.clear();

            for(Tile other : tile.getLinkedTiles(tempTiles)){
                if(isValid(other)){
                    oreCount.getAndIncrement(getDrop(other), 0, 1);
                }
            }

            for(io.anuke.mindustry.type.Item item : oreCount.keys()){
                itemArray.add(item);
            }

            itemArray.sort((item1, item2) -> Integer.compare(oreCount.get(item1, 0), oreCount.get(item2, 0)));
            itemArray.sort((item1, item2) -> item1.genOre && !item2.genOre ? 1 : item1.genOre == item2.genOre ? 0 : -1);

            if(itemArray.size == 0){
                return;
            }

            entity.dominantItem = itemArray.peek();
            entity.dominantItems = oreCount.get(itemArray.peek(), 0);
        }

        float totalHardness = entity.dominantItems * entity.dominantItem.hardness;

        if(entity.timer.get(timerDump, 15)){
            tryDump(tile);
        }

        entity.drillTime += entity.warmup * entity.delta();

        if(entity.items.total() < itemCapacity && entity.dominantItems > 0 && entity.cons.valid()){

            float speed = 1f;

            if(entity.consumed(ConsumeLiquid.class) && !liquidRequired){
                speed = liquidBoostIntensity;
            }

            int evo = HiveBlock.getMaxEvolutionNearby(tile, evoSearchRadius);
            float evoSpeed = HiveBlock.getEvolutionSpeedMultiplier(evo);
            speed *= evoSpeed;

            if(MassAdrenaline.isBonusActiveAt(tile)) speed *= MassAIConfig.ADRENALINE_BIO_SPEED;
            entity.warmup = Mathf.lerpDelta(entity.warmup, speed, warmupSpeed);
            entity.progress += entity.delta()
            * entity.dominantItems * speed * entity.warmup;
            entity.ambientSoundEnabled = true;
            if(Mathf.chance(Timers.delta() * updateEffectChance * entity.warmup))
                Effects.effect(updateEffect, entity.x + Mathf.range(size * 2f), entity.y + Mathf.range(size * 2f));
        }else{
            entity.warmup = Mathf.lerpDelta(entity.warmup, 0f, warmupSpeed);
            entity.ambientSoundEnabled = false;
            return;
        }

        if(entity.dominantItems > 0 && entity.progress >= drillTime + hardnessDrillMultiplier * Math.max(totalHardness, 1f) / entity.dominantItems
                && tile.entity.items.total() < itemCapacity){

            offloadNear(tile, entity.dominantItem);

            useContent(tile, entity.dominantItem);

            entity.index++;
            entity.progress = 0f;

            Effects.effect(drillEffect, entity.dominantItem.color,
                    entity.x + Mathf.range(size), entity.y + Mathf.range(size));
        }
    }
}
