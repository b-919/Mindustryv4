package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/** Owns the cubemap space background, its mesh and shader. */
public class PlanetSkybox implements Disposable{
    private final Matrix4 view = new Matrix4();
    private final Matrix4 projection = new Matrix4();

    private Cubemap cubemap;
    private Mesh mesh;
    private ShaderProgram shader;

    public PlanetSkybox(){
        try{
            cubemap = new Cubemap(
                Gdx.files.internal("sprites/space/right.png"),
                Gdx.files.internal("sprites/space/left.png"),
                Gdx.files.internal("sprites/space/top.png"),
                Gdx.files.internal("sprites/space/bottom.png"),
                Gdx.files.internal("sprites/space/front.png"),
                Gdx.files.internal("sprites/space/back.png")
            );
            cubemap.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        }catch(Throwable t){
            Gdx.app.error("Planet3D", "Could not load space cubemap.", t);
            cubemap = null;
        }

        mesh = buildMesh();

        try{
            shader = new ShaderProgram(
                Gdx.files.internal("shaders/planetsky.vertex").readString(),
                Gdx.files.internal("shaders/planetsky.fragment").readString()
            );
            if(!shader.isCompiled()){
                Gdx.app.error("Planet3D", "Skybox shader failed: " + shader.getLog());
            }
        }catch(Throwable t){
            Gdx.app.error("Planet3D", "Could not load skybox shader.", t);
            shader = null;
        }
    }

    public boolean isReady(){
        return cubemap != null && mesh != null && shader != null && shader.isCompiled();
    }

    public void render(Camera cam){
        if(!isReady()) return;

        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        view.set(cam.view).setTranslation(0f, 0f, 0f);
        projection.set(cam.projection).mul(view);

        shader.begin();
        shader.setUniformMatrix("u_proj", projection);
        cubemap.bind();
        mesh.render(shader, GL20.GL_TRIANGLES);
        shader.end();

        Gdx.gl.glDepthMask(true);
    }

    private Mesh buildMesh(){
        //six quads, two triangles each
        float[] v = {
            1,-1,-1, 1,-1, 1, 1, 1, 1, 1,-1,-1, 1, 1, 1, 1, 1,-1,
            -1,-1, 1, -1,-1,-1, -1, 1,-1, -1,-1, 1, -1, 1,-1, -1, 1, 1,
            -1, 1, 1, 1, 1, 1, 1, 1,-1, -1, 1, 1, 1, 1,-1, -1, 1,-1,
            -1,-1,-1, 1,-1,-1, 1,-1, 1, -1,-1,-1, 1,-1, 1, -1,-1, 1,
            -1,-1, 1, 1,-1, 1, 1, 1, 1, -1,-1, 1, 1, 1, 1, -1, 1, 1,
            1,-1,-1, -1,-1,-1, -1, 1,-1, 1,-1,-1, -1, 1,-1, 1, 1,-1
        };

        Mesh result = new Mesh(true, 36, 0, new VertexAttribute(Usage.Position, 3, "a_position"));
        result.setVertices(v);
        return result;
    }

    @Override
    public void dispose(){
        if(cubemap != null) cubemap.dispose();
        if(mesh != null) mesh.dispose();
        if(shader != null) shader.dispose();
        cubemap = null;
        mesh = null;
        shader = null;
    }
}
