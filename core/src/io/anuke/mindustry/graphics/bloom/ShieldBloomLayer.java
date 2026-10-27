package io.anuke.mindustry.graphics.bloom;

import io.anuke.mindustry.graphics.BloomLayer;
import io.anuke.mindustry.graphics.BloomLayers;
import io.anuke.mindustry.world.blocks.defense.ForceProjector.ShieldEntity;

import static io.anuke.mindustry.Vars.*;

public class ShieldBloomLayer implements BloomLayer{
    @Override
    public String name(){
        return BloomLayers.SHIELDS;
    }

    @Override
    public int priority(){
        return 50;
    }

    @Override
    public void draw(){
        for(ShieldEntity shield : shieldGroup.all()){
            shield.drawBloom();
        }
    }
}
