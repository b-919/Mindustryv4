package io.anuke.mindustry.content.blocks;

import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.classic.defense.ClassicShield;
import io.anuke.mindustry.world.blocks.classic.distribution.Teleporter;
import io.anuke.mindustry.world.blocks.classic.distribution.TunnelConveyor;
import io.anuke.mindustry.world.blocks.classic.power.PowerBooster;
import io.anuke.mindustry.world.blocks.classic.power.PowerLaser;
import io.anuke.mindustry.world.blocks.classic.production.ClassicDrill;
import io.anuke.mindustry.world.blocks.distribution.Conveyor;
import io.anuke.mindustry.world.blocks.distribution.Junction;
import io.anuke.mindustry.world.blocks.distribution.Router;
import io.anuke.mindustry.world.blocks.distribution.Sorter;
import io.anuke.mindustry.world.blocks.production.GenericCrafter;
import io.anuke.mindustry.world.blocks.storage.CoreBlock;

public class ClassicBlocks extends BlockList implements ContentList {
    public static Block classicCore,
    //distribution
    classicConveyor, steelConveyor, poweredConveyor, classicRouter, classicJunction, classicSorter, conveyorTunnel, teleporter,
    //drills
    stoneDrill, ironDrill, coalDrill, titaniumDrill, uraniumDrill, omniDrill,
    //production
    classicSmelter, classicCrucible,
    //power generator
    classicCombustionGenerator, classicThermalGenerator, classicRTGenerator, classicNuclearReactor,
    //effect
    classicShield,
    //powa dristibutition
    powerBooster, powerLaser, powerLaserCorner, powerLaserRouter;

    @Override
    public void load() {
        classicCore = new CoreBlock("classic-core"){{
            defenseDrones = false;
            droneType = null;
        }};

        classicConveyor = new Conveyor("classic-conveyor"){{
            health = 45;
            speed = 0.03f;
            animationframe = 2;
            bridgeReplacement = null;
            junctionReplacement = classicJunction;
        }};

        /*steelConveyor = new Conveyor("steel-conveyor"){{
            health = 55;
            speed = 0.04f;
            animationframe = 2;
        }};

        poweredConveyor = new Conveyor("powered-conveyor"){{
            health = 75;
            speed = 0.03f;
            animationframe = 2;
        }};*/

        classicRouter = new Router("classic-router");
        classicJunction = new Junction("classic-junction");
        classicSorter = new Sorter("classic-sorter");

        conveyorTunnel = new TunnelConveyor("conveyor-tunnel");
        teleporter = new Teleporter("teleporter");
        stoneDrill = new ClassicDrill("stone-drill"){{
            result = Items.stone;
            time = 4;
        }};
        ironDrill = new ClassicDrill("iron-drill"){{
            result = Items.iron;
        }};
        coalDrill = new ClassicDrill("coal-drill"){{
            result = Items.coal;
            time = 6;
        }};
        uraniumDrill = new ClassicDrill("uranium-drill"){{
            result = Items.uranium;
            time = 7;
        }};
        titaniumDrill = new ClassicDrill("titanium-drill"){{
            result = Items.titanium;
            time = 7;
        }};

        classicSmelter = new GenericCrafter("classic-smelter"){{
           health = 70;
           itemCapacity = 20;
           smelter = true;
           hasItems = true;
           layerLight = true;
           useFlux = false;
           output = Items.steel;
           consumes.items(new ItemStack[]{new ItemStack(Items.iron, 1)});
           consumes.item(Items.coal).optional(true);
           setAmbientSound("loopSmelter", 0.07f);
           smokeInterval = 8f;
        }};

        classicCrucible = new GenericCrafter("classic-crucible"){{
            health = 70;
            itemCapacity = 20;
            craftTime = 20f;
            smelter = true;
            hasItems = true;
            layerLight = true;
            useFlux = false;
            output = Items.dirium;
            consumes.items(new ItemStack[]{new ItemStack(Items.titanium, 1), new ItemStack(Items.steel, 1)});
            consumes.item(Items.coal).optional(true);
            setAmbientSound("loopSmelter", 0.07f);
            smokeInterval = 16f;
        }};

        classicShield = new ClassicShield("shield-generator");

        powerBooster = new PowerBooster("classic-power-booster");
        powerLaser = new PowerLaser("power-laser");
        powerLaserCorner = new PowerLaser("power-laser-corner"){{
            laserDirections = 2;
        }};
        powerLaserRouter = new PowerLaser("power-laser-router"){{
            laserDirections = 3;
        }};
    }
}