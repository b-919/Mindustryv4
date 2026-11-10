package io.anuke.mindustry.type.abilities;

import io.anuke.mindustry.content.fx.UnitFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.effect.Lightning;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.MechAbility;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

/** Creates a lightning ring when the mech lands after boosting. */
public class LandShockAbility implements MechAbility{
    public float cooldown = 120f;
    public float lightningDamage = 17f;
    public int lightningLength = 14;

    @Override
    public void onLand(Player player){
        if(player.timer.get(Player.timerAbility, cooldown)){
            Effects.shake(1f, 1f, player);
            Effects.effect(UnitFx.landShock, player);
            for(int i = 0; i < 8; i++){
                Timers.run(Mathf.random(8f), () -> Lightning.create(player.getTeam(), Palette.lancerLaser, lightningDamage, player.x, player.y, Mathf.random(360f), lightningLength));
            }
        }
    }
}
