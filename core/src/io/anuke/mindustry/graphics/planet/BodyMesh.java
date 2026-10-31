package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.graphics.mesh.HexMesher;
import io.anuke.mindustry.graphics.mesh.MeshBuilder;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.ucore.noise.Simplex;
import io.anuke.ucore.util.Mathf;

//testing
public class BodyMesh extends PlanetSurfaceMesh{
    public BodyMesh(Mesh mesh, ShaderProgram shader){
        super(mesh, shader, 0f);
    }

    public static BodyMesh create(PlanetDefinition body, int seed, ShaderProgram shader){
        Simplex noise = new Simplex(seed);
        float offset = body.name == null ? 0f : body.name.hashCode() % 1000;

        Mesh mesh = MeshBuilder.buildHex(new HexMesher(){
            @Override
            public void getColor(Vector3 position, Color out){
                double value = noise.octaveNoise3D(3, 0.55, 6f,
                    position.x + offset, position.y, position.z);
                float tint = (float)value * 0.16f;

                out.set(
                    Mathf.clamp(body.colorR + tint, 0f, 1f),
                    Mathf.clamp(body.colorG + tint, 0f, 1f),
                    Mathf.clamp(body.colorB + tint, 0f, 1f),
                    1f);
            }
        }, Vars.mobile ? 2 : 4, body.radius, 0.02f);

        return new BodyMesh(mesh, shader);
    }
}
