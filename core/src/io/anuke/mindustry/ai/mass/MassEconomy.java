package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongSet;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.CraftingBlocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.content.blocks.UnitBlocks;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.storage.CoreBlock;
import io.anuke.mindustry.world.blocks.units.UnitHiveSpawner;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

import java.util.Arrays;

import static io.anuke.mindustry.Vars.unitGroups;
import static io.anuke.mindustry.Vars.world;
import static io.anuke.mindustry.ai.mass.MassAIConfig.ORE_LIMITS;
import static io.anuke.mindustry.ai.mass.MassAIConfig.TARGET_ORES;

/** Spawner placement, drill subsections, generator placement, and core expansion. */
public class MassEconomy{
    static float spawnTimer = 0;
    static float unitQueueTimer = 0;
    static int[] oreBoosts = new int[7];
    static ObjectMap<Tile, Boolean> coreExpanded = new ObjectMap<>();
    static Array<SubSection> activeSubsections = new Array<>();
    //spawner centers placed by the mass, rebuilt lazily once per world and updated incrementally on placement
    private static ObjectSet<Tile> spawners = new ObjectSet<>();
    private static boolean spawnersScanned = false;
    //squad-driven spawning state: what the squads want vs what is already alive/queued
    private static final ObjectIntMap<UnitType> unitRequests = new ObjectIntMap<>();
    private static final ObjectIntMap<UnitType> inFlight = new ObjectIntMap<>();
    private static final ObjectIntMap<UnitType> remaining = new ObjectIntMap<>();
    private static final Array<UnitType> priorityTypes = new Array<>();

    public static void update(ObjectSet<Tile> cores){
        spawnTimer += Timers.delta();
        if(spawnTimer >= MassAIConfig.SUBSYSTEM_INTERVAL){
            spawnTimer = 0;
            trySpawnSubsection();
            checkCoreExpansion();
            trySpawnSpawners();
            trySpawnBiomassGenerators();
        }

        unitQueueTimer += Timers.delta();
        if(unitQueueTimer >= MassAIConfig.UNIT_QUEUE_INTERVAL){
            unitQueueTimer = 0;
            tryQueueUnits();
        }

        for(int i = activeSubsections.size - 1; i >= 0; i--){
            SubSection s = activeSubsections.get(i);
            s.update();
            if(s.failed){
                activeSubsections.removeIndex(i);
            }
        }
    }

    static void forgetCore(Tile tile){
        coreExpanded.remove(tile);
    }

    private static void trySpawnSubsection(){
        int coreCount = Vars.state.teams.get(Team.themass).cores.size;
        for(int i = 0; i < TARGET_ORES.length; i++){
            Item ore = TARGET_ORES[i];
            int limit = ORE_LIMITS[i] * coreCount;

            int activeDrills = 0;
            int building = 0;
            for(SubSection s : activeSubsections){
                if(s.targetOre == ore){
                    if(s.drillPlaced) activeDrills++;
                    else building++;
                }
            }

            if(activeDrills + building < limit){
                // If 0 active drills or boosted, try to spawn up to 2 at once if is possible
                int toSpawn = (activeDrills == 0 || oreBoosts[i] > 0) ? 2 : 1;
                if(oreBoosts[i] > 0) oreBoosts[i]--;

                for(int j = 0; j < toSpawn; j++){
                    if(activeDrills + building < limit){
                        if(spawnForOre(ore)){
                            building++;
                        }else{
                            break;
                        }
                    }
                }
            }
        }
    }

    private static boolean spawnForOre(Item ore){
        Tile oreTile = find2x2Ore(ore);
        if(oreTile == null) return false;

        Array<Tile> p = MassBuilder.findPathToAnyLine(oreTile);
        if(p != null && p.size > 1){
            Tile startTile = p.get(0);
            Array<Tile> path = new Array<>();
            for(int i = 1; i < p.size; i++){
                path.add(p.get(i));
            }

            activeSubsections.add(new SubSection(startTile, oreTile, ore, path));
            for(BuildingLine line : MassBuilder.activeLines){
                if(line.containsTile(startTile)){
                    line.lastSubsectionTimer = 0;
                    break;
                }
            }
            return true;
        }
        return false;
    }

    static void boostOre(Item ore){
        for(int i = 0; i < TARGET_ORES.length; i++){
            if(TARGET_ORES[i] == ore){
                oreBoosts[i] = 2;
                break;
            }
        }
    }

    private static Tile find2x2Ore(Item item){
        Tile bestTile = null;
        float bestDist = Float.MAX_VALUE;

        ObjectSet<Tile> orePositions = Vars.world.indexer.getOrePositions(item);
        if(orePositions.size == 0) return null;

        // Find nearest line/core for each ore quadrant
        for(Tile quadTile : orePositions){
            float minSourceDist = Float.MAX_VALUE;

            // check distance to cores
            for(Tile core : Vars.state.teams.get(Team.themass).cores){
                float dist = Mathf.dst(core.x - quadTile.x, core.y - quadTile.y);
                if(dist < minSourceDist) minSourceDist = dist;
            }

            // Check distance to all conveyor lines
            for(BuildingLine line : MassBuilder.activeLines){
                for(PathTile pt : line.tiles){
                    float dist = Mathf.dst(pt.tile.x - quadTile.x, pt.tile.y - quadTile.y);
                    if(dist < minSourceDist) minSourceDist = dist;
                }
            }

            if(minSourceDist < bestDist){
                // if quad is good search for random/nearby ores
                for(int x = quadTile.x - MassAIConfig.ORE_SEARCH_RADIUS; x < quadTile.x + MassAIConfig.ORE_SEARCH_RADIUS; x++){
                    for(int y = quadTile.y - MassAIConfig.ORE_SEARCH_RADIUS; y < quadTile.y + MassAIConfig.ORE_SEARCH_RADIUS; y++){
                        if(MassUtil.isValid2x2(x, y, item)){
                            bestTile = world.tile(x, y);
                            bestDist = minSourceDist;
                            x = quadTile.x + MassAIConfig.ORE_SEARCH_RADIUS;
                            break;
                        }
                    }
                }
            }
        }
        return bestTile;
    }

    private static void trySpawnBiomassGenerators(){
        int coreCount = Vars.state.teams.get(Team.themass).cores.size;
        int baseLimit = MassAIConfig.biomassGeneratorBaseLimit(Vars.state.difficulty); // limit increases per active hives/cores as usual

        int limit = baseLimit * coreCount;

        int count = 0;
        for(SubSection s : activeSubsections){
            if(s.targetBlock == CraftingBlocks.biomassGenerator){
                count++;
            }
        }

        if(count < limit){
            placeBiomassGenerator();
        }
    }

    private static void placeBiomassGenerator(){
        LongSet infected = Vars.infection.getInfectedQueue();
        if(infected.size == 0) return;

        // makes the thing spawn in infected tiles
        LongSet.LongSetIterator it = infected.iterator();
        int size = infected.size;
        for(int i = 0; i < MassAIConfig.INFECTED_SAMPLE_ATTEMPTS; i++){
            int targetIdx = Mathf.random(size - 1);
            long packed = -1;
            it.reset();
            for(int j = 0; j <= targetIdx && it.hasNext; j++){
                packed = it.next();
            }

            if(packed == -1) continue;
            Tile target = world.tile(packed);

            if(target != null && target.block() == Blocks.air && target.floor().placeableOn && !target.floor().isLiquid && target.isInfected){
                boolean occluded = false;
                int bsize = CraftingBlocks.biomassGenerator.size;
                int offset = -(bsize - 1) / 2;

                for(int dx = 0; dx < bsize; dx++){
                    for(int dy = 0; dy < bsize; dy++){
                        Tile t = world.tile(target.x + offset + dx, target.y + offset + dy);
                        if(t == null || t.block() != Blocks.air || t.floor().isLiquid || !t.floor().placeableOn || !t.isInfected){
                            occluded = true;
                            break;
                        }
                    }
                    if(occluded) break;
                }

                if(!occluded){
                    Array<Tile> path = MassBuilder.findPathToAnyLine(target);
                    if(path != null && path.size > 1){
                        Tile start = path.get(0);
                        Array<Tile> p = new Array<>();
                        for(int k = 1; k < path.size; k++){
                            p.add(path.get(k));
                        }
                        activeSubsections.add(new SubSection(start, target, CraftingBlocks.biomassGenerator, p));
                        return;
                    }
                }
            }
        }
    }

    private static void ensureSpawnersScanned(){
        if(spawnersScanned) return;
        spawnersScanned = true;
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                Tile t = world.tile(x, y);
                if(t != null && t.getTeam() == Team.themass && t.block() instanceof UnitHiveSpawner){
                    spawners.add(t);
                }
            }
        }
    }

    private static void trySpawnSpawners(){
        ensureSpawnersScanned();
        for(Tile core : Vars.state.teams.get(Team.themass).cores){
            ItemModule items = core.entity.items;

            int hiveSpawners = 0;
            int airSpawners = 0;
            int heavySpawners = 0;
            int radius = MassAIConfig.SPAWNER_RADIUS;

            // count existing spawners near the core (only centers are tracked, matching the old 1-per-multiblock count)
            for(Tile t : spawners){
                if(Math.abs(t.x - core.x) <= radius && Math.abs(t.y - core.y) <= radius){
                    if(!spawnerAlive(t)) continue;
                    if(t.block() == UnitBlocks.hiveSpawner) hiveSpawners++;
                    else if(t.block() == UnitBlocks.airHiveSpawner) airSpawners++;
                    else if(t.block() == UnitBlocks.heavyHiveSpawner) heavySpawners++;
                }
            }
            // max 10 spawn per core (if not welcome to unbalanced hell)
            if(hiveSpawners < MassAIConfig.HIVE_SPAWNER_LIMIT && items.has(Items.copper, MassAIConfig.HIVE_SPAWNER_COST)){
                if(placeRandomSpawner(core, UnitBlocks.hiveSpawner, radius)){
                    items.remove(Items.copper, MassAIConfig.HIVE_SPAWNER_COST);
                }
            }

            if(airSpawners < MassAIConfig.AIR_SPAWNER_LIMIT && items.has(Items.lead, MassAIConfig.AIR_SPAWNER_COST)){
                if(placeRandomSpawner(core, UnitBlocks.airHiveSpawner, radius)){
                    items.remove(Items.lead, MassAIConfig.AIR_SPAWNER_COST);
                }
            }

            if(heavySpawners < MassAIConfig.HEAVY_SPAWNER_LIMIT && items.has(Items.corruptedbiomatter, MassAIConfig.HEAVY_SPAWNER_COST)){
                if(placeRandomSpawner(core, UnitBlocks.heavyHiveSpawner, radius)){
                    items.remove(Items.corruptedbiomatter, MassAIConfig.HEAVY_SPAWNER_COST);
                }
            }
        }
    }

    private static boolean spawnerAlive(Tile t){
        return t.getTeam() == Team.themass && t.block() instanceof UnitHiveSpawner;
    }

    private static boolean placeRandomSpawner(Tile core, Block spawner, int radius){
        for(int i = 0; i < MassAIConfig.SPAWNER_PLACE_ATTEMPTS; i++){
            int tx = core.x + Mathf.random(-radius, radius);
            int ty = core.y + Mathf.random(-radius, radius);
            Tile target = world.tile(tx, ty);

            if(target != null && target.block() == Blocks.air && target.floor().placeableOn && !target.floor().isLiquid){
                boolean occluded = false;
                int size = spawner.size;
                int offset = -(size - 1) / 2;

                for(int dx = 0; dx < size; dx++){
                    for(int dy = 0; dy < size; dy++){
                        Tile t = world.tile(tx + offset + dx, ty + offset + dy);
                        if(t == null || t.block() != Blocks.air || t.floor().isLiquid || !t.floor().placeableOn){
                            occluded = true;
                            break;
                        }
                    }
                    if(occluded) break;
                }

                if(!occluded){
                    Vars.world.setBlock(target, spawner, Team.themass);
                    spawners.add(target);
                    return true;
                }
            }
        }
        return false;
    }

    private static void tryQueueUnits(){
        ensureSpawnersScanned();
        if(MassSquads.activeSquads.size == 0) return;

        MassSquads.computeUnitRequests(unitRequests);
        if(unitRequests.size == 0) return;

        countInFlight();

        remaining.clear();
        for(ObjectIntMap.Entry<UnitType> entry : unitRequests.entries()){
            int r = entry.value - inFlight.get(entry.key, 0);
            if(r > 0) remaining.put(entry.key, r);
        }
        if(remaining.size == 0) return;

        //economy: spawn the highest-value roles first so limited queue slots are not filled by cheap units
        priorityTypes.clear();
        for(UnitType type : remaining.keys()){
            priorityTypes.add(type);
        }
        priorityTypes.sort((a, b) -> Integer.compare(unitPriority(b), unitPriority(a)));

        for(UnitType type : priorityTypes){
            int missing = remaining.get(type, 0);
            if(missing <= 0) continue;

            for(Tile t : spawners){
                if(missing <= 0) break;
                if(!spawnerAlive(t)) continue;
                UnitHiveSpawner spawner = (UnitHiveSpawner) t.block();
                UnitHiveSpawner.UnitHiveSpawnerEntity entity = t.entity();
                if(entity == null) continue;

                int evo = spawner.getHiveEvolution(t);
                for(int idx = 0; idx < spawner.types.length; idx++){
                    if(spawner.getType(idx) != type) continue;
                    if(!spawner.isTypeUnlocked(idx, evo)) break;
                    while(missing > 0 && entity.queue.size < MassAIConfig.UNIT_QUEUE_TARGET){
                        if(!spawner.addToQueue(t, idx)) break;
                        missing--;
                        remaining.put(type, missing);
                    }
                    break;
                }
            }
        }
    }

    /**Role priority for queueing: heavy/high-value combat units first, cheap disposable units last.
     *  Intel bonus: while the enemy is air-heavy, float intercept-capable flyers up the queue.*/
    private static int unitPriority(UnitType type){
        int p = basePriority(type);
        if(type != null && type.isFlying && MassIntel.enemyAirRatio() > 0.6f) p += 12;
        return p;
    }

    private static int basePriority(UnitType type){
        if(type == UnitTypes.exterminatorBiomass) return 100;
        if(type == UnitTypes.artilleryBiomass) return 90;
        if(type == UnitTypes.evilTanky) return 80;
        if(type == UnitTypes.evilDagger) return 60;
        if(type == UnitTypes.evilWraith) return 55;
        if(type == UnitTypes.FlyingExplosiveBiomass) return 50;
        if(type == UnitTypes.acidMosquito) return 45;
        if(type == UnitTypes.explosiveBiomass) return 40;
        if(type == UnitTypes.evilSwarmDrone) return 30;
        return 20;
    }

    private static void countInFlight(){
        MassEconomy.inFlight.clear();
        for(BaseUnit unit : unitGroups[Team.themass.ordinal()].all()){
            if(unit != null && unit.isAdded() && !unit.isDead() && unit.getType() != null){
                MassEconomy.inFlight.getAndIncrement(unit.getType(), 0, 1);
            }
        }
        for(Tile t : spawners){
            if(!spawnerAlive(t)) continue;
            UnitHiveSpawner spawner = (UnitHiveSpawner) t.block();
            UnitHiveSpawner.UnitHiveSpawnerEntity entity = t.entity();
            if(entity == null || entity.queue.size == 0) continue;
            for(int i = 0; i < entity.queue.size; i++){
                UnitType type = spawner.getType(entity.queue.get(i));
                if(type != null) MassEconomy.inFlight.getAndIncrement(type, 0, 1);
            }
        }
    }

    private static void checkCoreExpansion(){
        if(Vars.state.teams.get(Team.themass).cores.size >= MassAIConfig.hiveLimit(Vars.state.difficulty)) return;

        for(Tile core : Vars.state.teams.get(Team.themass).cores){
            if(coreExpanded.get(core, false)) continue;

            Array<BuildingLine> lines = new Array<>();
            for(BuildingLine line : MassBuilder.activeLines){
                if(line.core == core){
                    lines.add(line);
                }
            }

            if(lines.size > 0){
                // Try to expand
                if(tryExpandCore(core, lines)){
                    coreExpanded.put(core, true);
                }
            }
        }
    }

    private static boolean tryExpandCore(Tile core, Array<BuildingLine> lines){
        if(!core.entity.items.has(Items.corruptedbiomatter, MassAIConfig.EXPAND_BIOMASS_COST)) return false;

        // Shuffle lines to pick a random one that works
        // if They still spawning the core in random places so this is mostly useless
        lines.shuffle();

        Block hive = StorageBlocks.hive;
        int size = hive.size;
        int offset = -(size - 1) / 2;

        for(BuildingLine line : lines){
            if(line.tiles.size == 0) continue;

            // Prioritize tiles further from the core
            Array<PathTile> lineTiles = new Array<>(line.tiles);
            lineTiles.sort((a, b) -> {
                float d1 = Mathf.dst(a.tile.x - core.x, a.tile.y - core.y);
                float d2 = Mathf.dst(b.tile.x - core.x, b.tile.y - core.y);
                return Float.compare(d2, d1);
            });

            for(int i = 0; i < Math.min(lineTiles.size, MassAIConfig.EXPAND_LINE_SAMPLE); i++){
                Tile base = lineTiles.get(i).tile;

                // If the line tile itself is near void/edge, skip it
                if(MassUtil.isNearEdge(base, MassAIConfig.MAP_EDGE_MARGIN)) continue;

                // Try several random offsets from this line tile
                for(int j = 0; j < MassAIConfig.EXPAND_ATTEMPTS; j++){
                    int tx = base.x + Mathf.random(-MassAIConfig.EXPAND_RANDOM_RADIUS, MassAIConfig.EXPAND_RANDOM_RADIUS);
                    int ty = base.y + Mathf.random(-MassAIConfig.EXPAND_RANDOM_RADIUS, MassAIConfig.EXPAND_RANDOM_RADIUS);

                    if(canPlaceCore(tx, ty)){
                        core.entity.items.remove(Items.corruptedbiomatter, MassAIConfig.EXPAND_BIOMASS_COST);

                        Tile target = world.tile(tx, ty);

                        // clears the area to set the core (this useless but well in case of a bug or something)
                        for(int dx = 0; dx < size; dx++){
                            for(int dy = 0; dy < size; dy++){
                                Tile t = world.tile(tx + offset + dx, ty + offset + dy);
                                if(t != null && t.block() != Blocks.air){
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

    private static boolean canPlaceCore(int x, int y){
        Block hive = StorageBlocks.hive;
        int size = hive.size;
        int offset = -(size - 1) / 2;

        // Ensure not too close to map edge (they're still placing cores near edges, but this is mostly to prevent them to spawn DIRECTLY on the edge)
        if(x + offset < MassAIConfig.MAP_EDGE_MARGIN || y + offset < MassAIConfig.MAP_EDGE_MARGIN ||
            x + offset + size > world.width() - MassAIConfig.MAP_EDGE_MARGIN || y + offset + size > world.height() - MassAIConfig.MAP_EDGE_MARGIN) return false;

        for(int dx = 0; dx < size; dx++){
            for(int dy = 0; dy < size; dy++){
                Tile t = world.tile(x + offset + dx, y + offset + dy);
                if(t == null || MassUtil.isNearEnemyCore(t) || MassUtil.isNearMassCore(t, MassAIConfig.CORE_MIN_DISTANCE)) return false;

                // void/Liquid check
                if(t.floor().isLiquid || !t.floor().placeableOn) return false;

                // allow placing over any themass building EXCEPT other cores and buildings from other teams
                if(t.block() != Blocks.air){
                    if(t.getTeam() != Team.themass) return false;
                    if(t.block().isMultiblock() && t.target().block() instanceof CoreBlock) return false;
                    if(t.block() instanceof CoreBlock) return false;
                }
            }
        }
        return true;
    }

    public static void reset(){
        spawnTimer = 0;
        unitQueueTimer = 0;
        Arrays.fill(oreBoosts, 0);
        coreExpanded.clear();
        activeSubsections.clear();
        spawners.clear();
        spawnersScanned = false;
        unitRequests.clear();
        inFlight.clear();
        remaining.clear();
    }
}