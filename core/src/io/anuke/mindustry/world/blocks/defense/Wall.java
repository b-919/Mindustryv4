package io.anuke.mindustry.world.blocks.defense;

import io.anuke.mindustry.graphics.CacheLayer;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.meta.BlockGroup;

public class Wall extends Block{

    public Wall(String name){
        super(name);
        solid = true;
        destructible = true;
        group = BlockGroup.walls;
        cacheLayer = CacheLayer.walls;
    }

    @Override
    public boolean canReplace(Block other){
        return super.canReplace(other) && health > other.health;
    }

}
