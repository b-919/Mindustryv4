package io.anuke.mindustry.world.blocks.power;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.core.Renderer;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.game.Schematic;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Layer;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.PowerBlock;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;
import io.anuke.ucore.function.Consumer;

import static io.anuke.mindustry.Vars.*;
import io.anuke.mindustry.Vars;

public class PowerNode extends PowerBlock{
    public static final float thicknessScl = 0.7f;
    public static final float flashScl = 0.12f;

    //last distribution block placed
    private static long lastPlaced = -1;
    private static final ObjectSet<PowerGraph> graphs = new ObjectSet<>();
    private static final Array<Tile> tempTiles = new Array<>();

    protected Translator t1 = new Translator();
    protected Translator t2 = new Translator();

    protected float laserRange = 6;
    protected int maxNodes = 3;

    public PowerNode(String name){
        super(name);
        hasBloom = true;
        expanded = true;
        layer = Layer.power;
        powerCapacity = 5f;
        configurable = true;
        consumesPower = false;
        outputsPower = false;

        emitLight = true;
        lightAmplification = 2f;
    }

    @Remote(targets = Loc.both, called = Loc.server, forward = true)
    public static void linkPowerNodes(Player player, Tile tile, Tile other){
        if(tile.entity.power == null || !((PowerNode)tile.block()).linkValid(tile, other)) return;

        TileEntity entity = tile.entity();

        if(!entity.power.links.contains(other.packedPosition())){
            entity.power.links.add(other.packedPosition());
        }

        if(other.getTeamID() == tile.getTeamID()){

            if(!other.entity.power.links.contains(tile.packedPosition())){
                other.entity.power.links.add(tile.packedPosition());
            }
        }

        entity.power.graph.add(other.entity.power.graph);
    }

    @Remote(targets = Loc.both, called = Loc.server, forward = true)
    public static void unlinkPowerNodes(Player player, Tile tile, Tile other){
        if(tile.entity.power == null) return;

        TileEntity entity = tile.entity();

        //clear all graph data first
        PowerGraph tg = entity.power.graph;
        tg.clear();

        entity.power.links.removeValue(other.packedPosition());
        other.entity.power.links.removeValue(tile.packedPosition());

        //reflow from this point, covering all tiles on this side
        tg.reflow(tile);

        if(other.entity.power.graph != tg){
            //create new graph for other end
            PowerGraph og = new PowerGraph();
            //reflow from other end
            og.reflow(other);
        }
    }

    @Override
    public void setBars(){
    }

    @Override
    public Object pointConfig(Object config, Consumer<GridPoint2> transformer){
        if(config instanceof int[]){
            int[] links = (int[])config;
            int[] result = new int[links.length];
            for(int i = 0; i < links.length; i++){
                GridPoint2 point = Schematic.unpackOffset(links[i]);
                transformer.accept(point);
                result[i] = Schematic.packOffset(point.x, point.y);
            }
            return result;
        }

        return config;
    }

    @Override
    public void playerPlaced(Tile tile){
        if(Vars.schematics.hasPendingConfig(tile)) return;

        TileEntity entity = tile.entity();

        if(entity != null && entity.power != null && entity.power.links.size == 0){
            getPotentialLinks(tile, tile.getTeamID(), other -> {
                if(entity.power.links.size < maxNodes && !entity.power.links.contains(other.packedPosition())){
                    Call.linkPowerNodes(null, tile, other);
                }
            });
        }

        if(entity == null || entity.power == null || entity.power.links.size == 0){
            Tile before = world.tile(lastPlaced);
            if(linkValid(tile, before) && before.block() instanceof PowerNode){
                for(Tile near : before.entity.proximity()){
                    if(near.target() == tile){
                        lastPlaced = tile.packedPosition();
                        return;
                    }
                }
                Call.linkPowerNodes(null, tile, before);
            }
        }

        lastPlaced = tile.packedPosition();
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.powerRange, laserRange, StatUnit.blocks);
    }

    @Override
    public void update(Tile tile){
        if (tile.entity == null) {
            return;
        }
        if (tile.entity.power == null) {
            return;
        }
        if (tile.entity.power.graph == null) {
            return;
        }
        tile.entity.power.graph.update();
    }

    @Override
    public boolean onConfigureTileTapped(Tile tile, Tile other){
        TileEntity entity = tile.entity();
        other = other.target();

        Tile result = other;

        if(linkValid(tile, other)){
            if(linked(tile, other)){
                threads.run(() -> Call.unlinkPowerNodes(null, tile, result));
            }else if(entity.power.links.size < maxNodes){
                threads.run(() -> Call.linkPowerNodes(null, tile, result));
            }
            return false;
        }
        return true;
    }

    @Override
    public void drawSelect(Tile tile){
        super.drawSelect(tile);

        Lines.stroke(1f);

        Draw.color(Palette.accent);
        Lines.poly(tile.drawx(), tile.drawy(), 50, laserRange*tilesize);
        Draw.reset();
    }

    @Override
    public void drawConfigure(Tile tile){
        TileEntity entity = tile.entity();

        Draw.color(Palette.accent);

        Lines.stroke(1f);
        Lines.circle(tile.drawx(), tile.drawy(),
                tile.block().size * tilesize / 2f + 1f + Mathf.absin(Timers.time(), 4f, 1f));

        Lines.poly(tile.drawx(), tile.drawy(), 50, laserRange*tilesize);

        for(int x = (int) (tile.x - laserRange); x <= tile.x + laserRange; x++){
            for(int y = (int) (tile.y - laserRange); y <= tile.y + laserRange; y++){
                Tile link = world.tile(x, y);
                if(link != null) link = link.target();

                if(link != tile && linkValid(tile, link, false)){
                    boolean linked = linked(tile, link);
                    Draw.color(linked ? Palette.place : Palette.breakInvalid);

                    Lines.circle(link.drawx(), link.drawy(),
                            link.block().size * tilesize / 2f + 1f + (linked ? 0f : Mathf.absin(Timers.time(), 4f, 1f)));

                    if((entity.power.links.size >= maxNodes || (link.block() instanceof PowerNode && link.entity.power.links.size >= ((PowerNode) link.block()).maxNodes)) && !linked){
                        Draw.color();
                        Draw.rect("cross-" + link.block().size, link.drawx(), link.drawy());
                    }
                }
            }
        }

        Draw.reset();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Lines.stroke(1f);
        Draw.color(Palette.placing);
        Lines.poly(x * tilesize + offset(), y * tilesize + offset(), 50, laserRange*tilesize);
        Draw.reset();
    }

    @Override
    public void drawLayer(Tile tile){
        if(!Settings.getBool("lasers")) return;

        com.badlogic.gdx.math.Matrix4 saved = null;
        if(Renderer.captureReflections){
            saved = Core.batch.getTransformMatrix().cpy();
            com.badlogic.gdx.math.Matrix4 ghost = new com.badlogic.gdx.math.Matrix4();
            ghost.setToTranslation(0f, -2f * Renderer.reflectionGroundGap(), 0f);
            Core.batch.setTransformMatrix(ghost);
        }

        TileEntity entity = tile.entity();

        for(int i = 0; i < entity.power.links.size; i++){
            Tile link = world.tile(entity.power.links.get(i));
            if(linkValid(tile, link) && (!(link.block() instanceof PowerNode)
                || ((tile.block().size > link.block().size) || (tile.block().size == link.block().size && tile.packedPosition() < link.packedPosition())))){
                drawLaser(tile, link);
            }
        }

        Draw.reset();

        if(Renderer.captureReflections && saved != null){
            Core.batch.setTransformMatrix(saved);
        }
    }

    @Override
    public void drawLight(Tile tile){
        if(!Settings.getBool("lasers")) return;

        TileEntity entity = tile.entity();

        for(int i = 0; i < entity.power.links.size; i++){
            Tile link = world.tile(entity.power.links.get(i));
            if(linkValid(tile, link) && (!(link.block() instanceof PowerNode)
                || ((tile.block().size > link.block().size) || (tile.block().size == link.block().size && tile.packedPosition() < link.packedPosition())))){
                drawLineLight(tile, link);
            }
        }

        //center glow
        super.drawLight(tile);
    }

    protected void drawLineLight(Tile tile, Tile target){
        float x1 = tile.drawx(), y1 = tile.drawy(),
                x2 = target.drawx(), y2 = target.drawy();

        float angle1 = Angles.angle(x1, y1, x2, y2);

        t1.trns(angle1, tile.block().size * tilesize / 2f - 1f);
        float lx1 = x1 + t1.x;
        float ly1 = y1 + t1.y;

        t1.trns(angle1 + 180f, target.block().size * tilesize / 2f - 1f);
        float lx2 = x2 + t1.x;
        float ly2 = y2 + t1.y;

        float pulse = Mathf.absin(Timers.time(), 5f, 0.3f) + 0.5f;

        Draw.color(Palette.powerLight, Palette.power, pulse);

        //draw small light circles along the line
        float space = Vector2.dst(lx1, ly1, lx2, ly2);
        int segments = Mathf.ceil(space / (tilesize * 2f));
        segments = Math.max(segments, 1);

        float lightRadius = tilesize * 1.5f;
        for(int i = 0; i <= segments; i++){
            float f = (float)i / segments;
            float px = Mathf.lerp(lx1, lx2, f);
            float py = Mathf.lerp(ly1, ly2, f);
            Draw.alpha(0.15f * pulse);
            Draw.rect("circle", px, py, lightRadius * 2, lightRadius * 2);
        }

        Draw.color();
    }

    @Override
    public void drawBloom(Tile tile){
        if(!Settings.getBool("lasers")) return;

        TileEntity entity = tile.entity();

        for(int i = 0; i < entity.power.links.size; i++){
            Tile link = world.tile(entity.power.links.get(i));
            if(linkValid(tile, link) && (!(link.block() instanceof PowerNode)
                || ((tile.block().size > link.block().size) || (tile.block().size == link.block().size && tile.packedPosition() < link.packedPosition())))){
                drawLaser(tile, link);
            }
        }

        Draw.reset();
    }

    protected boolean linked(Tile tile, Tile other){
        return tile.entity.power.links.contains(other.packedPosition());
    }

    protected boolean linkValid(Tile tile, Tile link){
        return linkValid(tile, link, true);
    }

    protected boolean linkValid(Tile tile, Tile link, boolean checkMaxNodes){
        if(!(tile != link && link != null && link.block().hasPower) || tile.getTeamID() != link.getTeamID()) return false;

        if(link.block() instanceof PowerNode){
            TileEntity oe = link.entity();

            return Vector2.dst(tile.drawx(), tile.drawy(), link.drawx(), link.drawy()) <= Math.max(laserRange * tilesize,
                    ((PowerNode) link.block()).laserRange * tilesize)
                    + (link.block().size - 1) * tilesize / 2f + (tile.block().size - 1) * tilesize / 2f &&
                    (!checkMaxNodes || (oe.power.links.size < ((PowerNode) link.block()).maxNodes || oe.power.links.contains(tile.packedPosition())));
        }else{
            return Vector2.dst(tile.drawx(), tile.drawy(), link.drawx(), link.drawy())
                    <= laserRange * tilesize + (link.block().size - 1) * tilesize;
        }
    }

    protected void getPotentialLinks(Tile tile, byte team, Consumer<Tile> others){
        tempTiles.clear();
        graphs.clear();

        for(Tile near : tile.entity.proximity()){
            if(near.entity != null && near.entity.power != null){
                graphs.add(near.entity.power.graph);
            }
        }

        if(tile.entity != null && tile.entity.power != null){
            graphs.add(tile.entity.power.graph);
        }

        int radius = (int)Math.ceil(laserRange) + 2;
        for(int x = tile.x - radius; x <= tile.x + radius; x++){
            for(int y = tile.y - radius; y <= tile.y + radius; y++){
                Tile other = world.tile(x, y);
                if(other == null) continue;
                other = other.target();

                if(other == tile || other.entity == null || other.entity.power == null) continue;
                if(other.getTeamID() != team) continue;
                if(!linkValid(tile, other)) continue;
                if(isAdjacentTo(tile, other)) continue;
                if(graphs.contains(other.entity.power.graph)) continue;
                if(other.block() instanceof PowerNode && other.entity.power.links.size >= ((PowerNode)other.block()).maxNodes
                    && !other.entity.power.links.contains(tile.packedPosition())) continue;
                if(!tempTiles.contains(other, true)){
                    tempTiles.add(other);
                }
            }
        }

        tempTiles.sort((a, b) -> {
            int type = -Boolean.compare(a.block() instanceof PowerNode, b.block() instanceof PowerNode);
            if(type != 0) return type;
            return Float.compare(Vector2.dst2(a.drawx(), a.drawy(), tile.drawx(), tile.drawy()),
                    Vector2.dst2(b.drawx(), b.drawy(), tile.drawx(), tile.drawy()));
        });

        int count = 0;
        for(Tile other : tempTiles){
            if(count >= maxNodes) break;
            if(graphs.contains(other.entity.power.graph)) continue;
            graphs.add(other.entity.power.graph);
            others.accept(other);
            count++;
        }
    }

    public static void getNodeLinks(Tile tile, Block block, byte team, Consumer<Tile> others){
        tempTiles.clear();
        graphs.clear();

        if(tile.entity != null){
            for(Tile near : tile.entity.proximity()){
                if(near.entity != null && near.entity.power != null){
                    graphs.add(near.entity.power.graph);
                }
            }
        }

        if(tile.entity != null && tile.entity.power != null){
            graphs.add(tile.entity.power.graph);
        }

        float maxRange = 0f;
        for(Block contentBlock : content.blocks()){
            if(contentBlock instanceof PowerNode){
                maxRange = Math.max(maxRange, ((PowerNode)contentBlock).laserRange);
            }
        }

        int radius = (int)Math.ceil(maxRange) + 2;
        for(int x = tile.x - radius; x <= tile.x + radius; x++){
            for(int y = tile.y - radius; y <= tile.y + radius; y++){
                Tile other = world.tile(x, y);
                if(other == null) continue;
                other = other.target();

                if(other == tile || other.entity == null || other.entity.power == null) continue;
                if(other.getTeamID() != team || !(other.block() instanceof PowerNode)) continue;

                PowerNode node = (PowerNode)other.block();
                if(!node.linkValid(other, tile)) continue;
                if(other.entity.power.links.size >= node.maxNodes && !other.entity.power.links.contains(tile.packedPosition())) continue;
                if(isAdjacentTo(tile, block.size, other)) continue;
                if(graphs.contains(other.entity.power.graph)) continue;
                if(!tempTiles.contains(other, true)){
                    tempTiles.add(other);
                }
            }
        }

        tempTiles.sort((a, b) -> {
            int type = -Boolean.compare(a.block() instanceof PowerNode, b.block() instanceof PowerNode);
            if(type != 0) return type;
            return Float.compare(Vector2.dst2(a.drawx(), a.drawy(), tile.drawx(), tile.drawy()),
                    Vector2.dst2(b.drawx(), b.drawy(), tile.drawx(), tile.drawy()));
        });

        for(Tile other : tempTiles){
            if(graphs.contains(other.entity.power.graph)) continue;
            graphs.add(other.entity.power.graph);
            others.accept(other);
        }
    }

    private static boolean isAdjacentTo(Tile tile, Tile other){
        return isAdjacentTo(tile, tile.block().size, other);
    }

    private static boolean isAdjacentTo(Tile tile, int blockSize, Tile other){
        for(com.badlogic.gdx.math.GridPoint2 point : Edges.getEdges(blockSize)){
            Tile near = world.tile(tile.x + point.x, tile.y + point.y);
            if(near != null && near.target() == other){
                return true;
            }
        }
        return false;
    }

    protected void drawLaser(Tile tile, Tile target){
        float x1 = tile.drawx(), y1 = tile.drawy(),
                x2 = target.drawx(), y2 = target.drawy();

        float angle1 = Angles.angle(x1, y1, x2, y2);
        float angle2 = angle1 + 180f;

        t1.trns(angle1, tile.block().size * tilesize / 2f - 1f);
        t2.trns(angle2, target.block().size * tilesize / 2f - 1f);

        x1 += t1.x;
        y1 += t1.y;
        x2 += t2.x;
        y2 += t2.y;

        float space = Vector2.dst(x1, y1, x2, y2);
        float scl = 4f, mag = 2f, tscl = 4f, segscl = 3f;

        int segments = Mathf.ceil(space / segscl);

        Draw.color(Palette.power, Palette.powerLight, Mathf.absin(Timers.time(), 5f, 1f));
        Lines.stroke(1f);

        for(int i = 0; i < segments; i++){
            float f1 = (float)i / segments;
            float f2 = (float)(i+1) / segments;
            t1.trns(angle1 + 90f, Mathf.lerp(Mathf.sin(tile.entity.id * 124f + Timers.time()/tscl + f1 * space, scl, mag), 0f, Math.abs(f1 - 0.5f)*2f));
            t2.trns(angle1 + 90f, Mathf.lerp(Mathf.sin(tile.entity.id * 124f + Timers.time()/tscl + f2 * space, scl, mag), 0f, Math.abs(f2 - 0.5f)*2f));

            Lines.line(x1 + (x2 - x1) * f1 + t1.x, y1 + (y2 - y1) * f1 + t1.y,
                        x1 + (x2 - x1) * f2 + t2.x, y1 + (y2 - y1) * f2 + t2.y);
        }
    }

    @Override
    public TileEntity newEntity(){
        return new PowerNodeEntity();
    }

    public static class PowerNodeEntity extends TileEntity{
        @Override
        public Object config(){
            if(power == null) return null;
            LongArray links = power.links;
            if(links.size == 0) return null;
            int[] relLinks = new int[links.size];
            for(int i = 0; i < links.size; i++){
                Tile other = world.tile(links.get(i));
                if(other != null){
                    relLinks[i] = Schematic.packOffset(other.x - tile.x, other.y - tile.y);
                }else{
                    relLinks[i] = 0;
                }
            }
            return relLinks;
        }

        @Override
        public void configured(Object config){
            if(config instanceof int[]){
                int[] relLinks = (int[])config;
                power.links.clear();
                for(int rel : relLinks){
                    GridPoint2 offset = Schematic.unpackOffset(rel);
                    Tile other = world.tile(tile.x + offset.x, tile.y + offset.y);
                    if(other != null && other.block().hasPower && other.getTeamID() == tile.getTeamID()){
                        power.links.add(other.packedPosition());
                    }
                }
                if(power.graph != null){
                    power.graph.reflow(tile);
                    power.graph.update();
                }
            }
        }
    }
}
