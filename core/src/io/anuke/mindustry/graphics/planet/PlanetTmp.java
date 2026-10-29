package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/** Reusable temporary math objects for the 3D planet renderer. */
public class PlanetTmp{
    public final Matrix4 ortho = new Matrix4();
    public final Vector3 light = new Vector3();
    public final Vector3 center = new Vector3();
    public final Vector3 near = new Vector3();
    public final Vector3 far = new Vector3();
    public final Vector3 hit = new Vector3();
    public final Vector3 project = new Vector3();
    public final Vector3 sun = new Vector3();
    public final Vector3 arcStart = new Vector3();
    public final Vector3 arcEnd = new Vector3();
    public final Vector3 arcControl = new Vector3();
    public final Vector3 arcPrev = new Vector3();
    public final Vector3 arcPoint = new Vector3();
    public final Ray ray = new Ray();
}
