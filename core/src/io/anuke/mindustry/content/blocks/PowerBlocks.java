package io.anuke.mindustry.content.blocks;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.Liquids;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.power.*;

public class PowerBlocks extends BlockList implements ContentList{
    public static Block combustionGenerator, thermalGenerator, turbineGenerator,differentialGenerator, rtgGenerator, solarPanel, largeSolarPanel,
            thoriumReactor, fusionReactor, battery, batteryLarge, powerNode, powerNodeLarge, surgeTower, illuminator;

    @Override
    public void load(){
        combustionGenerator = new BurnerGenerator("combustion-generator"){{
            powerOutput = 0.15f;
            powerCapacity = 40f;
            itemDuration = 120f;
            smoke = true;
            setAmbientSound("loopSmelter", 0.03f);

        }};

        thermalGenerator = new LiquidHeatGenerator("thermal-generator"){{
            maxLiquidGenerate = 2f;
            powerCapacity = 40f;
            powerPerLiquid = 0.6f;
            generateEffect = BlockFx.redgeneratespark;
            setAmbientSound("loopHum", 0.08f);
            size = 2;
        }};

        turbineGenerator = new TurbineGenerator("turbine-generator"){{
            powerOutput = 0.40f;
            powerCapacity = 40f;
            itemDuration = 120f;
            powerPerLiquid = 0.7f;
            consumes.liquid(Liquids.water, 0.05f);
            size = 2;
            setAmbientSound("loopSmelter", 0.06f);

            smoke = true;
            smokeColor = Color.valueOf("f1f1f1");
            smokeLength = 60f;
            smokeInterval = 64f;
            smokeRandomness = 1.15f;
            smokeShadowAlpha = 0.05f;
        }};
        differentialGenerator = new TurbineGenerator("differential-generator"){{
            powerOutput = 1.38f;
            powerCapacity = 40f;
            itemDuration = 220f;
            powerPerLiquid = 0.7f;
            consumes.item(Items.pyratite);
            consumes.liquid(Liquids.cryofluid, 0.1f);
            size = 3;
            setAmbientSound("loopDifferential", 0.12f);

            smoke = true;
            smokeColor = Color.valueOf("af927b");
            smokeInterval = 64f;
            smokeRandomness = 1.15f;
        }};

        rtgGenerator = new DecayGenerator("rtg-generator"){{
            powerCapacity = 40f;
            size = 2;
            powerOutput = 0.45f;
            itemDuration = 220f;
            setAmbientSound("");
        }};

        solarPanel = new SolarGenerator("solar-panel"){{
            generation = 0.0066f;
        }};

        largeSolarPanel = new SolarGenerator("solar-panel-large"){{
            size = 3;
            generation = 0.088f;
        }};

        thoriumReactor = new NuclearReactor("thorium-reactor"){{
            size = 3;
            health = 700;
            powerMultiplier = 1.6f;
        }};

        fusionReactor = new FusionReactor("fusion-reactor"){{
            size = 4;
            health = 600;
            setAmbientSound("loopPulse", 0.08f);
        }};

        battery = new Battery("battery"){{
            powerCapacity = 320f;
        }};

        batteryLarge = new Battery("battery-large"){{
            size = 3;
            powerCapacity = 2000f;
        }};

        powerNode = new PowerNode("power-node"){{
            shadow = "shadow-round-1";
            maxNodes = 4;
            laserRange = 6;
        }};

        powerNodeLarge = new PowerNode("power-node-large"){{
            size = 2;
            maxNodes = 6;
            laserRange = 9.5f;
            shadow = "shadow-round-2";
        }};

        surgeTower = new PowerNode("surge-tower"){{
            size = 2;
            maxNodes = 2;
            laserRange = 30f;
        }};

        illuminator = new LightBlock("illuminator"){{
            radius = 160f;
            brightness = 0.75f;
            health = 90;
        }};

    }
}
