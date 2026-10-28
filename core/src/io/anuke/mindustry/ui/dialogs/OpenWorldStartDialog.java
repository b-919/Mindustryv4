package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.EventType.WorldLoadEvent;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.TechTree;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.mindustry.maps.generation.OpenWorldSaveManager;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.TextButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Strings;

import java.text.SimpleDateFormat;
import java.util.Date;

import static io.anuke.mindustry.Vars.*;

public class OpenWorldStartDialog extends FloatingDialog{
    private String seedText = "";
    private String worldName = "";
    private String techTree = TechTree.defaultTech;
    private final OpenWorldSaveManager saveManager = new OpenWorldSaveManager();

    public OpenWorldStartDialog(){
        super("$text.openworld.start");
        addCloseButton();

        shown(() -> {
            seedText = "";
            worldName = "";
            techTree = TechTree.defaultTech;
            rebuild();
        });
    }

    void rebuild(){
        content().clear();

        Table main = new Table();
        main.defaults().pad(5f);

        // New World Section
        main.add("[accent]" + "$text.openworld.new").colspan(2).left().padBottom(8f);
        main.row();

        Table newWorldTable = new Table();
        newWorldTable.defaults().pad(4f);

        newWorldTable.add("$text.openworld.name").padRight(10f);
        newWorldTable.addField("", text -> worldName = text).size(180f, 40f);
        newWorldTable.row();

        newWorldTable.add("$text.openworld.seed").padRight(10f);
        newWorldTable.addField("", text -> seedText = text).size(180f, 40f);
        newWorldTable.row();

        newWorldTable.add("$setting.difficulty.name").padRight(10f);
        Table difButtons = new Table();
        difButtons.defaults().size(60f, 34f);
        difButtons.addImageButton("icon-arrow-left", 10 * 3, () -> {
            state.difficulty = Difficulty.values()[
                Mathf.mod(state.difficulty.ordinal() - 1, Difficulty.values().length)];
        });
        difButtons.addButton("", "toggle", () -> {}).update(b -> b.setText(state.difficulty.toString())).disabled(true).size(90f, 34f);
        difButtons.addImageButton("icon-arrow-right", 10 * 3, () -> {
            state.difficulty = Difficulty.values()[
                Mathf.mod(state.difficulty.ordinal() + 1, Difficulty.values().length)];
        });
        newWorldTable.add(difButtons);
        newWorldTable.row();

        newWorldTable.add("$text.techtree.select").padRight(10f);
        Table techButtons = new Table();
        techButtons.defaults().size(90f, 34f);
        ButtonGroup<TextButton> techGroup = new ButtonGroup<>();
        for(int i = 0; i < TechTree.all().size; i++){
            String tree = TechTree.all().get(i);
            techButtons.addButton(TechTree.localizedName(tree), "toggle", () -> techTree = tree)
                .update(b -> b.setChecked(tree.equals(techTree))).group(techGroup).size(90f, 34f);
            if(i % 2 == 1) techButtons.row();
        }
        newWorldTable.add(techButtons);
        newWorldTable.row();

        main.add(newWorldTable).colspan(2).left().padBottom(8f);
        main.row();

        main.addButton("$text.openworld.start", this::startNewWorld).size(200f, 45f).colspan(2).padTop(5f);
        main.row();

        // Load World Section
        Array<String> saves = saveManager.listSaves();

        if(saves.size > 0){
            main.add("[accent]" + "$text.openworld.load").colspan(2).left().padBottom(8f).padTop(15f);
            main.row();

            Table saveList = new Table();
            ScrollPane pane = new ScrollPane(saveList);
            pane.setFadeScrollBars(false);

            for(String saveName : saves){
                OpenWorldSaveManager.OpenWorldMeta meta = saveManager.readMeta(saveName);
                if(meta == null) continue;

                TextButton button = new TextButton(saveName, "clear");
                button.getLabelCell().growX().left();

                button.defaults().left();
                button.table(t -> {
                    t.right();
                    t.addImageButton("icon-trash", "empty", 14 * 3, () -> {
                        ui.showConfirm("$text.confirm", "$text.openworld.delete.confirm", () -> {
                            saveManager.deleteWorld(saveName);
                            rebuild();
                        });
                    }).size(14 * 3).right();
                }).padRight(-10).growX();

                button.row();
                String color = "[lightgray]";
                button.add(color + meta.seed);
                button.row();
                button.add(color + meta.difficulty);
                button.row();
                button.add(color + Strings.formatMillis(meta.timePlayed));
                button.row();
                button.add(color + meta.chunksSaved + " chunks");
                button.row();
                if(meta.dateCreated != null && !meta.dateCreated.isEmpty()){
                    button.add(color + meta.dateCreated);
                    button.row();
                }

                button.clicked(() -> {
                    if(!button.childrenPressed()){
                        loadWorld(saveName, meta);
                    }
                });

                saveList.add(button).uniformX().fillX().pad(4).margin(10f).padRight(-4);
                saveList.row();
            }

            pane.setScrollingDisabled(true, false);
            main.add(pane).colspan(2).fillX().height(Math.min(saves.size * 120f + 20f, 250f));
            main.row();
        }

        content().clear();
        content().add(main).pad(15f);
    }

    void startNewWorld(){
        long seed;
        if(seedText == null || seedText.trim().isEmpty()){
            seed = MathUtils.random(Long.MIN_VALUE, Long.MAX_VALUE);
        }else{
            seed = seedText.trim().hashCode();
        }

        String saveName = worldName;
        if(saveName == null || saveName.trim().isEmpty()){
            saveName = "world_" + System.currentTimeMillis();
        }else{
            saveName = saveName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        }

        // puts unique name
        for(String existing : saveManager.listSaves()){
            if(existing.equals(saveName)){
                saveName += "_" + System.currentTimeMillis();
                break;
            }
        }

        hide();

        final String finalSaveName = saveName;
        ui.loadLogic(() -> {
            logic.reset();
            state.mode = GameMode.openWorld;
            state.techTree = techTree.equals(TechTree.defaultTech) ? null : techTree;

            world.beginOpenWorld(seed, finalSaveName);

            ChunkManager cm = world.chunks();

            int spawnCX = 0;
            int spawnCY = 0;

            for(int dx = -1; dx <= 1; dx++){
                for(int dy = -1; dy <= 1; dy++){
                    cm.getOrCreateChunk(spawnCX + dx, spawnCY + dy);
                }
            }

            cm.registerCoreChunks(spawnCX, spawnCY);

            // write metadata
            OpenWorldSaveManager.OpenWorldMeta meta = new OpenWorldSaveManager.OpenWorldMeta();
            meta.name = finalSaveName;
            meta.seed = seed;
            meta.difficulty = state.difficulty;
            meta.techTree = state.techTree == null ? "" : state.techTree;
            meta.dateCreated = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
            meta.build = io.anuke.mindustry.game.Version.build;
            meta.chunksSaved = 0;
            saveManager.writeMeta(finalSaveName, meta);

            int coreTileX = spawnCX * ChunkManager.CHUNK_SIZE + ChunkManager.CHUNK_SIZE / 2;
            int coreTileY = spawnCY * ChunkManager.CHUNK_SIZE + ChunkManager.CHUNK_SIZE / 2;

            Tile coreTile = findPlaceableTile(coreTileX, coreTileY);

            world.setBlock(coreTile, StorageBlocks.core, Team.blue);
            players[0].set(coreTile.worldx(), coreTile.worldy());

            Events.fire(new WorldLoadEvent());

            logic.play();
        });
    }

    void loadWorld(String saveName, OpenWorldSaveManager.OpenWorldMeta meta){
        hide();

        ui.loadLogic(() -> {
            logic.reset();
            state.mode = GameMode.openWorld;
            state.difficulty = meta.difficulty;
            state.techTree = meta.techTree == null || meta.techTree.isEmpty() || meta.techTree.equals(TechTree.defaultTech) ? null : meta.techTree;

            world.beginOpenWorld(meta.seed, saveName);

            ChunkManager cm = world.chunks();

            // load nearby chunks
            for(int dx = -ChunkManager.RENDER_RADIUS; dx <= ChunkManager.RENDER_RADIUS; dx++){
                for(int dy = -ChunkManager.RENDER_RADIUS; dy <= ChunkManager.RENDER_RADIUS; dy++){
                    cm.getOrCreateChunk(dx, dy);
                }
            }

            cm.registerCoreChunks(0, 0);

            cm.rebuildAfterLoad();

            cm.getSaveManager().loadEntities(saveName);
            cm.getSaveManager().loadPlans(saveName);

            // restore player position from meta
            if(meta.playerX != 0 || meta.playerY != 0){
                players[0].set(meta.playerX, meta.playerY);
            }

            Events.fire(new WorldLoadEvent());

            // find core
            Tile coreTile = null;
            for(int r = 0; r < ChunkManager.CHUNK_SIZE; r++){
                for(int dx = -r; dx <= r; dx++){
                    for(int dy = -r; dy <= r; dy++){
                        if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                        int x = dx + ChunkManager.CHUNK_SIZE / 2;
                        int y = dy + ChunkManager.CHUNK_SIZE / 2;
                        Tile tile = world.rawTile(x, y);
                        if(tile != null && tile.block() == StorageBlocks.core){
                            coreTile = tile;
                            break;
                        }
                    }
                    if(coreTile != null) break;
                }
                if(coreTile != null) break;
            }

            if(coreTile == null){
                int coreTileX = ChunkManager.CHUNK_SIZE / 2;
                int coreTileY = ChunkManager.CHUNK_SIZE / 2;
                coreTile = findPlaceableTile(coreTileX, coreTileY);
                world.setBlock(coreTile, StorageBlocks.core, Team.blue);
            }

            if(meta.playerX == 0 && meta.playerY == 0){
                players[0].set(coreTile.worldx(), coreTile.worldy());
            }

            logic.play();
        });
    }

    private Tile findPlaceableTile(int startX, int startY){
        for(int r = 0; r < ChunkManager.CHUNK_SIZE; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    int x = startX + dx, y = startY + dy;
                    Tile tile = world.rawTile(x, y);
                    if(tile != null && !tile.floor().isLiquid && tile.block() == Blocks.air){
                        return tile;
                    }
                }
            }
        }
        return world.rawTile(startX, startY);
    }
}
