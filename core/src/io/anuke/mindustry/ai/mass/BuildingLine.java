package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Rock;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;
//TODO needs refactor rn is too basic
/** A vein/conveyor line growing out of a hive core. Branches via subdivision or idling.*/
class BuildingLine{
    final Tile core;
    final int direction;
    final int dx, dy, rotation;
    int startX, startY;
    int targetLength;
    int divisions = 0;
    boolean subdivided = false;
    Array<PathTile> tiles = new Array<>();
    Array<Tile> pathBuffer = new Array<>();
    //staggered so all lines don't act on the same tick; each line still acts exactly once per interval
    float timer = Mathf.random(MassAIConfig.LINE_ACTION_INTERVAL);
    float lastSubsectionTimer = 0;
    IntIntMap attempts = new IntIntMap();
    IntSet gaveUp = new IntSet();

    BuildingLine(Tile core, int direction){
        this(core, direction, -1, -1, MassAIConfig.LINE_DIVISIONS);
        this.targetLength = MassAIConfig.LINE_MAX_LENGTH;  // they sometimes ignore this, this works only on big maps
    }

    BuildingLine(Tile core, int direction, int sx, int sy, int divisions){
        this.core = core;
        this.direction = direction;
        this.divisions = divisions;
        this.dx = Geometry.d4[direction].x;
        this.dy = Geometry.d4[direction].y;
        this.rotation = (direction + 2) % 4;
        this.targetLength = Mathf.random(40, 100);

        if(sx == -1){
            int size = core.block().size;
            int offset = (size + 1) / 2;
            this.startX = core.x + dx * offset;
            this.startY = core.y + dy * offset;
        }else{
            this.startX = sx;
            this.startY = sy;
        }

        MassBuilder.markLineTile(world.tile(startX, startY));
    }

    void update(){
        timer += Timers.delta();
        lastSubsectionTimer += Timers.delta();

        if(timer >= MassAIConfig.LINE_ACTION_INTERVAL){
            timer = 0;

            // Try to rebuild broken blocks first
            for(int i = 0; i < tiles.size; i++){
                if(gaveUp.contains(i)) continue;

                if(isBroken(i)){
                    if(tryRebuild(i)){
                        attempts.put(i, 0);
                        return; // limits actions per second
                    }else{
                        int a = attempts.get(i, 0) + 1;
                        attempts.put(i, a);
                        if(a >= MassAIConfig.LINE_REBUILD_ATTEMPTS){
                            gaveUp.add(i);
                            targetLength--;
                        }
                        return;
                    }
                }else{
                    attempts.put(i, 0);
                }
            }

            // if idle for too long, branch or grow the line
            if(lastSubsectionTimer >= MassAIConfig.LINE_IDLE_TIME){
                lastSubsectionTimer = 0;
                if(divisions >= MassAIConfig.MIN_DIVISIONS_FOR_BRANCH && Mathf.chance(MassAIConfig.LINE_BRANCH_CHANCE) && tiles.size > 5){
                    branch();
                }else if(tiles.size < MassAIConfig.LINE_MAX_LENGTH){
                    targetLength = Math.min(MassAIConfig.LINE_MAX_LENGTH, targetLength + MassAIConfig.LINE_GROW_AMOUNT);
                }
            }

            // Increase targetLength if no ores nearby
            if(tiles.size >= targetLength && targetLength < MassAIConfig.LINE_MAX_LENGTH){
                Tile last = tiles.size == 0 ? world.tile(startX, startY) : tiles.peek().tile;
                if(last != null && !MassUtil.hasOresNearby(last, 20)){
                    targetLength = Math.min(MassAIConfig.LINE_MAX_LENGTH, targetLength + MassAIConfig.LINE_GROW_AMOUNT);
                }
            }

            // If no rebuilding is needed, try to expand the line
            if(tiles.size < targetLength && tiles.size < MassAIConfig.LINE_MAX_LENGTH){
                buildNext();
            }
        }
    }

    //Absolutely this shit is broken
    boolean isBroken(int i){
        if(i < 0 || i >= tiles.size) return false;
        PathTile pt = tiles.get(i);
        Tile tile = pt.tile;
        return tile == null || tile.block() != DistributionBlocks.veins || tile.getTeam() != Team.themass || tile.getRotation() != pt.rotation;
    }

    boolean tryRebuild(int i){
        PathTile pt = tiles.get(i);
        Tile tile = pt.tile;
        if(tile != null && tile.block() == Blocks.air){
            tile.setBlock(DistributionBlocks.veins, Team.themass, pt.rotation);
            MassBuilder.markLineTile(tile);
            return true;
        }
        return false;
    }

    void buildNext(){
        if(pathBuffer.size > 0){
            Tile next = pathBuffer.removeIndex(0);
            if((next.block() == Blocks.air || (next.block() instanceof Rock)) && !MassUtil.isNearEnemyCore(next)){
                Tile prev = tiles.size == 0 ? world.tile(startX - dx, startY - dy) : tiles.peek().tile;
                int rot = next.relativeTo(prev.x, prev.y);
                next.setBlock(DistributionBlocks.veins, Team.themass, rot);
                MassBuilder.markLineTile(next);
                tiles.add(new PathTile(next, rot));
            }else{
                pathBuffer.clear();
            }
            return;
        }

        int tx, ty;
        if(tiles.size == 0){
            tx = startX;
            ty = startY;
        }else{
            Tile last = tiles.peek().tile;
            tx = last.x + dx;
            ty = last.y + dy;
        }

        if(tx < 0 || ty < 0 || tx >= world.width() || ty >= world.height()){
            targetLength = tiles.size;
            return;
        }

        Tile tile = world.tile(tx, ty);
        if(tile == null || MassUtil.isNearEnemyCore(tile)){
            targetLength = tiles.size;
            return;
        }

        if(tile.block() == Blocks.air || (tile.block() instanceof Rock)){
            tile.setBlock(DistributionBlocks.veins, Team.themass, rotation);
            MassBuilder.markLineTile(tile);
            tiles.add(new PathTile(tile, rotation));
        }else if(tile.block().solid){
            // Try to pathfind around
            for(int jump = 3; jump <= 7; jump++){
                Tile target = world.tile(tx + dx * jump, ty + dy * jump);
                if(target != null && !target.block().solid && !MassUtil.isNearEnemyCore(target)){
                    Array<Tile> path = MassBuilder.findPath(tiles.size == 0 ? world.tile(startX - dx, startY - dy) : tiles.peek().tile, target);
                    if(path != null){
                        pathBuffer.addAll(path);
                        return;
                    }
                }
            }
            // Subdivide (ERROR tried to divide by zero)
            subdivide();
            targetLength = tiles.size;
        }
    }

    void subdivide(){
        if(tiles.size == 0 || divisions < MassAIConfig.MIN_DIVISIONS_FOR_BRANCH) return;
        if(!Mathf.chance(MassAIConfig.LINE_SUBDIVIDE_CHANCE)){
            subdivided = true;
            return;
        }
        subdivided = true;
        Tile last = tiles.peek().tile;
        int d1 = (direction + 1) % 4;
        int d2 = (direction + 3) % 4;

        int tx1 = last.x + Geometry.d4[d1].x;
        int ty1 = last.y + Geometry.d4[d1].y;
        if(canStartLine(tx1, ty1)){
            activeLinesAdd(new BuildingLine(core, d1, tx1, ty1, Math.max(0, divisions - MassAIConfig.MIN_DIVISIONS_FOR_BRANCH)));
        }

        int tx2 = last.x + Geometry.d4[d2].x;
        int ty2 = last.y + Geometry.d4[d2].y;
        if(canStartLine(tx2, ty2)){
            activeLinesAdd(new BuildingLine(core, d2, tx2, ty2, Math.max(0, divisions - MassAIConfig.MIN_DIVISIONS_FOR_BRANCH)));
        }
    }

    boolean canStartLine(int x, int y){
        Tile t = world.tile(x, y);
        return t != null && t.block() == Blocks.air && !MassUtil.isNearEnemyCore(t);
    }

    void branch(){
        if(tiles.size < 5 || divisions < MassAIConfig.MIN_DIVISIONS_FOR_BRANCH) return;
        divisions -= MassAIConfig.MIN_DIVISIONS_FOR_BRANCH;
        int index = Mathf.random(tiles.size / 2, tiles.size - 1);
        Tile base = tiles.get(index).tile;

        int d1 = (direction + 1) % 4;
        int d2 = (direction + 3) % 4;
        int dir = Mathf.choose(d1, d2);
        int tx = base.x + Geometry.d4[dir].x;
        int ty = base.y + Geometry.d4[dir].y;
        Tile next = world.tile(tx, ty);

        if(next != null && next.block() == Blocks.air && !MassUtil.isNearEnemyCore(next)){
            activeLinesAdd(new BuildingLine(core, dir, tx, ty, Math.max(0, divisions - MassAIConfig.MIN_DIVISIONS_FOR_BRANCH)));
        }
    }

    private void activeLinesAdd(BuildingLine line){
        MassBuilder.activeLines.add(line);
    }

    boolean containsTile(Tile tile){
        if(world.tile(startX, startY) == tile) return true;
        for(PathTile pt : tiles){
            if(pt.tile == tile) return true;
        }
        return false;
    }

    void write(DataOutputStream stream) throws IOException{
        stream.writeLong(core.packedPosition());
        stream.writeInt(direction);
        stream.writeInt(startX);
        stream.writeInt(startY);
        stream.writeInt(targetLength);
        stream.writeInt(divisions);
        stream.writeBoolean(subdivided);
        stream.writeFloat(timer);
        stream.writeFloat(lastSubsectionTimer);

        stream.writeInt(tiles.size);
        for(PathTile pt : tiles){
            pt.write(stream);
        }

        stream.writeInt(attempts.size);
        for(IntIntMap.Entry entry : attempts.entries()){
            stream.writeInt(entry.key);
            stream.writeInt(entry.value);
        }

        stream.writeInt(gaveUp.size);
        IntSet.IntSetIterator it = gaveUp.iterator();
        while(it.hasNext){
            stream.writeInt(it.next());
        }
    }

    static BuildingLine read(DataInputStream stream) throws IOException{
        Tile core = world.tile(stream.readLong());
        int direction = stream.readInt();
        int sx = stream.readInt();
        int sy = stream.readInt();
        //these two were written in this order but read swapped before the refactor
        int targetLength = stream.readInt();
        int divisions = stream.readInt();

        BuildingLine line = new BuildingLine(core, direction, sx, sy, divisions);
        line.targetLength = targetLength;
        line.subdivided = stream.readBoolean();
        line.timer = stream.readFloat();
        line.lastSubsectionTimer = stream.readFloat();

        int tileCount = stream.readInt();
        for(int i = 0; i < tileCount; i++){
            PathTile pt = PathTile.read(stream);
            line.tiles.add(pt);
            MassBuilder.markLineTile(pt.tile);
        }

        int attemptCount = stream.readInt();
        for(int i = 0; i < attemptCount; i++){
            line.attempts.put(stream.readInt(), stream.readInt());
        }

        int gaveUpCount = stream.readInt();
        for(int i = 0; i < gaveUpCount; i++){
            line.gaveUp.add(stream.readInt());
        }

        return line;
    }
}
