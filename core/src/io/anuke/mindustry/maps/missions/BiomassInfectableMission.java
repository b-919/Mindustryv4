package io.anuke.mindustry.maps.missions;

import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.SpawnGroup;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Waves;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.generation.Generation;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.state;
import static io.anuke.mindustry.Vars.world;

public class BiomassInfectableMission extends MissionWithStartingCore{
    private final int target;
    private float hiveSpawnTime;
    private float timer = 0;
    private boolean hiveSpawned = false;

    public BiomassInfectableMission(int target){
        super();
        this.target = target;
        this.hiveSpawnTime = Mathf.random(5f, 20f) * 60f * 60f;
    }

    public BiomassInfectableMission(int target, int xCorePos, int yCorePos){
        super(xCorePos, yCorePos);
        this.target = target;
        this.hiveSpawnTime = Mathf.random(5f, 20f) * 60f * 60f;
    }

    @Override
    public void reset(){
        timer = 0;
        hiveSpawned = false;
    }

    @Override
    public boolean isInfectable(){
        return true;
    }

    @Override
    public Array<SpawnGroup> getWaves(Sector sector){
        return Waves.getSpawns();
    }

    @Override
    public void generate(Generation gen){
        generateCoreAtFirstSpawnPoint(gen, Team.blue);
    }

    @Override
    public GameMode getMode(){
        return GameMode.waves;
    }

    @Override
    public String displayString(){
        return Bundles.format("text.mission.wave", state.wave, target, (int)(state.wavetime/60));
    }

    @Override
    public void update(){
        timer += Timers.delta();
        if(!hiveSpawned && timer >= hiveSpawnTime){
            MassAI.spawnInitialHive();
            hiveSpawned = true;
        }

        if(state.wave > target){
            state.mode = GameMode.noWaves;
        }
    }

    @Override
    public boolean isComplete(){
        return state.wave > target && state.enemies() == 0 && !Vars.state.teams.isActive(Team.themass);
    }
}
