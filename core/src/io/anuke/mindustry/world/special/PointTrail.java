package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.FloatArray;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.util.Mathf;

public class PointTrail{
    FloatArray points = new FloatArray();
    int length = 20;
    static float[] verts = new float[4 * 5];

    public void update(float x, float y, float x2, float y2){
        if(points.size >= length * 4) points.removeRange(0, 3);
        points.add(x, y, x2, y2);
    }

    public void clear(){
        points.clear();
    }

    public void draw(){
        TextureRegion r = EmpathyRegions.swordTrail;
        int s = (points.size / 4) - 1;
        float[] items = points.items;

        float col = Core.batch.getPackedColor();
        float v1 = r.getV2(), v2 = r.getV();

        for(int i = 0; i < s; i++){
            float f1 = i / (float)s;
            float f2 = (i + 1f) / s;

            int i1 = i * 4;
            int i2 = (i + 1) * 4;

            float u1 = Mathf.lerp(r.getU(), r.getU2(), f1), u2 = Mathf.lerp(r.getU(), r.getU2(), f2);

            float x1 = items[i1], y1 = items[i1 + 1];
            float x2 = items[i1 + 2], y2 = items[i1 + 3];
            float mx1 = (x1 + x2) / 2f, my1 = (y1 + y2) / 2f;
            x1 = (x1 - mx1) * f1 + mx1;
            x2 = (x2 - mx1) * f1 + mx1;
            y1 = (y1 - my1) * f1 + my1;
            y2 = (y2 - my1) * f1 + my1;

            float x21 = items[i2], y21 = items[i2 + 1];
            float x22 = items[i2 + 2], y22 = items[i2 + 3];
            float mx2 = (x21 + x22) / 2f, my2 = (y21 + y22) / 2f;
            x21 = (x21 - mx2) * f2 + mx2;
            x22 = (x22 - mx2) * f2 + mx2;
            y21 = (y21 - my2) * f2 + my2;
            y22 = (y22 - my2) * f2 + my2;

            set(0, x1, y1, col, u1, v1);
            set(1, x2, y2, col, u1, v2);
            set(2, x22, y22, col, u2, v2);
            set(3, x21, y21, col, u2, v1);

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
