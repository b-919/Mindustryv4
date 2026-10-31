package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.utils.Array;

/**
 * A named group of planet definitions that are rendered together. Different systems may place
 * their bodies at the same coordinates; only the selected system is ever built and rendered.
 */
public class SolarSystemDefinition{
    public final String name;
    public final Array<PlanetDefinition> bodies = new Array<>();

    public SolarSystemDefinition(String name){
        this.name = name;
    }

    public void add(PlanetDefinition body){
        body.system = this;
        bodies.add(body);
    }

    /** The star of this system, or null if it has none. */
    public PlanetDefinition sun(){
        //indexed loops: these are called while another loop over bodies may be active
        for(int i = 0; i < bodies.size; i++){
            PlanetDefinition body = bodies.get(i);
            if(body.isSun) return body;
        }
        return null;
    }

    /**
     * The body placed at the origin of the space scene. Prefer a body with a real campaign map
     * so its terrain can be rendered; otherwise use the first non-sun body.
     */
    public PlanetDefinition primary(){
        for(int i = 0; i < bodies.size; i++){
            PlanetDefinition body = bodies.get(i);
            if(!body.isSun && body.generator != null) return body;
        }
        for(int i = 0; i < bodies.size; i++){
            PlanetDefinition body = bodies.get(i);
            if(!body.isSun) return body;
        }
        return sun();
    }
}
