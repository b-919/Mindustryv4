package io.anuke.mindustry.graphics;

import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.type.Weather;
import io.anuke.ucore.core.Settings;

import static io.anuke.mindustry.Vars.headless;
import static io.anuke.mindustry.Vars.state;
import static io.anuke.mindustry.Vars.weather;

/** Draws weather effects on top of the world.*/
public class WeatherRenderer{

    /**Draws weather effects on top of the world. */
    public void drawOver(){
        if(!canDraw()) return;
        weather.active().drawOver(weather.opacity() * weather.intensity());
    }

    /**Draws weather effects under units, e.g. splashes. */
    public void drawUnder(){
        if(!canDraw()) return;
        weather.active().drawUnder(weather.opacity() * weather.intensity());
    }

    private boolean canDraw(){
        Weather active = weather.active();
        return !headless && active != null && weather.opacity() > 0.001f
                && Settings.getBool("showweather", true)
                && (state.is(State.playing) || state.is(State.paused));
    }
}