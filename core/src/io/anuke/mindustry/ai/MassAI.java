package io.anuke.mindustry.ai;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.*;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.CraftingBlocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.content.blocks.ProductionBlocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.game.EventType.WorldLoadEvent;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.content.blocks.UnitBlocks;
import io.anuke.mindustry.world.blocks.units.UnitHiveSpawner;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.content.blocks.TurretBlocks;
import io.anuke.mindustry.type.AmmoEntry;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.world.blocks.defense.turrets.ItemTurret;
import io.anuke.mindustry.world.blocks.defense.turrets.Turret.TurretEntity;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Rock;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.core.Timers;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.entities.units.UnitCommand;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.BaseUnit;
import com.badlogic.gdx.math.Rectangle;
import io.anuke.mindustry.world.blocks.defense.turrets.Turret;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Log;

import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;
import static io.anuke.mindustry.Vars.unitGroups;
/* DID xD
    Make them more aggressive - (add vein like system that goes directly to enemy blocks and damage them)
   make them spawn Units, done
   Finish the Mass Like buildings (mostly looking like the The Flesh That Hates mod from MC) done, thx t
   Make them spawn turrets when they are under attack - add a trySpawnTurret done, thank to good
   make them randomly spawn turrets done
   Their Units should Defend the Hive Cores - command blocks have retreat / attack / patrol setting my idea is to make the enemy cores run this done
   Add Grace time done (unfinished for hive spawners)
   done
*/
public class MassAI {
    public static boolean debug = false;
    private static ObjectSet<Tile> initializedCores = new ObjectSet<>();
    private static Array<BuildingLine> activeLines = new Array<>();
    private static Array<SubSection> activeSubsections = new Array<>();
    private static float spawnTimer = 0;
    private static float unitQueueTimer = 0;
    private static float turretTimer = 0;
    private static float nextTurretTime = 0;
    private static float damageTurretTimer = 0;
    private static float nextDamageTurretTime = 0;
    private static Array<PendingBuild> pendingBuilds = new Array<>();
    private static final Item[] targetOres = {Items.scrap, Items.lead, Items.copper, Items.coal, Items.titanium, Items.thorium, Items.chromium};
    private static final int[] oreLimits = {6, 6, 6, 5, 4, 4, 4};
    private static int[] oreBoosts = new int[7];
    private static ObjectMap<Tile, Boolean> coreExpanded = new ObjectMap<>();
    private static UnitCommand currentCommand = UnitCommand.attack;
    private static float commandTimer = 0;
    private static float enemyNearbyTimer = 0;
    private static float squadTaskTimer = 0f;
    private static float nextSquadTaskTime = 45f * 60f;
    private static final int squadSize = 6;
    private static final Array<SquadOrder> squadOrders = new Array<>();
    private static final ObjectIntMap<BaseUnit> unitSquadAssignments = new ObjectIntMap<>();
    private static Rectangle rect = new Rectangle();
    private static boolean enemyNearby = false;
    public static float nextInfectionTime = 0;
    private static boolean disableGrace = false;

    static {
        Events.on(WorldLoadEvent.class, event -> {
            initializedCores.clear();
            activeLines.clear();
            activeSubsections.clear();
            coreExpanded.clear();
            spawnTimer = 0;
            unitQueueTimer = 0;
            turretTimer = 0;
            damageTurretTimer = 0;
            nextDamageTurretTime = 0;
            pendingBuilds.clear();
            nextTurretTime = Mathf.random(15f, 30f) * 60f;

            if (nextInfectionTime <= 0) {// only initialize if not already set by loading
                nextInfectionTime = Mathf.random(5f, 40f) * 60f * 60f;
            }
            disableGrace = false;
            for (int i = 0; i < oreBoosts.length; i++) oreBoosts[i] = 0;
            currentCommand = UnitCommand.patrol;
            commandTimer = 0;
            enemyNearbyTimer = 0;
            squadTaskTimer = 0f;
            nextSquadTaskTime = Mathf.random(30f, 75f) * 60f;
            squadOrders.clear();
            unitSquadAssignments.clear();
            enemyNearby = false;
        });
    }

    public static void write(DataOutputStream stream) throws IOException {
        stream.writeInt(initializedCores.size);
        for (Tile core : initializedCores) {
            stream.writeLong(core.packedPosition());
        }

        stream.writeInt(activeLines.size);
        for (BuildingLine line : activeLines) {
            line.write(stream);
        }

        stream.writeInt(activeSubsections.size);
        for (SubSection sub : activeSubsections) {
            sub.write(stream);
        }

        stream.writeFloat(spawnTimer);
        stream.writeFloat(turretTimer);
        stream.writeFloat(nextTurretTime);
        stream.writeFloat(damageTurretTimer);
        stream.writeFloat(nextDamageTurretTime);
        stream.writeFloat(nextInfectionTime);

        stream.writeInt(pendingBuilds.size);
        for (PendingBuild build : pendingBuilds) {
            build.write(stream);
        }

        for (int boost : oreBoosts) {
            stream.writeInt(boost);
        }

        stream.writeInt(coreExpanded.size);
        for (ObjectMap.Entry<Tile, Boolean> entry : coreExpanded.entries()) {
            stream.writeLong(entry.key.packedPosition());
            stream.writeBoolean(entry.value);
        }

        stream.writeInt(currentCommand.ordinal());
        stream.writeFloat(commandTimer);
        stream.writeFloat(enemyNearbyTimer);
        stream.writeBoolean(enemyNearby);
    }

    public static void read(DataInputStream stream) throws IOException {
        int coreCount = stream.readInt();
        initializedCores.clear();
        for (int i = 0; i < coreCount; i++) {
            initializedCores.add(world.tile(stream.readLong()));
        }

        int lineCount = stream.readInt();
        activeLines.clear();
        for (int i = 0; i < lineCount; i++) {
            activeLines.add(BuildingLine.read(stream));
        }

        int subCount = stream.readInt();
        activeSubsections.clear();
        for (int i = 0; i < subCount; i++) {
            activeSubsections.add(SubSection.read(stream));
        }

        spawnTimer = stream.readFloat();
        turretTimer = stream.readFloat();
        nextTurretTime = stream.readFloat();
        damageTurretTimer = stream.readFloat();
        nextDamageTurretTime = stream.readFloat();
        nextInfectionTime = stream.readFloat();
        disableGrace = true;

        int pendingCount = stream.readInt();
        pendingBuilds.clear();
        for (int i = 0; i < pendingCount; i++) {
            pendingBuilds.add(PendingBuild.read(stream));
        }

        for (int i = 0; i < oreBoosts.length; i++) {
            oreBoosts[i] = stream.readInt();
        }

        int expandedCount = stream.readInt();
        coreExpanded.clear();
        for (int i = 0; i < expandedCount; i++) {
            coreExpanded.put(world.tile(stream.readLong()), stream.readBoolean());
        }

        currentCommand = UnitCommand.values()[stream.readInt()];
        commandTimer = stream.readFloat();
        enemyNearbyTimer = stream.readFloat();
        squadTaskTimer = 0f;
        nextSquadTaskTime = Mathf.random(30f, 75f) * 60f;
        squadOrders.clear();
        unitSquadAssignments.clear();
        enemyNearby = stream.readBoolean();
    }

    private static int countTurrets(boolean air) {
        int count = 0;
        Team massTeam = Team.themass;
        for (int x = 0; x < world.width(); x++) {
            for (int y = 0; y < world.height(); y++) {
                Tile tile = world.tile(x, y);
                if (tile != null && tile.getTeam() == massTeam) {
                    Block block = tile.block();
                    if (air) {
                        if (block == TurretBlocks.evilScatter || block == TurretBlocks.evilCyclone) {
                            count++;
                        }
                    } else {
                        if (block == TurretBlocks.evilDuo || block == TurretBlocks.evilSalvo || 
                            block == TurretBlocks.evilRipple || block == TurretBlocks.evilFuse) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static int countNearbyCores(Tile near) {
        int count = 0;
        Team massTeam = Team.themass;
        ObjectSet<Tile> cores = Vars.state.teams.get(massTeam).cores;
        float radius = 40; // in tiles
        for (Tile core : cores) {
            if (Mathf.dst(core.x - near.x, core.y - near.y) <= radius) {
                count++;
            }
        }
        return count;
    }

    public static void update() {
        if(Net.client()) return;
        if (Vars.state.isPaused() || Vars.state.teams == null) return;
        if(Settings.getBool("massai-debug", false) != debug){
            setDebug(Settings.getBool("massai-debug", false));
        }

        ObjectSet<Tile> cores = Vars.state.teams.get(Team.themass).cores;

        if (Vars.state.allowMassInfection && cores.size == 0) {
            nextInfectionTime -= Timers.delta();
            //System.out.println("Timer time is " + nextInfectionTime);
            if (nextInfectionTime <= 0) {
                nextInfectionTime = Mathf.random(5f, 40f) * 60f * 60f;
                spawnInitialHive();
                debugLog("infection timer reached zero, spawning initial hive");
                cores = Vars.state.teams.get(Team.themass).cores;
            }
        }/* else if (!Vars.state.allowMassInfection && cores.size == 0) {
            nextInfectionTime = Mathf.random(5f, 40f) * 60f * 60f;
            System.out.println("Timer time is " + nextInfectionTime);
        }*/
        // Remove tracking for destroyed cores
        ObjectSet.ObjectSetIterator<Tile> it = initializedCores.iterator();
        while (it.hasNext) {
            Tile tile = it.next();
            if (!cores.contains(tile)) {
                it.remove();
                coreExpanded.remove(tile);
            }
        }

        // Remove lines belonging to destroyed cores btw some line will keep respawning after the core is destroyed, for this destroy all cores/hives
        for(int i = activeLines.size - 1; i >= 0; i--){
            if(!cores.contains(activeLines.get(i).core)){
                activeLines.removeIndex(i);
            }
        }

        for (Tile core : cores) {
            if (!initializedCores.contains(core)) {
                startBuilding(core);
                debugLog("core initialized at ({0},{1})", core.x, core.y);
                initializedCores.add(core);
            }
        }

        for(int i = activeLines.size - 1; i >= 0; i--){
            BuildingLine line = activeLines.get(i);
            line.update();
        }

        spawnTimer += Timers.delta();
        turretTimer += Timers.delta();
        damageTurretTimer += Timers.delta();

        for (int i = pendingBuilds.size - 1; i >= 0; i--) {
            PendingBuild build = pendingBuilds.get(i);
            build.timer += Timers.delta();
            if (build.timer >= build.delay) {
                build.place();
                pendingBuilds.removeIndex(i);
            }
        }

        if (turretTimer >= nextTurretTime) {
            turretTimer = 0;
            nextTurretTime = Mathf.random(15f, 30f) * 60f;
            debugLog("periodic turret attempt targetAirRandom");
            trySpawnTurret(false, Mathf.chance(0.3), 0, 0);
        }

        if (spawnTimer >= 5f * 60f) {
            spawnTimer = 0;
            trySpawnSubsection();
            checkCoreExpansion();
            trySpawnSpawners();
            trySpawnBiomassGenerators();
        }

        unitQueueTimer += Timers.delta();
        if (unitQueueTimer >= 2f * 60f) {
            unitQueueTimer = 0;
            tryQueueUnits();
        }

        for (int i = activeSubsections.size - 1; i >= 0; i--) {
            SubSection s = activeSubsections.get(i);
            s.update();
            if (s.failed) {
                activeSubsections.removeIndex(i);
            }
        }

        // default global command fallback for biomass when not split by squads
        if (cores.size == 0) {
            currentCommand = UnitCommand.retreat;
        } else {
            commandTimer += Timers.delta();

            boolean foundEnemy = false;
            for (Tile core : cores) {
                rect.setSize(200f * 2f).setCenter(core.worldx(), core.worldy());
                enemyNearby = false;
                Units.getNearbyEnemies(Team.themass, rect, u -> enemyNearby = true);
                if (enemyNearby) {
                    foundEnemy = true;
                    break;
                }
            }

            if (foundEnemy) {
                currentCommand = UnitCommand.patrol;
                enemyNearbyTimer = 0;
                debugLog("global fallback command set to patrol due to nearby enemy");
            } else if (currentCommand == UnitCommand.patrol) {
                enemyNearbyTimer += Timers.delta();
                // after 15-30 seconds with no enemies they will command attack AND command if the initial grace period is over
                if (enemyNearbyTimer >= Mathf.random(15f, 30f) * 60f && !isGracePeriod()) {
                    currentCommand = UnitCommand.attack;
                    debugLog("global fallback command set to attack after patrol timeout");
                }
            } else if (!isGracePeriod() && currentCommand != UnitCommand.attack) {
                currentCommand = UnitCommand.attack;
                debugLog("global fallback command forced to attack (grace over)");
            }
        }

        updateSquadOrders(cores, currentCommand, foundAnyEnemyNearCore(cores));
    }

    private static void updateSquadOrders(ObjectSet<Tile> cores, UnitCommand fallbackCommand, boolean enemyNearCore){
        Array<BaseUnit> massUnits = new Array<>();
        for(BaseUnit unit : unitGroups[Team.themass.ordinal()].all()){
            if(unit != null && unit.isAdded() && !unit.isDead()){
                massUnits.add(unit);
            }
        }

        if(massUnits.size == 0){
            squadOrders.clear();
            unitSquadAssignments.clear();
            return;
        }

        int squadCount = Math.max(1, (massUnits.size + squadSize - 1) / squadSize);
        if(squadOrders.size < squadCount){
            for(int i = squadOrders.size; i < squadCount; i++){
                squadOrders.add(new SquadOrder(i));
            }
        }else if(squadOrders.size > squadCount){
            while(squadOrders.size > squadCount){
                squadOrders.pop();
            }
        }

        squadTaskTimer += Timers.delta();
        if(squadTaskTimer >= nextSquadTaskTime){
            squadTaskTimer = 0f;
            nextSquadTaskTime = Mathf.random(30f, 75f) * 60f;
            assignSquadTasks(cores, enemyNearCore, fallbackCommand);
        }else{
            for(SquadOrder order : squadOrders){
                if(order.task == null){
                    order.task = SquadTask.PATROL_HIVES;
                    order.command = UnitCommand.attack;
                }
            }
        }

        for(int i = 0; i < massUnits.size; i++){
            BaseUnit unit = massUnits.get(i);
            SquadOrder order = squadOrders.get(Math.min(i / squadSize, squadOrders.size - 1));
            unitSquadAssignments.put(unit, order.id);
            if(order.task == SquadTask.SUPPLY_DEFENSE && Mathf.chance(0.01f)){
                trySpawnTurret(false, Mathf.chance(0.4), unit.x, unit.y);
                debugLog("squad={0} task=supply-defense action=trySpawnTurret unit=({1},{2})", order.id, (int)unit.x, (int)unit.y);
            }
            if(unit.getCommand() != order.command){
                unit.onCommand(order.command);
                debugLog("unit={0} squad={1} command={2} task={3}", unit.getID(), order.id, order.command, order.task);
            }
        }
    }

    private static void assignSquadTasks(ObjectSet<Tile> cores, boolean enemyNearCore, UnitCommand fallbackCommand){
        for(int i = 0; i < squadOrders.size; i++){
            SquadOrder order = squadOrders.get(i);
            if(order.task != null){
                continue;
            }
            SquadTask task = rollTaskForSquad(cores, enemyNearCore, fallbackCommand, i);
            order.task = task;
            order.command = task.command;
            debugLog("squad={0} assignedTask={1} command={2} enemyNearCore={3} cores={4}", order.id, task, task.command, enemyNearCore, cores.size);
        }
    }

    private static SquadTask rollTaskForSquad(ObjectSet<Tile> cores, boolean enemyNearCore, UnitCommand fallbackCommand, int squadIndex){
        FloatArray weights = new FloatArray(SquadTask.all.length);
        float total = 0f;

        for(SquadTask task : SquadTask.all){
            if(task.maxSquads != -1 && countTaskAssignments(task) >= task.maxSquads){
                weights.add(0f);
                continue;
            }
            float weight = task.basePriority;
            if(task == SquadTask.ATTACK_PLAYER_BASE){
                if(isGracePeriod()) weight *= 0.15f;
                if(enemyNearCore) weight *= 0.65f;
                if(fallbackCommand == UnitCommand.attack) weight *= 1.6f;
            }else if(task == SquadTask.DEFEND_HIVES){
                if(enemyNearCore) weight *= 2.5f;
                if(cores.size <= 1) weight *= 1.4f;
            }else if(task == SquadTask.PATROL_HIVES){
                if(enemyNearCore) weight *= 1.3f;
                if(fallbackCommand == UnitCommand.patrol) weight *= 1.2f;
            }else if(task == SquadTask.SUPPLY_DEFENSE){
                if(cores.size == 0) weight = 0.01f;
                int groundTurrets = countTurrets(false);
                if(groundTurrets < Math.max(4, cores.size * 4)) weight *= 2.0f;
                if(enemyNearCore) weight *= 1.7f;
            }

            // keep at least one attacking squad after grace if many squads exist
            if(task == SquadTask.ATTACK_PLAYER_BASE && !isGracePeriod() && squadOrders.size >= 3 && squadIndex == 0){
                weight *= 1.8f;
            }

            total += weight;
            weights.add(weight);
        }

        if(total <= 0.001f){
            return fallbackCommand == UnitCommand.attack ? SquadTask.ATTACK_PLAYER_BASE :
                   fallbackCommand == UnitCommand.retreat ? SquadTask.DEFEND_HIVES :
                   SquadTask.PATROL_HIVES;
        }

        float value = Mathf.random(total);
        float cursor = 0f;
        for(int i = 0; i < SquadTask.all.length; i++){
            cursor += weights.get(i);
            if(value <= cursor){
                return SquadTask.all[i];
            }
        }
        return SquadTask.PATROL_HIVES;
    }

    private static int countTaskAssignments(SquadTask task){
        int count = 0;
        for(SquadOrder order : squadOrders){
            if(order.task == task) count++;
        }
        return count;
    }

    private static boolean foundAnyEnemyNearCore(ObjectSet<Tile> cores){
        for(Tile core : cores){
            rect.setSize(200f * 2f).setCenter(core.worldx(), core.worldy());
            enemyNearby = false;
            Units.getNearbyEnemies(Team.themass, rect, u -> enemyNearby = true);
            if(enemyNearby){
                return true;
            }
        }
        return false;
    }

    public static void setDebug(boolean enabled){
        debug = enabled;
        Settings.putBool("massai-debug", enabled);
        Log.info("[MassAI] debug={0}", enabled);
    }

    public static void drawDebugOverlay(){
        if(!debug || Vars.headless || Vars.state == null || Vars.state.isPaused()) return;

        IntMap<Array<BaseUnit>> bySquad = new IntMap<>();
        for(BaseUnit unit : unitGroups[Team.themass.ordinal()].all()){
            if(unit == null || !unit.isAdded() || unit.isDead()) continue;
            int squadId = unitSquadAssignments.get(unit, -1);
            if(squadId < 0) continue;
            Array<BaseUnit> list = bySquad.get(squadId);
            if(list == null){
                list = new Array<>();
                bySquad.put(squadId, list);
            }
            list.add(unit);
        }

        Lines.stroke(1.2f);
        for(IntMap.Entry<Array<BaseUnit>> entry : bySquad.entries()){
            int squadId = entry.key;
            Array<BaseUnit> units = entry.value;
            if(units.size == 0) continue;

            float cx = 0f, cy = 0f;
            for(BaseUnit unit : units){
                cx += unit.x;
                cy += unit.y;
            }
            cx /= units.size;
            cy /= units.size;

            Draw.color(Color.valueOf("7efcff"));
            Lines.circle(cx, cy, 8f + Mathf.absin(Timers.time(), 6f, 2f));

            for(BaseUnit unit : units){
                Lines.line(cx, cy, unit.x, unit.y);
                Draw.color(Color.WHITE);
                Draw.text("SQ " + squadId, unit.x, unit.y + 11f);
                Draw.color(Color.valueOf("7efcff"));
            }
        }
        Draw.reset();
    }

    private static void debugLog(String text, Object... args){
        if(!debug) return;
        Log.info("[MassAI] " + text, args);
    }

    public static void onDamage() {
        float grace = getGraceTime();
        if (commandTimer < grace) {
            commandTimer = grace;
            debugLog("onDamage: grace skipped, commandTimer set to {0}", commandTimer);
        }
    }

    public static float getGraceTime() {
        if (Vars.state == null || Vars.state.difficulty == null) return 5f * 60f * 60f;
        
        float minutes = 5f;
        switch (Vars.state.difficulty) {
            case training: minutes = 10f; break;
            case easy: minutes = 7f; break;
            case normal: minutes = 5f; break;
            case hard: minutes = 3f; break;
            case insane: minutes = 1f; break;
            case eradication: minutes = 0.5f; break;
        }
        return minutes * 60f * 60f;
    }

    public static boolean isGracePeriod() {
        if (disableGrace) return false;
        return commandTimer < getGraceTime();
    }

    public static void spawnInitialHive() {
        if (Vars.state.teams.get(Team.themass).cores.size > 0) return;

        Tile playerCore = null;
        for (Player player : Vars.players) {
            if (player != null && player.getClosestCore() != null) {
                playerCore = player.getClosestCore().tile;
                break;
            }
        }
        if (playerCore == null) return;

        Tile spawn = null;
        float maxDist = 0;

        for (int i = 0; i < 50; i++) {
            int x = Mathf.random(world.width() - 1);
            int y = Mathf.random(world.height() - 1);
            Tile tile = world.tile(x, y);

            if (tile != null && tile.block() == Blocks.air && !tile.floor().solid && !isNearEnemyCore(tile)) {
                float dist = Mathf.dst(x - playerCore.x, y - playerCore.y);
                if (dist > maxDist) {
                    maxDist = dist;
                    spawn = tile;
                }
            }
        }

        if (spawn != null) {
            world.setBlock(spawn, StorageBlocks.hive, Team.themass);
            debugLog("initial hive spawned at ({0},{1})", spawn.x, spawn.y);
            if (!Vars.headless) {
                String msg = Mathf.chance(0.01) ? Bundles.get("text.biomass.alert.rare") : Bundles.get("text.biomass.alert");
                Vars.ui.hudfrag.showBiomassAlert(msg);
            }
        }
    }

    public static void trySpawnTurret(boolean fromDamage, boolean targetAir, float targetX, float targetY) {
        if (fromDamage && damageTurretTimer < nextDamageTurretTime) return;

        Team massTeam = Team.themass;
        ObjectSet<Tile> cores = Vars.state.teams.get(massTeam).cores;
        if (cores.size == 0) return;

        // determine which turret to spawn based on resources in the nearest core, it will always choose the most lowcost one and efficient against the target
        // if unit is aerial will spawn turrets that targets air, same if the target is ground unit
        Tile checkTile = fromDamage ? world.tile((int)(targetX / Vars.tilesize), (int)(targetY / Vars.tilesize)) : cores.first();
        if (checkTile == null) checkTile = cores.first();
        Tile nearestCore = findClosestCore(checkTile, massTeam);
        if (nearestCore == null) return;

        if (!fromDamage) {
            int coreCount = countNearbyCores(nearestCore);
            int limit = 10 * Math.max(1, coreCount);
            if (countTurrets(targetAir) >= limit) return;
        }

        ItemModule items = nearestCore.entity.items;

        Block turretBlock = TurretBlocks.evilDuo;
        if (targetAir) {
            if (items.has(Items.thorium, 10) && items.has(Items.titanium, 10)) {
                turretBlock = TurretBlocks.evilCyclone;
            } else if (items.has(Items.scrap, 5)) {
                turretBlock = TurretBlocks.evilScatter;
            }
        } else {
            if (items.has(Items.chromium, 7) && items.has(Items.thorium, 7)) {
                turretBlock = TurretBlocks.evilRipple;
            } else if (items.has(Items.thorium, 6)) {
                turretBlock = TurretBlocks.evilFuse;
            } else if (items.has(Items.titanium, 5)) {
                turretBlock = TurretBlocks.evilSalvo;
            }
        }

        int size = turretBlock.size;

        // checks spawns
        Array<Tile> potentialBases = new Array<>();
        if (fromDamage) {
            int rx = (int)(targetX / Vars.tilesize);
            int ry = (int)(targetY / Vars.tilesize);
            int range = 10;
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    Tile t = world.tile(rx + x, ry + y);
                    if (t != null && t.getTeam() == massTeam && t.block() != Blocks.air) {
                        potentialBases.add(t);
                    }
                }
            }
        }
        
        if (potentialBases.size == 0) {
            for (Tile core : cores) potentialBases.add(core);
            for (int i = 0; i < Math.min(activeLines.size, 10); i++) {
                BuildingLine line = activeLines.random();
                if (line.tiles.size > 0) potentialBases.add(line.tiles.random().tile);
            }
        }

        if (potentialBases.size == 0) return;

        for (int i = 0; i < 20; i++) {
            Tile base = potentialBases.random();
            int rotation = Mathf.random(3);
            int offset = 2 + (size / 2);
            Tile target = world.tile(base.x + Geometry.d4[rotation].x * offset, base.y + Geometry.d4[rotation].y * offset);

            if (target != null && isAreaClear(target, size) && !isNearEnemyCore(target)) {
                if (fromDamage) {
                    float dist = Mathf.dst(target.worldx() - targetX, target.worldy() - targetY);
                    if (dist > turretBlock.viewRange) continue;
                }

                if (turretBlock == TurretBlocks.evilRipple) {
                    items.remove(Items.chromium, 7);
                    items.remove(Items.thorium, 7);
                } else if (turretBlock == TurretBlocks.evilFuse) {
                    items.remove(Items.thorium, 6);
                } else if (turretBlock == TurretBlocks.evilSalvo) {
                    items.remove(Items.titanium, 5);
                } else if (turretBlock == TurretBlocks.evilScatter) {
                    items.remove(Items.scrap, 5);
                } else if (turretBlock == TurretBlocks.evilCyclone) {
                    items.remove(Items.thorium, 10);
                    items.remove(Items.titanium, 10);
                }

                Array<Tile> veinPath = findPathToAnyLine(target);
                if (veinPath != null) {
                    // start from the building line(aka vein lines) and build towards the turret (first tile)
                    for (int j = veinPath.size - 1; j >= 0; j--) {
                        Tile vt = veinPath.get(j);
                        if (vt.block() == Blocks.air) {
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
                pendingBuilds.add(new PendingBuild(target, turretBlock, massTeam, 0, turretDelay, fromDamage, targetX, targetY));
                debugLog("queued turret build block={0} at ({1},{2}) delay={3}", turretBlock.name, target.x, target.y, turretDelay);

                if (fromDamage) {
                    damageTurretTimer = 0;
                    nextDamageTurretTime = Mathf.random(10f, 35f) * 60f;
                }

                return;
            }
        }
    }

    private static boolean isAreaClear(Tile center, int size) {
        int offset = -(size - 1) / 2;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                Tile t = world.tile(center.x + offset + dx, center.y + offset + dy);
                if (t == null || t.block() != Blocks.air || t.floor().isLiquid || !t.floor().placeableOn) {
                    return false;
                }

                // check if there is no line or building, THIS TO PREVENT TURRETS SPAWNING ON LINES AND CUTTING THE FULL FLOW OF RESOURCES
                for (BuildingLine line : activeLines) {
                    if (line.startX == t.x && line.startY == t.y) return false;
                    for (PathTile pt : line.tiles) {
                        if (pt.tile == t) return false;
                    }
                }
            }
        }
        return true;
    }

    private static Tile findClosestCore(Tile tile, Team team) {
        Tile closest = null;
        float minDst = Float.MAX_VALUE;
        for (Tile core : Vars.state.teams.get(team).cores) {
            float dst = Mathf.dst(tile.x - core.x, tile.y - core.y);
            if (dst < minDst) {
                minDst = dst;
                closest = core;
            }
        }
        return closest;
    }

    private static void trySpawnSubsection() {
        int coreCount = Vars.state.teams.get(Team.themass).cores.size;
        for (int i = 0; i < targetOres.length; i++) {
            Item ore = targetOres[i];
            int limit = oreLimits[i] * coreCount;
            
            int activeDrills = 0;
            int building = 0;
            for (SubSection s : activeSubsections) {
                if (s.targetOre == ore) {
                    if (s.drillPlaced) activeDrills++;
                    else building++;
                }
            }

            if (activeDrills + building < limit) {
                // If 0 active drills or boosted, try to spawn up to 2 at once if is possible
                int toSpawn = (activeDrills == 0 || oreBoosts[i] > 0) ? 2 : 1;
                if (oreBoosts[i] > 0) oreBoosts[i]--;
                
                for (int j = 0; j < toSpawn; j++) {
                    if (activeDrills + building < limit) {
                        if (spawnForOre(ore)) {
                            building++;
                        } else {
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void trySpawnBiomassGenerators() {
        int coreCount = Vars.state.teams.get(Team.themass).cores.size;
        int baseLimit = 3;  // limit increases per active hives/cores as uusal
        if (Vars.state.difficulty != null) {
            switch (Vars.state.difficulty) {
                case training: baseLimit = 1; break;
                case easy: baseLimit = 2; break;
                case normal: baseLimit = 3; break;
                case hard: baseLimit = 4; break;
                case insane: baseLimit = 6; break;
                case eradication: baseLimit = 10; break;
            }
        }

        int limit = baseLimit * coreCount;

        int count = 0;
        for (SubSection s : activeSubsections) {
            if (s.targetBlock == CraftingBlocks.biomassGenerator) {
                count++;
            }
        }

        if (count < limit) {
            placeBiomassGenerator();
        }
    }

    private static void placeBiomassGenerator() {
        LongSet infected = Vars.infection.getInfectedQueue();
        if (infected.size == 0) return;

        // makes the thing spawn in infected tiles
        LongSet.LongSetIterator it = infected.iterator();
        int size = infected.size;
        for (int i = 0; i < 50; i++) {
            int targetIdx = Mathf.random(size - 1);
            long packed = -1;
            it.reset();
            for(int j = 0; j <= targetIdx && it.hasNext; j++) {
                packed = it.next();
            }

            if (packed == -1) continue;
            Tile target = world.tile(packed);

            if (target != null && target.block() == Blocks.air && target.floor().placeableOn && !target.floor().isLiquid && target.isInfected) {
                boolean occluded = false;
                int bsize = CraftingBlocks.biomassGenerator.size;
                int offset = -(bsize - 1) / 2;

                for (int dx = 0; dx < bsize; dx++) {
                    for (int dy = 0; dy < bsize; dy++) {
                        Tile t = world.tile(target.x + offset + dx, target.y + offset + dy);
                        if (t == null || t.block() != Blocks.air || t.floor().isLiquid || !t.floor().placeableOn || !t.isInfected) {
                            occluded = true;
                            break;
                        }
                    }
                    if (occluded) break;
                }

                if (!occluded) {
                    Array<Tile> path = findPathToAnyLine(target);
                    if (path != null && path.size > 1) {
                        Tile start = path.get(0);
                        Array<Tile> p = new Array<>();
                        for (int k = 1; k < path.size; k++) {
                            p.add(path.get(k));
                        }
                        activeSubsections.add(new SubSection(start, target, CraftingBlocks.biomassGenerator, p));
                        return;
                    }
                }
            }
        }
    }

    private static void trySpawnSpawners() {
        for (Tile core : Vars.state.teams.get(Team.themass).cores) {
            ItemModule items = core.entity.items;

            int hiveSpawners = 0;
            int airSpawners = 0;
            int heavySpawners = 0;
            int radius = 30;

            // count existing spawners near the core
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    Tile t = world.tile(core.x + dx, core.y + dy);
                    if (t != null && t.getTeam() == Team.themass) {
                        if (t.block() == UnitBlocks.hiveSpawner) hiveSpawners++;
                        else if (t.block() == UnitBlocks.airHiveSpawner) airSpawners++;
                        else if (t.block() == UnitBlocks.heavyHiveSpawner) heavySpawners++;
                    }
                }
            }
            // max 10 spawn per core (if not welcome to unbalanced hell)
            if (hiveSpawners < 10 && items.has(Items.copper, 10)) {
                if (placeRandomSpawner(core, UnitBlocks.hiveSpawner, radius)) {
                    items.remove(Items.copper, 10);
                }
            }

            if (airSpawners < 10 && items.has(Items.lead, 10)) {
                if (placeRandomSpawner(core, UnitBlocks.airHiveSpawner, radius)) {
                    items.remove(Items.lead, 10);
                }
            }

            if (heavySpawners < 2 && items.has(Items.corruptedbiomatter, 25)) {
                if (placeRandomSpawner(core, UnitBlocks.heavyHiveSpawner, radius)) {
                    items.remove(Items.corruptedbiomatter, 5);
                }
            }
        }
    }

    private static boolean placeRandomSpawner(Tile core, Block spawner, int radius) {
        for (int i = 0; i < 40; i++) {
            int tx = core.x + Mathf.random(-radius, radius);
            int ty = core.y + Mathf.random(-radius, radius);
            Tile target = world.tile(tx, ty);

            if (target != null && target.block() == Blocks.air && target.floor().placeableOn && !target.floor().isLiquid) {
                boolean occluded = false;
                int size = spawner.size;
                int offset = -(size - 1) / 2;
                
                for (int dx = 0; dx < size; dx++) {
                    for (int dy = 0; dy < size; dy++) {
                        Tile t = world.tile(tx + offset + dx, ty + offset + dy);
                        if (t == null || t.block() != Blocks.air || t.floor().isLiquid || !t.floor().placeableOn) {
                            occluded = true;
                            break;
                        }
                    }
                    if (occluded) break;
                }

                if (!occluded) {
                    Vars.world.setBlock(target, spawner, Team.themass);
                    return true;
                }
            }
        }
        return false;
    }

    private static void tryQueueUnits() {
        for (Tile core : Vars.state.teams.get(Team.themass).cores) {
            int radius = 30;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    Tile t = world.tile(core.x + dx, core.y + dy);
                    if (t == null || !(t.block() instanceof UnitHiveSpawner)) continue;
                    if (t.getTeam() != Team.themass) continue;

                    UnitHiveSpawner spawner = (UnitHiveSpawner) t.block();
                    UnitHiveSpawner.UnitHiveSpawnerEntity entity = t.entity();
                    if (entity == null) continue;

                    int queueTarget = 3;
                    int attempts = 0;
                    while (entity.queue.size < queueTarget && attempts < 10) {
                        int typeIdx = Mathf.random(0, spawner.types.length - 1);
                        if (!spawner.addToQueue(t, typeIdx)) {
                            attempts++;
                            continue;
                        }
                        attempts = 0;
                    }
                }
            }
        }
    }

    private static void checkCoreExpansion() {
        if (Vars.state.teams.get(Team.themass).cores.size == 10) return;

        for (Tile core : Vars.state.teams.get(Team.themass).cores) {
            if (coreExpanded.get(core, false)) continue;

            Array<BuildingLine> lines = new Array<>();
            for (BuildingLine line : activeLines) {
                if (line.core == core) {
                    lines.add(line);
                }
            }

            if (lines.size > 0) {
                // Try to expand
                if (tryExpandCore(core, lines)) {
                    coreExpanded.put(core, true);
                }
            }
        }
    }

    private static boolean tryExpandCore(Tile core, Array<BuildingLine> lines) {
        if (!core.entity.items.has(Items.corruptedbiomatter, 20)) return false; // biomatter is more logic

        // Shuffle lines to pick a random one that works
        // They still spawning the core in random places so this is mostly useless
        lines.shuffle();
        
        Block hive = StorageBlocks.hive;
        int size = hive.size;
        int offset = -(size - 1) / 2;
        
        for (BuildingLine line : lines) {
            if (line.tiles.size == 0) continue;
            
            // Prioritize tiles further from the core
            Array<PathTile> lineTiles = new Array<>(line.tiles);
            lineTiles.sort((a, b) -> {
                float d1 = Mathf.dst(a.tile.x - core.x, a.tile.y - core.y);
                float d2 = Mathf.dst(b.tile.x - core.x, b.tile.y - core.y);
                return Float.compare(d2, d1);
            });
            
            for (int i = 0; i < Math.min(lineTiles.size, 20); i++) {
                Tile base = lineTiles.get(i).tile;
                
                // If the line tile itself is near void/edge, skip it
                if (isNearEdge(base, 5)) continue;
                
                // Try several random offsets from this line tile
                for (int j = 0; j < 20; j++) {
                    int tx = base.x + Mathf.random(-30, 30);
                    int ty = base.y + Mathf.random(-30, 30);
                    
                    if (canPlaceCore(tx, ty)) {
                        core.entity.items.remove(Items.copper, 20);
                        core.entity.items.remove(Items.lead, 20);
                        
                        Tile target = world.tile(tx, ty);
                        
                        // clears the area to set the core (this useless but well in case of a bug or something)
                        for (int dx = 0; dx < size; dx++) {
                            for (int dy = 0; dy < size; dy++) {
                                Tile t = world.tile(tx + offset + dx, ty + offset + dy);
                                if (t != null && t.block() != Blocks.air) {
                                    world.removeBlock(t);
                                }
                            }
                        }
                        
                        Vars.world.setBlock(target, hive, Team.themass);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean canPlaceCore(int x, int y) {
        Block hive = StorageBlocks.hive;
        int size = hive.size;
        int offset = -(size - 1) / 2;
        
        // Ensure not too close to map edge (they're still placing cores near edges, but this is mostly to prevent them to spawn DIRECTLY on the edge)
        if (x + offset < 5 || y + offset < 5 || x + offset + size > world.width() - 5 || y + offset + size > world.height() - 5) return false;

        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                Tile t = world.tile(x + offset + dx, y + offset + dy);
                if (t == null || isNearEnemyCore(t) || isNearMassCore(t, 75)) return false;
                
                // void/Liquid check
                if (t.floor().isLiquid || !t.floor().placeableOn) return false;
                
                // allow placing over any themass building EXCEPT other cores and buildings from other teams
                if (t.block() != Blocks.air) {
                    if (t.getTeam() != Team.themass) return false;
                    if (t.block().isMultiblock() && t.target().block() instanceof io.anuke.mindustry.world.blocks.storage.CoreBlock) return false;
                    if (t.block() instanceof io.anuke.mindustry.world.blocks.storage.CoreBlock) return false;
                }
            }
        }
        return true;
    }

    private static boolean spawnForOre(Item ore) {
        Tile oreTile = find2x2Ore(ore);
        if (oreTile == null) return false;

        Array<Tile> p = findPathToAnyLine(oreTile);
        if (p != null && p.size > 1) {
            Tile startTile = p.get(0);
            Array<Tile> path = new Array<>();
            for (int i = 1; i < p.size; i++) {
                path.add(p.get(i));
            }

            activeSubsections.add(new SubSection(startTile, oreTile, ore, path));
            for (BuildingLine line : activeLines) {
                if (line.containsTile(startTile)) {
                    line.lastSubsectionTimer = 0;
                    break;
                }
            }
            return true;
        }
        return false;
    }

    private static Array<Tile> findPathToAnyLine(Tile target) {
        Queue<Tile> queue = new Queue<>();
        ObjectMap<Tile, Tile> parents = new ObjectMap<>();
        queue.addLast(target);
        parents.put(target, null);

        while (!queue.isEmpty()) {
            Tile curr = queue.removeFirst();

            //
            if (curr.block() == DistributionBlocks.veins && curr.getTeam() == Team.themass) {
                boolean isLine = false;
                for (BuildingLine line : activeLines) {
                    if (line.containsTile(curr)) {
                        isLine = true;
                        break;
                    }
                }

                if (isLine) {
                    Array<Tile> p = new Array<>();
                    Tile node = curr;
                    while (node != null) {
                        p.add(node);
                        node = parents.get(node);
                    }
                    return p;
                }
            }

            for (int i = 0; i < 4; i++) {
                Tile next = curr.getNearby(Geometry.d4[i]);
                if (next != null && !parents.containsKey(next) && isPassable(next)) {
                    parents.put(next, curr);
                    queue.addLast(next);
                    if (parents.size > 2000) return null;
                }
            }
        }
        return null;
    }

    private static Tile find2x2Ore(Item item) {
        Tile bestTile = null;
        float bestDist = Float.MAX_VALUE;

        ObjectSet<Tile> orePositions = Vars.world.indexer.getOrePositions(item);
        if (orePositions.size == 0) return null;

        // Find nearest line/core for each ore quadrant
        for (Tile quadTile : orePositions) {
            float minSourceDist = Float.MAX_VALUE;
            
            // check distance to cores
            for (Tile core : Vars.state.teams.get(Team.themass).cores) {
                float dist = Mathf.dst(core.x - quadTile.x, core.y - quadTile.y);
                if (dist < minSourceDist) minSourceDist = dist;
            }
            
            // Check distance to all conveyor lines
            for (BuildingLine line : activeLines) {
                for (PathTile pt : line.tiles) {
                    float dist = Mathf.dst(pt.tile.x - quadTile.x, pt.tile.y - quadTile.y);
                    if (dist < minSourceDist) minSourceDist = dist;
                }
            }

            if (minSourceDist < bestDist) {
                // if quad is good search for random/nearby ores
                for (int x = quadTile.x - 10; x < quadTile.x + 10; x++) {
                    for (int y = quadTile.y - 10; y < quadTile.y + 10; y++) {
                        if (isValid2x2(x, y, item)) {
                            bestTile = world.tile(x, y);
                            bestDist = minSourceDist;
                            x = quadTile.x + 10;
                            break;
                        }
                    }
                }
            }
        }
        return bestTile;
    }

    private static void boostOre(Item ore) {
        for (int i = 0; i < targetOres.length; i++) {
            if (targetOres[i] == ore) {
                oreBoosts[i] = 2;
                break;
            }
        }
    }

    private static boolean isValid2x2(int x, int y, Item item) {
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                Tile t = world.tile(x + dx, y + dy);
                if (t == null || t.floor().drops == null || t.floor().drops.item != item || isNearEnemyCore(t)) return false;
                
                Block block = t.block();
                if (block != Blocks.air && !(t.getTeam() == Team.themass && block == DistributionBlocks.veins)) {
                    return false;
                }
            }
        }
        return true;
    }
    // I can't believe this shit is working
    private static boolean isNearMassCore(Tile tile, float radius) {
        if (Vars.state.teams == null) return false;
        ObjectSet<Tile> cores = Vars.state.teams.get(Team.themass).cores;
        if (cores == null) return false;
        for (Tile core : cores) {
            if (Mathf.dst(tile.x - core.x, tile.y - core.y) < radius) return true;
        }
        return false;
    }

    private static boolean isNearEdge(Tile tile, int distance) {
        return tile.x < distance || tile.y < distance || tile.x > world.width() - distance || tile.y > world.height() - distance;
    }

    private static boolean isNearEnemyCore(Tile tile) {
        if (Vars.state.teams == null) return false;
        float radius = Vars.state.mode.enemyCoreBuildRadius;
        for (Team team : Team.all) {
            if (team == Team.themass || team == Team.none) continue;
            ObjectSet<Tile> cores = Vars.state.teams.get(team).cores;
            if (cores == null) continue;
            for (Tile core : cores) {
                if (Mathf.dst(tile.x - core.x, tile.y - core.y) < radius / Vars.tilesize) return true;
            }
        }
        return false;
    }

    private static boolean hasOresNearby(Tile tile, int radius) {
        for (Item item : targetOres) {
            Tile ore = Vars.world.indexer.findClosestOre(tile.worldx(), tile.worldy(), item);
            if (ore != null && Mathf.dst(tile.x - ore.x, tile.y - ore.y) < radius) return true;
        }
        return false;
    }

    private static Array<Tile> findPath(Tile start, Tile target) {
        Queue<Tile> queue = new Queue<>();
        ObjectMap<Tile, Tile> parents = new ObjectMap<>();
        queue.addLast(start);
        parents.put(start, null);

        while (!queue.isEmpty()) {
            Tile curr = queue.removeFirst();
            if (curr.x == target.x && curr.y == target.y) {
                Array<Tile> path = new Array<>();
                while (curr != start) {
                    path.add(curr);
                    curr = parents.get(curr);
                }
                path.reverse();
                return path;
            }

            for (int i = 0; i < 4; i++) {
                Tile next = curr.getNearby(Geometry.d4[i]);
                if (next != null && !parents.containsKey(next) && isPassable(next)) {
                    parents.put(next, curr);
                    queue.addLast(next);
                    if (parents.size > 2000) return null;
                }
            }
        }
        return null;
    }

    private static boolean isPassable(Tile tile) {
        return (tile.block() == Blocks.air || (tile.getTeam() == Team.themass && tile.block() == DistributionBlocks.veins)) && !isNearEnemyCore(tile);
    }

    private static void startBuilding(Tile core) {
        for (int i = 0; i < 4; i++) {
            activeLines.add(new BuildingLine(core, i));
        }
    }

    private static class SubSection {
        Tile startTile;
        final Tile targetTile;
        final Block targetBlock;
        final Item targetOre;
        final Array<Tile> path;
        int progress = 0;
        float timer = 0;
        float stuckTimer = 0;
        boolean drillPlaced = false;
        boolean failed = false;
        boolean destroying = false;

        SubSection(Tile startTile, Tile targetTile, Item targetOre, Array<Tile> path) {
            this(startTile, targetTile, ProductionBlocks.biomassBulb, targetOre, path);
        }

        SubSection(Tile startTile, Tile targetTile, Block targetBlock, Array<Tile> path) {
            this(startTile, targetTile, targetBlock, null, path);
        }

        SubSection(Tile startTile, Tile targetTile, Block targetBlock, Item targetOre, Array<Tile> path) {
            this.startTile = startTile;
            this.targetTile = targetTile;
            this.targetBlock = targetBlock;
            this.targetOre = targetOre;
            this.path = path;
        }

        void update() {
            if (failed) return;

            if (destroying) {
                if (targetTile.block() == Blocks.air || targetTile.entity == null || targetTile.entity.isDead()) {
                    failed = true;
                    return;
                }
                targetTile.entity.damage(20000f);
                return;
            }

            if (drillPlaced) {
                if (targetTile.block() != targetBlock || targetTile.getTeam() != Team.themass) {
                    if (targetOre != null) boostOre(targetOre);
                    failed = true;
                    return;
                }

                // check if it's stuck or (full capacity, biomass generator is considered stuck if capacity = 2)
                if (targetTile.entity != null && targetTile.entity.items.total() >= targetTile.block().itemCapacity) {
                    stuckTimer += Timers.delta();
                    if (stuckTimer >= 15f * 60f) { // 15 seconds
                        // try to reconnect
                        Array<Tile> newPath = findPathToAnyLine(targetTile);
                        if (newPath != null && newPath.size > 1) {
                            this.startTile = newPath.get(0);
                            path.clear();
                            for (int k = 1; k < newPath.size; k++) {
                                path.add(newPath.get(k));
                            }
                            progress = 0;
                            drillPlaced = false;
                            stuckTimer = 0;
                        } else {
                            // FAILED to reconnect -> destroy
                            destroying = true;
                            if (targetOre != null) boostOre(targetOre);
                        }
                    }
                } else {
                    stuckTimer = 0;
                }
                return;
            }

            timer += Timers.delta();
            if (timer >= 60f) {
                timer = 0;
                if (progress < path.size) {
                    Tile next = path.get(progress);
                    if (isNearEnemyCore(next)) {
                        failed = true;
                        return;
                    }
                    if (next.block() == Blocks.air || (next.getTeam() == Team.themass && next.block() == DistributionBlocks.veins)) {
                        Tile prev = (progress == 0) ? startTile : path.get(progress - 1);
                        int rotation = next.relativeTo(prev.x, prev.y);
                        
                        next.setBlock(DistributionBlocks.veins, Team.themass, rotation);
                        progress++;
                    } else {
                        failed = true;
                    }
                } else {
                    if (targetBlock == ProductionBlocks.biomassBulb) {
                        if (isValid2x2(targetTile.x, targetTile.y, targetOre)) {
                            Vars.world.setBlock(targetTile, ProductionBlocks.biomassBulb, Team.themass);
                            drillPlaced = true;
                        } else if (targetTile.block() == ProductionBlocks.biomassBulb && targetTile.getTeam() == Team.themass) {
                            drillPlaced = true; // reconnect the drill
                        } else {
                            failed = true;
                        }
                    } else {
                        Vars.world.setBlock(targetTile, targetBlock, Team.themass);
                        drillPlaced = true;
                    }
                }
            }
        }

        void write(DataOutputStream stream) throws IOException {
            stream.writeLong(startTile.packedPosition());
            stream.writeLong(targetTile.packedPosition());
            stream.writeInt(targetBlock == null ? -1 : targetBlock.id);
            stream.writeInt(targetOre == null ? -1 : targetOre.id);
            stream.writeInt(path.size);
            for (Tile t : path) {
                stream.writeLong(t.packedPosition());
            }
            stream.writeInt(progress);
            stream.writeFloat(timer);
            stream.writeFloat(stuckTimer);
            stream.writeBoolean(drillPlaced);
            stream.writeBoolean(failed);
            stream.writeBoolean(destroying);
        }

        static SubSection read(DataInputStream stream) throws IOException {
            Tile startTile = world.tile(stream.readLong());
            Tile targetTile = world.tile(stream.readLong());
            int blockId = stream.readInt();
            Block targetBlock = blockId == -1 ? null : Vars.content.block(blockId);
            int oreId = stream.readInt();
            Item targetOre = oreId == -1 ? null : Vars.content.item(oreId);
            int pathSize = stream.readInt();
            Array<Tile> path = new Array<>(pathSize);
            for (int i = 0; i < pathSize; i++) {
                path.add(world.tile(stream.readLong()));
            }
            SubSection sub = new SubSection(startTile, targetTile, targetBlock, targetOre, path);
            sub.progress = stream.readInt();
            sub.timer = stream.readFloat();
            sub.stuckTimer = stream.readFloat();
            sub.drillPlaced = stream.readBoolean();
            sub.failed = stream.readBoolean();
            sub.destroying = stream.readBoolean();
            return sub;
        }
    }

    private static class BuildingLine {
        final Tile core;
        final int direction;
        final int dx, dy, rotation;
        int startX, startY;
        int targetLength;
        int divisions = 0;
        boolean subdivided = false;
        Array<PathTile> tiles = new Array<>();
        Array<Tile> pathBuffer = new Array<>();
        float timer = 0;
        float lastSubsectionTimer = 0;
        IntIntMap attempts = new IntIntMap();
        IntSet gaveUp = new IntSet();

        BuildingLine(Tile core, int direction) {
            this(core, direction, -1, -1, 20);
            this.targetLength = 100;  // they sometimes ignore this, this works only on big maps
        }

        BuildingLine(Tile core, int direction, int sx, int sy, int divisions) {
            this.core = core;
            this.direction = direction;
            this.divisions = divisions;
            this.dx = Geometry.d4[direction].x;
            this.dy = Geometry.d4[direction].y;
            this.rotation = (direction + 2) % 4;
            this.targetLength = Mathf.random(40, 100);

            if (sx == -1) {
                int size = core.block().size;
                int offset = (size + 1) / 2;
                this.startX = core.x + dx * offset;
                this.startY = core.y + dy * offset;
            } else {
                this.startX = sx;
                this.startY = sy;
            }
        }

        void update() {
            timer += Timers.delta();
            lastSubsectionTimer += Timers.delta();

            if (timer >= 60f) {
                timer = 0;

                // Try to rebuild broken blocks first
                for (int i = 0; i < tiles.size; i++) {
                    if (gaveUp.contains(i)) continue;

                    if (isBroken(i)) {
                        if (tryRebuild(i)) {
                            attempts.put(i, 0);
                            return; // limits actions per second
                        } else {
                            int a = attempts.get(i, 0) + 1;
                            attempts.put(i, a);
                            if (a >= 5) {
                                gaveUp.add(i);
                                targetLength--;
                            }
                            return;
                        }
                    } else {
                        attempts.put(i, 0);
                    }
                }

                // if idle for too long, branch or grow the line
                // this SHOULD STOP BRANCHING OR GROWING WHEN REACHING THE LIMITS, but it doesn't - Fixed by adding limits to the limits was fucking annoying to see absolute mess of conveyors across the god damn map
                if (lastSubsectionTimer >= 30f * 60f) {
                    lastSubsectionTimer = 0;
                    if (divisions >= 3 && Mathf.chance(0.1) && tiles.size > 5) {
                        branch();
                    } else if (tiles.size < 100) {
                        targetLength = Math.min(100, targetLength + 10);
                    }
                }

                // Increase targetLength if no ores nearby
                if (tiles.size >= targetLength && targetLength < 100) {
                    Tile last = tiles.size == 0 ? world.tile(startX, startY) : tiles.peek().tile;
                    if (last != null && !hasOresNearby(last, 20)) {
                        targetLength = Math.min(100, targetLength + 10);
                    }
                }

                // If no rebuilding is needed, try to expand the line
                if (tiles.size < targetLength && tiles.size < 100) {
                    buildNext();
                }
            }
        }
        //Absolutely this shit is broken
        boolean isBroken(int i) {
            if (i < 0 || i >= tiles.size) return false;
            PathTile pt = tiles.get(i);
            Tile tile = pt.tile;
            return tile == null || tile.block() != DistributionBlocks.veins || tile.getTeam() != Team.themass || tile.getRotation() != pt.rotation;
        }

        boolean tryRebuild(int i) {
            PathTile pt = tiles.get(i);
            Tile tile = pt.tile;
            if (tile != null && tile.block() == Blocks.air) {
                tile.setBlock(DistributionBlocks.veins, Team.themass, pt.rotation);
                return true;
            }
            return false;
        }

        void buildNext() {
            if (pathBuffer.size > 0) {
                Tile next = pathBuffer.removeIndex(0);
                if ((next.block() == Blocks.air || (next.block() instanceof Rock)) && !isNearEnemyCore(next)) {
                    Tile prev = tiles.size == 0 ? world.tile(startX - dx, startY - dy) : tiles.peek().tile;
                    int rot = next.relativeTo(prev.x, prev.y);
                    next.setBlock(DistributionBlocks.veins, Team.themass, rot);
                    tiles.add(new PathTile(next, rot));
                } else {
                    pathBuffer.clear();
                }
                return;
            }

            int tx, ty;
            if (tiles.size == 0) {
                tx = startX;
                ty = startY;
            } else {
                Tile last = tiles.peek().tile;
                tx = last.x + dx;
                ty = last.y + dy;
            }

            if (tx < 0 || ty < 0 || tx >= world.width() || ty >= world.height()) {
                targetLength = tiles.size;
                return;
            }

            Tile tile = world.tile(tx, ty);
            if (tile == null || isNearEnemyCore(tile)) {
                targetLength = tiles.size;
                return;
            }

            if (tile.block() == Blocks.air || (tile.block() instanceof Rock)) {
                tile.setBlock(DistributionBlocks.veins, Team.themass, rotation);
                tiles.add(new PathTile(tile, rotation));
            } else if (tile.block().solid) {
                // Try to pathfind around
                for (int jump = 3; jump <= 7; jump++) {
                    Tile target = world.tile(tx + dx * jump, ty + dy * jump);
                    if (target != null && !target.block().solid && !isNearEnemyCore(target)) {
                        Array<Tile> path = findPath(tiles.size == 0 ? world.tile(startX - dx, startY - dy) : tiles.peek().tile, target);
                        if (path != null) {
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

        /*boolean isAtLimit() { // useless
            return tiles.size >= 100 || (divisions < 3 && (tiles.size >= targetLength || isBlocked()));
        }*/
        boolean isBlocked() { // for future use
            if (tiles.size == 0) return false;
            Tile last = tiles.peek().tile;
            Tile next = world.tile(last.x + dx, last.y + dy);
            return next == null || next.block().solid || isNearEnemyCore(next);
        }

        void subdivide() {
            if (tiles.size == 0 || divisions < 3) return;
            if (!Mathf.chance(0.2)) {
                subdivided = true;
                return;
            }
            subdivided = true;
            Tile last = tiles.peek().tile;
            int d1 = (direction + 1) % 4;
            int d2 = (direction + 3) % 4;
            
            int tx1 = last.x + Geometry.d4[d1].x;
            int ty1 = last.y + Geometry.d4[d1].y;
            if (canStartLine(tx1, ty1)) {
                activeLines.add(new BuildingLine(core, d1, tx1, ty1, divisions - 3));
            }
            
            int tx2 = last.x + Geometry.d4[d2].x;
            int ty2 = last.y + Geometry.d4[d2].y;
            if (canStartLine(tx2, ty2)) {
                activeLines.add(new BuildingLine(core, d2, tx2, ty2, divisions - 3));
            }
        }

        boolean canStartLine(int x, int y) {
            Tile t = world.tile(x, y);
            return t != null && t.block() == Blocks.air && !isNearEnemyCore(t);
        }

        void branch() {
            if (tiles.size < 5 || divisions < 3) return;
            divisions -= 3;
            int index = Mathf.random(tiles.size / 2, tiles.size - 1);
            Tile base = tiles.get(index).tile;

            int d1 = (direction + 1) % 4;
            int d2 = (direction + 3) % 4;
            int dir = Mathf.choose(d1, d2);
            int tx = base.x + Geometry.d4[dir].x;
            int ty = base.y + Geometry.d4[dir].y;
            Tile next = world.tile(tx, ty);

            if (next != null && next.block() == Blocks.air && !isNearEnemyCore(next)) {
                activeLines.add(new BuildingLine(core, dir, tx, ty, divisions - 3));
            }
        }

        boolean containsTile(Tile tile) {
            if (world.tile(startX, startY) == tile) return true;
            for (PathTile pt : tiles) {
                if (pt.tile == tile) return true;
            }
            return false;
        }

        void write(DataOutputStream stream) throws IOException {
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
            for (PathTile pt : tiles) {
                pt.write(stream);
            }

            stream.writeInt(attempts.size);
            for (IntIntMap.Entry entry : attempts.entries()) {
                stream.writeInt(entry.key);
                stream.writeInt(entry.value);
            }

            stream.writeInt(gaveUp.size);
            IntSet.IntSetIterator it = gaveUp.iterator();
            while (it.hasNext) {
                stream.writeInt(it.next());
            }
        }

        static BuildingLine read(DataInputStream stream) throws IOException {
            Tile core = world.tile(stream.readLong());
            int direction = stream.readInt();
            int sx = stream.readInt();
            int sy = stream.readInt();
            int divisions = stream.readInt();

            BuildingLine line = new BuildingLine(core, direction, sx, sy, divisions);
            line.targetLength = stream.readInt();
            line.subdivided = stream.readBoolean();
            line.timer = stream.readFloat();
            line.lastSubsectionTimer = stream.readFloat();

            int tileCount = stream.readInt();
            for (int i = 0; i < tileCount; i++) {
                line.tiles.add(PathTile.read(stream));
            }

            int attemptCount = stream.readInt();
            for (int i = 0; i < attemptCount; i++) {
                line.attempts.put(stream.readInt(), stream.readInt());
            }

            int gaveUpCount = stream.readInt();
            for (int i = 0; i < gaveUpCount; i++) {
                line.gaveUp.add(stream.readInt());
            }

            return line;
        }
    }

    private static class PathTile {
        final Tile tile;
        final int rotation;
        PathTile(Tile tile, int rotation) {
            this.tile = tile;
            this.rotation = rotation;
        }

        void write(DataOutputStream stream) throws IOException {
            stream.writeLong(tile.packedPosition());
            stream.writeInt(rotation);
        }

        static PathTile read(DataInputStream stream) throws IOException {
            return new PathTile(world.tile(stream.readLong()), stream.readInt());
        }
    }

    private static class SquadOrder{
        final int id;
        SquadTask task;
        UnitCommand command = UnitCommand.patrol;

        SquadOrder(int id){
            this.id = id;
        }
    }

    private enum SquadTask{
        ATTACK_PLAYER_BASE(UnitCommand.attack, 1.25f, -1),
        DEFEND_HIVES(UnitCommand.patrol, 1.05f, 3),
        PATROL_HIVES(UnitCommand.patrol, 1.0f, 5),
        SUPPLY_DEFENSE(UnitCommand.patrol, 0.95f, 2);

        static final SquadTask[] all = values();
        final UnitCommand command;
        final float basePriority;
        final int maxSquads;

        SquadTask(UnitCommand command, float basePriority, int maxSquads){
            this.command = command;
            this.basePriority = basePriority;
            this.maxSquads = maxSquads;
        }
    }
    // I think I should use this for more stuff but I'm so lazy to refactor the full code
    private static class PendingBuild {
        Tile tile;
        Block block;
        Team team;
        int rotation;
        float delay;
        float timer;
        boolean fromDamage;
        float targetX, targetY;

        PendingBuild(Tile tile, Block block, Team team, int rotation, float delay) {
            this(tile, block, team, rotation, delay, false, 0, 0);
        }

        PendingBuild(Tile tile, Block block, Team team, int rotation, float delay, boolean fromDamage, float targetX, float targetY) {
            this.tile = tile;
            this.block = block;
            this.team = team;
            this.rotation = rotation;
            this.delay = delay;
            this.fromDamage = fromDamage;
            this.targetX = targetX;
            this.targetY = targetY;
        }

        void write(DataOutputStream stream) throws IOException {
            stream.writeLong(tile == null ? -1L : tile.packedPosition());
            stream.writeInt(block == null ? -1 : block.id);
            stream.writeInt(team == null ? -1 : team.ordinal());
            stream.writeInt(rotation);
            stream.writeFloat(delay);
            stream.writeFloat(timer);
            stream.writeBoolean(fromDamage);
            stream.writeFloat(targetX);
            stream.writeFloat(targetY);
        }

        static PendingBuild read(DataInputStream stream) throws IOException {
            long tilePos = stream.readLong();
            Tile tile = tilePos == -1L ? null : world.tile(tilePos);
            int blockId = stream.readInt();
            Block block = blockId == -1 ? null : Vars.content.block(blockId);
            int teamId = stream.readInt();
            Team team = teamId == -1 ? null : Team.all[teamId];
            int rotation = stream.readInt();
            float delay = stream.readFloat();
            float timer = stream.readFloat();
            boolean fromDamage = stream.readBoolean();
            float targetX = stream.readFloat();
            float targetY = stream.readFloat();

            PendingBuild build = new PendingBuild(tile, block, team, rotation, delay, fromDamage, targetX, targetY);
            build.timer = timer;
            return build;
        }

        void place() {
            if (tile == null) return;
            // does something I forgot
            world.setBlock(tile, block, team);
            if (rotation != 0) tile.setRotation((byte) rotation);

            if (tile.entity instanceof TurretEntity) {
                TurretEntity entity = (TurretEntity) tile.entity;
                if (fromDamage) {
                    entity.rotation = tile.angleTo(targetX, targetY);
                } else {
                    Tile enemyCore = null;
                    float minDst = Float.MAX_VALUE;
                    for (Team t : Team.all) {
                        if (t != team && t != Team.none) {
                            for (Tile core : Vars.state.teams.get(t).cores) {
                                float dst = Mathf.dst(tile.x - core.x, tile.y - core.y);
                                if (dst < minDst) {
                                    minDst = dst;
                                    enemyCore = core;
                                }
                            }
                        }
                    }
                    if (enemyCore != null) entity.rotation = tile.angleTo(enemyCore);
                }

                if (block instanceof ItemTurret) {
                    ItemTurret it = (ItemTurret) block;
                    AmmoType[] types = it.getAmmoTypes();
                    if (types != null && types.length > 0) {
                        AmmoType type = types[0];
                        entity.ammo.add(new AmmoEntry(type, 20));
                    }
                }
            }
        }
    }
}
