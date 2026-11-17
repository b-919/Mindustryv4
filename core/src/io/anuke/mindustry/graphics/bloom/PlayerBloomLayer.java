package io.anuke.mindustry.graphics.bloom;

import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.graphics.BloomLayer;
import io.anuke.mindustry.graphics.BloomLayers;
import io.anuke.ucore.graphics.Hue;
import io.anuke.ucore.util.Tmp;

import static io.anuke.mindustry.Vars.*;

public class PlayerBloomLayer implements BloomLayer{
    @Override
    public String name(){
        return BloomLayers.PLAYERS;
    }

    @Override
    public int priority(){
        return 40;
    }

    @Override
    public void draw(){
        for(Player player : playerGroup.all()){
            if(player.isDead()) continue;

            // mining laser bloom
            if (((BuilderTrait) player).getMineTile() != null) {
                ((BuilderTrait) player).drawMining(player);
            }

            // trail bloom
            if(player.getType().isFlying || player.boostHeat > 0.001f){
                player.trail.draw(
                    Hue.mix(player.getType().trailColor, player.getType().trailColorTo, player.getType().isFlying ? 0f : player.boostHeat, Tmp.c1),
                    5f * (player.isFlying() ? 1f : player.boostHeat));
            }

            // mech bloom
            for(int i = 0; i < player.mechAbilities.size; i++){
                player.mechAbilities.get(i).drawBloom(player);
            }
        }
    }
}
