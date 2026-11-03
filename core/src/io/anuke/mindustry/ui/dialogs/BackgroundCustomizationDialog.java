package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.editor.SpaceCameraDialog;
import io.anuke.mindustry.graphics.MenuBackground;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.scene.ui.*;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.*;

import static io.anuke.mindustry.Vars.ios;
import static io.anuke.mindustry.Vars.ui;

/** Dialog for the customization of the main menu Background */
public class BackgroundCustomizationDialog extends FloatingDialog{
    private static final int sectionNone = -1, sectionShader = 0, sectionSpace = 1, sectionSimulation = 2;
    private static final String shaderGuideUrl =
        "https://github.com/b-919/Mindustryv4/blob/mainv4/BACKGROUND_SHADER_GUIDE.md";

    private final MenuBackground background = ui.backfrag.background;
    private int section = sectionNone;

    public BackgroundCustomizationDialog(){
        super("$text.settings.background");
        setFillParent(true);
        addCloseButton();
        shown(this::buildUi);
    }

    private void buildUi(){
        content().clear();
        content().margin(8f);

        Table root = new Table();
        root.top();

        Table tabs = new Table();
        float size = Math.max(90f, Math.min(190f, Math.min(Gdx.graphics.getWidth() / 4.2f, Gdx.graphics.getHeight() / 3.2f)));
        float pad = Math.max(8f, size * 0.13f);
        tabs.defaults().size(size, size).pad(pad);
        addSectionButton(tabs, "$text.background.shader", sectionShader);
        addSectionButton(tabs, "$text.background.space", sectionSpace);
        addSectionButton(tabs, "$text.background.simulation", sectionSimulation);

        Table options = new Table();
        options.top();

        if(section == sectionShader){
            addShaderOptions(options);
        }else if(section == sectionSpace){
            addSpaceOptions(options);
        }else if(section == sectionSimulation){
            addSimulationOptions(options);
        }

        if(section == sectionNone){
            root.add().expandY().row();
            root.add(tabs).row();
            root.add().expandY().row();
        }else{
            root.add(tabs).padTop(30f).row();

            if(section == sectionShader){
                root.addButton("$text.background.reset", this::resetShaderValues)
                    .width(300f).height(44f).padTop(4f).row();
            }

            ScrollPane pane = new ScrollPane(options);
            pane.setFadeScrollBars(false);
            root.add(pane).grow().padTop(12f).padLeft(30f).padRight(30f).padBottom(10f).row();
        }

        content().add(root).grow();
    }

    private float optionsWidth(){
        return Math.max(260f, Math.min(560f, Gdx.graphics.getWidth() - 80f));
    }

    private void addSectionButton(Table table, String title, int target){
        TextButton button = table.addButton(title, "toggle", () -> {
            section = section == target ? sectionNone : target;
            buildUi();
        }).get();

        button.setChecked(section == target);
    }

    private void addShaderOptions(Table root){
        Table list = new Table();
        list.defaults().width(optionsWidth()).height(48f).pad(3f);

        list.addButton(shaderTitle(Bundles.get("text.background.default", "Default Shader"),
            !background.isSpace() && background.isDefaultShader()), () -> {
            background.setShader(MenuBackground.shaderDefault);
            Shaders.menu.loadConfig();
            buildUi();
        }).row();

        Array<FileHandle> shaders = background.customShaders();
        for(int i = 0; i < shaders.size; i++){
            FileHandle file = shaders.get(i);
            String name = file.name();
            list.addButton(shaderTitle(name, !background.isSpace() && name.equals(background.shaderName())), () -> {
                background.setShader(name);
                buildUi();
            }).row();
        }

        if(shaders.size == 0){
            list.add(Bundles.get("text.background.none", "No custom shaders imported.")).pad(4f).row();
        }

        if(!ios){
            Table importRow = new Table();
            importRow.addButton("$text.background.import", this::importShader).size(260f, 48f).padRight(4f);
            importRow.addButton("?", () -> {
                if(!Gdx.net.openURI(shaderGuideUrl)){
                    ui.showError("$text.linkfail");
                    Gdx.app.getClipboard().setContents(shaderGuideUrl);
                }
            }).size(48f, 48f);

            list.add(importRow).row();
        }

        if(!background.isDefaultShader()){
            list.addButton("$text.background.remove", () -> {
                background.removeShader(background.shaderName());
                buildUi();
            }).width(260f).row();
        }

        root.add(list).row();

        if(background.shaderFailed()){
            root.add("[scarlet]" + Bundles.get("text.background.failed",
                "This shader failed to compile; the default background is shown instead."))
                .width(optionsWidth()).wrap().pad(4f).row();
        }

        if(!background.isSpace() && background.isDefaultShader()){
            addShaderConfig(root);
        }
    }

    private void addShaderConfig(Table root){
        Table config = new Table();
        config.left();
        config.defaults().pad(3f);

        config.add(Bundles.get("text.background.values", "Default shader values")).padTop(8f).row();

        addSlider(config, Bundles.get("text.background.speed", "Animation Speed"), "bg-shader-speed", 50f, 5f, 500f, 5f, "");
        addSlider(config, Bundles.get("text.background.round", "Cell Size"), "bg-shader-round", 8f, 1f, 32f, 1f, "");
        addSlider(config, Bundles.get("text.background.wave", "Wave Scale"), "bg-shader-wave", 120f, 20f, 500f, 5f, "");
        addSlider(config, Bundles.get("text.background.brightness", "Brightness"), "bg-shader-brightness", 50f, 0f, 200f, 5f, "%");
        addSlider(config, Bundles.get("text.background.stripe", "Stripe Width"), "bg-shader-stripe", 35f, 0f, 110f, 1f, "");

        addColor(config, Bundles.get("text.background.color1", "Color 1"), "bg-shader-color1", "ffd37f");
        addColor(config, Bundles.get("text.background.color2", "Color 2"), "bg-shader-color2", "eab678");
        addColor(config, Bundles.get("text.background.color3", "Color 3"), "bg-shader-color3", "d4816b");
        addColor(config, Bundles.get("text.background.color4", "Color 4"), "bg-shader-color4", "8e4d48");

        root.add(config).padTop(6f).row();
    }

    private void resetShaderValues(){
        Settings.putString("bg-shader-color1", "ffd37f");
        Settings.putString("bg-shader-color2", "eab678");
        Settings.putString("bg-shader-color3", "d4816b");
        Settings.putString("bg-shader-color4", "8e4d48");
        Settings.putFloat("bg-shader-round", 8f);
        Settings.putFloat("bg-shader-speed", 50f);
        Settings.putFloat("bg-shader-wave", 120f);
        Settings.putFloat("bg-shader-brightness", 50f);
        Settings.putFloat("bg-shader-stripe", 35f);
        Settings.putFloat("bg-shader-period", 110f);
        Settings.save();
        Shaders.menu.loadConfig();
        buildUi();
    }

    private void addSpaceOptions(Table root){
        Table table = new Table();
        table.defaults().width(optionsWidth()).height(48f).pad(3f);

        table.addButton(background.isSpace()
            ? Bundles.get("text.background.space.active", "Space Render (Active)")
            : Bundles.get("text.background.space.use", "Use Space Render"), () -> {
            background.setSpace();
            buildUi();
        }).row();

        table.addButton("$text.background.space.configure", this::showSpaceEditor).width(320f).row();

        root.add(table).row();
    }

    private void addSimulationOptions(Table root){
        Table table = new Table();
        table.addButton("$text.background.simulation.use", () -> ui.showInfo(Bundles.get(
            "text.background.simulation.wip", "Simulation backgrounds are a work in progress.")))
            .width(320f).height(48f);

        root.add(table).row();
    }

    private void addSlider(Table table, String title, String key, float def, float min, float max,
                           float step, String suffix){
        float value = Settings.getFloat(key, def);

        Slider slider = new Slider(min, max, step, false);
        slider.setValue(value);

        Label label = new Label(title + ": " + (int)slider.getValue() + suffix);
        slider.changed(() -> {
            int valueNow = (int)slider.getValue();
            Settings.putFloat(key, valueNow);
            Settings.save();
            Shaders.menu.loadConfig();
            label.setText(title + ": " + valueNow + suffix);
        });

        table.add(label).width(optionsWidth() * 0.52f);
        table.add(slider).width(optionsWidth() * 0.44f);
        table.row();
    }

    private void addColor(Table table, String title, String key, String def){
        Color color = Color.valueOf(Settings.getString(key, def));

        Image swatch = new Image("white");
        swatch.setColor(color);

        table.add(title).width(optionsWidth() * 0.28f);
        table.add(swatch).size(42f, 28f).padRight(8f);
        table.addButton("$text.background.change", () -> new ColorPickerDialog().show(color, selected -> {
            Settings.putString(key, selected.toString().substring(0, 6));
            Settings.save();
            Shaders.menu.loadConfig();
            buildUi();
        })).size(Math.min(160f, optionsWidth() * 0.3f), 42f);
        table.row();
    }

    private void importShader(){
        Platform.instance.showFileChooser("$text.background.import", "GLSL Shader", file -> {
            try{
                background.importShader(file);
                buildUi();
                ui.showInfoFade(Bundles.get("text.background.imported", "Shader imported."));
            }catch(Exception e){
                ui.showError(Strings.parseException(e, true));
            }
        }, true, "fragment,frag,glsl");
    }

    private void showSpaceEditor(){
        new SpaceCameraDialog(background::cameraTags, () -> {
            background.saveCamera(background.cameraTags());
            buildUi();
            ui.showInfoFade(Bundles.get("text.background.space.saved", "Space render saved."));
        }).show();
    }

    private String shaderTitle(String title, boolean active){
        return active ? title + " (active)" : title;
    }
}
