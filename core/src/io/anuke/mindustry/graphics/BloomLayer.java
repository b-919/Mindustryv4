package io.anuke.mindustry.graphics;

/** A self-contained group of bloom emission sources. */
public interface BloomLayer{
    /** Unique name. Used as the handle for {@link BloomLayers#get(String)}, {@link BloomLayers#remove(String)}
     *  and {@link BloomLayers#setEnabled(String, boolean)}. */
    String name();

    /** Draws this layer's sources into the bound bloom emission surface. */
    void draw();

    /** Layers are drawn in ascending priority order. Overlap is additive, so this only matters for
     *  sources that rely on a specific order. */
    default int priority(){
        return 0;
    }
}
