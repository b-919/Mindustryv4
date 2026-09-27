package io.anuke.mindustry.editor;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.TechTree;
import io.anuke.mindustry.maps.MapTileData;
import io.anuke.mindustry.maps.generation.WorldGenerator;
import io.anuke.mindustry.maps.generation.WorldGenerator.GenResult;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.ui.dialogs.FloatingDialog;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.ColorMapper;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.Image;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.Slider;
import io.anuke.ucore.scene.ui.TextButton;
import io.anuke.ucore.scene.ui.TextField;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Strings;
import io.anuke.ucore.util.Structs;

import static io.anuke.mindustry.Vars.*;

public class SectorGenerateDialog extends FloatingDialog{
    final MapEditor editor;
    long seed = 0;
    float temperatureScale = 1100f;
    float elevationDensity = 6.1f;
    float lakeFactor = 0.15f;
    float treeDensity = 0.0145f;
    float deadTreeDensity = 0.0075f;
    float frozenTreeDensity = 0.0045f;
    float oreDensity = 0.5f;
    float ridgeScale = 400f;
    boolean generateTrees = true;
    boolean generateOres = true;
    boolean generateCliffs = true;
    /**Tech tree whose ores are generated. Ores of other trees are never placed.*/
    String oreTech = TechTree.defaultTech;

    TextField seedField;
    Texture texture;
    Pixmap pixmap;

    public SectorGenerateDialog(MapEditor editor){
        super("$text.editor.sectorgenerate");
        this.editor = editor;
        addCloseButton();

        buttons().addImageTextButton("$text.editor.generate", "icon-redo", 30f, () -> {
            ui.loadGraphics(() -> {
                generate();
                hide();
            });
        }).size(200f, 60f);

        buttons().addImageTextButton("$text.filter.forceupdate", "icon-refresh", 30f, this::updatePreview).size(200f, 60f);

        shown(this::setup);
        hidden(this::cleanup);
    }

    void setup(){
        seed = (long)Mathf.random(Long.MAX_VALUE);
        int w = Math.max(editor.getMap().width() / 2, 1);
        int h = Math.max(editor.getMap().height() / 2, 1);
        pixmap = new Pixmap(w, h, Format.RGBA8888);
        texture = new Texture(pixmap);

        content().clear();
        content().top().left().margin(10f);

        content().table(left -> {
            left.top();
            float size = Math.min(Graphics.width() / 1.2f, Graphics.height() / 1.5f);
            left.add(new Image(texture)).size(Math.min(size, 700f)).padRight(10).top();
        }).top().left();

        content().add(new Image("white")).width(2f).fillY().padLeft(4).padRight(4);

        content().table(right -> {
            right.top().left();

            Table controlsContent = new Table();
            controlsContent.defaults().left();
            controlsContent.top();

            controlsContent.add("$text.editor.sectorgenerate.description").left().row();
            controlsContent.row();

            addSeedField(controlsContent);
            controlsContent.row();

            addSlider(controlsContent, "$text.filter.option.scale-elevation", 1f, 20f, v -> elevationDensity = v, () -> elevationDensity);
            controlsContent.row();
            addSlider(controlsContent, "$text.filter.option.temperature", 200f, 3000f, v -> temperatureScale = v, () -> temperatureScale);
            controlsContent.row();
            addSlider(controlsContent, "$text.filter.option.lake-factor", 0f, 0.5f, v -> lakeFactor = v, () -> lakeFactor);
            controlsContent.row();
            addSlider(controlsContent, "$text.filter.option.ridge-scale", 50f, 1000f, v -> ridgeScale = v, () -> ridgeScale);
            controlsContent.row();
            addSlider(controlsContent, "$text.filter.option.tree-density", 0f, 0.05f, v -> treeDensity = v, () -> treeDensity);
            controlsContent.row();
            addSlider(controlsContent, "$text.filter.option.dead-tree-density", 0f, 0.05f, v -> deadTreeDensity = v, () -> deadTreeDensity);
            controlsContent.row();
            addSlider(controlsContent, "$text.filter.option.frozen-tree-density", 0f, 0.05f, v -> frozenTreeDensity = v, () -> frozenTreeDensity);
            controlsContent.row();
            addSlider(controlsContent, "$text.filter.option.ore-density", 0.1f, 1f, v -> oreDensity = v, () -> oreDensity);
            controlsContent.row();

            controlsContent.addCheck("$text.filter.option.generate-trees", b -> generateTrees = b).checked(generateTrees).left().padTop(8);
            controlsContent.row();
            controlsContent.addCheck("$text.filter.option.generate-ores", b -> generateOres = b).checked(generateOres).left();
            controlsContent.row();
            controlsContent.addCheck("$text.filter.option.generate-cliffs", b -> generateCliffs = b).checked(generateCliffs).left();
            controlsContent.row();
            controlsContent.row();

            controlsContent.add("$text.editor.oretech").left().padBottom(2f);
            controlsContent.row();

            Table techs = new Table();
            techs.defaults().left();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            int i = 0;
            for(String tree : TechTree.all()){
                techs.addButton(TechTree.localizedName(tree), "toggle", () -> oreTech = tree).update(b -> {
                    b.setChecked(oreTech.equals(tree));
                }).group(group).size(120f, 36f);
                if(i++ % 2 == 1) techs.row();
            }
            controlsContent.add(techs).left().padBottom(4f);
            controlsContent.row();

            controlsContent.add("$text.editor.oretech.info").color(Color.GRAY).wrap().width(280f).left();

            ScrollPane sp = new ScrollPane(controlsContent);
            sp.setScrollingDisabled(true, false);
            right.add(sp).grow().top().left();
        }).grow().top().left();

        updatePreview();
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
    }

    void addSeedField(Table t){
        t.table(seedTable -> {
            seedTable.left();
            seedTable.add("$text.editor.seed").padRight(10);
            seedField = new TextField("");
            seedField.setMessageText("random");
            seedTable.add(seedField).width(200f);
        }).left();
    }

    void addSlider(Table t, String label, float min, float max, Consumer<Float> setter, java.util.function.Supplier<Float> getter){
        t.table(sliderTable -> {
            sliderTable.defaults().left();
            sliderTable.add(label).width(160f).padRight(10);

            Slider slider = new Slider(min, max, (max - min) / 15f, false);
            slider.moved(setter);
            slider.setValue(getter.get());
            sliderTable.add(slider).growX().padRight(10);

            Label valueLabel = new Label(() -> Strings.toFixed(getter.get(), 2));
            sliderTable.add(valueLabel).width(60f).right();
        }).left();
    }

    Array<Item> genOres(){
        return generateOres ? Item.getAllOres(oreTech.equals(TechTree.defaultTech) ? null : oreTech) : new Array<>();
    }

    void applySettings(){
        WorldGenerator gen = world.generator;
        gen.elevationDensity = elevationDensity;
        gen.temperatureScale = temperatureScale;
        gen.lakeFactor = lakeFactor;
        gen.ridgeScale = ridgeScale;
        gen.treeDensity = treeDensity;
        gen.deadTreeDensity = deadTreeDensity;
        gen.frozenTreeDensity = frozenTreeDensity;
        gen.genOres = generateOres;
    }

    void updatePreview(){
        if(pixmap == null || texture == null) return;

        applySettings();

        long parsedSeed = parseSeed();
        int sx = (int)(parsedSeed % Short.MAX_VALUE);
        int sy = (int)(parsedSeed / Short.MAX_VALUE % Short.MAX_VALUE);

        WorldGenerator generator = world.generator;
        GenResult result = new GenResult();
        Array<GridPoint2> spawns = new Array<>();
        spawns.add(new GridPoint2(pixmap.getWidth() / 2, pixmap.getHeight() / 2));
        Array<Item> ores = genOres();

        for(int px = 0; px < pixmap.getWidth(); px++){
            for(int py = 0; py < pixmap.getHeight(); py++){
                int tx = px * 2;
                int ty = py * 2;
                generator.generateTile(result, sx, sy, tx, ty, true, spawns, ores);

                Block floor = io.anuke.mindustry.Vars.content.block(result.floor.id);
                Block wall = io.anuke.mindustry.Vars.content.block(result.wall.id);
                int color = ColorMapper.colorFor(floor, wall, Team.none, result.elevation, (byte)0);
                pixmap.drawPixel(px, pixmap.getHeight() - 1 - py, color);
            }
        }

        if(texture != null){
            texture.draw(pixmap, 0, 0);
        }
    }

    long parseSeed(){
        if(seedField == null) return seed;
        String text = seedField.getText();
        if(text == null || text.isEmpty()) return seed;
        try{
            return Long.parseLong(text);
        }catch(NumberFormatException e){
            return text.hashCode() & 0x7FFFFFFFFFFFFFFFL;
        }
    }

    void generate(){
        MapTileData data = editor.getMap();
        int width = data.width();
        int height = data.height();

        applySettings();

        long parsedSeed = parseSeed();
        int sx = (int)(parsedSeed % Short.MAX_VALUE);
        int sy = (int)(parsedSeed / Short.MAX_VALUE % Short.MAX_VALUE);

        WorldGenerator generator = world.generator;
        GenResult result = new GenResult();
        Array<GridPoint2> spawns = new Array<>();
        spawns.add(new GridPoint2(width / 2, height / 2));
        Array<Item> ores = genOres();

        Tile[][] tiles = new Tile[width][height];

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                generator.generateTile(result, sx, sy, x, y, true, spawns, ores);
                tiles[x][y] = new Tile(x, y, result.floor.id, result.wall.id, (byte)0, (byte)0, result.elevation);
            }
        }

        if(generateTrees){
            generator.spawnTrees(tiles);
        }

        generator.prepareTiles(tiles);

        if(generateCliffs){
            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    Tile tile = tiles[x][y];
                    byte elevation = tile.getElevation();

                    for(GridPoint2 point : Geometry.d4){
                        if(!Structs.inBounds(x + point.x, y + point.y, width, height)) continue;
                        if(tiles[x + point.x][y + point.y].getElevation() < elevation){
                            if(generator.sim2.octaveNoise2D(1, 1, 1.0 / 8, x, y) > 0.8){
                                tile.setElevation(-1);
                            }
                            break;
                        }
                    }
                }
            }
        }

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                Tile tile = tiles[x][y];
                data.write(x, y, MapTileData.DataPosition.floor, tile.floor().id);
                data.write(x, y, MapTileData.DataPosition.wall, tile.block().id);
                data.write(x, y, MapTileData.DataPosition.elevation, tile.getElevation());
            }
        }

        editor.renderer().updateAll();
    }
}
