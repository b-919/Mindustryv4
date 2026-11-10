package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Shapes;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Mathf;

/**
 * Heals one nearby damaged ally at a time and draws a repair beam towards it.
 * <p>
 * This is the ability form of the former {@code GroundHealUnit} update/draw code.
 */
public class HealBeamAbility extends Ability{
    private static final Color beamColor = Color.valueOf("70f17f");

    public float healSpeed = 0.3f;
    public boolean turretMirror = false;

    private final Unit[] bestNonHealer = new Unit[1];
    private final Unit[] bestHealer = new Unit[1];
    private final float[] bestNonHealerHealth = new float[1];
    private final float[] bestHealerHealth = new float[1];

    private TextureRegion turretRegion;
    private Unit healTarget;
    private float healStrength, healRotation = 90f, timer;

    @Override
    public void init(UnitType type){
        String name = type.name + "-heal-turret";
        TextureRegion fallback = type.region != null ? type.region : Draw.region("clear");
        if(!Draw.hasRegion(name)){
            Log.err("Missing texture region: '" + name + "' add it to sprites atlas or update it");
        }
        turretRegion = Draw.region(name, fallback);
    }

    @Override
    public void update(BaseUnit unit){
        if(healTarget != null && (healTarget.isDead() || unit.distanceTo(healTarget) > unit.getType().healRange ||
                healTarget.health >= healTarget.maxHealth())){
            healTarget = null;
        }else if(healTarget != null){
            healTarget.health += healSpeed * Timers.delta() * healStrength;
            healTarget.clampHealth();
            healRotation = Mathf.slerpDelta(healRotation, unit.angleTo(healTarget), 0.5f);
        }else{
            healRotation = Mathf.slerpDelta(healRotation, unit.rotation, 0.2f);
        }

        if(healTarget != null){
            healStrength = Mathf.lerpDelta(healStrength, 1f, 0.08f * Timers.delta());
        }else{
            healStrength = Mathf.lerpDelta(healStrength, 0f, 0.07f * Timers.delta());
        }

        timer += Timers.delta();
        if(timer >= 20f){
            timer = 0f;
            healTarget = findBestHealTarget(unit);
        }
    }

    @Override
    public void draw(BaseUnit unit){
        if(turretRegion == null) return;

        Draw.alpha(unit.hitTime / Unit.hitDuration);
        for(int i : Mathf.signs){
            if(i < 0 && !turretMirror) continue;

            float tx = unit.x + Angles.trnsx(unit.rotation - 90, unit.getType().healTurretOffsetX * i, unit.getType().healTurretOffsetY);
            float ty = unit.y + Angles.trnsy(unit.rotation - 90, unit.getType().healTurretOffsetX * i, unit.getType().healTurretOffsetY);

            if(turretRegion.getTexture() != null){
                Draw.rect(turretRegion, tx, ty, healRotation - 90);
            }

            if(healTarget != null && healStrength > 0.01f &&
                    Angles.angleDist(unit.angleTo(healTarget), healRotation) < 30f){
                float ang = unit.angleTo(healTarget);
                float len = 5f;

                Draw.color(beamColor);
                Shapes.laser("laser", "laser-end",
                        tx + Angles.trnsx(ang, len), ty + Angles.trnsy(ang, len),
                        healTarget.x, healTarget.y, healStrength);
                Draw.color();
            }
        }
        Draw.alpha(1f);
    }

    private Unit findBestHealTarget(BaseUnit unit){
        bestNonHealer[0] = null;
        bestNonHealerHealth[0] = -1f;
        bestHealer[0] = null;
        bestHealerHealth[0] = -1f;

        Units.getNearby(unit.getTeam(), unit.x, unit.y, unit.getType().healRange, other -> {
            if(other == unit || other.health >= other.maxHealth()) return;

            if(!other.isHealer()){
                if(other.health > bestNonHealerHealth[0]){
                    bestNonHealerHealth[0] = other.health;
                    bestNonHealer[0] = other;
                }
            }else{
                if(other.health > bestHealerHealth[0]){
                    bestHealerHealth[0] = other.health;
                    bestHealer[0] = other;
                }
            }
        });

        Unit result = bestNonHealer[0] != null ? bestNonHealer[0] : bestHealer[0];
        bestNonHealer[0] = null;
        bestHealer[0] = null;
        return result;
    }
}
