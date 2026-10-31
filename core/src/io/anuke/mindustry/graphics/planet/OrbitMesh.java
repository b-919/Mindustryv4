package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.Vars;

/** One orbit ring, centered on a body's parent and passing through the body itself. */
public class OrbitMesh implements GenericMesh{
    private static final Matrix4 IDENTITY = new Matrix4();

    private final Mesh mesh;
    private final ShaderProgram shader;

    public OrbitMesh(Mesh mesh, ShaderProgram shader){
        this.mesh = mesh;
        this.shader = shader;
    }

    /** Builds a ring for {@code target} around {@code center}. */
    public static OrbitMesh create(Vector3 center, Vector3 target, Color color, ShaderProgram shader){
        int segments = Vars.mobile ? 128 : 220;
        float[] vertices = new float[segments * 2 * 4];
        Vector3 a = new Vector3(), b = new Vector3();
        int p = 0;

        float radius = Math.max(0.0001f, target.dst(center));
        Vector3 toTarget = new Vector3(target).sub(center).nor();
        Vector3 tangent = new Vector3(0f, 1f, 0f).crs(toTarget);
        if(tangent.isZero()){
            tangent.set(1f, 0f, 0f);
        }else{
            tangent.nor();
        }

        for(int i = 0; i < segments; i++){
            orbitPoint(center, toTarget, tangent, radius, MathUtils.PI2 * i / segments, a);
            orbitPoint(center, toTarget, tangent, radius, MathUtils.PI2 * (i + 1) / segments, b);
            p = putWorldVertex(vertices, p, a, color);
            p = putWorldVertex(vertices, p, b, color);
        }

        Mesh mesh = new Mesh(true, segments * 2, 0,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_color"));
        mesh.setVertices(vertices, 0, p);

        return new OrbitMesh(mesh, shader);
    }

    /** Builds a ring around the origin from an explicit orbit basis. */
    public static OrbitMesh createLocal(float radius, Vector3 base, Vector3 tangent, Color color, ShaderProgram shader){
        int segments = Vars.mobile ? 128 : 220;
        float[] vertices = new float[segments * 2 * 4];
        Vector3 a = new Vector3(), b = new Vector3();
        int p = 0;

        for(int i = 0; i < segments; i++){
            localPoint(base, tangent, radius, MathUtils.PI2 * i / segments, a);
            localPoint(base, tangent, radius, MathUtils.PI2 * (i + 1) / segments, b);
            p = putWorldVertex(vertices, p, a, color);
            p = putWorldVertex(vertices, p, b, color);
        }

        Mesh mesh = new Mesh(true, segments * 2, 0,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_color"));
        mesh.setVertices(vertices, 0, p);

        return new OrbitMesh(mesh, shader);
    }

    private static void localPoint(Vector3 base, Vector3 tangent, float radius, float angle, Vector3 out){
        float cos = MathUtils.cos(angle) * radius;
        float sin = MathUtils.sin(angle) * radius;
        out.set(base.x * cos, base.y * cos, base.z * cos)
            .add(tangent.x * sin, tangent.y * sin, tangent.z * sin);
    }

    private static void orbitPoint(Vector3 center, Vector3 toTarget, Vector3 tangent, float radius, float angle, Vector3 out){
        float cos = MathUtils.cos(angle) * radius;
        float sin = MathUtils.sin(angle) * radius;
        out.set(center).add(toTarget.x * cos, toTarget.y * cos, toTarget.z * cos)
            .add(tangent.x * sin, tangent.y * sin, tangent.z * sin);
    }

    private static int putWorldVertex(float[] out, int p, Vector3 v, Color color){
        float bits = color.toFloatBits();
        out[p++] = v.x;
        out[p++] = v.y;
        out[p++] = v.z;
        out[p++] = bits;
        return p;
    }

    @Override
    public void render(PlanetParams params, Matrix4 transform){
        shader.begin();
        shader.setUniformMatrix("u_proj", params.projection);
        shader.setUniformMatrix("u_trans", transform == null ? IDENTITY : transform);
        shader.setUniformf("u_alpha", 1f);
        mesh.render(shader, GL20.GL_LINES);
        shader.end();
    }

    @Override
    public void dispose(){
        mesh.dispose();
    }
}
