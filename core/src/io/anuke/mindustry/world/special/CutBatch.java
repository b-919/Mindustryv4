package io.anuke.mindustry.world.special;

import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Timers;
import com.badlogic.gdx.audio.Sound;
import io.anuke.ucore.function.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.gl.GL20;
import com.badlogic.gdx.math.Interpolation;
import io.anuke.ucore.util.Mathf;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.SeedRandom;
import io.anuke.mindustry.entities.*;

public class CutBatch extends Batch{
    public Effect explosionEffect;
    public Cons<Severation> cutHandler;
    public Sound sound;
    static Array<Severation> returnEntities = new Array<>();

    public Array<Severation> switchBatch(Runnable run){
        Batch last = Core.batch;
        GL20 lgl = Core.gl;
        Core.batch = this;
        Core.gl = FragmentationBatch.mock;
        Lines.useLegacyLine = true;
        returnEntities.clear();

        run.run();

        Lines.useLegacyLine = false;
        Core.batch = last;
        Core.gl = lgl;
        sound = null;

        return returnEntities;
    }

    protected void setMixColor(Color tint){

    }
    protected void setMixColor(float r, float g, float b, float a){

    }
    @Override
    protected void setPackedMixColor(float packedColor){

    }

    @Override
    protected void draw(Texture texture, float[] spriteVertices, int offset, int count){}

    @Override
    protected void draw(TextureRegion region, float x, float y, float originX, float originY, float width, float height, float rotation){
        float midX = (width / 2f);
        float midY = (height / 2f);

        float cos = MathUtils.cosDeg(rotation);
        float sin = MathUtils.sinDeg(rotation);
        float dx = midX - originX;
        float dy = midY - originY;

        float bx = (cos * dx - sin * dy) + (x + originX);
        float by = (sin * dx + cos * dy) + (y + originY);

        if(Color.alpha(colorPacked) <= 0.9f || region == FragmentationBatch.updateCircle() || blending != Blending.normal || region == Core.atlas.white() || !region.found()){
            RejectedRegion r = new RejectedRegion();
            r.region = region;
            r.blend = blending;
            r.z = z;
            r.width = width;
            r.height = height;

            FlameFX.rejectedRegion.at(bx, by, rotation, Tmp.c1.set(Float.floatToRawIntBits(colorPacked)), r);
            return;
        }
        Severation c = Severation.generate(region, bx, by, width, height, rotation);
        c.color = colorPacked;
        c.z = z;
        if(sound != null) c.explosionSound = sound;
        if(explosionEffect != null){
            c.explosionEffect = explosionEffect;
        }
        if(cutHandler != null){
            cutHandler.get(c);
        }
        returnEntities.add(c);
    }

    @Override
    protected void flush(){

    }

    @Override
    protected void setShader(Shader shader, boolean apply){}

    public static class RejectedRegion{
        public TextureRegion region;
        public Blending blend = Blending.normal;
        public float width, height, z;
    }
}
