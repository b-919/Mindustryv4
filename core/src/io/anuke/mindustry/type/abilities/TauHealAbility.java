package io.anuke.mindustry.type.abilities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import io.anuke.mindustry.content.fx.UnitFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.type.Mech;
import io.anuke.mindustry.type.MechAbility;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Shapes;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

/** Heals nearby allies and draws the repair beam/turret. */
public class TauHealAbility implements MechAbility{
    public float healRange = 90f;
    public float healAmount = 0.3f;
    public float healReload = 160f;

    private final Rectangle rect = new Rectangle();
    private boolean wasHealed;
    private TextureRegion turretRegion;

    @Override
    public void load(Mech mech){
        turretRegion = Draw.region("repair-point-turret");
    }

    @Override
    public void update(Player player){
        if(player.timer.get(Player.timerAbility, healReload)){
            wasHealed = false;

            rect.setSize(healRange * 2f).setCenter(player.x, player.y);
            Units.getNearby(player.getTeam(), rect, unit -> {
                if(unit.distanceTo(player) <= healRange){
                    if(unit.health < unit.maxHealth()){
                        Effects.effect(UnitFx.heal, unit);
                        wasHealed = true;
                    }
                    unit.healBy(10f);
                }
            });

            if(wasHealed){
                Effects.effect(UnitFx.healWave, player);
            }
        }

        if(player.healTarget != null && (player.healTarget.isDead() || player.distanceTo(player.healTarget) > healRange ||
                player.healTarget.health >= player.healTarget.maxHealth())){
            player.healTarget = null;
        }else if(player.healTarget != null){
            player.healTarget.health += healAmount * Timers.delta() * player.healStrength;
            player.healTarget.clampHealth();
            player.healRotation = Mathf.slerpDelta(player.healRotation, player.angleTo(player.healTarget), 0.5f);
        }else{
            player.healRotation = Mathf.slerpDelta(player.healRotation, player.rotation, 0.2f);
        }

        if(player.healTarget != null){
            player.healStrength = Mathf.lerpDelta(player.healStrength, 1f, 0.08f * Timers.delta());
        }else{
            player.healStrength = Mathf.lerpDelta(player.healStrength, 0f, 0.07f * Timers.delta());
        }

        if(player.timer.get(Player.timerHeal, 20)){
            player.healTarget = Units.getClosest(player.getTeam(), player.x, player.y, healRange,
                    unit -> unit.health < unit.maxHealth() && unit != player);
        }
    }

    @Override
    public void drawOver(Player player){
        Mech mech = player.mech;
        float tx = player.snappedX() + Angles.trnsx(player.rotation - 90, mech.healTurretOffsetX, mech.healTurretOffsetY);
        float ty = player.snappedY() + Angles.trnsy(player.rotation - 90, mech.healTurretOffsetX, mech.healTurretOffsetY);

        Draw.rect(turretRegion, tx, ty, player.healRotation - 90);

        if(player.healTarget != null && player.healStrength > 0.01f &&
                Angles.angleDist(player.angleTo(player.healTarget), player.healRotation) < 30f){
            float ang = player.angleTo(player.healTarget);
            float len = 5f;

            Draw.color(Color.valueOf("70f17f"));
            Shapes.laser("laser", "laser-end",
                    tx + Angles.trnsx(ang, len), ty + Angles.trnsy(ang, len),
                    player.healTarget.x, player.healTarget.y, player.healStrength);
            Draw.color();
        }
    }
}
