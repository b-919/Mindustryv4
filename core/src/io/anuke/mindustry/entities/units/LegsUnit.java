package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.entities.Damage;
import io.anuke.mindustry.entities.Leg;
import io.anuke.mindustry.graphics.InverseKinematics;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.CapStyle;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;

import static io.anuke.mindustry.Vars.world;
import static io.anuke.mindustry.content.blocks.Blocks.air;

/**
 * A unit type that walks using multiple articulated legs, backported from modern Mindustry.
 * Legs are solved with two-segment inverse kinematics and drawn as articulated segments.
 */
public abstract class LegsUnit extends GroundUnit{
    private static final Translator straightVec = new Translator();
    private static final Translator moveVec = new Translator();
    private static final Translator offsetVec = new Translator();
    private static final Translator tmpVec = new Translator();
    private static final Vector2 tmp1 = new Vector2(), tmp2 = new Vector2(), tmp3 = new Vector2();

    transient Leg[] legs = {};
    transient float totalLength;
    transient float moveSpace;
    transient float lastX, lastY;
    transient Vector2 curMoveOffset = new Vector2();

    @Override
    public void added(){
        super.added();
        resetLegs();
    }

    @Override
    public void update(){
        super.update();
        updateLegs();
    }

    public void resetLegs(){
        int count = Math.max(type.legCount, 1);

        legs = new Leg[count];

        if(type.lockLegBase){
            baseRotation = rotation;
        }

        for(int i = 0; i < legs.length; i++){
            Leg l = new Leg();

            float dstRot = legAngle(i);
            Vector2 baseOffset = legOffset(offsetVec, i).add(x, y);

            moveVec.trns(dstRot, type.legLength / 2f);
            l.joint.set(moveVec).add(baseOffset);

            moveVec.trns(dstRot, type.legLength);
            l.base.set(moveVec).add(baseOffset);

            legs[i] = l;
        }
        totalLength = Mathf.random(100f);
    }

    void updateLegs(){
        float dx = x - lastX, dy = y - lastY;

        if(Mathf.dst(dx, dy) > 0.001f){
            baseRotation = Mathf.slerpDelta(baseRotation, Mathf.atan2(dx, dy), type.rotatespeed);
        }

        if(type.lockLegBase){
            baseRotation = rotation;
        }

        float legLength = type.legLength;

        if(legs.length != type.legCount){
            resetLegs();
        }

        float moveSpeed = type.legSpeed;
        int div = Math.max(legs.length / type.legGroupSize, 2);
        moveSpace = legLength / 1.6f / (div / 2f) * type.legMoveSpace;
        totalLength += type.legContinuousMove ? type.speed * status.getSpeedMultiplier() * Timers.delta() : Mathf.dst(dx, dy);

        float trns = moveSpace * 0.85f * type.legForwardScl;

        boolean moving = Mathf.dst(dx, dy) > 0.01f;
        moveVec.set(0, 0);
        if(moving){
            moveVec.trns(Mathf.atan2(dx, dy), trns);
        }
        curMoveOffset.lerp(moveVec, 1f - Mathf.pow(1f - 0.1f, Timers.delta()));

        for(int i = 0; i < legs.length; i++){
            float dstRot = legAngle(i);
            Vector2 baseOffset = legOffset(offsetVec, i).add(x, y);
            Leg l = legs[i];

            clampLength(l.joint.sub(baseOffset), type.legMinLength * legLength / 2f, type.legMaxLength * legLength / 2f).add(baseOffset);
            clampLength(l.base.sub(baseOffset), type.legMinLength * legLength, type.legMaxLength * legLength).add(baseOffset);

            float stageF = (totalLength + i * type.legPairOffset) / moveSpace;
            int stage = (int)stageF;
            int group = stage % div;
            boolean move = i % div == group;
            boolean side = i < legs.length / 2;
            //back legs have reversed directions
            boolean backLeg = Math.abs((i + 0.5f) - legs.length / 2f) <= 0.501f;
            if(backLeg && type.flipBackLegs) side = !side;
            if(type.flipLegSide) side = !side;

            l.moving = move;
            l.stage = moving ? stageF % 1f : Mathf.lerpDelta(l.stage, 0f, 0.1f);

            Tile tile = world.tileWorld(l.base.x, l.base.y);
            Floor floor = tile == null ? (Floor) air : tile.floor();

            if(l.group != group){
                //create an effect when transitioning to a group it can't move in
                if(!move && (moving || !type.legContinuousMove) && i % div == l.group){
                    if(floor.isLiquid && tile != null){
                        Effects.effect(floor.walkEffect, floor.liquidColor, l.base.x, l.base.y);
                    }

                    if(type.legSplashDamage > 0 && !Net.client()){
                        Damage.damage(team, l.base.x, l.base.y, type.legSplashRange, type.legSplashDamage);
                    }
                }

                l.group = group;
            }

            //leg destination
            tmpVec.trns(dstRot, legLength * type.legLengthScl).add(baseOffset).add(curMoveOffset);
            //joint destination, btw don't make joints to end behind the unit
            InverseKinematics.solve(legLength / 2f, legLength / 2f, tmp2.set(l.base).sub(baseOffset), side, tmp3);
            tmp3.add(baseOffset);

            if(move){
                float moveFract = stageF % 1f;

                l.base.lerp(tmpVec, 1f - Mathf.pow(1f - moveFract, Timers.delta()));
                l.joint.lerp(tmp3, 1f - Mathf.pow(1f - moveFract / 2f, Timers.delta()));
            }

            l.joint.lerp(tmp3, 1f - Mathf.pow(1f - moveSpeed / 4f, Timers.delta()));

            //limit again after updating
            clampLength(l.joint.sub(baseOffset), type.legMinLength * legLength / 2f, type.legMaxLength * legLength / 2f).add(baseOffset);
            clampLength(l.base.sub(baseOffset), type.legMinLength * legLength, type.legMaxLength * legLength).add(baseOffset);
        }

        lastX = x;
        lastY = y;
    }

    Vector2 legOffset(Translator out, int index){
        out.trns(defaultLegAngle(index), type.legBaseOffset);

        if(type.legStraightness > 0){
            straightVec.trns(defaultLegAngle(index) - baseRotation, type.legBaseOffset);
            straightVec.y = Mathf.sign(straightVec.y) * type.legBaseOffset * type.legStraightLength;
            straightVec.rotate(baseRotation);
            out.lerp(straightVec, type.baseLegStraightness);
        }

        return out;
    }

    /**@return outwards facing angle of leg at the specified index.*/
    float legAngle(int index){
        if(type.legStraightness > 0){
            return Mathf.slerp(defaultLegAngle(index), (index >= legs.length / 2 ? -90 : 90f) + baseRotation, type.legStraightness);
        }
        return defaultLegAngle(index);
    }

    float defaultLegAngle(int index){
        return baseRotation + 360f / legs.length * index + (360f / legs.length / 2f);
    }

    private static Vector2 clampLength(Vector2 v, float min, float max){
        float len = v.len();
        if(len < min && len > 0.0001f){
            v.scl(min / len);
        }else if(len > max){
            v.scl(max / len);
        }
        return v;
    }

    @Override
    public void draw(){
        Draw.alpha(hitTime / hitDuration);

        Floor floor = getFloorOn();

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, 0.5f);
        }

        //legs are drawn front first
        for(int j = legs.length - 1; j >= 0; j--){
            int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
            Leg leg = legs[i];
            boolean flip = i >= legs.length / 2f;
            int flips = Mathf.sign(flip);

            Vector2 position = legOffset(offsetVec, i).add(x, y);

            tmp1.set(leg.base).sub(leg.joint).scl(-1f).setLength(type.legExtension);

            if(type.footRegion != null){
                Draw.rect(type.footRegion, leg.base.x, leg.base.y, Angles.angle(position.x, position.y, leg.base.x, leg.base.y));
            }

            Lines.stroke(type.legRegion.getRegionHeight() * flips);
            Lines.line(type.legRegion, position.x, position.y, leg.joint.x, leg.joint.y, CapStyle.none, 0f);

            Lines.stroke(type.legBaseRegion.getRegionHeight() * flips);
            Lines.line(type.legBaseRegion, leg.joint.x + tmp1.x, leg.joint.y + tmp1.y, leg.base.x, leg.base.y, CapStyle.none, 0f);

            if(type.jointRegion != null){
                Draw.rect(type.jointRegion, leg.joint.x, leg.joint.y);
            }
        }

        //base joints are drawn after everything else
        if(type.baseJointRegion != null){
            for(int j = legs.length - 1; j >= 0; j--){
                Vector2 position = legOffset(offsetVec, (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2)).add(x, y);
                Draw.rect(type.baseJointRegion, position.x, position.y, baseRotation);
            }
        }

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, drownTime * 0.4f);
        }else{
            Draw.tint(Color.WHITE);
        }

        Draw.rect(type.baseRegion, x, y, baseRotation - 90);

        Draw.rect(type.region, x, y, rotation - 90);

        drawWeaponMounts();

        drawItems();

        Draw.reset();
    }
}
