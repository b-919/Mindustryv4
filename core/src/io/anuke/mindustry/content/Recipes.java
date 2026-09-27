package io.anuke.mindustry.content;

import io.anuke.mindustry.content.blocks.*;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.TechTree;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.type.Recipe.RecipeVisibility;

import static io.anuke.mindustry.type.Category.*;

public class Recipes implements ContentList{

    @Override
    public void load(){
        //DEBUG
        String classicTech = TechTree.create("Classic");
        new Recipe(turret, TurretBlocks.evilDuo).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(turret, TurretBlocks.evilSalvo).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(turret, TurretBlocks.evilScatter).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(turret, TurretBlocks.evilRipple).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(turret, TurretBlocks.evilCyclone).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(turret, TurretBlocks.evilFuse).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(effect, DebugBlocks.infectiontest).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(units, UnitBlocks.hiveSpawner).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(units, UnitBlocks.airHiveSpawner).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(units, UnitBlocks.heavyHiveSpawner).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(production, CraftingBlocks.biomassGenerator).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(distribution, DistributionBlocks.veins).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(distribution, DistributionBlocks.stackRouter).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(production, ProductionBlocks.biomassBulb).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(production, ProductionBlocks.corruptedcultivator).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(effect, StorageBlocks.hive).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(distribution, DebugBlocks.itemSource).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(distribution, DebugBlocks.itemVoid).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(liquid, DebugBlocks.liquidSource).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(power, DebugBlocks.powerVoid).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(power, DebugBlocks.powerInfinite).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(effect, DebugBlocks.superBooster).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        new Recipe(units, DebugBlocks.debugFactory).setHidden(true).setAlwaysUnlocked(true).setShowIf(m -> m.infiniteResources).joinAllTechTrees();
        //new Recipe(effect, PowerBlocks.lightBlock).setMode(GameMode.sandbox).setAlwaysUnlocked(true);

        /*new Recipe(defense, DefenseBlocks.thoriumWallEXTRALarge).setMode(GameMode.sandbox).setHidden(true).setAlwaysUnlocked(true);*/

        //DEFENSE

        //walls
        new Recipe(defense, DefenseBlocks.stoneWall, stack(Items.stone, 8)).setTechTrees(TechTree.defaultTech,classicTech);
        new Recipe(defense, DefenseBlocks.copperWall, stack(Items.copper, 8)).setAlwaysUnlocked(true);
        new Recipe(defense, DefenseBlocks.copperWallLarge, stack(Items.copper, 8 * 4)).setAlwaysUnlocked(true);

        new Recipe(defense, DefenseBlocks.scrapWall, stack(Items.scrap, 8));
        new Recipe(defense, DefenseBlocks.scrapWallLarge, stack(Items.scrap, 8 * 4));

        new Recipe(defense, DefenseBlocks.denseAlloyWall, stack(Items.densealloy, 8));
        new Recipe(defense, DefenseBlocks.denseAlloyWallLarge, stack(Items.densealloy, 8 * 4));

        new Recipe(defense, DefenseBlocks.door, stack(Items.densealloy, 8), stack(Items.silicon, 8));
        new Recipe(defense, DefenseBlocks.doorLarge, stack(Items.densealloy, 8 * 4), stack(Items.silicon, 8 * 4));

        new Recipe(defense, DefenseBlocks.thoriumWall, stack(Items.thorium, 8));
        new Recipe(defense, DefenseBlocks.thoriumWallLarge, stack(Items.thorium, 8 * 4));

        new Recipe(defense, DefenseBlocks.chromiumWall, stack(Items.chromium, 8));
        new Recipe(defense, DefenseBlocks.chromiumWallLarge, stack(Items.chromium, 8 * 4));

        new Recipe(defense, DefenseBlocks.phaseWall, stack(Items.phasefabric, 8));
        new Recipe(defense, DefenseBlocks.phaseWallLarge, stack(Items.phasefabric, 8 * 4));

        new Recipe(defense, DefenseBlocks.surgeWall, stack(Items.surgealloy, 8));
        new Recipe(defense, DefenseBlocks.surgeWallLarge, stack(Items.surgealloy, 8 * 4));

        //projectors
        new Recipe(effect, DefenseBlocks.mender, stack(Items.copper, 25),  stack(Items.lead, 30));
        new Recipe(effect, DefenseBlocks.mendProjector, stack(Items.lead, 200), stack(Items.densealloy, 150), stack(Items.titanium, 50), stack(Items.silicon, 180));
        new Recipe(effect, DefenseBlocks.overdriveProjector, stack(Items.lead, 200), stack(Items.densealloy, 150), stack(Items.titanium, 150), stack(Items.silicon, 250));
        new Recipe(effect, DefenseBlocks.forceProjector, stack(Items.lead, 200), stack(Items.densealloy, 150), stack(Items.titanium, 150), stack(Items.silicon, 250));

        new Recipe(effect, StorageBlocks.container, stack(Items.densealloy, 200));
        new Recipe(effect, StorageBlocks.vault, stack(Items.densealloy, 500), stack(Items.thorium, 250));
        new Recipe(effect, LogisticBlocks.miningPostT1, stack(Items.densealloy, 150), stack(Items.lead, 50), stack(Items.silicon, 150));
        new Recipe(effect, LogisticBlocks.miningPostT2, stack(Items.densealloy, 450), stack(Items.thorium, 200), stack(Items.silicon, 450))
                .setDependencies(LogisticBlocks.miningPostT1);
        new Recipe(effect, LogisticBlocks.logicExporter,stack(Items.lead, 50), stack(Items.densealloy, 100), stack(Items.silicon, 175), stack(Items.thorium, 75), stack(Items.plastanium, 125));
        new Recipe(effect, LogisticBlocks.logicImporter,stack(Items.lead, 50), stack(Items.densealloy, 100), stack(Items.silicon, 175), stack(Items.thorium, 75), stack(Items.plastanium, 125));

        new Recipe(effect, StorageBlocks.launchPad, stack(Items.copper, 250), stack(Items.silicon, 200), stack(Items.lead, 200), stack(Items.titanium, 150))
                .setOnlyCampaign(true);
        new Recipe(effect, StorageBlocks.landingPad, stack(Items.copper, 200), stack(Items.silicon, 150), stack(Items.lead, 150))
                .setOnlyCampaign(true);
        new Recipe(effect, StorageBlocks.core, stack(Items.copper, 1000), stack(Items.lead, 1000), stack(Items.densealloy, 1000), stack(Items.silicon, 1000), stack(Items.thorium, 1000), stack(Items.chromium, 1000));

        new Recipe(effect, DefenseBlocks.shockMine, stack(Items.lead, 50), stack(Items.silicon, 25))
            .setDependencies(Items.blastCompound);

        //TURRETS
        new Recipe(turret, TurretBlocks.duo, stack(Items.copper, 40)).setAlwaysUnlocked(true);
        new Recipe(turret, TurretBlocks.scatter, stack(Items.copper, 85), stack(Items.lead, 45));
        new Recipe(turret, TurretBlocks.arc, stack(Items.copper, 50), stack(Items.lead, 30), stack(Items.silicon, 20));
        new Recipe(turret, TurretBlocks.scorch, stack(Items.copper, 60), stack(Items.densealloy, 25));
        new Recipe(turret, TurretBlocks.hail, stack(Items.copper, 60), stack(Items.densealloy, 35));
        new Recipe(turret, TurretBlocks.lancer, stack(Items.copper, 50), stack(Items.lead, 100), stack(Items.silicon, 90));
        new Recipe(turret, TurretBlocks.wave, stack(Items.densealloy, 60), stack(Items.titanium, 70), stack(Items.lead, 150));
        new Recipe(turret, TurretBlocks.salvo, stack(Items.copper, 180), stack(Items.densealloy, 150), stack(Items.titanium, 70));
        new Recipe(turret, TurretBlocks.swarmer, stack(Items.densealloy, 70), stack(Items.titanium, 70), stack(Items.plastanium, 90), stack(Items.silicon, 60));
        new Recipe(turret, TurretBlocks.ripple, stack(Items.copper, 300), stack(Items.densealloy, 220), stack(Items.titanium, 120));
        new Recipe(turret, TurretBlocks.cyclone, stack(Items.copper, 400), stack(Items.densealloy, 400), stack(Items.titanium, 200), stack(Items.plastanium, 150));
        new Recipe(turret, TurretBlocks.fuse, stack(Items.copper, 450), stack(Items.densealloy, 450), stack(Items.thorium, 250));
        new Recipe(turret, TurretBlocks.spectre, stack(Items.copper, 700), stack(Items.densealloy, 600), stack(Items.surgealloy, 500), stack(Items.plastanium, 350), stack(Items.thorium, 500));
        new Recipe(turret, TurretBlocks.meltdown, stack(Items.copper, 500), stack(Items.lead, 700), stack(Items.densealloy, 600), stack(Items.surgealloy, 650), stack(Items.silicon, 650));
        new Recipe(turret, TurretBlocks.foreshadow, stack(Items.copper, 1000), stack(Items.densealloy, 600), stack(Items.surgealloy, 300), stack(Items.plastanium, 200), stack(Items.silicon, 600), stack(Items.chromium, 250));

        //DISTRIBUTION
        new Recipe(distribution, DistributionBlocks.conveyor, stack(Items.copper, 1)).setAlwaysUnlocked(true);
        new Recipe(distribution, DistributionBlocks.titaniumconveyor, stack(Items.copper, 2), stack(Items.titanium, 1));
        new Recipe(distribution, DistributionBlocks.thoriumconveyor,stack(Items.copper, 2), stack(Items.thorium, 2) );
        new Recipe(distribution, DistributionBlocks.phaseConveyor, stack(Items.phasefabric, 10), stack(Items.silicon, 15), stack(Items.lead, 20), stack(Items.densealloy, 20));
        new Recipe(distribution, DistributionBlocks.stackConveyor, stack(Items.plastanium, 1), stack(Items.silicon, 1), stack(Items.lead, 1));
        new Recipe(distribution, DistributionBlocks.trainRail, stack(Items.densealloy, 8), stack(Items.titanium, 6), stack(Items.copper, 6)).setAlwaysUnlocked(true);

        //starter transport
        new Recipe(distribution, DistributionBlocks.junction, stack(Items.copper, 2)).setAlwaysUnlocked(true);
        new Recipe(distribution, DistributionBlocks.router, stack(Items.copper, 6)).setAlwaysUnlocked(true);

        //advanced densealloy transporat
        new Recipe(distribution, DistributionBlocks.distributor, stack(Items.densealloy, 8), stack(Items.copper, 8));
        new Recipe(distribution, DistributionBlocks.sorter, stack(Items.densealloy, 4), stack(Items.copper, 4));
        new Recipe(distribution, DistributionBlocks.invertedSorter, stack(Items.densealloy, 4), stack(Items.copper, 4));
        new Recipe(distribution, DistributionBlocks.overflowGate, stack(Items.densealloy, 4), stack(Items.copper, 8));
        new Recipe(distribution, DistributionBlocks.underflowGate, stack(Items.densealloy, 4), stack(Items.copper, 8));
        new Recipe(distribution, DistributionBlocks.itemBridge, stack(Items.densealloy, 8), stack(Items.copper, 8));
        new Recipe(distribution, DistributionBlocks.massDriver, stack(Items.densealloy, 250), stack(Items.silicon, 150), stack(Items.lead, 250), stack(Items.thorium, 100));
        new Recipe(distribution, StorageBlocks.unloader, stack(Items.densealloy, 50), stack(Items.silicon, 60));


        //CRAFTING

        //smelting
        new Recipe(crafting, CraftingBlocks.smelter, stack(Items.copper, 100));
        new Recipe(crafting, CraftingBlocks.arcsmelter, stack(Items.copper, 110), stack(Items.densealloy, 70), stack(Items.lead, 50));
        new Recipe(crafting, CraftingBlocks.denseAlloyKiln, stack(Items.copper, 210), stack(Items.densealloy, 190), stack(Items.silicon, 110), stack(Items.lead, 150), stack(Items.chromium, 100));
        new Recipe(crafting, CraftingBlocks.arcscrapsmelter, stack(Items.copper, 130), stack(Items.densealloy, 90), stack(Items.silicon, 60), stack(Items.lead, 70));
        new Recipe(crafting, CraftingBlocks.siliconsmelter, stack(Items.copper, 60), stack(Items.lead, 50));
        new Recipe(crafting, CraftingBlocks.siliconcrucible, stack(Items.titanium, 120), stack(Items.densealloy, 140),stack(Items.plastanium, 50), stack(Items.silicon, 80));

        //advanced fabrication
        new Recipe(crafting, CraftingBlocks.plastaniumCompressor, stack(Items.silicon, 160), stack(Items.lead, 230), stack(Items.densealloy, 120), stack(Items.titanium, 160));
        new Recipe(crafting, CraftingBlocks.phaseWeaver, stack(Items.silicon, 260), stack(Items.lead, 240), stack(Items.thorium, 150));
        new Recipe(crafting, CraftingBlocks.alloySmelter, stack(Items.silicon, 160), stack(Items.lead, 160), stack(Items.thorium, 140));
        new Recipe(crafting, CraftingBlocks.surgeAlloyCrucible, stack(Items.silicon, 180), stack(Items.lead, 200), stack(Items.thorium, 180), stack(Items.chromium, 200), stack(Items.surgealloy, 20));
        new Recipe(crafting, CraftingBlocks.blueMicrochipCrafter, stack(Items.silicon, 260), stack(Items.lead, 240), stack(Items.thorium, 220), stack(Items.chromium, 175), stack(Items.surgealloy, 75));

        //misc
        new Recipe(crafting, CraftingBlocks.pulverizer, stack(Items.copper, 60), stack(Items.lead, 50));
        new Recipe(crafting, CraftingBlocks.pyratiteMixer, stack(Items.copper, 100), stack(Items.lead, 50));
        new Recipe(crafting, CraftingBlocks.blastMixer, stack(Items.lead, 60), stack(Items.densealloy, 40));
        new Recipe(crafting, CraftingBlocks.cryofluidmixer, stack(Items.lead, 130), stack(Items.silicon, 80), stack(Items.thorium, 90));

        new Recipe(crafting, CraftingBlocks.solidifier, stack(Items.densealloy, 30), stack(Items.copper, 20));
        new Recipe(crafting, CraftingBlocks.melter, stack(Items.copper, 60), stack(Items.lead, 70), stack(Items.densealloy, 90));
        new Recipe(crafting, CraftingBlocks.scrapmelter, stack(Items.copper, 40), stack(Items.scrap, 50), stack(Items.lead, 50), stack(Items.densealloy, 70));
        new Recipe(crafting, CraftingBlocks.incinerator, stack(Items.densealloy, 10), stack(Items.lead, 30));
        new Recipe(crafting, CraftingBlocks.coalcentrifuge, stack(Items.titanium, 40), stack(Items.densealloy, 80), stack(Items.lead, 60));

        //processing
        new Recipe(crafting, CraftingBlocks.biomatterCompressor, stack(Items.lead, 70), stack(Items.silicon, 60));
        new Recipe(crafting, CraftingBlocks.separator, stack(Items.copper, 60), stack(Items.densealloy, 50));
        new Recipe(crafting, CraftingBlocks.slag_centrifuge, stack(Items.copper, 100), stack(Items.densealloy, 90), stack(Items.silicon, 60), stack(Items.scrap, 20));
        new Recipe(crafting, CraftingBlocks.centrifuge, stack(Items.copper, 130), stack(Items.densealloy, 130), stack(Items.silicon, 60), stack(Items.titanium, 50));

        //POWER
        new Recipe(power, PowerBlocks.powerNode, stack(Items.copper, 2), stack(Items.lead, 6))
                .setDependencies(PowerBlocks.combustionGenerator);
        new Recipe(power, PowerBlocks.powerNodeLarge, stack(Items.densealloy, 10), stack(Items.lead, 20), stack(Items.silicon, 6))
                .setDependencies(PowerBlocks.powerNode);
        new Recipe(power, PowerBlocks.surgeTower, stack(Items.densealloy, 15), stack(Items.lead, 20), stack(Items.silicon, 30), stack(Items.surgealloy, 30))
                .setDependencies(PowerBlocks.powerNodeLarge);
        new Recipe(power, PowerBlocks.battery, stack(Items.copper, 8), stack(Items.lead, 30), stack(Items.silicon, 4))
                .setDependencies(PowerBlocks.powerNode);
        new Recipe(power, PowerBlocks.batteryLarge, stack(Items.densealloy, 40), stack(Items.lead, 80), stack(Items.silicon, 30))
                .setDependencies(PowerBlocks.powerNode);

        //generators - combustion
        new Recipe(power, PowerBlocks.combustionGenerator, stack(Items.copper, 50), stack(Items.lead, 30));
        new Recipe(power, PowerBlocks.turbineGenerator, stack(Items.copper, 70), stack(Items.densealloy, 50), stack(Items.lead, 80), stack(Items.silicon, 60));
        new Recipe(power, PowerBlocks.differentialGenerator, stack(Items.copper, 70), stack(Items.titanium, 50), stack(Items.densealloy, 100), stack(Items.lead, 100), stack(Items.silicon, 65));
        new Recipe(power, PowerBlocks.thermalGenerator, stack(Items.copper, 80), stack(Items.densealloy, 70), stack(Items.lead, 100), stack(Items.silicon, 70), stack(Items.thorium, 70));

        //generators - solar
        new Recipe(power, PowerBlocks.solarPanel, stack(Items.lead, 5), stack(Items.silicon, 5));
        new Recipe(power, PowerBlocks.largeSolarPanel, stack(Items.lead, 50), stack(Items.silicon, 50), stack(Items.phasefabric, 30));

        //generators - nuclear
        new Recipe(power, PowerBlocks.thoriumReactor, stack(Items.lead, 600), stack(Items.silicon, 400), stack(Items.densealloy, 300), stack(Items.thorium, 300));
        new Recipe(power, PowerBlocks.rtgGenerator, stack(Items.lead, 200), stack(Items.silicon, 150), stack(Items.phasefabric, 50), stack(Items.plastanium, 150), stack(Items.thorium, 100));
        new Recipe(power, PowerBlocks.fusionReactor, stack(Items.lead, 1000), stack(Items.silicon, 600), stack(Items.densealloy, 800), stack(Items.thorium, 200), stack(Items.surgealloy, 500), stack(Items.chromium, 250));

        //DRILLS, PRODUCERS
        new Recipe(production, ProductionBlocks.mechanicalDrill, stack(Items.copper, 12)).setAlwaysUnlocked(true);
        new Recipe(production, ProductionBlocks.pneumaticDrill, stack(Items.copper, 18), stack(Items.densealloy, 10));
        new Recipe(production, ProductionBlocks.laserDrill, stack(Items.copper, 30), stack(Items.densealloy, 20), stack(Items.silicon, 20), stack(Items.titanium, 30));
        new Recipe(production, ProductionBlocks.blastDrill, stack(Items.copper, 60), stack(Items.densealloy, 40), stack(Items.silicon, 40), stack(Items.titanium, 90), stack(Items.thorium, 60));
        new Recipe(production, ProductionBlocks.plasmaDrill, stack(Items.copper, 120), stack(Items.densealloy, 80), stack(Items.silicon, 80), stack(Items.titanium, 120), stack(Items.thorium, 120), stack(Items.chromium, 80), stack(Items.surgealloy, 60));

        new Recipe(production, ProductionBlocks.waterExtractor, stack(Items.copper, 50), stack(Items.densealloy, 50), stack(Items.lead, 40));
        new Recipe(production, ProductionBlocks.cultivator, stack(Items.copper, 20), stack(Items.lead, 50), stack(Items.silicon, 20));
        new Recipe(production, ProductionBlocks.oilExtractor, stack(Items.copper, 300), stack(Items.densealloy, 350), stack(Items.lead, 230), stack(Items.thorium, 230), stack(Items.silicon, 150));

        //UNITS

        //upgrades
        new Recipe(upgrade, UpgradeBlocks.dartPad, stack(Items.lead, 150), stack(Items.copper, 150), stack(Items.silicon, 200)).setVisible(RecipeVisibility.desktopOnly);
        new Recipe(upgrade, UpgradeBlocks.tridentPad, stack(Items.lead, 250), stack(Items.copper, 250), stack(Items.silicon, 250), stack(Items.titanium, 300), stack(Items.plastanium, 200));
        new Recipe(upgrade, UpgradeBlocks.javelinPad, stack(Items.lead, 350), stack(Items.silicon, 450), stack(Items.titanium, 500), stack(Items.plastanium, 400), stack(Items.phasefabric, 200));
        new Recipe(upgrade, UpgradeBlocks.glaivePad, stack(Items.lead, 450), stack(Items.silicon, 650), stack(Items.titanium, 700), stack(Items.plastanium, 600), stack(Items.surgealloy, 200), stack(Items.chromium, 100));

        new Recipe(upgrade, UpgradeBlocks.alphaPad, stack(Items.lead, 200), stack(Items.densealloy, 100), stack(Items.copper, 150)).setVisible(RecipeVisibility.mobileOnly);
        new Recipe(upgrade, UpgradeBlocks.tauPad, stack(Items.lead, 250), stack(Items.densealloy, 250), stack(Items.copper, 250), stack(Items.silicon, 250));
        new Recipe(upgrade, UpgradeBlocks.deltaPad, stack(Items.lead, 350), stack(Items.densealloy, 350), stack(Items.copper, 400), stack(Items.silicon, 450), stack(Items.thorium, 300));
        new Recipe(upgrade, UpgradeBlocks.omegaPad, stack(Items.lead, 450), stack(Items.densealloy, 550), stack(Items.silicon, 650), stack(Items.thorium, 600), stack(Items.surgealloy, 240), stack(Items.chromium, 100));

        new Recipe(upgrade, UnitBlocks.reconstructor, stack(Items.copper, 100), stack(Items.lead, 50), stack(Items.silicon, 150), stack(Items.chromium, 75), stack(Items.plastanium, 25));


        //actual unit related stuff
       /* new Recipe(units, UnitBlocks.scavengerFactory, stack(Items.copper, 60), stack(Items.lead, 90), stack(Items.scrap, 70));
        new Recipe(units, UnitBlocks.spiritFactory, stack(Items.copper, 70), stack(Items.lead, 110), stack(Items.silicon, 130));
        new Recipe(units, UnitBlocks.ghostFactory, stack(Items.densealloy, 50), stack(Items.copper, 90), stack(Items.lead, 110), stack(Items.silicon, 180));
        new Recipe(units, UnitBlocks.phantomFactory, stack(Items.densealloy, 90), stack(Items.thorium, 80), stack(Items.lead, 110), stack(Items.silicon, 210));*/
        //this Code is Deprecated

        new Recipe(units,UnitBlocks.dronesFactory, stack(Items.copper, 60), stack(Items.lead, 90), stack(Items.silicon, 110));

        new Recipe(units, UnitBlocks.scrappeonFactory, stack(Items.copper, 50), stack(Items.lead, 80), stack(Items.scrap, 50));
        new Recipe(units, UnitBlocks.daggerFactory, stack(Items.lead, 90), stack(Items.silicon, 70));
        new Recipe(units, UnitBlocks.titanFactory, stack(Items.densealloy, 90), stack(Items.lead, 140), stack(Items.silicon, 90));
        new Recipe(units, UnitBlocks.fortressFactory, stack(Items.thorium, 200), stack(Items.lead, 220), stack(Items.silicon, 150), stack(Items.chromium, 100), stack(Items.phasefabric, 50));

        new Recipe(units, UnitBlocks.novaFactory, stack(Items.lead, 110), stack(Items.silicon, 90));

        new Recipe(units, UnitBlocks.scrapperFactory, stack(Items.scrap, 30), stack(Items.copper, 30), stack(Items.lead, 40));
        new Recipe(units, UnitBlocks.wraithFactory, stack(Items.copper, 90), stack(Items.lead, 80), stack(Items.silicon, 90));
        new Recipe(units, UnitBlocks.ghoulFactory, stack(Items.titanium, 100), stack(Items.lead, 130), stack(Items.silicon, 220));
        new Recipe(units, UnitBlocks.revenantFactory, stack(Items.plastanium, 300), stack(Items.titanium, 400), stack(Items.lead, 300), stack(Items.silicon, 400), stack(Items.chromium, 100));

        new Recipe(units, UnitBlocks.crawlerFactory, stack(Items.copper, 80), stack(Items.lead, 80), stack(Items.silicon, 150));
        new Recipe(units, UnitBlocks.bombdroneFactory, stack(Items.copper, 120),stack(Items.densealloy, 80) ,stack(Items.lead, 100), stack(Items.silicon, 200));
        new Recipe(units, UnitBlocks.highTierFactory, stack(Items.chromium, 600), stack(Items.lead, 400), stack(Items.silicon, 1000), stack(Items.surgealloy, 100), stack(Items.densealloy, 800));

        new Recipe(units, UnitBlocks.repairPoint, stack(Items.lead, 30), stack(Items.copper, 30), stack(Items.silicon, 30));
        new Recipe(units, UnitBlocks.commandCenter, stack(Items.lead, 10), stack(Items.densealloy, 10), stack(Items.silicon, 20));
        new Recipe(units, UnitBlocks.trainCrafter, stack(Items.densealloy, 120), stack(Items.titanium, 90), stack(Items.silicon, 110), stack(Items.lead, 140)).setAlwaysUnlocked(true);
        //LIQUIDS
        new Recipe(liquid, LiquidBlocks.conduit, stack(Items.lead, 1)).setDependencies(CraftingBlocks.smelter);
        new Recipe(liquid, LiquidBlocks.pulseConduit, stack(Items.titanium, 1), stack(Items.lead, 1));
        new Recipe(liquid, LiquidBlocks.phaseConduit, stack(Items.phasefabric, 10), stack(Items.silicon, 15), stack(Items.lead, 20), stack(Items.titanium, 20));

        new Recipe(liquid, LiquidBlocks.liquidRouter, stack(Items.titanium, 4), stack(Items.lead, 4));
        new Recipe(liquid, LiquidBlocks.liquidtank, stack(Items.titanium, 50), stack(Items.lead, 50));
        new Recipe(liquid, LiquidBlocks.liquidJunction, stack(Items.titanium, 4), stack(Items.lead, 4));
        new Recipe(liquid, LiquidBlocks.bridgeConduit, stack(Items.titanium, 8), stack(Items.lead, 8));

        new Recipe(liquid, LiquidBlocks.mechanicalPump, stack(Items.copper, 30), stack(Items.lead, 20)).setDependencies(CraftingBlocks.smelter);
        new Recipe(liquid, LiquidBlocks.rotaryPump, stack(Items.copper, 140), stack(Items.lead, 100), stack(Items.silicon, 40), stack(Items.titanium, 70));
        new Recipe(liquid, LiquidBlocks.thermalPump, stack(Items.copper, 160), stack(Items.lead, 130), stack(Items.silicon, 60), stack(Items.titanium, 80), stack(Items.thorium, 70));
    // Classic shit

        //distribution
        new Recipe(distribution, ClassicBlocks.classicConveyor, stack(Items.stone, 1)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(distribution, ClassicBlocks.classicRouter, stack(Items.stone, 2)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(distribution, ClassicBlocks.classicSorter, stack(Items.iron, 2)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(distribution, ClassicBlocks.classicJunction,  stack(Items.iron, 2)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(distribution, ClassicBlocks.conveyorTunnel,  stack(Items.iron, 2)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(distribution, ClassicBlocks.teleporter,  stack(Items.steel, 30), stack(Items.dirium, 40)).setAlwaysUnlocked(true).setTechTree(classicTech);
        //power
        new Recipe(power, ClassicBlocks.powerLaser, stack(Items.steel, 3), stack(Items.iron, 3)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(power, ClassicBlocks.powerLaserCorner, stack(Items.steel, 4), stack(Items.iron, 4)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(power, ClassicBlocks.powerLaserRouter, stack(Items.steel, 5), stack(Items.iron, 5)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(power, ClassicBlocks.powerBooster, stack(Items.steel, 8), stack(Items.iron, 8)).setAlwaysUnlocked(true).setTechTree(classicTech);
        //drills
        new Recipe(production, ClassicBlocks.stoneDrill, stack(Items.stone, 12)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(production, ClassicBlocks.ironDrill, stack(Items.stone, 25)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(production, ClassicBlocks.coalDrill, stack(Items.stone, 25), stack(Items.iron, 40)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(production, ClassicBlocks.titaniumDrill, stack(Items.iron, 50), stack(Items.steel, 50)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(production, ClassicBlocks.uraniumDrill, stack(Items.iron, 40), stack(Items.steel, 40)).setAlwaysUnlocked(true).setTechTree(classicTech);
        //crafting
        new Recipe(crafting, ClassicBlocks.classicSmelter, stack(Items.stone, 40), stack(Items.iron, 40)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(crafting, ClassicBlocks.classicCrucible, stack(Items.steel, 50), stack(Items.titanium, 50)).setAlwaysUnlocked(true).setTechTree(classicTech);
        //effect
        new Recipe(effect, ClassicBlocks.classicShield, stack(Items.titanium, 30), stack(Items.dirium, 30)).setAlwaysUnlocked(true).setTechTree(classicTech);
        new Recipe(effect, ClassicBlocks.classicCore).setAlwaysUnlocked(true).setTechTree(classicTech);
    }

    private static ItemStack stack(Item item, int amount){
        return new ItemStack(item, amount);
    }

    @Override
    public ContentType type(){
        return ContentType.recipe;
    }
}
