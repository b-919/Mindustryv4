package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;

import static io.anuke.ucore.util.Mathf.clamp;

/** Orbit camera used by the campaign planet view. */
public class PlanetCamera{
    public final PerspectiveCamera cam = new PerspectiveCamera(58f, 1f, 1f);

    public void update(int width, int height, float lon, float lat, float zoom){
        float distance = clamp(3.65f * zoom, 1.6f, 9f);
        float cy = MathUtils.cos(lat), sy = MathUtils.sin(lat);
        float cx = MathUtils.cos(lon), sx = MathUtils.sin(lon);

        cam.position.set(distance * cy * cx, distance * sy, distance * cy * sx);
        cam.up.set(0f, 1f, 0f);
        cam.lookAt(0f, 0f, 0f);
        cam.near = 0.1f;
        cam.far = 100f;
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
    }
}
