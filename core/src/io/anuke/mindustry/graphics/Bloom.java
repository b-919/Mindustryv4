package io.anuke.mindustry.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.anuke.ucore.graphics.postprocessing.utils.FullscreenQuad;
// dear god i love backporting code
/**
 * Backport of the modern Mindustry/Arc bloom. Captures the scene, cuts the bright areas at a
 * low resolution, applies a separable gaussian blur and finally combines photographically
 * (original * (1 - bloom) + bloom). Replaces the old gdx-pp postprocessor chain.
 * Based on arc.graphics.g2d.Bloom by kalle_h and Anuke.
 */
public class Bloom{
    public int blurPasses = 1;

    private final FullscreenQuad quad = new FullscreenQuad();

    private ShaderProgram thresholdShader, gaussianShader, bloomShader;
    private FrameBuffer pingPong1, pingPong2;
    private boolean ps1;
    private float ps1Width, ps1Height;

    private boolean ready;

    public Bloom(int width, int height){
        resize(width, height);
    }

    /** Recreates the internal buffers and shaders at the given screen size. Safe to call between frames. */
    public void resize(int width, int height){
        disposeBuffers();
        disposeShaders();

        int w = Math.max(width / 4, 1), h = Math.max(height / 4, 1);

        pingPong1 = new FrameBuffer(Format.RGBA8888, w, h, false);
        pingPong2 = new FrameBuffer(Format.RGBA8888, w, h, false);

        thresholdShader = load("screenspace", "threshold");
        gaussianShader = load("blurspace", "gaussian");
        bloomShader = load("screenspace", "bloom");

        if(thresholdShader == null || gaussianShader == null || bloomShader == null){
            ready = false;
            return;
        }

        gaussianShader.begin();
        gaussianShader.setUniformf("size", w, h);
        gaussianShader.end();

        setThreshold(0.15f);
        setBloomIntensity(2.5f);
        setOriginalIntensity(1f);

        bloomShader.begin();
        bloomShader.setUniformi("u_texture1", 1);
        bloomShader.end();

        setPS1(false, 0f, 0f);

        ready = true;
    }

    public boolean isReady(){
        return ready;
    }

    private ShaderProgram load(String vert, String frag){
        ShaderProgram program = new ShaderProgram(Gdx.files.internal("shaders/" + vert + ".vertex"),
                Gdx.files.internal("shaders/" + frag + ".fragment"));
        if(!program.isCompiled()){
            Gdx.app.error("Bloom", "Failed to compile bloom shader '" + frag + "': " + program.getLog());
            program.dispose();
            return null;
        }
        return program;
    }

    public void setThreshold(float threshold){
        thresholdShader.begin();
        thresholdShader.setUniformf("treshold", threshold);
        thresholdShader.setUniformf("tresholdInvTx", 1f / (1f - threshold));
        thresholdShader.end();
    }

    public void setBloomIntensity(float intensity){
        bloomShader.begin();
        bloomShader.setUniformf("BloomIntensity", intensity);
        bloomShader.end();
    }

    public void setOriginalIntensity(float intensity){
        bloomShader.begin();
        bloomShader.setUniformf("OriginalIntensity", intensity);
        bloomShader.end();
    }

    /** Enables planet-only PS1 pixelation/quantization in the final bloom composite. */
    public void setPS1(boolean enabled, float width, float height){
        this.ps1 = enabled;
        this.ps1Width = width;
        this.ps1Height = height;

        if(bloomShader != null){
            bloomShader.begin();
            bloomShader.setUniformf("u_ps1", enabled ? 1f : 0f);
            bloomShader.setUniformf("u_ps1Resolution", width, height);
            bloomShader.end();
        }
    }

    /** Applies the bloom to the given scene texture and draws the result onto the current framebuffer target. */
    public void render(Texture scene){
        render(scene, scene);
    }

    /** Applies selective bloom: thresholds from {@code emission} (only bloom sources), blurs, then composites onto {@code scene}. */
    public void render(Texture scene, Texture emission){
        if(!ready) return;

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        // threshold the bright areas of the emission buffer into the smaller buffer
        pingPong1.begin();
        emission.bind(0);
        thresholdShader.begin();
        quad.render(thresholdShader);
        thresholdShader.end();
        pingPong1.end();

        // separable gaussian blur, horizontal then vertical
        for(int i = 0; i < blurPasses; i++){
            pingPong2.begin();
            gaussianShader.begin();
            gaussianShader.setUniformf("dir", 1f, 0f);
            pingPong1.getColorBufferTexture().bind(0);
            quad.render(gaussianShader);
            gaussianShader.end();
            pingPong2.end();

            pingPong1.begin();
            gaussianShader.begin();
            gaussianShader.setUniformf("dir", 0f, 1f);
            pingPong2.getColorBufferTexture().bind(0);
            quad.render(gaussianShader);
            gaussianShader.end();
            pingPong1.end();
        }

        // mix the original scene and the blurred threshold onto the current target (screen)
        scene.bind(0);
        pingPong1.getColorBufferTexture().bind(1);
        bloomShader.begin();
        quad.render(bloomShader);
        bloomShader.end();

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glDepthMask(true);
    }

    public void dispose(){
        disposeBuffers();
        disposeShaders();
        quad.dispose();
    }

    private void disposeBuffers(){
        if(pingPong1 != null) pingPong1.dispose();
        if(pingPong2 != null) pingPong2.dispose();
        pingPong1 = null;
        pingPong2 = null;
    }

    private void disposeShaders(){
        if(thresholdShader != null) thresholdShader.dispose();
        if(gaussianShader != null) gaussianShader.dispose();
        if(bloomShader != null) bloomShader.dispose();
        thresholdShader = null;
        gaussianShader = null;
        bloomShader = null;
        ready = false;
    }
}
