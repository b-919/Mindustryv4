package io.anuke.mindustry.maps.filters;

import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.world.Block;

import static io.anuke.mindustry.maps.filters.FilterOption.*;

public class BiomeFilter extends GenerateFilter{
    public float tempScl = 1100, moistScl = 800, tempOctaves = 9f, moistOctaves = 6f;
    public Block floorWater = Blocks.deepwater, floorShallow = Blocks.water;
    public Block floorSand = Blocks.sand, floorGrass = Blocks.grass;
    public Block floorStone = Blocks.stone, floorSnow = Blocks.snow;
    public Block floorIce = Blocks.ice, floorLava = Blocks.lava;
    public Block floorBlackstone = Blocks.blackstone;

    @Override
    public String name(){
        return "Biome";
    }

    @Override
    public FilterOption[] options(){
        return new FilterOption[]{
            new SliderOption("temp-scale", () -> tempScl, f -> tempScl = f, 50f, 5000f),
            new SliderOption("moisture-scale", () -> moistScl, f -> moistScl = f, 50f, 5000f),
            new SliderOption("temp-octaves", () -> tempOctaves, f -> tempOctaves = f, 1f, 10f),
            new SliderOption("moisture-octaves", () -> moistOctaves, f -> moistOctaves = f, 1f, 10f),
            new BlockOption("floor-water", () -> floorWater, b -> floorWater = b, floorsOptional),
            new BlockOption("floor-shallow", () -> floorShallow, b -> floorShallow = b, floorsOptional),
            new BlockOption("floor-sand", () -> floorSand, b -> floorSand = b, floorsOptional),
            new BlockOption("floor-grass", () -> floorGrass, b -> floorGrass = b, floorsOptional),
            new BlockOption("floor-stone", () -> floorStone, b -> floorStone = b, floorsOptional),
            new BlockOption("floor-snow", () -> floorSnow, b -> floorSnow = b, floorsOptional),
            new BlockOption("floor-ice", () -> floorIce, b -> floorIce = b, floorsOptional),
            new BlockOption("floor-lava", () -> floorLava, b -> floorLava = b, floorsOptional),
            new BlockOption("floor-blackstone", () -> floorBlackstone, b -> floorBlackstone = b, floorsOptional)
        };
    }

    @Override
    public void apply(GenerateInput in){
        float temp = noise(in.x + 500, in.y, tempScl, 1f, tempOctaves, 0.5f) * 0.5f + 0.5f;
        float moisture = noise(in.x, in.y + 1000, moistScl, 1f, moistOctaves, 0.5f) * 0.5f + 0.5f;
        float elevation = noise(in.x + 2000, in.y + 2000, 400, 1f, 5, 0.62f);

        Block chosen;
        if(elevation < 0.35f){
            chosen = floorWater;
        }else if(elevation < 0.4f){
            chosen = floorShallow;
        }else if(elevation < 0.43f){
            chosen = floorSand;
        }else if(temp < 0.3f){
            chosen = floorSnow;
        }else if(temp < 0.45f){
            chosen = floorStone;
        }else if(temp < 0.65f){
            chosen = floorGrass;
        }else if(elevation > 0.8f){
            chosen = floorLava;
        }else{
            chosen = floorBlackstone;
        }

        if(elevation > 0.75f && temp < 0.5f){
            chosen = floorIce;
        }

        if(chosen != Blocks.air){
            in.floor = chosen;
        }
    }
}
