package io.anuke.mindustry.type.abilities;

import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.type.MechAbility;

public class DebugRegenAbility extends MechAbility{
    @Override
    public void update(Player player){
        if(player.health < player.maxHealth()){
            player.healBy(player.maxHealth() - player.health);
        }
    }
}
