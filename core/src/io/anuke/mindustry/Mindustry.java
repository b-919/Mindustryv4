package io.anuke.mindustry;

import io.anuke.mindustry.core.*;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.game.EventType.GameLoadEvent;
import io.anuke.mindustry.io.BundleLoader;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.modules.ModuleCore;
import io.anuke.ucore.scene.ui.layout.Unit;
import io.anuke.ucore.util.Log;

import static io.anuke.mindustry.Vars.*;

public class Mindustry extends ModuleCore{

    @Override
    public void init(){
        Timers.mark();

        Vars.init();

        Log.setUseColors(false);
        BundleLoader.load();
        Unit.dp.product = Settings.getInt("uisize", 100) / 100f;
        Unit.dp.reset();
        content.load();
        schematics.load();

        weather = new WeatherState();
        module(logic = new Logic());
        module(world = new World());
        module(soundController = new SoundController());
        Sounds.init();
        module(control = new Control());
        module(renderer = new Renderer());
        module(ui = new UI());
        module(netServer = new NetServer());
        module(netClient = new NetClient());
        module(musicController = new MusicController());
    }

    @Override
    public void postInit(){
        launchManager.load();
        Log.info("Time to load [total]: {0}", Timers.elapsed());
        Events.fire(new GameLoadEvent());
    }

    @Override
    public void render(){
        threads.handleBeginRender();
        PerfCounter.frame.begin();
        super.render();
        PerfCounter.frame.end();
        threads.handleEndRender();
    }

}
