package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.entities.Leg;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.graphics.CapStyle;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

/**
 * Shared body rendering for all unit archetypes.
 * <p>
 * Content-specific extras (legs configured through {@link io.anuke.mindustry.entities.units.UnitType},
 * props, wings, biomass cells) are still drawn by their owning class until they are described as
 * {@code UnitPart} data.
 */
public final class UnitRenderer{
    private UnitRenderer(){
    }

    public static void drawGround(GroundUnit unit){
        Draw.alpha(unit.hitTime / Unit.hitDuration);

        float ft = Mathf.sin(unit.walkTime * unit.type.speed * 5f, 6f, 2f);

        Floor floor = unit.getFloorOn();

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, 0.5f);
        }

        for(int i : Mathf.signs){
            Draw.rect(unit.type.legRegion,
                    unit.x + Angles.trnsx(unit.baseRotation, ft * i),
                    unit.y + Angles.trnsy(unit.baseRotation, ft * i),
                    12f * i, 12f - Mathf.clamp(ft * i, 0, 2), unit.baseRotation - 90);
        }

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, unit.getDrownTime() * 0.4f);
        }else{
            Draw.tint(Color.WHITE);
        }

        Draw.rect(unit.type.baseRegion, unit.x, unit.y, unit.baseRotation - 90);

        Draw.rect(unit.type.region, unit.x, unit.y, unit.rotation - 90);

        unit.drawWeaponMounts();

        unit.drawItems();

        for(int i = 0; i < unit.abilities.size; i++){
            unit.abilities.get(i).draw(unit);
        }

        Draw.alpha(1f);
    }

    public static void drawFlying(FlyingUnit unit){
        Draw.alpha(unit.hitTime / Unit.hitDuration);

        Draw.rect(unit.type.name, unit.x, unit.y, unit.rotation - 90);

        Draw.alpha(unit.hitTime / Unit.hitDuration);
        unit.drawWeaponMounts();

        unit.drawItems();

        for(int i = 0; i < unit.abilities.size; i++){
            unit.abilities.get(i).draw(unit);
        }

        Draw.alpha(1f);
    }

    public static void drawLegs(LegsUnit unit){
        Draw.alpha(unit.hitTime / Unit.hitDuration);

        Floor floor = unit.getFloorOn();

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, 0.5f);
        }

        //legs are drawn front first
        for(int j = unit.legs.length - 1; j >= 0; j--){
            int i = (j % 2 == 0 ? j / 2 : unit.legs.length - 1 - j / 2);
            Leg leg = unit.legs[i];
            boolean flip = i >= unit.legs.length / 2f;
            int flips = Mathf.sign(flip);

            Vector2 position = unit.legOffset(unit.offsetVec, i).add(unit.x, unit.y);

            unit.tmp1.set(leg.base).sub(leg.joint).scl(-1f).setLength(unit.type.legExtension);

            if(unit.type.footRegion != null){
                Draw.rect(unit.type.footRegion, leg.base.x, leg.base.y, Angles.angle(position.x, position.y, leg.base.x, leg.base.y));
            }

            Lines.stroke(unit.type.legRegion.getRegionHeight() * flips);
            Lines.line(unit.type.legRegion, position.x, position.y, leg.joint.x, leg.joint.y, CapStyle.none, 0f);

            Lines.stroke(unit.type.legBaseRegion.getRegionHeight() * flips);
            Lines.line(unit.type.legBaseRegion, leg.joint.x + unit.tmp1.x, leg.joint.y + unit.tmp1.y, leg.base.x, leg.base.y, CapStyle.none, 0f);

            if(unit.type.jointRegion != null){
                Draw.rect(unit.type.jointRegion, leg.joint.x, leg.joint.y);
            }
        }

        //base joints are drawn after everything else
        if(unit.type.baseJointRegion != null){
            for(int j = unit.legs.length - 1; j >= 0; j--){
                Vector2 position = unit.legOffset(unit.offsetVec, (j % 2 == 0 ? j / 2 : unit.legs.length - 1 - j / 2)).add(unit.x, unit.y);
                Draw.rect(unit.type.baseJointRegion, position.x, position.y, unit.baseRotation);
            }
        }

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, unit.getDrownTime() * 0.4f);
        }else{
            Draw.tint(Color.WHITE);
        }

        Draw.rect(unit.type.baseRegion, unit.x, unit.y, unit.baseRotation - 90);

        Draw.rect(unit.type.region, unit.x, unit.y, unit.rotation - 90);

        unit.drawWeaponMounts();

        unit.drawItems();

        for(int i = 0; i < unit.abilities.size; i++){
            unit.abilities.get(i).draw(unit);
        }

        Draw.reset();
    }

    public static void drawTank(TankUnit unit){
        Draw.alpha(unit.hitTime / Unit.hitDuration);

        float speed = unit.getVelocity().len();
        float speedFrac = Mathf.clamp(speed / unit.type.maxVelocity);

        float trackVibrate = Mathf.sin(unit.walkTime * unit.type.speed * 12f, 2f, 0.5f) * speedFrac;
        float trackJitter = Mathf.sin(unit.walkTime * unit.type.speed * 18f + 1.7f, 1.5f, 0.15f) * speedFrac;

        Floor floor = unit.getFloorOn();

        // Soft shadow
        if(!io.anuke.mindustry.core.Renderer.captureReflections){
            Draw.color(0f, 0f, 0f, 0.35f);
            Draw.rect(unit.type.iconRegion, unit.x, unit.y - 1.5f);
            Draw.color(Color.WHITE);
        }

        // Treads
        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, 0.5f);
        }

        for(int i : Mathf.signs){
            Draw.rect(unit.type.treadRegion,
                    unit.x + Angles.trnsx(unit.baseRotation, trackJitter * i, trackVibrate),
                    unit.y + Angles.trnsy(unit.baseRotation, trackJitter * i, trackVibrate),
                    16 * i, 24f, unit.baseRotation - 90);
        }

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, unit.getDrownTime() * 0.4f);
        }else{
            Draw.tint(Color.WHITE);
        }

        // Hull base plate
        Draw.rect(unit.type.baseRegion, unit.x, unit.y, unit.baseRotation - 90);

        // Body at baseRotation (tank hull faces track direction)
        Draw.rect(unit.type.region, unit.x, unit.y, unit.baseRotation - 90);

        // Turret
        float trY = -unit.weapon.getRecoil(unit, true);
        Draw.rect(unit.weapon.equipRegion,
                unit.x + Angles.trnsx(unit.rotation - 90, unit.weapon.x, trY),
                unit.y + Angles.trnsy(unit.rotation - 90, unit.weapon.x, trY),
                unit.weaponRotation - 90);

        // Items
        unit.drawItems();

        for(int i = 0; i < unit.abilities.size; i++){
            unit.abilities.get(i).draw(unit);
        }

        Draw.alpha(1f);
    }
}
