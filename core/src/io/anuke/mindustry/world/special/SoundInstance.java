package io.anuke.mindustry.world.special;

import com.badlogic.gdx.audio.Sound;

public class SoundInstance{
    final Sound sound;
    long id = -1L;

    public SoundInstance(Sound sound){
        this.sound = sound;
    }

    public void play(float vol, float pitch, boolean loop){
        if(id != -1L) return;
        id = loop ? sound.loop(vol, pitch, 0f) : sound.play(vol, pitch, 0f);
        if(loop) sound.setLooping(id, true);
    }
    public void setVolume(float vol){
        if(id == -1L) return;
        sound.setVolume(id, vol);
    }
    public void protect(){
    }
    public void stop(){
        if(id == -1L) return;

        sound.stop(id);
        id = -1L;
    }
}
