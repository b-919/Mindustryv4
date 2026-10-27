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
            if(player.mech.flying || player.boostHeat > 0.001f){
                player.trail.draw(
                    Hue.mix(player.mech.trailColor, player.mech.trailColorTo, player.mech.flying ? 0f : player.boostHeat, Tmp.c1),
                    5f * (player.isFlying() ? 1f : player.boostHeat));
            }

            // mech bloom
            player.mech.drawBloom(player);
        }
    }
}
