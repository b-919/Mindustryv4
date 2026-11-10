package io.anuke.mindustry.type.abilities;

import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.type.MechAbility;

/** Only allows shooting above a minimum velocity; used by the trident bomber. */
public class VelocityShootAbility implements MechAbility{
    public float minVelocity = 1.2f;

    @Override
    public boolean canShoot(Player player){
        return player.getVelocity().len() > minVelocity;
    }
}
