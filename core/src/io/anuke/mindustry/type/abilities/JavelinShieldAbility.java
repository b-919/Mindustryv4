package io.anuke.mindustry.type.abilities;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.fx.BulletFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.effect.Lightning;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.type.Mech;
import io.anuke.mindustry.type.MechAbility;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

/** Speed-based lightning shield, used by the javelin ship. */
public class JavelinShieldAbility implements MechAbility{
    public float minVelocity = 3.6f;
    public float maxVelocity = 6f;

    private TextureRegion shield;

    @Override
    public void load(Mech mech){
        shield = Draw.region(mech.name + "-shield");
    }

    @Override
    public void update(Player player){
        float scl = scld(player);
        if(Mathf.chance(Timers.delta() * (0.15 * scl))){
            Effects.effect(BulletFx.hitLancer, Palette.lancerLaser, player.x, player.y);
            Lightning.create(player.getTeam(), Palette.lancerLaser, 10f,
                    player.x + player.getVelocity().x, player.y + player.getVelocity().y, player.rotation, 14);
        }
    }

    @Override
    public void draw(Player player){
        float scl = scld(player);
        if(scl < 0.01f) return;

        float alpha = Core.batch.getColor().a;
        Graphics.shader();
        Graphics.setAdditiveBlending();
        Draw.color(Palette.lancerLaser);
        Draw.alpha(scl / 2f);
        Draw.rect(shield, player.snappedX() + Mathf.range(scl / 2f), player.snappedY() + Mathf.range(scl / 2f), player.rotation - 90);
        Graphics.setNormalBlending();
        Graphics.shader(Shaders.mix);
        Draw.color();
        Draw.alpha(alpha);
    }

    @Override
    public void drawBloom(Player player){
        float scl = scld(player);
        if(scl < 0.01f) return;

        Draw.color(Palette.lancerLaser);
        Draw.alpha(scl * 0.6f);
        Draw.rect(shield, player.snappedX() + Mathf.range(scl / 2f), player.snappedY() + Mathf.range(scl / 2f), player.rotation - 90);
        Draw.color();
    }

    @Override
    public float getRotationAlpha(Player player){
        return 0.5f;
    }

    private float scld(Player player){
        return Mathf.clamp((player.getVelocity().len() - minVelocity) / (maxVelocity - minVelocity));
    }
}
