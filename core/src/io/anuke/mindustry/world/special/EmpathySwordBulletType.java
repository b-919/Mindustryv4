package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.math.Interpolation;
import io.anuke.ucore.util.Mathf;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.mindustry.content.*;
import io.anuke.mindustry.entities.bullet.*;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.gen.Call;

public class EmpathySwordBulletType extends BulletType{
    public EmpathySwordBulletType(){
        super(18f, 400f);
        lifetime = 1.1f * 60f;
        pierce = pierceArmor = pierceBuilding = true;
        trailColor = FlamePal.empathy;
        drag = 0.005f;
        collides = collidesTiles = false;
        hittable = false;
        hitEffect = Fx.hitBulletColor;
        hitColor = FlamePal.empathy;
        keepVelocity = false;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        if(b.timer(0, 5f)){
            float length = 40 / 2f;
            float dam = 370f;
            Vector2 v = Tmp.v1.trns(b.rotation(), length);
            Utils.hitLaser(b.team, 2f, -v.x + b.x, -v.y + b.y, v.x + b.x, v.y + b.y, null, h -> false, (h, x, y) -> {
                hit(b, x, y);
                boolean lethal = h.maxHealth() < dam * 2f;
                float tdam = Math.max(dam, h.maxHealth() / 700f);
                if(h instanceof Unit u){
                    EmpathyDamage.damageUnit(u, tdam, lethal, null);
                }else if(h instanceof Building build){
                    EmpathyDamage.damageBuilding(build, tdam, true, null);
                }
            });
        }
    }

    @Override
    public void draw(Bullet b){
        TextureRegion r1 = EmpathyRegions.sword, r2 = EmpathyRegions.swordSide;
        //float fin = b.fin();
        float fin = Mathf.clamp(b.time / ((r1.width * Draw.scl * 0.5f) / speed));
        float rot = b.time * 3f + Mathf.randomSeedRange(b.id, 180f);
        float w = MathUtils.cosDeg(rot);
        float h = MathUtils.sinDeg(rot);

        TextureRegion t1 = Tmp.tr1, t2 = Tmp.tr2;
        t1.set(r1);
        t2.set(r2);
        float u1 = Mathf.lerp(r1.u2, r1.u, fin);
        float u2 = Mathf.lerp(r2.u2, r2.u, fin);
        t1.setU(u1);
        t2.setU(u2);

        Draw.color();
        Draw.rect(t1, b.x, b.y, t1.width * Draw.scl * 0.5f, t1.height * Draw.scl * 0.5f * w, b.rotation());
        Draw.rect(t2, b.x, b.y, t2.width * Draw.scl * 0.5f, t2.height * Draw.scl * 0.5f * h, b.rotation());
    }

    @Override
    public void drawLight(Bullet b){

    }
}
