package io.anuke.mindustry.maps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.async.AsyncExecutor;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.campaign.CampaignManager;
import io.anuke.mindustry.maps.campaign.CampaignRegistry;
import io.anuke.mindustry.maps.campaign.CampaignSectorGenerator;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.PlanetGenerator;
import io.anuke.mindustry.maps.campaign.PlanetMap;
import io.anuke.mindustry.maps.campaign.PlanetOrbit;
import io.anuke.mindustry.maps.campaign.PlanetMap.Region;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.maps.generation.WorldGenerator;
import io.anuke.mindustry.maps.generation.WorldGenerator.GenResult;
import io.anuke.mindustry.maps.missions.BiomassInfectableMission;
import io.anuke.mindustry.maps.missions.BiomassInfectedMission;
import io.anuke.mindustry.maps.missions.Mission;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.ColorMapper;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.util.*;

import static io.anuke.mindustry.Vars.*;

public class Sectors {
    public static final int sectorImageSize = 32;
    public static final String defaultCampaign = CampaignRegistry.serpulo;

    private final ObjectMap<String, GridMap<Sector>> campaignGrids = new ObjectMap<>();
    /** Incremented whenever campaign sector state changes; renderers can use this to refresh. */
    private int version;
    /** Shared planet/orbit time for the active campaign, in seconds. Advanced while playing. */
    private float planetTime;

    /** Ticks between background simulation steps. */
    private static final float simPeriod = 60f;
    /** Ticks between production/export rate refreshes. */
    private static final float ratePeriod = 60f;
    /** Real seconds between planet-clock writes while playing. */
    private static final float clockSavePeriod = 30f;
    private float simTimer;
    private float rateTimer;
    private float clockSaveTimer;
    private final ObjectIntMap<Item> pendingInput = new ObjectIntMap<>();
    private final ObjectIntMap<Item> pendingImport = new ObjectIntMap<>();
    private final ObjectIntMap<Item> pendingExport = new ObjectIntMap<>();
    private final Array<Sector> simSectors = new Array<>();

    public int version(){
        return version;
    }

    public float getPlanetTime(){
        return planetTime;
    }

    public void setPlanetTime(float time){
        planetTime = time;
    }

    public void advancePlanetTime(float seconds){
        planetTime += seconds;
        clockSaveTimer += seconds;

        if(clockSaveTimer >= clockSavePeriod){
            saveClock();
            clockSaveTimer = 0f;
        }
    }

    /** Writes only the campaign planet clock, so day/night and orbit survive a restart. */
    public void saveClock(){
        Settings.putFloat("sector-planet-time-" + activeCampaign, planetTime);
        Settings.save();
    }

    /** Advances background simulation for captured sectors and refreshes the active sector's rates. */
    public void updateSimulation(float delta){
        if(state.isPaused() || state.is(State.menu)) return;

        Sector active = world.getSector();

        if(active != null && !state.gameOver){
            rateTimer += delta;

            if(rateTimer >= ratePeriod){
                refreshRates(active, rateTimer / 60f);
                rateTimer = 0f;
            }
        }else{
            if(rateTimer != 0f || pendingInput.size > 0 || pendingImport.size > 0 || pendingExport.size > 0){
                resetRateState();
            }
        }

        simTimer += delta;

        if(simTimer >= simPeriod){
            float seconds = simTimer / 60f;

            simSectors.clear();
            for(Sector sector : activeGrid().values()){
                if(sector != null && sector.complete && sector != active){
                    simSectors.add(sector);
                }
            }

            for(Sector sector : simSectors){
                if(sector.sim == null) continue;
                sector.sim.simulate(seconds, launchManager);
            }

            simTimer = 0f;
        }
    }

    /** Records items entering a player core while a sector is being played. */
    public void onCoreInput(Item item, int amount){
        if(world.getSector() == null || state.gameOver) return;
        pendingInput.put(item, pendingInput.get(item, 0) + amount);
    }

    /** Records items received by a landing pad so imports are not counted as production. */
    public void onItemImport(Item item, int amount){
        if(world.getSector() == null || state.gameOver) return;
        pendingImport.put(item, pendingImport.get(item, 0) + amount);
    }

    /** Records items exported by a launch pad. */
    public void onItemExport(Item item, int amount){
        if(world.getSector() == null || state.gameOver) return;
        pendingExport.put(item, pendingExport.get(item, 0) + amount);
    }

    /** Copies the active core contents into its simulation before the world is saved. */
    public void captureActiveSector(){
        Sector sector = world.getSector();
        if(sector == null || !sector.complete || sector.sim == null || state.gameOver || state.is(State.menu)) return;

        if(rateTimer > 0f){
            refreshRates(sector, rateTimer / 60f);
        }

        sector.sim.captureWorld();
    }

    private void refreshRates(Sector sector, float seconds){
        if(seconds <= 0f) return;

        SectorSim sim = sector.sim;

        for(ObjectFloatMap.Entry<Item> entry : sim.production.entries()){
            Item item = entry.key;
            float sample = Math.max(pendingInput.get(item, 0) - pendingImport.get(item, 0), 0) / seconds;
            float next = Mathf.lerp(entry.value, sample, 0.25f);
            entry.value = next < 0.001f ? 0f : next;
        }

        for(ObjectIntMap.Entry<Item> entry : pendingInput.entries()){
            Item item = entry.key;
            if(sim.production.containsKey(item)) continue;

            float sample = Math.max(entry.value - pendingImport.get(item, 0), 0) / seconds;
            if(sample >= 0.001f){
                sim.production.put(item, sample);
            }
        }

        for(ObjectFloatMap.Entry<Item> entry : sim.export.entries()){
            Item item = entry.key;
            float sample = pendingExport.get(item, 0) / seconds;
            float next = Mathf.lerp(entry.value, sample, 0.25f);
            entry.value = next < 0.001f ? 0f : next;
        }

        for(ObjectIntMap.Entry<Item> entry : pendingExport.entries()){
            Item item = entry.key;
            if(sim.export.containsKey(item)) continue;

            float sample = entry.value / seconds;
            if(sample >= 0.001f){
                sim.export.put(item, sample);
            }
        }

        for(ObjectFloatMap.Entry<Item> entry : sim.imports.entries()){
            Item item = entry.key;
            float sample = pendingImport.get(item, 0) / seconds;
            float next = Mathf.lerp(entry.value, sample, 0.25f);
            entry.value = next < 0.001f ? 0f : next;
        }

        for(ObjectIntMap.Entry<Item> entry : pendingImport.entries()){
            Item item = entry.key;
            if(sim.imports.containsKey(item)) continue;

            float sample = entry.value / seconds;
            if(sample >= 0.001f){
                sim.imports.put(item, sample);
            }
        }

        resetRateState();
    }

    private void resetRateState(){
        rateTimer = 0f;
        pendingInput.clear();
        pendingImport.clear();
        pendingExport.clear();
    }

    private String simKey(String campaignName, Sector sector){
        return "sector-sim-" + campaignName + "-" + sector.packedPosition();
    }

    public void updateSectorLight(Sector sector){
        PlanetDefinition planet = CampaignRegistry.planetForCampaign(activeCampaign);
        if(sector != null && planet != null){
            sector.light = PlanetOrbit.sectorLight(planet, sector, planetTime);
        }
    }

    public void updateAllSectorLights(){
        PlanetDefinition planet = CampaignRegistry.planetForCampaign(activeCampaign);
        if(planet == null) return;

        for(Sector sector : activeGrid().values()){
            sector.light = PlanetOrbit.sectorLight(planet, sector, planetTime);
        }
    }
    private Array<Item> cachedOres = null;
    private String cachedOreTech = null;
    private final AsyncExecutor executor = new AsyncExecutor(6);
    private CampaignManager campaignManager;
    private String activeCampaign = defaultCampaign;
    private static final Sectors INSTANCE = new Sectors();

    public Sectors() {
        this.campaignManager = new CampaignManager();
    }
    public static Sectors getInstance() {
        return INSTANCE;
    }
    public void playSector(int x, int y) {
        Sector sector = get(x, y);
        if (sector != null) {
            playSector(sector);
        }
    }

    public void playSector(Sector sector) {
        weather.reset();
        resetRateState();

        if (!headless && sector.hasSave() && SaveIO.breakingVersions.contains(sector.getSave().getBuild())) {
            sector.getSave().delete();
            ui.showInfo("$text.save.old");
        }

        if (!sector.hasSave()) {
            for (Mission mission : sector.missions) {
                mission.reset();
            }
            world.loadSector(sector);
            logic.play();
            if(sector.sim != null) sector.sim.applyToWorld();
            if (!headless) {
                sector.saveID = control.saves.addSave("sector-" + sector.packedPosition()).index;
            }
            world.sectors.save();
            world.setSector(sector);
            startPlanetCycle(sector);
            //if(!headless) CoreBlock.startLandingForTeam(defaultTeam);
            if (!sector.complete) sector.currentMission().onBegin();
        } else if (SaveIO.breakingVersions.contains(sector.getSave().getBuild())) {
            ui.showInfo("$text.save.old");
        } else try {
            sector.getSave().load();
            if(sector.sim != null) sector.sim.applyToWorld();
            world.setSector(sector);
            state.set(State.playing);
            startPlanetCycle(sector);
            if (!sector.complete) sector.currentMission().onBegin();
        } catch (Exception e) {
            Log.err(e);
            sector.getSave().delete();

            playSector(sector);

            if (!headless) {
                threads.runGraphics(() -> ui.showError("$text.sector.corrupted"));
            }
        }
    }

    /** Sector maps use the planet's sun/rotation state instead of the automatic day/night cycle. */
    private void startPlanetCycle(Sector sector){
        updateSectorLight(sector);
        if(weather != null){
            weather.setDayNight(false);
        }
        state.darkness = Mathf.clamp(1f - sector.light, 0f, 1f);
    }

    /** If a sector is not yet unlocked, returns null. */
    public Sector get(int x, int y) {
        return activeGrid().get(x, y);
    }

    public Sector get(int position) {
        return activeGrid().get(Bits.getLeftShort(position), Bits.getRightShort(position));
    }

    public Difficulty getDifficulty(Sector sector) {
        if (sector.difficulty == 0) {
            return Difficulty.hard;
        } else if (sector.difficulty < 4) {
            return Difficulty.normal;
        } else if (sector.difficulty < 9) {
            return Difficulty.hard;
        } else if (sector.difficulty < 12) {
            return Difficulty.insane;
        } else {
            return Difficulty.eradication;
        }
    }

    public Array<Item> getOres(int x, int y) {
        Array<Item> defaultOres = techOres();
        Array<Item> ores = activeGenerator().getOres(x, y, defaultOres);
        if(ores == defaultOres) return ores;

        //preset lists may name ores belonging to other tech trees, so filter them too
        Array<Item> filtered = new Array<>(ores.size);
        for(Item ore : ores){
            if(ore.genOre && ore.belongsToTech(state.techTree)) filtered.add(ore);
        }
        return filtered.size == 0 ? defaultOres : filtered;
    }

    public ObjectIntMap<Item> getStoredItems(Sector sector) {
        if(sector == null) return new ObjectIntMap<>();

        if(world.getSector() == sector && state.teams != null){
            ItemModule largest = null;
            int largestTotal = -1;

            for(Tile tile : state.teams.get(state.playerTeam).cores){
                if(tile == null || tile.entity == null || tile.entity.items == null) continue;

                int total = tile.entity.items.total();
                if(total > largestTotal){
                    largestTotal = total;
                    largest = tile.entity.items;
                }
            }

            if(largest != null){
                ObjectIntMap<Item> result = new ObjectIntMap<>();
                largest.forEach((item, amount) -> {
                    if(amount > 0) result.put(item, (int)amount);
                });
                return result;
            }
        }

        if(sector.sim != null){
            return sector.sim.stored.size > 0 ? sector.sim.stored : sector.sim.items;
        }

        return new ObjectIntMap<>();
    }

    /** Liquids present in the generated terrain of this sector. */
    public Array<Liquid> getLiquids(Sector sector) {        Array<Liquid> out = new Array<>();
        if(sector == null) return out;

        PlanetGenerator generator = CampaignRegistry.generator(activeCampaign);
        WorldGenerator terrain = generator == null ? null : generator.terrain();
        if(terrain == null) return out;

        int size = generator.sectorSize();
        int step = Math.max(8, size / 32);
        GenResult result = new GenResult();

        for(int x = step / 2; x < size; x += step){
            for(int y = step / 2; y < size; y += step){
                terrain.generateTile(result, sector.x, sector.y, x, y, true, null, null);

                if(result.floor instanceof Floor){
                    Liquid liquid = ((Floor)result.floor).liquidDrop;
                    if(liquid != null && !out.contains(liquid, true)){
                        out.add(liquid);
                    }
                }
            }
        }

        return out;
    }

    /**Ores usable by the currently selected tech tree, cached until the tree changes.*/
    private Array<Item> techOres(){String tech = state.techTree == null ? "" : state.techTree;
        if(!tech.equals(cachedOreTech) || cachedOres == null){
            cachedOreTech = tech;
            cachedOres = Item.getAllOres(state.techTree);
        }
        return cachedOres;
    }

    /** Unlocks a sector. This shows nearby sectors. */
    public void completeSector(int x, int y) {
        Sector sector = get(x, y);
        if(sector == null){
            sector = createSectorSilent(x, y);
        }
        if(sector == null) return;
        if(world.getSector() == sector && sector.sim != null){
            sector.sim.captureWorld();
        }
        sector.complete = true;

        if(sector.infected){
            sector.infected = false;
            initSector(sector);
        }

        PlanetMap map = CampaignRegistry.planetMap(activeCampaign);
        Region region = map == null ? null : map.regionFor(sector);
        if(region != null){
            for(Region neighbor : region.neighbors){
                if(get(neighbor.x, neighbor.y) == null){
                    createSectorSilent(neighbor.x, neighbor.y);
                }
            }
        }
    }

    /** Creates a sector at a location if it is not present, but does not complete it. */
    public void createSector(int x, int y) {
        createSectorSilent(x, y);
    }

    /** Creates a sector without generating a 2D preview texture. Returns the existing sector if present. */
    public Sector createSectorSilent(int x, int y) {
        GridMap<Sector> grid = activeGrid();

        if (grid.containsKey(x, y)) return grid.get(x, y);

        Sector sector = new Sector();
        sector.x = (short) x;
        sector.y = (short) y;
        sector.complete = false;
        initSector(sector);

        grid.put(sector.x, sector.y, sector);

        return sector;
    }

    public void abandonSector(Sector sector) {
        abandonSector(sector, true);
    }

    public void abandonSector(Sector sector, boolean changeMissions) {
        if (sector.hasSave()) {
            sector.getSave().delete();
        }
        sector.completedMissions = 0;
        sector.complete = false;
        sector.sim = new SectorSim();

        if(changeMissions){
            if(sector.isInfected()){
                sector.infected = true;
                infectNeighbors(sector, 0.8f);
            }else if(sector.isInfectable()){
                sector.infected = true;
                infectNeighbors(sector, 0.5f);
            }
        }

        initSector(sector);

        activeGrid().put(sector.x, sector.y, sector);

        save();
    }

    private void infectNeighbors(Sector sector, float chance){
        PlanetMap map = CampaignRegistry.planetMap(activeCampaign);
        Region region = map == null ? null : map.regionFor(sector);
        if(region == null) return;

        for(Region neighbor : region.neighbors){
            if(!Mathf.chance(chance)) continue;

            Sector other = get(neighbor.x, neighbor.y);
            if(other == null){
                other = createSectorSilent(neighbor.x, neighbor.y);
            }

            boolean alreadyInfected = other.isInfected();
            if(!alreadyInfected){
                for(Mission m : other.missions){
                    if(m.isInfectable()){
                        alreadyInfected = true;
                        break;
                    }
                }
            }

            if(!alreadyInfected){
                activeGenerator().initSector(other);
                other.missions.clear();
                other.missions.add(new BiomassInfectableMission(other.difficulty * 5 + Mathf.randomSeed(other.getSeed(), 1, 4) * 5));
                other.complete = false;
                other.completedMissions = 0;
                other.sim = new SectorSim();
                other.spawns.clear();
                other.spawns.addAll(other.currentMission().getWaves(other));
                refreshSectorPreview(other);
            }
        }
    }

    public void load() {
        SectorSim.registerSerializer();
        campaignManager.loadCampaigns();
        setActiveCampaign(defaultCampaign);
    }

    public void clear() {
        GridMap<Sector> grid = activeGrid();
        for(Sector sector : grid.values()){
            if(sector.texture != null){
                sector.texture.dispose();
            }
            Settings.putBytes(simKey(activeCampaign, sector), new byte[0]);
        }
        grid.clear();
        simTimer = 0f;
        clockSaveTimer = 0f;
        resetRateState();
        save();
        createSector(0, 0);
    }

    public void save() {
        version++;

        Array<Sector> out = new Array<>();
        GridMap<Sector> grid = activeGrid();

        for (Sector sector : grid.values()) {
            if (sector != null && !out.contains(sector, true)) {
                out.add(sector);
            }
        }

        updateAllSectorLights();
        Settings.putFloat("sector-planet-time-" + activeCampaign, planetTime);
        Settings.putObject(campaignSettingsKey(activeCampaign), out);
        clockSaveTimer = 0f;

        for(Sector sector : out){
            if(sector.sim != null){
                Settings.putObject(simKey(activeCampaign, sector), sector.sim);
            }
        }

        launchManager.save();
        Settings.save();
    }

    public String getActiveCampaign(){
        return activeCampaign;
    }

    public void setActiveCampaign(String campaignName){
        activeCampaign = campaignName;
        loadCampaignSectors(campaignName);
    }

    public void refreshSectorPreview(Sector sector){
        updateSectorLight(sector);
        save();
    }

    public void refreshActiveCampaignPreviews(){
        updateAllSectorLights();
    }

    private void initSector(Sector sector) {
        activeGenerator().initSector(sector);

        if(sector.infected && !sector.complete){
            sector.missions.clear();
            sector.missions.add(new BiomassInfectedMission());
            sector.completedMissions = 0;
            if(sector.spawns == null){
                sector.spawns = new Array<>();
            }else{
                sector.spawns.clear();
            }
            sector.spawns.addAll(sector.currentMission().getWaves(sector));
        }
    }

    private void createTexture(Sector sector) {
        if (headless) return;

        if (sector.texture != null) {
            sector.texture.dispose();
        }

        PlanetGenerator generator = CampaignRegistry.generator(activeCampaign);
        WorldGenerator terrain = generator.terrain();
        int mapSize = generator.sectorSize();

        executor.submit(() -> {
            Pixmap pixmap = new Pixmap(sectorImageSize, sectorImageSize, Format.RGBA8888);
            GenResult result = new GenResult();
            GenResult secResult = new GenResult();

            for (int x = 0; x < pixmap.getWidth(); x++) {
                for (int y = 0; y < pixmap.getHeight(); y++) {
                    int toX = x * mapSize / sectorImageSize;
                    int toY = y * mapSize / sectorImageSize;

                    terrain.generateTile(result, sector.x, sector.y, toX, toY, false, null, null);
                    terrain.generateTile(secResult, sector.x, sector.y, toX, ((y + 1) * mapSize / sectorImageSize), false, null, null);

                    int color = ColorMapper.colorFor(result.floor, result.wall, Team.none, result.elevation, secResult.elevation > result.elevation ? (byte) (1 << 6) : (byte) 0);

                    if(sector != null && sector.isInfected()){
                        Block floor = result.floor;
                        Block wall = result.wall;
                        if(floor.infectedVariant != null){
                            floor = floor.infectedVariant;
                        }
                        if(wall.infectedVariant != null){
                            wall = wall.infectedVariant;
                        }
                        color = ColorMapper.colorFor(floor, wall, Team.none, result.elevation, secResult.elevation > result.elevation ? (byte) (1 << 6) : (byte) 0);
                    }

                    pixmap.drawPixel(x, pixmap.getHeight() - 1 - y, color);
                }
            }

            Gdx.app.postRunnable(() -> {
                sector.texture = new Texture(pixmap);
                pixmap.dispose();
            });

            return null;
        });
    }

    public GridMap<Sector> activeGrid(){
        GridMap<Sector> grid = campaignGrids.get(activeCampaign);
        if(grid == null){
            grid = new GridMap<>();
            campaignGrids.put(activeCampaign, grid);
        }
        return grid;
    }

    private CampaignSectorGenerator activeGenerator(){
        return CampaignRegistry.generator(activeCampaign);
    }

    private String campaignSettingsKey(String campaignName){
        return "sector-data-4-" + campaignName;
    }

    private void loadCampaignSectors(String campaignName){
        planetTime = Settings.getFloat("sector-planet-time-" + campaignName, 0f);
        simTimer = 0f;
        clockSaveTimer = 0f;
        resetRateState();
        GridMap<Sector> grid = campaignGrids.get(campaignName);
        if(grid == null){
            grid = new GridMap<>();
            campaignGrids.put(campaignName, grid);
        }else{
            for(Sector sector : grid.values()){
                if(sector.texture != null){
                    sector.texture.dispose();
                }
            }
            grid.clear();
        }

        Array<Sector> out = Settings.getObject(campaignSettingsKey(campaignName), Array.class, Array::new);

        for(Sector sector : out){
            if(sector == null) continue;
            initSector(sector);
            sector.sim = Settings.getObject(simKey(campaignName, sector), SectorSim.class, SectorSim::new);
            if(sector.sim == null){
                sector.sim = new SectorSim();
            }else if(sector.sim.capacity < sector.sim.totalItems()){
                sector.sim.capacity = sector.sim.totalItems();
            }
            grid.put(sector.x, sector.y, sector);
        }

        if(out.size == 0){
            createSector(0, 0);
            save();
        }
    }
}
