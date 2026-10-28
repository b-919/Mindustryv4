package io.anuke.mindustry.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.WindowedMean;
import com.badlogic.gdx.utils.TimeUtils;

public enum PerfCounter{
    frame, update, other, stateUpdate, entityMisc, entityUpdate, buildingUpdate, powerUpdate, unitUpdate, bulletUpdate, ui, render,
    blocks, reflections, bloom, lights;

    public static final PerfCounter[] all = values();
    public static final PerfCounter[] displayedCounters = {
        powerUpdate, buildingUpdate, entityMisc, bulletUpdate, unitUpdate,
        render, blocks, reflections, bloom, lights, ui, stateUpdate, other
    };

    public final WindowedMean mean = new WindowedMean(meanWindow);

    private static final int meanWindow = 120;

    private long lastTimeNS;
    private long beginTimeNS;
    private long lastUpdateFrame = -1;

    /** Add additional time to this counter (independent of begin/end). */
    public void add(long nanos){
        lastTimeNS += nanos;
    }

    public void begin(){
        beginTimeNS = TimeUtils.nanoTime();
    }

    public void end(long subtract){
        lastTimeNS += TimeUtils.nanoTime() - beginTimeNS - subtract;
    }

    public void end(){
        lastTimeNS += TimeUtils.nanoTime() - beginTimeNS;
    }

    public void beginPart(){
        beginTimeNS = TimeUtils.nanoTime();
    }

    public void endPart(long subtract){
        lastTimeNS += TimeUtils.nanoTime() - beginTimeNS - subtract;
    }

    public void finishParts(){
        lastTimeNS += TimeUtils.nanoTime() - beginTimeNS;
    }

    public void checkUpdate(){
        if(lastUpdateFrame < Gdx.graphics.getFrameId()){
            lastUpdateFrame = Gdx.graphics.getFrameId();
            mean.addValue((float)lastTimeNS);
            lastTimeNS = 0;
        }
    }

    public static void updateAll(){
        for(PerfCounter counter : all){
            counter.checkUpdate();
        }
    }

    public float valueMs(){
        return mean.getMean() * 1000f;
    }
}