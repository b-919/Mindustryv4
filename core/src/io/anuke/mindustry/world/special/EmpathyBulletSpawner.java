package io.anuke.mindustry.world.special;

import io.anuke.ucore.function.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Interpolation;
import io.anuke.ucore.util.Mathf;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.gen.Call;

public class EmpathyBulletSpawner extends DrawEntity{
    public float lifetime, time;
    float size = 30f;
    Posc owner;
    Cons<EmpathyBulletSpawner> cons;
    public float[] datas = new float[3];

    public static void create(Posc owner, float x, float y, float size, float lifetime, Cons<EmpathyBulletSpawner> cons){
        EmpathyBulletSpawner e = new EmpathyBulletSpawner();
        e.owner = owner;
        e.x = x;
        e.y = y;
        e.size = size;
        e.lifetime = lifetime;
        e.cons = cons;
        e.add();
    }

    @Override
    public void update(){
        if(owner != null){
            Tmp.v1.set(owner.x(), owner.y()).sub(x, y).scl(1f / 3f).limit(2.5f).scl(Timers.delta());
            x += Tmp.v1.x;
            y += Tmp.v1.y;
        }

        cons.get(this);

        if((time += Timers.delta()) > lifetime){
            remove();
        }
    }

    @Override
    public float clipSize(){
        return size * 2 + 5f;
    }

    @Override
    public void draw(){
        float alpha = Mathf.clamp(time / 16f) * Mathf.clamp((lifetime - time) / 16f);
        Draw.color(FlamePal.empathyAdd);
        Draw.alpha(alpha);
        Draw.blend(Blending.additive);
        Lines.stroke(1f);
        Lines.circle(x, y, size);
        GraphicUtils.polygram(x, y, Time.time, size, 11, 2);
        GraphicUtils.polygram(x, y, -Time.time, size * 0.9f, 7, 2);
        Draw.blend();
    }
}
