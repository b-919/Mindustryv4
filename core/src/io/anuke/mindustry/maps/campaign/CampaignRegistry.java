package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

public class CampaignRegistry{
    public static final String serpulo = "Serpulo";

    private static final ObjectMap<String, PlanetGenerator> registered = new ObjectMap<>();
    private static final Array<PlanetDefinition> planets = new Array<>();
    private static final ObjectMap<String, PlanetMap> planetMaps = new ObjectMap<>();

    static{
        register(serpulo, new SerpuloPlanetGenerator());
    }

    public static void register(String name, PlanetGenerator generator){
        registered.put(name, generator);
    }

    public static PlanetGenerator generator(String name){
        PlanetGenerator gen = registered.get(name);
        if(gen != null) return gen;
        return registered.get(serpulo);
    }

    public static Array<String> all(){
        Array<String> names = new Array<>();
        for(String key : registered.keys()){
            names.add(key);
        }
        return names;
    }

    public static Array<Definition> definitions(){
        Array<Definition> defs = new Array<>();
        for(ObjectMap.Entry<String, PlanetGenerator> entry : registered.entries()){
            defs.add(new Definition(entry.key, entry.value));
        }
        return defs;
    }

    public static boolean contains(String name){
        return registered.containsKey(name);
    }

    public static Array<PlanetDefinition> planets(){
        ensurePlanets();
        return planets;
    }

    public static PlanetDefinition planetForCampaign(String campaignName){
        ensurePlanets();
        for(PlanetDefinition def : planets){
            if(def.campaign != null && def.campaign.equals(campaignName)){
                return def;
            }
        }
        return planets.size == 0 ? null : planets.first();
    }

    /** Cached client-side region map for a campaign planet. */
    public static PlanetMap planetMap(String campaignName){
        PlanetMap map = planetMaps.get(campaignName);
        if(map == null){
            PlanetDefinition definition = planetForCampaign(campaignName);
            if(definition == null) return null;
            map = new PlanetMap(definition);
            planetMaps.put(campaignName, map);
        }
        return map;
    }

    public static void addPlanet(PlanetDefinition definition){
        if(definition.generator == null && definition.campaign != null){
            if(!registered.containsKey(definition.campaign)){
                io.anuke.ucore.util.Log.warn("No planet generator registered for campaign '{0}'; falling back to Serpulo.", definition.campaign);
            }
            definition.generator = generator(definition.campaign);
        }
        planets.add(definition);
    }

    private static void ensurePlanets(){
        if(planets.size == 0){
            io.anuke.mindustry.content.Planets.load();
        }
    }

    private CampaignRegistry(){}
}
