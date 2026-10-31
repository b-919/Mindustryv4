package io.anuke.mindustry.graphics.planet;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.mesh.MeshBuilder;
import io.anuke.mindustry.graphics.mesh.PlanetGrid.Corner;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.PlanetMap;
import io.anuke.mindustry.maps.campaign.PlanetMap.Region;
import io.anuke.mindustry.maps.campaign.PlanetVisual;
import io.anuke.ucore.util.Mathf;

/**
 * All overlay geometry on the planet surface: the hex grid, sector fills,
 * sector outlines and the connection arcs.
 */
public class PlanetOverlay{
    private static final Matrix4 IDENTITY = new Matrix4();

    private final PlanetMap map;
    private final ShaderProgram shader;

    private final Mesh gridMesh;
    private final Mesh overlayMesh;
    private final Mesh highlightMesh;
    private final Mesh arcMesh;

    private final float[] gridHeights;
    private final float[] regionHeights;
    private final float gridLift, fillLift, lineLift, arcLift;
    private final float[] overlayVertices;
    private final float[] lineVertices;
    private final float[] arcVertices;

    private final Color tmpColor = new Color();
    private final PlanetTmp tmp = new PlanetTmp();
    private int overlayCount, lineCount, arcCount;

    public PlanetOverlay(PlanetMap map, ShaderProgram shader, float[] gridHeights, float[] regionHeights,
                         float gridLift, float fillLift, float lineLift, float arcLift){
        this.map = map;
        this.shader = shader;
        this.gridHeights = gridHeights;
        this.regionHeights = regionHeights;
        this.gridLift = gridLift;
        this.fillLift = fillLift;
        this.lineLift = lineLift;
        this.arcLift = arcLift;

        gridMesh = MeshBuilder.buildPlanetGrid(map.grid, map.definition.visual.gridColor, gridHeights, gridLift);

        overlayVertices = new float[map.grid.tiles.length * 12 * 4];
        lineVertices = new float[map.grid.tiles.length * 6 * 2 * 4];
        arcVertices = new float[4096 * 2 * 4];

        int maxOverlayVertices = map.grid.tiles.length * 12;
        int maxLineVertices = map.grid.tiles.length * 6 * 2;

        overlayMesh = new Mesh(true, maxOverlayVertices, 0,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_color"));

        highlightMesh = new Mesh(true, maxLineVertices, 0,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_color"));

        arcMesh = new Mesh(true, 4096 * 2, 0,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_color"));
    }

    public void renderGrid(PlanetParams params){
        if(params.gridAlpha <= 0.01f) return;

        shader.begin();
        shader.setUniformMatrix("u_proj", params.projection);
        shader.setUniformMatrix("u_trans", IDENTITY);
        shader.setUniformf("u_alpha", params.gridAlpha);
        gridMesh.render(shader, GL20.GL_LINES);
        shader.end();
    }

    /** Rebuilds the sector fill/outline vertex buffers for the current selection. */
    public void buildGeometry(Sector selected, Sector hovered, float time){
        int o = 0, l = 0;
        float pulse = Mathf.absin(time * 1.7f, 1f, 0.07f);
        PlanetVisual visual = map.definition.visual;

        for(Region region : map.regions){
            Sector sector = region.sector;
            if(sector == null) continue;

            float alpha;
            if(selected != null && sector == selected){
                tmpColor.set(Palette.accent);
                alpha = 0.24f + pulse;
                tmpColor.a = alpha;
            }else if(hovered != null && sector == hovered){
                tmpColor.set(1f, 1f, 1f, 0.18f);
                alpha = 0.18f;
            }else if(sector.complete){
                alpha = 0f;
            }else if(map.isUnlocked(sector)){
                tmpColor.set(Palette.accent);
                alpha = 0.10f + pulse * 0.5f;
                tmpColor.a = alpha;
            }else{
                tmpColor.set(0f, 0f, 0f, 0.06f);
                alpha = 0.06f;
            }

            if(sector.isInfected() && !(selected != null && sector == selected)){
                tmpColor.set(visual.sectorInfectedGlow);
                tmpColor.a = Math.max(alpha, 0.16f);
                alpha = tmpColor.a;
            }

            boolean highlighted = (selected != null && sector == selected) || (hovered != null && sector == hovered);
            if(!highlighted){
                float light = Mathf.clamp(sector.light, 0f, 1f);
                float mul = 0.30f + 0.70f * light;
                tmpColor.r *= mul;
                tmpColor.g *= mul;
                tmpColor.b *= mul;
            }

            if(alpha > 0f){
                Corner[] corners = region.tile.corners;
                for(int i = 1; i < corners.length - 1; i++){
                    o = putVertex(overlayVertices, o, corners[0], fillLift, tmpColor);
                    o = putVertex(overlayVertices, o, corners[i], fillLift, tmpColor);
                    o = putVertex(overlayVertices, o, corners[i + 1], fillLift, tmpColor);
                }
            }

            if(highlighted){
                tmpColor.set(sector == selected ? Palette.accent : Color.WHITE);
                tmpColor.a = 0.9f;
                Corner[] corners = region.tile.corners;
                for(int i = 0; i < corners.length; i++){
                    Corner a = corners[i];
                    Corner b = corners[(i + 1) % corners.length];
                    l = putLineVertex(lineVertices, l, a, lineLift, tmpColor);
                    l = putLineVertex(lineVertices, l, b, lineLift, tmpColor);
                }
            }
        }

        overlayCount = o;
        lineCount = l;
    }

    public void renderSectors(PlanetParams params){
        if(params.sectorAlpha <= 0.01f) return;

        if(overlayCount > 0){
            overlayMesh.setVertices(overlayVertices, 0, overlayCount);
            shader.begin();
            shader.setUniformMatrix("u_proj", params.projection);
            shader.setUniformMatrix("u_trans", IDENTITY);
            shader.setUniformf("u_alpha", params.sectorAlpha);
            overlayMesh.render(shader, GL20.GL_TRIANGLES);
            shader.end();
        }

        if(lineCount > 0){
            highlightMesh.setVertices(lineVertices, 0, lineCount);
            shader.begin();
            shader.setUniformMatrix("u_proj", params.projection);
            shader.setUniformMatrix("u_trans", IDENTITY);
            shader.setUniformf("u_alpha", params.sectorAlpha);
            highlightMesh.render(shader, GL20.GL_LINES);
            shader.end();
        }
    }

    /** Builds the arcs from the selected sector to its neighbors. */
    public void buildArcs(Sector selected){
        arcCount = 0;
        if(selected == null || map == null) return;

        Region from = map.regionFor(selected);
        if(from == null) return;

        Vector3 start = tmp.arcStart.set(from.center).scl(1f + regionHeights[from.index] + fillLift);
        tmpColor.set(Palette.accent);
        tmpColor.a = 0.55f;

        for(Region to : from.neighbors){
            if(to == null || to.sector == null) continue;

            Vector3 end = tmp.arcEnd.set(to.center).scl(1f + regionHeights[to.index] + fillLift);
            tmp.arcControl.set(start).add(end).scl(0.5f);
            float height = 1f + Math.max(regionHeights[from.index], regionHeights[to.index]) + arcLift;
            tmp.arcControl.nor().scl(height);

            int segments = Vars.mobile ? 14 : 20;
            Vector3 prev = tmp.arcPrev.set(start);
            for(int i = 1; i <= segments; i++){
                float t = i / (float)segments;
                float inv = 1f - t;
                tmp.arcPoint.set(start).scl(inv * inv)
                    .add(tmp.arcControl.x * 2f * inv * t, tmp.arcControl.y * 2f * inv * t, tmp.arcControl.z * 2f * inv * t)
                    .add(end.x * t * t, end.y * t * t, end.z * t * t);

                if(arcCount + 8 <= arcVertices.length){
                    arcCount = putWorldVertex(arcVertices, arcCount, prev, tmpColor);
                    arcCount = putWorldVertex(arcVertices, arcCount, tmp.arcPoint, tmpColor);
                }
                prev.set(tmp.arcPoint);
            }
        }
    }

    public void renderArcs(PlanetParams params){
        if(params.sectorAlpha <= 0.01f || arcCount == 0) return;

        arcMesh.setVertices(arcVertices, 0, arcCount);
        shader.begin();
        shader.setUniformMatrix("u_proj", params.projection);
        shader.setUniformMatrix("u_trans", IDENTITY);
        shader.setUniformf("u_alpha", params.sectorAlpha);
        arcMesh.render(shader, GL20.GL_LINES);
        shader.end();
    }

    private int putVertex(float[] out, int p, Corner corner, float offset, Color color){
        float bits = color.toFloatBits();
        float height = 1f + (gridHeights == null ? 0f : gridHeights[corner.id]) + offset;
        out[p++] = corner.v.x * height;
        out[p++] = corner.v.y * height;
        out[p++] = corner.v.z * height;
        out[p++] = bits;
        return p;
    }

    private int putLineVertex(float[] out, int p, Corner corner, float offset, Color color){
        return putVertex(out, p, corner, offset, color);
    }

    private static int putWorldVertex(float[] out, int p, Vector3 v, Color color){
        float bits = color.toFloatBits();
        out[p++] = v.x;
        out[p++] = v.y;
        out[p++] = v.z;
        out[p++] = bits;
        return p;
    }

    public void dispose(){
        gridMesh.dispose();
        overlayMesh.dispose();
        highlightMesh.dispose();
        arcMesh.dispose();
    }
}
