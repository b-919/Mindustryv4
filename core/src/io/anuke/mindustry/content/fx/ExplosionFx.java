package io.anuke.mindustry.content.fx;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Drawf;
import io.anuke.mindustry.game.ContentList;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

public class ExplosionFx extends FxList implements ContentList{
    public static Effect shockwave, bigShockwave, nuclearShockwave, explosion, blockExplosion, blockExplosionSmoke, fusionShockwave;

    @Override
    public void load(){

        shockwave = new Effect(10f, 80f, e -> {
            Draw.color(Color.WHITE, Color.LIGHT_GRAY, e.fin());
            Lines.stroke(e.fout() * 2f + 0.2f);
            Lines.circle(e.x, e.y, e.fin() * 28f);
            Draw.reset();
        });

        bigShockwave = new Effect(10f, 80f, e -> {
            Draw.color(Color.WHITE, Color.LIGHT_GRAY, e.fin());
            Lines.stroke(e.fout() * 3f);
            Lines.circle(e.x, e.y, e.fin() * 50f);
            Draw.reset();
        });
        //bigShockwave.emitLight = true; ignore I was testing
        //bigShockwave.lightRadius = 70f;
        //bigShockwave.lightOpacity = 0.6f;

        nuclearShockwave = new Effect(10f, 200f, e -> {
            Draw.color(Color.WHITE, Color.LIGHT_GRAY, e.fin());
            Lines.stroke(e.fout() * 3f + 0.2f);
            Lines.poly(e.x, e.y, 40, e.fin() * 140f);
            Draw.reset();
        });
        nuclearShockwave.emitLight = true;
        nuclearShockwave.lightRadius = 160f;
        nuclearShockwave.lightOpacity = 0.8f;
        nuclearShockwave.lightColor = Palette.lighterOrange;

        fusionShockwave = new Effect(20f, 400f, e -> {
            Draw.color(Color.LIGHT_GRAY, Color.DARK_GRAY, e.fin());
            Lines.stroke(e.fout() * 3f + 0.2f);
            Lines.poly(e.x, e.y, 80, e.fin() * 280f);
            Draw.reset();
        });
        fusionShockwave.emitLight = true;
        fusionShockwave.lightRadius = 320f;
        fusionShockwave.lightOpacity = 1.6f;
        fusionShockwave.lightColor = Color.ORANGE;

        explosion = new Effect(30, e -> {
            e.scaled(7, i -> {
                Lines.stroke(3f * i.fout());
                Lines.circle(e.x, e.y, 3f + i.fin() * 10f);
                Drawf.light(e.x, e.y, 12f * i.fout() + 2f, Palette.lightOrange, 0.5f * i.fout());
            });

            Draw.color(Color.GRAY);

            Angles.randLenVectors(e.id, 6, 2f + 19f * e.finpow(), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, e.fout() * 3f + 0.5f);
                Fill.circle(e.x + x / 2f, e.y + y / 2f, e.fout() * 1f);
            });

            Draw.color(Palette.lighterOrange, Palette.lightOrange, Color.GRAY, e.fin());
            Lines.stroke(1.5f * e.fout());

            Angles.randLenVectors(e.id + 1, 8, 1f + 23f * e.finpow(), (x, y) -> {
                Lines.lineAngle(e.x + x, e.y + y, Mathf.atan2(x, y), 1f + e.fout() * 3f);
                Drawf.light(e.x + x, e.y + y, 6f * e.fout() + 1.5f, 0.5f * e.fout());
            });

            Draw.reset();
        });
        explosion.emitLight = true;
        explosion.particleLight = true;
        explosion.lightRadius = 45f;
        explosion.lightOpacity = 0.6f;
        explosion.lightColor = Palette.lightOrange;

        blockExplosion = new Effect(30, e -> {
            e.scaled(7, i -> {
                Lines.stroke(3.1f * i.fout());
                Lines.circle(e.x, e.y, 3f + i.fin() * 14f);
                Drawf.light(e.x, e.y, 14f * i.fout() + 2f, Palette.lightOrange, 0.5f * i.fout());
            });

            Draw.color(Color.GRAY);

            Angles.randLenVectors(e.id, 6, 2f + 19f * e.finpow(), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, e.fout() * 3f + 0.5f);
                Fill.circle(e.x + x / 2f, e.y + y / 2f, e.fout() * 1f);
            });

            Draw.color(Palette.lighterOrange, Palette.lightOrange, Color.GRAY, e.fin());
            Lines.stroke(1.7f * e.fout());

            Angles.randLenVectors(e.id + 1, 9, 1f + 23f * e.finpow(), (x, y) -> {
                Lines.lineAngle(e.x + x, e.y + y, Mathf.atan2(x, y), 1f + e.fout() * 3f);
                Drawf.light(e.x + x, e.y + y, 6f * e.fout() + 1.5f, 0.5f * e.fout());
            });

            Draw.reset();
        });
        blockExplosion.emitLight = true;
        blockExplosion.particleLight = true;
        blockExplosion.lightRadius = 45f;
        blockExplosion.lightOpacity = 0.6f;
        blockExplosion.lightColor = Palette.lightOrange;

        blockExplosionSmoke = new Effect(30, e -> {
            Draw.color(Color.GRAY);

            Angles.randLenVectors(e.id, 6, 4f + 30f * e.finpow(), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, e.fout() * 3f);
                Fill.circle(e.x + x / 2f, e.y + y / 2f, e.fout() * 1f);
            });

            Draw.reset();
        });
    }
}
