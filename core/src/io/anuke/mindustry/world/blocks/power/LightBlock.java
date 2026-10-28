package io.anuke.mindustry.world.blocks.power;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.PowerBlock;
import io.anuke.mindustry.ui.dialogs.ColorPickerDialog;
import io.anuke.ucore.graphics.Hue;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LightBlock extends PowerBlock {
    public float brightness = 0.9f;
    public float radius = 80f;
    /** Small fixture sprite used as the bloom source; the halo itself comes from the lightmap. */
    public TextureRegion topRegion;

    public LightBlock(String name) {
        super(name);
        hasBloom = true;
        hasPower = true;
        update = true;
        configurable = true;
        consumes.power(0.05f);
    }

    @Override
    public void load() {
        super.load();
        topRegion = Draw.region(name + "-top");
    }

    @Override
    public void init() {
        super.init();
        emitLight = true;
    }

    @Override
    public float lightRadius() {
        return radius;
    }

    @Override
    public void drawLight(Tile tile){
        LightEntity entity = tile.entity();
        float radius = entity.radius < 0f ? this.radius : entity.radius;
        float brightness = entity.brightness < 0f ? this.brightness : entity.brightness;
        float opacity = brightness * entity.smooth;

        if(radius > 0.001f && opacity > 0.001f){
            Draw.color(Tmp.c1.set(entity.color));
            Shaders.light.region = Draw.region("circle");
            Draw.alpha(opacity);
            Draw.rect("circle", tile.drawx(), tile.drawy(), radius * 2, radius * 2);
            Draw.alpha(opacity * 0.5f);
            Draw.rect("circle", tile.drawx(), tile.drawy(), radius * 2, radius * 2);
        }
    }

    @Override
    public void drawBloom(Tile tile){
        LightEntity entity = tile.entity();
        float brightness = entity.brightness < 0f ? this.brightness : entity.brightness;
        float opacity = brightness * entity.smooth;

        if(opacity > 0.01f && topRegion != null){
            Draw.color(Tmp.c1.set(entity.color));
            Draw.alpha(opacity * 0.6f);
            Draw.rect(topRegion, tile.drawx(), tile.drawy());
            Draw.color();
        }
    }

    @Override
    public void buildTable(Tile tile, Table table) {
        LightEntity entity = tile.entity();

        table.addImageButton("icon-pencil", 40, () -> {
            new ColorPickerDialog().show(Tmp.c1.set(entity.color), color -> entity.color = Hue.rgb(color));
        });
    }

    @Override
    public TileEntity newEntity() {
        return new LightEntity();
    }

    public class LightEntity extends TileEntity {
        public int color = Hue.rgb(Color.WHITE);
        public float radius = -1f;
        public float brightness = -1f;
        /** 0..1 fade toward the current power state, so lamps ramp up/down instead of popping. */
        public float smooth = 0f;

        @Override
        public void update(){
            super.update();
            smooth = Mathf.lerpDelta(smooth, (cons != null && cons.valid()) || tile.isEnemyCheat() ? 1f : 0f, 0.1f);
        }

        @Override
        public void write(DataOutput stream) throws IOException {
            super.write(stream);
            stream.writeInt(color);
        }

        @Override
        public void read(DataInput stream) throws IOException {
            super.read(stream);
            color = stream.readInt();
        }

        @Override
        public void writeConfig(DataOutput stream) throws IOException {
            stream.writeInt(color);
        }

        @Override
        public void readConfig(DataInput stream) throws IOException {
            color = stream.readInt();
        }

        @Override
        public Object config() {
            return color;
        }

        @Override
        public void configured(Object config) {
            if (config instanceof Integer) {
                color = (Integer) config;
            }
        }
    }
}
