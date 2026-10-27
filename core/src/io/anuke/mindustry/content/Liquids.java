package io.anuke.mindustry.content;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Liquid;

public class Liquids implements ContentList{
    public static Liquid water, lava, slag, oil, cryofluid, chromium_acid, infected_water;

    @Override
    public void load(){

        water = new Liquid("water", Color.valueOf("486acd")){
            {
                heatCapacity = 0.4f;
                tier = 0;
                effect = StatusEffects.wet;
            }

            @Override
            public boolean alwaysUnlocked() {
                return true;
            }
        };

        infected_water = new Liquid("infected_water", Color.valueOf("720909")){
            {
                heatCapacity = 0.0f;
                tier = 0;
                effect = StatusEffects.wet;
            }
        };

        lava = new Liquid("lava", Color.valueOf("e37341")){
            {
                temperature = 0.8f;
                viscosity = 0.8f;
                tier = 2;
                effect = StatusEffects.melting;
                emitLight = true;
                lightScale = 1.7f;
            }
        };

        slag = new Liquid("slag", Color.valueOf("dd7f16")){
            {
                temperature = 1.0f;
                viscosity = 0.8f;
                tier = 2;
                effect = StatusEffects.melting;
                emitLight = true;
                lightScale = 2.1f;
            }
        };

        oil = new Liquid("oil", Color.valueOf("313131")){
            {
                viscosity = 0.7f;
                flammability = 0.6f;
                explosiveness = 0.6f;
                heatCapacity = 0.7f;
                tier = 1;
                effect = StatusEffects.tarred;
            }
        };

        cryofluid = new Liquid("cryofluid", Color.SKY){
            {
                heatCapacity = 0.9f;
                temperature = 0.25f;
                tier = 1;
                effect = StatusEffects.freezing;
                emitLight = true;
                lightScale = 1.6f;
            }
        };

        chromium_acid = new Liquid("chromium_acid", Color.valueOf("b11e1e")){
            {
                viscosity = 0.5f;
                tier = 1;
                effect = StatusEffects.acid;
        }
            @Override
            public boolean alwaysUnlocked() {
                return true;
            }
        };
    }

    @Override
    public ContentType type(){
        return ContentType.liquid;
    }
}
