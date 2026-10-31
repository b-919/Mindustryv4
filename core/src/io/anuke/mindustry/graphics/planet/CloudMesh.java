package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** One independently rotating cloud sheet. */
public class CloudMesh implements GenericMesh{
    private final Mesh mesh;
    private final float speed;
    private final ShaderProgram shader;
    private final Matrix4 transform = new Matrix4();
    private float angle;

    public CloudMesh(Mesh mesh, float speed, ShaderProgram shader){
        this.mesh = mesh;
        this.speed = speed;
        this.shader = shader;
    }

    @Override
    public void render(PlanetParams params, Matrix4 bodyTransform){
        angle += params.cloudDelta * speed * params.cloudSpeedMultiplier;
        angle %= 360f;
        if(angle < 0f) angle += 360f;

        if(bodyTransform == null){
            transform.idt();
        }else{
            transform.set(bodyTransform);
        }
        transform.rotate(Vector3.Y, angle);

        shader.begin();
        shader.setUniformMatrix("u_proj", params.projection);
        shader.setUniformMatrix("u_trans", transform);
        shader.setUniformf("u_lightdir", params.light.x, params.light.y, params.light.z);
        shader.setUniformf("u_ambientColor", PlanetSurfaceMesh.AMBIENT_R, PlanetSurfaceMesh.AMBIENT_G, PlanetSurfaceMesh.AMBIENT_B);
        shader.setUniformf("u_alpha", params.cloudAlpha);
        PlanetSurfaceMesh.setJitter(shader, params);
        mesh.render(shader, GL20.GL_TRIANGLES);
        shader.end();
    }

    @Override
    public void dispose(){
        mesh.dispose();
    }
}
