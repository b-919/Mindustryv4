package io.anuke.mindustry.world.blocks.storage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.Liquids;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.core.UI;
import io.anuke.mindustry.entities.TileEntity;
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
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class LandingPad extends Block {
    public TextureRegion pod;
    public float pullTime = 15f * 60f;

    public Effect landPodEffect = new Effect(140f, e -> {// particle hell
        float x = e.x, y = e.y;
        float progress = Mathf.clamp(e.time / 100f);
        float interp = 1f - Mathf.pow(progress, 0.2f);
        float oy = interp * 200f;
        float size = 1.2f + interp * 2.3f;
        float alpha = progress < 0.1f ? progress / 0.1f : (progress < 1f ? 1f : 1f - (e.time - 100f) / 40f);
        float rotation = Mathf.randomSeed(e.id, 0, 360) + progress * (Mathf.randomSeed(e.id + 1, 0, 80) - 40);
        if(progress > 0.3f && progress < 1f){
            float smokeProgress = (progress - 0.3f) / 0.7f;
            float spread = 6f * (1f - progress);
            for(int i = 0; i < 2; i++){
                float px = x + Mathf.range(spread + 5f);
                float py = y + oy + Mathf.range(spread + 5f);
                float particleAlpha = alpha * (1f - progress) * Mathf.clamp(smokeProgress * 2f);
                Color color = Color.valueOf("ff971c").cpy().lerp(Palette.lightishGray, Mathf.clamp(smokeProgress * 2f));
                float tsize = 3f + progress * 5f;

                Effects.effect(new Effect(60f + Mathf.random(60f), p -> {
                    Draw.color(color);
                    Draw.alpha(p.fout() * particleAlpha);
                    float s = tsize * (1f + p.fin() * 0.5f);
                    Draw.rect(Draw.region("circle"), p.x, p.y, s, s);
                }), px, py);
            }

            // pod trail
            if(Mathf.chance(0.5)){
                float px = x + Mathf.range(1.5f);
                float py = y + oy + 10f + Mathf.range(3f);
                float particleAlpha = alpha * (1f - progress) * 0.4f;
                Color color = Color.valueOf("ff971c").cpy().lerp(Palette.lightishGray, Mathf.clamp(smokeProgress * 3f));
                float tsize = 1f + progress * 2f;

                Effects.effect(new Effect(40f + Mathf.random(40f), p -> {
                    Draw.color(color);
                    Draw.alpha(p.fout() * particleAlpha);
                    float s = tsize * (1f + p.fin() * 0.5f);
                    Draw.rect(Draw.region("circle"), p.x, p.y, s, s);
                }), px, py);
            }
        }

        if (progress < 1f) {
            Draw.color(Color.WHITE);
            Draw.alpha(alpha);
            Draw.rect(pod, x, y + oy, pod.getRegionWidth() * size, pod.getRegionHeight() * size, rotation);
        }
        // particle wave
        if(progress > 0.85f && progress < 1f){
            float wave = (progress - 0.85f) / 0.15f;
            Draw.color(Palette.lightishGray);
            Draw.alpha(1f - wave);
            for(int i = 0; i < 15; i++){
                float ang = i / 15f * 360f;
                Draw.rect(Draw.region("circle"), x + Mathf.sin(ang, 10f, 40f * wave), y + Mathf.cos(ang, 10f, 40f * wave), 5f, 5f);
            }
        }

        Draw.reset();
    });

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
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    @Override
    public void load() {
        super.load();
        pod = Draw.region("launchpod"); // Use same pod texture
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

            Draw.color(Color.WHITE);
            Draw.alpha(alpha);
            Draw.rect(pod, tile.drawx(), tile.drawy());
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
