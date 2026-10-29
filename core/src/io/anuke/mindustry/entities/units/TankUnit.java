package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.LongMap;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Predict;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Weapon;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class TankUnit extends BaseUnit{
    protected static Translator vec = new Translator();
    private static final int maxOrderPathNodes = 700;
    private static final int orderPathRepathDelay = 30;
    protected static final int movePathMaxNodes = 400;
    protected static final int movePathRepathDelay = 15;

    protected float walkTime;
    protected float stuckTime;
    protected float baseRotation;
    protected float weaponRotation;
    protected float treadTime;
    protected Weapon weapon;
    protected LongArray orderPath = new LongArray();
    protected int orderPathCursor = 0;
    protected int orderPathRepath = 0;
    protected LongArray movePath = new LongArray();
    protected int movePathCursor = 0;
    protected int movePathRepath = 0;
    protected float movePathTargetX, movePathTargetY;

    public final UnitState

    attack = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health < maxHealth() * 0.5f){
                Tile repair = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair));
                Unit healer = Units.getClosest(team, x, y, getType().healRange, u -> u.isHealer() && u != TankUnit.this);
                if(repair != null && distanceTo(repair) < getType().healRange){
                    state.set(retreat);
                    return;
                }else if(healer != null){
                    state.set(retreat);
                    return;
                }
            }

            TileEntity core = getClosestEnemyCore();
            float dst = core == null ? 0 : distanceTo(core);

            if(core != null && dst < getWeapon().getAmmo().getRange() / 1.1f){
                target = core;
            }

            if(target == null && core == null){
                targetClosestEnemyFlag(BlockFlag.producer);
                if(target == null) targetClosestEnemyFlag(BlockFlag.turret);
                if(target == null) targetClosestEnemyFlag(BlockFlag.target);
                if(target != null){
                    moveWithPathfinding(target.getX(), target.getY());
                    return;
                }
            }

            if(dst > getWeapon().getAmmo().getRange() * 0.5f){
                moveToEnemyCore();
            }
        }
    },
    patrol = new UnitState(){
        public void update(){
            TileEntity target = getClosestCore();
            if(target != null){
                if(distanceTo(target) > 400f){
                    moveAwayFromCore();
                }else{
                    patrol();
                }
            }
        }
    },
    retreat = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            Unit healer = Units.getClosest(team, x, y, getType().healRange, u -> u.isHealer() && u != TankUnit.this);
            Tile repair = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair));
            if(health >= maxHealth()){
                clearMovePath();
                if(isCommanded()){
                    onCommand(getCommand());
                }else{
                    state.set(attack);
                }
                return;
            }

            if(retarget() || target == null || (target instanceof TileEntity && (((TileEntity)target).getTile() == null || ((TileEntity)target).getTile().target().block().flags == null || !((TileEntity)target).getTile().target().block().flags.contains(BlockFlag.repair))) || (target instanceof Unit && !((Unit)target).isHealer())){
                clearMovePath();
                if(repair != null) target = repair.entity();
                else if(healer != null) target = healer;
                else target = getClosestCore();
            }

            if(target != null){
                float dst = distanceTo(target);
                if(dst > 7f){
                    if(target instanceof TileEntity && ((TileEntity)target).getTile() != null && ((TileEntity)target).getTile().target().block().flags != null && ((TileEntity)target).getTile().target().block().flags.contains(BlockFlag.repair)){
                        moveWithPathfinding(target.getX(), target.getY());
                    }else if(target instanceof Unit && ((Unit)target).isHealer()){
                        if(dst > type.healRange){
                            moveToHome();
                        }else{
                            moveWithPathfinding(target.getX(), target.getY());
                        }
                    }else{
                        moveToHome();
                    }
                }else{
                    velocity.setZero();
                }
            }else{
                moveToHome();
            }
        }
    },

    hold = new UnitState(){
        public void update(){
            velocity.scl(0.9f);
            if(retarget()){
                targetClosest();
            }
        }
    };

    public TankUnit(){
    }

    @Override
    public void onCommand(UnitCommand command){
        state.set(command == UnitCommand.retreat ? retreat :
                  command == UnitCommand.attack ? attack :
                  command == UnitCommand.patrol ? patrol :
                  null);
    }

    @Override
    public void init(UnitType type, Team team){
        super.init(type, team);
        this.weapon = type.weapon;
    }

    @Override
    public UnitState getStartState(){
        return attack;
    }

    @Override
    public Weapon getWeapon(){
        return weapon;
    }

    @Override
    public float getDrag(){
        return type.drag * 1.5f;
    }

    @Override
    public boolean isRetreating(){
        return state.is(retreat);
    }

    @Override
    public void interpolate(){
        super.interpolate();

        if(interpolator.values.length > 1){
            baseRotation = interpolator.values[1];
        }
        if(interpolator.values.length > 2){
            weaponRotation = interpolator.values[2];
        }
    }

    @Override
    public void move(float x, float y){
        if(Mathf.dst(x, y) > 0.01f){
            baseRotation = Mathf.slerpDelta(baseRotation, Mathf.atan2(x, y), type.baseRotateSpeed);
        }
        super.move(x, y);
    }

    @Override
    public void update(){
        super.update();

        stuckTime = !vec.set(x, y).sub(lastPosition()).isZero(0.0001f) ? 0f : stuckTime + Timers.delta();

        if(stuckTime > 20f){
            clearMovePath();
        }

        if(!velocity.isZero()){
            baseRotation = Mathf.slerpDelta(baseRotation, velocity.angle(), 0.08f);
        }

        if(stuckTime < 1f){
            walkTime += Timers.delta();
        }

        treadTime += velocity.len() * Timers.delta();

        if(Units.invalidateTarget(target, this)){
            rotation = Mathf.slerpDelta(rotation, baseRotation, type.rotatespeed);
        }

        if(!Net.client()){
            updateWeaponRotation();
        }
    }

    protected void updateWeaponRotation(){
        if(!Units.invalidateTarget(target, this)){
            weaponRotation = Mathf.slerpDelta(weaponRotation, angleTo(target), type.rotatespeed);
        }else{
            float targetRotation = velocity.isZero() ? baseRotation : velocity.angle();
            weaponRotation = Mathf.slerpDelta(weaponRotation, targetRotation, type.baseRotateSpeed);
        }
    }

    @Override
    public void behavior(){
        if(health <= health * type.retreatPercent && !isCommanded()){
            setState(retreat);
        }
    }

    @Override
    protected void updateShooting(){
        Weapon weapon = getWeapon();
        if(weapon == null || weapon.getAmmo() == null || target == null) return;

        if(Units.invalidateTarget(target, team, x, y, weapon.getAmmo().getRange())) return;

        Vector2 to = Predict.intercept(this, target, weapon.getAmmo().bullet.speed);
        if(Mathf.angNear(angleTo(target), weaponRotation, type.shootCone)){
            weapon.update(this, to.x, to.y);
        }
    }

    @Override
    public void updateTargeting(){
        super.updateTargeting();

        if(!isRetreating() && Units.invalidateTarget(target, team, x, y, Float.MAX_VALUE)){
            target = null;
        }

        if(getOrderType() != UnitOrderType.attackTarget){
            retarget(this::targetClosest);
        }
    }

    protected void moveTo(float x, float y){
        float angle = angleTo(x, y);
        float curSpeed = velocity.len();
        float moveAngle = angle;

        if(curSpeed > 0.1f){
            float curAngle = velocity.angle();
            float delta = angle - curAngle;
            if(delta > 180f) delta -= 360f;
            if(delta < -180f) delta += 360f;
            moveAngle = curAngle + delta * 0.2f;
        }

        moveAngle = avoidAngle(moveAngle);
        velocity.add(vec.trns(moveAngle, type.speed * Timers.delta()));
    }

    protected void moveToEnemyCore(){
        Tile tile = world.tileWorld(x, y);
        if(tile == null) return;

        TileEntity core = getClosestEnemyCore();
        if(core == null) return;

        Tile targetTile = world.pathfinder.getTargetTile(team, tile);

        if(tile == targetTile){
            float ddx = core.getX() - x, ddy = core.getY() - y;
            boolean nearCore = ddx * ddx + ddy * ddy < (12 * tilesize) * (12 * tilesize);

            if(!nearCore && steerAlongChunkPath(core.getX(), core.getY())){
                return;
            }

            if(!hopClear(core.getX(), core.getY())){
                recenter();
                return;
            }

            float angle = avoidAngle(angleTo(core));
            velocity.add(vec.trns(angle, type.speed * Timers.delta()));
            return;
        }

        float gangle = avoidAngle(angleTo(targetTile));
        velocity.add(vec.trns(gangle, type.speed * Timers.delta()));
    }

    protected void moveToHome(){
        Team enemy = null;
        for(Team team : Vars.state.teams.enemiesOf(team)){
            if(Vars.state.teams.isActive(team)){
                enemy = team;
                break;
            }
        }

        if(enemy == null) return;

        Tile tile = world.tileWorld(x, y);
        if(tile == null) return;
        Tile targetTile = world.pathfinder.getTargetTile(enemy, tile);

        if(tile == targetTile) return;

        velocity.add(vec.trns(angleTo(targetTile), type.speed * Timers.delta()));
    }

    protected void moveAwayFromCore(){
        moveToHome();
    }

    protected void patrol(){
        vec.trns(baseRotation, type.speed * Timers.delta());
        velocity.add(vec.x, vec.y);
        vec.trns(baseRotation, type.hitsizeTile);
        Tile tile = world.tileWorld(x + vec.x, y + vec.y);
        if((tile == null || tile.solid() || tile.floor().drownTime > 0) || stuckTime > 10f){
            baseRotation += Mathf.sign(id % 2 - 0.5f) * Timers.delta() * 3f;
        }
    }

    protected void circle(float circleLength){
        if(target == null) return;

        vec.set(target.getX() - x, target.getY() - y);

        if(vec.len() < circleLength){
            vec.rotate((circleLength - vec.len()) / circleLength * 180f);
        }

        float moveAngle = avoidAngle(vec.angle());
        float len = type.speed * Timers.delta();
        vec.set(Angles.trnsx(moveAngle, len), Angles.trnsy(moveAngle, len));

        velocity.add(vec);
    }

    private float orderArrivalDst(float x, float y){
        Tile t = world.tileWorld(x, y);
        float blockRadius = t != null && t.block() != null && t.block().size > 0 ? t.block().size * tilesize / 2f : 0f;
        return Math.max(type.hitsize, 10f) + blockRadius;
    }

    @Override
    protected boolean updateOrder(){
        if(!hasOrder()){
            clearOrderPath();
            return false;
        }

        float arrivalDst = orderArrivalDst(getOrderX(), getOrderY());

        if(getOrderType() == UnitOrderType.move){
            float dst = distanceTo(getOrderX(), getOrderY());
            if(dst <= arrivalDst){
                clearOrder();
                clearOrderPath();
                velocity.scl(0.5f);
                state.set(hold);
                return false;
            }

            followOrderPath();
            return true;
        }

        if(getOrderType() == UnitOrderType.attackMove){
            if(retarget()){
                targetClosest();
            }

            float dst = distanceTo(getOrderX(), getOrderY());
            if(dst <= arrivalDst){
                clearOrder();
                clearOrderPath();
                state.set(hold);
                return false;
            }

            followOrderPath();
            return true;
        }

        if(getOrderType() == UnitOrderType.attackTarget){
            if(target == null || target.isDead() || target.getTeam() == team){
                clearOrder();
                clearOrderPath();
                state.set(hold);
                return false;
            }

            orderX = target.getX();
            orderY = target.getY();

            followOrderPath();
            return true;
        }

        return false;
    }

    protected void clearOrderPath(){
        orderPath.clear();
        orderPathCursor = 0;
        orderPathRepath = 0;
    }

    protected void clearMovePath(){
        movePath.clear();
        movePathCursor = 0;
        movePathRepath = 0;
    }

    /**Straight-line fallback for an order: only used when the body can actually traverse it.*/
    private void moveOrHoldOrder(Tile goal){
        if(goal != null && hopClear(goal)){
            moveTo(goal.worldx(), goal.worldy());
            return;
        }
        orderPathRepath = 0;
        recenter();
    }

    /**Straight-line fallback for combat movement: only used when the body can traverse it.*/
    private void moveOrHoldMove(float wx, float wy){
        if(hopClear(wx, wy)){
            moveTo(wx, wy);
            return;
        }
        movePathRepath = 0;
        recenter();
    }

    /**Re-aligns on the current tile so the next repath starts from a known, safe spot.*/
    private void recenter(){
        Tile here = world.tileWorld(x, y);
        if(here != null && Mathf.dst(x - here.worldx(), y - here.worldy()) > 1f){
            moveTo(here.worldx(), here.worldy());
        }
    }

    protected void moveWithPathfinding(float targetX, float targetY){
        Tile start = world.tileWorld(x, y);
        Tile goal = world.tileWorld(targetX, targetY);

        if(start == null || goal == null){
            moveOrHoldMove(targetX, targetY);
            return;
        }

        goal = findPassableGoal(goal);
        if(start == goal) return;

        boolean needsRepath = movePathRepath <= 0 || movePath.size == 0 || movePathCursor >= movePath.size ||
                Mathf.dst(movePathTargetX - targetX, movePathTargetY - targetY) > tilesize * 3;

        if(needsRepath){
            buildMovePath(start, goal);
            movePathTargetX = targetX;
            movePathTargetY = targetY;
            movePathRepath = movePathRepathDelay;
        }else{
            movePathRepath--;
        }

        if(movePath.size == 0 || movePathCursor >= movePath.size){
            moveOrHoldMove(targetX, targetY);
            return;
        }

        Tile waypoint = world.tile(movePath.get(movePathCursor));
        if(waypoint == null){
            moveOrHoldMove(targetX, targetY);
            return;
        }

        if(Mathf.dst(x - waypoint.worldx(), y - waypoint.worldy()) <= tilesize * 0.4f
            && (movePathCursor + 1 >= movePath.size || hopClear(world.tile(movePath.get(movePathCursor + 1))))){
            movePathCursor++;
            if(movePathCursor >= movePath.size){
                moveOrHoldMove(targetX, targetY);
                return;
            }
            waypoint = world.tile(movePath.get(movePathCursor));
            if(waypoint == null){
                moveOrHoldMove(targetX, targetY);
                return;
            }
        }

        if(stuckTime > 8f){
            clearMovePath();
            float jitterAngle = Mathf.atan2(targetX - x, targetY - y) + Mathf.range(120f);
            velocity.add(vec.trns(jitterAngle, type.speed * 0.7f * Timers.delta()));
            return;
        }

        if(!hopClear(waypoint)){
            movePathRepath = 0;
            recenter();
            return;
        }

        moveTo(waypoint.worldx(), waypoint.worldy());
    }

    protected void buildMovePath(Tile start, Tile goal){
        movePath.clear();
        movePathCursor = 0;

        if(start == goal) return;

        LongArray open = new LongArray();
        LongMap<Long> cameFrom = new LongMap<Long>();
        LongMap<Integer> gScore = new LongMap<Integer>();
        LongMap<Integer> fScore = new LongMap<Integer>();
        LongMap<Integer> closed = new LongMap<Integer>();

        long startPos = start.packedPosition();
        long goalPos = goal.packedPosition();

        open.add(startPos);
        gScore.put(startPos, 0);
        fScore.put(startPos, (Math.abs(start.x - goal.x) + Math.abs(start.y - goal.y)) * 10);

        int expanded = 0;

        while(open.size > 0 && expanded < movePathMaxNodes){
            int bestIndex = 0;
            long current = open.get(0);
            int bestScore = fScore.get(current, Integer.MAX_VALUE);

            for(int i = 1; i < open.size; i++){
                long node = open.get(i);
                int score = fScore.get(node, Integer.MAX_VALUE);
                if(score < bestScore){
                    bestScore = score;
                    current = node;
                    bestIndex = i;
                }
            }

            open.removeIndex(bestIndex);

            if(current == goalPos){
                reconstructMovePath(cameFrom, current, startPos);
                return;
            }

            closed.put(current, 1);
            expanded++;

            Tile currentTile = world.tile(current);
            if(currentTile == null) continue;

            for(int sx = -1; sx <= 1; sx++){
                for(int sy = -1; sy <= 1; sy++){
                    if(sx == 0 && sy == 0) continue;
                    int nx = currentTile.x + sx, ny = currentTile.y + sy;
                    Tile next = world.tile(nx, ny);
                    if(next == null || !orderPassable(next)) continue;
                    if(sx != 0 && sy != 0 && (world.solid(currentTile.x + sx, currentTile.y) || world.solid(currentTile.x, currentTile.y + sy))){
                        continue;
                    }

                    long nextPos = next.packedPosition();
                    if(closed.get(nextPos, 0) == 1) continue;

                    int currentScore = gScore.get(current, Integer.MAX_VALUE / 8);
                    int stepCost = (sx == 0 || sy == 0 ? 10 : 14) + (int)(next.cost * 2f);
                    int tentativeG = currentScore + stepCost;
                    int known = gScore.get(nextPos, Integer.MAX_VALUE / 8);

                    if(tentativeG < known){
                        cameFrom.put(nextPos, current);
                        gScore.put(nextPos, tentativeG);
                        int heuristic = (Math.abs(next.x - goal.x) + Math.abs(next.y - goal.y)) * 10;
                        fScore.put(nextPos, tentativeG + heuristic);

                        boolean exists = false;
                        for(int i = 0; i < open.size; i++){
                            if(open.get(i) == nextPos){
                                exists = true;
                                break;
                            }
                        }
                        if(!exists) open.add(nextPos);
                    }
                }
            }
        }
    }

    protected void reconstructMovePath(LongMap<Long> cameFrom, long current, long startPos){
        LongArray rev = new LongArray();
        rev.add(current);

        while(cameFrom.containsKey(current)){
            current = cameFrom.get(current, startPos);
            rev.add(current);
            if(current == startPos) break;
        }

        for(int i = rev.size - 2; i >= 0; i--){
            movePath.add(rev.get(i));
        }
        movePathCursor = 0;
    }

    protected void followOrderPath(){
        Tile start = world.tileWorld(x, y);
        Tile goal = world.tileWorld(getOrderX(), getOrderY());

        if(start == null || goal == null){
            moveOrHoldOrder(goal);
            return;
        }

        goal = findPassableGoal(goal);
        if(start == goal){
            return;
        }

        if(orderPathRepath <= 0 || orderPath.size == 0 || orderPathCursor >= orderPath.size){
            buildOrderPath(start, goal);
            orderPathRepath = orderPathRepathDelay;
        }else{
            orderPathRepath--;
        }

        if(orderPath.size == 0 || orderPathCursor >= orderPath.size){
            if(steerAlongChunkPath(goal.worldx(), goal.worldy())){
                return;
            }
            moveOrHoldOrder(goal);
            return;
        }

        Tile waypoint = world.tile(orderPath.get(orderPathCursor));
        if(waypoint == null){
            buildOrderPath(start, goal);
            if(orderPath.size == 0){
                moveOrHoldOrder(goal);
                return;
            }
            waypoint = world.tile(orderPath.get(orderPathCursor));
            if(waypoint == null){
                moveOrHoldOrder(goal);
                return;
            }
        }

        if(Mathf.dst(x - waypoint.worldx(), y - waypoint.worldy()) <= tilesize * 0.4f
            && (orderPathCursor + 1 >= orderPath.size || hopClear(world.tile(orderPath.get(orderPathCursor + 1))))){
            orderPathCursor++;
            if(orderPathCursor >= orderPath.size){
                moveOrHoldOrder(goal);
                return;
            }
            waypoint = world.tile(orderPath.get(orderPathCursor));
            if(waypoint == null){
                moveOrHoldOrder(goal);
                return;
            }
        }

        if(!hopClear(waypoint)){
            orderPathRepath = 0;
            recenter();
            return;
        }

        moveTo(waypoint.worldx(), waypoint.worldy());
    }

    private Tile findPassableGoal(Tile goal){
        if(orderPassable(goal)) return goal;
        for(int r = 1; r <= 3; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.abs(dx) != r && Math.abs(dy) != r) continue;
                    Tile t = world.tile(goal.x + dx, goal.y + dy);
                    if(t != null && orderPassable(t)) return t;
                }
            }
        }
        return goal;
    }

    protected void buildOrderPath(Tile start, Tile goal){
        orderPath.clear();
        orderPathCursor = 0;

        if(start == goal){
            return;
        }

        LongArray open = new LongArray();
        LongMap<Long> cameFrom = new LongMap<Long>();
        LongMap<Integer> gScore = new LongMap<Integer>();
        LongMap<Integer> fScore = new LongMap<Integer>();
        LongMap<Integer> closed = new LongMap<Integer>();

        long startPos = start.packedPosition();
        long goalPos = goal.packedPosition();

        open.add(startPos);
        gScore.put(startPos, 0);
        fScore.put(startPos, (Math.abs(start.x - goal.x) + Math.abs(start.y - goal.y)) * 10);

        int expanded = 0;

        while(open.size > 0 && expanded < maxOrderPathNodes){
            int bestIndex = 0;
            long current = open.get(0);
            int bestScore = fScore.get(current, Integer.MAX_VALUE);

            for(int i = 1; i < open.size; i++){
                long node = open.get(i);
                int score = fScore.get(node, Integer.MAX_VALUE);
                if(score < bestScore){
                    bestScore = score;
                    current = node;
                    bestIndex = i;
                }
            }

            open.removeIndex(bestIndex);

            if(current == goalPos){
                reconstructOrderPath(cameFrom, current, startPos);
                return;
            }

            closed.put(current, 1);
            expanded++;

            Tile currentTile = world.tile(current);
            if(currentTile == null) continue;

            for(int sx = -1; sx <= 1; sx++){
                for(int sy = -1; sy <= 1; sy++){
                    if(sx == 0 && sy == 0) continue;
                    int nx = currentTile.x + sx, ny = currentTile.y + sy;
                    Tile next = world.tile(nx, ny);
                    if(next == null || !orderPassable(next)) continue;
                    if(sx != 0 && sy != 0 && (world.solid(currentTile.x + sx, currentTile.y) || world.solid(currentTile.x, currentTile.y + sy))){
                        continue;
                    }

                    long nextPos = next.packedPosition();
                    if(closed.get(nextPos, 0) == 1) continue;

                    int currentScore = gScore.get(current, Integer.MAX_VALUE / 8);
                    int stepCost = (sx == 0 || sy == 0 ? 10 : 14) + (int)(next.cost * 2f);
                    int tentativeG = currentScore + stepCost;
                    int known = gScore.get(nextPos, Integer.MAX_VALUE / 8);

                    if(tentativeG < known){
                        cameFrom.put(nextPos, current);
                        gScore.put(nextPos, tentativeG);
                        int heuristic = (Math.abs(next.x - goal.x) + Math.abs(next.y - goal.y)) * 10;
                        fScore.put(nextPos, tentativeG + heuristic);

                        boolean exists = false;
                        for(int i = 0; i < open.size; i++){
                            if(open.get(i) == nextPos){
                                exists = true;
                                break;
                            }
                        }
                        if(!exists) open.add(nextPos);
                    }
                }
            }
        }
    }

    protected void reconstructOrderPath(LongMap<Long> cameFrom, long current, long startPos){
        LongArray rev = new LongArray();
        rev.add(current);

        while(cameFrom.containsKey(current)){
            current = cameFrom.get(current, startPos);
            rev.add(current);
            if(current == startPos) break;
        }

        for(int i = rev.size - 2; i >= 0; i--){
            orderPath.add(rev.get(i));
        }
        orderPathCursor = 0;
    }

    protected boolean orderPassable(Tile tile){
        if(tile.solid() && !(tile.breakable() && tile.target().getTeam() != team)) return false;
        return tile.floor().drownTime <= 0f;
    }

    @Override
    public void draw(){
        Draw.alpha(hitTime / hitDuration);

        float speed = velocity.len();
        float speedFrac = Mathf.clamp(speed / type.maxVelocity);

        float trackVibrate = Mathf.sin(walkTime * type.speed * 12f, 2f, 0.5f) * speedFrac;
        float trackJitter = Mathf.sin(walkTime * type.speed * 18f + 1.7f, 1.5f, 0.15f) * speedFrac;

        Floor floor = getFloorOn();

        // Soft shadow
        if(!io.anuke.mindustry.core.Renderer.captureReflections){
            Draw.color(0f, 0f, 0f, 0.35f);
            Draw.rect(type.iconRegion, x, y - 1.5f);
            Draw.color(Color.WHITE);
        }

        // Treads
        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, 0.5f);
        }

        for(int i : Mathf.signs){
            Draw.rect(type.treadRegion,
                    x + Angles.trnsx(baseRotation, trackJitter * i, trackVibrate),
                    y + Angles.trnsy(baseRotation, trackJitter * i, trackVibrate),
                    16 * i, 24f, baseRotation - 90);
        }

        if(floor.isLiquid){
            Draw.tint(Color.WHITE, floor.liquidColor, drownTime * 0.4f);
        }else{
            Draw.tint(Color.WHITE);
        }

        // Hull base plate
        Draw.rect(type.baseRegion, x, y, baseRotation - 90);

        // Body at baseRotation (tank hull faces track direction)
        Draw.rect(type.region, x, y, baseRotation - 90);

        // Turret
        float trY = -weapon.getRecoil(this, true);
        Draw.rect(weapon.equipRegion,
                x + Angles.trnsx(rotation - 90, weapon.width, trY),
                y + Angles.trnsy(rotation - 90, weapon.width, trY),
                weaponRotation - 90);

        // Items
        drawItems();

        Draw.alpha(1f);
    }

    @Override
    protected void drawItems(){
        float backTrns = 4f, itemSize = 5f;
        if(inventory.hasItem()){
            io.anuke.mindustry.type.ItemStack stack = inventory.getItem();
            int stored = Mathf.clamp(stack.amount / 6, 1, 8);

            for(int i = 0; i < stored; i++){
                float angT = i == 0 ? 0 : Mathf.randomSeedRange(i + 2, 60f);
                float lenT = i == 0 ? 0 : Mathf.randomSeedRange(i + 3, 1f) - 1f;
                Draw.rect(stack.item.region,
                    x + Angles.trnsx(baseRotation + 180f + angT, backTrns + lenT),
                    y + Angles.trnsy(baseRotation + 180f + angT, backTrns + lenT),
                    itemSize, itemSize, baseRotation);
            }
        }
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeByte(weapon.id);
    }

    @Override
    public void read(DataInput data, long time) throws IOException{
        super.read(data, time);
        weapon = content.getByID(ContentType.weapon, data.readByte() & 0xFF);
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        stream.writeByte(weapon.id);
        super.writeSave(stream);
    }

    @Override
    public void readSave(DataInput stream) throws IOException{
        weapon = content.getByID(ContentType.weapon, stream.readByte() & 0xFF);
        super.readSave(stream);
    }
}
