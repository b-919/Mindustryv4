package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Loads and owns every shader used by the 3D planet scene. */
public class PlanetShaders implements Disposable{
    public ShaderProgram planet;
    public ShaderProgram overlay;
    public ShaderProgram atmosphere;
    public ShaderProgram cloud;
    public ShaderProgram sun;
    public ShaderProgram emissive;

    private final boolean mobile;

    public PlanetShaders(boolean mobile){
        this.mobile = mobile;
    }

    /** Compiles all programs, or returns false if any of them failed. Safe to call repeatedly. */
    public boolean build(){
        if(isReady()) return true;

        //retry cleanly: drop anything compiled by a previous partial attempt
        dispose();

        planet = load("planet3d", null);
        overlay = load("planetoverlay", null);
        atmosphere = load("planetatmosphere", mobile ? "#define SCATTER_OUT 2\n#define SCATTER_IN 2" : null);
        cloud = load("planetcloud", null);
        sun = load("planetsun", null);
        emissive = load("planetemissive", null);

        return isReady();
    }

    public boolean isReady(){
        return planet != null && overlay != null && atmosphere != null && cloud != null && sun != null && emissive != null;
    }

    /** Loads a shader pair from core/assets/shaders. Optional defines are prepended to the fragment shader. */
    private ShaderProgram load(String name, String defines){
        try{
            String vertex = Gdx.files.internal("shaders/" + name + ".vertex").readString();
            String fragment = Gdx.files.internal("shaders/" + name + ".fragment").readString();

            if(defines != null && defines.length() > 0){
                fragment = defines + "\n" + fragment;
            }

            ShaderProgram program = new ShaderProgram(vertex, fragment);
            if(!program.isCompiled()){
                Gdx.app.error("Planet3D", "Shader '" + name + "' failed: " + program.getLog());
                program.dispose();
                return null;
            }
            return program;
        }catch(Exception e){
            Gdx.app.error("Planet3D", "Could not load shader '" + name + "'", e);
            return null;
        }
    }

    @Override
    public void dispose(){
        if(planet != null) planet.dispose();
        if(overlay != null) overlay.dispose();
        if(atmosphere != null) atmosphere.dispose();
        if(cloud != null) cloud.dispose();
        if(sun != null) sun.dispose();
        if(emissive != null) emissive.dispose();

        planet = null;
        overlay = null;
        atmosphere = null;
        cloud = null;
        sun = null;
        emissive = null;
    }
}
