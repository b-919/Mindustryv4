package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.content.blocks.TurretBlocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.world;

/** Turret placement (periodic and on damage) and delayed pending builds. */
public class MassDefense{
    static float turretTimer = 0;
    static float nextTurretTime = 0;
    static float damageTurretTimer = 0;
    static float nextDamageTurretTime = 0;
    static Array<PendingBuild> pendingBuilds = new Array<>();

    private static final Block[] airTurrets = {TurretBlocks.evilCyclone, TurretBlocks.evilScatter};
    private static final Block[] groundTurrets = {TurretBlocks.evilRipple, TurretBlocks.evilFuse, TurretBlocks.evilSalvo};

    //reused buffer for the intel threat hotspot (x,y)
    private static final float[] hotspotScratch = new float[2];

    //turret counts, rebuilt at most once per tick and invalidated when a turret is placed
    private static int turretCountAir = 0;
    private static int turretCountGround = 0;
    private static float turretCountTime = -1f;

    public static void update(){
        turretTimer += Timers.delta();
        damageTurretTimer += Timers.delta();

        for(int i = pendingBuilds.size - 1; i >= 0; i--){
            PendingBuild build = pendingBuilds.get(i);
            build.timer += Timers.delta();
            if(build.timer >= build.delay){
                build.place();
                pendingBuilds.removeIndex(i);
            }
        }

        if(turretTimer >= nextTurretTime){
            turretTimer = 0;
            nextTurretTime = Mathf.random(MassAIConfig.PERIODIC_TURRET_MIN, MassAIConfig.PERIODIC_TURRET_MAX);
            //intel: bias the periodic turret toward the enemy's dominant movement type (air vs ground)
            boolean targetAir = Mathf.chance(MassIntel.enemyAirRatio());
            //intel: if the base was hit recently, aim the new turret at the threat hotspot instead of the core
            float targetX = 0f, targetY = 0f;
            boolean targeted = MassIntel.hotspot(hotspotScratch);
            if(targeted){
                targetX = hotspotScratch[0];
                targetY = hotspotScratch[1];
            }
            if(MassAI.debug) Log.info("[MassAI] periodic turret attempt targetAir={0} enemyCount={1} hotspot={2}", targetAir, MassIntel.enemyCount(), targeted);
            trySpawnTurret(false, targetAir, targetX, targetY);
        }
    }

    static void invalidateTurretCount(){
        turretCountTime = -1f;
    }

    static int countTurrets(boolean air){
        float time = Timers.time();
        if(turretCountTime != time){
            turretCountTime = time;
            turretCountAir = 0;
            turretCountGround = 0;
            Team massTeam = Team.themass;
            for(int x = 0; x < world.width(); x++){
                for(int y = 0; y < world.height(); y++){
                    Tile tile = world.tile(x, y);
                    if(tile != null && tile.getTeam() == massTeam){
                        Block block = tile.block();
                        for(Block turret : airTurrets){
                            if(block == turret){
                                turretCountAir++;
                                break;
                            }
                        }
                        for(Block turret : groundTurrets){
                            if(block == turret){
                                turretCountGround++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return air ? turretCountAir : turretCountGround;
    }

    public static void trySpawnTurret(boolean fromDamage, boolean targetAir, float targetX, float targetY){
        if(fromDamage && damageTurretTimer < nextDamageTurretTime) return;

        Team massTeam = Team.themass;
        ObjectSet<Tile> cores = Vars.state.teams.get(massTeam).cores;
        if(cores.size == 0) return;

        // determine which turret to spawn based on resources in the nearest core, it will always choose the most lowcost one and efficient against the target
        // if unit is aerial will spawn turrets that targets air, same if the target is ground unit
        //targeted whenever the caller supplied a position (on-damage or an intel hotspot), otherwise default to a core
        boolean targeted = fromDamage || targetX != 0f || targetY != 0f;
        Tile checkTile = targeted ? world.tile((int)(targetX / Vars.tilesize), (int)(targetY / Vars.tilesize)) : cores.first();
        if(checkTile == null) checkTile = cores.first();
        Tile nearestCore = MassUtil.findClosestCore(checkTile, massTeam);
        if(nearestCore == null) return;

        if(!fromDamage){
            int coreCount = MassUtil.countNearbyCores(nearestCore);
            int limit = MassAIConfig.TURRET_LIMIT_PER_CORE * Math.max(1, coreCount);
            if(countTurrets(targetAir) >= limit) return;
        }

        ItemModule items = nearestCore.entity.items;

        // the same cost table selects and pays, so checks and deductions can never drift apart
        Block turretBlock = TurretBlocks.evilDuo;
        for(Block turret : targetAir ? airTurrets : groundTurrets){
            if(items.has(costOf(turret))){
                turretBlock = turret;
                break;
            }
        }

        int size = turretBlock.size;

        // checks spawns
        Array<Tile> potentialBases = new Array<>();
        if(targeted){
            int rx = (int)(targetX / Vars.tilesize);
            int ry = (int)(targetY / Vars.tilesize);
            int range = MassAIConfig.DAMAGE_TURRET_SEARCH_RANGE;
            for(int x = -range; x <= range; x++){
                for(int y = -range; y <= range; y++){
                    Tile t = world.tile(rx + x, ry + y);
                    if(t != null && t.getTeam() == massTeam && t.block() != Blocks.air){
                        potentialBases.add(t);
                    }
                }
            }
        }

        if(potentialBases.size == 0){
            for(Tile core : cores) potentialBases.add(core);
            for(int i = 0; i < Math.min(MassBuilder.activeLines.size, 10); i++){
                BuildingLine line = MassBuilder.activeLines.random();
                if(line.tiles.size > 0) potentialBases.add(line.tiles.random().tile);
            }
        }

        if(potentialBases.size == 0) return;

        for(int i = 0; i < MassAIConfig.TURRET_PLACE_ATTEMPTS; i++){
            Tile base = potentialBases.random();
            int rotation = Mathf.random(3);
            int offset = 2 + (size / 2);
            Tile target = world.tile(base.x + Geometry.d4[rotation].x * offset, base.y + Geometry.d4[rotation].y * offset);

            if(target != null && MassBuilder.isAreaClear(target, size) && !MassUtil.isNearEnemyCore(target)){
                if(fromDamage){
                    float dist = Mathf.dst(target.worldx() - targetX, target.worldy() - targetY);
                    if(dist > turretBlock.viewRange) continue;
                }

                ItemStack[] cost = costOf(turretBlock);
                for(ItemStack stack : cost){
                    items.remove(stack.item, stack.amount);
                }

                Array<Tile> veinPath = MassBuilder.findPathToAnyLine(target);
                if(veinPath != null){
                    // start from the building line(aka vein lines) and build towards the turret (first tile)
                    for(int j = veinPath.size - 1; j >= 0; j--){
                        Tile vt = veinPath.get(j);
                        if(vt.block() == Blocks.air){
                            //point towards the next tile in the sequence from line to turret
                            // If j is 0, it points to the turret target(unit/player i think if not I'm crazy)
                            Tile next = (j == 0) ? target : veinPath.get(j - 1);

                            //pathfinds the vein from the lines to the turret placement location
                            float delay = (veinPath.size - j) * 5f;
                            pendingBuilds.add(new PendingBuild(vt, DistributionBlocks.veins, massTeam, vt.relativeTo(next.x, next.y), delay));
                        }
                    }
                }

                float turretDelay = (veinPath != null ? veinPath.size * 5f : 0) + 10f;
                //adrenaline mode: hives swarming with enemies queue their turrets in half the time
                if(MassAdrenaline.isBonusActiveAt(target)) turretDelay /= MassAIConfig.ADRENALINE_TURRET_SPEED;
                pendingBuilds.add(new PendingBuild(target, turretBlock, massTeam, 0, turretDelay, fromDamage, targetX, targetY));
                if(MassAI.debug) Log.info("[MassAI] queued turret build block={0} at ({1},{2}) delay={3}", turretBlock.name, target.x, target.y, turretDelay);

                if(fromDamage){
                    damageTurretTimer = 0;
                    nextDamageTurretTime = Mathf.random(MassAIConfig.DAMAGE_TURRET_MIN, MassAIConfig.DAMAGE_TURRET_MAX);
                }

                return;
            }
        }
    }

    /**Single source of truth for the turret costs; selection and payment both use it.*/
    static ItemStack[] costOf(Block turretBlock){
        if(turretBlock == TurretBlocks.evilRipple) return MassAIConfig.COST_EVIL_RIPPLE;
        if(turretBlock == TurretBlocks.evilFuse) return MassAIConfig.COST_EVIL_FUSE;
        if(turretBlock == TurretBlocks.evilSalvo) return MassAIConfig.COST_EVIL_SALVO;
        if(turretBlock == TurretBlocks.evilScatter) return MassAIConfig.COST_EVIL_SCATTER;
        if(turretBlock == TurretBlocks.evilCyclone) return MassAIConfig.COST_EVIL_CYCLONE;
        return MassAIConfig.COST_NONE;
    }

    public static void reset(){
        turretTimer = 0;
        nextTurretTime = Mathf.random(MassAIConfig.PERIODIC_TURRET_MIN, MassAIConfig.PERIODIC_TURRET_MAX);
        damageTurretTimer = 0;
        nextDamageTurretTime = 0;
        pendingBuilds.clear();
        invalidateTurretCount();
    }
}