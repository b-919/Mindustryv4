package io.anuke.mindustry.graphics.bloom;

import com.badlogic.gdx.graphics.GL20;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.graphics.BloomLayer;
import io.anuke.mindustry.graphics.BloomLayers;
import io.anuke.mindustry.graphics.CacheLayer;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Surface;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.*;

public class TileBloomLayer implements BloomLayer{
    @Override
    public String name(){
        return BloomLayers.TILES;
    }

    @Override
    public void draw(){
        int avgx = Mathf.scl(Core.camera.position.x, tilesize);
        int avgy = Mathf.scl(Core.camera.position.y, tilesize);
        int rangex = (int)(Core.camera.viewportWidth / tilesize / 2) + 2;
        int rangey = (int)(Core.camera.viewportHeight / tilesize / 2) + 2;

        int minx, miny, maxx, maxy;
        if(world.isOpenWorld()){
            minx = avgx - rangex;
            miny = avgy - rangey;
            maxx = avgx + rangex;
            maxy = avgy + rangey;
        }else{
            minx = Math.max(avgx - rangex, 0);
            miny = Math.max(avgy - rangey, 0);
            maxx = Math.min(world.width() - 1, avgx + rangex);
            maxy = Math.min(world.height() - 1, avgy + rangey);
        }

        drawLavaFloorBloom(minx, miny, maxx, maxy);

        // block bloom + liquid layers + non-lava floor bloom (single tile loop)
        for(int x = minx; x <= maxx; x++){
            for(int y = miny; y <= maxy; y++){
                Tile tile = world.peekTile(x, y);
                if(tile == null) continue;

                Block block = tile.block();
                if(block.hasBloom){
                    block.drawBloom(tile);
                }

                // blocks that render stored liquid: the liquid layer itself blooms if it emits light
                if(block.hasLiquidLayer){
                    Liquid liquid = block.liquidFor(tile);
                    if(liquid != null && liquid.emitLight){
                        block.drawLiquid(tile);
                    }
                }

                //liquid floor glow, Lava is exception
                Floor floor = tile.floor();
                if(block == Blocks.air && floor.liquidDrop != null && floor.liquidDrop.emitLight
                    && !isShaderLavaFloor(tile)){
                    if(floor.bloomFromDraw){
                        Draw.reset();
                        floor.drawBloomFromDraw(tile);
                        Graphics.shader();
                        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                        Draw.reset();
                    }else{
                        Draw.color(floor.liquidDrop.color);
                        Draw.alpha(0.15f);
                        Draw.rect("circle", tile.drawx(), tile.drawy(), tilesize * 3f, tilesize * 3f);
                        Draw.color();
                    }
                }
            }
        }
    }

    private void drawLavaFloorBloom(int minx, int miny, int maxx, int maxy){
        if(!renderer.blocks.isLayerVisible(CacheLayer.lava)) return;

        Surface lavaSurface = renderer.lavaBloomSurface;
        boolean any = false;

        Graphics.surface(lavaSurface, false, false);
        //the shader checks color.r > 0.01, so the raw layer must start black-transparent
        Graphics.clear(0f, 0f, 0f, 0f);
        Core.batch.setProjectionMatrix(Core.camera.combined);
        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        for(int x = minx; x <= maxx; x++){
            for(int y = miny; y <= maxy; y++){
                Tile tile = world.peekTile(x, y);
                if(tile == null || !isShaderLavaFloor(tile)) continue;

                Draw.reset();
                tile.floor().drawBloomFromDraw(tile);
                any = true;
            }
        }

        //return to the bloom surface
        Graphics.surface();
        Core.batch.setProjectionMatrix(Core.camera.combined);
        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        if(any){
            Graphics.shader(Shaders.lava);
            Draw.rect(lavaSurface.texture(), Core.camera.position.x, Core.camera.position.y,
                Core.camera.viewportWidth, -Core.camera.viewportHeight);
            Core.batch.flush();
            Graphics.shader();
            Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            Draw.color();
        }
    }

    private boolean isShaderLavaFloor(Tile tile){
        if(tile.block() != Blocks.air) return false;

        Floor floor = tile.floor();
        return floor.bloomFromDraw
            && floor.cacheLayer == CacheLayer.lava
            && floor.liquidDrop != null
            && floor.liquidDrop.emitLight;
    }
}
