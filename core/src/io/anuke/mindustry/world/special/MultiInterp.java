package io.anuke.mindustry.world.special;

import com.badlogic.gdx.math.Interpolation;

/** Chains several interpolations together. */
public class MultiInterp implements Interpolation{
    public static Interpolation fastfastslow = new MultiInterp(Interpolation.pow2In, Interpolation.pow2);

    Interpolation[] interps;

    public MultiInterp(Interpolation... interps){
        this.interps = interps;
    }

    @Override
    public float apply(float v){
        for(Interpolation i : interps){
            v = i.apply(v);
        }

        return v;
    }
}
