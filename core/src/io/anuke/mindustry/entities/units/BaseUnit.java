package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.LongArray;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.fx.ExplosionFx;
import io.anuke.mindustry.entities.Damage;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.effect.ScorchDecal;
import io.anuke.mindustry.entities.traits.ShooterTrait;
import io.anuke.mindustry.entities.traits.SpawnerTrait;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.mindustry.entities.units.ai.AIController;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.Weapon;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.units.CommandCenter.CommandCenterEntity;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;
import static io.anuke.mindustry.sounds.Sounds.unitExplode;

/**Base class for AI units.*/
public abstract class BaseUnit extends Unit implements ShooterTrait{

    protected static int timerIndex = 0;

    protected static final int timerTarget = timerIndex++;
    protected static final int timerShootLeft = timerIndex++;
    protected static final int timerShootRight = timerIndex++;

    public UnitType type;
    protected Timer timer = new Timer(10);
    protected StateMachine state = new StateMachine();
    protected TargetTrait target;
    protected AIController controller;
    protected UnitOrderType orderType = UnitOrderType.none;
    protected float orderX, orderY;

    protected boolean isWave;
    protected Squad squad;
    protected long spawner = -1;

    /**internal constructor used for deserialization, DO NOT USE*/
    public BaseUnit(){
    }

    @Remote(called = Loc.server)
    public static void onUnitDeath(BaseUnit unit){
        if(unit == null) return;

        if(Net.server() || !Net.active()){
            UnitDrops.dropItems(unit);
        }

        float explosiveness = 2f + (unit.inventory.hasItem() ? unit.inventory.getItem().item.explosiveness * unit.inventory.getItem().amount : 0f);
        float flammability = (unit.inventory.hasItem() ? unit.inventory.getItem().item.flammability * unit.inventory.getItem().amount : 0f);
        Damage.dynamicExplosion(unit.x, unit.y, flammability, explosiveness, 0f, unit.getSize() / 2f, Palette.darkFlame);

        unit.onSuperDeath();
        //visual only.
        if(Net.client()){
            Tile tile = world.tile(unit.spawner);
            if(tile != null){
                tile.block().unitRemoved(tile, unit);
            }

            unit.spawner = -1;
        }

        if(unit.getType().living){
            ScorchDecal.create(unit.x, unit.y, Color.valueOf("2b0000"));
        }else{
            ScorchDecal.create(unit.x, unit.y);
        }
        Effects.effect(ExplosionFx.explosion, unit);
        Effects.shake(2f, 2f, unit);
        Sound sound = unitExplode;
        if(Vars.soundController != null && sound != null){
            Vars.soundController.at(sound, unit.x, unit.y, 1f, 0.7f);}
        //must run afterwards so the unit's group is not null
        threads.runDelay(unit::remove);
    }

    @Override
    public float getDrag(){
        return type.drag;
    }

    /**Called when a command is recieved from the command center.*/
    public abstract void onCommand(UnitCommand command);

    /**Initialize the type and team of this unit. Only call once!*/
    public void init(UnitType type, Team team){
        if(this.type != null) throw new RuntimeException("This unit is already initialized!");

        this.type = type;
        this.team = team;
        this.isPlayerControllable = type.playerControllable;
        this.isRTSAIControllable = type.rtsAIControllable;
    }

    public boolean isCommanded(){
        return !isWave && world.indexer.getAllied(team, BlockFlag.comandCenter).size != 0 && world.indexer.getAllied(team, BlockFlag.comandCenter).first().entity instanceof CommandCenterEntity;
    }

    public UnitCommand getCommand(){
        if(isCommanded()){
            return world.indexer.getAllied(team, BlockFlag.comandCenter).first().<CommandCenterEntity>entity().command;
        }
        return null;
    }

    public UnitType getType(){
        return type;
    }

    public void setDirectTarget(TargetTrait target){
        this.target = target;
    }

    public UnitOrderType getOrderType(){
        return orderType;
    }

    public boolean hasOrder(){
        return orderType != UnitOrderType.none;
    }

    public void clearOrder(){
        orderType = UnitOrderType.none;
    }

    public void orderMove(float x, float y){
        orderType = UnitOrderType.move;
        orderX = x;
        orderY = y;
    }

    public void orderAttackMove(float x, float y){
        orderType = UnitOrderType.attackMove;
        orderX = x;
        orderY = y;
    }

    public void orderAttackTarget(float x, float y){
        orderType = UnitOrderType.attackTarget;
        orderX = x;
        orderY = y;
    }

    public TargetTrait getTarget(){
        return target;
    }

    public float getOrderX(){
        return orderX;
    }

    public float getOrderY(){
        return orderY;
    }

    public Tile getSpawner(){
        return world.tile(spawner);
    }

    public void setSpawner(Tile tile){
        this.spawner = tile.packedPosition();
    }

    public void setIntSpawner(long pos){
        this.spawner = pos;
    }

    /**Sets this to a 'wave' unit, which means it has slightly different AI and will not run out of ammo.*/
    public void setWave(){
        isWave = true;
    }

    public void setSquad(Squad squad){
        this.squad = squad;
        squad.units++;
    }

    public void rotate(float angle){
        rotation = Mathf.slerpDelta(rotation, angle, type.rotatespeed);
    }

    public boolean targetHasFlag(BlockFlag flag){
        return target instanceof TileEntity && ((TileEntity) target).tile.block().flags != null &&
            ((TileEntity) target).tile.block().flags.contains(flag);
    }

    public void updateRespawning(){
        if(spawner == -1) return;

        Tile tile = world.tile(spawner);
        if(tile != null && tile.entity != null){
            if(tile.entity instanceof SpawnerTrait){
                ((SpawnerTrait) tile.entity).updateSpawning(this);
            }
        }else{
            spawner = -1;
        }
    }

    public void setState(UnitState state){
        this.state.set(state);
    }

    public void retarget(Runnable run){
        if(timer.get(timerTarget, 20)){
            run.run();
        }
    }
    public boolean retarget(){
        return timer.get(timerTarget, 20);
    }

    /**Only runs when the unit has a target.*/
    public void behavior(){

    }

    /**
     * Fires at the current target whenever it is valid, in weapon range and aimed at.
     * Runs every tick, independent of AI states, orders and movement (modern-style
     * autonomous weapon targeting). Only aims and controls mounts; {@link #updateWeaponMounts()}
     * ticks them exactly once per frame.
     */
    protected void updateShooting(){

    }

    /**True when a valid enemy target is inside weapon range; movement code must not fight body rotation while this is set.*/
    protected boolean isAiming(){
        return target != null && getWeaponMountAmmo() != null
                && !Units.invalidateTarget(target, team, x, y, getWeaponMountRange());
    }

    @Override
    public Weapon[] getWeaponDefinitions(){
        return type == null ? new Weapon[0] : type.allWeapons(getWeapon());
    }

    @Override
    public float getShootCone(){
        return type == null ? super.getShootCone() : type.shootCone;
    }

    @Override
    public boolean rotatesWeapons(){
        return type != null && type.rotateWeapon;
    }

    public boolean isRetreating(){
        return false;
    }

    public void updateTargeting(){
        if(target == null || (target instanceof Unit && (target.isDead() || (!isRetreating() && target.getTeam() == team)))
        || (target instanceof TileEntity && ((TileEntity) target).tile.entity == null)){
            target = null;
        }
    }

    public void targetClosestAllyFlag(BlockFlag flag){
        Tile target = Geometry.findClosest(x, y, world.indexer.getAllied(team, flag));
        if(target != null) this.target = target.entity;
    }

    public void targetClosestEnemyFlag(BlockFlag flag){
        Tile target = Geometry.findClosest(x, y, world.indexer.getEnemy(team, flag));
        if(target != null) this.target = target.entity;
    }

    public void targetClosest(){
        TargetTrait next = Units.getClosestTarget(team, x, y, Math.max(getWeaponMountRange(), type.range), u -> type.targetAir || !u.isFlying());
        if(next != null) target = next;
    }

    public TileEntity getClosestEnemyCore(){

        for(Team enemy : Vars.state.teams.enemiesOf(team)){
            Tile tile = Geometry.findClosest(x, y, Vars.state.teams.get(enemy).cores);
            if(tile != null){
                return tile.entity;
            }
        }

        return null;
    }

    protected transient LongArray chunkPath;
    protected transient int chunkPathIndex;
    protected transient float chunkPathGoalX, chunkPathGoalY;
    protected transient int chunkPathCooldown;
    protected transient boolean chunkPathFallback;
    protected transient int chunkPathLayer = -1;
    protected transient float steerGoalX = Float.NaN, steerGoalY = Float.NaN;
    protected transient int chunkPathRefresh = 0;
    protected transient long pathEditStamp = -1;
    protected transient long chunkStuckTarget = 0;
    protected transient float chunkStuckProg = Float.NaN;
    protected transient int chunkStuck = 0;
    protected transient int chunkSpliceFail = 0;
    protected transient boolean chunkPathFine = false;
    private final transient Translator chunkVec = new Translator();
    private static final float[] avoidOffsets = {40f, -40f, 80f, -80f, 120f, -120f, 160f, -160f};

    /** Returns true when a ground unit moving along this angle would hit solid terrain or deeper drowning liquid. */
    protected boolean blockedAtAngle(float angle){
        float look = Math.max(type.hitsize * 0.75f + 5f, 12f);
        float half = Math.max(type.hitsize * 0.5f - 1.5f, 3f);
        float step = Math.max(3f, look * 0.3f);

        for(float d = step; d <= look + 0.01f; d += step){
            float lx = x + Angles.trnsx(angle, d);
            float ly = y + Angles.trnsy(angle, d);
            Tile t = world.tileWorld(lx, ly);
            if(t == null || t.solid()) return true;
        }
        for(int s = -1; s <= 1; s += 2){
            float px = x + Angles.trnsx(angle, look * 0.5f) + Angles.trnsx(angle + 90f, s * half);
            float py = y + Angles.trnsy(angle, look * 0.5f) + Angles.trnsy(angle + 90f, s * half);
            Tile t = world.tileWorld(px, py);
            if(t == null || t.solid()) return true;
        }

        if(!isFlying()){
            Tile here = world.tileWorld(x, y);
            float curDrown = here == null ? 0f : here.floor().drownTime;
            Tile probe = world.tileWorld(x + Angles.trnsx(angle, look), y + Angles.trnsy(angle, look));
            if(probe != null && probe.floor().drownTime > curDrown + 0.01f) return true;
        }
        return false;
    }

    /**
     * Adjusts a movement angle to slide around obstacles instead of grinding into
     * walls/corners. Flying units pass through unchanged.
     */
    protected float avoidAngle(float angle){
        if(isFlying() || !blockedAtAngle(angle)) return angle;

        for(float off : avoidOffsets){
            if(!blockedAtAngle(angle + off)) return angle + off;
        }
        return angle + 180f;
    }

    /**
     * Tries, in order: the HPA chunk graph (open world only), fine window A*, then a
     * fallback corridor for unreachable/too-far targets. Cooldowns differ per layer so
     * repeated queries stay cheap.
     */
    private boolean queryPath(float gx, float gy){
        chunkPath = null;
        chunkPathIndex = 0;
        chunkPathFallback = false;
        chunkPathLayer = -1;
        chunkPathFine = false;
        resetChunkSteer();

        if(world.isOpenWorld()){
            chunkPath = world.pathfinder.findChunkPath(x, y, gx, gy);
            if(chunkPath != null && chunkPath.size > 0){
                LongArray refined = refineChunkCorridor(chunkPath, gx, gy);
                if(refined != null && refined.size > 1){
                    chunkPath = refined;
                    chunkPathFine = true;
                }
                chunkPathGoalX = gx;
                chunkPathGoalY = gy;
                chunkPathCooldown = 12;
                chunkPathLayer = 0;
                return true;
            }
        }

        chunkPath = world.pathfinder.findUnitPath(team, x, y, gx, gy);
        if(chunkPath != null && chunkPath.size > 0){
            chunkPathGoalX = gx;
            chunkPathGoalY = gy;
            chunkPathCooldown = 20;
            chunkPathLayer = 1;
            return true;
        }

        chunkPath = world.pathfinder.findFallbackWaypoint(team, x, y, gx, gy);
        if(chunkPath != null && chunkPath.size > 0){
            chunkPathGoalX = gx;
            chunkPathGoalY = gy;
            chunkPathFallback = true;
            chunkPathCooldown = 15;
            chunkPathLayer = 2;
            return true;
        }
        return false;
    }

    /**
     * Refines a coarse door-by-door corridor into an actual walkable fine-grid polyline by A*ing
     * leg by leg (unit -> door -> door -> ... -> goal) while all of it lies inside the loaded window.
     * Steering then follows slopes and ramps for real instead of straight-lining between border
     * doors - the straight hops cut cliff faces and are the source of the corner-wedging and the
     * constant re-route churn. Returns null whenever any single leg cannot reach its endpoint
     * (goal out of window / unloaded frontier); the caller keeps the coarse corridor, whose reactive
     * stuck logic then still stands in until a later re-query manages to refine.
     */
    private LongArray refineChunkCorridor(LongArray coarse, float gx, float gy){
        LongArray out = new LongArray();
        float curX = x, curY = y;
        for(int i = 0; i < coarse.size; i++){
            long wp = coarse.items[i];
            float wx = (int)(wp >> 32) * tilesize + tilesize / 2f;
            float wy = (int)wp * tilesize + tilesize / 2f;
            LongArray leg = world.pathfinder.findUnitPath(team, curX, curY, wx, wy);
            if(leg == null || leg.size == 0) return null;
            for(int j = 0; j < leg.size; j++){
                if(leg.items[j] == wp) continue;
                out.add(leg.items[j]);
            }
            long last = leg.peek();
            curX = (int)(last >> 32) * tilesize + tilesize / 2f;
            curY = (int)last * tilesize + tilesize / 2f;
        }
        LongArray tail = world.pathfinder.findUnitPath(team, curX, curY, gx, gy);
        if(tail == null || tail.size == 0) return null;
        for(int j = 0; j < tail.size; j++){
            out.add(tail.items[j]);
        }
        return out.size > 1 ? out : null;
    }

    /**
     * Steers along a cached waypoint path toward (gx, gy).
     * If a real path is already active it is kept until fully consumed (mirroring v7's
     * persistent-route behavior) instead of being thrown away whenever the final goal sits
     * far outside the fine window; once consumed, the route is re-queried immediately from
     * the new position so units never fall back to straight-line walking except when no
     * route exists at all. The fallback corridor also re-arms instantly on each hop so units
     * keep contouring around big obstacle clusters. Returns false only when no path layer
     * produced a route (caller may then steering directly).
     */
    /** Resets the corner-steering trackers whenever the active route is replaced or dropped. */
    private void resetChunkSteer(){
        chunkStuckTarget = 0;
        chunkStuckProg = Float.NaN;
        chunkStuck = 0;
        chunkSpliceFail = 0;
    }

    protected boolean steerAlongChunkPath(float gx, float gy){
        steerGoalX = gx;
        steerGoalY = gy;
        if(chunkPathCooldown > 0) chunkPathCooldown--;

        //target moved far from the current route's goal: never keep marching toward the old goal
        //(e.g. the unit retargeted to a different core or issued a new order). Drop now so the
        //route re-queries towards the new target this same frame instead of driving to the old one.
        if(chunkPath != null && chunkPath.size > 0
                && Mathf.dst(chunkPathGoalX - gx, chunkPathGoalY - gy) > 8f * tilesize){
            chunkPath = null;
            chunkPathIndex = 0;
            chunkPathCooldown = 0;
            resetChunkSteer();
        }

        //world changed since the route was built: only drop it when the edit actually sits on the
        //route's remaining waypoint(s). Dropping every route on ANY tile edit (mining, tree cutting,
        //building anywhere in the world) makes HPA throw brand new paths every frame and the unit
        //visibly hop between different polylines mid-journey. A wall placed off-route is instead
        //handled by the stuck detector below, which stitches a fine local detour only when needed.
        long stamp = world.pathfinder.editStamp;
        if(stamp != pathEditStamp && chunkPath != null && chunkPathIndex < chunkPath.size){
            pathEditStamp = stamp;
            Tile head = world.tile(chunkPath.get(chunkPathIndex));
            if(head != null && (head.solid() || head.floor().isLiquid)){
                chunkPath = null;
                chunkPathIndex = 0;
                chunkPathCooldown = 0;
                resetChunkSteer();
            }
        }

        if(chunkPath != null && chunkPath.size > 0){
            //re-anchor to the actual unit position: drop waypoints already underfoot or behind the
            //unit relative to the target so a route never makes it backtrack to an earlier door
            pruneLeadingWaypoints(gx, gy);
            if(chunkPathIndex >= chunkPath.size){
                chunkPath = null;
                chunkPathIndex = 0;
                chunkPathCooldown = 0;
                resetChunkSteer();
                return steerAlongChunkPath(gx, gy);
            }

            //refresh the corridor only as a slow keep-alive: extending it happens automatically when the
            //unit consumes the tail and re-queries from the new position. Rebuilding the route every
            //few ticks from a mid-route position made the unit visibly swap between different polyline
            //shapes mid-process, which reads as HPA "throwing" new paths while it is still walking.
            if(++chunkPathRefresh >= 150){
                chunkPathRefresh = 0;
                chunkPath = null;
                chunkPathIndex = 0;
                chunkPathCooldown = 0;
                resetChunkSteer();
            }
        }

        boolean needsQuery = chunkPath == null || chunkPath.size == 0 || chunkPathIndex >= chunkPath.size;
        boolean targetMoved = chunkPath != null
                && Mathf.dst(chunkPathGoalX - gx, chunkPathGoalY - gy) > 8 * tilesize;

        if((needsQuery || targetMoved) && chunkPathCooldown <= 0){
            queryPath(gx, gy);
            pathEditStamp = world.pathfinder.editStamp;
            chunkPathRefresh = 0;
        }

        if(chunkPath == null || chunkPath.size == 0 || chunkPathIndex >= chunkPath.size) return false;

        //rubber-band: skip ahead over waypoints that are nearly on the same heading AND mutually visible
        //with the unit - only then is the straight cut provably clear. A blind angle check alone let
        //units cut the very corner the path detected (chunk doors on opposite sides of a slope),
        //walk straight into it and wedge there until the route happened to re-form.
        int idx = Math.min(chunkPathIndex, chunkPath.size - 1);
        while(idx + 1 < chunkPath.size){
            float first = angleTo(waypointX(idx), waypointY(idx));
            float next = angleTo(waypointX(idx + 1), waypointY(idx + 1));
            if(Math.abs(Angles.angleDist(first, next)) > 14f) break;
            if(!inlineClear(x, y, waypointX(idx + 1), waypointY(idx + 1))) break;
            idx++;
        }
        chunkPathIndex = idx;

        float wx = waypointX(idx), wy = waypointY(idx);
        if(Mathf.dst(wx - x, wy - y) < 3 * tilesize){
            chunkPathIndex = Math.min(idx + 1, chunkPath.size - 1);
        }

        long wp = chunkPath.items[chunkPathIndex];
        wx = (int)(wp >> 32) * tilesize + tilesize / 2f;
        wy = (int)wp * tilesize + tilesize / 2f;
        float wayDist = Mathf.dst(x - wx, y - wy);

        if(wp != chunkStuckTarget){
            chunkStuckTarget = wp;
            chunkStuckProg = Float.NaN;
            chunkStuck = 0;
            chunkSpliceFail = 0;
        }

        if(Float.isNaN(chunkStuckProg) || chunkStuckProg - wayDist > tilesize * 0.15f){
            chunkStuck = 0;
        }else{
            chunkStuck++;
        }
        chunkStuckProg = wayDist;
        boolean hopBlocked = !inlineClear(x, y, wx, wy);
        //chronic jam: refined-fine hops are clear by construction so a jam is a fresh obstruction or a
        //gap too narrow for the body (>30 frames). Coarse fallback: >14 frames on a truly blocking hop,
        //or >44 even when the hop line looks clear. Either way the waypoint is unusable for this unit.
        if(chunkStuck > (chunkPathFine ? 30 : (hopBlocked ? 14 : 44)) && !isFlying()){
            chunkStuck = 0;
            chunkStuckProg = Float.NaN;

            if(chunkPathFine){
                //refined corridor: consecutive waypoints are sub-segments of one walkable polyline, so
                //skipping the jammed one keeps the rest of the route valid - no splicing or re-throwing
                if(chunkPathIndex + 1 < chunkPath.size){
                    chunkPathIndex++;
                    resetChunkSteer();
                    return true;
                }
                chunkPath = null;
                chunkPathIndex = 0;
                chunkPathCooldown = 0;
                resetChunkSteer();
                return steerAlongChunkPath(gx, gy);
            }

            //second attempt on a still-blocked hop (the fine grid may have been unavailable earlier)
            if(hopBlocked && chunkSpliceFail <= 1){
                chunkSpliceFail = 2;
                LongArray detour = world.pathfinder.findUnitPath(team, x, y, wx, wy);
                if(detour != null && detour.size > 0){
                    LongArray merged = new LongArray();
                    merged.addAll(detour.items, 0, detour.size);
                    for(int i = chunkPathIndex + 1; i < chunkPath.size; i++){
                        merged.add(chunkPath.get(i));
                    }
                    chunkPath = merged;
                    chunkPathIndex = 0;
                    chunkPathCooldown = 12;
                    resetChunkSteer();
                    return true;
                }
            }

            //the door is genuinely unreachable (fine-grid route fails or gap too narrow for the body):
            //skip it and steer at the next waypoint instead of grinding or re-throwing the same path
            if(chunkPathIndex + 1 < chunkPath.size){
                chunkPathIndex++;
                resetChunkSteer();
                return true;
            }
            //at the last waypoint the coarse corridor cannot finish the goal; a fine-grid leg straight
            //to the target works whenever the goal still lies inside the loaded window (else the coarse
            //re-query below re-adopts whatever corridor is cached)
            LongArray direct = world.pathfinder.findUnitPath(team, x, y, gx, gy);
            if(direct != null && direct.size > 0){
                chunkPath = direct;
                chunkPathIndex = 0;
                chunkPathGoalX = gx;
                chunkPathGoalY = gy;
                chunkPathFallback = false;
                chunkPathLayer = 1;
                chunkPathFine = false;
                chunkPathCooldown = 20;
                resetChunkSteer();
                return true;
            }
            chunkPath = null;
            chunkPathIndex = 0;
            chunkPathCooldown = 0;
            resetChunkSteer();
            return steerAlongChunkPath(gx, gy);
        }

        float angle = avoidAngle(angleTo(wx, wy));
        velocity.add(chunkVec.trns(angle, type.speed * Timers.delta()));
        if(!isAiming()) rotation = Mathf.slerpDelta(rotation, angle, type.rotatespeed);
        return true;
    }

    /** Advances {@link #chunkPathIndex} past waypoints that are already at/behind the unit. */
    private void pruneLeadingWaypoints(float gx, float gy){
        int idx = chunkPathIndex;
        float gdx = gx - x, gdy = gy - y;
        float goalDistSq = gdx * gdx + gdy * gdy;
        while(idx < chunkPath.size){
            float toWpX = waypointX(idx) - x, toWpY = waypointY(idx) - y;
            if(Mathf.dst(toWpX, toWpY) < 3 * tilesize){
                idx++;
                continue;
            }
            //strictly-behind waypoints (>90° away from the goal direction) would make the unit
            //backtrack; skip them unless the goal itself is basically adjacent
            if(goalDistSq > tilesize * tilesize && (gdx * toWpX + gdy * toWpY) <= 0){
                idx++;
                continue;
            }
            break;
        }
        chunkPathIndex = idx;
    }

    private float waypointX(int index){
        long wp = chunkPath.items[index];
        return (int)(wp >> 32) * tilesize + tilesize / 2f;
    }

    private float waypointY(int index){
        long wp = chunkPath.items[index];
        return (int)wp * tilesize + tilesize / 2f;
    }

    /**
     * True when a straight line between two world-space points crosses only walkable tiles,
     * sampled across the unit's body width. Keeps the rubber-band corner-cutting from skipping
     * chunk doors that hide a slope just around the corner.
     */
    private boolean inlineClear(float ax, float ay, float bx, float by){
        if(Mathf.dst(ax - bx, ay - by) <= tilesize) return true;
        int steps = Math.max(2, (int)(Mathf.dst(ax - bx, ay - by) / (tilesize * 0.5f)));
        float ang = Mathf.atan2(by - ay, bx - ax);
        float half = Math.max(type.hitsize * 0.35f, 2f);
        float dx = (bx - ax) / steps, dy = (by - ay) / steps;
        for(int s = 1; s < steps; s++){
            float px = ax + dx * s, py = ay + dy * s;
            for(int o = -1; o <= 1; o++){
                Tile t = world.tileWorld(px + Angles.trnsx(ang + 90f, half * o), py + Angles.trnsy(ang + 90f, half * o));
                if(t == null || t.solid() || t.floor().isLiquid) return false;
            }
        }
        return true;
    }

    /** @return the currently followed waypoint route, or null when the unit has none (debug preview). */
    public LongArray getChunkPath(){
        return chunkPath;
    }

    public int getChunkPathIndex(){
        return chunkPathIndex;
    }

    /** 0 = chunk HPA, 1 = unit A*, 2 = fallback corridor, -1 = none (debug preview). */
    public int getChunkPathLayer(){
        return chunkPathLayer;
    }

    public boolean isChunkPathFallback(){
        return chunkPathFallback;
    }

    public float getSteerGoalX(){
        return steerGoalX;
    }

    public float getSteerGoalY(){
        return steerGoalY;
    }

    public float getChunkPathGoalX(){
        return chunkPathGoalX;
    }

    public float getChunkPathGoalY(){
        return chunkPathGoalY;
    }

    public UnitState getStartState(){
        return null;
    }

    protected boolean updateOrder(){
        if(!hasOrder()) return false;

        if(getOrderType() == UnitOrderType.attackMove){
            if(retarget()){
                targetClosest();
            }
            if(target != null && !Units.invalidateTarget(target, this) && distanceTo(target) < getWeapon().getAmmo().getRange()){
                rotate(angleTo(target));
            }
        }

        if(getOrderType() == UnitOrderType.attackTarget){
            if(target == null || target.isDead() || target.getTeam() == team){
                clearOrder();
                return false;
            }

            orderX = target.getX();
            orderY = target.getY();

            if(target != null && !Units.invalidateTarget(target, this) && distanceTo(target) < getWeapon().getAmmo().getRange()){
                rotate(angleTo(target));
            }
        }

        float dst = distanceTo(getOrderX(), getOrderY());
        Tile goalTile = world.tileWorld(getOrderX(), getOrderY());
        float blockRadius = goalTile != null && goalTile.block() != null && goalTile.block().size > 0 ? goalTile.block().size * tilesize / 2f : 0f;
        if(dst <= Math.max(getSize(), 10f) + blockRadius){
            clearOrder();
            return false;
        }

        float angle = angleTo(getOrderX(), getOrderY());
        float rad = angle * 0.01745329252f;
        velocity.add(type.speed * Timers.delta() * (float)Math.cos(rad),
                     type.speed * Timers.delta() * (float)Math.sin(rad));
        rotate(angle);
        return true;
    }

    public void updateDefaultAI(){
        if(!updateOrder()){
            state.update();
        }
    }

    protected void drawItems(){
        float backTrns = 4f, itemSize = 5f;
        if(inventory.hasItem()){
            ItemStack stack = inventory.getItem();
            int stored = Mathf.clamp(stack.amount / 6, 1, 8);

            for(int i = 0; i < stored; i++){
                float angT = i == 0 ? 0 : Mathf.randomSeedRange(i + 2, 60f);
                float lenT = i == 0 ? 0 : Mathf.randomSeedRange(i + 3, 1f) - 1f;
                Draw.rect(stack.item.region,
                    x + Angles.trnsx(rotation + 180f + angT, backTrns + lenT),
                    y + Angles.trnsy(rotation + 180f + angT, backTrns + lenT),
                    itemSize, itemSize, rotation);
            }
        }
    }

    @Override
    public boolean isValid(){
        return super.isValid() && isAdded();
    }

    @Override
    public Timer getTimer(){
        return timer;
    }

    @Override
    public int getShootTimer(boolean left){
        return left ? timerShootLeft : timerShootRight;
    }

    @Override
    public Weapon getWeapon(){
        return type.weapon;
    }

    @Override
    public TextureRegion getIconRegion(){
        return type.iconRegion;
    }

    @Override
    public int getItemCapacity(){
        return type.itemCapacity;
    }

    @Override
    public void interpolate(){
        super.interpolate();

        if(interpolator.values.length > 0){
            rotation = interpolator.values[0];
        }
    }

    @Override
    public float maxHealth(){
        return type.health;
    }

    @Override
    public float getArmor(){
        return type.armor;
    }

    @Override
    public float getSize(){
        return type.hitsize;
    }

    @Override
    public boolean isHealer(){
        return type.isHealer;
    }

    @Override
    public float getMass(){
        return type.mass;
    }

    @Override
    public boolean isFlying(){
        return type.isFlying;
    }

    @Override
    public void update(){
        hitTime -= Timers.delta();

        ensureWeaponMounts();

        if(isDead()){
            updateRespawning();
            return;
        }

        if(Net.client()){
            interpolate();
            status.update(this);
            return;
        }

        avoidOthers(1.25f);

        if(spawner != -1 && (world.tile(spawner) == null || world.tile(spawner).entity == null)){
            damage(health);
        }

        if(squad != null){
            squad.update();
        }

        updateTargeting();

        if(controller != null){
            controller.updateUnit();
        }else{
            updateDefaultAI();
        }
        updateVelocityStatus();

        if(target != null) behavior();
        updateShooting();
        updateWeaponMounts();

        if(!world.isOpenWorld()){
            x = Mathf.clamp(x, tilesize, world.width() * tilesize - tilesize);
            y = Mathf.clamp(y, tilesize, world.height() * tilesize - tilesize);
        }
    }

    @Override
    public void draw(){
    }

    @Override
    public void drawLight(){
        boolean emit = emitLight != null ? emitLight : type.emitLight;
        float radius = (lightRadius < 0 ? type.lightRadius : lightRadius);
        float opacity = (lightOpacity < 0 ? type.lightOpacity : lightOpacity);
        Color color = lightColor == null ? type.lightColor : lightColor;

        if(emit && radius > 0.001f){
            Draw.color(color);
            io.anuke.mindustry.graphics.Shaders.light.region = Draw.region("circle");
            Draw.alpha(opacity);
            Draw.rect("circle", x, y, radius * 2, radius * 2);
            Draw.alpha(opacity * 0.5f);
            Draw.rect("circle", x, y, radius * 2, radius * 2);
        }
    }

    @Override
    public float getMaxVelocity(){
        return type.maxVelocity;
    }

    @Override
    public void removed(){
        super.removed();
        Tile tile = world.tile(spawner);
        if(tile != null && !Net.client()){
            tile.block().unitRemoved(tile, this);
        }
        spawner = -1;
    }

    @Override
    public float drawSize(){
        return 14;
    }

    @Override
    public void onDeath(){
        Call.onUnitDeath(this);
    }

    @Override
    public void added(){
        state.set(getStartState());

        health(maxHealth());

        if(isCommanded()){
            onCommand(getCommand());
        }
    }

    @Override
    public void getHitbox(Rectangle rectangle){
        rectangle.setSize(type.hitsize).setCenter(x, y);
    }

    @Override
    public void getHitboxTile(Rectangle rectangle){
        rectangle.setSize(type.hitsizeTile).setCenter(x, y);
    }

    @Override
    public EntityGroup targetGroup(){
        return unitGroups[team.ordinal()];
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        super.writeSave(stream);
        stream.writeByte(type.id);
        stream.writeBoolean(isWave);
        stream.writeLong(spawner);
    }

    @Override
    public void readSave(DataInput stream) throws IOException{
        super.readSave(stream);
        byte type = stream.readByte();
        this.isWave = stream.readBoolean();
        this.spawner = stream.readLong();

        this.type = content.getByID(ContentType.unit, type & 0xFF);
        add();
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.writeSave(data);
        data.writeByte(type.id);
    }

    @Override
    public void read(DataInput data, long time) throws IOException{
        float lastx = x, lasty = y, lastrot = rotation;
        super.readSave(data);
        this.type = content.getByID(ContentType.unit, data.readByte() & 0xFF);

        interpolator.read(lastx, lasty, x, y, time, rotation);
        rotation = lastrot;
    }

    public void onSuperDeath(){
        super.onDeath();
    }
}
