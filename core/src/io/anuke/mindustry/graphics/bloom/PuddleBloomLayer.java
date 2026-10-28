package io.anuke.mindustry.graphics.bloom;

import io.anuke.mindustry.entities.effect.Puddle;
import io.anuke.mindustry.graphics.BloomLayer;
import io.anuke.mindustry.graphics.BloomLayers;
import io.anuke.mindustry.core.Renderer;

import static io.anuke.mindustry.Vars.*;

public class PuddleBloomLayer implements BloomLayer{
    @Override
    public String name(){
        return BloomLayers.PUDDLES;
    }

    @Override
    public int priority(){
        return 5;
    }

    @Override
    public void draw(){
        for(Object entity : puddleGroup.allRaw()){
            if(entity instanceof Puddle){
                Puddle puddle = (Puddle)entity;
                if(renderer.isVisible(puddle.x, puddle.y, 64f)){
                    puddle.drawBloom();
                }
            }
        }
    }
}
