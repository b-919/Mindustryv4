package io.anuke.mindustry.entities.bullet;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.content.fx.BulletFx;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.util.Physics;
import io.anuke.ucore.util.Translator;

import com.badlogic.gdx.graphics.Color;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.mindustry.graphics.Palette;

import static io.anuke.mindustry.Vars.world;

/**
 * A hitscan-style bullet that instantly damages everything in a line of the given length.
 * Damage is reduced by the health of every target pierced.
 */
public class RailBulletType extends BulletType{
    public Effect pierceEffect = BulletFx.hitBulletSmall, updateEffect = Fx.none;
    public float pierceDamageFactor = 1f;
    public float length = 100f;
    public float updateEffectSeg = 20f;

    protected static Rectangle rect = new Rectangle();
    protected static Rectangle hitrect = new Rectangle();
    protected static Translator tr = new Translator();

    public RailBulletType(float damage){
        super(0.001f, damage);
        pierce = true;
        hitTiles = false;
        collidesTiles = false;
        collides = false;
        keepVelocity = false;
        lifetime = 16f;
        hiteffect = Fx.none;
        despawneffect = Fx.none;
    }

    @Override
    public void init(Bullet b){
        b.damage = damage;

        tr.trns(b.angle(), length);
        world.raycastEachWorld(b.x, b.y, b.x + tr.x, b.y + tr.y, (cx, cy) -> {
            Tile tile = world.tile(cx, cy);
            if(tile != null && tile.entity != null && tile.target().getTeamID() != b.getTeam().ordinal() && tile.entity.collide(b)){
                Effects.effect(pierceEffect, tile.worldx(), tile.worldy(), b.angle());
                tile.entity.collision(b);
            }
            return false;
        });

        rect.setPosition(b.x, b.y).setSize(tr.x, tr.y);
        float x2 = tr.x + b.x, y2 = tr.y + b.y;

        if(rect.width < 0){
            rect.x += rect.width;
            rect.width *= -1;
        }

        if(rect.height < 0){
            rect.y += rect.height;
            rect.height *= -1;
        }

        float expand = 3f;

        rect.y -= expand;
        rect.x -= expand;
        rect.width += expand * 2;
        rect.height += expand * 2;

        Units.getNearbyEnemies(b.getTeam(), rect, e -> {
            e.getHitbox(hitrect);
            Rectangle other = hitrect;
            other.y -= expand;
            other.x -= expand;
            other.width += expand * 2;
            other.height += expand * 2;

            Vector2 vec = Physics.raycastRect(b.x, b.y, x2, y2, other);

            if(vec != null){
                Effects.effect(pierceEffect, vec.x, vec.y, b.angle());
                e.collision(b, vec.x, vec.y);
                b.collision(e, vec.x, vec.y);
            }
        });
        float norx = tr.x / length, nory = tr.y / length;
        for(float i = 0; i <= length; i += updateEffectSeg){
            Effects.effect(updateEffect, b.x + norx * i, b.y + nory * i, b.angle());
        }

        b.remove();
    }

    @Override
    public void drawBloom(Bullet b){
        float fade = b.fout();
        Draw.color(Palette.bulletYellowBack);
        Draw.alpha(fade * 0.8f);
        Lines.stroke(8f * fade);
        Lines.line(b.x, b.y, b.x + tr.x, b.y + tr.y);
        Draw.color(Color.WHITE);
        Draw.alpha(fade * 0.5f);
        Lines.stroke(3f * fade);
        Lines.line(b.x, b.y, b.x + tr.x, b.y + tr.y);
        Draw.reset();
    }
}
