package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** Additive atmospheric shell around the planet. */
public class AtmosphereMesh implements GenericMesh{
    private static final Matrix4 IDENTITY = new Matrix4();

    private final Mesh mesh;
    private final ShaderProgram shader;
    private final float innerRadius, outerRadius;
    private final float alphaScale, intensity;
    private final Color color;
    private final Vector3 center = new Vector3();

    public AtmosphereMesh(Mesh mesh, ShaderProgram shader, float innerRadius, float outerRadius,
                          float alphaScale, float intensity, Color color){
        this.mesh = mesh;
        this.shader = shader;
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.alphaScale = alphaScale;
        this.intensity = intensity;
        this.color = new Color(color);
    }

    @Override
    public void render(PlanetParams params, Matrix4 transform){
        shader.begin();
        shader.setUniformMatrix("u_proj", params.projection);
        shader.setUniformMatrix("u_trans", transform == null ? IDENTITY : transform);
        if(transform != null){
            transform.getTranslation(center);
        }else{
            center.setZero();
        }
        shader.setUniformf("u_center", center.x, center.y, center.z);
        shader.setUniformf("u_light", params.light.x, params.light.y, params.light.z);
        shader.setUniformf("u_campos", params.camPos.x, params.camPos.y, params.camPos.z);
        shader.setUniformf("u_color", color.r, color.g, color.b);
        shader.setUniformf("u_inner", innerRadius);
        shader.setUniformf("u_outer", outerRadius);
        shader.setUniformf("u_intensity", intensity);
        shader.setUniformf("u_alpha", params.atmosphereAlpha * alphaScale * color.a);
        PlanetSurfaceMesh.setJitter(shader, params);
        mesh.render(shader, GL20.GL_TRIANGLES);
        shader.end();
    }

    @Override
    public void dispose(){
        mesh.dispose();
    }
}
