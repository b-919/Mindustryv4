package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Rock;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;


class BuildingLine{
    final Tile core;
    int direction;
    int dx, dy;
    int startX, startY;
    int targetLength;
    int divisions = 0;
    Array<PathTile> tiles = new Array<>();
    Array<Tile> pathBuffer = new Array<>();
    //staggered so all lines don't act on the same tick; each line still acts exactly once per interval
    float timer = Mathf.random(MassAIConfig.LINE_ACTION_INTERVAL);
    float lastSubsectionTimer = 0;
    IntIntMap attempts = new IntIntMap();
    IntSet gaveUp = new IntSet();
    int lastDir;
    int turnBias = 0;
    int tilesSinceBranch = 0;
    int blockedTurns = 0;
    int straightCount = 0;
    boolean dead = false;
    float targetX, targetY;
    boolean targetValid = false;
    int attractTimer = 0;

    BuildingLine(Tile core, int direction){
        this(core, direction, -1, -1, MassAIConfig.LINE_DIVISIONS);
        this.targetLength = MassAIConfig.LINE_MAX_LENGTH;  // they sometimes ignore this, this works only on big maps
        this.turnBias = Mathf.choose(-1, 1);
    }

    BuildingLine(Tile core, int direction, int sx, int sy, int divisions){
        this.core = core;
        this.direction = direction;
        this.divisions = divisions;
        this.lastDir = direction;
        this.dx = Geometry.d4[direction].x;
        this.dy = Geometry.d4[direction].y;
        this.targetLength = Mathf.random(20, 60);

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

            if(dead) return;
            if(tiles.size >= 3 && Mathf.chance(MassAIConfig.VEIN_MERGE_CHANCE)){
                Tile from = tip();
                if(from != null && adjacentForeignVein(from)){
                    dead = true;
                    return;
                }
            }

            if(lastSubsectionTimer >= MassAIConfig.LINE_IDLE_TIME){
                lastSubsectionTimer = 0;
                if(tiles.size < MassAIConfig.LINE_MAX_LENGTH){
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
                growStep();
            }
        }
    }

    Tile tip(){
        return tiles.size == 0 ? world.tile(startX, startY) : tiles.peek().tile;
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

    void growStep(){
        if(tiles.size == 0){
            Tile start = world.tile(startX, startY);
            if(start == null || (start.block() != Blocks.air && !(start.block() instanceof Rock))){
                dead = true;
                return;
            }
            int rot = (lastDir + 2) % 4;
            start.setBlock(DistributionBlocks.veins, Team.themass, rot);
            MassBuilder.markLineTile(start);
            tiles.add(new PathTile(start, rot));
            return;
        }

        if(pathBuffer.size > 0){
            Tile next = pathBuffer.removeIndex(0);
            if((next.block() == Blocks.air || (next.block() instanceof Rock)) && !MassUtil.isNearEnemyCore(next) && !MassBuilder.isMassVein(next)){
                Tile prev = tiles.size == 0 ? world.tile(startX - dx, startY - dy) : tiles.peek().tile;
                int rot = next.relativeTo(prev.x, prev.y);
                next.setBlock(DistributionBlocks.veins, Team.themass, rot);
                MassBuilder.markLineTile(next);
                tiles.add(new PathTile(next, rot));
                tilesSinceBranch++;
                straightCount = 0;
            }else{
                pathBuffer.clear();
            }
            return;
        }

        ensureTarget();

        Tile from = tip();
        if(from == null){
            dead = true;
            return;
        }

        int reversal = (lastDir + 2) % 4;
        int best = -1;
        float bestScore = -Float.MAX_VALUE;

        for(int d = 0; d < 4; d++){
            if(d == reversal) continue;
            int nx = from.x + Geometry.d4[d].x;
            int ny = from.y + Geometry.d4[d].y;
            Tile nt = world.tile(nx, ny);
            if(nt == null || MassUtil.isNearEnemyCore(nt)) continue;
            if(MassBuilder.isMassVein(nt)) continue;
            if(nt.block() != Blocks.air && !(nt.block() instanceof Rock)) continue;

            float score = 0;
            score += (distToTarget(from.x, from.y) - distToTarget(nx, ny)) * MassAIConfig.VEIN_ATTRACT_WEIGHT;

            if(MassUtil.isTargetOreFloor(nt)){
                score -= MassAIConfig.VEIN_ORE_AVOID_PENALTY;
            }

            if(d == lastDir){
                score += 1f;
                if(straightCount >= MassAIConfig.VEIN_STRAIGHT_ATTEMPT){
                    score -= MassAIConfig.VEIN_STRAIGHT_PENALTY;
                }
            }else{
                score -= MassAIConfig.VEIN_TURN_PENALTY;
                if(turnBias != 0 && Mathf.mod(d - lastDir, 4) == turnBias){
                    score += 0.5f;
                }
            }

            score -= spacingPenalty(nx, ny, from);
            score += Mathf.random(-MassAIConfig.VEIN_NOISE, MassAIConfig.VEIN_NOISE);

            if(score > bestScore){
                bestScore = score;
                best = d;
            }
        }

        if(best == -1){
            onBlocked();
            return;
        }

        int nx = from.x + Geometry.d4[best].x;
        int ny = from.y + Geometry.d4[best].y;
        Tile nt = world.tile(nx, ny);
        if(nt == null){
            dead = true;
            return;
        }

        int rot = (best + 2) % 4;
        nt.setBlock(DistributionBlocks.veins, Team.themass, rot);
        MassBuilder.markLineTile(nt);
        tiles.add(new PathTile(nt, rot));
        straightCount = (best == lastDir) ? straightCount + 1 : 0;
        lastDir = best;
        tilesSinceBranch++;

        if(tilesSinceBranch >= MassAIConfig.VEIN_BRANCH_AFTER && canBranch() && Mathf.chance(MassAIConfig.VEIN_BRANCH_CHANCE)){
            tilesSinceBranch = 0;
            spawnBranch();
        }
        if(Mathf.chance(0.06f)){
            turnBias = Mathf.choose(-1, 1);
        }
    }
    void ensureTarget(){
        if(attractTimer > 0){
            attractTimer--;
            if(targetValid) return;
        }
        attractTimer = MassAIConfig.VEIN_ATTRACT_REFRESH;

        Tile from = tip();
        if(from == null) return;

        if(targetValid && Mathf.dst(from.x - targetX, from.y - targetY) < 5){
            targetValid = false;
        }

        Tile t = attractorFor(from);
        if(t != null && Mathf.dst(t.x - from.x, t.y - from.y) > 6){
            targetX = t.x;
            targetY = t.y;
            targetValid = true;
        }else{
            targetX = from.x + Geometry.d4[lastDir].x * 50f;
            targetY = from.y + Geometry.d4[lastDir].y * 50f;
            targetValid = true;
        }
    }
    Tile attractorFor(Tile from){
        if(MassSquads.evolutionLevel() < 2){
            return MassUtil.closestOreAnchor(from);
        }else{
            Tile inf = MassUtil.sampleInfectedNear(from, MassAIConfig.VEIN_ATTRACT_MAX_ANCHORS);
            if(inf != null) return inf;
            Tile ec = MassUtil.nearestEnemyCoreTile(from);
            if(ec != null) return ec;
            return MassUtil.closestOreAnchor(from);
        }
    }

    float distToTarget(int x, int y){
        return Mathf.dst(x - targetX, y - targetY);
    }

    float spacingPenalty(int nx, int ny, Tile from){
        float penalty = 0;
        for(int i = 0; i < 8; i++){
            Tile t = world.tile(nx + Geometry.d8[i].x, ny + Geometry.d8[i].y);
            if(t == null || t == from || !MassBuilder.isMassVein(t)) continue;
            penalty += MassAIConfig.VEIN_SPACING_PENALTY;
        }
        return penalty;
    }
    boolean adjacentForeignVein(Tile from){
        for(int i = 0; i < 4; i++){
            int d = Mathf.mod(lastDir + i, 4);
            if(d == (lastDir + 2) % 4) continue;
            Tile t = world.tile(from.x + Geometry.d4[d].x, from.y + Geometry.d4[d].y);
            if(t != null && MassBuilder.isMassVein(t) && !containsTile(t)) return true;
        }
        return false;
    }
    void onBlocked(){
        Tile from = tip();
        if(from == null){
            dead = true;
            return;
        }

        for(int jump = 3; jump <= 7; jump++){
            Tile target = world.tile(from.x + Geometry.d4[lastDir].x * jump, from.y + Geometry.d4[lastDir].y * jump);
            if(target != null && !target.block().solid && !MassUtil.isNearEnemyCore(target) && !MassUtil.isTargetOreFloor(target)){
                Array<Tile> path = MassBuilder.findPath(from, target);
                if(path != null){
                    pathBuffer.addAll(path);
                    return;
                }
            }
        }

        blockedTurns++;
        if(blockedTurns >= MassAIConfig.VEIN_MAX_BLOCKED_TURNS){
            dead = true;
            return;
        }
        lastDir = Mathf.mod(lastDir + (Mathf.chance(0.5f) ? 1 : 3), 4);
        straightCount = 0;
        targetValid = false;
    }

    boolean canBranch(){
        return tiles.size >= MassAIConfig.VEIN_BRANCH_AFTER && divisions >= MassAIConfig.MIN_DIVISIONS_FOR_BRANCH
            && tiles.size < MassAIConfig.LINE_MAX_LENGTH;
    }
    void spawnBranch(){
        int index = Mathf.random(tiles.size / 2, tiles.size - 1);
        PathTile pt = tiles.get(index);
        Tile base = pt.tile;
        int trunk = (pt.rotation + 2) % 4;
        int side = Mathf.chance(0.5f) ? 1 : 3;
        int dir = Mathf.mod(trunk + side, 4);
        int bx = base.x + Geometry.d4[dir].x;
        int by = base.y + Geometry.d4[dir].y;
        Tile b = world.tile(bx, by);
        if(b != null && b.block() == Blocks.air && !MassUtil.isNearEnemyCore(b) && !MassBuilder.isMassVein(b)){
            divisions -= MassAIConfig.VEIN_BRANCH_COST;
            BuildingLine child = new BuildingLine(core, dir, bx, by, Math.max(0, divisions));
            child.turnBias = Mathf.choose(-1, 1);
            activeLinesAdd(child);
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
        stream.writeFloat(timer);
        stream.writeFloat(lastSubsectionTimer);
        stream.writeInt(lastDir);
        stream.writeInt(turnBias);
        stream.writeInt(tilesSinceBranch);
        stream.writeInt(blockedTurns);
        stream.writeInt(straightCount);
        stream.writeBoolean(dead);
        stream.writeBoolean(targetValid);
        stream.writeFloat(targetX);
        stream.writeFloat(targetY);
        stream.writeInt(attractTimer);

        stream.writeInt(tiles.size);
        for(PathTile pt : tiles){
            pt.write(stream);
        }

        stream.writeInt(pathBuffer.size);
        for(Tile t : pathBuffer){
            stream.writeLong(t.packedPosition());
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
        int targetLength = stream.readInt();
        int divisions = stream.readInt();

        BuildingLine line = new BuildingLine(core, direction, sx, sy, divisions);
        line.targetLength = targetLength;
        line.timer = stream.readFloat();
        line.lastSubsectionTimer = stream.readFloat();
        //lastDir must be restored before the start tile is marked, so a stranded read strand resumes correctly
        line.lastDir = stream.readInt();
        line.turnBias = stream.readInt();
        line.tilesSinceBranch = stream.readInt();
        line.blockedTurns = stream.readInt();
        line.straightCount = stream.readInt();
        line.dead = stream.readBoolean();
        line.targetValid = stream.readBoolean();
        line.targetX = stream.readFloat();
        line.targetY = stream.readFloat();
        line.attractTimer = stream.readInt();

        int tileCount = stream.readInt();
        for(int i = 0; i < tileCount; i++){
            PathTile pt = PathTile.read(stream);
            line.tiles.add(pt);
            MassBuilder.markLineTile(pt.tile);
        }

        int bufferCount = stream.readInt();
        for(int i = 0; i < bufferCount; i++){
            line.pathBuffer.add(world.tile(stream.readLong()));
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