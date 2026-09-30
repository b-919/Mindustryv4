package io.anuke.mindustry.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Shader;
import io.anuke.ucore.scene.ui.layout.Unit;

import static io.anuke.mindustry.Vars.tilesize;
import static io.anuke.mindustry.Vars.world;

public class Shaders{
    public static Outline outline;
    public static BlockBuild blockbuild;
    public static BlockPreview blockpreview;
    public static Shield shield;
    public static Water water;
    public static SurfaceShader lava;
    public static SurfaceShader oil;
    public static Space space;
    public static UnitBuild build;
    public static MixShader mix;
    public static Shader fullMix;
    public static FogShader fog;
    public static MenuShader menu;
    public static LightShader light;

    public static void init(){
        outline = new Outline();
        blockbuild = new BlockBuild();
        blockpreview = new BlockPreview();
        shield = new Shield();
        water = new Water();
        lava = new SurfaceShader("lava");
        oil = new SurfaceShader("oil");
        space = new Space();
        build = new UnitBuild();
        mix = new MixShader();
        fog = new FogShader();
        fullMix = new Shader("fullmix", "default");
        menu = new MenuShader();
        light = new LightShader();
    }

    public static class LightShader extends Shader{
        public Color ambient = new Color();
        public Texture lightmap;
        public int type = 0;

        public LightShader(){
            super("light", "default");
        }

        @Override
        public void apply(){
            shader.setUniformi("u_type", type);

            if(type == 0){
                if(region != null){
                    shader.setUniformf("u_uv", region.getU(), region.getV());
                    shader.setUniformf("u_uv2", region.getU2(), region.getV2());
                    shader.setUniformf("u_texsize", region.getTexture().getWidth(), region.getTexture().getHeight());
                }
            }else{
                shader.setUniformi("u_lightmap", 1);
                lightmap.bind(1);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);

                shader.setUniformf("u_ambient", ambient.r, ambient.g, ambient.b, ambient.a);
            }
        }
    }

    public static class MenuShader extends Shader{
        float time = 0f;

        public MenuShader(){
            super("menu", "default");
        }

        @Override
        public void apply(){
            time = time % 158;

            shader.setUniformf("u_resolution", Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            shader.setUniformi("u_time", (int)(time += Gdx.graphics.getDeltaTime() * 60f));
            shader.setUniformf("u_uv", Draw.getBlankRegion().getU(), Draw.getBlankRegion().getV());
            shader.setUniformf("u_scl", Unit.dp.scl(1f));
            shader.setUniformf("u_uv2", Draw.getBlankRegion().getU2(), Draw.getBlankRegion().getV2());
        }
    }

    public static class FogShader extends Shader{
        public FogShader(){
            super("fog", "default");
        }
    }

    public static class MixShader extends Shader{
        public Color color = new Color(Color.WHITE);

        public MixShader(){
            super("mix", "default");
        }

        @Override
        public void apply(){
            super.apply();
            shader.setUniformf("u_color", color);
        }
    }

    public static class Space extends SurfaceShader{

        public Space(){
            super("space2");
        }

        @Override
        public void apply(){
            super.apply();
            shader.setUniformf("u_center", world.width() * tilesize / 2f, world.height() * tilesize / 2f);
        }
    }

    public static class UnitBuild extends Shader{
        public float progress, time;
        public Color color = new Color();
        public TextureRegion region;

        public UnitBuild(){
            super("build", "default");
        }

        @Override
        public void apply(){
            shader.setUniformf("u_time", time);
            shader.setUniformf("u_color", color);
            shader.setUniformf("u_progress", progress);
            shader.setUniformf("u_uv", region.getU(), region.getV());
            shader.setUniformf("u_uv2", region.getU2(), region.getV2());
            shader.setUniformf("u_texsize", region.getTexture().getWidth(), region.getTexture().getHeight());
        }
    }

    public static class Outline extends Shader{
        public Color color = new Color();

        public Outline(){
            super("outline", "default");
        }

        @Override
        public void apply(){
            shader.setUniformf("u_color", color);
            shader.setUniformf("u_texsize", region.getTexture().getWidth(), region.getTexture().getHeight());
        }
    }

    public static class BlockBuild extends Shader{
        public Color color = new Color();
        public float progress;

        public BlockBuild(){
            super("blockbuild", "default");
        }

        @Override
        public void apply(){
            shader.setUniformf("u_progress", progress);
            shader.setUniformf("u_color", color);
            shader.setUniformf("u_uv", region.getU(), region.getV());
            shader.setUniformf("u_uv2", region.getU2(), region.getV2());
            shader.setUniformf("u_time", Timers.time());
            shader.setUniformf("u_texsize", region.getTexture().getWidth(), region.getTexture().getHeight());
        }
    }

    public static class BlockPreview extends Shader{
        public Color color = new Color();

        public BlockPreview(){
            super("blockpreview", "default");
        }

        @Override
        public void apply(){
            shader.setUniformf("u_color", color);
            shader.setUniformf("u_uv", region.getU(), region.getV());
            shader.setUniformf("u_uv2", region.getU2(), region.getV2());
            shader.setUniformf("u_texsize", region.getTexture().getWidth(), region.getTexture().getHeight());
        }
    }

    public static class Shield extends Shader{
        public Color teamColor = new Color();

        public Shield(){
            super("shield", "default");
        }

        @Override
        public void apply(){
            shader.setUniformf("u_dp", Unit.dp.scl(1f));
            shader.setUniformf("u_time", Timers.time() / Unit.dp.scl(1f));
            shader.setUniformf("u_offset",
                    Core.camera.position.x - Core.camera.viewportWidth / 2,
                    Core.camera.position.y - Core.camera.viewportHeight / 2);
            shader.setUniformf("u_texsize", Core.camera.viewportWidth,
            Core.camera.viewportHeight);
            shader.setUniformf("u_teamColor", teamColor);
        }
    }

    /** Composites the water surface onto the scene, with the reflection buffer
     * (Renderer.reflectSurface) sampled at the same distorted coordinates, masked by its alpha
     * and washed toward the water color sampled from the scene. */
    public static class Water extends SurfaceShader{
        /** Reflection buffer texture; null disables reflections. */
        public Texture reflection;
        /** Reflection wash color and blend amount. */
        public Color refTint = new Color(0x29619bff);
        public float refTintAmount = 0.42f;
        /** Overall reflection strength multiplier on reflection alpha. */
        public float refOpacity = 0.9f;

        public Water(){
            super("water");
        }

        @Override
        public void apply(){
            super.apply();

            if(reflection != null){
                reflection.bind(1);
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
                shader.setUniformi("u_reflection", 1);
                shader.setUniformf("u_refTint", refTint.r, refTint.g, refTint.b, refTintAmount);
                shader.setUniformf("u_refOpacity", refOpacity);
            }
        }
    }

    public static class SurfaceShader extends Shader{

        public SurfaceShader(String frag){
            super(frag, "default");
        }

        @Override
        public void apply(){
            shader.setUniformf("camerapos",
                    Core.camera.position.x - Core.camera.viewportWidth / 2,
                    Core.camera.position.y - Core.camera.viewportHeight / 2);
            shader.setUniformf("screensize", Core.camera.viewportWidth,
            Core.camera.viewportHeight);
            shader.setUniformf("time", Timers.time());
        }
    }
}
