package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Mathf;

public class ApathySentryUnit extends BaseUnit{
    float moveX, moveY, moveTime;
    public TargetTrait target;
    public float reload = 0f;
    public float healDelay = 0f;
    public float healFade = 0f;
    public boolean active;
    ApathyIUnit owner;

    void moveSentry(float wx, float wy){
        moveX = wx;
        moveY = wy;
        moveTime = 0f;
    }

    @Override
    public void onCommand(io.anuke.mindustry.entities.units.UnitCommand command){
    }

    @Override
    public void draw(){
        TextureRegion r = type.region;

        if(healDelay > 0){
            Draw.color(Color.valueOf("6ee7a8"));
            Lines.stroke(3f * healFade * (1f + Mathf.absin(1f, 0.5f, 1f)));
            Lines.line(x, y, owner.x, owner.y);
        }

        Draw.color();
        if(r != null){
            Draw.rect(r, x, y, r.getRegionWidth() * Draw.scale() * 0.5f, r.getRegionHeight() * Draw.scale() * 0.5f, rotation - 90f);
        }
    }
}
