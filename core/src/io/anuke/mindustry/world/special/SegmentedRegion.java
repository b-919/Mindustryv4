package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

public class SegmentedRegion{
    TextureRegion region;
    int subDiv = 16;

    static float[] verts = new float[4 * 5];
    static Vector2 tmp = new Vector2();

    public SegmentedRegion(String name){
        region = Draw.region(name);
    }

    public void render(float x, float y, float width, float height, Consumer<Vector2> mover){
        TextureRegion r = region;
        float offx = width / 2f, offy = height / 2f;
        float col = Core.batch.getPackedColor();

        for(int i = 0; i < subDiv; i++){
            float f1 = i / (float)subDiv, f2 = (i + 1f) / subDiv;

            float x1 = x - offx, x2 = (width + x) - offx;
            float y1 = (height * f2 + y) - offy, y2 = (height * f1 + y) - offy;

            float u = r.getU();
            float u2 = r.getU2();
            float v = Mathf.lerp(r.getV2(), r.getV(), f1);
            float v2 = Mathf.lerp(r.getV2(), r.getV(), f2);

            mover.accept(tmp.set(x1, y2));
            set(0, tmp.x, tmp.y, col, u, v);

            mover.accept(tmp.set(x1, y1));
            set(1, tmp.x, tmp.y, col, u, v2);

            mover.accept(tmp.set(x2, y1));
            set(2, tmp.x, tmp.y, col, u2, v2);

            mover.accept(tmp.set(x2, y2));
            set(3, tmp.x, tmp.y, col, u2, v);

            Core.batch.draw(r.getTexture(), verts, 0, verts.length);
        }
    }

    private static void set(int i, float x, float y, float col, float u, float v){
        int o = i * 5;
        verts[o] = x;
        verts[o + 1] = y;
        verts[o + 2] = col;
        verts[o + 3] = u;
        verts[o + 4] = v;
    }
}
