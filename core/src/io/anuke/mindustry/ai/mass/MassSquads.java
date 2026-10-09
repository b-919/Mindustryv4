package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitCommand;
import io.anuke.mindustry.entities.units.UnitOrderType;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.storage.HiveBlock;
import io.anuke.mindustry.world.blocks.units.UnitFactory;
import io.anuke.mindustry.world.blocks.units.UnitFactoryAdvanced;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.trait.Entity;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.tilesize;
import static io.anuke.mindustry.Vars.unitGroups;
import static io.anuke.mindustry.Vars.world;

/** Preset squad roster order system for biomass units.*/
public class MassSquads{
    //legacy globals kept for save compatibility
    static UnitCommand currentCommand = UnitCommand.attack;
    static float enemyNearbyTimer = 0;

    public static final ObjectIntMap<BaseUnit> unitSquadAssignments = new ObjectIntMap<>();
    static final Array<MassSquad> activeSquads = new Array<>();

    private static int rosterCoreCount = -1;
    private static int rosterEvo = -1;

    private static float baseX, baseY;
    private static final Array<Tile> enemyCoresScratch = new Array<>();
    private static int enemyProducers;
    private static int enemyUnitFactories;

    private static Unit clusterTarget;
    private static final Array<Unit> clusterUnits = new Array<>();
    private static float nextClusterScan;

    private static boolean hiveUnderAttack;
    private static Unit defendTarget;
    private static float defendX, defendY;
    private static float defendGiveupTimer;
    private static float lastAttackNotifyTime = -MassAIConfig.DEFEND_NOTIFY_INTERVAL;

    private static final Array<BaseUnit> massUnitsScratch = new Array<>();
    private static final Array<ObjectIntMap<UnitType>> squadTypeMaps = new Array<>();
    private static final ObjectIntMap<BaseUnit> tempAssignments = new ObjectIntMap<>();
    private static final Rectangle scanRect = new Rectangle();
    private static final FloatArray rollWeights = new FloatArray();
    private static final Array<Unit> enemyScratch = new Array<>();

    public static void update(ObjectSet<Tile> cores){
        if(cores.size == 0){
            currentCommand = UnitCommand.retreat;
            return;
        }

        //legacy global-command bookkeeping kept fresh for the save file
        if(MassUtil.anyEnemyNearCores(cores)){
            currentCommand = UnitCommand.patrol;
            enemyNearbyTimer = 0;
        }else{
            enemyNearbyTimer += Timers.delta();
            currentCommand = UnitCommand.attack;
        }

        MassInfection.commandTimer += Timers.delta();

        ensureRoster(cores);
        scanScenario();
        updateDefenseState();

        refreshSquadMetrics();
        assignFreshUnits();
        recomputeCentroids();

        for(MassSquad squad : activeSquads){
            squad.nextOrderTime -= Timers.delta();
            squad.nextTargetRefresh -= Timers.delta();

            if(squad.defensive){
                squad.nextOrderTime = Math.max(squad.nextOrderTime, 0.5f);
            }else if(squad.giveUpPatrol && squad.nextOrderTime <= 0f){
                squad.giveUpPatrol = false;
            }

            if(isPursuitOrder(squad.order)){
                squad.pursuitTimer -= Timers.delta();
                if(squad.pursuitTarget == null || squad.pursuitTarget.isDead() || !squad.pursuitTarget.isAdded()){
                    exitPursuit(squad);
                }else if(squad.pursuitTimer <= 0f){
                    float d = Mathf.dst(squad.cx - squad.pursuitTarget.x, squad.cy - squad.pursuitTarget.y);
                    if(d > MassAIConfig.PATROL_ENGAGE_RADIUS * 1.5f){
                        exitPursuit(squad);
                    }else{
                        squad.pursuitTimer = MassAIConfig.PURSUIT_MIN_TIME;
                    }
                }
            }

            if(squad.nextOrderTime <= 0f && !isPursuitOrder(squad.order)){
                squad.nextOrderTime = Mathf.random(MassAIConfig.ORDER_REFRESH_MIN, MassAIConfig.ORDER_REFRESH_MAX);
                rollOrder(squad);
                squad.nextTargetRefresh = 0f;
            }

            if(squad.nextTargetRefresh <= 0f && !isPursuitOrder(squad.order)){
                squad.nextTargetRefresh = MassAIConfig.TARGET_REFRESH_INTERVAL;
                refreshSquadTarget(squad);
            }

            if(isPatrolOrder(squad.order)){
                updateSquadNodes(squad);
            }else if(!isPursuitOrder(squad.order)){
                squad.nodeIndex = 0;
            }
        }

        detectPatrolEngagement();
        guaranteeAttackSquad();
        if(hiveUnderAttack) applyDefenseOverrides();

        for(MassSquad squad : activeSquads){
            orderSquad(squad);
        }
    }

    private static void ensureRoster(ObjectSet<Tile> cores){
        int coreCount = cores.size;
        int evo = getMaxEvolution(cores);
        if(coreCount == rosterCoreCount && evo == rosterEvo) return;

        int perHive = Math.max(1, MassAIConfig.squadsPerHive(Vars.state.difficulty));
        if(MassAI.debug) Log.info("[MassAI] roster rebuilt coreCount={0} evo={1} perHive={2}", coreCount, evo, perHive);
        rosterCoreCount = coreCount;
        rosterEvo = evo;
        activeSquads.clear();
        squadTypeMaps.clear();
        unitSquadAssignments.clear();

        SquadPreset[] presets = presetsFor(evo);
        int total = perHive * coreCount;
        for(int i = 0; i < total; i++){
            addPreset(presets[i % presets.length]);
        }
    }

    /**Role list available at an evolution level */
    private static SquadPreset[] presetsFor(int evo){
        if(evo >= 3){
            return new SquadPreset[]{
                SquadPreset.LOW, SquadPreset.HEAVY_ASSAULT, SquadPreset.ARTILLERY, SquadPreset.ULTRA,
                SquadPreset.AIR_STRIKE, SquadPreset.SWARM, SquadPreset.SIEGE, SquadPreset.DEMOLITION_AIR
            };
        }else if(evo == 2){
            return new SquadPreset[]{
                SquadPreset.LOW, SquadPreset.HEAVY_ASSAULT, SquadPreset.ARTILLERY,
                SquadPreset.AIR_STRIKE, SquadPreset.SIEGE, SquadPreset.DEMOLITION_GROUND
            };
        }else if(evo == 1){
            return new SquadPreset[]{
                SquadPreset.LOW, SquadPreset.DEMOLITION_AIR, SquadPreset.DEMOLITION_GROUND, SquadPreset.AIR_STRIKE
            };
        }
        return new SquadPreset[]{SquadPreset.LOW};
    }

    private static void addPreset(SquadPreset preset){
        MassSquad squad = new MassSquad(activeSquads.size, preset);
        squad.nodeTimer = 0f;
        activeSquads.add(squad);
        squadTypeMaps.add(new ObjectIntMap<UnitType>());
    }

    private static int getMaxEvolution(ObjectSet<Tile> cores){
        int evo = 0;
        for(Tile core : cores){
            if(core.entity() instanceof HiveBlock.HiveEntity){
                evo = Math.max(evo, ((HiveBlock.HiveEntity) core.entity()).evolution);
            }
        }
        return evo;
    }

    private static void scanScenario(){
        float totalX = 0f, totalY = 0f;
        int count = 0;
        for(Tile core : Vars.state.teams.get(Team.themass).cores){
            totalX += core.worldx();
            totalY += core.worldy();
            count++;
        }
        baseX = count > 0 ? totalX / count : 0f;
        baseY = count > 0 ? totalY / count : 0f;

        enemyCoresScratch.clear();
        enemyCoresScratch.addAll(MassUtil.getEnemyCores());

        enemyProducers = 0;
        enemyUnitFactories = 0;
        Array<Tile> prod = world.indexer.getEnemy(Team.themass, BlockFlag.producer);
        for(Tile tile : prod){
            if(tile == null || tile.entity == null || tile.getTeam() == Team.themass) continue;
            boolean factory = tile.block() instanceof UnitFactory || tile.block() instanceof UnitFactoryAdvanced;
            if(nearEnemyCore(tile, MassAIConfig.PRODUCTION_SCAN_RADIUS)){
                if(factory) enemyUnitFactories++;
                else enemyProducers++;
            }
        }

        nextClusterScan -= Timers.delta();
        if(nextClusterScan <= 0f){
            nextClusterScan = MassAIConfig.UNIT_CLUSTER_SCAN_INTERVAL;
            findUnitCluster();
        }
    }

    private static boolean nearEnemyCore(Tile tile, float radiusTiles){
        for(Tile core : enemyCoresScratch){
            if(Mathf.dst(tile.x - core.x, tile.y - core.y) <= radiusTiles) return true;
        }
        return false;
    }

    private static void findUnitCluster(){
        clusterUnits.clear();
        int scanned = 0;
        for(Team team : Team.all){
            if(team == Team.themass || team == Team.none) continue;
            for(BaseUnit unit : unitGroups[team.ordinal()].all()){
                if(unit == null || !unit.isAdded() || unit.isDead()) continue;
                if(scanned++ >= MassAIConfig.ENEMY_UNIT_SCAN_MAX) break;
                boolean exposed = true;
                for(Tile core : enemyCoresScratch){
                    if(Mathf.dst((unit.x / tilesize) - core.x, (unit.y / tilesize) - core.y) <= MassAIConfig.ATTACK_UNITS_EXPOSED_RADIUS){
                        exposed = false;
                        break;
                    }
                }
                if(exposed) clusterUnits.add(unit);
            }
        }

        Unit best = null;
        int bestCount = 0;
        float radius = MassAIConfig.UNIT_CLUSTER_RADIUS * tilesize;
        for(int i = 0; i < clusterUnits.size; i++){
            Unit u = clusterUnits.get(i);
            int cnt = 1;
            for(int j = 0; j < clusterUnits.size; j++){
                Unit v = clusterUnits.get(j);
                if(v == u) continue;
                if(Mathf.dst(u.x - v.x, u.y - v.y) <= radius) cnt++;
            }
            if(cnt > bestCount){
                bestCount = cnt;
                best = u;
            }
        }
        clusterTarget = bestCount >= MassAIConfig.UNIT_CLUSTER_MIN ? best : null;
    }

    public static void notifyAttack(float x, float y, Entity attacker){
        if(hiveUnderAttack) return;
        if(!nearAnyMassCore(x / tilesize, y / tilesize, MassAIConfig.DEFEND_ALERT_RADIUS)) return;

        float time = Timers.time();
        if(time - lastAttackNotifyTime < MassAIConfig.DEFEND_NOTIFY_INTERVAL) return;
        lastAttackNotifyTime = time;

        Unit target = attacker instanceof Unit && !((Unit) attacker).isDead() && attacker.isAdded() ?
            (Unit) attacker : findNearestEnemy(x, y);
        if(target == null) return;

        defendTarget = target;
        defendX = x;
        defendY = y;
        defendGiveupTimer = MassAIConfig.DEFEND_GIVEUP_TIME;
        hiveUnderAttack = true;
        if(MassAI.debug) Log.info("[MassAI] hive under attack near ({0},{1})", (int) (x / tilesize), (int) (y / tilesize));
    }

    private static boolean nearAnyMassCore(float tileX, float tileY, float radiusTiles){
        for(Tile core : Vars.state.teams.get(Team.themass).cores){
            if(Mathf.dst(tileX - core.x, tileY - core.y) <= radiusTiles) return true;
        }
        return false;
    }

    private static Unit findNearestEnemy(float x, float y){
        enemyScratch.clear();
        Units.getNearbyEnemies(Team.themass, scanRect.setSize(MassAIConfig.DEFEND_SCAN).setCenter(x, y), enemyScratch::add);
        Unit best = null;
        float bestD = Float.MAX_VALUE;
        for(Unit u : enemyScratch){
            float d = Mathf.dst(u.x - x, u.y - y);
            if(d < bestD){
                bestD = d;
                best = u;
            }
        }
        return best;
    }

    private static void updateDefenseState(){
        if(!hiveUnderAttack) return;

        if(defendTarget == null || defendTarget.isDead() || !defendTarget.isAdded()){
            if(MassAI.debug && defendTarget != null) Log.info("[MassAI] attacker eliminated, defense stands down");
            restoreDefenseSquads();
            hiveUnderAttack = false;
            defendTarget = null;
            return;
        }

        defendGiveupTimer -= Timers.delta();
        if(defendGiveupTimer <= 0f){
            if(MassAI.debug) Log.info("[MassAI] attacker survived {0}s, defense gives up", MassAIConfig.DEFEND_GIVEUP_TIME);
            giveupDefenseSquads();
            hiveUnderAttack = false;
            defendTarget = null;
        }
    }

    private static void restoreDefenseSquads(){
        for(MassSquad squad : activeSquads){
            if(squad.defensive){
                squad.defensive = false;
                squad.order = squad.previousPatrol;
                squad.nodeTimer = 0f;
                if(MassAI.debug) Log.info("[MassAI] squad={0} back on {1}", squad.id, squad.order);
            }
        }
    }

    private static void giveupDefenseSquads(){
        for(MassSquad squad : activeSquads){
            if(squad.defensive){
                squad.defensive = false;
                squad.order = MassOrder.PATROL_HIVES;
                squad.giveUpPatrol = true;
                squad.nextOrderTime = MassAIConfig.DEFEND_GIVEUP_ORDER_TIME;
                squad.nodeTimer = 0f;
                if(squad.nodeCount == 0) recomputeSquadNodes(squad);
            }
        }
    }

    private static void refreshSquadMetrics(){
        for(MassSquad squad : activeSquads){
            squad.memberCount = 0;
            squad.cx = 0f;
            squad.cy = 0f;
        }
        for(ObjectIntMap<UnitType> map : squadTypeMaps){
            map.clear();
        }

        massUnitsScratch.clear();
        for(Team team : Team.all){
            if(team == Team.themass || team == Team.none) continue;
        }
        for(BaseUnit unit : unitGroups[Team.themass.ordinal()].all()){
            if(unit != null && unit.isAdded() && !unit.isDead()){
                massUnitsScratch.add(unit);
            }
        }

        tempAssignments.clear();
        for(ObjectIntMap.Entry<BaseUnit> entry : unitSquadAssignments.entries()){
            BaseUnit unit = entry.key;
            if(unit == null || unit.isDead() || !unit.isAdded()) continue;
            tempAssignments.put(unit, entry.value);
        }
        unitSquadAssignments.clear();
        for(ObjectIntMap.Entry<BaseUnit> entry : tempAssignments.entries()){
            unitSquadAssignments.put(entry.key, entry.value);
            int squadId = entry.value;
            if(squadId < 0 || squadId >= activeSquads.size) continue;
            MassSquad squad = activeSquads.get(squadId);
            squad.memberCount++;
            squad.cx += entry.key.x;
            squad.cy += entry.key.y;
            squadTypeMaps.get(squadId).getAndIncrement(entry.key.getType(), 0, 1);
        }
    }

    private static void assignFreshUnits(){
        for(BaseUnit unit : massUnitsScratch){
            if(unitSquadAssignments.containsKey(unit)) continue;
            if(unit.getSpawner() == null) continue;
            UnitType type = unit.getType();
            if(type == null) continue;

            MassSquad best = null;
            int bestWant = 0;
            for(int i = 0; i < activeSquads.size; i++){
                MassSquad squad = activeSquads.get(i);
                ObjectIntMap<UnitType> counts = squadTypeMaps.get(i);
                int want = squad.preset.want(type, counts.get(type, 0), squad.memberCount);
                if(want > bestWant){
                    bestWant = want;
                    best = squad;
                }
            }
            if(best == null){
                for(int i = 0; i < activeSquads.size; i++){
                    MassSquad squad = activeSquads.get(i);
                    if(squad.preset.canAcceptFill(type, squad.memberCount)){
                        best = squad;
                        break;
                    }
                }
            }
            if(best == null) continue;

            unitSquadAssignments.put(unit, best.id);
            if(MassAI.debug) Log.info("[MassAI] unit={0} type={1} assigned to squad={2} (preset={3})", unit.getID(), type.name, best.id, best.preset);
        }
    }

    private static void recomputeCentroids(){
        for(MassSquad squad : activeSquads){
            squad.memberCount = 0;
            squad.cx = 0f;
            squad.cy = 0f;
        }
        for(ObjectIntMap.Entry<BaseUnit> entry : unitSquadAssignments.entries()){
            int squadId = entry.value;
            if(squadId < 0 || squadId >= activeSquads.size) continue;
            MassSquad squad = activeSquads.get(squadId);
            squad.memberCount++;
            squad.cx += entry.key.x;
            squad.cy += entry.key.y;
        }
        for(MassSquad squad : activeSquads){
            if(squad.memberCount > 0){
                squad.cx /= squad.memberCount;
                squad.cy /= squad.memberCount;
            }
        }
    }

    private static void rollOrder(MassSquad squad){
        boolean grace = MassInfection.isGracePeriod();
        rollWeights.clear();
        float total = 0f;

        for(MassOrder o : MassOrder.all){
            float w = 0f;
            switch(o){
                case ATTACK_BASE:
                    if(enemyCoresScratch.size > 0) w = 1f + 0.1f * activeSquads.size;
                    break;
                case ATTACK_PRODUCTION:
                    w = 0.55f * Math.min(1f, enemyProducers / 4f);
                    break;
                case ATTACK_UNIT_PRODUCTION:
                    w = 0.5f * Math.min(1f, enemyUnitFactories / 2f);
                    break;
                case ATTACK_UNITS:
                    //the more enemies on the field, the more squads are pushed to hunt them
                    if(clusterTarget != null) w = 0.7f * Math.min(1f, (activeSquads.size + 1) / 3f)
                        * Mathf.clamp(MassIntel.enemyCount() / 8f, 0.6f, 1.6f);
                    break;
                case PATROL_OUTSIDE_HIVES:
                    w = 0.22f * ((grace || rosterEvo == 0) ? 1.5f : 1f);
                    break;
                case PATROL_HIVES:
                    //more hive patrols while the base is under recent pressure (threat-map heat)
                    w = 0.5f + MassIntel.threatHeat() * 0.6f;
                    break;
                default:
                    break;
            }

            if(grace && isAttackOrder(o)){
                w *= 0.08f;
            }
            if((squad.preset == SquadPreset.ARTILLERY || squad.preset == SquadPreset.ULTRA) && o == MassOrder.ATTACK_BASE){
                w *= 1.3f;
            }
            if(squad.preset.antiProduction){
                //air units preferred to raid factories/producers over cores
                if(o == MassOrder.ATTACK_PRODUCTION || o == MassOrder.ATTACK_UNIT_PRODUCTION){
                    w *= 3f;
                }else if(o == MassOrder.ATTACK_BASE){
                    w *= 0.4f;
                }
            }else if(squad.preset == SquadPreset.SIEGE && o == MassOrder.ATTACK_BASE){
                w *= 1.5f;
            }
            if(squad.preset.air && o == MassOrder.PATROL_HIVES){
                w *= 0.5f;
            }

            rollWeights.add(w);
            total += w;
        }

        MassOrder chosen = MassOrder.PATROL_HIVES;
        if(total > 0.001f){
            float value = Mathf.random(total);
            float cursor = 0f;
            for(int i = 0; i < MassOrder.all.length; i++){
                cursor += rollWeights.get(i);
                if(value <= cursor){
                    chosen = MassOrder.all[i];
                    break;
                }
            }
        }else if(enemyCoresScratch.size > 0){
            chosen = MassOrder.ATTACK_BASE;
        }
        rollWeights.clear();

        squad.order = chosen;
        squad.nodeTimer = 0f;
        squad.attackTile = null;
        squad.attackUnit = null;
        squad.pursuitTarget = null;
        squad.pursuitTimer = 0f;
    }

    private static void refreshSquadTarget(MassSquad squad){
        switch(squad.order){
            case ATTACK_BASE:
                squad.attackTile = nearestEnemyCore(baseX, baseY);
                if(squad.attackTile == null) squad.order = MassOrder.PATROL_HIVES;
                break;
            case ATTACK_PRODUCTION:
                squad.attackTile = findProductionTarget(false, squad.cx, squad.cy);
                if(squad.attackTile == null) squad.order = MassOrder.ATTACK_BASE;
                break;
            case ATTACK_UNIT_PRODUCTION:
                squad.attackTile = findProductionTarget(true, squad.cx, squad.cy);
                if(squad.attackTile == null) squad.order = MassOrder.ATTACK_BASE;
                break;
            case ATTACK_UNITS:
                if(clusterTarget != null && !clusterTarget.isDead() && clusterTarget.isAdded()){
                    squad.attackUnit = clusterTarget;
                }else{
                    squad.attackUnit = null;
                    squad.order = MassOrder.PATROL_HIVES;
                    squad.nextOrderTime = 0f;
                }
                break;
            default:
                break;
        }
    }

    private static Tile nearestEnemyCore(float x, float y){
        Tile best = null;
        float bestD = Float.MAX_VALUE;
        for(Tile core : enemyCoresScratch){
            float d = Mathf.dst(core.worldx() - x, core.worldy() - y);
            if(d < bestD){
                bestD = d;
                best = core;
            }
        }
        return best;
    }

    private static Tile findProductionTarget(boolean factory, float fromX, float fromY){
        Tile best = null;
        float bestD = Float.MAX_VALUE;
        Array<Tile> prod = world.indexer.getEnemy(Team.themass, BlockFlag.producer);
        for(Tile tile : prod){
            if(tile == null || tile.entity == null || tile.getTeam() == Team.themass) continue;
            if(!nearEnemyCore(tile, MassAIConfig.PRODUCTION_SCAN_RADIUS)) continue;
            boolean isFactory = tile.block() instanceof UnitFactory || tile.block() instanceof UnitFactoryAdvanced;
            if(isFactory != factory) continue;
            Tile target = tile.target();
            float d = Mathf.dst(target.drawx() - fromX, target.drawy() - fromY);
            if(d < bestD){
                bestD = d;
                best = target;
            }
        }
        return best;
    }

    private static void updateSquadNodes(MassSquad squad){
        squad.nodeTimer -= Timers.delta();
        if(squad.nodeTimer <= 0f){
            squad.nodeTimer = MassAIConfig.NODE_REFRESH_INTERVAL;
            recomputeSquadNodes(squad);
        }
        if(squad.nodeCount == 0 || squad.memberCount == 0) return;
        if(squad.nodeIndex >= squad.nodeCount) squad.nodeIndex = 0;
        float nx = squad.patrolX[squad.nodeIndex];
        float ny = squad.patrolY[squad.nodeIndex];
        if(Mathf.dst(squad.cx - nx, squad.cy - ny) <= MassAIConfig.NODE_REACH * tilesize){
            squad.nodeIndex = (squad.nodeIndex + 1) % squad.nodeCount;
        }
    }

    private static void recomputeSquadNodes(MassSquad squad){
        int count = 3;
        if(squad.order == MassOrder.PATROL_OUTSIDE_HIVES){
            Tile enemy = nearestEnemyCore(baseX, baseY);
            if(enemy == null){
                squad.order = MassOrder.PATROL_HIVES;
                return;
            }
            float ang = Mathf.atan2(enemy.worldy() - baseY, enemy.worldx() - baseX);
            float coreDist = 0f;
            float baseTileX = baseX / tilesize;
            float baseTileY = baseY / tilesize;
            for(Tile core : Vars.state.teams.get(Team.themass).cores){
                float d = Mathf.dst(core.x - baseTileX, core.y - baseTileY);
                if(d > coreDist) coreDist = d;
            }
            float extent = Mathf.clamp(coreDist, 15f, 40f) + MassAIConfig.PATROL_OUTSIDE_EXTRA;
            squad.nodeCount = count;
            for(int i = 0; i < count; i++){
                float a = ang + (i - 1) * 14f;
                float d = (extent + i * MassAIConfig.PATROL_OUTSIDE_SPACING) * tilesize;
                squad.patrolX[i] = baseX + Angles.trnsx(a, d);
                squad.patrolY[i] = baseY + Angles.trnsy(a, d);
            }
        }else{
            squad.nodeCount = count;
            float radius = MassAIConfig.PATROL_HIVE_RADIUS * tilesize;
            for(int i = 0; i < count; i++){
                float a = i * 120f + squad.id * 47f;
                squad.patrolX[i] = baseX + Angles.trnsx(a, radius);
                squad.patrolY[i] = baseY + Angles.trnsy(a, radius);
            }
        }
        squad.nodeIndex = 0;
    }

    private static void guaranteeAttackSquad(){
        if(MassInfection.isGracePeriod() || enemyCoresScratch.size == 0 || hiveUnderAttack) return;
        boolean anyAttack = false;
        for(MassSquad squad : activeSquads){
            if(isAttackOrder(squad.order) || isPursuitOrder(squad.order)){
                anyAttack = true;
                break;
            }
        }
        if(!anyAttack){
            for(MassSquad squad : activeSquads){
                if(squad.defensive || squad.giveUpPatrol) continue;
                squad.order = MassOrder.ATTACK_BASE;
                squad.nextOrderTime = Math.max(squad.nextOrderTime, 10f * 60f);
                if(MassAI.debug) Log.info("[MassAI] guarantee: forcing squad={0} to attack base", squad.id);
                break;
            }
        }
    }

    private static void applyDefenseOverrides(){
        float radius = MassAIConfig.DEFEND_RADIUS * tilesize;
        int candidates = 0;
        int farthest = -1;
        float farD = -1f;
        for(MassSquad squad : activeSquads){
            if(squad.defensive){
                squad.giveUpPatrol = false;
                continue;
            }
            if(squad.memberCount == 0) continue;
            if(!isPatrolOrder(squad.order) && !isPursuitOrder(squad.order)) continue;
            float d = Mathf.dst(squad.cx - defendX, squad.cy - defendY);
            if(d > radius) continue;
            candidates++;
            if(d > farD){
                farD = d;
                farthest = squad.id;
            }
        }
        if(candidates == 0) return;

        int spare = candidates >= 2 ? farthest : -1;
        for(MassSquad squad : activeSquads){
            if(squad.defensive) continue;
            if(squad.memberCount == 0) continue;
            if(!isPatrolOrder(squad.order) && !isPursuitOrder(squad.order)) continue;
            float d = Mathf.dst(squad.cx - defendX, squad.cy - defendY);
            if(d > radius || squad.id == spare) continue;
            if(isPursuitOrder(squad.order)){
                squad.pursuitTarget = null;
                squad.pursuitTimer = 0f;
            }
            squad.previousPatrol = isPatrolOrder(squad.order) ? squad.order : squad.previousPatrol;
            squad.order = MassOrder.DEFEND_HIVE;
            squad.defensive = true;
            squad.giveUpPatrol = false;
            squad.nodeTimer = 0f;
            if(MassAI.debug) Log.info("[MassAI] squad={0} defending hive at ({1},{2})", squad.id, (int) (defendX / tilesize), (int) (defendY / tilesize));
        }
    }

    private static void exitPursuit(MassSquad squad){
        squad.order = squad.previousPatrol == MassOrder.PURSUE ? MassOrder.PATROL_HIVES : squad.previousPatrol;
        if(squad.order != MassOrder.PATROL_HIVES && squad.order != MassOrder.PATROL_OUTSIDE_HIVES){
            squad.order = MassOrder.PATROL_HIVES;
        }
        squad.previousPatrol = squad.order;
        squad.pursuitTarget = null;
        squad.pursuitTimer = 0f;
        squad.nodeTimer = 0f;
    }

    private static void enterPursuit(MassSquad squad, Unit target){
        if(squad.order == MassOrder.PURSUE && squad.pursuitTarget == target) return;
        if(!(isPatrolOrder(squad.order) || isPursuitOrder(squad.order))) return;
        squad.previousPatrol = isPatrolOrder(squad.order) ? squad.order : squad.previousPatrol;
        squad.order = MassOrder.PURSUE;
        squad.pursuitTarget = target;
        squad.pursuitTimer = MassAIConfig.PURSUIT_GIVEUP_TIME;
        if(MassAI.debug) Log.info("[MassAI] squad={0} pursuing target near ({1},{2})", squad.id, (int)(target.x/tilesize), (int)(target.y/tilesize));
    }

    private static float engagementRadius(SquadPreset preset){
        if(preset == null) return MassAIConfig.PATROL_ENGAGE_RADIUS;
        switch(preset){
            case ULTRA: return MassAIConfig.PATROL_ENGAGE_RADIUS * 1.15f;
            case SIEGE: return MassAIConfig.PATROL_ENGAGE_RADIUS * 1.1f;
            case ARTILLERY: return MassAIConfig.PATROL_ENGAGE_RADIUS * 1.1f;
            case SWARM: return MassAIConfig.PATROL_ENGAGE_RADIUS * 0.7f;
            case DEMOLITION_AIR: return MassAIConfig.PATROL_ENGAGE_RADIUS * 0.85f;
            default: return MassAIConfig.PATROL_ENGAGE_RADIUS;
        }
    }

    private static void detectPatrolEngagement(){
        if(hiveUnderAttack) return;
        for(MassSquad squad : activeSquads){
            if(!isPatrolOrder(squad.order) || squad.defensive || squad.giveUpPatrol) continue;
            if(squad.memberCount == 0) continue;

            float radius = engagementRadius(squad.preset);
            enemyScratch.clear();
            Units.getNearbyEnemies(Team.themass, scanRect.setSize(radius * 2f).setCenter(squad.cx, squad.cy), enemyScratch::add);

            Unit best = null;
            float bestD = Float.MAX_VALUE;
            for(Unit enemy : enemyScratch){
                if(enemy == null || enemy.isDead() || !enemy.isAdded()) continue;
                float d = Mathf.dst(enemy.x - squad.cx, enemy.y - squad.cy);
                if(d <= radius && d < bestD){
                    bestD = d;
                    best = enemy;
                }
            }
            if(best != null){
                enterPursuit(squad, best);
            }
        }
    }

    private static void orderSquad(MassSquad squad){
        if(squad.memberCount == 0) return;

        float tx, ty;
        UnitOrderType want;
        switch(squad.order){
            case ATTACK_BASE:
            case ATTACK_PRODUCTION:
            case ATTACK_UNIT_PRODUCTION:
                if(squad.attackTile == null){
                    for(ObjectIntMap.Entry<BaseUnit> entry : unitSquadAssignments.entries()){
                        if(entry.value == squad.id) entry.key.clearOrder();
                    }
                    return;
                }
                tx = squad.attackTile.drawx();
                ty = squad.attackTile.drawy();
                want = UnitOrderType.attackMove;
                break;
            case ATTACK_UNITS:
                if(squad.attackUnit == null || squad.attackUnit.isDead() || !squad.attackUnit.isAdded()){
                    for(ObjectIntMap.Entry<BaseUnit> entry : unitSquadAssignments.entries()){
                        if(entry.value == squad.id) entry.key.clearOrder();
                    }
                    return;
                }
                tx = squad.attackUnit.x;
                ty = squad.attackUnit.y;
                want = UnitOrderType.attackTarget;
                break;
            case PATROL_HIVES:
            case PATROL_OUTSIDE_HIVES:
                if(squad.nodeCount == 0) recomputeSquadNodes(squad);
                if(squad.nodeIndex >= squad.nodeCount) squad.nodeIndex = 0;
                tx = squad.patrolX[squad.nodeIndex];
                ty = squad.patrolY[squad.nodeIndex];
                want = UnitOrderType.move;
                break;
            case PURSUE:
                if(squad.pursuitTarget == null || squad.pursuitTarget.isDead() || !squad.pursuitTarget.isAdded()){
                    exitPursuit(squad);
                    for(ObjectIntMap.Entry<BaseUnit> entry : unitSquadAssignments.entries()){
                        if(entry.value == squad.id) entry.key.clearOrder();
                    }
                    return;
                }
                tx = squad.pursuitTarget.x;
                ty = squad.pursuitTarget.y;
                want = UnitOrderType.attackTarget;
                break;
            default:
                if(defendTarget != null && !defendTarget.isDead() && defendTarget.isAdded()){
                    tx = defendTarget.x;
                    ty = defendTarget.y;
                    want = UnitOrderType.attackTarget;
                }else{
                    tx = defendX;
                    ty = defendY;
                    want = UnitOrderType.move;
                }
                break;
        }

        for(ObjectIntMap.Entry<BaseUnit> entry : unitSquadAssignments.entries()){
            if(entry.value != squad.id) continue;
            BaseUnit unit = entry.key;
            if(want == UnitOrderType.attackTarget){
                if(squad.order == MassOrder.ATTACK_UNITS){
                    unit.setDirectTarget(squad.attackUnit);
                }else if(squad.order == MassOrder.DEFEND_HIVE){
                    unit.setDirectTarget(defendTarget);
                }else if(squad.order == MassOrder.PURSUE){
                    unit.setDirectTarget(squad.pursuitTarget);
                }else{
                    unit.setDirectTarget(defendTarget);
                }
            }
            boolean force = want == UnitOrderType.attackTarget;
            boolean moved = Mathf.dst(unit.getOrderX() - tx, unit.getOrderY() - ty) > tilesize;
            if(force || !unit.hasOrder() || unit.getOrderType() != want || moved){
                unit.clearOrder();
                if(want == UnitOrderType.move){
                    unit.orderMove(tx, ty);
                }else if(want == UnitOrderType.attackMove){
                    unit.orderAttackMove(tx, ty);
                }else{
                    unit.orderAttackTarget(tx, ty);
                }
            }
        }
    }

    private static boolean isAttackOrder(MassOrder order){
        return order == MassOrder.ATTACK_BASE || order == MassOrder.ATTACK_PRODUCTION ||
            order == MassOrder.ATTACK_UNITS || order == MassOrder.ATTACK_UNIT_PRODUCTION;
    }

    private static boolean isPatrolOrder(MassOrder order){
        return order == MassOrder.PATROL_HIVES || order == MassOrder.PATROL_OUTSIDE_HIVES;
    }

    private static boolean isPursuitOrder(MassOrder order){
        return order == MassOrder.PURSUE;
    }

    static void computeUnitRequests(ObjectIntMap<UnitType> out){
        out.clear();
        for(int i = 0; i < activeSquads.size; i++){
            MassSquad squad = activeSquads.get(i);
            ObjectIntMap<UnitType> counts = squadTypeMaps.get(i);
            for(UnitType type : squad.preset.types){
                int want = squad.preset.want(type, counts.get(type, 0), squad.memberCount);
                if(want > 0){
                    out.getAndIncrement(type, 0, want);
                }
            }
        }
    }
    //debug
    public static String orderName(int squadId){
        if(squadId < 0 || squadId >= activeSquads.size) return "-";
        return activeSquads.get(squadId).order.name();
    }

    public static void squadTarget(int squadId, float[] out){
        if(squadId < 0 || squadId >= activeSquads.size) return;
        MassSquad squad = activeSquads.get(squadId);
        switch(squad.order){
            case ATTACK_BASE:
            case ATTACK_PRODUCTION:
            case ATTACK_UNIT_PRODUCTION:
                if(squad.attackTile != null){
                    out[0] = squad.attackTile.drawx();
                    out[1] = squad.attackTile.drawy();
                }
                break;
            case ATTACK_UNITS:
                if(squad.attackUnit != null && !squad.attackUnit.isDead() && squad.attackUnit.isAdded()){
                    out[0] = squad.attackUnit.x;
                    out[1] = squad.attackUnit.y;
                }
                break;
            case PURSUE:
                if(squad.pursuitTarget != null && !squad.pursuitTarget.isDead() && squad.pursuitTarget.isAdded()){
                    out[0] = squad.pursuitTarget.x;
                    out[1] = squad.pursuitTarget.y;
                }
                break;
            case DEFEND_HIVE:
                if(defendTarget != null && !defendTarget.isDead() && defendTarget.isAdded()){
                    out[0] = defendTarget.x;
                    out[1] = defendTarget.y;
                }else if(defendX != 0f || defendY != 0f){
                    out[0] = defendX;
                    out[1] = defendY;
                }
                break;
            default:
                if(squad.nodeCount > 0){
                    int idx = Math.min(squad.nodeIndex, squad.nodeCount - 1);
                    out[0] = squad.patrolX[idx];
                    out[1] = squad.patrolY[idx];
                }
                break;
        }
    }

    /**Restores the transient squad scheduling state after a save load.*/
    static void onLoaded(){
        resetState();
    }

    public static void reset(){
        resetState();
    }

    private static void resetState(){
        currentCommand = UnitCommand.attack;
        enemyNearbyTimer = 0;
        rosterCoreCount = -1;
        rosterEvo = -1;
        baseX = 0f;
        baseY = 0f;
        enemyProducers = 0;
        enemyUnitFactories = 0;
        clusterTarget = null;
        nextClusterScan = 0f;
        hiveUnderAttack = false;
        defendTarget = null;
        defendX = 0f;
        defendY = 0f;
        defendGiveupTimer = 0f;
        lastAttackNotifyTime = -MassAIConfig.DEFEND_NOTIFY_INTERVAL;
        enemyCoresScratch.clear();
        clusterUnits.clear();
        enemyScratch.clear();
        massUnitsScratch.clear();
        activeSquads.clear();
        squadTypeMaps.clear();
        unitSquadAssignments.clear();
        tempAssignments.clear();
    }
}