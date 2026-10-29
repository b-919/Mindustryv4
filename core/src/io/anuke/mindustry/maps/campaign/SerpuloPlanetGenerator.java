package io.anuke.mindustry.maps.campaign;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.SectorPresets;
import io.anuke.mindustry.maps.SectorPresets.SectorPreset;
import io.anuke.mindustry.maps.generation.Generation;
import io.anuke.mindustry.maps.generation.WorldGenerator;
import io.anuke.mindustry.maps.generation.WorldGenerator.GenResult;
import io.anuke.mindustry.maps.missions.BattleMission;
import io.anuke.mindustry.maps.missions.BiomassInfectableMission;
import io.anuke.mindustry.maps.missions.BiomassInfectedBattleMission;
import io.anuke.mindustry.maps.missions.BiomassInfectedMission;
import io.anuke.mindustry.maps.missions.Mission;
import io.anuke.mindustry.maps.missions.Missions;
import io.anuke.mindustry.maps.missions.WaveMission;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.type.Recipe.RecipeVisibility;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.blocks.Rock;
import io.anuke.mindustry.world.blocks.defense.Wall;
import io.anuke.ucore.noise.Simplex;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.content;
import static io.anuke.mindustry.Vars.world;

public class SerpuloPlanetGenerator extends PlanetGenerator{
    private final SectorPresets presets = new SectorPresets();

    public SerpuloPlanetGenerator(){
        mapSectorSize = 448;
        planetSampleSize = 5000;
    }

    //terrain sampling state
    private final GenResult gen = new GenResult();
    private final GenResult gen2 = new GenResult();
    private final Color tmpColor2 = new Color();
    private final Simplex heightNoise = new Simplex(0x2f6b17);
    private final GenResult mountainResult = new GenResult();
    private final Vector3 mountainDir = new Vector3();
    private final Vector3 mountainTangent = new Vector3();
    private final Vector3 mountainBitangent = new Vector3();
    private float sampleMix = 1f;

    //per-biome elevation offsets, in planet radio
    public float iceElevation = 0.030f;
    public float snowElevation = 0.020f;
    public float stoneElevation = 0.007f;
    public float groundElevation = 0.004f;
    public float waterElevation = 0f;
    public float elevationNoiseScale = 32f;
    public float elevationNoiseAmount = 0.0015f;
    //elevation
    public float maxElevation = 0.50f;
    //mountain relief
    public float mountainReliefScale = 32f;
    public float mountainReliefMin = 0.35f;

    //lava
    public final Color lavaColor = new Color(1f, 0.56f, 0.08f, 1f);
    public final Color lavaEmissive = new Color(1f, 0.68f, 0.12f, 1f);
    //mountain values per tile
    public float snowMountainHeight = 0.039f;
    public float iceMountainHeight = 0.065f;
    //radii related
    public float mountainInnerRadius = 0.50f;
    public float mountainOuterRadius = 0.15f;

    @Override
    public WorldGenerator terrain(){
        return world.generator;
    }

    @Override
    public float getHeight(Vector3 direction){
        sample(direction);

        float height = heightOf(gen, direction);
        if(sampleMix < 0.999f){
            height = Mathf.lerp(height, heightOf(gen2, direction), 1f - sampleMix);
        }

        String name = gen.floor == null || gen.floor.name == null ? "" : gen.floor.name.toLowerCase();
        if(name.contains("ice") || name.contains("snow")){
            float coverage = mountainCoverage(direction);
            float reliefNoise = (float)heightNoise.octaveNoise3D(3, 0.5, mountainReliefScale,
                direction.x, direction.y, direction.z);
            float ridge = 1f - Math.abs(reliefNoise * 2f - 1f);
            float relief = mountainReliefMin + (1f - mountainReliefMin) * ridge;
            float peak = (name.contains("ice") ? iceMountainHeight : snowMountainHeight) * coverage * coverage * relief;
            height += peak;
        }

        return Mathf.clamp(height, 0f, maxElevation);
    }

    /** How strongly a snow/ice tile is surrounded by more snow/ice. center = 1, edge = 0. */
    private float mountainCoverage(Vector3 direction){
        mountainTangent.set(0f, 1f, 0f).crs(direction);
        if(mountainTangent.len2() < 0.000001f){
            mountainTangent.set(1f, 0f, 0f);
        }else{
            mountainTangent.nor();
        }
        mountainBitangent.set(direction).crs(mountainTangent).nor();

        float score = 0f, total = 0f;

        for(int ring = 0; ring < 2; ring++){
            float radius = ring == 0 ? mountainInnerRadius : mountainOuterRadius;
            float weight = ring == 0 ? 1f : 0.5f;
            float offset = ring == 0 ? 0f : MathUtils.PI / 8f;

            for(int i = 0; i < 8; i++){
                float angle = offset + MathUtils.PI2 * i / 8f;
                float cos = MathUtils.cos(angle) * radius;
                float sin = MathUtils.sin(angle) * radius;

                mountainDir.set(direction)
                    .add(mountainTangent.x * cos + mountainBitangent.x * sin,
                         mountainTangent.y * cos + mountainBitangent.y * sin,
                         mountainTangent.z * cos + mountainBitangent.z * sin).nor();

                if(isSnowOrIce(mountainFloor(mountainDir))){
                    score += weight;
                }
                total += weight;
            }
        }

        return total == 0f ? 0f : score / total;
    }

    private Block mountainFloor(Vector3 direction){
        float lon = MathUtils.atan2(direction.z, direction.x);
        float lat = Mathf.clamp((float)Math.asin(MathUtils.clamp(direction.y, -1f, 1f)), -1.45f, 1.45f);
        sampleAt(mountainResult, lon, lat);
        return mountainResult.floor;
    }

    private boolean isSnowOrIce(Block block){
        if(block == null || block.name == null) return false;
        String name = block.name.toLowerCase();
        return name.contains("ice") || name.contains("snow");
    }

    @Override
    public void getColor(Vector3 direction, Color out){
        PlanetMap.Region region = map == null ? null : map.regionAt(direction);
        boolean infected = region != null && region.sector != null && region.sector.isInfected();

        sample(direction);
        colorOf(gen, out, infected);

        if(sampleMix < 0.999f){
            colorOf(gen2, tmpColor2, infected);
            out.lerp(tmpColor2, 1f - sampleMix);
        }
    }

    @Override
    public void getEmissiveColor(Vector3 direction, Color out){
        sample(direction);
        String name = gen.floor == null || gen.floor.name == null ? "" : gen.floor.name.toLowerCase();
        boolean liquid = gen.floor instanceof Floor && ((Floor)gen.floor).isLiquid;

        if(name.contains("lava") || name.contains("magma") || name.contains("hotrock")){
            out.set(lavaEmissive);
        }else if(liquid){
            out.set(0.12f, 0.28f, 0.55f, 0.22f);
        }else if(name.contains("ice") || name.contains("snow")){
            out.set(0.35f, 0.62f, 1f, 0.06f);
        }else{
            out.set(0f, 0f, 0f, 0f);
        }
    }

    @Override
    public boolean isLiquid(Vector3 direction){
        sample(direction);
        return gen.floor instanceof Floor && ((Floor)gen.floor).isLiquid;
    }

    private float heightOf(GenResult result, Vector3 direction){
        Block floor = result.floor;
        String name = floor == null || floor.name == null ? "" : floor.name.toLowerCase();
        // liquids are flat
        if(floor instanceof Floor && ((Floor)floor).isLiquid){
            return waterElevation;
        }

        //biome elevation order: ice > snow > stone > sand/grass
        float height;
        if(name.contains("ice")){
            height = iceElevation;
        }else if(name.contains("snow")){
            height = snowElevation;
        }else if(name.contains("stone")){
            height = stoneElevation;
        }else{
            height = groundElevation;
        }

        float noise = (float)heightNoise.octaveNoise3D(2, 0.5, elevationNoiseScale,
            direction.x, direction.y, direction.z);
        height += (noise - 0.5f) * elevationNoiseAmount;

        return Mathf.clamp(height, 0f, maxElevation);
    }

    private void colorOf(GenResult result, Color out, boolean infected){
        Block floor = result.floor;
        Block wall = result.wall;

        if(infected){
            if(floor instanceof Floor && ((Floor)floor).infectedVariant != null){
                floor = ((Floor)floor).infectedVariant;
            }
            if(wall instanceof Rock && ((Rock)wall).infectedVariant != null){
                wall = ((Rock)wall).infectedVariant;
            }
        }

        out.set(floor.minimapColor);
        if(out.a < 0.01f){
            out.set(wall.minimapColor);
        }

        String name = floor.name == null ? "" : floor.name.toLowerCase();
        if(name.contains("lava") || name.contains("magma") || name.contains("hotrock")){
            out.set(lavaColor);
            out.a = 1f;
        }else if(out.a < 0.01f){
            out.set(0.5f, 0.5f, 0.5f, 1f);
        }else{
            out.a = 1f;
        }
    }

    private void sample(Vector3 direction){
        if(planet == null) return;

        float lon = MathUtils.atan2(direction.z, direction.x);
        float lat = Mathf.clamp((float)Math.asin(MathUtils.clamp(direction.y, -1f, 1f)), -1.45f, 1.45f);
        float seam = MathUtils.PI - Math.abs(lon);

        if(seam < 0.18f){
            sampleMix = Mathf.clamp(seam / 0.18f, 0f, 1f);
            sampleAt(gen, lon, lat);
            sampleAt(gen2, lon >= 0f ? lon - MathUtils.PI2 : lon + MathUtils.PI2, lat);
        }else{
            sampleMix = 1f;
            sampleAt(gen, lon, lat);
        }
    }

    private void sampleAt(GenResult result, float lon, float lat){
        int size = planetSampleSize;
        int sx = (int)Math.floor((lon + MathUtils.PI) / MathUtils.PI2 * planet.gridLongitude) - planet.gridLongitude / 2;
        int sy = (int)Math.floor((lat + MathUtils.PI / 2f) / MathUtils.PI * planet.gridLatitude) - planet.gridLatitude / 2;

        int lx = Mathf.clamp((int)(((lon + MathUtils.PI) / MathUtils.PI2) * (size - 1)), 0, size - 1);
        int ly = Mathf.clamp((int)(((lat + MathUtils.PI / 2f) / MathUtils.PI) * (size - 1)), 0, size - 1);

        terrain().generateTile(result, sx, sy, lx, ly, false, null, null, size);
    }

    @Override
    public Array<int[]> sectorPresets(){
        Array<int[]> out = new Array<>();
        for(SectorPresets.SectorPreset preset : presets.getPresets().values()){
            out.add(new int[]{preset.x, preset.y});
        }
        return out;
    }

    @Override
    public Array<Item> getOres(int x, int y, Array<Item> defaultOres){
        Array<Item> ores = presets.getOres(x, y);
        return ores == null ? defaultOres : ores;
    }

    @Override
    public void initSector(Sector sector){
        sector.missions.clear();
        sector.difficulty = (int)Mathf.dst(sector.x, sector.y);

        if(presets.get(sector.x, sector.y) != null){
            SectorPreset p = presets.get(sector.x, sector.y);
            sector.missions.addAll(p.missions);
            sector.x = (short)p.x;
            sector.y = (short)p.y;
        }else{
            generate(sector);
        }

        sector.spawns = new Array<>();
        for(Mission mission : sector.missions){
            sector.spawns.addAll(mission.getWaves(sector));
        }

        if(sector.difficulty > 12){
            sector.startingItems = Array.with(new ItemStack(Items.copper, 1900), new ItemStack(Items.scrap, 1000), new ItemStack(Items.lead, 500), new ItemStack(Items.densealloy, 470), new ItemStack(Items.silicon, 460), new ItemStack(Items.titanium, 230));
        }else if(sector.difficulty > 8){
            sector.startingItems = Array.with(new ItemStack(Items.copper, 1500), new ItemStack(Items.scrap, 900), new ItemStack(Items.lead, 400), new ItemStack(Items.densealloy, 340), new ItemStack(Items.silicon, 250));
        }else if(sector.difficulty > 5){
            sector.startingItems = Array.with(new ItemStack(Items.copper, 950), new ItemStack(Items.scrap, 800), new ItemStack(Items.lead, 300), new ItemStack(Items.densealloy, 190), new ItemStack(Items.silicon, 140));
        }else if(sector.difficulty > 3){
            sector.startingItems = Array.with(new ItemStack(Items.copper, 700), new ItemStack(Items.scrap, 700), new ItemStack(Items.lead, 200), new ItemStack(Items.densealloy, 130));
        }else if(sector.difficulty > 2){
            sector.startingItems = Array.with(new ItemStack(Items.copper, 400), new ItemStack(Items.scrap, 600), new ItemStack(Items.lead, 100));
        }else{
            sector.startingItems = Array.with();
        }
    }

    private void generate(Sector sector){
        float rand = Mathf.randomSeed(sector.getSeed() + 7);

        if(rand < 0.10){
            sector.missions.add(new BiomassInfectedMission());
        }else if(rand < 0.20){
            sector.missions.add(new BiomassInfectedBattleMission());
        }else if(rand < 0.35f){
            sector.missions.add(new BiomassInfectableMission(sector.difficulty * 5 + Mathf.randomSeed(sector.getSeed(), 1, 4) * 5));
        }else if(rand < 0.60f){
            addRecipeMission(sector, 3);
            sector.missions.add(new WaveMission(sector.difficulty * 5 + Mathf.randomSeed(sector.getSeed(), 1, 4) * 5));
        }else{
            sector.missions.add(new BattleMission());
        }

        addRecipeMission(sector, 11);

        Generation gen = new Generation(sector, null, sectorSize(), sectorSize(), null);
        Array<GridPoint2> points = new Array<>();
        for(Mission mission : sector.missions){
            points.addAll(mission.getSpawnPoints(gen));
        }

        GenResult result = new GenResult();
        for(GridPoint2 point : new Array.ArrayIterable<>(points)){
            terrain().generateTile(result, sector.x, sector.y, point.x, point.y, true, null, null);
            if(((Floor)result.floor).isLiquid || result.wall.solid){
                sector.missions.clear();
                sector.missions.add(new WaveMission(Math.max(1, sector.difficulty) * 5 + Mathf.randomSeed(sector.getSeed(), 1, 4) * 5));
                break;
            }
        }

        if(sector.missions.size == 0){
            sector.missions.add(new WaveMission(Math.max(1, sector.difficulty) * 5 + Mathf.randomSeed(sector.getSeed(), 1, 4) * 5));
        }
    }

    private void addRecipeMission(Sector sector, int offset){
        if(Mathf.randomSeed(sector.getSeed() + offset) < 0.5){
            Array<Recipe> recipes = new Array<>();
            for(Recipe r : content.recipes()){
                if(r.result instanceof Wall || (r.visibility != RecipeVisibility.all) || r.cost < 10f) continue;
                recipes.add(r);
            }
            float maxdiff = 8f;
            recipes.sort((r1, r2) -> Float.compare(r1.cost, r2.cost));
            int end = (int)(Mathf.clamp(sector.difficulty / maxdiff + 0.25f) * (recipes.size - 1));
            int start = (int)(Mathf.clamp(sector.difficulty / maxdiff) * (recipes.size / 2f));

            if(recipes.size > 0 && end > start){
                Recipe recipe = recipes.get(Mathf.randomSeed(sector.getSeed() + 10, start, end));
                sector.missions.addAll(Missions.blockRecipe(recipe.result));
            }
        }
    }
}
