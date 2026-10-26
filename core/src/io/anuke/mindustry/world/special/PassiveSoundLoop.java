package io.anuke.mindustry.world.special;

import com.badlogic.gdx.audio.Sound;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

public class PassiveSoundLoop{
    private static final float fadeSpeed = 0.075f;
    private static final float falloff = 9000f;

    private final Sound sound;
    private long id = -1L;
    private float volume, baseVolume;
    private boolean played;
    private float x, y;
    private float pan;
    private boolean constant = false, doppler = false;

    public PassiveSoundLoop(Sound sound){
        this.sound = sound;
    }

    public PassiveSoundLoop(Sound sound, boolean constant, boolean doppler){
        this.sound = sound;
        this.constant = constant;
        this.doppler = doppler;
    }

    public void update(){
        if(!played && id != -1L){
            volume = Mathf.clamp(volume - fadeSpeed * Timers.delta());

            if(volume <= 0.001f){
                sound.stop(id);
                id = -1L;
                return;
            }

            float v = constant ? 1f : calcFalloff(x, y);

            sound.setPan(id, pan, Mathf.clamp(v * volume * baseVolume, 0f, 1f));
        }
        played = false;
    }

    public float calcFalloff(float x, float y){
        if(Core.camera == null) return 1f;

        float dx = x - Core.camera.position.x;
        float dy = y - Core.camera.position.y;
        return Mathf.clamp(1f / Math.max((dx * dx + dy * dy) / falloff, 1f));
    }

    public float calcPan(float x, float y){
        if(Core.camera == null) return 0f;

        return Mathf.clamp((x - Core.camera.position.x) / (Core.camera.viewportWidth / 2f), -0.9f, 0.9f);
    }

    public void play(float x, float y, float vol, boolean play){
        play(x, y, vol, calcPan(x, y), play);
    }

    public void play(float x, float y, float vol, float pan, boolean play){
        if(id == -1L){
            if(play){
                float v = constant ? 1f : calcFalloff(x, y);

                baseVolume = vol;
                this.x = x;
                this.y = y;
                this.pan = pan;
                id = sound.loop(Mathf.clamp(v * vol, 0f, 1f), 1f, pan);
                if(id != -1L) sound.setLooping(id, true);
                played = true;
            }
        }else{
            float v = constant ? 1f : calcFalloff(x, y);
            float lx = this.x, ly = this.y;

            baseVolume = vol;
            this.x = x;
            this.y = y;
            this.pan = pan;
            played = play;

            sound.setPan(id, pan, Mathf.clamp(v * volume * vol, 0f, 1f));

            if(doppler){
                float dst1 = Core.camera.position.dst(lx, ly, 0f);
                float dst2 = Core.camera.position.dst(x, y, 0f);

                float d = ((dst2 - dst1) / 3f) / Timers.delta();
                float w = 20f;
                float delta = Mathf.clamp(w / (w + Math.max(d, -(w - 1f))), 0.5f, 3f);

                sound.setPitch(id, delta);
            }
        }
        if(play) volume = Mathf.clamp(volume + fadeSpeed * Timers.delta());
    }

    public void stop(){
        if(id != -1L){
            sound.stop(id);
            id = -1L;
        }
    }
}
