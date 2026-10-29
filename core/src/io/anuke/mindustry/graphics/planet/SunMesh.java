package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.graphics.mesh.HexMesher;
import io.anuke.mindustry.graphics.mesh.MeshBuilder;
import io.anuke.mindustry.maps.campaign.PlanetVisual;
import io.anuke.ucore.noise.Simplex;
import io.anuke.ucore.util.Mathf;

/** The star: emissive surface plus additive glow shells, all drawn with the sun shader. */
public class SunMesh implements GenericMesh{
    private final Mesh mesh;
    private final Mesh[] glows;
    private final ShaderProgram shader;

    public SunMesh(Mesh mesh, Mesh[] glows, ShaderProgram shader){
        this.mesh = mesh;
        this.glows = glows;
        this.shader = shader;
    }

    public static SunMesh create(PlanetVisual sunVisual, Simplex noise, ShaderProgram shader){
        Mesh mesh = MeshBuilder.buildHex(new HexMesher(){
            @Override
            public void getColor(Vector3 position, Color out){
                double value = noise.octaveNoise3D(4, 0.55, 4.8, position.x, position.y, position.z);
                float t = Mathf.clamp((float)(value * 1.35f), 0f, 1f);
                out.set(sunVisual.sunLowColor).lerp(sunVisual.sunHighColor, t);
            }

            @Override
            public void getEmissiveColor(Vector3 position, Color out){
                out.set(sunVisual.sunHighColor);
            }
        }, Vars.mobile ? 2 : sunVisual.sunDivisions, sunVisual.sunRadius, 0f);

        Mesh[] glows = new Mesh[sunVisual.sunGlows.size];
        int glowDivisions = Vars.mobile ? 2 : sunVisual.sunDivisions;
        for(int i = 0; i < sunVisual.sunGlows.size; i++){
            SunGlowDef def = sunVisual.sunGlows.get(i);
            glows[i] = buildGlow(sunVisual.sunColor, def.radius, def.alpha, glowDivisions);
        }

        return new SunMesh(mesh, glows, shader);
    }

    private static Mesh buildGlow(Color base, float radius, float alpha, int divisions){
        Color color = new Color(base);
        color.a = alpha;

        return MeshBuilder.buildHex(new HexMesher(){
            @Override
            public void getColor(Vector3 position, Color out){
                out.set(color);
            }
        }, divisions, radius, 0f);
    }

    @Override
    public void render(PlanetParams params, Matrix4 transform){
        bind(params, transform);
        mesh.render(shader, GL20.GL_TRIANGLES);
        shader.end();
    }

    /** Renders the additive glow shells. The caller sets additive blending and disables depth writes. */
    public void renderGlows(PlanetParams params, Matrix4 transform){
        for(Mesh glow : glows){
            bind(params, transform);
            glow.render(shader, GL20.GL_TRIANGLES);
            shader.end();
        }
    }

    private void bind(PlanetParams params, Matrix4 transform){
        shader.begin();
        shader.setUniformMatrix("u_proj", params.projection);
        shader.setUniformMatrix("u_trans", transform);
    }

    @Override
    public void dispose(){
        mesh.dispose();
        for(Mesh glow : glows){
            glow.dispose();
        }
    }
}
