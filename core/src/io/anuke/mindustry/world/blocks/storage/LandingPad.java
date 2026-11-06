package io.anuke.mindustry.world.blocks.storage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.Liquids;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.graphics.DrawPseudo3D;
import io.anuke.mindustry.graphics.Drawf;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.consumers.ConsumeLiquid;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class LandingPad extends Block {
    private static final Color flameColor = Color.valueOf("ff971c");
    private static final Color thrusterColor = Color.valueOf("ffe36e");
    public TextureRegion pod;
    public TextureRegion flare;
    public float pullTime = 15f * 60f;

    public Effect landPodEffect = new Effect(120f, e -> {
        float fin = e.fin();
        float fall = Mathf.clamp(fin / 0.83f);
        float landed = Mathf.clamp((fin - 0.83f) / 0.17f);
        float height = Mathf.pow(1f - fall, 4f) * (100f + Mathf.randomSeed(e.id + 2, 0, 24));
        float drift = (1f - fall) * (Mathf.randomSeed(e.id + 3, 0, 12) - 6f);
        float rotation = (1f - fall) * (70f + Mathf.randomSeed(e.id, 0, 40));
        float alpha = Math.min(1f, fin / 0.08f);
        float thrust = Mathf.clamp(0.25f + fall * 0.95f);
        float wh = DrawPseudo3D.worldHeight(height);
        float sx = DrawPseudo3D.xHeight(e.x + drift, wh);
        float sy = DrawPseudo3D.yHeight(e.y + height, wh);
        float hscale = DrawPseudo3D.hScale(wh);
        float podScale = (0.95f + Mathf.clamp(height / 110f) * 0.45f) * hscale;
        float flareFade = Mathf.clamp(1f - Mathf.clamp((fall - 0.8f) / 0.2f) * 1.3f);
        float flareScale = podScale * (0.35f + flareFade * 0.65f);
        float flareRotation = Mathf.randomSeed(e.id + 21, 0, 360) + fin * (90f + Mathf.randomSeed(e.id + 22, 0, 180));
        if(Drawf.bloomPass){
            drawThrusterFlare(sx, sy, flareScale, alpha * flareFade, flareRotation, thrust);
            Draw.reset();
            return;
        }
        DrawPseudo3D.shadow(e.x, e.y, height, 110f, 18f, 0.22f);
        if(landed > 0f){
            float wa = 1f - landed;
            float radius = landed * 50f;
            Draw.color(Palette.lightishGray);
            Draw.alpha(wa);
            Lines.stroke(3f * wa + 0.6f);
            Lines.circle(e.x, e.y, radius);
            Lines.stroke(1.4f * wa);
            Draw.alpha(wa * 0.5f);
            Lines.circle(e.x, e.y, radius * 0.62f);
            for(int i = 0; i < 20; i++){
                float angle = i * 18f + e.id * 7f;
                float px2 = e.x + Angles.trnsx(angle, radius);
                float py2 = e.y + Angles.trnsy(angle, radius);
                float size = 4f + landed * 4f;
                Tmp.c1.set(flameColor).lerp(Palette.lightishGray, landed);
                Draw.color(Tmp.c1);
                Draw.alpha(wa * 0.7f);
                Draw.rect("circle", px2, py2, size, size);
            }
            if(landed < 0.25f){
                Draw.color(flameColor);
                Draw.alpha((1f - landed / 0.25f) * 0.6f);
                Fill.circle(e.x, e.y, 10f + landed * 80f);
            }
            Lines.stroke(1f);
        }
        if(alpha > 0f){
            float plumeBase = Math.max(0f, height - 50f);
            float plumeLen = height - plumeBase;
            float ground = Mathf.clamp(1f - height / 45f, 0f, 1f);
            int puffs = 28;
            for(int i = 0; i < puffs; i++){
                float f = (i / (float)puffs + fin * 2.4f) % 1f;
                float h = plumeBase + plumeLen * (1f - f);
                float radial = f > 0.6f
                    ? ((f - 0.6f) / 0.4f) * (10f + 20f * ground) * (0.5f + fall * 0.5f)
                    : 0f;
                float angle = i * 137.5f + e.id * 11f;
                float wob = Mathf.sin(fin * 11f + i * 1.6f + e.id * 0.17f, 1f, 1.5f + f * 4f);
                float pwh = DrawPseudo3D.worldHeight(h);
                float px2 = DrawPseudo3D.xHeight(e.x + drift * (1f - f) + wob + Angles.trnsx(angle, radial), pwh);
                float py2 = DrawPseudo3D.yHeight(e.y + h, pwh) - 3f + Angles.trnsy(angle, radial) * 0.35f;
                float size = 4f + f * 8f + radial * 0.25f;
                Tmp.c1.set(flameColor).lerp(Palette.lightishGray, Mathf.clamp(fin * 0.8f + f * 0.6f));
                Draw.color(Tmp.c1);
                Draw.alpha(alpha * (0.9f - f * 0.6f));
                Draw.rect("circle", px2, py2, size, size);
            }
            for(int i = 0; i < 12; i++){
                float f = (i / 12f + fin * 1.6f) % 1f;
                float h = plumeBase + plumeLen * (1f - f * 0.95f) * 0.5f;
                float angle = i * 137.5f + e.id * 7f;
                float radial = f * (8f + 14f * ground);
                float pwh = DrawPseudo3D.worldHeight(h);
                float px2 = DrawPseudo3D.xHeight(e.x + Angles.trnsx(angle, radial), pwh);
                float py2 = DrawPseudo3D.yHeight(e.y + h, pwh) + Angles.trnsy(angle, radial) * 0.4f;
                float size = 8f + f * 8f;

                Draw.color(Palette.lightishGray);
                Draw.alpha(alpha * (0.2f - f * 0.11f));
                Draw.rect("circle", px2, py2, size, size);
            }
        }
        if(alpha > 0f){
            drawThrusterFlare(sx, sy, flareScale, alpha * flareFade, flareRotation, thrust);
        }
        if(alpha > 0f && landed <= 0f){
            float rw = pod.getRegionWidth() * podScale;
            float rh = pod.getRegionHeight() * podScale;

            Draw.color(0f, 0f, 0f, 0.22f * alpha);
            Draw.rect(pod, sx + 5f, sy - 6f, rw, rh, rotation);
            Draw.color(Color.WHITE);
            Draw.alpha(alpha);
            Draw.rect(pod, sx, sy, rw, rh, rotation);
        }
        if(alpha > 0f && landed <= 0f){
            drawThrusterFlare(sx, sy, flareScale, alpha * flareFade * 0.35f, flareRotation, thrust);
        }

        Draw.reset();
    });

    private void drawThrusterFlare(float x, float y, float scale, float alpha, float rotation, float thrust){
        Draw.color(thrusterColor);
        Draw.alpha(alpha * (0.5f + thrust * 0.3f));
        float size = (24f + thrust * 16f) * scale * 1.2f;
        Draw.rect(flare, x, y, size, size, rotation);
    }

    public LandingPad(String name) {
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        hasLiquids = true;
        configurable = true;
        itemCapacity = 100;
        liquidCapacity = 20f;
        size = 3;

        consumes.liquid(Liquids.water, 0.05f);
        drawCached = true;
        drawDynamic = true;
        emitLight = true;
        lightColor = thrusterColor;
        landPodEffect.lightRadius = 45f;
        lightOpacity = 0.2f;
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    @Override
    public void load() {
        super.load();
        pod = Draw.region("launchpod"); // Use same pod texture
        flare = Draw.region("flare");
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source) {
        return false;
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
        LandingPadEntity entity = (LandingPadEntity)tile.entity;
        if(entity.hasPod && entity.items.total() > 0){
            float alpha = 1f;
            float threshold = itemCapacity / 2f;
            if(entity.items.total() < threshold){
                alpha = entity.items.total() / threshold;
            }

            DrawPseudo3D.shadow(tile.drawx(), tile.drawy(), 0f, 1f, 16f, 0.12f);
            float podHeight = DrawPseudo3D.worldHeight(6f);
            float podScale = DrawPseudo3D.hScale(podHeight);
            float podX = DrawPseudo3D.xHeight(tile.drawx(), podHeight);
            float podY = DrawPseudo3D.yHeight(tile.drawy(), podHeight);

            Draw.color(Color.WHITE);
            Draw.alpha(alpha);
            Draw.rect(pod, podX, podY, pod.getRegionWidth() * podScale, pod.getRegionHeight() * podScale);
            Draw.reset();
        }
    }

    @Override
    public void buildTable(Tile tile, Table table) {
        LandingPadEntity entity = (LandingPadEntity)tile.entity;

        table.addButton("None", "clear", () -> {
            entity.targetItem = null;
        }).size(80, 40).pad(4);

        int i = 1;
        for (Item item : launchManager.getInventory().keys()) {
            if (launchManager.getAmount(item) <= 0) continue;

            ImageButton button = table.addImageButton("white", "clear-toggle", 24, () -> {
                entity.targetItem = item;
            }).size(44, 44).pad(4).get();
            button.getStyle().imageUp = new io.anuke.ucore.scene.style.TextureRegionDrawable(item.region);
            button.setChecked(entity.targetItem == item);

            if (i % 4 == 0) table.row();
            i++;
        }
    }

    @Override
    public TileEntity newEntity() {
        return new LandingPadEntity();
    }

    public class LandingPadEntity extends TileEntity {
        public Item targetItem;
        public float timer;
        public float landingTimer;
        public boolean landing;
        public boolean hasPod;

        @Override
        public void update() {
            if (world.getSector() == null) return;

            if (items.total() > 0) {
                tile.block().tryDump(tile);
            } else {
                hasPod = false;
            }

            if (landing) {
                landingTimer += Timers.delta();
                if (landingTimer >= 100f) {
                    int amount = launchManager.getAmount(targetItem);
                    if (amount > 0) {
                        int toTake = Math.min(amount, itemCapacity - items.total());
                        launchManager.removeItems(targetItem, toTake);
                        items.add(targetItem, toTake);
                        world.sectors.onItemImport(targetItem, toTake);
                        hasPod = true;
                    }
                    landing = false;
                    landingTimer = 0;
                }
                return;
            }

            if (targetItem == null || items.total() >= itemCapacity || !consumes.get(ConsumeLiquid.class).valid(LandingPad.this, this)) {
                timer = 0;
                return;
            }

            timer += Timers.delta();
            if (timer >= pullTime) {
                int amount = launchManager.getAmount(targetItem);
                if (amount > 0) {
                    landing = true;
                    landingTimer = 0;
                    timer = 0;

                    // burn
                    Effects.effect(landPodEffect, tile.drawx(), tile.drawy());
                } else {
                    timer = 0;
                }
            }
        }

        @Override
        public void writeConfig(DataOutput stream) throws IOException {
            stream.writeShort(targetItem == null ? -1 : targetItem.id);
            stream.writeBoolean(landing);
            stream.writeFloat(landingTimer);
            stream.writeBoolean(hasPod);
        }

        @Override
        public void readConfig(DataInput stream) throws IOException {
            int id = stream.readShort();
            targetItem = id == -1 ? null : (Item)content.getByID(ContentType.item, id);
            landing = stream.readBoolean();
            landingTimer = stream.readFloat();
            hasPod = stream.readBoolean();
        }

        @Override
        public Object config(){
            return targetItem;
        }

        @Override
        public void configured(Object config){
            if(config instanceof Item){
                targetItem = (Item)config;
            }else{
                targetItem = null;
            }
        }
    }
}
