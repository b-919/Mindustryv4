package io.anuke.mindustry.entities.units.types;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.entities.units.BiomassGroundUnit;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

public class BiomassExterminator extends BiomassGroundUnit {
    @Override
    public void draw(){
        Draw.alpha(hitTime / hitDuration);

        float ft = Mathf.sin(walkTime * type.speed*5f, 6f, 2f);

        Floor floor = getFloorOn();

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, 0.5f);
        }

        for(int i : Mathf.signs){
            Draw.rect(type.legRegion,
                    x + Angles.trnsx(baseRotation, ft * i),
                    y + Angles.trnsy(baseRotation, ft * i),
                    32 * i, 32 - Mathf.clamp(ft * i, 0, 4), baseRotation - 90);
        }

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, drownTime * 0.4f);
        }else{
            Draw.tint(Color.WHITE);
        }

        Draw.rect(type.baseRegion, x, y, baseRotation - 90);

        Draw.rect(type.region, x, y, rotation - 90);

        for(int i : Mathf.signs){
            if(!weapon.mirror && i < 0) continue;
            float tra = rotation - 90, trY = -weapon.getRecoil(this, i > 0);
            float w = i > 0 ? -12 : 16;
            float h = i > 0 ? 32 : 42;
            Draw.rect(weapon.equipRegion,
                    x + Angles.trnsx(tra, weapon.x * i, trY),
                    y + Angles.trnsy(tra, weapon.x * i, trY), w, h, rotation - 90);
        }

        drawItems();

        Draw.alpha(1f);
    }
}
