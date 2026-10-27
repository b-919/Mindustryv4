package io.anuke.mindustry.entities.units.types;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

import static io.anuke.ucore.core.Timers.delta;

public class BombDrone extends FlyingUnit {
    float propRot;

    @Override
    public void update(){
        super.update();
        propRot += 25f * delta();
    }
    @Override
    public void draw(){
        Draw.alpha(hitTime / hitDuration);

        Draw.rect(type.name, x, y, rotation - 90);

        drawItems();

        Draw.alpha(1f);

        drawProp(-3.5f,  3.45f,  propRot) ;
        drawProp(-3.5f,  -3.45f,  propRot);
        drawProp( 3.5f,  3.45f, -propRot);
        drawProp( 3.5f, -3.45f , -propRot);
    }

    void drawProp(float localX, float localY, float spin){
        float wx = x + Angles.trnsx(rotation, localX, localY);
        float wy = y + Angles.trnsy(rotation, localX, localY);

        Draw.alpha(hitTime / hitDuration);
        Draw.color();
        Draw.alpha(0f);
        Draw.rect(type.name + "-propeller", wx, wy, spin);
    }
}
