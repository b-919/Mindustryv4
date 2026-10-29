package io.anuke.mindustry.graphics.mesh;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Vector3;
import io.anuke.mindustry.graphics.mesh.PlanetGrid.Corner;
import io.anuke.mindustry.graphics.mesh.PlanetGrid.Ptile;

/**
 * Builds the geodesic planet meshes.
 * Vertex format: position(3), normal(3), packed color(4).
 */
public class MeshBuilder{
    private static final int vertexSize = 8;

    /** Builds a terrain mesh with per-corner height and per-tile color. */
    public static Mesh buildHex(HexMesher mesher, int divisions, float radius, float intensity){
        PlanetGrid grid = PlanetGrid.create(Math.max(0, divisions));

        int maxVertices = grid.tiles.length * 6;
        int maxIndices = grid.tiles.length * 12;
        float[] vertices = new float[maxVertices * vertexSize];
        short[] indices = new short[maxIndices];

        float[] heights = new float[grid.corners.length];
        for(int i = 0; i < grid.corners.length; i++){
            heights[i] = (1f + mesher.getHeight(grid.corners[i].v) * intensity) * radius;
        }

        Color color = new Color();
        Vector3 normal = new Vector3();
        int vptr = 0, iptr = 0, base = 0;

        for(Ptile tile : grid.tiles){
            if(mesher.skip(tile.v)) continue;

            Corner[] corners = tile.corners;

            normal(
                corners[0].v.x * heights[corners[0].id], corners[0].v.y * heights[corners[0].id], corners[0].v.z * heights[corners[0].id],
                corners[2].v.x * heights[corners[2].id], corners[2].v.y * heights[corners[2].id], corners[2].v.z * heights[corners[2].id],
                corners[4].v.x * heights[corners[4].id], corners[4].v.y * heights[corners[4].id], corners[4].v.z * heights[corners[4].id],
                normal
            );

            //one flat color per hex
            mesher.getColor(tile.v, color);
            float colorBits = color.toFloatBits();
            mesher.getEmissiveColor(tile.v, color);
            float emissiveBits = color.toFloatBits();

            for(Corner corner : corners){
                float h = heights[corner.id];
                vptr = vertex(vertices, vptr, corner.v.x * h, corner.v.y * h, corner.v.z * h, normal, colorBits, emissiveBits);
            }

            indices[iptr++] = (short)(base);
            indices[iptr++] = (short)(base + 1);
            indices[iptr++] = (short)(base + 2);

            indices[iptr++] = (short)(base);
            indices[iptr++] = (short)(base + 2);
            indices[iptr++] = (short)(base + 3);

            indices[iptr++] = (short)(base);
            indices[iptr++] = (short)(base + 3);
            indices[iptr++] = (short)(base + 4);

            if(corners.length > 5){
                indices[iptr++] = (short)(base);
                indices[iptr++] = (short)(base + 4);
                indices[iptr++] = (short)(base + 5);
            }

            base += corners.length;
        }

        Mesh mesh = new Mesh(true, Math.max(1, base), Math.max(1, iptr),
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.Normal, 3, "a_normal"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_color"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_emissive")
        );

        mesh.setVertices(vertices, 0, vptr);
        mesh.setIndices(indices, 0, iptr);
        return mesh;
    }

    public static Mesh buildHex(Color color, int divisions, float radius){
        return buildHex(new HexMesher(){
            @Override
            public void getColor(Vector3 position, Color out){
                out.set(color);
            }
        }, divisions, radius, 0f);
    }

    public static Mesh buildPlanetGrid(PlanetGrid grid, Color color, float scale){
        return buildPlanetGrid(grid, color, null, scale);
    }

    /** Builds a line mesh that follows the supplied per-corner heights. */
    public static Mesh buildPlanetGrid(PlanetGrid grid, Color color, float[] heights, float offset){
        int maxVertices = grid.tiles.length * 6 * 2;
        float[] vertices = new float[maxVertices * 4];
        float colorBits = color.toFloatBits();
        int vptr = 0;

        for(Ptile tile : grid.tiles){
            Corner[] corners = tile.corners;
            for(int i = 0; i < corners.length; i++){
                Corner a = corners[i];
                Corner b = corners[(i + 1) % corners.length];
                float ah = heights == null ? offset : 1f + heights[a.id] + offset;
                float bh = heights == null ? offset : 1f + heights[b.id] + offset;

                vptr = lineVertex(vertices, vptr, a.v.x * ah, a.v.y * ah, a.v.z * ah, colorBits);
                vptr = lineVertex(vertices, vptr, b.v.x * bh, b.v.y * bh, b.v.z * bh, colorBits);
            }
        }

        Mesh mesh = new Mesh(true, Math.max(1, vptr / 4), 0,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.ColorPacked, 4, "a_color")
        );

        mesh.setVertices(vertices, 0, vptr);
        return mesh;
    }

    /** Legacy name used by the old preview renderer; now produces the same geodesic mesh. */
    public static Mesh buildPlanet(HexMesher mesher, int divisions, float radius, float intensity){
        return buildHex(mesher, divisions, radius, intensity);
    }

    private static int vertex(float[] out, int p, float x, float y, float z, Vector3 normal, float color, float emissive){
        out[p++] = x;
        out[p++] = y;
        out[p++] = z;
        out[p++] = normal.x;
        out[p++] = normal.y;
        out[p++] = normal.z;
        out[p++] = color;
        out[p++] = emissive;
        return p;
    }

    private static int lineVertex(float[] out, int p, float x, float y, float z, float color){
        out[p++] = x;
        out[p++] = y;
        out[p++] = z;
        out[p++] = color;
        return p;
    }

    private static void normal(float v1x, float v1y, float v1z, float v2x, float v2y, float v2z, float v3x, float v3y, float v3z, Vector3 out){
        float x = v2x - v1x, y = v2y - v1y, z = v2z - v1z;
        float vx = v3x - v1x, vy = v3y - v1y, vz = v3z - v1z;

        out.set(y * vz - z * vy, z * vx - x * vz, x * vy - y * vx).nor();
        if(out.len2() < 0.000001f){
            out.set(v1x, v1y, v1z).nor();
        }
    }
}
