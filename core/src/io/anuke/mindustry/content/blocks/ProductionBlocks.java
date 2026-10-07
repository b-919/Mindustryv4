package io.anuke.mindustry.content.blocks;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.Liquids;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.production.Cultivator;
import io.anuke.mindustry.world.blocks.production.Drill;
import io.anuke.mindustry.world.blocks.production.HiveDrill;
import io.anuke.mindustry.world.blocks.production.Fracker;
import io.anuke.mindustry.world.blocks.production.SolidPump;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.content.blocks.Blocks.infectedGrass;

public class ProductionBlocks extends BlockList implements ContentList{
    public static Block mechanicalDrill, pneumaticDrill, laserDrill, blastDrill, plasmaDrill, waterExtractor, oilExtractor, cultivator, corruptedcultivator, biomassBulb;

    @Override
    public void load(){
        mechanicalDrill = new Drill("mechanical-drill"){{
            tier = 2;
            drillTime = 300;
            size = 2;
            drawMineItem = true;
        }};

        pneumaticDrill = new Drill("pneumatic-drill"){{
            tier = 3;
            drillTime = 240;
            size = 2;
            drawMineItem = true;
        }};

        laserDrill = new Drill("laser-drill"){{
            drillTime = 140;
            size = 2;
            hasPower = true;
            tier = 4;
            updateEffect = BlockFx.pulverizeMedium;
            drillEffect = BlockFx.mineBig;

            consumes.power(0.11f);
        }};

        blastDrill = new Drill("blast-drill"){{
            drillTime = 60;
            size = 3;
            drawRim = true;
            hasPower = true;
            tier = 5;
            updateEffect = BlockFx.pulverizeRed;
            updateEffectChance = 0.03f;
            drillEffect = BlockFx.mineHuge;
            rotateSpeed = 6f;
            warmupSpeed = 0.01f;

            consumes.power(0.3f);
        }};

        plasmaDrill = new Drill("plasma-drill"){{
            heatColor = Color.valueOf("ff461b");
            drillTime = 50;
            size = 4;
            hasLiquids = true;
            hasPower = true;
            tier = 5;
            rotateSpeed = 9f;
            drawRim = true;
            updateEffect = BlockFx.pulverizeRedder;
            updateEffectChance = 0.04f;
            drillEffect = BlockFx.mineHuge;
            warmupSpeed = 0.005f;

            consumes.power(0.7f);
        }};

        waterExtractor = new SolidPump("water-extractor"){{
            result = Liquids.water;
            pumpAmount = 0.125f;
            size = 2;
            liquidCapacity = 30f;
            rotateSpeed = 1.4f;

            consumes.power(0.05f);
        }};

        oilExtractor = new Fracker("oil-extractor"){{
            result = Liquids.oil;
            updateEffect = BlockFx.pulverize;
            liquidCapacity = 50f;
            updateEffectChance = 0.05f;
            pumpAmount = 0.09f;
            size = 3;
            liquidCapacity = 30f;

            consumes.item(Items.sand);
            consumes.power(0.3f);
            consumes.liquid(Liquids.water, 0.15f);
        }};

        cultivator = new Cultivator("cultivator"){{
            result = Items.biomatter;
            drillTime = 260;
            size = 2;
            hasLiquids = true;
            hasPower = true;

            consumes.power(0.08f);
            consumes.liquid(Liquids.water, 0.2f);
        }};
        corruptedcultivator = new Cultivator("corrupted-cultivator"){{
            result = Items.corruptedbiomatter;
            drillTime = 260;
            size = 2;
            hasLiquids = true;
            hasPower = true;
            plantColor = Color.valueOf("511111");
            plantColorLight = Color.valueOf("6b0707");

            consumes.power(0.08f);
            consumes.liquid(Liquids.water, 0.2f);
        }
            @Override
            public boolean isValid(Tile tile){
                return tile != null && tile.floor() == infectedGrass;
            }
        };
        biomassBulb = new HiveDrill("biomass-bulb"){{
            health = 580;
            update = true;
            size = 2;
            tier = 5;
            drillTime = 90;
            living = true;
            drawMineItem = true;
            updateEffect = BlockFx.biomassSmoke;
            drillEffect = BlockFx.biomassSpore;
            shadow = "biomass-bulbshadow";
        }
        @Override
            public TextureRegion[] getIcon(){
            return new TextureRegion[]{Draw.region(name), Draw.region(name + "-top")};
        }
        @Override
            public void draw(Tile tile){


            DrillEntity entity = tile.entity();
            float pulse = 1f + Mathf.absin(Timers.time(), drillTime / 60, 0.15f);

            Draw.rect(region, tile.drawx(), tile.drawy());
            Draw.rect(topRegion, tile.drawx(), tile.drawy(), pulse * size * 8f, pulse * size * 8f);
            if(entity.dominantItem != null && drawMineItem){
                Draw.color(entity.dominantItem.color);
                Draw.rect("blank", tile.drawx(), tile.drawy(), 2f, 2f);
                Draw.color();
            }
        }
        };

    }
}
