package io.anuke.mindustry.entities.units.types;

import com.badlogic.gdx.math.Rectangle;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.GroundUnit;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Translator;

public class GroundHealUnit extends GroundUnit {
    private static Rectangle rect = new Rectangle();
    private static Translator moveVec = new Translator();

    protected Unit followTarget;

    @Override
    public void behavior() {
        boolean isEnemyTeamNonPvP = getTeam() == Vars.state.enemyTeam && !Vars.state.mode.isPvp && Vars.state.mode != GameMode.customAttackMode;
        if (isEnemyTeamNonPvP) {
            Unit ally = Units.getClosest(getTeam(), x, y, 400f, u -> u != this && !u.isFlying());
            if (ally != null) {
                if (distanceTo(ally) > 40f) {
                    path.moveWithPathfinding(this, ally.x, ally.y);
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
                path.moveWithPathfinding(this, predictX, predictY);
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
                path.moveWithPathfinding(this, predictX, predictY);
            } else if (dist < followDist - 20f) {
                path.moveWithPathfinding(this, predictX, predictY);
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
}
