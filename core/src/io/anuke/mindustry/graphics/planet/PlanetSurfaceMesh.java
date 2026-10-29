package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;

/**
 * Base for the lit, hex-built bodies: planet terrain.
 * The mesh itself is built by {@link PlanetModel}; this class owns the shader
 * binding and the uniform contract.
 */
public class PlanetSurfaceMesh implements GenericMesh{
    public static final float AMBIENT_R = 0.95f, AMBIENT_G = 0.98f, AMBIENT_B = 1.05f;

    protected final Mesh mesh;
    protected final ShaderProgram shader;
    protected final float emissive;

    public PlanetSurfaceMesh(Mesh mesh, ShaderProgram shader, float emissive){
        this.mesh = mesh;
        this.shader = shader;
        this.emissive = emissive;
    }

    @Override
    public void render(PlanetParams params, Matrix4 transform){
        shader.begin();
        shader.setUniformMatrix("u_proj", params.projection);
        shader.setUniformMatrix("u_trans", transform);
        shader.setUniformf("u_lightdir", params.light.x, params.light.y, params.light.z);
        shader.setUniformf("u_camdir", params.camDir.x, params.camDir.y, params.camDir.z);
        shader.setUniformf("u_campos", params.camPos.x, params.camPos.y, params.camPos.z);
        shader.setUniformf("u_ambientColor", AMBIENT_R, AMBIENT_G, AMBIENT_B);
        shader.setUniformf("u_emissive", emissive);
        setJitter(shader, params);
        mesh.render(shader, GL20.GL_TRIANGLES);
        shader.end();
    }

    /** Renders only the emissive attribute, for the selective bloom buffer. */
    public void renderEmissive(PlanetParams params, Matrix4 transform, ShaderProgram emissiveShader){
        emissiveShader.begin();
        emissiveShader.setUniformMatrix("u_proj", params.projection);
        emissiveShader.setUniformMatrix("u_trans", transform);
        setJitter(emissiveShader, params);
        mesh.render(emissiveShader, GL20.GL_TRIANGLES);
        emissiveShader.end();
    }

    protected static void setJitter(ShaderProgram shader, PlanetParams params){
        shader.setUniformf("u_jitter", params.jitter);
        shader.setUniformf("u_jitterResolution", params.jitterResolutionX, params.jitterResolutionY);
    }

    @Override
    public void dispose(){
        mesh.dispose();
    }
}
