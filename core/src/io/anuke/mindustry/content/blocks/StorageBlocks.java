package io.anuke.mindustry.content.blocks;

import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.storage.*;

public class StorageBlocks extends BlockList implements ContentList{
    public static Block core, vault, container, unloader, launchPad, landingPad,
     hive;

    @Override
    public void load(){
        launchPad = new LaunchPad("launch-pad"){{
            health = 200;
            powerCapacity = 60f;
        }};

        landingPad = new LandingPad("landing-pad"){{
            health = 200;
        }};

        core = new CoreBlock("core"){{
            health = 1100;
            defenseDrones = true;
            defenseDroneType = UnitTypes.defenseDrone;
            maxDefenseDrones = 3;
        }};

        vault = new Vault("vault"){{
            size = 3;
            itemCapacity = 900;
        }};

        container = new Vault("container"){{
            size = 2;
            itemCapacity = 200;
        }};

        unloader = new SortedUnloader("unloader"){{
            speed = 60f / 11f;
        }};

        hive = new HiveBlock("hive"){{
            size = 3;
            itemCapacity = 200000;
            health = 10000;
            droneType = UnitTypes.evilDraug;
            defenseDrones = true;
            maxDefenseDrones = 8;
            shadow = "hive-shadow";
            living = true;
            defenseDroneType = UnitTypes.evilSwarmDrone;
            spreadsInfection = true;
            infectionRadius = 50f;
        }};
    }
}
