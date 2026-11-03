package io.anuke.mindustry.entities.units.types;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.GroundUnit;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Shapes;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Translator;

public class GroundHealUnit extends GroundUnit {
    private static Rectangle rect = new Rectangle();
    private static ObjectSet<String> warnedMissingHealTurretRegions = new ObjectSet<>();
    private static Translator moveVec = new Translator();

    public boolean healTurretMirror = false;
    public float healSpeed = 0.3f;

    protected static int timerHeal = timerIndex++;
    protected Unit healTarget;
    protected Unit followTarget;
    protected float healStrength, healRotation = 90;
    protected TextureRegion healTurretRegion;

    @Override
    public void init(io.anuke.mindustry.entities.units.UnitType type, Team team) {
        super.init(type, team);

        String turretRegionName = type.name + "-heal-turret";
        TextureRegion fallback = type.region != null ? type.region : Draw.region("clear");
        if (!Draw.hasRegion(turretRegionName) && warnedMissingHealTurretRegions.add(turretRegionName)) {
            Log.err("Missing texture region: '" + turretRegionName + " add it to sprites atlas or update it");
        }
        healTurretRegion = Draw.region(turretRegionName, fallback);
    }

    @Override
    public void update() {
        super.update();

        if (healTarget != null && (healTarget.isDead() || distanceTo(healTarget) > type.healRange ||
                healTarget.health >= healTarget.maxHealth())) {
            healTarget = null;
        } else if (healTarget != null) {
            healTarget.health += healSpeed * Timers.delta() * healStrength;
            healTarget.clampHealth();
            healRotation = Mathf.slerpDelta(healRotation, angleTo(healTarget), 0.5f);
        } else {
            healRotation = Mathf.slerpDelta(healRotation, rotation, 0.2f);
        }

        if (healTarget != null) {
            healStrength = Mathf.lerpDelta(healStrength, 1f, 0.08f * Timers.delta());
        } else {
            healStrength = Mathf.lerpDelta(healStrength, 0f, 0.07f * Timers.delta());
        }

        if (getTimer().get(timerHeal, 20)) {
            healTarget = findBestHealTarget();
        }
    }

    protected Unit findBestHealTarget() {
        Unit[] bestNonHealer = {null};
        float[] bestNonHealerHealth = {-1f};

        Unit[] bestHealer = {null};
        float[] bestHealerHealth = {-1f};

        Units.getNearby(getTeam(), x, y, type.healRange, unit -> {
            if (unit == this || unit.health >= unit.maxHealth()) return;

            if (!unit.isHealer()) {
                if (unit.health > bestNonHealerHealth[0]) {
                    bestNonHealerHealth[0] = unit.health;
                    bestNonHealer[0] = unit;
                }
            } else {
                if (unit.health > bestHealerHealth[0]) {
                    bestHealerHealth[0] = unit.health;
                    bestHealer[0] = unit;
                }
            }
        });

        return bestNonHealer[0] != null ? bestNonHealer[0] : bestHealer[0];
    }

    @Override
    public void behavior() {
        boolean isEnemyTeamNonPvP = getTeam() == Vars.state.enemyTeam && !Vars.state.mode.isPvp && Vars.state.mode != GameMode.customAttackMode;
        if (isEnemyTeamNonPvP) {
            Unit ally = Units.getClosest(getTeam(), x, y, 400f, u -> u != this && !u.isFlying());
            if (ally != null) {
                if (distanceTo(ally) > 40f) {
                    moveWithPathfinding(ally.x, ally.y);
                }
            } else {
                super.behavior();
            }
            return;
        }

        final float avoidDistUnit = 250f;
        final float avoidDistTurret = 350f;
        final float searchDist = 450f;

        final float[] targetX = {0}, targetY = {0};
        final int[] count = {0};

        rect.setSize(searchDist * 2f).setCenter(x, y);
        Units.getNearbyEnemies(getTeam(), rect, enemy -> {
            float dist = distanceTo(enemy);
            if (dist < avoidDistUnit) {
                float ang = angleTo(enemy);
                targetX[0] -= Angles.trnsx(ang, avoidDistUnit - dist);
                targetY[0] -= Angles.trnsy(ang, avoidDistUnit - dist);
                count[0]++;
            }
        });

        TileEntity turret = Units.findEnemyTile(getTeam(), x, y, searchDist, t -> t.block().flags != null && t.block().flags.contains(BlockFlag.turret));
        if (turret != null) {
            float dist = distanceTo(turret);
            if (dist < avoidDistTurret) {
                float ang = angleTo(turret);
                targetX[0] -= Angles.trnsx(ang, avoidDistTurret - dist);
                targetY[0] -= Angles.trnsy(ang, avoidDistTurret - dist);
                count[0]++;
            }
        }

        if (count[0] > 0) {
            clearMovePath();
            moveTo(x + targetX[0], y + targetY[0]);
            return;
        }

        Unit lowHealthAlly = findBestFollowTarget();

        if (lowHealthAlly != null) {
            followTarget = lowHealthAlly;
            float dst = distanceTo(lowHealthAlly);
            float behindDist = type.hitsize + lowHealthAlly.getSize() + 10f;
            float desiredHealDist = type.healRange * 0.6f;

            getBehindTarget(lowHealthAlly, behindDist, moveVec);
            float predictX = lowHealthAlly.x + moveVec.x + lowHealthAlly.getTargetVelocityX() * 10f;
            float predictY = lowHealthAlly.y + moveVec.y + lowHealthAlly.getTargetVelocityY() * 10f;

            if (dst > desiredHealDist || dst < behindDist * 0.5f) {
                moveWithPathfinding(predictX, predictY);
            }
        } else {
            followTarget = null;
            idleFollow();
        }
    }

    protected Unit findBestFollowTarget() {
        Unit[] best = {null};
        float[] bestScore = {-1f};

        Units.getNearby(getTeam(), x, y, 400f, unit -> {
            if (unit == this || unit.isFlying()) return;

            float dist = distanceTo(unit);
            float healthScore = unit.health / unit.maxHealth();
            float score = healthScore * 1000f - dist;
            if (!unit.isHealer()) score += 500f;

            if (score > bestScore[0]) {
                bestScore[0] = score;
                best[0] = unit;
            }
        });

        return best[0];
    }

    protected void idleFollow() {
        Unit ally = findBestIdleFollowTarget();
        if (ally != null) {
            followTarget = ally;
            float dist = distanceTo(ally);
            float followDist = 70f;
            float behindDist = type.hitsize + ally.getSize() + 10f;

            getBehindTarget(ally, behindDist, moveVec);
            float predictX = ally.x + moveVec.x + ally.getTargetVelocityX() * 10f;
            float predictY = ally.y + moveVec.y + ally.getTargetVelocityY() * 10f;

            if (dist > followDist) {
                moveWithPathfinding(predictX, predictY);
            } else if (dist < followDist - 20f) {
                moveWithPathfinding(predictX, predictY);
            }
        } else {
            followTarget = null;
            super.behavior();
        }
    }

    protected Unit findBestIdleFollowTarget() {
        Unit[] best = {null};
        float[] bestScore = {-1f};

        Units.getNearby(getTeam(), x, y, 500f, unit -> {
            if (unit == this || unit.isFlying()) return;

            float healthScore = unit.health / unit.maxHealth();
            float dist = distanceTo(unit);
            float score = healthScore * 1000f - dist;
            if (!unit.isHealer()) score += 500f;

            if (score > bestScore[0]) {
                bestScore[0] = score;
                best[0] = unit;
            }
        });

        return best[0];
    }

    @Override
    public void draw() {
        super.draw();

        if (healTurretRegion == null) {
            String turretRegionName = type.name + "-heal-turret";
            TextureRegion fallback = type.region != null ? type.region : Draw.region("clear");
            healTurretRegion = Draw.region(turretRegionName, fallback);
        }

        Draw.alpha(hitTime / hitDuration);
        for (int i : Mathf.signs) {
            if (i < 0 && !healTurretMirror) continue;

            float tx = x + Angles.trnsx(rotation - 90, type.healTurretOffsetX * i, type.healTurretOffsetY);
            float ty = y + Angles.trnsy(rotation - 90, type.healTurretOffsetX * i, type.healTurretOffsetY);

            if (healTurretRegion != null && healTurretRegion.getTexture() != null) {
                Draw.rect(healTurretRegion, tx, ty, healRotation - 90);
            }

            if (healTarget != null && healStrength > 0.01f &&
                    Angles.angleDist(angleTo(healTarget), healRotation) < 30f) {
                float ang = angleTo(healTarget);
                float len = 5f;

                Draw.color(Color.valueOf("70f17f"));
                Shapes.laser("laser", "laser-end",
                        tx + Angles.trnsx(ang, len), ty + Angles.trnsy(ang, len),
                        healTarget.x, healTarget.y, healStrength);
                Draw.color();
            }
        }
    }
}
