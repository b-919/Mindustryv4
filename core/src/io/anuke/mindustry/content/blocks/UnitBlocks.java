package io.anuke.mindustry.content.blocks;

import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.units.*;

public class UnitBlocks extends BlockList implements ContentList{
    public static Block
        //scavengerFactory, spiritFactory, phantomFactory, ghostFactory,
            dronesFactory,
        scrapperFactory, wraithFactory, ghoulFactory, revenantFactory,
        scrappeonFactory,
        daggerFactory, titanFactory, fortressFactory,
            novaFactory,
            crawlerFactory, bombdroneFactory,
        reconstructor, highTierFactory, repairPoint, commandCenter,
            trainCrafter,
            hiveSpawner, airHiveSpawner, heavyHiveSpawner;

    @Override
    public void load(){
        //This code is Deprecated
        /*scavengerFactory = new UnitFactory("scavenger-factory"){{
            type = UnitTypes.scavenger;
            produceTime = 7600;
            size = 2;
            consumes.power(0.04f);
            consumes.items(new ItemStack(Items.scrap, 10));
        }};

        spiritFactory = new UnitFactory("spirit-factory"){{
            type = UnitTypes.spirit;
            produceTime = 5700;
            size = 2;
            consumes.power(0.08f);
            consumes.items(new ItemStack(Items.silicon, 30), new ItemStack(Items.lead, 30));
        }};

        ghostFactory = new UnitFactory("ghost-factory"){{
            type = UnitTypes.ghost;
            produceTime = 6300;
            size = 2;
            consumes.power(0.12f);
            consumes.items(new ItemStack(Items.silicon, 50), new ItemStack(Items.lead, 50), new ItemStack(Items.densealloy, 60));
        }};

        phantomFactory = new UnitFactory("phantom-factory"){{
            type = UnitTypes.phantom;
            produceTime = 7300;
            size = 2;
            consumes.power(0.2f);
            consumes.items(new ItemStack(Items.silicon, 70), new ItemStack(Items.lead, 80), new ItemStack(Items.titanium, 80));
        }};*/
        dronesFactory = new UnitFactoryAdvanced("drones-factory"){{
            types = new UnitType[]{
                    UnitTypes.scavenger,
                    UnitTypes.draug,
                    UnitTypes.spirit,
                    UnitTypes.ghost,
                    UnitTypes.phantom
            };
            consumerStacks = new ItemStack[][]{
                    new ItemStack[]{
                            new ItemStack(Items.scrap, 10),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.copper, 10),
                            new ItemStack(Items.lead, 15)
                    },
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 15),
                            new ItemStack(Items.lead, 15)
                    },
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 30),
                            new ItemStack(Items.lead, 30),
                            new ItemStack(Items.densealloy, 40)
                    },
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 40),
                            new ItemStack(Items.lead, 50),
                            new ItemStack(Items.titanium, 50)
                    }
            };
            producerTimes = new float[]{
                    5600,
                    4600,
                    5700,
                    6300,
                    7300,
            };
            maxSpawn = new int[]{
                    20,
                    15,
                    10,
                    10,
                    5,
            };
            consumes.power(0.08f);
            size =2;
        }};


        wraithFactory = new UnitFactory("wraith-factory"){{
            type = UnitTypes.wraith;
            produceTime = 1800;
            size = 2;
            consumes.power(0.1f);
            consumes.items(new ItemStack(Items.silicon, 8));
        }};

        novaFactory = new UnitFactory("nova-factory"){{
           type = UnitTypes.nova;
            produceTime = 1700;
            size = 2;
            consumes.power(0.05f);
            consumes.items(new ItemStack(Items.silicon, 12), new ItemStack(Items.lead, 10));
        }};

        scrapperFactory = new UnitFactory("scrapper-factory"){{
            type = UnitTypes.scrapper;
            produceTime = 900;
            size = 2;
            consumes.power(0.05f);
            consumes.items(new ItemStack(Items.scrap, 10), new ItemStack(Items.lead, 10));
            maxSpawn = 20;
        }};

        ghoulFactory = new UnitFactory("ghoul-factory"){{
            type = UnitTypes.ghoul;
            produceTime = 3600;
            size = 3;
            consumes.power(0.2f);
            shadow = "shadow-round-3";
            consumes.items(new ItemStack(Items.silicon, 22), new ItemStack(Items.titanium, 20));
            maxSpawn = 6;
        }};

        revenantFactory = new UnitFactory("revenant-factory"){{
            type = UnitTypes.revenant;
            produceTime = 8000;
            size = 4;
            consumes.power(0.3f);
            shadow = "shadow-round-4";
            consumes.items(new ItemStack(Items.silicon, 60), new ItemStack(Items.titanium, 60), new ItemStack(Items.plastanium, 30));
            maxSpawn = 4;
        }};

        scrappeonFactory = new UnitFactory("scrappeon-factory"){{
            type = UnitTypes.scrappeon;
            produceTime = 1200;
            size = 2;
            consumes.power(0.02f);
            consumes.items(new ItemStack(Items.scrap, 10));
            maxSpawn = 20;
        }};

        crawlerFactory = new UnitFactory("crawler-factory"){{
            type = UnitTypes.crawler;
            produceTime = 1000;
            size = 2;
            consumes.power(0.04f);
            consumes.items(new ItemStack(Items.silicon, 10), new ItemStack(Items.coal, 10));
        }};

        bombdroneFactory = new UnitFactory("bomb_drone-factory"){{
            type = UnitTypes.bombDrone;
            produceTime = 1800;
            size = 2;
            consumes.power(0.06f);
            consumes.items(new ItemStack(Items.silicon, 10), new ItemStack(Items.coal, 10), new ItemStack(Items.lead, 20));
        }};

        daggerFactory = new UnitFactory("dagger-factory"){{
            type = UnitTypes.dagger;
            produceTime = 1700;
            size = 2;
            consumes.power(0.05f);
            consumes.items(new ItemStack(Items.silicon, 8));
        }};

        titanFactory = new UnitFactory("titan-factory"){{
            type = UnitTypes.titan;
            produceTime = 3400;
            size = 3;
            consumes.power(0.15f);
            shadow = "shadow-round-3";
            consumes.items(new ItemStack(Items.silicon, 16), new ItemStack(Items.densealloy, 10));
            maxSpawn = 6;
        }};

        fortressFactory = new UnitFactory("fortress-factory"){{
            type = UnitTypes.fortress;
            produceTime = 5000;
            size = 3;
            consumes.power(0.2f);
            shadow = "shadow-round-3";
            consumes.items(new ItemStack(Items.silicon, 30), new ItemStack(Items.thorium, 40));
            maxSpawn = 4;
        }};
        highTierFactory = new UnitFactoryAdvanced("high-tier-factory"){{
            types = new UnitType[]{
                    UnitTypes.lich,
                    UnitTypes.chaosarray,
                    UnitTypes.reaper,
                    UnitTypes.eradicator
            };
            consumerStacks = new ItemStack[][]{
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 400),
                            new ItemStack(Items.lead, 550),
                            new ItemStack(Items.thorium, 200),
                            new ItemStack(Items.plastanium, 150),
                            new ItemStack(Items.chromium, 300),
                            new ItemStack(Items.surgealloy, 300),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 500),
                            new ItemStack(Items.lead, 600),
                            new ItemStack(Items.thorium, 300),
                            new ItemStack(Items.titanium, 200),
                            new ItemStack(Items.chromium, 150),
                            new ItemStack(Items.surgealloy, 300),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 900),
                            new ItemStack(Items.lead, 1000),
                            new ItemStack(Items.thorium, 550),
                            new ItemStack(Items.plastanium, 650),
                            new ItemStack(Items.chromium, 320),
                            new ItemStack(Items.surgealloy, 450),
                            new ItemStack(Items.bluemicrochip, 50),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 1000),
                            new ItemStack(Items.lead, 1100),
                            new ItemStack(Items.thorium, 700),
                            new ItemStack(Items.titanium, 550),
                            new ItemStack(Items.chromium, 420),
                            new ItemStack(Items.surgealloy, 600),
                            new ItemStack(Items.bluemicrochip, 50),
                    },

            };
            producerTimes = new float[]{
                    17000f,
                    18000f,
                    21000f,
                    22000f,
            };
            maxSpawn = new int[]{
                    2,
                    2,
                    1,
                    1,
            };
            size = 8;
            consumes.power(0.64f);
            setBuildUnitSound("unitCreateBig");
        }};


        repairPoint = new RepairPoint("repair-point"){{
            shadow = "shadow-round-1";
            repairSpeed = 0.1f;
            solid = false;
        }};

        reconstructor = new Reconstructor("reconstructor"){{
            size = 2;
        }};

        commandCenter = new CommandCenter("command-center"){{
            size = 2;
        }};

        trainCrafter = new TrainCrafter("train-crafter"){{
            consumes.power(0.25f);
            consumes.items(new ItemStack(Items.densealloy, 20), new ItemStack(Items.silicon, 30), new ItemStack(Items.titanium, 25));
        }};
        hiveSpawner = new UnitHiveSpawner("hive-spawner"){{
            size = 2;
            living = true;
            consumerStacks = new ItemStack[][]{
                    new ItemStack[]{
                            new ItemStack(Items.copper, 5),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.titanium, 5),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.coal, 5),
                    },
            };
            types = new UnitType[]{
                    UnitTypes.evilDagger,
                    UnitTypes.evilTanky,
                    UnitTypes.explosiveBiomass
            };
            evoUnlock = new int[]{0, 1, 2};
            producerTimes = new float[]{
                    60f * 8,
                    60f * 15,
                    60f * 12
            };
            maxSpawn = new int[]{
                    15,
                    8,
                    10
            };
            shadow = "hive-spawner-shadow";
        }};
        airHiveSpawner = new UnitHiveSpawner("air-hive-spawner"){{
            size = 2;
            living = true;
            consumerStacks = new ItemStack[][]{
                    new ItemStack[]{
                            new ItemStack(Items.lead, 5),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.coal, 5),
                            new ItemStack(Items.copper, 5),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.thorium, 5),
                            new ItemStack(Items.scrap, 1)
                    }
            };
            types = new UnitType[]{
                    UnitTypes.evilWraith,
                    UnitTypes.FlyingExplosiveBiomass,
                    UnitTypes.acidMosquito
            };
            evoUnlock = new int[]{0, 1, 2};
            producerTimes = new float[]{
                    60f * 10,
                    60f * 14,
                    60f * 18
            };
            maxSpawn = new int[]{
                    12,
                    8,
                    6
            };
            shadow = "air-hive-spawnershadow";
        }};
        heavyHiveSpawner = new UnitHiveSpawner("heavy-hive-spawner"){{
            size = 3;
            living = true;
            consumerStacks = new ItemStack[][]{
                    new ItemStack[]{
                            new ItemStack(Items.corruptedbiomatter, 25),
                            new ItemStack(Items.chromium, 25),
                    },
                    new ItemStack[]{
                            new ItemStack(Items.corruptedbiomatter, 5),
                            new ItemStack(Items.thorium, 10),
                    }
            };
            types = new UnitType[]{
                    UnitTypes.exterminatorBiomass,
                    UnitTypes.artilleryBiomass
            };
            evoUnlock = new int[]{2, 3};
            producerTimes = new float[]{
                    60f * 30,
                    60f * 25
            };
            maxSpawn = new int[]{
                    4,
                    6
            };
            shadow = "heavy-hive-spawnershadow";
        }};
    }
}
