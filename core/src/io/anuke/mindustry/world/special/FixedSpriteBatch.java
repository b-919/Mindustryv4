package io.anuke.mindustry.world.special;

import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Timers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.gl.GL20;
import com.badlogic.gdx.math.Interpolation;
import io.anuke.ucore.util.Mathf;

public class FixedSpriteBatch extends SpriteBatch{
    public static FixedSpriteBatch batch;
    static Batch lastBatch;

    public static void init(){
        batch = new FixedSpriteBatch();
    }

    public static void beginSwap(){
        lastBatch = Core.batch;
        Mat proj = Draw.proj(), trans = Draw.trans();
        //Draw.flush();
        batch.setFixedShader(FlameShaders.pinkShader);
        Core.batch = batch;
        Draw.proj(proj);
        Draw.trans(trans);
    }
    public static void endSwap(){
        Draw.flush();
        Core.batch = lastBatch;
    }

    public FixedSpriteBatch(){
        super(1024, null);
    }

    public void setFixedAlpha(float a){
        colorPacked = Color.toFloatBits(a, a, a, a);
    }
    public void setFixedShader(Shader shader){
        super.setShader(shader, true);
    }
    public void setFixedBlending(Blending blending){
        super.setBlending(blending);
    }

    protected void setMixColor(float r, float g, float b, float a){}
    protected void setMixColor(Color tint){}
    @Override
    protected void setPackedMixColor(float packedColor){}

    @Override
    protected void setBlending(Blending blending){}
    @Override
    protected void setShader(Shader shader, boolean apply){}
}
