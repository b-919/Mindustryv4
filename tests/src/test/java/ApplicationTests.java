import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.ai.mass.MassAIConfig;
import io.anuke.mindustry.ai.mass.MassSave;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.CraftingBlocks;
import io.anuke.mindustry.content.blocks.PowerBlocks;
import io.anuke.mindustry.content.blocks.StorageBlocks;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.core.Logic;
import io.anuke.mindustry.core.NetServer;
import io.anuke.mindustry.core.World;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.game.Content;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.io.BundleLoader;
import io.anuke.mindustry.io.MapIO;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.maps.MapMeta;
import io.anuke.mindustry.maps.MapTileData;
import io.anuke.mindustry.maps.MapTileData.DataPosition;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.modules.ModuleCore;
import io.anuke.ucore.util.EmptyLogger;
import io.anuke.ucore.util.Log;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.File;
import static io.anuke.mindustry.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

public class ApplicationTests{

    @BeforeAll
    static void launchApplication(){
        try{
            boolean[] begins = {false};
            Throwable[] exceptionThrown = {null};
            Log.setUseColors(false);

            ModuleCore core = new ModuleCore(){
                @Override
                public void init(){
                    Vars.init();

                    headless = true;

                    BundleLoader.load();
                    content.load();
                    content.initialize(Content::init);

                    module(logic = new Logic());
                    module(world = new World());
                    module(netServer = new NetServer());
                }

                @Override
                public void postInit(){
                    super.postInit();
                    begins[0] = true;
                }
            };

            HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
            config.preferencesDirectory = "test_files/";

            new File("tests_files/").delete();

            new HeadlessApplication(core, config){{
                Gdx.app.setApplicationLogger(new EmptyLogger());
            }};

            for(Thread thread : Thread.getAllStackTraces().keySet()){
                if(thread.getName().equals("HeadlessApplication")){
                    thread.setUncaughtExceptionHandler((t, throwable) -> exceptionThrown[0] = throwable);
                    break;
                }
            }

            while(!begins[0]){
                if(exceptionThrown[0] != null){
                    fail(exceptionThrown[0]);
                }
                Thread.sleep(10);
            }
        }catch(Throwable r){
            fail(r);
        }
    }

    @BeforeEach
    void resetWorld(){
        Timers.setDeltaProvider(() ->  1f);
        logic.reset();
        state.set(State.menu);
    }

    @Test
    void initialization(){
        assertNotNull(logic);
        assertNotNull(world);
        assertTrue(content.getContentMap().length > 0);
    }

    @Test
    void loadSector(){
        world.sectors.createSector(0, 0);
        world.sectors.playSector(world.sectors.get(0, 0));
    }

    @Test
    void playMap(){
        assertTrue(world.maps.all().size > 0);

        world.loadMap(world.maps.all().first());
    }

    @Test
    void spawnWaves(){
        world.loadMap(world.maps.all().first());
        logic.runWave();
        unitGroups[waveTeam.ordinal()].updateEvents();
        assertFalse(unitGroups[waveTeam.ordinal()].isEmpty());
    }

    @Test
    void createMap(){
        assertTrue(world.maps.all().size > 0);

        Tile[][] tiles = world.createTiles(8, 8);

        world.beginMapLoad();
        for(int x = 0; x < tiles.length; x++){
            for(int y = 0; y < tiles[0].length; y++){
                tiles[x][y] = new Tile(x, y, (byte)0, (byte)0);
            }
        }
        world.endMapLoad();
    }

    @Test
    void multiblock(){
        createMap();
        int bx = 4;
        int by = 4;
        world.setBlock(world.tile(bx, by), StorageBlocks.core, Team.blue);
        assertEquals(world.tile(bx, by).getTeam(), Team.blue);
        for(int x = bx-1; x <= bx + 1; x++){
            for(int y = by-1; y <= by + 1; y++){
                if(x == bx && by == y){
                    assertEquals(world.tile(x, y).block(), StorageBlocks.core);
                }else{
                    assertTrue(world.tile(x, y).block() == Blocks.blockpart && world.tile(x, y).getLinked() == world.tile(bx, by));
                }
            }
        }
    }

    @Test
    void blockInventories(){
        multiblock();
        Tile tile = world.tile(4, 4);
        tile.entity.items.add(Items.coal, 5);
        tile.entity.items.add(Items.titanium, 50);
        assertEquals(tile.entity.items.total(), 55);
        tile.entity.items.remove(Items.phasefabric, 10);
        tile.entity.items.remove(Items.titanium, 10);
        assertEquals(tile.entity.items.total(), 45);
    }

    @Test
    void timers(){
        boolean[] ran = {false};
        Timers.run(1.9999f, () -> ran[0] = true);

        Timers.update();
        assertFalse(ran[0]);
        Timers.update();
        assertTrue(ran[0]);
    }

    @Test
    void save(){
        assertTrue(world.maps.all().size > 0);

        world.loadMap(world.maps.all().first());
        SaveIO.saveToSlot(0);
    }

    @Test
    void load(){
        assertTrue(world.maps.all().size > 0);

        world.loadMap(world.maps.all().first());
        Map map = world.getMap();

        SaveIO.saveToSlot(0);
        resetWorld();
        SaveIO.loadFromSlot(0);

        assertEquals(world.getMap(), map);
        assertEquals(world.width(), map.meta.width);
        assertEquals(world.height(), map.meta.height);
    }

    @Test
    void massDifficultyScaling(){
        assertEquals(10f, MassAIConfig.graceMinutes(Difficulty.training), 0.001f);
        assertEquals(0.5f, MassAIConfig.graceMinutes(Difficulty.eradication), 0.001f);
        assertEquals(5f, MassAIConfig.graceMinutes(null), 0.001f);

        assertEquals(1, MassAIConfig.biomassGeneratorBaseLimit(Difficulty.training));
        assertEquals(10, MassAIConfig.biomassGeneratorBaseLimit(Difficulty.eradication));
        assertEquals(3, MassAIConfig.biomassGeneratorBaseLimit(null));

        assertEquals(2, MassAIConfig.squadsPerHive(Difficulty.training));
        assertEquals(16, MassAIConfig.squadsPerHive(Difficulty.eradication));
        assertEquals(4, MassAIConfig.squadsPerHive(null));

        assertEquals(4, MassAIConfig.hiveLimit(Difficulty.training));
        assertEquals(24, MassAIConfig.hiveLimit(Difficulty.eradication));
        assertEquals(10, MassAIConfig.hiveLimit(null));
    }

    @Test
    void massSaveFormats() throws Exception{
        assertTrue(world.maps.all().size > 0);
        world.loadMap(world.maps.all().first());

        //legacy layout: no header, no response timers, old global-command tail
        MassSave.read(new DataInputStream(new java.io.ByteArrayInputStream(massBlock(MassSave.FORMAT_LEGACY, 555f))),
            MassSave.FORMAT_LEGACY);
        assertEquals(555f, MassAI.getInfectionTime(), 0.01f);

        //format 4: header + response timers, still with the tail
        MassSave.read(new DataInputStream(new java.io.ByteArrayInputStream(massBlock(MassSave.FORMAT_HEADERED, 777f))),
            MassSave.FORMAT_HEADERED);
        assertEquals(777f, MassAI.getInfectionTime(), 0.01f);

        //format 5: writer/reader symmetry
        MassAI.setInfectionTime(1234.5f);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try(java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)){
            MassSave.write(out);
        }
        MassAI.setInfectionTime(0f);
        MassSave.read(new DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())), MassSave.SAVE_VERSION);
        assertEquals(1234.5f, MassAI.getInfectionTime(), 0.01f);

        //a mismatched header must fail instead of desynchronizing the rest of the save
        assertThrows(java.io.IOException.class, () -> MassSave.read(
            new DataInputStream(new java.io.ByteArrayInputStream(massBlock(MassSave.FORMAT_HEADERED, 0f))),
            MassSave.SAVE_VERSION));
    }

    private static byte[] massBlock(int format, float infectionTime) throws Exception{
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try(java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)){
            if(format >= MassSave.FORMAT_HEADERED) out.writeInt(format);
            out.writeInt(0); //cores
            out.writeInt(0); //lines
            out.writeInt(0); //subsections
            out.writeFloat(1f); //spawn timer
            out.writeFloat(2f); //turret timer
            out.writeFloat(3f); //next turret
            out.writeFloat(4f); //damage turret timer
            out.writeFloat(5f); //next damage turret
            if(format >= MassSave.FORMAT_HEADERED){
                out.writeFloat(6f); //response timer
                out.writeFloat(7f); //next response
            }
            out.writeFloat(infectionTime); //infection countdown
            out.writeInt(0); //pending builds
            for(int i = 0; i < 7; i++) out.writeInt(0); //ore boosts
            out.writeInt(0); //expanded cores
            if(format < MassSave.SAVE_VERSION) out.writeInt(0); //legacy currentCommand
            out.writeFloat(123f); //grace timer
            if(format < MassSave.SAVE_VERSION){
                out.writeFloat(0f); //legacy enemy nearby timer
                out.writeBoolean(false); //legacy enemy nearby flag
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void inventoryDeposit(){
        depositTest(CraftingBlocks.smelter, Items.copper);
        depositTest(StorageBlocks.vault, Items.copper);
        depositTest(PowerBlocks.thoriumReactor, Items.thorium);
    }

    @Test
    void edges(){
        GridPoint2[] edges = Edges.getEdges(1);
        assertEquals(edges[0], new GridPoint2(1, 0));
        assertEquals(edges[1], new GridPoint2(0, 1));
        assertEquals(edges[2], new GridPoint2(-1, 0));
        assertEquals(edges[3], new GridPoint2(0, -1));

        GridPoint2[] edges2 = Edges.getEdges(2);
        assertEquals(8, edges2.length);
    }

    void depositTest(Block block, Item item){
        BaseUnit unit = UnitTypes.alphaDrone.create(Team.none);
        Tile tile = new Tile(0, 0, Blocks.air.id, block.id);
        int capacity = tile.block().itemCapacity;

        int deposited = tile.block().acceptStack(item, capacity - 1, tile, unit);
        assertEquals(capacity - 1, deposited);

        tile.block().handleStack(item, capacity - 1, tile, unit);
        assertEquals(tile.entity.items.get(item), capacity - 1);

        int overflow = tile.block().acceptStack(item, 10, tile, unit);
        assertEquals(1, overflow);

        tile.block().handleStack(item, 1, tile, unit);
        assertEquals(capacity, tile.entity.items.get(item));
    }

    @Test // test made to check if old maps works without data coruption
    void contentIdShiftPreservesSave(){
        assertTrue(world.maps.all().size > 0);

        world.loadMap(world.maps.all().first());
        int bx = 20, by = 20;
        world.setBlock(world.tile(bx, by), StorageBlocks.core, Team.blue);

        Block a = null, b = null;
        for(Block block : content.blocks()){
            if(block != Blocks.air && block != Blocks.blockpart && block != StorageBlocks.core){
                if(a == null) a = block;
                else if(b == null) b = block;
                else break;
            }
        }
        assertNotNull(a);
        assertNotNull(b);

        java.io.File tmp = new java.io.File("test_files/idshift.msav");
        tmp.getParentFile().mkdirs();
        com.badlogic.gdx.files.FileHandle file = com.badlogic.gdx.Gdx.files.local(tmp.getPath());

        SaveIO.write(file);

        int ia = a.id, ib = b.id;
        Array<Block> blocks = content.blocks();

        setContentId(a, (short) ib);
        setContentId(b, (short) ia);
        Block hold = blocks.get(ia);
        blocks.set(ia, blocks.get(ib));
        blocks.set(ib, hold);

        try{
            SaveIO.load(file);

            assertEquals(StorageBlocks.core, world.tile(bx, by).block(), "Block IDs shifted but core must resolve by name");
        }finally{
            Block hold2 = blocks.get(ib);
            blocks.set(ib, blocks.get(ia));
            blocks.set(ia, hold2);
            setContentId(a, (short) ia);
            setContentId(b, (short) ib);
        }

        assertEquals(a, blocks.get(ia));
        assertEquals(b, blocks.get(ib));
        assertEquals(ia, a.id);
        assertEquals(ib, b.id);
    }

    @Test
    void legacyMapsLoadUncorrupted(){
        for(Map map : world.maps.all()){
            try(DataInputStream ds = new DataInputStream(map.stream.get())){
                MapMeta meta = MapIO.readMapMeta(ds);

                for(com.badlogic.gdx.utils.IntIntMap.Entry entry : meta.blockMap.entries()){
                    assertTrue(entry.key >= 0, "Legacy map '" + map.meta.name() + "' has a negative remap key");
                }

                MapTileData data = MapIO.readTileData(ds, meta, false);
                int rawWalls = 0;
                for(int y = 0; y < data.height(); y++){
                    for(int x = 0; x < data.width(); x++){
                        short floor = data.read(x, y, DataPosition.floor);
                        short wall = data.read(x, y, DataPosition.wall);
                        assertTrue(floor >= 0 && wall >= 0, "Remapped tile ID must never be negative");
                        if(wall != 0) rawWalls++;
                    }
                }
                Log.info("legacy map {0} loaded with {1} walls", map.meta.name(), rawWalls);
            }catch(Exception e){
                fail("Failed to load legacy map " + map.meta.name() + ": " + e.getMessage());
            }
        }
    }

    static void setContentId(Content content, short id){
        try{
            java.lang.reflect.Field field = Content.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(content, id);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}