package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;

/** Tunable constants for the Mass AI. All values match the pre-refactor hard-coded ones. */
public class MassAIConfig{
    //timing, in ticks (60 per second)
    public static final float subsystemInterval = 5f * 60f;
    public static final float unitQueueInterval = 2f * 60f;
    public static final float periodicTurretMin = 15f * 60f;
    public static final float periodicTurretMax = 30f * 60f;
    public static final float damageTurretMin = 10f * 60f;
    public static final float damageTurretMax = 35f * 60f;
    public static final float responseMin = 8f * 60f;
    public static final float responseMax = 15f * 60f;
    public static final float subsectionConncheckInterval = 60f * 60f;
    public static final float infectionMin = 5f * 60f * 60f;
    public static final float infectionMax = 40f * 60f * 60f;
    public static final float lineActionInterval = 60f;
    public static final float lineIdleTime = 30f * 60f;
    public static final float drillStuckTime = 15f * 60f;

    //squads
    public static final float orderRefreshMin = 40f * 60f;
    public static final float orderRefreshMax = 90f * 60f;
    public static final float targetRefreshInterval = 4f * 60f;
    public static final float nodeRefreshInterval = 12f * 60f;
    public static final float nodeReach = 12f;
    public static final float defendRadius = 25f;
    public static final float defendScan = 200f * 2f;
    public static final float defendAlertRadius = 40f;
    public static final float defendGiveupTime = 30f * 60f; //seconds
    public static final float defendGiveupOrderTime = 60f * 60f; //seconds
    public static final float defendNotifyInterval = 5f * 60f; //seconds
    public static final float attackUnitsExposedRadius = 20f;
    public static final int unitClusterMin = 3;
    public static final float unitClusterRadius = 16f;
    public static final float unitClusterScanInterval = 2f * 60f;
    public static final int enemyUnitScanMax = 160;
    public static final float productionScanRadius = 32f;
    public static final float patrolHiveRadius = 55f;
    public static final float patrolOutsideExtra = 35f;
    public static final float patrolOutsideSpacing = 30f;
    public static final float patrolOrbitRadius = 1.5f; //tiles
    public static final float patrolOrbitSpeed = 0.6f; //degrees per tick
    public static final float pursueRangeMultiplier = 2.5f;
    public static final float retaliateTime = 15f * 60f;
    public static final float patrolReturnTime = 15f * 60f;
    public static final float pursuitGiveupTime = 10f * 60f; //seconds
    public static final float pursuitMinTime = 1f * 60f; //seconds
    public static final float periodicTurretAirChance = 0.3f;

    //defense
    public static final int turretLimitPerCore = 10;
    public static final int damageTurretSearchRange = 10;
    public static final int turretPlaceAttempts = 20;
    public static final ItemStack[] costEvilRipple = {stack(Items.chromium, 7), stack(Items.thorium, 7)};
    public static final ItemStack[] costEvilFuse = {stack(Items.thorium, 6)};
    public static final ItemStack[] costEvilSalvo = {stack(Items.titanium, 5)};
    public static final ItemStack[] costEvilScatter = {stack(Items.scrap, 5)};
    public static final ItemStack[] costEvilCyclone = {stack(Items.thorium, 10), stack(Items.titanium, 10)};
    public static final ItemStack[] costNone = {};

    //economy
    public static final int spawnerRadius = 30;
    public static final int hiveSpawnerLimit = 10;
    public static final int airSpawnerLimit = 10;
    public static final int heavySpawnerLimit = 2;
    public static final int hiveSpawnerCost = 10; //copper
    public static final int airSpawnerCost = 10; //lead
    public static final int heavySpawnerCost = 25; //corrupted biomatter
    public static final int expandBiomassCost = 20; //corrupted biomatter
    public static final int unitQueueTarget = 3;
    public static final int maxCores = 10;
    public static final int coreMinDistance = 75;
    public static final int mapEdgeMargin = 5;
    public static final int spawnerPlaceAttempts = 40;
    public static final int initialHiveAttempts = 50;
    public static final int infectedSampleAttempts = 50;
    public static final int expandLineSample = 20;
    public static final int expandAttempts = 20;
    public static final int expandRandomRadius = 30;
    public static final int oreSearchRadius = 10;
    public static final Item[] targetOres = {Items.scrap, Items.lead, Items.copper, Items.coal, Items.titanium, Items.thorium, Items.chromium};
    public static final int[] oreLimits = {6, 6, 6, 5, 4, 4, 4};

    //building lines
    public static final int lineMaxLength = 100;
    public static final int lineGrowAmount = 10;
    public static final int lineRebuildAttempts = 5;
    public static final int lineDivisions = 20;
    public static final int minDivisionsForBranch = 3;

    //vein growth
    public static final int veinBranchAfter = 5;
    public static final int veinBranchCost = 3;
    public static final float veinBranchChance = 0.3f;
    public static final float veinMergeChance = 0.4f;
    public static final float veinTurnPenalty = 1.1f;
    public static final float veinSpacingPenalty = 2.2f;
    public static final float veinOreAvoidPenalty = 8f;
    public static final int veinStraightAttempt = 6;
    public static final float veinStraightPenalty = 2.2f;
    public static final float veinAttractWeight = 0.75f;
    public static final float veinNoise = 0.4f;
    public static final int veinAttractRefresh = 4;
    public static final int veinAttractMaxAnchors = 300;
    public static final int veinMaxBlockedTurns = 3;

    //pathfinding
    public static final int bfsMaxNodes = 2000;

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
        int limit = maxCores;
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
    public static final int adrenalineEnemyThreshold = 10;
    /**Radius (in tiles) around a hive checked for enemies when detecting adrenaline mode.*/
    public static final float adrenalineRadius = 40f;
    /**How often (ticks) adrenaline mode is re-evaluated.*/
    public static final float adrenalineInterval = 30f;
    /**Turret (pending-build) delay reduction while a hive is in adrenaline mode.*/
    public static final float adrenalineTurretSpeed = 2f;
    /**Production speed bonus for biobulbs/tumors in adrenaline mode.*/
    public static final float adrenalineBioSpeed = 1.5f;
    /**Production speed bonus for unit factories in adrenaline mode.*/
    public static final float adrenalineFactorySpeed = 1.2f;

    //intel
    /**How often (in ticks) enemy composition is re-scanned.*/
    public static final float intelInterval = 60f;
    /**Max enemy units scanned per intel update.*/
    public static final int intelScanMax = 400;
    /**Heat added to the threat map per damage event.*/
    public static final float threatHeatPerHit = 0.34f;
    /**Minimum heat for the threat map to count as a hotspot.*/
    public static final float threatHotspotMin = 0.5f;
    /**Seconds for a full threat heat value to decay back to zero.*/
    public static final float threatDecayTime = 12f * 60f;

    private static ItemStack stack(Item item, int amount){
        return new ItemStack(item, amount);
    }
}
