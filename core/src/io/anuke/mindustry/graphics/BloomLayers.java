package io.anuke.mindustry.graphics;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.graphics.bloom.*;

/** Central registry of {@link BloomLayer}s. */
public final class BloomLayers{
    public static final String
        TILES = "tiles",
        PUDDLES = "puddles",
        EFFECTS = "effects",
        BULLETS = "bullets",
        UNITS = "units",
        PLAYERS = "players",
        SHIELDS = "shields";

    private static final Array<BloomLayer> layers = new Array<>();
    private static final ObjectSet<String> disabled = new ObjectSet<>();

    /** Set when a layer is added/removed so the array is only re-sorted when necessary. */
    private static boolean needsSort = true;

    static{
        registerDefaults();
    }

    private BloomLayers(){
    }

    /** Registers the layers that ship with the game. */
    private static void registerDefaults(){
        add(new TileBloomLayer());
        add(new PuddleBloomLayer());
        add(new EffectBloomLayer());
        add(new BulletBloomLayer());
        add(new UnitBloomLayer());
        add(new PlayerBloomLayer());
        add(new ShieldBloomLayer());
    }

    /** Registers a layer, replacing any existing layer with the same name. */
    public static BloomLayer add(BloomLayer layer){
        remove(layer.name());
        disabled.remove(layer.name());
        layers.add(layer);
        needsSort = true;
        return layer;
    }

    /** Convenience registration for one-off sources. The name doubles as the handle for enable/disable/remove. */
    public static BloomLayer add(String name, int priority, Runnable drawer){
        return add(new SimpleBloomLayer(name, priority, drawer));
    }

    /** Removes the layer with the given name. Returns true if a layer was removed. */
    public static boolean remove(String name){
        for(int i = 0; i < layers.size; i++){
            if(layers.get(i).name().equals(name)){
                layers.removeIndex(i);
                needsSort = true;
                return true;
            }
        }
        return false;
    }

    public static BloomLayer get(String name){
        for(int i = 0; i < layers.size; i++){
            if(layers.get(i).name().equals(name)){
                return layers.get(i);
            }
        }
        return null;
    }

    public static boolean isRegistered(String name){
        return get(name) != null;
    }

    /** Whether the layer exists and has not been disabled. */
    public static boolean isActive(String name){
        return isRegistered(name) && !disabled.contains(name);
    }

    public static boolean isEnabled(String name){
        return !disabled.contains(name);
    }

    /** Enables or excludes a layer at runtime, without changing any bloom draw calls. */
    public static void setEnabled(String name, boolean enabled){
        if(enabled){
            disabled.remove(name);
        }else{
            disabled.add(name);
        }
    }

    public static void enable(String name){
        setEnabled(name, true);
    }

    public static void disable(String name){
        setEnabled(name, false);
    }

    /** Draws every enabled layer in priority order. The caller must have the emission surface bound. */
    public static void draw(){
        if(needsSort){
            layers.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
            needsSort = false;
        }

        for(int i = 0; i < layers.size; i++){
            BloomLayer layer = layers.get(i);
            if(!disabled.contains(layer.name())){
                layer.draw();
            }
        }
    }

    /** Restores the built-in layers and clears all exclusions. */
    public static void reset(){
        layers.clear();
        disabled.clear();
        needsSort = true;
        registerDefaults();
    }

    /** Live view of the registered layers, in registration order. */
    public static Array<BloomLayer> all(){
        return layers;
    }

    private static class SimpleBloomLayer implements BloomLayer{
        private final String name;
        private final int priority;
        private final Runnable drawer;

        SimpleBloomLayer(String name, int priority, Runnable drawer){
            this.name = name;
            this.priority = priority;
            this.drawer = drawer;
        }

        @Override
        public String name(){
            return name;
        }

        @Override
        public int priority(){
            return priority;
        }

        @Override
        public void draw(){
            drawer.run();
        }
    }
}
