package io.anuke.mindustry.content.blocks;

import com.badlogic.gdx.utils.Array;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.Liquids;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.BarType;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.PowerBlock;
import io.anuke.mindustry.world.blocks.defense.OverdriveProjector;
import io.anuke.mindustry.world.blocks.defense.Wall;
import io.anuke.mindustry.world.blocks.distribution.Sorter;
import io.anuke.mindustry.world.blocks.power.PowerNode;
import io.anuke.mindustry.world.blocks.units.UnitFactoryAdvanced;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Geometry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import static io.anuke.mindustry.Vars.*;

public class DebugBlocks extends BlockList implements ContentList{
    public static Block powerVoid, superBooster, powerInfinite, itemSource, liquidSource, itemVoid, debugFactory, infectiontest;

    @Remote(targets = Loc.both, called = Loc.both, forward = true)
    public static void setLiquidSourceLiquid(Player player, Tile tile, Liquid liquid){
        LiquidSourceEntity entity = tile.entity();
        entity.source = liquid;
    }

    @Override
    public void load(){
        powerVoid = new PowerBlock("powervoid"){
            {
                powerCapacity = Float.MAX_VALUE;
                shadow = "shadow-round-1";
            }

            @Override
            public void setBars(){
                super.setBars();
                bars.remove(BarType.power);
            }

            @Override
            public void init(){
                super.init();
                stats.remove(BlockStat.powerCapacity);
            }
        };

        powerInfinite = new PowerNode("powerinfinite"){
            {
                powerCapacity = 10000f;
                maxNodes = 100;
                outputsPower = true;
                consumesPower = false;
                shadow = "shadow-round-1";
            }

            @Override
            public void update(Tile tile){
                super.update(tile);
                tile.entity.power.amount = powerCapacity;
            }
        };

        itemSource = new Sorter("itemsource"){
            {
                hasItems = true;
            }

            @Override
            public boolean outputsItems(){
                return true;
            }

            @Override
            public void setBars(){
                super.setBars();
                bars.remove(BarType.inventory);
            }

            @Override
            public void update(Tile tile){
                SorterEntity entity = tile.entity();
                entity.items.set(entity.sortItem, 1);
                tryDump(tile, entity.sortItem);
            }

            @Override
            public boolean acceptItem(Item item, Tile tile, Tile source){
                return false;
            }
        };

        liquidSource = new Block("liquidsource"){
            {
                update = true;
                solid = true;
                hasLiquids = true;
                liquidCapacity = 100f;
                configurable = true;
                outputsLiquid = true;
            }

            @Override
            public void update(Tile tile){
                LiquidSourceEntity entity = tile.entity();

                tile.entity.liquids.add(entity.source, liquidCapacity);
                tryDumpLiquid(tile, entity.source);
            }

            @Override
            public void draw(Tile tile){
                super.draw(tile);

                LiquidSourceEntity entity = tile.entity();

                Draw.color(entity.source.color);
                Draw.rect("blank", tile.worldx(), tile.worldy(), 4f, 4f);
                Draw.color();
            }

            @Override
            public void buildTable(Tile tile, Table table){
                LiquidSourceEntity entity = tile.entity();

                Array<Liquid> items = content.liquids();

                ButtonGroup<ImageButton> group = new ButtonGroup<>();
                Table cont = new Table();

                for(int i = 0; i < items.size; i++){
                    if(!control.unlocks.isUnlocked(items.get(i))) continue;

                    final int f = i;
                    ImageButton button = cont.addImageButton("liquid-icon-" + items.get(i).name, "clear-toggle", 24,
                            () -> Call.setLiquidSourceLiquid(null, tile, items.get(f))).size(38).group(group).get();
                    button.setChecked(entity.source.id == f);

                    if(i % 4 == 3){
                        cont.row();
                    }
                }

                table.add(cont);
            }

            @Override
            public TileEntity newEntity(){
                return new LiquidSourceEntity();
            }
        };

        itemVoid = new Block("itemvoid"){
            {
                update = solid = true;
            }

            @Override
            public void handleItem(Item item, Tile tile, Tile source){
            }

            @Override
            public boolean acceptItem(Item item, Tile tile, Tile source){
                return true;
            }
        };
        superBooster = new OverdriveProjector("super_booster"){{
            consumes.power(0.1f);
            speedBoost = 8f;
            size = 2;
            consumes.item(Items.phasefabric).optional(true);
        }};

        debugFactory = new UnitFactoryAdvanced("debug-factory"){{
            types = new UnitType[]{
                    UnitTypes.dagger,
                    UnitTypes.scrapper,
                    UnitTypes.ghost,
                    UnitTypes.lich,
                    UnitTypes.debugtank,
                    UnitTypes.chaosarray,
                    UnitTypes.nova,
                    UnitTypes.reaper,
                    UnitTypes.eradicator,
                    UnitTypes.atrax,
                    UnitTypes.artilleryBiomass,
                    UnitTypes.explosiveBiomass,
                    UnitTypes.acidMosquito,
                    UnitTypes.exterminatorBiomass,
                    UnitTypes.evilDagger,
                    UnitTypes.evilDraug,
                    UnitTypes.evilSwarmDrone,
                    UnitTypes.evilTanky,
                    UnitTypes.evilWraith
            };
            consumerStacks = new ItemStack[][]{
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 30),
                            new ItemStack(Items.lead, 30)},
                    new ItemStack[]{
                            new ItemStack(Items.scrap, 5),
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 30),
                            new ItemStack(Items.lead, 30),
                            new ItemStack(Items.densealloy, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},
                    new ItemStack[]{
                            new ItemStack(Items.silicon, 5)},

            };
            producerTimes = new float[]{
                    2000f,
                    1000f,
                    3000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
                    1000f,
            };
            maxSpawn = new int[]{
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
                    5,
            };
            size = 2;
            consumes.power(0.04f);
        }};
        infectiontest = new Block("infectiontest"){
            {
                health = 80;
                spreadsInfection = true;
            }

            @Override
            public void placed(Tile tile){
                super.placed(tile);
                tile.infect();
                for(int i = 0; i < 8; i++){
                    Tile other = tile.getNearby(Geometry.d8[i]);
                    if(other != null) other.infect();
                }
            }
        };
    }



    class LiquidSourceEntity extends TileEntity{
        public Liquid source = Liquids.water;

        @Override
        public void writeConfig(DataOutput stream) throws IOException{
            stream.writeByte(source.id);
        }

        @Override
        public void readConfig(DataInput stream) throws IOException{
            source = content.liquid(stream.readByte() & 0xFF);
        }

        @Override
        public Object config(){
            return source;
        }

        @Override
        public void configured(Object config){
            if(config instanceof Liquid){
                source = (Liquid)config;
            }
        }
    }
}
