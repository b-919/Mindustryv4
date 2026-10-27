package io.anuke.mindustry.graphics;

import com.badlogic.gdx.graphics.Color;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Effects;

/** World-space drawing helpers */
public final class Drawf{
    private Drawf(){
    }

    /** Queues a radial light at a world position. */
    public static void light(float x, float y, float radius, Color color, float opacity){
        Effects.light(x, y, radius, color, opacity);
    }

    /** Queues a white light. */
    public static void lightWhite(float x, float y, float radius, float opacity){
        light(x, y, radius, Color.WHITE, opacity);
    }

    /** Queues a light using the current Draw color (the batch color). */
    public static void light(float x, float y, float radius, float opacity){
        light(x, y, radius, Core.batch.getColor(), opacity);
    }

    /** Enables or disables light collection. Used while re-rendering effects into the bloom buffer. */
    public static void setCollecting(boolean value){
        Effects.setLightCollection(value);
    }
}
