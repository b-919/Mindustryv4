package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Lit campaign terrain. Emissive so lava surfaces glow through the planet shader. */
public class TerrainMesh extends PlanetSurfaceMesh{
    public TerrainMesh(Mesh mesh, ShaderProgram shader){
        super(mesh, shader, 1f);
    }
}
