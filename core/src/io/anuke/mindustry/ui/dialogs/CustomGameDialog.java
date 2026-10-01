package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.TechTree;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.ui.BorderImage;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.scene.event.Touchable;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.TextButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.*;

public class CustomGameDialog extends FloatingDialog{
    private WeatherRulesDialog weatherDialog = new WeatherRulesDialog();

    public CustomGameDialog(){
        super("$text.customgame");
        addCloseButton();
        shown(() -> {
            state.darkness = 0f;
            state.rain = false;
            control.customDarkness = false;
            weather.reset();
            setup();
        });

        onResize(this::setup);
    }

    void setup(){
        content().clear();

        Table maps = new Table();
        maps.marginRight(14);
        ScrollPane pane = new ScrollPane(maps);
        pane.setFadeScrollBars(false);

        int maxwidth = (Gdx.graphics.getWidth() > Gdx.graphics.getHeight() ? 4 : 2);

        Table selmode = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        selmode.add("$text.level.mode").padRight(15f);
        int i = 0;

        Table modes = new Table();
        modes.marginBottom(5);

        for(GameMode mode : GameMode.values()){
            if(mode.hidden) continue;

            modes.addButton("$mode." + mode.name() + ".name", "toggle", () -> state.mode = mode)
                .update(b -> b.setChecked(state.mode == mode)).group(group).size(140f, 54f);
            if(i++ % 2 == 1) modes.row();
        }
        selmode.add(modes);
        selmode.addButton("?", this::displayGameModeHelp).width(50f).fillY().padLeft(18f);
        selmode.addImageButton("icon-tools", this::displayGameModeRules).size(50f, 54f).padLeft(6f);

        content().add(selmode);
        content().row();

        Difficulty[] ds = Difficulty.values();

        float s = 50f;

        Table sdif = new Table();

        sdif.add("$setting.difficulty.name").padRight(15f);

        sdif.defaults().height(s + 4);
        sdif.addImageButton("icon-arrow-left", 10 * 3, () -> {
            state.difficulty = (ds[Mathf.mod(state.difficulty.ordinal() - 1, ds.length)]);
        }).width(s);

        sdif.addButton("", () -> {})
        .update(t -> {
            t.setText(state.difficulty.toString());
            t.setTouchable(Touchable.disabled);
        }).width(180f);

        sdif.addImageButton("icon-arrow-right", 10 * 3, () -> {
            state.difficulty = (ds[Mathf.mod(state.difficulty.ordinal() + 1, ds.length)]);
        }).width(s);

        content().add(sdif);
        content().row();


        float images = 146f;

        i = 0;
        maps.defaults().width(170).fillY().top().pad(4f);
        for(Map map : world.maps.all()){

            if(i % maxwidth == 0){
                maps.row();
            }

            ImageButton image = new ImageButton(new TextureRegion(map.texture), "clear");
            image.margin(5);
            image.getImageCell().size(images);
            image.top();
            image.row();
            image.add("[accent]" + map.getDisplayName()).pad(3f).growX().wrap().get().setAlignment(Align.center, Align.center);
            image.row();
            image.label((() -> Bundles.format("text.level.highscore", Settings.getInt("hiscore" + map.name, 0)))).pad(3f);

            BorderImage border = new BorderImage(map.texture, 3f);
            border.setScaling(Scaling.fit);
            image.replaceImage(border);

            image.clicked(() -> {
                hide();
                weather.autoSelect(map);
                control.playMap(map);
            });

            maps.add(image);

            i++;
        }

        ImageButton gen = maps.addImageButton("icon-editor", "clear", 16*4, () -> {
            hide();
            world.generator.playRandomMap();
        }).growY().get();
        gen.row();
        gen.add("$text.map.random");

        if(world.maps.all().size == 0){
            maps.add("$text.maps.none").pad(50);
        }

        content().add(pane).uniformX();
    }

    private void displayGameModeHelp(){
        FloatingDialog d = new FloatingDialog(Bundles.get("mode.text.help.title"));
        d.setFillParent(false);
        Table table = new Table();
        table.defaults().pad(1f);
        ScrollPane pane = new ScrollPane(table);
        pane.setFadeScrollBars(false);
        table.row();
        for(GameMode mode : GameMode.values()){
            if(mode.hidden) continue;
            table.labelWrap("[accent]" + mode.toString() + ":[] [lightgray]" + mode.description()).width(400f);
            table.row();
        }

        d.content().add(pane).width(Math.min(480f, Gdx.graphics.getWidth() - 30f))
                .height(Math.min(520f, Gdx.graphics.getHeight() - 90f));
        d.buttons().addButton("$text.ok", d::hide).size(110, 50).pad(10f);
        d.keyDown(Keys.ESCAPE, d::hide);
        d.keyDown(Keys.BACK, d::hide);
        d.show();
    }

    private void displayGameModeRules(){
        GameMode mode = state.mode;

        FloatingDialog d = new FloatingDialog(mode.toString() + " rules");
        d.setFillParent(false);
        //TODO Replace placeholder texts with bundle localisations
        Table table = new Table();
        table.defaults().pad(2f).left();
        table.add("[accent]" + mode.toString()).left();
        table.row();
        table.add("[lightgray]" + mode.description()).width(400f).wrap().left();
        table.row();
        table.row();
        table.addCheck("Infinite resources", mode.infiniteResources, b -> mode.infiniteResources = b).left();
        table.row();
        table.addCheck("Disable wave timer", mode.disableWaveTimer, b -> mode.disableWaveTimer = b).left();
        table.row();
        table.addCheck("Disable waves", mode.disableWaves, b -> mode.disableWaves = b).left();
        table.row();
        table.addCheck("Show mission", mode.showMission, b -> mode.showMission = b).left();
        table.row();
        table.addCheck("Enemy cheat", mode.enemyCheat, b -> mode.enemyCheat = b).left();
        table.row();
        table.addCheck("PvP", mode.isPvp, b -> mode.isPvp = b).left();
        table.row();
        table.add("Tech Tree").padTop(8f).left();
        table.row();
        table.add("$text.techtree.info").color(Color.GRAY).wrap().width(300f).left().padBottom(4f);
        table.row();

        Table techs = new Table();
        ButtonGroup<TextButton> techGroup = new ButtonGroup<>();
        int ti = 0;
        for(int i = 0; i < TechTree.all().size; i++){
            String tree = TechTree.all().get(i);
            techs.addButton(TechTree.localizedName(tree), "toggle", () -> {
                state.techTree = tree.equals(TechTree.defaultTech) ? null : tree;
            }).update(b -> {
                String current = state.techTree == null ? TechTree.defaultTech : state.techTree;
                b.setChecked(current.equals(tree));
            }).group(techGroup).size(140f, 40f);
            if(ti++ % 2 == 1) techs.row();
        }
        table.add(techs).left().padBottom(8f);
        table.row();

        table.add("Enemy Selector").padTop(8f).left();
        table.row();

        Table enemies = new Table();
        ButtonGroup<TextButton> enemyGroup = new ButtonGroup<>();
        int j = 0;
        for(Team team : Team.all){
            if(team == Team.none) continue;

            enemies.addButton("$team." + team.name() + ".name", "toggle", () -> state.enemyTeam = team)
                .update(b -> b.setChecked(state.enemyTeam == team)).group(enemyGroup).size(140f, 54f);
            if(j++ % 2 == 1) enemies.row();
        }

        table.add(enemies).left();
        table.row();
        table.addCheck("$text.customgame.allowRandomInfection", state.allowMassInfection, b -> state.allowMassInfection = b).left();
        table.row();
        table.addCheck("$text.customgame.startWithBiomass", state.startWithBiomass, b -> state.startWithBiomass = b).left();
        table.row();

        table.add("Map Darkness: " + (int)(state.darkness * 100) + "%").update(l -> l.setText("Map Darkness: " + (int)(state.darkness * 100) + "%")).padTop(8f).left();
        table.row();
        table.addSlider(0f, 1f, 0.01f, state.darkness, f -> {
            state.darkness = f;
            control.customDarkness = true;
        }).width(200f).left();
        table.row();
        table.addButton("$text.weather.title", weatherDialog::show).size(200f, 40f).left();
        table.row();
        table.add("$text.weather.rules.info", Color.GRAY).wrap().width(300f).left().padBottom(6f);
        table.row();
        table.add("RTS AI Teams").padTop(8f).left();
        table.row();

        for(Team team : Team.all){
            if(team == Team.none || team == Team.themass) continue;

            table.addCheck("$team." + team.name() + ".name", (state.rtsAIBits & (1L << team.ordinal())) != 0, b -> {
                if(b){
                    state.rtsAIBits |= (1L << team.ordinal());
                }else{
                    state.rtsAIBits &= ~(1L << team.ordinal());
                }
            }).left();
            table.row();
        }

        ScrollPane pane = new ScrollPane(table);
        pane.setFadeScrollBars(false);

        d.content().add(pane).width(Math.min(480f, Gdx.graphics.getWidth() - 30f))
                .height(Math.min(640f, Gdx.graphics.getHeight() - 90f));
        d.buttons().addButton("$text.ok", d::hide).size(110, 50).pad(10f);
        d.keyDown(Keys.ESCAPE, d::hide);
        d.keyDown(Keys.BACK, d::hide);
        d.show();
    }

}
