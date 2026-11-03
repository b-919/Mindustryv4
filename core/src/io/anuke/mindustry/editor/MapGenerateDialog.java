package io.anuke.mindustry.editor;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.MapTileData;
import io.anuke.mindustry.maps.filters.*;
import io.anuke.mindustry.ui.dialogs.FloatingDialog;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.ColorMapper;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.function.Supplier;
import io.anuke.ucore.scene.ui.Image;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.layout.Table;

import static io.anuke.mindustry.Vars.*;

public class MapGenerateDialog extends FloatingDialog{
    final MapEditor editor;
    Array<GenerateFilter> filters = new Array<>();
    Texture texture;
    Pixmap pixmap;
    Table filterTable;
    Image previewImage;
    Label statusLabel;

    public MapGenerateDialog(MapEditor editor){
        super("$text.editor.generate");
        this.editor = editor;
        addCloseButton();

        buttons().addImageTextButton("$text.editor.generate", "icon-redo", 30f, () -> {
            ui.loadGraphics(() -> {
                applyFilters();
                hide();
            });
        }).size(200f, 60f);

        buttons().addImageTextButton("$text.filter.randomize", "icon-redo", 30f, () -> {
            for(GenerateFilter filter : filters){
                filter.randomize();
            }
            rebuildFilters();
            markDirty();
        }).size(200f, 60f);

        buttons().addImageTextButton("$text.filter.add", "icon-add", 30f, this::showAdd).size(200f, 60f);

        buttons().addImageTextButton("$text.filter.forceupdate", "icon-refresh", 30f, this::updatePreview).size(200f, 60f);

        shown(this::setup);
        hidden(this::cleanup);
    }

    void setup(){
        int mw = editor.getMap().width();
        int mh = editor.getMap().height();
        int scale = Math.max(1, Math.min(mw, mh) > 256 ? 2 : 1);
        int w = Math.max(mw / scale, 1);
        int h = Math.max(mh / scale, 1);
        pixmap = new Pixmap(w, h, Format.RGBA8888);
        texture = new Texture(pixmap);

        content().clear();
        content().top().left().margin(10f);

        content().table(left -> {
            left.top();

            previewImage = new Image(texture);
            previewImage.clicked(this::updatePreview);
            float size = Math.min(Graphics.width() / 1.2f, Graphics.height() / 1.5f);
            left.add(previewImage).size(Math.min(size, 800f)).padRight(10).top();
            left.row();
            statusLabel = new Label("$text.filter.needupdate");
            statusLabel.setColor(new com.badlogic.gdx.graphics.Color(1f, 0.6f, 0.3f, 1f));
            left.add(statusLabel).padTop(4);
        }).top().left();

        content().add(new Image("white")).width(2f).fillY().padLeft(4).padRight(4);

        content().table(right -> {
            right.top().left();

            Table filterContent = new Table();
            filterTable = filterContent;
            ScrollPane sp = new ScrollPane(filterContent);
            sp.setScrollingDisabled(true, false);
            right.add(sp).grow().top().left();
        }).grow().top().left();

        rebuildFilters();
        updatePreview();
    }

    void markDirty(){
            if(statusLabel != null) statusLabel.setVisible(true);
    }

    void cleanup(){
        if(pixmap != null){
            pixmap.dispose();
            pixmap = null;
        }
        if(texture != null){
            texture.dispose();
            texture = null;
        }
        filterTable = null;
    }

    void rebuildFilters(){
        if(filterTable == null) return;
        filterTable.clear();
        filterTable.defaults().left();
        filterTable.top();

        int i = 0;
        for(GenerateFilter filter : filters){
            final int idx = i;
            final GenerateFilter f = filter;
            filterTable.table(t -> {
                t.defaults().left();
                t.top();
                t.background("button");

                t.table(header -> {
                    header.defaults().left();
                    header.add(f.name()).width(200f).padRight(4);

                    header.addButton("x", () -> {
                        filters.removeIndex(idx);
                        rebuildFilters();
                        markDirty();
                    }).size(40f, 32f);

                    header.addButton("+", () -> {
                        GenerateFilter copy = f.copy();
                        copy.randomize();
                        filters.insert(idx + 1, copy);
                        rebuildFilters();
                        markDirty();
                    }).size(40f, 32f);

                    if(idx > 0){
                        header.addButton("^", () -> {
                            filters.swap(idx, idx - 1);
                            rebuildFilters();
                            markDirty();
                        }).size(40f, 32f);
                    }
                    if(idx < filters.size - 1){
                        header.addButton("v", () -> {
                            filters.swap(idx, idx + 1);
                            rebuildFilters();
                            markDirty();
                        }).size(40f, 32f);
                    }
                }).fillX();

                t.row();

                t.table(opts -> {
                    opts.defaults().left();
                    opts.top();
                    for(FilterOption option : f.options()){
                        option.changed = this::markDirty;
                        option.build(opts);
                    }
                }).width(420f).top().left();

            }).width(420f).pad(4).top().left().fillY();

            filterTable.row();
            i++;
        }

        if(filters.size == 0){
            filterTable.add("$text.filter.none").wrap().width(200f);
        }
    }

    void showAdd(){
        FloatingDialog dialog = new FloatingDialog("$text.filter.add");
        Table addTable = new Table();
        addTable.background("button");
        addTable.defaults().size(190f, 50f);

        addFilterButton(addTable, dialog, "Noise", NoiseFilter::new);
        addFilterButton(addTable, dialog, "Terrain", TerrainFilter::new);
        addFilterButton(addTable, dialog, "Biome", BiomeFilter::new);
        addFilterButton(addTable, dialog, "Ore", OreFilter::new);
        addFilterButton(addTable, dialog, "Default Ores", DefaultOresFilter::new);
        addFilterButton(addTable, dialog, "River", RiverNoiseFilter::new);
        addFilterButton(addTable, dialog, "Lake", LakeNoiseFilter::new);
        addFilterButton(addTable, dialog, "Biome", BiomeFilter::new);
        addFilterButton(addTable, dialog, "Blend", BlendFilter::new);
        addFilterButton(addTable, dialog, "Scatter", ScatterFilter::new);
        addFilterButton(addTable, dialog, "Median", MedianFilter::new);
        addFilterButton(addTable, dialog, "Distort", DistortFilter::new);
        addFilterButton(addTable, dialog, "Mirror", MirrorFilter::new);

        dialog.content().add(new ScrollPane(addTable));
        dialog.addCloseButton();
        dialog.show();
    }

    void addFilterButton(Table p, FloatingDialog dialog, String name, Supplier<GenerateFilter> creator){
        p.addButton(name, () -> {
            GenerateFilter filter = creator.get();
            filter.randomize();
            filters.add(filter);
            dialog.hide();
            rebuildFilters();
            markDirty();
        }).pad(2);
        if(p.getCells().size % 3 == 0) p.row();
    }

    void updatePreview(){
        if(pixmap == null) return;
        if(statusLabel != null) {
            statusLabel.setVisible(false);
        }

        int mw = editor.getMap().width();
        int mh = editor.getMap().height();
        int scale = Math.max(1, Math.min(mw, mh) > 256 ? 2 : 1);
        int scaleX = scale;
        int scaleY = scale;

        MapTileData data = editor.getMap();

        byte[][] elevations = new byte[mw][mh];
        for(int px = 0; px < pixmap.getWidth(); px++){
            for(int py = 0; py < pixmap.getHeight(); py++){
                int tx = px * scaleX;
                int ty = py * scaleY;
                if(tx < mw && ty < mh){
                    elevations[tx][ty] = (byte)data.read(tx, ty, MapTileData.DataPosition.elevation);
                }
            }
        }

        for(int px = 0; px < pixmap.getWidth(); px++){
            for(int py = 0; py < pixmap.getHeight(); py++){
                int tx = px * scaleX;
                int ty = py * scaleY;

                GenerateFilter.GenerateInput input = new GenerateFilter.GenerateInput();
                short floorId = data.read(tx, ty, MapTileData.DataPosition.floor);
                short wallId = data.read(tx, ty, MapTileData.DataPosition.wall);
                byte elev = elevations[tx < mw ? tx : 0][ty < mh ? ty : 0];
                Block floor = Vars.content.block(floorId);
                Block wall = Vars.content.block(wallId);

                input.set(tx, ty, wall, floor, Blocks.air, elev);

                for(GenerateFilter filter : filters){
                    input.begin(mw, mh, (x, y) -> {
                        short f = data.read(x, y, MapTileData.DataPosition.floor);
                        short w = data.read(x, y, MapTileData.DataPosition.wall);
                        byte e = (byte)data.read(x, y, MapTileData.DataPosition.elevation);
                        Tile t = new Tile(x, y, f, w, (byte)0, (byte)0, e);
                        return t;
                    });
                    filter.apply(input);
                }

                byte cliffs = 0;
                for(int d = 0; d < 4; d++){
                    int nx = tx + io.anuke.ucore.util.Geometry.d4[d].x;
                    int ny = ty + io.anuke.ucore.util.Geometry.d4[d].y;
                    if(nx >= 0 && nx < mw && ny >= 0 && ny < mh){
                        byte neighborElev = elevations[nx][ny];
                        if(neighborElev < input.elevation && neighborElev != -1){
                            cliffs |= (1 << (d * 2));
                        }
                    }
                }

                int color = ColorMapper.colorFor(input.floor, input.block, Team.none, input.elevation, cliffs);
                pixmap.drawPixel(px, pixmap.getHeight() - 1 - py, color);
            }
        }

        for(GenerateFilter filter : filters){
            filter.drawOverlay(pixmap, pixmap.getWidth(), pixmap.getHeight(), mw, mh);
        }

        if(texture != null){
            texture.draw(pixmap, 0, 0);
        }
    }

    void applyFilters(){
        MapTileData data = editor.getMap();
        int width = data.width();
        int height = data.height();

        for(GenerateFilter filter : filters){
            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    GenerateFilter.GenerateInput input = new GenerateFilter.GenerateInput();
                    short floorId = data.read(x, y, MapTileData.DataPosition.floor);
                    short wallId = data.read(x, y, MapTileData.DataPosition.wall);
                    byte elev = (byte)data.read(x, y, MapTileData.DataPosition.elevation);
                    Block floor = Vars.content.block(floorId);
                    Block wall = Vars.content.block(wallId);

                    input.set(x, y, wall, floor, Blocks.air, elev);

                    input.begin(width, height, (tx, ty) -> {
                        short f = data.read(tx, ty, MapTileData.DataPosition.floor);
                        short w = data.read(tx, ty, MapTileData.DataPosition.wall);
                        byte e = (byte)data.read(tx, ty, MapTileData.DataPosition.elevation);
                        Tile t = new Tile(tx, ty, f, w, (byte)0, (byte)0, e);
                        return t;
                    });

                    filter.apply(input);

                    data.write(x, y, MapTileData.DataPosition.floor, input.floor.id);
                    data.write(x, y, MapTileData.DataPosition.wall, input.block.id);
                    data.write(x, y, MapTileData.DataPosition.elevation, (short)input.elevation);
                }
            }
        }

        editor.renderer().updateAll();
    }

    public void setFilters(Array<GenerateFilter> filters){
        this.filters = filters;
    }

    public Array<GenerateFilter> getFilters(){
        return filters;
    }
}
