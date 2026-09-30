package io.anuke.mindustry.type;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

import static io.anuke.mindustry.Vars.world;


/**
 * Rain weather, drawn as moving line streaks with ground splashes.
 * Adapted from modern Mindustry's RainWeather class.
 */
public class RainWeather extends Weather{
    /**Fall speed in world units per tick (60 ticks = 1 second).*/
    public float yspeed = 5f;
    /**Horizontal wind speed in world units per tick (60 ticks = 1 second).*/
    public float xspeed = 1.5f;
    /**How far beyond the screen rain is still drawn, for seamless wrapping.*/
    public float padding = 16f;
    /**Rain density: one drop per this many world units squared.*/
    public float density = 1200f;
    public float stroke = 0.75f;
    public float sizeMin = 8f;
    public float sizeMax = 40f;
    public float splashTimeScale = 22f;
    public Color color = Color.valueOf("7a95ea");

    private static final float boundMax = 10000 * 8f;

    public RainWeather(String name){
        super(name);
    }

    @Override
    public void drawOver(float alpha){
        drawRain(sizeMin, sizeMax, xspeed, yspeed, density, alpha, stroke, color);
    }

    @Override
    public void drawUnder(float alpha){
        drawSplashes(sizeMax, density, alpha, splashTimeScale, stroke, color);
    }

    /**Draws rain as moving line streaks. Adapted from modern Mindustry's Weather.drawRain().*/
    public void drawRain(float sizeMin, float sizeMax, float xspeed, float yspeed, float density, float intensity, float stroke, Color color){
        rand.setSeed(0);
        float pad = sizeMax * 0.9f;
        float time = Timers.time();

        viewRect(pad);

        int total = (int)(rect.area() / density * intensity);
        Lines.stroke(stroke);
        Draw.color(color);

        for(int i = 0; i < total; i++){
            float scl = rand.nextFloat() * 0.5f + 0.5f;
            float scl2 = rand.nextFloat() * 0.5f + 0.5f;
            float size = rand.nextFloat() * (sizeMax - sizeMin) + sizeMin;
            float x = (rand.nextFloat() * boundMax + time * xspeed * scl2);
            float y = (rand.nextFloat() * boundMax - time * yspeed * scl);
            float tint = rand.nextFloat() * intensity;

            x = Mathf.mod(x - rect.x, rect.width) + rect.x;
            y = Mathf.mod(y - rect.y, rect.height) + rect.y;

            if(Tmp.r3.set(x - size / 2, y - size / 2, size, size).overlaps(visible)){
                Draw.alpha(tint);
                Lines.lineAngle(x, y, Mathf.atan2(xspeed * scl2, -yspeed * scl), size / 2f);
            }
        }

        Draw.reset();
    }

    /**Draws ground splashes. Adapted from modern Mindustry's Weather.drawSplashes().*/
    public void drawSplashes(float padding, float density, float intensity, float timeScale, float stroke, Color color){
        rand.setSeed(0);
        float time = Timers.time();

        viewRect(padding);

        int total = (int)(rect.area() / density * intensity) / 2;
        Lines.stroke(stroke);

        float t = time / timeScale;

        for(int i = 0; i < total; i++){
            float offset = rand.nextFloat();
            float time2 = t + offset;
            int pos = (int)time2;
            float life = time2 % 1f;

            float x = rand.nextFloat() * boundMax + pos * 953f;
            float y = rand.nextFloat() * boundMax - pos * 453f;

            x = Mathf.mod(x - rect.x, rect.width) + rect.x;
            y = Mathf.mod(y - rect.y, rect.height) + rect.y;

            if(Tmp.r3.set(x - life * 2f, y - life * 2f, life * 4f, life * 4f).overlaps(visible)){
                Tile tile = world.tileWorld(x, y);

                if(tile != null && tile.floor().isLiquid){
                    Draw.color(Tmp.c1.set(tile.floor().liquidColor == null ? color : tile.floor().liquidColor).mul(1.5f));
                    Draw.alpha(slope(life) * intensity);
                    Lines.circle(x, y, life * 4f);
                }else if(tile != null && tile.floor().liquidDrop == null && !tile.floor().solid){
                    Draw.color(color);
                    Draw.alpha(slope(life) * intensity);

                    float space = 45f;
                    for(int j : Mathf.signs){
                        float ang = 90f + j * space;
                        float len = 1f + 5f * life;
                        Lines.lineAngle(x + Angles.trnsx(ang, len), y + Angles.trnsy(ang, len), ang, 3f * (1f - life));
                    }
                }
            }
        }

        Draw.reset();
    }

    private static float slope(float fin){
        return 1f - Math.abs(fin - 0.5f) * 2f;
    }
}
