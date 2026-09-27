package io.anuke.mindustry.maps.filters;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.OreBlocks;
import io.anuke.mindustry.game.TechTree;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.noise.Simplex;

import static io.anuke.mindustry.maps.filters.FilterOption.*;

public class DefaultOresFilter extends GenerateFilter{
    public String tech = TechTree.defaultTech;

    private Array<Item> oreItems = new Array<>();
    private int[] noiseIndexes = new int[0];
    private String cachedForTech = null;

    @Override
    public String name(){
        return "Default Ores";
    }

    @Override
    public FilterOption[] options(){
        return new FilterOption[]{
            new TechOption("ore-tech", () -> tech, t -> tech = t)
        };
    }

    @Override
    public void apply(GenerateInput in){
        if(!(in.floor instanceof Floor) || !((Floor)in.floor).hasOres) return;
        if(in.block != Blocks.air) return;

        updateOres();

        for(int i = oreItems.size - 1; i >= 0; i--){
            Item item = oreItems.get(i);
            int slot = noiseIndexes[i];
            Simplex noise = new Simplex(seed + slot);

            if(noise.octaveNoise2D(1, 0.7, 1f / (4 + slot * 2), in.x, in.y) / 4f +
                Math.abs(0.5f - noise.octaveNoise2D(2, 0.7, 1f / (50 + slot * 2), in.x, in.y)) > 0.48f &&
                Math.abs(0.5f - noise.octaveNoise2D(1, 1, 1f / (55 + slot * 4), in.x, in.y)) > 0.22f){

                Block oreBlock = OreBlocks.get(in.floor, item);
                if(oreBlock != null){
                    in.floor = oreBlock;
                    break;
                }
            }
        }
    }

    /**Rebuilds the ore list when the selected tech tree changed.*/
    private void updateOres(){
        String current = tech == null || tech.isEmpty() || !TechTree.contains(tech) ? TechTree.defaultTech : tech;
        if(current.equals(cachedForTech)) return;

        cachedForTech = current;

        Array<Item> items = new Array<>();
        IntArray indexes = new IntArray();

        String tree = current.equals(TechTree.defaultTech) ? null : current;
        Array<Item> all = Item.getAllOres();
        for(int i = 0; i < all.size; i++){
            Item item = all.get(i);
            if(!item.belongsToTech(tree)) continue;
            items.add(item);
            indexes.add(i);
        }

        oreItems = items;
        noiseIndexes = indexes.toArray();
    }
}
