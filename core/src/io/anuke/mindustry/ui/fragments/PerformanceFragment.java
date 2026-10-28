package io.anuke.mindustry.ui.fragments;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.core.PerfCounter;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.scene.Element;
import io.anuke.ucore.scene.Group;
import io.anuke.ucore.scene.event.Touchable;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.Label.LabelStyle;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Strings;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static io.anuke.mindustry.Vars.*;


public class PerformanceFragment extends Fragment{
    private static final long TILE_BYTES_ESTIMATE = 192L;

    private final ObjectMap<PerfCounter, Color> counterToColor = new ObjectMap<>();

    public PerformanceFragment(){
        counterToColor.put(PerfCounter.powerUpdate, Palette.powerLight);
        counterToColor.put(PerfCounter.buildingUpdate, Palette.surge);
        counterToColor.put(PerfCounter.entityMisc, Color.SKY);
        counterToColor.put(PerfCounter.bulletUpdate, Palette.redDust);
        counterToColor.put(PerfCounter.unitUpdate, Color.ROYAL);
        counterToColor.put(PerfCounter.render, Palette.portal);
        counterToColor.put(PerfCounter.blocks, Palette.accent);
        counterToColor.put(PerfCounter.reflections, Palette.lancerLaser);
        counterToColor.put(PerfCounter.bloom, Palette.lightTrail);
        counterToColor.put(PerfCounter.lights, Palette.powerLight);
        counterToColor.put(PerfCounter.ui, Palette.remove);
        counterToColor.put(PerfCounter.stateUpdate, Color.CYAN);
        counterToColor.put(PerfCounter.other, Color.PINK);
        counterToColor.put(PerfCounter.update, Color.ORANGE);
    }

    @Override
    public void build(Group parent){
        parent.fill(t -> {
            t.visible(() -> Settings.getBool("showperformance", false));
            t.setTouchable(Touchable.disabled);
            t.top().left();
            t.marginLeft(12f).marginTop(8f);
            t.update(PerfCounter::updateAll);

            for(PerfCounter counter : PerfCounter.displayedCounters){
                Label label = new Label("", Core.skin.get("small", LabelStyle.class));
                label.setAlignment(Align.left);
                label.update(() -> {
                    label.setText(counter.name() + ": " + Strings.toFixed(counter.valueMs(), 1) + "ms");
                    label.setColor(counterToColor.get(counter, Color.WHITE));
                });
                t.add(label).left().padBottom(2f);
                t.row();
            }

            t.add(new PerfBar()).size(400f, 50f).padTop(4f);

            Label system = new Label("", Core.skin.get("small", LabelStyle.class));
            system.setAlignment(Align.left);
            system.update(() -> {
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                long max = rt.maxMemory();
                float cpu = cpuLoad();
                system.setText("RAM: " + Strings.toFixed(used / 1024f / 1024f, 1) + " / " +
                    Strings.toFixed(max / 1024f / 1024f, 1) + " MB (" + Strings.toFixed(used * 100f / Math.max(1L, max), 1) +
                    "%)  Threads: " + threadCount() + (cpu < 0f ? "" : "  CPU: " + Strings.toFixed(cpu, 1) + "%"));
            });
            t.add(system).left().padTop(4f);
            t.row();

            Label chunkInfo = new Label("", Core.skin.get("small", LabelStyle.class));
            chunkInfo.setAlignment(Align.left);
            chunkInfo.update(() -> {
                if(!world.isOpenWorld() || !(state.is(State.playing) || state.is(State.paused))){
                    chunkInfo.setText("");
                    return;
                }
                ChunkManager cm = world.chunks();
                int count = cm.getLoadedChunkCount();
                float avg = cm.chunksGenerated > 0 ? cm.totalChunkGenMs / cm.chunksGenerated : 0f;
                float mb = count * (ChunkManager.CHUNK_SIZE * ChunkManager.CHUNK_SIZE) * TILE_BYTES_ESTIMATE / 1024f / 1024f;
                chunkInfo.setText("Chunks: " + count + " (~" + Strings.toFixed(mb, 1) + "MB est)  last: " +
                    Strings.toFixed(cm.lastChunkGenMs, 1) + "ms  avg: " + Strings.toFixed(avg, 1) +
                    "ms  total: " + cm.chunksGenerated);
            });
            t.add(chunkInfo).left().padTop(2f);
        });
    }

    private static int threadCount(){
        try{
            return ManagementFactory.getThreadMXBean().getThreadCount();
        }catch(Throwable ignore){
            return Thread.activeCount();
        }
    }

    private static float cpuLoad(){
        try{
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if(bean instanceof com.sun.management.OperatingSystemMXBean){
                double load = ((com.sun.management.OperatingSystemMXBean)bean).getProcessCpuLoad();
                return load < 0 ? -1f : (float)(load * 100f);
            }
        }catch(Throwable ignore){
        }
        return -1f;
    }

    private class PerfBar extends Element{
        @Override
        public void draw(Batch batch, float alpha){
            float w = getWidth(), h = getHeight();

            Draw.color(0f, 0f, 0f, 0.7f);
            Fill.crect(0f, 0f, w, h);

            float max = 0f;
            for(PerfCounter counter : PerfCounter.displayedCounters){
                max = Math.max(max, counter.valueMs());
            }
            if(max <= 0f) max = 1f;

            float segH = h / PerfCounter.displayedCounters.length;
            for(int i = 0; i < PerfCounter.displayedCounters.length; i++){
                PerfCounter counter = PerfCounter.displayedCounters[i];
                float frac = Math.min(1f, counter.valueMs() / max);
                float y = h - segH * (i + 1);
                Color col = counterToColor.get(counter, Color.WHITE);
                Draw.color(col.r, col.g, col.b, 0.9f);
                Fill.crect(0f, y, frac * w, segH - 1f);
            }
            Draw.color();
        }
    }
}