package io.anuke.mindustry.type.abilities;

import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.content.fx.UnitFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.types.AlphaDrone;
import io.anuke.mindustry.maps.TutorialSector;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.MechAbility;
import io.anuke.ucore.core.Effects;

import static io.anuke.mindustry.Vars.unitGroups;

/** Spawns player-owned alpha drones while the player is shooting. */
public class AlphaDroneAbility implements MechAbility{
    public int maxDrones = 3;
    public float buildTime = 20f;

    @Override
    public void update(Player player){
        if(player.isShooting && getDrones(player) < maxDrones && !TutorialSector.supressDrone()){
            player.timer.get(Player.timerAbility, buildTime);

            if(player.timer.getTime(Player.timerAbility) > buildTime / 2f){
                if(!Net.client()){
                    AlphaDrone drone = (AlphaDrone)UnitTypes.alphaDrone.create(player.getTeam());
                    drone.leader = player;
                    drone.set(player.x, player.y);
                    drone.add();

                    Effects.effect(UnitFx.unitLand, player);
                }
            }
        }
    }

    private int getDrones(Player player){
        int sum = 0;
        for(BaseUnit unit : unitGroups[player.getTeam().ordinal()].all()){
            if(unit instanceof AlphaDrone && ((AlphaDrone)unit).leader == player) sum++;
        }
        return sum;
    }
}
