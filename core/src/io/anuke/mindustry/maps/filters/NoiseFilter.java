package io.anuke.mindustry.maps.filters;

import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.world.Block;

import static io.anuke.mindustry.maps.filters.FilterOption.*;

public class NoiseFilter extends GenerateFilter{
    public float scl = 40, threshold = 0.5f, octaves = 3f, falloff = 0.5f, tilt = 0f;
    public Block floor = Blocks.stone, block = Blocks.air, target = Blocks.air;

    @Override
    public String name(){
        return "Noise";
    }

    @Override
    public FilterOption[] options(){
        return new FilterOption[]{
            new SliderOption("scale", () -> scl, f -> scl = f, 1f, 500f),
            new SliderOption("threshold", () -> threshold, f -> threshold = f, 0f, 1f),
            new SliderOption("octaves", () -> octaves, f -> octaves = f, 1f, 10f),
            new SliderOption("falloff", () -> falloff, f -> falloff = f, 0f, 1f),
            new SliderOption("tilt", () -> tilt, f -> tilt = f, -4f, 4f),
            new BlockOption("target", () -> target, b -> target = b, anyOptional),
            new BlockOption("floor", () -> floor, b -> floor = b, floorsOptional),
            new BlockOption("wall", () -> block, b -> block = b, wallsOptional)
        };
    }

    @Override
    public void apply(GenerateInput in){
        float n = noise(in.x, in.y + in.x * tilt, scl, 1f, octaves, falloff);
        if(n > threshold && (target == Blocks.air || in.floor == target || in.block == target)){
            if(floor != Blocks.air) in.floor = floor;
            if(block != Blocks.air && in.block != Blocks.air && !in.block.synthetic()) in.block = block;
        }
    }
}
