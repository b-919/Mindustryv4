package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;

/** Tunable constants for the Mass AI. All values match the pre-refactor hard-coded ones. */
public class MassAIConfig{
    //timing, in ticks (60 per second)
    public static final float SUBSYSTEM_INTERVAL = 5f * 60f;
    public static final float UNIT_QUEUE_INTERVAL = 2f * 60f;
    public static final float PERIODIC_TURRET_MIN = 15f * 60f;
    public static final float PERIODIC_TURRET_MAX = 30f * 60f;
    public static final float DAMAGE_TURRET_MIN = 10f * 60f;
    public static final float DAMAGE_TURRET_MAX = 35f * 60f;
    public static final float INFECTION_MIN = 5f * 60f * 60f; //todo set
    public static final float INFECTION_MAX = 40f * 60f * 60f; //todo set
    public static final float LINE_ACTION_INTERVAL = 60f;
    public static final float LINE_IDLE_TIME = 30f * 60f;
    public static final float DRILL_STUCK_TIME = 15f * 60f;

    //squads
    public static final float ROSTER_INTERVAL = 4f * 60f; //todo set
    public static final float ORDER_REFRESH_MIN = 40f * 60f;
    public static final float ORDER_REFRESH_MAX = 90f * 60f;
    public static final float TARGET_REFRESH_INTERVAL = 4f * 60f;
    public static final float NODE_REFRESH_INTERVAL = 12f * 60f;
    public static final float NODE_REACH = 12f;
    public static final float ATTACK_ARRIVE_DIST = 14f; //todo set
    public static final float DEFEND_RADIUS = 25f;
    public static final float DEFEND_SCAN = 200f * 2f; //todo set
    public static final float DEFEND_ALERT_RADIUS = 40f;
    public static final float DEFEND_GIVEUP_TIME = 30f;
    public static final float DEFEND_GIVEUP_ORDER_TIME = 60f;
    public static final float DEFEND_NOTIFY_INTERVAL = 5f;
    public static final float ATTACK_UNITS_EXPOSED_RADIUS = 20f;
    public static final int UNIT_CLUSTER_MIN = 3;
    public static final float UNIT_CLUSTER_RADIUS = 16f;
    public static final float UNIT_CLUSTER_SCAN_INTERVAL = 2f * 60f;
    public static final int ENEMY_UNIT_SCAN_MAX = 160;
    public static final float PRODUCTION_SCAN_RADIUS = 32f;
    public static final float PATROL_HIVE_RADIUS = 55f;
    public static final float PATROL_OUTSIDE_EXTRA = 35f;
    public static final float PATROL_OUTSIDE_SPACING = 30f;
    public static final float PATROL_ENGAGE_RADIUS = 24f;
    public static final float PURSUIT_GIVEUP_TIME = 10f;
    public static final float PURSUIT_MIN_TIME = 1f;
    public static final float PERIODIC_TURRET_AIR_CHANCE = 0.3f;

    //defense
    public static final float ENEMY_SCAN_SIZE = 200f * 2f;
    public static final int TURRET_LIMIT_PER_CORE = 10;
    public static final int DAMAGE_TURRET_SEARCH_RANGE = 10;
    public static final int TURRET_PLACE_ATTEMPTS = 20;
    public static final ItemStack[] COST_EVIL_RIPPLE = {new ItemStack(Items.chromium, 7), new ItemStack(Items.thorium, 7)};
    public static final ItemStack[] COST_EVIL_FUSE = {new ItemStack(Items.thorium, 6)};
    public static final ItemStack[] COST_EVIL_SALVO = {new ItemStack(Items.titanium, 5)};
    public static final ItemStack[] COST_EVIL_SCATTER = {new ItemStack(Items.scrap, 5)};
    public static final ItemStack[] COST_EVIL_CYCLONE = {new ItemStack(Items.thorium, 10), new ItemStack(Items.titanium, 10)};
    public static final ItemStack[] COST_NONE = {};

    //economy
    public static final int SPAWNER_RADIUS = 30;
    public static final int HIVE_SPAWNER_LIMIT = 10;
    public static final int AIR_SPAWNER_LIMIT = 10;
    public static final int HEAVY_SPAWNER_LIMIT = 2;
    public static final int HIVE_SPAWNER_COST = 10; //copper
    public static final int AIR_SPAWNER_COST = 10; //lead
    public static final int HEAVY_SPAWNER_COST = 25; //corrupted biomatter
    public static final int EXPAND_BIOMASS_COST = 20; //corrupted biomatter
    public static final int UNIT_QUEUE_TARGET = 3;
    //public static final int UNIT_QUEUE_ATTEMPTS = 10;
    public static final int MAX_CORES = 10;
    public static final int CORE_MIN_DISTANCE = 75;
    public static final int MAP_EDGE_MARGIN = 5;
    public static final int SPAWNER_PLACE_ATTEMPTS = 40;
    public static final int INITIAL_HIVE_ATTEMPTS = 50;
    public static final int INFECTED_SAMPLE_ATTEMPTS = 50;
    public static final int EXPAND_LINE_SAMPLE = 20;
    public static final int EXPAND_ATTEMPTS = 20;
    public static final int EXPAND_RANDOM_RADIUS = 30;
    public static final int ORE_SEARCH_RADIUS = 10;
    public static final int CORE_PROXIMITY_TILES = 40;
    //public static final int TURRET_SEEK_RANGE = 10;
    public static final Item[] TARGET_ORES = {Items.scrap, Items.lead, Items.copper, Items.coal, Items.titanium, Items.thorium, Items.chromium};
    public static final int[] ORE_LIMITS = {6, 6, 6, 5, 4, 4, 4};

    //building lines
    public static final int LINE_MAX_LENGTH = 100;
    public static final int LINE_GROW_AMOUNT = 10;
    public static final int LINE_REBUILD_ATTEMPTS = 5;
    public static final int LINE_DIVISIONS = 20;
    public static final int MIN_DIVISIONS_FOR_BRANCH = 3;
    public static final float LINE_BRANCH_CHANCE = 0.1f;
    public static final float LINE_SUBDIVIDE_CHANCE = 0.2f;

    //pathfinding
    public static final int BFS_MAX_NODES = 2000;

    /**Grace period length in minutes for a difficulty; 5 minutes when unknown.*/
    public static float graceMinutes(Difficulty difficulty){
        float minutes = 5f;
        if(difficulty == null) return minutes;
        minutes = switch (difficulty) {
            case training -> 10f;
            case easy -> 7f;
            case normal -> 5f;
            case hard -> 3f;
            case insane -> 1f;
            case eradication -> 0.5f;
        };
        return minutes;
    }

    /**Base biomass generator limit per core for a difficulty; 3 when unknown.*/
    public static int biomassGeneratorBaseLimit(Difficulty difficulty){
        int baseLimit = 3;
        if(difficulty == null) return baseLimit;
        baseLimit = switch (difficulty) {
            case training -> 1;
            case easy -> 2;
            case normal -> 3;
            case hard -> 4;
            case insane -> 6;
            case eradication -> 10;
        };
        return baseLimit;
    }

    /**Number of squads generated per hive for a difficulty; 4 (normal default) when unknown.*/
    public static int squadsPerHive(Difficulty difficulty){
        int squads = 4;
        if(difficulty == null) return squads;
        squads = switch (difficulty) {
            case training -> 2;
            case easy -> 3;
            case normal -> 4;
            case hard -> 6;
            case insane -> 8;
            case eradication -> 16;
        };
        return squads;
    }

    /**Maximum number of hives/cores the mass may hold for a difficulty; 10 (normal default) when unknown.*/
    public static int hiveLimit(Difficulty difficulty){
        int limit = MAX_CORES;
        if(difficulty == null) return limit;
        limit = switch (difficulty) {
            case training -> 4;
            case easy -> 8;
            case normal -> 10;
            case hard -> 12;
            case insane -> 14;
            case eradication -> 24;
        };
        return limit;
    }

    //adrenaline
    /**Enemy units inside a hive radius needed to trigger adrenaline mode.*/
    public static final int ADRENALINE_ENEMY_THRESHOLD = 10;
    /**Radius (in tiles) around a hive checked for enemies when detecting adrenaline mode.*/
    public static final float ADRENALINE_RADIUS = 40f;
    /**How often (ticks) adrenaline mode is re-evaluated.*/
    public static final float ADRENALINE_INTERVAL = 30f;
    /**Turret (pending-build) delay reduction while a hive is in adrenaline mode.*/
    public static final float ADRENALINE_TURRET_SPEED = 2f;
    /**Production speed bonus for biobulbs/tumors in adrenaline mode.*/
    public static final float ADRENALINE_BIO_SPEED = 1.5f;
    /**Production speed bonus for unit factories in adrenaline mode.*/
    public static final float ADRENALINE_FACTORY_SPEED = 1.2f;

    //intel
    /**How often (in ticks) enemy composition is re-scanned.*/
    public static final float INTEL_INTERVAL = 60f;
    /**Max enemy units scanned per intel update.*/
    public static final int INTEL_SCAN_MAX = 400;
    /**Heat added to the threat map per damage event.*/
    public static final float THREAT_HEAT_PER_HIT = 0.34f;
    /**Minimum heat for the threat map to count as a hotspot.*/
    public static final float THREAT_HOTSPOT_MIN = 0.5f;
    /**Seconds for a full threat heat value to decay back to zero.*/
    public static final float THREAT_DECAY_TIME = 12f;
}
