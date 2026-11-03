package io.anuke.mindustry.world.blocks.storage;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.util.Mathf;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class HiveBlock extends CoreBlock {

    public static final int MAX_EVOLUTION = 3;
    private static final int[] EVO_BIOMASS_COST = {150, 250, 350};
    private static final float[] EVO_TIME_MINUTES = {17f, 15f, 12f};
    private static final int[] BIOMASS_AMOUNT_MIN = {1, 2, 3, 5};
    private static final int[] BIOMASS_AMOUNT_MAX = {3, 5, 7, 10};
    private static final float[] BIOMASS_INTERVAL_MIN = {1f, 0.75f, 0.5f, 0.25f};
    private static final float[] BIOMASS_INTERVAL_MAX = {4f, 3.5f, 3f, 2f};

    public HiveBlock(String name) {
        super(name);
    }

    @Override
    public void update(Tile tile) {
        super.update(tile);
        setAmbientSound("none", 0.09f);
        setBuildPlayerSound("none");

        if(Net.client()) return;

        HiveEntity entity = tile.entity();
        if(entity == null) return;
        entity.biomassTimer += Timers.delta();
        if(entity.biomassTimer >= entity.biomassGoal){
            entity.biomassTimer = 0;
            int evo = Mathf.clamp(entity.evolution, 0, MAX_EVOLUTION);
            float minInterval = BIOMASS_INTERVAL_MIN[evo] * 60f * 60f;
            float maxInterval = BIOMASS_INTERVAL_MAX[evo] * 60f * 60f;
            entity.biomassGoal = Mathf.random(minInterval, maxInterval);
            entity.items.add(Items.corruptedbiomatter, Mathf.random(BIOMASS_AMOUNT_MIN[evo], BIOMASS_AMOUNT_MAX[evo]));
        }
        if(entity.evolution < MAX_EVOLUTION){//evolution
            entity.evolutionTimer += Timers.delta();
            float evoTime = getEvolutionTime(entity.evolution) * 60f * 60f;
            if(entity.evolutionTimer >= evoTime){
                evolve(tile, entity, false);
                return;
            }

            //biomass-based evolution (can evolve early by spending resources)
            int cost = getEvolutionBiomassCost(entity.evolution);
            if(entity.items.has(Items.corruptedbiomatter, cost)){
                entity.items.remove(Items.corruptedbiomatter, cost);
                evolve(tile, entity, true);
            }
        }
    }

    private void evolve(Tile tile, HiveEntity entity, boolean spentBiomass){
        entity.evolution++;
        entity.evolutionTimer = 0;

        //reset biomass timer with new faster interval
        entity.biomassTimer = 0;
        int evo = Mathf.clamp(entity.evolution, 0, MAX_EVOLUTION);
        float minInterval = BIOMASS_INTERVAL_MIN[evo] * 60f * 60f;
        float maxInterval = BIOMASS_INTERVAL_MAX[evo] * 60f * 60f;
        entity.biomassGoal = Mathf.random(minInterval, maxInterval);
    }

    public static int getEvolutionBiomassCost(int currentEvolution){
        if(currentEvolution < 0 || currentEvolution >= MAX_EVOLUTION) return Integer.MAX_VALUE;
        int base = EVO_BIOMASS_COST[currentEvolution];
        int extra = getDifficultyBiomassExtra();
        return Math.max(0, base + extra);
    }

    public static float getEvolutionTime(int currentEvolution){
        if(currentEvolution < 0 || currentEvolution >= MAX_EVOLUTION) return Float.MAX_VALUE;
        float base = EVO_TIME_MINUTES[currentEvolution];
        float extra = getDifficultyTimeExtra();
        return Math.max(0.5f, base + extra);
    }

    private static int getDifficultyBiomassExtra(){
        Difficulty diff = Vars.state.difficulty;
        if(diff == null) return 0;
        switch(diff){
            case training: return 100;
            case easy: return 50;
            case hard: return -50;
            case insane: return -75;
            case eradication: return -100;
            default: return 0;
        }
    }

    private static float getDifficultyTimeExtra(){
        Difficulty diff = Vars.state.difficulty;
        if(diff == null) return 0f;
        switch(diff){
            case training: return 5f;
            case easy: return 2f;
            case normal: return 0f;
            case hard: return -3f;
            case insane: return -5f;
            case eradication: return -7f;
            default: return 0f;
        }
    }

    /**Returns the highest evolution level of any nearby HiveBlock on the same team within radius (in tiles).*/
    public static int getMaxEvolutionNearby(Tile tile, float radius){
        if(tile == null || Vars.state.teams == null) return 0;
        ObjectSet<Tile> cores = Vars.state.teams.get(tile.getTeam()).cores;
        if(cores == null) return 0;

        int maxEvo = 0;
        float radiusSqr = radius * radius;
        for(Tile core : cores){
            if(core == tile) continue;
            if(!(core.block() instanceof HiveBlock)) continue;
            float dx = core.x - tile.x;
            float dy = core.y - tile.y;
            if(dx * dx + dy * dy <= radiusSqr){
                HiveEntity e = core.entity();
                if(e != null && e.evolution > maxEvo){
                    maxEvo = e.evolution;
                }
            }
        }
        return maxEvo;
    }

    /**Returns a speed multiplier based on evolution level. 1.0 at evo 0, scaling up to maxMult at evo 3.*/
    public static float getEvolutionSpeedMultiplier(int evolution){
        return 1f + (float) Mathf.clamp(evolution, 0, MAX_EVOLUTION) / MAX_EVOLUTION;
    }

    @Override
    public void onProximityAdded(Tile tile) {
        super.onProximityAdded(tile);

        StorageEntity entity = tile.entity();
        Team team = tile.getTeam();
        ObjectSet<Tile> cores = Vars.state.teams.get(team).cores;

        if(cores != null){
            for(Tile core : cores){
                if(core == tile || !(core.block() instanceof HiveBlock)) continue;
                StorageEntity other = core.entity();
                if(other != null && other.graph != null && other.graph != entity.graph){
                    entity.graph.merge(other.graph);
                }
            }
        }
    }

    @Override
    public void onProximityRemoved(Tile tile) {
        StorageEntity entity = tile.entity();
        StorageGraph graph = entity.graph;

        if(graph != null){
            graph.removeWithoutSplit(tile);
        }
    }

    @Override
    public void draw(Tile tile) {
        HiveEntity entity = tile.entity();

        float pulse = 1f + Mathf.absin(Timers.time(), 4f, 0.05f);

        Draw.rect(entity.solid ? Draw.region(name) : openRegion, tile.drawx(), tile.drawy(), pulse * size * 8f, pulse * size * 8f);

        Draw.alpha(entity.heat);
        Draw.rect(topRegion, tile.drawx(), tile.drawy(), pulse * size * 8f, pulse * size * 8f);
        Draw.color();

        if(entity.currentUnit != null){
            float time = entity.time;
            float progress = entity.progress;
            Unit player = entity.currentUnit;
            TextureRegion region = player.getIconRegion();

            Shaders.build.region = region;
            Shaders.build.progress = progress;
            Shaders.build.color.set(Color.valueOf("d30000"));
            Shaders.build.time = -time / 10f;

            Graphics.shader(Shaders.build, false);
            Shaders.build.apply();
            Draw.rect(region, tile.drawx(), tile.drawy());
            Graphics.shader();

            Draw.color(Color.valueOf("d30000"));

            Lines.lineAngleCenter(
                    tile.drawx() + Mathf.sin(time, 6f, Vars.tilesize / 3f * size),
                    tile.drawy(),
                    90,
                    size * Vars.tilesize / 2f);

            Draw.reset();
        }

        //draw evolution indicator
        if(entity.evolution > 0){
            Draw.color(Color.valueOf("d30000"));
            float dotSize = 1.5f;
            for(int i = 0; i < entity.evolution; i++){
                float angle = 90f + (i - (entity.evolution - 1) / 2f) * 30f;
                float dist = size * Vars.tilesize / 2f + 4f;
                float cx = tile.drawx() + MathUtils.cosDeg(angle) * dist;
                float cy = tile.drawy() + MathUtils.sinDeg(angle) * dist;
                Fill.square(cx, cy, dotSize, 45f);
            }
            Draw.reset();
        }
    }

    @Override
    public TileEntity newEntity() {
        return new HiveEntity();
    }

    public class HiveEntity extends CoreEntity {
        public int evolution;
        public float biomassTimer;
        public float biomassGoal = 60f * 60f * 3.5f;
        public float evolutionTimer;

        @Override
        public void write(DataOutput stream) throws IOException {
            super.write(stream);
            stream.writeInt(evolution);
            stream.writeFloat(biomassTimer);
            stream.writeFloat(biomassGoal);
            stream.writeFloat(evolutionTimer);
        }

        @Override
        public void read(DataInput stream) throws IOException {
            super.read(stream);
            evolution = stream.readInt();
            biomassTimer = stream.readFloat();
            biomassGoal = stream.readFloat();
            evolutionTimer = stream.readFloat();
        }
    }
}
