package io.anuke.mindustry.graphics.bloom;

import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.entities.traits.MinerTrait;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.core.Renderer;
import io.anuke.mindustry.graphics.BloomLayer;
import io.anuke.mindustry.graphics.BloomLayers;
import io.anuke.ucore.entities.EntityGroup;

import static io.anuke.mindustry.Vars.*;

public class UnitBloomLayer implements BloomLayer{
    @Override
    public String name(){
        return BloomLayers.UNITS;
    }

    @Override
    public int priority(){
        return 30;
    }

    @Override
    public void draw(){
        for(EntityGroup<? extends BaseUnit> group : unitGroups){
            for(BaseUnit unit : group.all()){
                if(unit.isDead()) continue;

                // mining laser bloom
                if(unit instanceof MinerTrait miner){
                    if(miner.isMining()){
                        miner.drawMining(unit);
                    }
                }
                if(unit instanceof BuilderTrait builder){
                    if(builder.getMineTile() != null){
                        builder.drawMining(unit);
                    }
                }

                // engine flare bloom: the type decides whether the unit has one
                if(renderer.isVisible(unit.x, unit.y, 64f)){
                    unit.drawEngine();
                }
            }
        }
    }
}
