package io.anuke.mindustry.content.blocks;

import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.distribution.*;

public class DistributionBlocks extends BlockList implements ContentList{
public static Block conveyor, titaniumconveyor, thoriumconveyor, distributor, junction,
    itemBridge, phaseConveyor, sorter, invertedSorter, router, stackConveyor, overflowGate,
    underflowGate, massDriver, trainRail, stackRouter;
       // veins, //da conveinyors

    @Override
    public void load(){

        conveyor = new Conveyor("conveyor"){{
            health = 45;
            speed = 0.03f;
        }};

        titaniumconveyor = new Conveyor("titanium-conveyor"){{
            health = 65;
            speed = 0.07f;

        }};

        thoriumconveyor = new ArmoredConveyor("thorium-conveyor"){{
            health = 230;
            speed = 0.07f;
            noSideBlend = true;
        }};

        junction = new Junction("junction"){{
            speed = 26;
            capacity = 32;
        }};

        itemBridge = new BufferedItemBridge("bridge-conveyor"){{
            range = 4;
            speed = 60f;
            bufferCapacity = 15;
        }};

        phaseConveyor = new ItemBridge("phase-conveyor"){{
            range = 12;
            hasPower = true;
            consumes.power(0.03f);
        }};

        sorter = new Sorter("sorter");

        invertedSorter = new Sorter("inverted-sorter"){{
            invert = true;
        }};

        router = new Router("router");

        stackConveyor = new StackConveyor("stack-conveyor"){{
            health = 120;
            speed = 0.11f;
            itemCapacity = 10;
            recharge = 2f;
        }};

        stackRouter = new StackRouter("stack-router"){{
            health = 120;
            speed = 60f;
        }};

        distributor = new Router("distributor"){{
            size = 2;
        }};

        overflowGate = new OverflowGate("overflow-gate");

        underflowGate = new OverflowGate("underflow-gate"){{
            invert = true;
        }};

        massDriver = new MassDriver("mass-driver"){{
            size = 3;
            itemCapacity = 60;
            range = 440f;
        }};

        /*veins = new Veins("veins"){{
            size = 1;
            speed = 0.15f;
            itemCapacity = 6;
            living = true;
        }};*/

        trainRail = new TrainRail("train-rail"){{
            health = 140;
        }};
    }
}
