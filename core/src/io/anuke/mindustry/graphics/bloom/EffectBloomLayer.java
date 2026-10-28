package io.anuke.mindustry.graphics.bloom;

import io.anuke.mindustry.graphics.BloomLayer;
import io.anuke.mindustry.graphics.BloomLayers;
import io.anuke.mindustry.graphics.Drawf;
import io.anuke.mindustry.core.Renderer;
import io.anuke.ucore.entities.impl.EffectEntity;

import static io.anuke.mindustry.Vars.*;

public class EffectBloomLayer implements BloomLayer{
    @Override
    public String name(){
        return BloomLayers.EFFECTS;
    }

    @Override
    public int priority(){
        return 10;
    }

    @Override
    public void draw(){
        //the effect renderers queue per-particle l
        Drawf.setCollecting(false);
        try{
            //effectGroup is heterogeneous so allRaw() to prevent crash
            for(Object entity : effectGroup.allRaw()){
                if(entity instanceof EffectEntity){
                    drawBloom((EffectEntity) entity);
                }
            }
            for(Object entity : groundEffectGroup.allRaw()){
                if(entity instanceof EffectEntity){
                    drawBloom((EffectEntity) entity);
                }
            }
        }finally{
            Drawf.setCollecting(true);
        }
    }

    private void drawBloom(EffectEntity entity){
        if(entity.effect != null && entity.effect.emitLight
                && renderer.isVisible(entity.x, entity.y, Renderer.bloomCullMargin)){
            entity.draw();
        }
    }
}
