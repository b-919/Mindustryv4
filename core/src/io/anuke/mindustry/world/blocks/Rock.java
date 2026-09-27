package io.anuke.mindustry.world.blocks;

import io.anuke.mindustry.content.Items;

public class Rock extends Prop{
    public Rock infectedVariant;

    public Rock(String name){
        super(name);
        deconstructDrop(new DeconstructDrop(Items.stone, 4, 12));
    }
}
