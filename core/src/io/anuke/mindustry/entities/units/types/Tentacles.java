package io.anuke.mindustry.entities.units.types;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.entities.units.GroundUnit;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Hue;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

public class Tentacles extends GroundUnit{
    private static final int tentacleCount = 24;
    private static final int segments = 7;
    private static final float baseLength = 17f;
    private static final Color[] colors = {
            Color.valueOf("ae0707"),
            Color.valueOf("940909"),
            Color.valueOf("711212"),
            Color.valueOf("4f1212")
    };
    private static final Color tmpColor = new Color();

    @Override
    public void draw(){
        float time = Timers.time();
        float offset = id * 125f;

        Draw.alpha(1f);

        for(int i = 0; i < tentacleCount; i++){
            float baseAngle = time * 0.1f + offset + i * (360f / tentacleCount);
            float lastX = x, lastY = y;
            float lastAngle = baseAngle;
            float scale = 1f + Mathf.sin(time - i * 40f, 30f, 0.2f);

            if(target != null){
                float dst = Angles.angleDist(angleTo(target), lastAngle);
                scale += Mathf.clamp(1f - dst / 60f, 0f, 1f) * 0.7f;
            }

            scale = Mathf.clamp(scale, 0f, 2f);

            for(int s = 0; s < segments; s++){
                Lines.stroke(segments - s);

                float angle = lastAngle + Mathf.sin(
                        time + offset + i * (99f + s * 9f) + s * 99f,
                        Math.max(38f - s * 4f, 6f), 10f);

                float length = Math.max(baseLength - s * 2f, 4f) * scale;
                float nextX = lastX + Angles.trnsx(angle, length);
                float nextY = lastY + Angles.trnsy(angle, length);

                Draw.color(Hue.mix(colors, tmpColor, (s + 1f) / segments));
                Lines.line(lastX, lastY, nextX, nextY);

                lastX = nextX;
                lastY = nextY;
                lastAngle = angle;
            }
        }

        Lines.stroke(1f);
        Draw.color(dark());
        Draw.rect(type.region, x, y, rotation - 90);

        if(type.drawWeaponMounts) drawWeaponMounts();
        drawItems();

        Draw.color();
    }

    @Override
    public void drawShadow(float offsetX, float offsetY){
        Draw.color(dark());
        Draw.rect(type.region, x + offsetX, y + offsetY, rotation - 90);
        Draw.color();
    }

    private static Color dark(){
        return colors[colors.length - 1];
    }
}
