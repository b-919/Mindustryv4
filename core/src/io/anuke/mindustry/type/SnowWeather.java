package io.anuke.mindustry.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

/**
 * Snow weather, drawn as soft gaussian-blurred snowflakes that drift side to side.
 * Adapted from modern Mindustry's ParticleWeather class and Weather.drawParticles().
 */
public class SnowWeather extends Weather{
    public float blur = 6f;
    public float xspeed = 0.25f;
    public float yspeed = -2f;
    public float padding = 16f;
    public float density = 1200f;
    public float sizeMin = 2.4f;
    public float sizeMax = 12f;
    public float minAlpha = 1f;
    public float maxAlpha = 1f;
    public float sinSclMin = 30f;
    public float sinSclMax = 80f;
    public float sinMagMin = 1f;
    public float sinMagMax = 7f;
    public Color color = Color.WHITE;
    public boolean randomParticleRotation = false;

    private static final float boundMax = 10000 * 8f;
    private static final int regionSize = 64;

    private TextureRegion region;

    public SnowWeather(String name){
        super(name);
    }

    @Override
    public void drawOver(float alpha){
        if(region == null) region = makeRegion(blur);
        drawParticles(alpha, alpha, xspeed, yspeed);
    }

    public void drawParticles(float intensity, float opacity, float windx, float windy){
        rand.setSeed(0);
        float pad = sizeMax * 1.5f;
        float time = Timers.time();

        viewRect(pad);

        int total = (int)(rect.area() / density * intensity);
        Draw.color(color);
        Draw.alpha(opacity);

        for(int i = 0; i < total; i++){
            float scl = rand.nextFloat() * 0.5f + 0.5f;
            float scl2 = rand.nextFloat() * 0.5f + 0.5f;
            float size = rand.nextFloat() * (sizeMax - sizeMin) + sizeMin;
            float x = rand.nextFloat() * boundMax + time * windx * scl2;
            float y = rand.nextFloat() * boundMax + time * windy * scl;
            float alpha = rand.nextFloat() * (maxAlpha - minAlpha) + minAlpha;
            float rotation = randomParticleRotation ? rand.nextFloat() * 360f : 0f;

            x += Mathf.sin(y, rand.nextFloat() * (sinSclMax - sinSclMin) + sinSclMin,
                    rand.nextFloat() * (sinMagMax - sinMagMin) + sinMagMin);

            x = Mathf.mod(x - rect.x, rect.width) + rect.x;
            y = Mathf.mod(y - rect.y, rect.height) + rect.y;

            if(Tmp.r3.set(x - size / 2, y - size / 2, size, size).overlaps(visible)){
                Draw.alpha(alpha * opacity);
                Draw.rect(region, x, y, size, size, rotation);
            }
        }

        Draw.reset();
    }

    private static TextureRegion makeRegion(float sigma){
        float radius = regionSize / 2f - sigma * 2f;

        Pixmap source = new Pixmap(regionSize, regionSize, Format.RGBA8888);
        source.setBlending(Pixmap.Blending.None);
        for(int x = 0; x < regionSize; x++){
            for(int y = 0; y < regionSize; y++){
                float dx = x - regionSize / 2f + 0.5f;
                float dy = y - regionSize / 2f + 0.5f;
                source.drawPixel(x, y, dx * dx + dy * dy <= radius * radius ? Color.WHITE.toIntBits() : 0);
            }
        }

        Texture texture = new Texture(gaussianBlurAlpha(source, sigma));
        source.dispose();
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return new TextureRegion(texture);
    }

    private static Pixmap gaussianBlurAlpha(Pixmap in, float sigma){
        int w = in.getWidth(), h = in.getHeight();
        int radius = Math.max(1, (int) Math.ceil(sigma * 3f));
        float[] kernel = new float[radius * 2 + 1];
        float sum = 0f;
        for(int i = -radius; i <= radius; i++){
            float v = (float) Math.exp(-(i * i) / (2f * sigma * sigma));
            kernel[i + radius] = v;
            sum += v;
        }
        for(int i = 0; i < kernel.length; i++) kernel[i] /= sum;

        float[] src = new float[w * h];
        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                src[y * w + x] = (in.getPixel(x, y) & 0xff) / 255f;
            }
        }

        float[] tmp = new float[w * h];
        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                float acc = 0f;
                for(int k = -radius; k <= radius; k++){
                    int xx = Math.min(w - 1, Math.max(0, x + k));
                    acc += src[y * w + xx] * kernel[k + radius];
                }
                tmp[y * w + x] = acc;
            }
        }

        Pixmap out = new Pixmap(w, h, Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        for(int y = 0; y < h; y++){
            for(int x = 0; x < w; x++){
                float acc = 0f;
                for(int k = -radius; k <= radius; k++){
                    int yy = Math.min(h - 1, Math.max(0, y + k));
                    acc += tmp[yy * w + x] * kernel[k + radius];
                }
                int a = Math.round(acc * 255f);
                out.drawPixel(x, y, 0xffffff00 | a);
            }
        }
        return out;
    }
}
