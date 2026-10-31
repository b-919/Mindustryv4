package io.anuke.mindustry.content.fx;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.entities.effect.GroundEffectEntity.GroundEffect;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;

/** Effects used by the core landing sequence. */
public class CoreFx extends FxList{
    public static Effect coreLandDust, coreLand;

    @Override
    public void load(){
        coreLandDust = new GroundEffect(45f, e -> {
            Draw.color(Palette.lightishGray, e.color, e.fin());
            Angles.randLenVectors(e.id, 4, 4f + 22f * e.fin(), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 0.5f + e.fout() * 4f);
            });
            Draw.reset();
        });

        coreLand = new Effect(30f, e -> {
            Draw.color(Palette.accent, Color.WHITE, e.fin());
            Lines.stroke(e.fout() * 4f);
            Lines.circle(e.x, e.y, 8f + 42f * e.fin());
            Draw.color(Palette.accent);
            Draw.alpha(e.fout() * 0.25f);
            Fill.circle(e.x, e.y, 18f + 20f * e.fin());
            Draw.reset();
        });
        coreLand.emitLight = true;
        coreLand.lightRadius = 90f;
        coreLand.lightOpacity = 0.8f;
        coreLand.lightColor = Palette.accent;
    }
}
