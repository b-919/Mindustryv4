package io.anuke.mindustry.world.blocks.classic.defense;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.content.fx.BulletFx;
import io.anuke.mindustry.entities.traits.AbsorbTrait;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.world.BarType;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.defense.ForceProjector;
import io.anuke.mindustry.world.consumers.ConsumeLiquidFilter;
import io.anuke.mindustry.world.meta.BlockBar;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.EntityQuery;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.bulletGroup;

public class ClassicShield extends ForceProjector{
    public float powerDrain = 0.005f;
    public float powerPerDamage = 0.06f;
    public float maxRadius = 40f;
    public float radiusScale = 6f;

    public ClassicShield(String name){
        super(name);

        hasLiquids = false;
        hasItems = false;
        itemCapacity = 0;
        liquidCapacity = 0;
        consumes.remove(ConsumeLiquidFilter.class);

        phaseRadiusBoost = 1f;
        radius = 0f;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(BlockStat.powerRange, maxRadius, StatUnit.blocks);
    }

    @Override
    public void setBars(){
        super.setBars();
        bars.add(new BlockBar(BarType.heat, true,
                tile -> tile.<ForceEntity>entity().phaseHeat / maxRadius));
    }

    @Override
    public void update(Tile tile){
        ForceEntity entity = tile.entity();

        if(entity.shield == null){
            entity.shield = new ShieldEntity(tile);
            entity.shield.add();
        }

        entity.broken = false;
        entity.buildup = Mathf.lerpDelta(entity.buildup, 0f, 0.1f);

        boolean active = entity.power.amount > powerPerDamage;
        if(active){
            entity.power.amount -= powerDrain * Timers.delta();
        }

        float targetRadius = Math.min(entity.power.amount * radiusScale, maxRadius);
        entity.phaseHeat = Mathf.lerp(entity.phaseHeat, targetRadius, Timers.delta() * 0.05f);
        entity.radscl = Mathf.lerpDelta(entity.radscl, active && entity.phaseHeat > 0.5f ? 1f : 0f, 0.06f);
        if(entity.phaseHeat < 0.5f) active = false;

        if(entity.hit > 0f){
            entity.hit -= 1f / 5f * Timers.delta();
        }

        float realRadius = realRadius(entity);

        if(active && realRadius > 0.5f){
            float r = realRadius;
            EntityQuery.getNearby(bulletGroup, tile.drawx(), tile.drawy(), r * 2f, bullet -> {
                AbsorbTrait trait = (AbsorbTrait)bullet;
                if(trait.canBeAbsorbed() && trait.getTeam() != tile.getTeam()
                        && isInsideCircle(trait.getX(), trait.getY(), r, tile.drawx(), tile.drawy())){
                    trait.absorb();
                    Effects.effect(BulletFx.absorb, trait);
                    float hit = trait.getShieldDamage() * powerPerDamage;
                    entity.hit = 1f;
                    entity.power.amount -= Math.min(hit, entity.power.amount);
                }
            });
        }
    }

    static boolean isInsideCircle(float x0, float y0, float radius, float x, float y){
        float dx = x - x0, dy = y - y0;
        return dx * dx + dy * dy <= radius * radius;
    }

    public class ShieldEntity extends ForceProjector.ShieldEntity{
        public ShieldEntity(Tile tile){
            super(tile);
        }

        @Override
        public float drawSize(){
            return realRadius(entity) * 2f + 2f;
        }

        @Override
        public void draw(){
            Color teamColor = entity.getTeam() != null && entity.getTeam().color != null
                    ? entity.getTeam().color : Palette.accent;
            Draw.color(teamColor);
            Fill.circle(x, y, realRadius(entity));
            Draw.color();
        }

        @Override
        public void drawBloom(){
            if(entity.broken || entity.radscl <= 0f) return;
            Color teamColor = entity.getTeam() != null && entity.getTeam().color != null
                    ? entity.getTeam().color : Palette.accent;
            Draw.color(teamColor);
            Lines.stroke(4f * entity.radscl);
            Lines.poly(x, y, 48, realRadius(entity));
            Draw.reset();
        }

        @Override
        public void drawOver(){
            if(entity.hit <= 0f) return;

            Draw.color(0f, 0f, 0f, entity.hit);
            Fill.circle(x, y, realRadius(entity) * 0.98f);
            Draw.color();
        }
    }
}