package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.core.Platform;
import io.anuke.mindustry.game.Saves.SaveSlot;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.maps.campaign.CampaignRegistry;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.scene.Element;
import io.anuke.ucore.scene.event.InputEvent;
import io.anuke.ucore.scene.event.InputListener;
import io.anuke.ucore.scene.ui.Image;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.SettingsDialog;
import io.anuke.ucore.scene.ui.SettingsDialog.SettingsTable.Setting;
import io.anuke.ucore.scene.ui.Slider;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.anuke.mindustry.Vars.*;

public class SettingsMenuDialog extends SettingsDialog{
    public SettingsTable graphics;
    public SettingsTable game;
    public SettingsTable sound;
    public SettingsTable developer;

    private Table prefs;
    private Table menu;
    private boolean wasPaused;

    public SettingsMenuDialog(){
        setStyle(Core.skin.get("dialog", WindowStyle.class));

        hidden(() -> {
            if(!state.is(State.menu)){
                if(!wasPaused || Net.active())
                    state.set(State.playing);
            }
        });

        shown(() -> {
            if(!state.is(State.menu)){
                wasPaused = state.is(State.paused);
                state.set(State.paused);
            }
        });

        setFillParent(true);
        title().setAlignment(Align.center);
        getTitleTable().row();
        getTitleTable().add(new Image("white"))
                .growX().height(3f).pad(4f).get().setColor(Palette.accent);

        content().clearChildren();
        content().remove();
        buttons().remove();

        menu = new Table();

        Consumer<SettingsTable> s = table -> {
            table.row();
            table.addImageTextButton("$text.back", "icon-arrow-left", 10 * 3, this::back).size(240f, 60f).colspan(2).padTop(15f);
        };

        game = new SettingsTable(s);
        graphics = new SettingsTable(s);
        sound = new SettingsTable(s);
        developer = new SettingsTable(s);

        prefs = new Table();
        prefs.top();
        prefs.margin(14f);

        menu.defaults().size(300f, 60f).pad(3f);
        menu.addButton("$text.settings.game", () -> visible(0));
        menu.row();
        menu.addButton("$text.settings.graphics", () -> visible(1));
        menu.row();
        menu.addButton("$text.settings.sound", () -> visible(2));
        menu.row();
        menu.addButton("$text.settings.developer", () -> visible(3));
        if(!Vars.mobile){
            menu.row();
            menu.addButton("$text.settings.controls", ui.controls::show);
        }
        menu.row();
        menu.addButton("$text.settings.language", ui.language::show);
        menu.row();
        menu.addButton("$text.settings.data", this::showDataDialog);

        prefs.clearChildren();
        prefs.add(menu);

        ScrollPane pane = new ScrollPane(prefs);
        pane.addCaptureListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                Element actor = pane.hit(x, y, true);
                if(actor instanceof Slider){
                    pane.setFlickScroll(false);
                    return true;
                }

                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                pane.setFlickScroll(true);
                super.touchUp(event, x, y, pointer, button);
            }
        });
        pane.setFadeScrollBars(false);

        row();
        add(pane).grow().top();
        row();
        add(buttons()).fillX();

        hidden(this::back);

        addSettings();
    }

    void addSettings(){
        sound.volumePrefs();

        game.screenshakePref();
        game.checkPref("effects", true);
        game.checkPref("fogofwar", true);
        if(mobile){
            game.checkPref("autotarget", true);
        }
        game.sliderPref("saveinterval", 120, 10, 5 * 120, i -> Bundles.format("setting.seconds", i));
        game.checkPref("planet3d", true);
        game.checkPref("smoothcamera", false);
        game.checkPref("detach-camera", false);
        game.sliderPref("minzoomingamemultiplier", 100, 100, 300, 25, i -> (i / 100f) + "x");
        game.sliderPref("maxzoomingamemultiplier", 100, 100, 200, 25, i -> (i / 100f) + "x");

        game.pref(new SettingsTable.Setting(){
            @Override
            public void add(SettingsTable table){
                Settings.defaults("uisize", 100);
                Slider slider = new Slider(50, 200, 5, false);
                slider.setValue(Settings.getInt("uisize", 100));

                Label label = new Label(Bundles.get("setting.uisize.name", "UI Scale"));
                Label warn = new Label("[orange]" + Bundles.get("setting.uisize.restart", "Restart the game for UI changes to take effect!"));
                warn.setWrap(true);
                warn.setAlignment(Align.center, Align.center);

                slider.changed(() -> {
                    Settings.putInt("uisize", (int) slider.getValue());
                    Settings.save();
                    label.setText(Bundles.get("setting.uisize.name", "UI Scale") + ": " + (int) slider.getValue() + "%");
                    warn.setVisible((int) slider.getValue() != 100);
                });
                slider.change();

                table.add(label).minWidth(label.getPrefWidth() + 50).left().padTop(3f);
                table.add(slider).width(180).padTop(3f);
                table.row();
                table.add(warn).colspan(2).left().padTop(4f).width(340f);
                table.row();
            }
        });

        if(!mobile){
            game.checkPref("crashreport", false);
        }

        game.pref(new Setting(){
            @Override
            public void add(SettingsTable table){
                table.addButton("$text.settings.cleardata", SettingsMenuDialog.this::showDataDialog).size(220f, 60f).pad(6).left();
                table.add();
                table.row();
            }
        });

        graphics.sliderPref("fpscap", 125, 5, 240, 5, s -> (s > 240 ? Bundles.get("setting.fpscap.none") : Bundles.format("setting.fpscap.text", s)));

        if(!mobile){
            graphics.checkPref("vsync", true, b -> Gdx.graphics.setVSync(b));
            graphics.checkPref("fullscreen", false, b -> {
                if(b){
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                }else{
                    Gdx.graphics.setWindowedMode(600, 480);
                }
            });

            Gdx.graphics.setVSync(Settings.getBool("vsync"));
            if(Settings.getBool("fullscreen")){
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
        }

        graphics.checkPref("fps", false);
        graphics.checkPref("indicators", true);
        graphics.checkPref("lasers", true);
        graphics.checkPref("showweather", "Show Weather", true);
        graphics.checkPref("minimap", !mobile); //minimap is disabled by default on mobile devices

        graphics.sliderPref("renderer", "Render Scale", 100, 50, 100, i -> i + "%");
        graphics.checkPref("bloom", "Bloom", true);
        graphics.sliderPref("bloomintensity", "Bloom Intensity", 10, 5, 40, i -> (i / 10f) + "x");
        graphics.sliderPref("bloomblur", "Bloom Blur", 2, 1, 16, s -> s + "x");
        graphics.sliderPref("bloomthreshold", "Bloom Threshold", 15, 5, 80, i -> (i / 100f) + "");

        developer.checkPref("massai-debug", false, MassAI::setDebug);
        developer.checkPref("massai-path-debug", false);
        developer.checkPref("path-preview", false);
        developer.checkPref("showperformance", false);
        developer.checkPref("openworld-debug", false);
    }

    private void back(){
        prefs.clearChildren();
        prefs.add(menu);
    }

    private void visible(int index){
        prefs.clearChildren();
        Table table = Mathf.select(index, game, graphics, sound, developer);
        prefs.add(table);
    }

    private void showDataDialog(){
        FloatingDialog dialog = new FloatingDialog("$text.settings.data");
        dialog.setFillParent(false);
        dialog.content().defaults().size(300f, 60f).pad(3);
        dialog.addCloseButton();

        dialog.content().addButton("$text.settings.clearsaves", () -> {
            ui.showConfirm("$text.confirm", "$text.settings.clearsaves.confirm", () -> {
                control.saves.deleteAll();
                dialog.hide();
            });
        });
        dialog.content().row();

        dialog.content().addButton("$text.settings.resetunlocks", () -> {
            ui.showConfirm("$text.confirm", "$text.settings.clear.confirm", () -> {
                control.unlocks.reset();
                dialog.hide();
            });
        });
        dialog.content().row();

        dialog.content().addButton("$text.settings.resetcampaign", () -> {
            ui.showConfirm("$text.confirm", "$text.settings.resetcampaign.confirm", () -> {
                resetCampaign();
                dialog.hide();
            });
        });
        dialog.content().row();

        dialog.content().addButton("$text.settings.exportdata", () -> {
            ui.showConfirm("$text.confirm", "$text.settings.exportdata.confirm", () ->
                Platform.instance.showFileChooser("$text.settings.exportdata", "$text.settings.exportdata.confirm", file -> {
                    if(!file.extension().equals("zip")){
                        file = file.parent().child(file.nameWithoutExtension() + ".zip");
                    }
                    try{
                        exportData(file);
                        ui.showInfo("$text.settings.exported");
                    }catch(Exception e){
                        ui.showError(Strings.parseException(e, true));
                    }
                }, false, "zip"));
        });
        dialog.content().row();

        dialog.content().addButton("$text.settings.importdata", () -> {
            ui.showConfirm("$text.confirm", "$text.settings.importdata.confirm", () ->
                Platform.instance.showFileChooser("$text.settings.importdata", "$text.settings.importdata.confirm", file -> {
                    try{
                        importData(file);
                        Gdx.app.exit();
                    }catch(Exception e){
                        ui.showError(Strings.parseException(e, true));
                    }
                }, true, "zip"));
        });
        dialog.content().row();

        dialog.content().addButton("$text.settings.clearall", () -> {
            ui.showConfirm("$text.confirm", "$text.settings.clearall.confirm", () -> {
                Map<String, Object> map = new HashMap<>();
                for(String value : Settings.prefs().get().keySet()){
                    if(value.contains("usid") || value.contains("uuid")){
                        map.put(value, Settings.prefs().getString(value));
                    }
                }
                Settings.prefs().clear();
                Settings.prefs().put(map);
                Settings.save();

                for(FileHandle file : dataDirectory.list()){
                    file.deleteDirectory();
                }

                Gdx.app.exit();
            });
        });

        dialog.show();
    }

    private void resetCampaign(){
        for(SaveSlot slot : new Array<SaveSlot>(control.saves.getSaveSlots())){
            if(slot.isHidden()){
                slot.delete();
            }
        }

        String active = world.sectors.getActiveCampaign();
        for(String campaign : CampaignRegistry.all()){
            world.sectors.setActiveCampaign(campaign);
            world.sectors.clear();
        }
        world.sectors.setActiveCampaign(active);
    }
    private void exportData(FileHandle file) throws IOException{
        try(ZipOutputStream zos = new ZipOutputStream(file.write(false, 2048))){
            addDirectory(zos, dataDirectory, dataDirectory.path());

            //campaign save file is stored next to the app, include it for a complete backup
            //i will fix that in a future
            FileHandle campaigns = Platform.instance.getAppDirectory().child(SaveIO.CAMPAIGNS_SAVE_FILE);
            if(campaigns.exists()){
                addEntry(zos, campaigns, campaigns.parent().path());
            }
        }
    }

    private void importData(FileHandle file) throws IOException{
        boolean valid = false;
        try(ZipInputStream zis = new ZipInputStream(file.read())){
            ZipEntry entry;
            while((entry = zis.getNextEntry()) != null){
                String name = entry.getName();
                if(name.equals("io.anuke.mindustry") || name.equals("io.anuke.mindustry.server")){
                    valid = true;
                    break;
                }
            }
        }
        if(!valid){
            throw new IOException(Bundles.get("text.settings.invalid"));
        }

        for(FileHandle f : dataDirectory.list()){
            f.deleteDirectory();
        }

        try(ZipInputStream zis = new ZipInputStream(file.read())){
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            while((entry = zis.getNextEntry()) != null){
                String name = entry.getName();
                if(entry.isDirectory() || name.contains("..")){
                    continue;
                }
                FileHandle target = name.equals(SaveIO.CAMPAIGNS_SAVE_FILE)
                        ? Platform.instance.getAppDirectory().child(name)
                        : dataDirectory.child(name);
                target.parent().mkdirs();

                try(OutputStream os = target.write(false)){
                    int count;
                    while((count = zis.read(buffer)) != -1){
                        os.write(buffer, 0, count);
                    }
                }
                zis.closeEntry();
            }
        }

        Settings.load(appName, headless ? "io.anuke.mindustry.server" : "io.anuke.mindustry");
        Settings.save();
    }

    private void addDirectory(ZipOutputStream zos, FileHandle dir, String base) throws IOException{
        for(FileHandle child : dir.list()){
            if(child.isDirectory()){
                addDirectory(zos, child, base);
            }else{
                addEntry(zos, child, base);
            }
        }
    }

    private void addEntry(ZipOutputStream zos, FileHandle file, String base) throws IOException{
        String path = file.path().substring(base.length());
        if(path.startsWith("/") || path.startsWith("\\")){
            path = path.substring(1);
        }
        zos.putNextEntry(new ZipEntry(path.replace('\\', '/')));

        try(InputStream is = file.read()){
            byte[] buffer = new byte[8192];
            int count;
            while((count = is.read(buffer)) != -1){
                zos.write(buffer, 0, count);
            }
        }
        zos.closeEntry();
    }

    @Override
    public void addCloseButton(){
        buttons().addImageTextButton("$text.menu", "icon-arrow-left", 30f, this::hide).size(230f, 64f);

        keyDown(key -> {
            if(key == Keys.ESCAPE || key == Keys.BACK)
                hide();
        });
    }
}
