package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.graphics.Color;

/** One independently rotating cloud sheet. */
public class CloudDef{
    public final int seed;
    public final float radius, speed, threshold;
    public final Color color;

    public CloudDef(int seed, float radius, float speed, float threshold, Color color){
        this.seed = seed;
        this.radius = radius;
        this.speed = speed;
        this.threshold = threshold;
        this.color = color;
    }
}
