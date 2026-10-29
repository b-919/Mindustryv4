package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.math.Matrix4;

/** One drawable piece of a planet model. */
public interface GenericMesh{
    void render(PlanetParams params, Matrix4 transform);

    void dispose();
}
