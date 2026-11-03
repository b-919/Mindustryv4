package io.anuke.mindustry.maps.filters;

import com.badlogic.gdx.graphics.Pixmap;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.OreBlock;
import io.anuke.ucore.noise.Simplex;
import io.anuke.ucore.noise.RidgedPerlin;
import io.anuke.ucore.util.Mathf;

public abstract class GenerateFilter implements Cloneable{
    public int seed = 0;

    public abstract FilterOption[] options();

    public abstract void apply(GenerateInput in);

    public void drawOverlay(Pixmap pixmap, int pixW, int pixH, int mapW, int mapH){}

    public String name(){
        return getClass().getSimpleName().replace("Filter", "");
    }

    public void randomize(){
        seed = Mathf.random(999999999);
    }

    public GenerateFilter copy(){
        try{
            return (GenerateFilter) clone();
        }catch(CloneNotSupportedException e){
            throw new RuntimeException(e);
        }
    }

    protected float noise(int seedOffset, GenerateInput in, float scl, float mag){
        Simplex s = new Simplex(seedOffset + seed);
        return (float)(s.octaveNoise2D(1, 0, 1f / scl, in.x + 10, in.y + 10)) * mag;
    }

    protected float noise(GenerateInput in, float scl, float mag){
        Simplex s = new Simplex(seed);
        return (float)(s.octaveNoise2D(1, 0, 1f / scl, in.x + 10, in.y + 10)) * mag;
    }

    protected float noise(GenerateInput in, float scl, float mag, float octaves, float persistence){
        Simplex s = new Simplex(seed);
        return (float)(s.octaveNoise2D(octaves, persistence, 1f / scl, in.x + 10, in.y + 10)) * mag;
    }

    protected float noise(float x, float y, float scl, float mag, float octaves, float persistence){
        Simplex s = new Simplex(seed);
        return (float)(s.octaveNoise2D(octaves, persistence, 1f / scl, x + 10, y + 10)) * mag;
    }

    protected float rnoise(float x, float y, float scl, float mag){
        return new RidgedPerlin(seed + 1, 1).getValue((int)x, (int)y, 1f / scl) * mag;
    }

    protected float rnoise(float x, float y, int octaves, float scl, float falloff, float mag){
        return new RidgedPerlin(seed + 1, octaves).getValue((int)x, (int)y, 1f / scl) * mag;
    }

    protected float chance(int x, int y){
        return Mathf.randomSeed(x * 0x143289571L + y * 0x162839475L + seed);
    }

    public static class GenerateInput{
        public int x, y, width, height;
        public Block floor, block, overlay;
        public byte elevation;
        public TileProvider buffer;

        public void set(int x, int y, Block block, Block floor, Block overlay){
            this.x = x;
            this.y = y;
            this.block = block;
            this.floor = floor;
            this.overlay = overlay;
        }

        public void set(int x, int y, Block block, Block floor, Block overlay, byte elevation){
            set(x, y, block, floor, overlay);
            this.elevation = elevation;
        }

        public void set(Tile tile){
            set(tile.x, tile.y, tile.block(), tile.floor(), tile.floor() instanceof OreBlock ? tile.floor() : Blocks.air, tile.getElevation());
        }

        public void begin(int width, int height, TileProvider buffer){
            this.buffer = buffer;
            this.width = width;
            this.height = height;
        }

        public Tile tile(float fx, float fy){
            int tx = Mathf.clamp((int)fx, 0, width - 1);
            int ty = Mathf.clamp((int)fy, 0, height - 1);
            return buffer.get(tx, ty);
        }

        public interface TileProvider{
            Tile get(int x, int y);
        }
    }
}
