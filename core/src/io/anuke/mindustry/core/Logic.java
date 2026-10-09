package io.anuke.mindustry.core;

import com.badlogic.gdx.utils.Array;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams;
import io.anuke.mindustry.game.UnlockableContent;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.io.SaveFileVersion;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.maps.missions.WaveExtraMission;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.Entities;
import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.entities.EntityQuery;
import io.anuke.ucore.modules.Module;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.*;

/**
 * Logic module.
 * Handles all logic for entities and waves.
 * Handles game state events.
 * Does not store any game state itself.
 * <p>
 * This class should <i>not</i> call any outside methods to change state of modules, but instead fire events.
 */
public class Logic extends Module{
    private int lastRecenterX = Integer.MIN_VALUE, lastRecenterY = Integer.MIN_VALUE;
    private int lastTreeX = Integer.MIN_VALUE, lastTreeY = Integer.MIN_VALUE, lastTreeW, lastTreeH;

    public Logic(){
        Events.on(TileChangeEvent.class, event -> {
            if(event.tile.getTeam() == defaultTeam){
                Recipe recipe = Recipe.getByResult(event.tile.block());
                if(recipe != null && recipe.belongsToTech(state.techTree)){
                    handleContent(recipe);
                }
            }
        });

        Events.on(ResetEvent.class, event -> WaveExtraMission.resetFunds());
        Events.on(WaveEvent.class, event -> {
            if(state.mode == GameMode.SiegeMode){
                WaveExtraMission.awardWaveFunds(state.wave, state.difficulty);
            }
        });
        Events.on(WorldLoadEvent.class, event -> {
            if(state.mode == GameMode.SiegeMode && (state.wave > 1)){
                WaveExtraMission.awardWaveFunds(state.wave, state.difficulty);
            }
        });
    }

    @Override
    public void init(){
        EntityQuery.init();
        EntityQuery.collisions().setCollider(tilesize, (x, y) -> {
            Tile tile = world.tile(x, y);
            return tile != null && tile.solid();
        });
    }

    /**Handles the event of content being used by either the player or some block.*/
    public void handleContent(UnlockableContent content){
        if(world.getSector() != null){
            world.getSector().currentMission().onContentUsed(content);
        }

        if(!headless){
            control.unlocks.unlockContent(content);
        }
    }

    public void play(){
        state.set(State.playing);
        state.wavetime = wavespace * state.difficulty.timeScaling * 2;

        for(Tile tile : state.teams.get(defaultTeam).cores){
            if(world.getSector() != null){
                Array<ItemStack> items = world.getSector().startingItems;
                for(ItemStack stack : items){
                    tile.entity.items.add(stack.item, stack.amount);
                }
            }
        }

        Events.fire(new PlayEvent());

        if (state.startWithBiomass) {
            MassAI.spawnInitialHive();
        } else if (state.allowMassInfection && (MassAI.getInfectionTime() <= 0)){
            MassAI.setInfectionTime(Mathf.random(5f, 40f) * 60f * 60f);
        }
    }

    public void reset(){
        if(world.getSector() != null){
            world.sectors.refreshSectorPreview(world.getSector());
        }

        if(world.isOpenWorld()){
            world.endOpenWorld();
        }

        //any entities created from now on use the newest serialization format
        SaveFileVersion.currentVersion = Integer.MAX_VALUE;

        state.wave = 1;
        state.wavetime = wavespace * state.difficulty.timeScaling;
        state.gameOver = false;
        state.teams = new Teams();

        Timers.clear();
        Entities.clear();
        infection.reset();
        TileEntity.sleepingEntities = 0;

        if(weather != null){
            weather.setRain(false);
        }

        Events.fire(new ResetEvent());
    }

    public void runWave(){
        world.spawner.spawnEnemies();
        state.wave++;
        state.wavetime = wavespace * state.difficulty.timeScaling;

        Events.fire(new WaveEvent());
    }

    private void checkGameOver(){
        if(!state.mode.isPvp && state.teams.get(defaultTeam).cores.size == 0 && !state.gameOver){
            state.gameOver = true;
            Events.fire(new GameOverEvent(state.enemyTeam));
        }else if(state.mode.isPvp){
            Team alive = null;

            for(Team team : Team.all){
                if(state.teams.get(team).cores.size > 0){
                    if(alive != null){
                        return;
                    }
                    alive = team;
                }
            }

            if(alive != null && !state.gameOver){
                state.gameOver = true;
                Events.fire(new GameOverEvent(alive));
            }
        }
    }

    private void updateSectors(){
        if(world.getSector() == null || state.gameOver) return;

        world.getSector().currentMission().update();

        //check unlocked sectors
        while(!world.getSector().complete && world.getSector().currentMission().isComplete()){
            Call.onMissionFinish(world.getSector().completedMissions);
        }

        //check if all assigned missions are complete
        if(!world.getSector().complete && world.getSector().completedMissions >= world.getSector().missions.size){
            Call.onSectorComplete();
        }
    }

    @Remote(called = Loc.both)
    public static void onGameOver(Team winner){
        threads.runGraphics(() -> ui.restart.show(winner));
        netClient.setQuiet();
    }

    @Remote(called = Loc.server)
    public static void onMissionFinish(int index){
        world.getSector().missions.get(index).onComplete();
        world.getSector().completedMissions = index + 1;

        state.mode = world.getSector().currentMission().getMode();
        world.getSector().currentMission().onBegin();
        world.sectors.save();
    }

    private void updateRtsAI(){
        if(Net.client()) return;
        for(Team team : Team.all){
            if(team == Team.none || team == Team.themass) continue;
            if((state.rtsAIBits & (1L << team.ordinal())) != 0){
                Teams.TeamData data = state.teams.get(team);
                if(data.rtsAI == null){
                    data.rtsAI = new io.anuke.mindustry.ai.RtsAI(data);
                }
                data.rtsAI.update();
            }
        }
    }

    @Remote(called = Loc.server)
    public static void onSectorComplete(){
        state.mode = GameMode.victory;

        world.sectors.completeSector(world.getSector().x, world.getSector().y);
        world.sectors.save();

        if(!headless && !Net.client()){
            ui.missions.show(world.getSector());
        }

        Events.fire(new SectorCompleteEvent());
    }

    @Override
    public void update(){
        PerfCounter.update.begin();

        if(Vars.control != null){
            control.runUpdateLogic();
        }

        if(!state.is(State.menu)){

            if(!state.isPaused()){
                Timers.update();

                if(weather != null){
                    weather.update();
                }

                boolean SiegeModeTimer = state.mode == GameMode.SiegeMode;
                boolean canTickWaveTimer = !state.mode.disableWaves && !state.gameOver &&
                        (!state.mode.disableWaveTimer || SiegeModeTimer) &&
                        (!SiegeModeTimer || state.enemies() == 0);

                if(canTickWaveTimer){
                    state.wavetime -= Timers.delta();
                }

                if(!Net.client() && state.wavetime <= 0 && !state.mode.disableWaves && (!SiegeModeTimer || state.enemies() == 0)){
                    runWave();
                }

                if(!Entities.defaultGroup().isEmpty())
                    throw new RuntimeException("Do not add anything to the default group!");

                if(!headless){
                    Entities.update(effectGroup);
                    Entities.update(groundEffectGroup);
                }

                PerfCounter.unitUpdate.begin();
                for(EntityGroup group : unitGroups){
                    Entities.update(group);
                }
                PerfCounter.unitUpdate.end();

                PerfCounter.entityMisc.begin();
                Entities.update(puddleGroup);
                Entities.update(shieldGroup);
                PerfCounter.entityMisc.end();

                PerfCounter.bulletUpdate.begin();
                Entities.update(bulletGroup);
                PerfCounter.bulletUpdate.end();

                PerfCounter.buildingUpdate.begin();
                Entities.update(tileGroup);
                PerfCounter.buildingUpdate.end();

                PerfCounter.entityMisc.begin();
                Entities.update(fireGroup);
                Entities.update(playerGroup);
                PerfCounter.entityMisc.end();

                //effect group only contains item transfers in the headless version, update it!
                if(headless){
                    Entities.update(effectGroup);
                }

                for(EntityGroup group : unitGroups){
                    if(group.isEmpty()) continue;

                    EntityQuery.collideGroups(bulletGroup, group);
                }

                EntityQuery.collideGroups(bulletGroup, playerGroup);
                EntityQuery.collideGroups(playerGroup, playerGroup);

                world.pathfinder.update();
                infection.update();
                MassAI.update();
                updateRtsAI();

                if(world.isOpenWorld() && world.chunks() != null){
                    world.chunks().update();
                }

                if(world.isOpenWorld() && !headless && players.length > 0 && players[0] != null){
                    recenterOpenWorld();
                }
            }

            if(!Net.client() && !world.isInvalidMap()){
                PerfCounter.stateUpdate.begin();
                updateSectors();
                checkGameOver();
                PerfCounter.stateUpdate.end();
            }
        }

        PerfCounter.update.end();
    }

    private void recenterOpenWorld(){
        int playerTX = (int)(players[0].x / tilesize);
        int playerTY = (int)(players[0].y / tilesize);

        int halfW = world.width() / 2;
        int halfH = world.height() / 2;

        int dx = playerTX - (lastRecenterX + halfW);
        int dy = playerTY - (lastRecenterY + halfH);

        int threshold = ChunkManager.CHUNK_SIZE * ChunkManager.LOAD_RADIUS;

        //pathfinder flow grid follows the local/host player window, as before
        if(lastRecenterX == Integer.MIN_VALUE || Math.abs(dx) > threshold || Math.abs(dy) > threshold){
            lastRecenterX = playerTX - halfW;
            lastRecenterY = playerTY - halfH;

            world.pathfinder.recenter(playerTX, playerTY);
        }

        int minTX = Integer.MAX_VALUE, minTY = Integer.MAX_VALUE, maxTX = Integer.MIN_VALUE, maxTY = Integer.MIN_VALUE;

        for(Player player : playerGroup.all()){
            if(player == null) continue;
            int tx = (int)(player.x / tilesize);
            int ty = (int)(player.y / tilesize);
            minTX = Math.min(minTX, tx);
            minTY = Math.min(minTY, ty);
            maxTX = Math.max(maxTX, tx);
            maxTY = Math.max(maxTY, ty);
        }

        if(minTX == Integer.MAX_VALUE) return;

        int margin = ChunkManager.CHUNK_SIZE * 5;
        int leftTX = minTX - halfW - margin;
        int topTY = minTY - halfH - margin;
        int widthT = (maxTX - minTX) + (halfW + margin) * 2;
        int heightT = (maxTY - minTY) + (halfH + margin) * 2;

        if(lastTreeW != widthT || lastTreeH != heightT || leftTX < lastTreeX || topTY < lastTreeY
                || leftTX + widthT > lastTreeX + lastTreeW || topTY + heightT > lastTreeY + lastTreeH){
            lastTreeX = leftTX;
            lastTreeY = topTY;
            lastTreeW = widthT;
            lastTreeH = heightT;
            EntityQuery.resizeTree(leftTX * tilesize, topTY * tilesize, widthT * tilesize, heightT * tilesize);
        }
    }
}
