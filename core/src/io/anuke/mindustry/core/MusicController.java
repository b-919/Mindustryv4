package io.anuke.mindustry.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.game.EventType;
import io.anuke.mindustry.game.EventType.GameLoadEvent;
import io.anuke.mindustry.game.EventType.GameOverEvent;
import io.anuke.mindustry.game.EventType.PlayEvent;
import io.anuke.mindustry.game.EventType.StateChangeEvent;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.modules.Module;
import io.anuke.ucore.util.Mathf;

public class MusicController extends Module{
    public static boolean MusicCurrentlyPlaying = false;
    private static final float DarkMusicChance = 0.4f;

    private Music menu;
    private Music editor;
    private Music[] game;
    private Music[] boss;
    private Music[] dark;
    private Music current;
    private float gameMusicTimer;

    public MusicController(){
        if(Vars.headless) return;

        menu = Gdx.audio.newMusic(Gdx.files.internal("music/menu.ogg"));
        editor = Gdx.audio.newMusic(Gdx.files.internal("music/editor.ogg"));
        game = loadMusic(
                "game1",
                "game2",
                "game3",
                "game4",
                "game5",
                "game6",
                "game7",
                "game8",
                "game9"
        );
        boss = loadMusic(
                "boss1",
                "boss2"
        );

        dark = loadMusic(
                "game2",
                "game5",
                "game7",
                "game4"
        );

        menu.setLooping(true);
        editor.setLooping(true);
        for(Music music : game){
            music.setLooping(false);
        }
        for(Music music : boss){
            music.setLooping(false);
        }
        for(Music music : dark){
            music.setLooping(false);
        }

        Events.on(GameLoadEvent.class, e -> play(menu));
        Events.on(PlayEvent.class, e -> startGameSilence());
        Events.on(EventType.WorldLoadEvent.class, e -> startGameSilence());
        Events.on(EventType.WaveEvent.class, e -> {
            if(Vars.state.is(GameState.State.playing) && current == null && Mathf.chance(DarkMusicChance)){
                playRandom(dark);
            }
        });
        Events.on(GameOverEvent.class, e -> play(menu));
        Events.on(StateChangeEvent.class, e -> {
            if(e.to == GameState.State.menu) play(menu);
        });
    }

    private static Music[] loadMusic(String... names){
        Music[] music = new Music[names.length];
        for(int i = 0; i < names.length; i++){
            music[i] = Gdx.audio.newMusic(Gdx.files.internal("music/" + names[i] + ".ogg"));
        }
        return music;
    }

    private void playRandom(Music[] tracks){
        if(tracks == null || tracks.length == 0) return;
        play(tracks[Mathf.random(tracks.length - 1)]);
    }

    private void play(Music music){
        if(current == music) return;
        stopCurrent();
        current = music;
        current.setVolume(getVolume());
        current.play();
        MusicCurrentlyPlaying = current.isPlaying();
    }

    private float getVolume(){
        if(Settings.getBool("mutemusic")) return 0f;
        return Settings.getInt("musicvol", 10) / 10f;
    }

    private float nextGameMusicDelay(){
        return Mathf.random(20, 60) * Mathf.random(20, 60) * 5f;
    }

    private void startGameSilence(){
        stopCurrent();
        gameMusicTimer = nextGameMusicDelay();
    }

    private void stopCurrent(){
        if(current != null){
            current.stop();
            current = null;
        }
        MusicCurrentlyPlaying = false;
    }

    @Override
    public void update(){
        if(Vars.ui != null && Vars.ui.editor != null && Vars.ui.editor.isShown()){
            if(current != editor){
                play(editor);
            }else{
                current.setVolume(getVolume());
                MusicCurrentlyPlaying = current.isPlaying();
            }
            return;
        }

        if(current == editor && Vars.state.is(GameState.State.menu)){
            play(menu);
        }

        if(Vars.state.is(GameState.State.playing) && hasBossUnits()){
            if(current == null || !isBossTrack(current)){
                playRandom(boss);
            }else{
                current.setVolume(getVolume());
                MusicCurrentlyPlaying = current.isPlaying();
                if(!current.isPlaying()){
                    playRandom(boss);
                }
            }
            return;
        }

        if(current != null && isBossTrack(current) && Vars.state.is(GameState.State.playing)){
            startGameSilence();
        }

        if(current != null){
            current.setVolume(getVolume());
            MusicCurrentlyPlaying = current.isPlaying();

            if(!current.isPlaying() && isGameTrack(current)){
                current = null;
                MusicCurrentlyPlaying = false;
                gameMusicTimer = nextGameMusicDelay();
            }
        }else{
            MusicCurrentlyPlaying = false;
        }

        if(Vars.state.is(GameState.State.playing) && current == null){
            gameMusicTimer -= Timers.delta();
            if(gameMusicTimer <= 0f){
                playRandom(game);
            }
        }
    }

    @Override
    public void dispose(){
        if(menu != null) menu.dispose();
        if(editor != null) editor.dispose();
        disposeAll(game);
        disposeAll(boss);
        disposeAll(dark);
    }

    private void disposeAll(Music[] tracks){
        if(tracks == null) return;
        for(Music music : tracks){
            music.dispose();
        }
    }

    private boolean isGameTrack(Music music){
        return contains(game, music) || contains(boss, music) || contains(dark, music);
    }

    private boolean isBossTrack(Music music){
        return contains(boss, music);
    }

    private boolean contains(Music[] tracks, Music music){
        if(tracks == null) return false;
        for(Music track : tracks){
            if(track == music) return true;
        }
        return false;
    }

    private boolean hasBossUnits(){
        for(BaseUnit unit : Vars.unitGroups[Vars.state.enemyTeam.ordinal()].all()){
            if(unit.getType().isBoss){
                return true;
            }
        }
        return false;
    }
}
