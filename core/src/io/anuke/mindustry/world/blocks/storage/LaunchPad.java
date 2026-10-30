package io.anuke.mindustry.world.blocks.storage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.launchManager;
import static io.anuke.mindustry.Vars.world;

import io.anuke.mindustry.world.consumers.ConsumePower;

public class LaunchPad extends Block {
    public float launchTime = 20f * 60f; // 20 seconds
    public Color color = Color.valueOf("fdff9b");
    public TextureRegion topRegion;
    public TextureRegion pod;

    public Effect launchPodEffect = new Effect(140f, e -> {
        float x = e.x, y = e.y;
        float progress = e.fin();
        float size = 1f + progress * 2.5f;
        float rotation = Mathf.randomSeed(e.id, 0, 360) + progress * (Mathf.randomSeed(e.id + 1, 0, 100) - 50);
        float alpha = progress < 0.8f ? 1f : 1f - (progress - 0.8f) / 0.2f;

        float ox = Mathf.sin(progress * 10f, 2f, Mathf.randomSeed(e.id + 2, 0, 10));
        float oy = progress * progress * 150f;

        // Wave effect - ring of smoke
        float waveProgress = Mathf.clamp(progress * 2f);
        if (waveProgress < 1f) {
            float waveAlpha = (1f - waveProgress) * 0.7f;
            int particles = 12;
            for (int i = 0; i < particles; i++) {
                float angle = i * (360f / particles);
                float rad = waveProgress * 40f;
                float px = x + (float)Math.cos(angle * 0.017453292519943295) * rad;
                float py = y + (float)Math.sin(angle * 0.017453292519943295) * rad;

                Draw.color(Palette.lightishGray);
                Draw.alpha(waveAlpha * alpha);
                Draw.rect(Draw.region("circle"), px, py, 4f, 4f);
            }
        }

        // Shockwave smoke at start
        if (progress < 0.2f) {
            float shockAlpha = (1f - progress / 0.2f);
            int count = 15;
            for (int i = 0; i < count; i++) {
                float angle = Mathf.randomSeed(e.id + i + 10, 0, 360);
                float dist = (Mathf.randomSeed(e.id + i + 20, 0, 2400) / 100f) * (progress / 0.2f);
                float sx = x + (float)Math.cos(angle * 0.017453292519943295) * dist;
                float sy = y + (float)Math.sin(angle * 0.017453292519943295) * dist;
                
                float lerpVal = progress * 5f + (Mathf.randomSeed(e.id + i + 30, 0, 100) / 200f);
                Draw.color(Color.valueOf("ff971c").cpy().lerp(Palette.lightishGray, Mathf.clamp(lerpVal)));
                Draw.alpha(shockAlpha * alpha * (0.5f + Mathf.randomSeed(e.id + i + 40, 0, 100) / 200f));
                float psize = 2f + (Mathf.randomSeed(e.id + i + 50, 0, 800) / 100f) + progress * 10f;
                Draw.rect(Draw.region("circle"), sx, sy, psize, psize);
            }
        }

        // Smoke trail
        if(alpha > 0 && (int)(e.time / 1.5f) % 2 == 0){
            for(int i = 0; i < 6; i++){
                float particleAlpha = alpha * Mathf.random(0.5f, 1f);
                Color color = Color.valueOf("ff971c").cpy().lerp(Palette.lightishGray, Mathf.clamp(progress * 1.5f + Mathf.random(0.4f)));
                float tsize = 3f + progress * 12f + Mathf.random(6f);
                float px = x + ox + Mathf.range(size * 4f);
                float py = y + oy - 4f * size + Mathf.range(size * 4f);

                Effects.effect(new Effect(60f + Mathf.random(60f), p -> {
                    // this draw effect if a pretty funky due the limitations of Ucore, next time I will change Ucore to arc
                    Draw.color(color);
                    Draw.alpha(p.fout() * particleAlpha);
                    float s = tsize * (1f + p.fin() * 0.5f);
                    Draw.rect(Draw.region("circle"), p.x, p.y, s, s);
                }), px, py);
            }
        }

        if (alpha > 0) {
            Draw.color(Color.WHITE);
            Draw.alpha(alpha);
            Effects.effect(new Effect(1.1f, p -> {
                Draw.color(Color.WHITE);
                Draw.alpha(alpha);
                Draw.rect(pod, x + ox, y + oy, pod.getRegionWidth() * size, pod.getRegionHeight() * size, rotation);
            }), x, y);
        }

        Draw.reset();
    });

    public LaunchPad(String name) {
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        hasPower = true;
        itemCapacity = 100;
        size = 3;

        consumes.power(0.5f);
        drawCached = true;
        drawDynamic = true;
    }

    @Override
    public void load() {
        super.load();
        topRegion = Draw.region(name + "-top");
        pod = Draw.region("launchpod");
    }

    @Override
    public void draw(Tile tile) {
        drawCached(tile);
        drawDynamic(tile);
    }

    @Override
    public void drawCached(Tile tile) {
        super.draw(tile);
    }

    @Override
    public void drawDynamic(Tile tile) {
        LaunchPadEntity entity = (LaunchPadEntity)tile.entity;

        Draw.color(color);
        Draw.alpha(entity.progress / launchTime);
        Draw.rect(topRegion, tile.drawx(), tile.drawy());
        Draw.color();

        if(entity.progress >= launchTime * 0.6f){
            Draw.alpha(Mathf.clamp((entity.progress - launchTime * 0.6f) / (launchTime * 0.1f)));
            Draw.rect(pod, tile.drawx(), tile.drawy());
            Draw.reset();
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source) {
        return tile.entity.items.total() < itemCapacity;
    }

    @Override
    public TileEntity newEntity() {
        return new LaunchPadEntity();
    }

    public class LaunchPadEntity extends TileEntity {
        public float progress;

        @Override
        public void update() {
            if (world.getSector() == null || (hasPower && !consumes.get(ConsumePower.class).valid(LaunchPad.this, tile.entity))) return; // Only in campaign and if power is valid

            if (items.total() >= itemCapacity) {
                progress += Timers.delta();
                if (progress >= launchTime) {
                    launch();
                    progress = 0;
                }
            } else {
                progress = 0;
            }
        }

        @Override
        public void writeConfig(DataOutput stream) throws IOException {
            stream.writeFloat(progress);
        }

        @Override
        public void readConfig(DataInput stream) throws IOException {
            progress = stream.readFloat();
        }

        private void launch() {
            if (launchManager == null) return;

            //checks something
            final boolean[] canLaunch = {true};
            items.forEach((item, amount) -> {
                if(!launchManager.canAdd(item, (int)amount)){
                    canLaunch[0] = false;
                }
            });

            if (canLaunch[0]) {
                items.forEach((item, amount) -> {
                    launchManager.addItems(item, (int)amount);
                });
                items.clear();
                Effects.effect(launchPodEffect, tile.drawx(), tile.drawy());
            }
        }
    }
}
