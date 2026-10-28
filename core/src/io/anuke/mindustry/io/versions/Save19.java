package io.anuke.mindustry.io.versions;

/**
 * Save version with a unified GenericCrafter entity format: smelters and crafters share a single
 * entity that serializes progress, warmup, burnTime, heat, craftTime and time.
 * Older saves (18 and below) are still loadable via the legacy entity read formats.
 */
public class Save19 extends Save18{

    public Save19(){
        super(19);
    }

    public Save19(int version){
        super(version);
    }
}
