package io.anuke.mindustry.content.blocks;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.Liquids;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.production.*;
import io.anuke.mindustry.world.consumers.ConsumeItemFilter;
import io.anuke.mindustry.world.meta.BlockGroup;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

public class CraftingBlocks extends BlockList implements ContentList{
    public static Block smelter, arcsmelter, denseAlloyKiln, arcscrapsmelter, siliconsmelter, siliconcrucible, plastaniumCompressor, phaseWeaver, alloySmelter, surgeAlloyCrucible,
            pyratiteMixer, blastMixer, coalcentrifuge,
            cryofluidmixer, melter, scrapmelter, slag_centrifuge,separator, centrifuge, biomatterCompressor, pulverizer, solidifier, incinerator,
            blueMicrochipCrafter,
            biomassGenerator;

    @Override
    public void load(){
        smelter = new GenericCrafter("smelter"){{
            health = 70;
            smelter = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            output = Items.densealloy;
            craftTime = 45f;
            burnDuration = 46f;
            useFlux = true;
            setAmbientSound("loopSmelter", 0.07f);
            consumes.items(new ItemStack[]{new ItemStack(Items.copper, 1), new ItemStack(Items.lead, 2)});
            consumes.item(Items.coal).optional(true);
            smokeInterval = 8f;
        }};

        arcsmelter = new GenericCrafter("arc-smelter"){{
            health = 90;
            smelter = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.densealloy;
            craftTime = 30f;
            size = 2;

            useFlux = true;
            fluxNeeded = 2;
            setAmbientSound("loopSmelter", 0.07f);
            consumes.items(new ItemStack[]{new ItemStack(Items.copper, 1), new ItemStack(Items.lead, 2)});
            consumes.power(0.1f);

            smokeColor = Color.valueOf("f0f0f0");
            smokeLength = 10f;
            smokeRandomness = 3f;
        }};
        denseAlloyKiln = new GenericCrafter("dense-alloy-kiln"){{
            health = 240;
            smelter = true;
            hasPower = true;
            hasItems = true;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.densealloy;
            itemOutputAmount = 3;
            itemCapacity = 80;
            craftTime = 65f;
            size = 3;

            useFlux = true;
            fluxNeeded = 2;
            setAmbientSound("loopSmelter", 0.09f);
            consumes.items(new ItemStack[]{new ItemStack(Items.copper, 4), new ItemStack(Items.lead, 6), new ItemStack(Items.pyratite, 1)});
            consumes.power(0.6f);

            smokeColor = Color.valueOf("979285");
            smokeLength = 18f;
            smokeInterval = 48f;
            smokeRandomness = 1.5f;
        }};

        arcscrapsmelter = new GenericCrafter("arc-scrap-smelter"){{
            health = 90;
            smelter = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.densealloy;
            craftTime = 35f;
            size = 2;

            useFlux = true;
            fluxNeeded = 2;
            setAmbientSound("loopSmelter", 0.07f);
            consumes.items(new ItemStack[]{new ItemStack(Items.scrap, 2), new ItemStack(Items.coal, 1)});
            consumes.power(0.08f);

            smokeInterval = 48f;
        }};

        siliconsmelter = new GenericCrafter("silicon-smelter"){{
            health = 90;
            smelter = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.silicon;
            craftTime = 40f;
            powerCapacity = 20f;
            size = 2;
            hasLiquids = false;
            flameColor = Color.valueOf("ffef99");
            setAmbientSound("loopSmelter", 0.07f);
            consumes.items(new ItemStack[]{new ItemStack(Items.coal, 1), new ItemStack(Items.sand, 2)});
            consumes.power(0.05f);
        }};

        siliconcrucible = new GenericCrafter("silicon-crucible"){{
            health = 90;
            smelter = true;
            hasPower = true;
            hasItems = true;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.silicon;
            itemOutputAmount = 6;
            itemCapacity = 80;
            craftTime = 90f;
            powerCapacity = 20f;
            size = 4;
            hasLiquids = false;
            setAmbientSound("loopSmelter", 0.07f);
            flameColor = Color.valueOf("ffef99");

            consumes.items(new ItemStack[]{new ItemStack(Items.coal, 4), new ItemStack(Items.sand, 6), new ItemStack(Items.pyratite, 1)});
            consumes.power(0.25f);

            smokeColor = Color.valueOf("666156");
            smokeInterval = 72f;
            smokeRandomness = 1.5f;
        }};

        plastaniumCompressor = new PlastaniumCompressor("plastanium-compressor"){{
            hasItems = true;
            liquidCapacity = 60f;
            craftTime = 60f;
            output = Items.plastanium;
            itemCapacity = 30;
            powerCapacity = 40f;
            size = 2;
            health = 320;
            hasPower = hasLiquids = true;
            craftEffect = BlockFx.formsmoke;
            updateEffect = BlockFx.plasticburn;
            setAmbientSound("loopMachine", 0.03f);
            consumes.liquid(Liquids.oil, 0.25f);
            consumes.power(0.3f);
            consumes.item(Items.titanium, 2);
        }};

        phaseWeaver = new PhaseWeaver("phase-weaver"){{
            smelter = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.phasefabric;
            craftTime = 120f;
            powerCapacity = 50f;
            size = 2;
            setAmbientSound("loopTech", 0.02f);
            consumes.items(new ItemStack[]{new ItemStack(Items.thorium, 4), new ItemStack(Items.sand, 10)});
            consumes.power(0.5f);
        }};

        alloySmelter = new GenericCrafter("alloy-smelter"){{
            smelter = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.surgealloy;
            craftTime = 75f;
            powerCapacity = 60f;
            size = 2;

            useFlux = true;
            fluxNeeded = 3;

            consumes.power(0.4f);
            consumes.items(new ItemStack[]{new ItemStack(Items.titanium, 2), new ItemStack(Items.lead, 4), new ItemStack(Items.silicon, 3), new ItemStack(Items.copper, 3)});

            smokeInterval = 48f;
            smokeLength = 14f;
            smokeColor = Color.valueOf("dccdaa");
        }};
        surgeAlloyCrucible = new GenericCrafter("surge-alloy-crucible"){{
            smelter = true;
            hasPower = true;
            hasItems = true;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.surgealloy;
            itemOutputAmount = 3;
            itemCapacity = 80;
            craftTime = 115;
            powerCapacity = 140f;
            size = 3;

            useFlux = true;
            fluxNeeded = 3;

            consumes.power(1.2f);
            consumes.items(new ItemStack[]{new ItemStack(Items.titanium, 5), new ItemStack(Items.lead, 11), new ItemStack(Items.silicon, 9), new ItemStack(Items.copper, 8), new ItemStack(Items.pyratite, 1)});

            smokeInterval = 48f;
            smokeLength = 28f;
            smokeColor = Color.valueOf("9e9277");
        }};

        cryofluidmixer = new LiquidMixer("cryofluidmixer"){{
            outputLiquid = Liquids.cryofluid;
            liquidPerItem = 50f;
            itemCapacity = 50;
            size = 2;
            hasPower = true;
            setAmbientSound("loopMachine", 0.03f);
            consumes.power(0.1f);
            consumes.item(Items.titanium);
            consumes.liquid(Liquids.water, 0.3f);
        }};

        blastMixer = new GenericCrafter("blast-mixer"){{
            itemCapacity = 20;
            hasItems = true;
            hasPower = true;
            hasLiquids = true;
            output = Items.blastCompound;
            size = 2;
            setAmbientSound("loopMachineSpin", 0.12f);
            consumes.liquid(Liquids.oil, 0.05f);
            consumes.item(Items.pyratite, 1);
            consumes.power(0.04f);
        }};

        pyratiteMixer = new GenericCrafter("pyratite-mixer"){{
            smelter = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            group = BlockGroup.power;
            flameColor = Color.CLEAR;
            output = Items.pyratite;

            size = 2;
            setAmbientSound("loopMachineSpin", 0.1f);
            consumes.power(0.02f);
            consumes.items(new ItemStack[]{new ItemStack(Items.coal, 1), new ItemStack(Items.lead, 2), new ItemStack(Items.sand, 2)});
        }};

        melter = new GenericCrafter("melter"){{
            health = 200;
            outputLiquid = Liquids.lava;
            outputLiquidAmount = 1f;
            itemCapacity = 20;
            craftTime = 10f;
            hasLiquids = hasPower = hasItems = true;
            setAmbientSound("loopMachine", 0.03f);
            consumes.power(0.1f);
            consumes.item(Items.stone, 1);

            smoke = true;
            smokeLength = 7f;
            smokeRandomness = 1.5f;
            smokeDirection = 160f;
        }};

        scrapmelter = new GenericCrafter("scrap-melter"){{
            health = 100;
            outputLiquid = Liquids.slag;
            outputLiquidAmount = 2f;
            itemCapacity = 20;
            craftTime = 6f;
            hasLiquids = hasPower = hasItems = true;
            setAmbientSound("loopMachine", 0.03f);
            consumes.power(0.1f);
            consumes.item(Items.scrap, 2);

            smoke = true;
            smokeLength = 7f;
            smokeRandomness = 1.5f;
            smokeDirection = 160f;
            smokeColor = Color.valueOf("fff4d9");
        }};

        separator = new Separator("separator"){{
            results = new ItemStack[]{
                new ItemStack(null, 10),
                new ItemStack(Items.sand, 10),
                new ItemStack(Items.stone, 9),
                new ItemStack(Items.copper, 4),
                new ItemStack(Items.lead, 2),
                new ItemStack(Items.coal, 2),
                new ItemStack(Items.titanium, 1),
            };
            filterTime = 40f;
            itemCapacity = 40;
            health = 50;
            setAmbientSound("loopMachineSpin", 0.03f);
            consumes.item(Items.stone, 2);
            consumes.liquid(Liquids.water, 0.3f);
        }};

        slag_centrifuge = new Separator("slag-centrifuge"){{
            results = new ItemStack[]{
                new ItemStack(null, 5),
                new ItemStack(Items.copper, 10),
                new ItemStack(Items.lead, 9),
                new ItemStack(Items.sand, 9),
                new ItemStack(Items.densealloy, 4),
                new ItemStack(Items.silicon, 2),
                new ItemStack(Items.titanium, 1),
            };
            size = 2;
            filterTime = 40f;
            itemCapacity = 40;
            health = 50;
            spinnerLength = 1.5f;
            spinnerRadius = 3.5f;
            spinnerThickness = 1.5f;
            spinnerSpeed = 3f;
            setAmbientSound("loopMachineSpin", 0.06f);
            consumes.item(Items.scrap, 1);
            consumes.liquid(Liquids.slag, 0.4f);
        }};

        centrifuge = new Separator("centrifuge"){{
            results = new ItemStack[]{
                new ItemStack(null, 13),
                new ItemStack(Items.sand, 12),
                new ItemStack(Items.stone, 11),
                new ItemStack(Items.copper, 5),
                new ItemStack(Items.lead, 3),
                new ItemStack(Items.coal, 3),
                new ItemStack(Items.titanium, 2),
                new ItemStack(Items.thorium, 1)
            };
            setAmbientSound("loopMachineSpim", 0.03f);
            hasPower = true;
            filterTime = 15f;
            itemCapacity = 60;
            health = 50 * 4;
            spinnerLength = 1.5f;
            spinnerRadius = 3.5f;
            spinnerThickness = 1.5f;
            spinnerSpeed = 3f;
            size = 2;

            consumes.item(Items.stone, 2);
            consumes.power(0.2f);
            consumes.liquid(Liquids.water, 0.5f);
        }};

        biomatterCompressor = new Compressor("biomattercompressor"){{
            liquidCapacity = 60f;
            itemCapacity = 50;
            craftTime = 25f;
            outputLiquid = Liquids.oil;
            outputLiquidAmount = 1.5f;
            size = 2;
            health = 320;
            hasLiquids = true;

            consumes.item(Items.biomatter, 1);
            consumes.power(0.06f);
        }};

        pulverizer = new Pulverizer("pulverizer"){{
            itemCapacity = 40;
            output = Items.sand;
            health = 80;
            craftEffect = BlockFx.pulverize;
            craftTime = 40f;
            updateEffect = BlockFx.pulverizeSmall;
            hasItems = hasPower = true;
            setAmbientSound("loopGrind", 0.025f);
            consumes.add(new ConsumeItemFilter(item -> item == Items.stone || item == Items.scrap));
            consumes.power(0.05f);

            smoke = true;
            smokeColor = Color.valueOf("fff2d3");
            smokeInterval = 68f;
            smokeLength = 7f;
            smokeRandomness = 1.5f;
            smokeDirection = 150f;
        }};

        solidifier = new GenericCrafter("solidifer"){{
            liquidCapacity = 21f;
            craftTime = 14;
            output = Items.stone;
            itemCapacity = 20;
            health = 80;
            craftEffect = BlockFx.purifystone;
            hasLiquids = hasItems = true;

            consumes.liquid(Liquids.lava, 1f);

        }};coalcentrifuge = new GenericCrafter("coal-centrifuge"){{
            liquidCapacity = 10f;
            hasPower = true;
            craftTime = 30;
            itemOutputAmount = 2;
            output = Items.coal;
            size = 2;
            itemCapacity = 20;
            health = 80;
            craftEffect = BlockFx.smeltsmoke;
            hasLiquids = hasItems = true;
            consumes.liquid(Liquids.oil, 0.09f);
            consumes.power(0.07f);
        }};

        incinerator = new Incinerator("incinerator"){{
            health = 90;
        }};

        blueMicrochipCrafter = new PhaseWeaver("blue-microchip-crafter"){{
            smelter = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 20;
            layerLight = true;
            group = BlockGroup.power;
            craftEffect = BlockFx.smeltsmoke;
            output = Items.bluemicrochip;
            craftTime = 145f;
            powerCapacity = 50f;
            size = 4;
            setAmbientSound("loopTech", 0.02f);
            consumes.items(new ItemStack[]{new ItemStack(Items.chromium, 4),new ItemStack(Items.silicon, 3), new ItemStack(Items.copper, 2)});
            consumes.power(0.65f);
        }};

        biomassGenerator = new HiveCrafter("biomass-generator"){{
            itemCapacity = 2;
            craftTime = Mathf.random(1290f , 1990f);
            hasItems = true;
            output = Items.corruptedbiomatter;
            size = 2;
            shadow = "biomass-generatorshadow";
            craftEffect = BlockFx.biomassSmoke;
            updateEffect = BlockFx.biomassSpore;
            setAmbientSound("none");
        }
            @Override
            public boolean canPlaceOn(Tile tile) {
                return tile != null && tile.isInfected;
            }

            @Override
            public void draw(Tile tile) {
                float pulse = 1f + Mathf.absin(Timers.time(), 10f, 0.15f);

                Draw.rect(name(), tile.drawx(), tile.drawy(), pulse * size * 8f, pulse * size * 8f);
            }
        };
    }
}
