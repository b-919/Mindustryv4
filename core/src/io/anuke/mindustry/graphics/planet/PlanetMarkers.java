package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.PlanetMap;
import io.anuke.mindustry.maps.campaign.PlanetMap.Region;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.ui.layout.Unit;
import io.anuke.ucore.util.Mathf;

/** Draws the 2D sector icons and completion lines on top of the composited planet image. */
public class PlanetMarkers{
    private final Vector3 project = new Vector3();
    private float[] px = new float[0];
    private float[] py = new float[0];
    private boolean[] visible = new boolean[0];

    public void draw(PlanetMap map, float[] regionHeights, float gridLift, float sectorAlpha, PlanetCamera camera,
                     float x, float y, float width, float height, Sector selected, Sector hovered){
        if(map == null || sectorAlpha <= 0.01f) return;

        int regions = map.regions.length;
        if(px.length != regions){
            px = new float[regions];
            py = new float[regions];
            visible = new boolean[regions];
        }

        float a = sectorAlpha;
        float dpu = Unit.dp.scl(1f);
        float size = Mathf.clamp(34f * dpu / Math.max(0.7f, camera.cam.position.len() / 4.1f), 12f, 48f);

        for(int i = 0; i < regions; i++){
            Region region = map.regions[i];
            Sector sector = region.sector;
            visible[i] = false;
            if(sector == null || region.center.dot(camera.cam.position) <= 0f) continue;

            project.set(region.center).scl(1f + (regionHeights == null ? 0f : regionHeights[i]) + gridLift);
            camera.cam.project(project, x, y, width, height);
            if(project.x < x - size || project.x > x + width + size || project.y < y - size || project.y > y + height + size){
                continue;
            }

            px[i] = project.x;
            py[i] = project.y;
            visible[i] = true;
        }

        Lines.stroke(1.5f);
        for(int i = 0; i < regions; i++){
            Region region = map.regions[i];
            if(!visible[i] || region.sector == null || !region.sector.complete) continue;

            for(Region neighbor : region.neighbors){
                int other = neighbor.tile.id;
                if(neighbor.sector == null || !neighbor.sector.complete || other <= region.tile.id || !visible[other]) continue;
                Draw.color(Palette.accent);
                Draw.alpha(0.18f * a);
                Lines.line(px[i], py[i], px[other], py[other]);
            }
        }
        Lines.stroke(1f);
        Draw.alpha(a);

        for(int i = 0; i < regions; i++){
            if(!visible[i]) continue;

            Region region = map.regions[i];
            Sector sector = region.sector;
            if(sector == null) continue;

            boolean unlocked = map.isUnlocked(sector);
            float sx = px[i], sy = py[i];

            if(sector.complete){
                Draw.color(Palette.accent);
                Draw.alpha(a);
                Fill.poly(sx, sy, 4, size * 0.32f, 45f);
            }else if(unlocked){
                Draw.color(Color.WHITE);
                Draw.alpha(a);
                Fill.circle(sx, sy, size * 0.10f);
            }else{
                Draw.color(0.35f, 0.35f, 0.38f, 0.8f * a);
                Fill.circle(sx, sy, size * 0.07f);
            }

            if(unlocked && !sector.complete && sector.missions.size > 0){
                Draw.color(0f, 0f, 0f, 0.45f * a);
                Fill.circle(sx, sy, size * 0.24f);
                Draw.color(Color.WHITE);
                Draw.alpha(a);
                Draw.rect(sector.getDominantMission().getIcon(), sx, sy, size * 0.38f, size * 0.38f);
            }

            if(sector.hasSave()){
                Draw.color(Palette.accent);
                Draw.alpha(0.6f * a);
                Draw.rect("sector-select", sx, sy, size, size);
                Draw.alpha(a);
            }

            if(selected != null && sector == selected){
                Draw.color(Palette.accent);
                Draw.alpha(a);
                Draw.rect("sector-select", sx, sy, size * 1.35f, size * 1.35f);
                Draw.color(Color.WHITE);
                Draw.alpha(0.85f * a);
                Draw.rect("sector-select", sx, sy, size * 1.5f, size * 1.5f);
                Draw.alpha(a);
            }else if(hovered != null && sector == hovered){
                Draw.color(Color.WHITE);
                Draw.alpha(a);
                Draw.rect("sector-select", sx, sy, size * 1.2f, size * 1.2f);
            }
        }

        Draw.reset();
    }
}
