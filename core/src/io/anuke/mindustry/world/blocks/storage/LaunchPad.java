package io.anuke.mindustry.world.blocks.storage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.graphics.DrawPseudo3D;
import io.anuke.mindustry.graphics.Drawf;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.launchManager;
import static io.anuke.mindustry.Vars.state;
import static io.anuke.mindustry.Vars.world;

import io.anuke.mindustry.world.consumers.ConsumePower;

public class LaunchPad extends Block {
    private static final Color flameColor = Color.valueOf("ff971c");
    private static final Color thrusterColor = Color.valueOf("ffe36e");

    public float launchTime = 20f * 60f; // 20 seconds
    public Color color = Color.valueOf("fdff9b");
    public TextureRegion topRegion;
    public TextureRegion pod;
    public TextureRegion flare;

    public Effect launchPodEffect = new Effect(160f, e -> {
        float fin = e.fin();
        float alpha = e.fout(Interpolation.pow5Out);
        float height = e.fin(Interpolation.pow5In) * (110f + Mathf.randomSeed(e.id + 2, 0, 24));
        float drift = e.fin(Interpolation.pow2In) * (Mathf.randomSeed(e.id + 3, 0, 10) - 5f);
        float rotation = fin * (120f + Mathf.randomSeed(e.id, 0, 50));
        float thrust = 0.5f + e.fslope() * 0.5f;
        float wh = DrawPseudo3D.worldHeight(height);
        float px = e.x + drift;
        float py = e.y + height;
        float sx = DrawPseudo3D.xHeight(px, wh);
        float sy = DrawPseudo3D.yHeight(py, wh);
        float hscale = DrawPseudo3D.hScale(wh);
        float podScale = (0.95f + Mathf.clamp(height / 110f) * 0.45f) * hscale;
        float flareGrow = Interpolation.pow2Out.apply(Mathf.clamp(fin / 0.55f));
        float flareScale = podScale * (0.2f + flareGrow * 0.8f);
        float flareRotation = Mathf.randomSeed(e.id + 21, 0, 360) + fin * (90f + Mathf.randomSeed(e.id + 22, 0, 180));
        if(Drawf.bloomPass){
            drawThrusterFlare(sx, sy, flareScale, alpha * flareGrow, flareRotation, thrust);
            Draw.reset();
            return;
        }
        DrawPseudo3D.shadow(e.x, e.y, height, 110f, 18f, 0.22f);
        float wave = Mathf.clamp(fin * 3f);
        if(wave < 1f){
            float wa = 1f - wave;
            float radius = wave * 48f;
            Draw.color(Palette.lightishGray);
            Draw.alpha(wa * 0.9f);
            Lines.stroke(3f * wa + 0.6f);
            Lines.circle(e.x, e.y, radius);
            Lines.stroke(1.6f * wa);
            Draw.alpha(wa * 0.5f);
            Lines.circle(e.x, e.y, radius * 0.65f);
            for(int i = 0; i < 18; i++){
                float angle = i * 20f + e.id * 7f;
                float px2 = e.x + Angles.trnsx(angle, radius);
                float py2 = e.y + Angles.trnsy(angle, radius);
                float size = 3f + wave * 4f;
                Tmp.c1.set(flameColor).lerp(Palette.lightishGray, wave);
                Draw.color(Tmp.c1);
                Draw.alpha(wa * 0.8f);
                Draw.rect("circle", px2, py2, size, size);
            }
            Lines.stroke(1f);
        }
        if(fin < 0.12f){
            Draw.color(flameColor);
            Draw.alpha((1f - fin / 0.12f) * 0.7f);
            Fill.circle(e.x, e.y, 6f + fin * 60f);
        }
        if(alpha > 0f){
            int puffs = 28;
            float ground = Mathf.clamp(1f - height / 45f, 0f, 1f);
            for(int i = 0; i < puffs; i++){
                float f = (i / (float)puffs + fin * 2.2f) % 1f;
                float h = height * (1f - f * 0.95f);
                float radial = f > 0.6f ? ((f - 0.6f) / 0.4f) * (10f + 20f * ground) : 0f;
                float angle = i * 137.5f + e.id * 11f;
                float wob = Mathf.sin(fin * 10f + i * 1.7f + e.id * 0.13f, 1f, 1.5f + f * 4f);
                float pwh = DrawPseudo3D.worldHeight(h);
                float px2 = DrawPseudo3D.xHeight(e.x + drift * (1f - f) + wob + Angles.trnsx(angle, radial), pwh);
                float py2 = DrawPseudo3D.yHeight(e.y + h, pwh) - 3f + Angles.trnsy(angle, radial) * 0.35f;
                float size = 4f + f * 9f + radial * 0.25f;

                Tmp.c1.set(flameColor).lerp(Palette.lightishGray, Mathf.clamp(fin * 0.7f + f * 0.7f));
                Draw.color(Tmp.c1);
                Draw.alpha(alpha * (0.9f - f * 0.6f));
                Draw.rect("circle", px2, py2, size, size);
            }
            for(int i = 0; i < 12; i++){
                float f = (i / 12f + fin * 1.5f) % 1f;
                float h = height * (1f - f * 0.95f) * 0.5f + 2f;
                float angle = i * 137.5f + e.id * 7f;
                float radial = f * (8f + 14f * ground);
                float pwh = DrawPseudo3D.worldHeight(h);
                float px2 = DrawPseudo3D.xHeight(e.x + Angles.trnsx(angle, radial), pwh);
                float py2 = DrawPseudo3D.yHeight(e.y + h, pwh) + Angles.trnsy(angle, radial) * 0.4f;
                float size = 8f + f * 8f;

                Draw.color(Palette.lightishGray);
                Draw.alpha(alpha * (0.18f - f * 0.1f));
                Draw.rect("circle", px2, py2, size, size);
            }
        }
        if(alpha > 0f){
            drawThrusterFlare(sx, sy, flareScale, alpha * flareGrow, flareRotation, thrust);
        }
        if(alpha > 0f){
            float rw = pod.getRegionWidth() * podScale;
            float rh = pod.getRegionHeight() * podScale;
            Draw.color(0f, 0f, 0f, 0.22f * alpha);
            Draw.rect(pod, sx + 5f, sy - 6f, rw, rh, rotation);
            Draw.color(Color.WHITE);
            Draw.alpha(alpha);
            Draw.rect(pod, sx, sy, rw, rh, rotation);
        }
        if(alpha > 0f){
            drawThrusterFlare(sx, sy, flareScale, alpha * flareGrow * 0.35f, flareRotation, thrust);
        }

        Draw.reset();
    });

    private void drawThrusterFlare(float x, float y, float scale, float alpha, float rotation, float thrust){
        Draw.color(thrusterColor);
        Draw.alpha(alpha * (0.5f + thrust * 0.3f));
        float size = (24f + thrust * 16f) * scale * 1.2f;
        Draw.rect(flare, x, y, size, size, rotation);
    }

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

        emitLight = true;
        lightColor = thrusterColor;
        launchPodEffect.lightRadius = 45f;
        lightOpacity = 0.2f;
    }

    @Override
    public void load() {
        super.load();
        topRegion = Draw.region(name + "-top");
        pod = Draw.region("launchpod");
        flare = Draw.region("flare");
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
        float fill = Mathf.clamp(entity.items.total() / (float)itemCapacity);
        float charge = Math.max(fill, entity.progress / launchTime);

        Draw.color(color);
        Draw.alpha(charge);
        Draw.rect(topRegion, tile.drawx(), tile.drawy());
        Draw.color();

        if(charge >= 0.6f){
            DrawPseudo3D.shadow(tile.drawx(), tile.drawy(), 0f, 1f, 16f, 0.12f);
            float podHeight = DrawPseudo3D.worldHeight(6f);
            float podScale = DrawPseudo3D.hScale(podHeight);
            float podX = DrawPseudo3D.xHeight(tile.drawx(), podHeight);
            float podY = DrawPseudo3D.yHeight(tile.drawy(), podHeight);
            float shake = entity.progress > launchTime * 0.95f ? Mathf.sin(entity.progress * 0.6f, 0.5f, 0.35f) : 0f;
            Draw.color(Color.WHITE);
            Draw.alpha(Mathf.clamp((charge - 0.6f) / 0.1f));
            Draw.rect(pod, podX + shake, podY, pod.getRegionWidth() * podScale, pod.getRegionHeight() * podScale);
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
                    if(tile.getTeam() == state.playerTeam){
                        world.sectors.onItemExport(item, (int)amount);
                    }
                });
                items.clear();
                Effects.effect(launchPodEffect, tile.drawx(), tile.drawy());
            }
        }
    }
}
