package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** The moon, testing only */
public class MoonMesh extends PlanetSurfaceMesh{
    public MoonMesh(Mesh mesh, ShaderProgram shader){
        super(mesh, shader, 0f);
    }
}
