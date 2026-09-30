package io.anuke.mindustry.graphics;

import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.baseCameraScale;
import static io.anuke.mindustry.Vars.renderer;
import static io.anuke.ucore.core.Core.camera;

/** Utility for rendering things at a fake "height" above the ground, making them shift relative to the camera.
 *  Ported from the MineDusty mod (which took it from Meep's 3d pseudo code for the tantros-test-java repo). */
public class DrawPseudo3D{
    public static float xHeight(float x, float height){
        if(height <= 0) return x;
        return x + xOffset(x, height);
    }

    public static float yHeight(float y, float height){
        if(height <= 0) return y;
        return y + yOffset(y, height);
    }

    public static float xOffset(float x, float height){
        return (x - camera.position.x) * hMul(height);
    }

    public static float yOffset(float y, float height){
        return (y - camera.position.y) * hMul(height);
    }

    public static float hScale(float height){
        return 1f + hMul(height);
    }

    public static float hMul(float height){
        return height * scale();
    }

    /** Multiplier from world units to the abstract height scale used by the parallax functions. */
    public static float worldScale = 0.001f;

    /** Converts a world-unit height into the abstract height scale used by the parallax functions. */
    public static float worldHeight(float worldHeight){
        return worldHeight * worldScale;
    }

    /** Draws a soft ground shadow at (x, y) for something at 'height' out of 'maxHeight' (both in world units),
     *  with half-size 'size'. The shadow fades out as the object rises. */
    public static void shadow(float x, float y, float height, float maxHeight, float size){
        shadow(x, y, height, maxHeight, size, 0.12f);
    }

    /** Draws a soft ground shadow at (x, y) for something at 'height' out of 'maxHeight' (both in world units),
     *  with half-size 'size' and the given shadow alpha. The shadow fades out as the object rises. */
    public static void shadow(float x, float y, float height, float maxHeight, float size, float alpha){
        float fade = 1f - Mathf.clamp(maxHeight > 0f ? height / maxHeight : 0f);
        if(fade <= 0.01f) return;
        float s = size * (0.5f + 0.5f * fade);
        Draw.color(0f, 0f, 0f, fade * alpha);
        Draw.rect("circle", x, y, s, s);
    }

    /** The current display scale, relative to the scale the game starts at. */
    public static float scale(){
        return Mathf.clamp(renderer.getDisplayScale() / baseCameraScale, 0.5f, 4f);
    }

    public static float layerOffset(float x, float y){
        float max = Math.max(camera.viewportWidth, camera.viewportHeight);
        float dx = x - camera.position.x, dy = y - camera.position.y;
        return -Mathf.sqrt(dx * dx + dy * dy) / max / 1000f;
    }

    public static float heightFade(float height){
        float scl = hScale(height);
        return 1f - Mathf.curve(scl, 1.5f, 7f);
    }
}
