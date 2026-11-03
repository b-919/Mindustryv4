package io.anuke.mindustry.maps.filters;

import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.world.Block;

import static io.anuke.mindustry.maps.filters.FilterOption.*;

public class LakeNoiseFilter extends GenerateFilter{
    public float scl = 80, threshold = 0.6f, octaves = 4f, falloff = 0.5f, edgeScl = 20f;
    public Block floor = Blocks.water, floor2 = Blocks.deepwater, block = Blocks.air, target = Blocks.air;

    @Override
    public String name(){
        return "Lake";
    }

    @Override
    public FilterOption[] options(){
        return new FilterOption[]{
            new SliderOption("scale", () -> scl, f -> scl = f, 5f, 500f),
            new SliderOption("threshold", () -> threshold, f -> threshold = f, 0f, 1f),
            new SliderOption("octaves", () -> octaves, f -> octaves = f, 1f, 10f),
            new SliderOption("falloff", () -> falloff, f -> falloff = f, 0f, 1f),
            new SliderOption("edge-scale", () -> edgeScl, f -> edgeScl = f, 1f, 100f),
            new BlockOption("target", () -> target, b -> target = b, anyOptional),
            new BlockOption("floor", () -> floor, b -> floor = b, floorsOptional),
            new BlockOption("floor2", () -> floor2, b -> floor2 = b, floorsOptional)
        };
    }

    @Override
    public void apply(GenerateInput in){
        float n = noise(in.x, in.y, scl, 1f, octaves, falloff);
        float edge = noise(in.x, in.y, edgeScl, 0.3f, 2, 0.5f);

        if(n + edge > threshold && (target == Blocks.air || in.floor == target)){
            if(n + edge > threshold + 0.15f && floor2 != Blocks.air){
                in.floor = floor2;
            }else if(floor != Blocks.air){
                in.floor = floor;
            }
            if(in.block.solid){
                in.block = Blocks.air;
            }
        }
    }
}
