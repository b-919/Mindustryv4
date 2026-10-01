package io.anuke.mindustry.maps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.async.AsyncExecutor;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.maps.campaign.CampaignManager;
import io.anuke.mindustry.maps.campaign.CampaignRegistry;
import io.anuke.mindustry.maps.campaign.CampaignSectorGenerator;
import io.anuke.mindustry.io.SaveIO;
import io.anuke.mindustry.maps.generation.WorldGenerator.GenResult;
import io.anuke.mindustry.maps.missions.BiomassInfectableMission;
import io.anuke.mindustry.maps.missions.BiomassInfectedBattleMission;
import io.anuke.mindustry.maps.missions.BiomassInfectedMission;
import io.anuke.mindustry.maps.missions.Mission;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.ColorMapper;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.blocks.Rock;
import io.anuke.ucore.core.Settings;
import io.anuke.ucore.util.*;

import static io.anuke.mindustry.Vars.*;

public class Sectors {
    public static final int sectorImageSize = 32;
    public static final String defaultCampaign = CampaignRegistry.serpulo;

    private final ObjectMap<String, GridMap<Sector>> campaignGrids = new ObjectMap<>();
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
            if (!headless) {
                sector.saveID = control.saves.addSave("sector-" + sector.packedPosition()).index;
            }
            world.sectors.save();
            world.setSector(sector);
            if (!sector.complete) sector.currentMission().onBegin();
        } else if (SaveIO.breakingVersions.contains(sector.getSave().getBuild())) {
            ui.showInfo("$text.save.old");
        } else try {
            sector.getSave().load();
            world.setSector(sector);
            state.set(State.playing);
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

    /**Ores usable by the currently selected tech tree, cached until the tree changes.*/
    private Array<Item> techOres(){
        String tech = state.techTree == null ? "" : state.techTree;
        if(!tech.equals(cachedOreTech) || cachedOres == null){
            cachedOreTech = tech;
            cachedOres = Item.getAllOres(state.techTree);
        }
        return cachedOres;
    }

    /** Unlocks a sector. This shows nearby sectors. */
    public void completeSector(int x, int y) {
        createSector(x, y);
        Sector sector = get(x, y);
        if(sector == null) return;
        sector.complete = true;

        for (GridPoint2 g : Geometry.d4) {
            createSector(x + g.x, y + g.y);
        }
    }

    /** Creates a sector at a location if it is not present, but does not complete it. */
    public void createSector(int x, int y) {
        GridMap<Sector> grid = activeGrid();

        if (grid.containsKey(x, y)) return;

        Sector sector = new Sector();
        sector.x = (short) x;
        sector.y = (short) y;
        sector.complete = false;
        initSector(sector);

        grid.put(sector.x, sector.y, sector);

        if (sector.texture == null) {
            threads.runGraphics(() -> createTexture(sector));
        }
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

        if(changeMissions){
            if(sector.isInfectable()){
                sector.missions.clear();
                sector.missions.add(new BiomassInfectedMission());
                infectNeighbors(sector, 0.5f);
            }else if(sector.isInfected()){
                infectNeighbors(sector, 0.8f);
            }
        }

        initSector(sector);

        activeGrid().put(sector.x, sector.y, sector);

        threads.runGraphics(() -> createTexture(sector));

        save();
    }

    private void infectNeighbors(Sector sector, float chance){
        for(int x = -1; x <= 1; x++){
            for(int y = -1; y <= 1; y++){
                if(x == 0 && y == 0) continue;

                if(Mathf.chance(chance)){
                    int nx = sector.x + x;
                    int ny = sector.y + y;

                    Sector other = get(nx, ny);
                    if(other == null){
                        createSector(nx, ny);
                        other = get(nx, ny);
                    }

                    boolean alreadyInfected = false;
                    for(Mission m : other.missions){
                        if(m.isInfectable() || m.isInfected()){
                            alreadyInfected = true;
                            break;
                        }
                    }

                    if(!alreadyInfected){
                        other.missions.clear();
                        other.missions.add(new BiomassInfectableMission(other.difficulty * 5 + Mathf.randomSeed(other.getSeed(), 1, 4) * 5));
                        other.complete = false;
                        other.completedMissions = 0;
                        activeGenerator().initSector(other);
                        refreshSectorPreview(other);
                    }
                }
            }
        }
    }

    public void load() {
        campaignManager.loadCampaigns();
        setActiveCampaign(defaultCampaign);
    }

    public void clear() {
        GridMap<Sector> grid = activeGrid();
        for(Sector sector : grid.values()){
            if(sector.texture != null){
                sector.texture.dispose();
            }
        }
        grid.clear();
        save();
        createSector(0, 0);
    }

    public void save() {
        Array<Sector> out = new Array<>();
        GridMap<Sector> grid = activeGrid();

        for (Sector sector : grid.values()) {
            if (sector != null && !out.contains(sector, true)) {
                out.add(sector);
            }
        }

        Settings.putObject(campaignSettingsKey(activeCampaign), out);
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
        if(sector == null || headless) return;
        threads.runGraphics(() -> createTexture(sector));
    }

    public void refreshActiveCampaignPreviews(){
        if(headless) return;
        for(Sector sector : activeGrid().values()){
            refreshSectorPreview(sector);
        }
    }

    private void initSector(Sector sector) {
        activeGenerator().initSector(sector);
    }

    private void createTexture(Sector sector) {
        if (headless) return;

        if (sector.texture != null) {
            sector.texture.dispose();
        }

        executor.submit(() -> {
            Pixmap pixmap = new Pixmap(sectorImageSize, sectorImageSize, Format.RGBA8888);
            GenResult result = new GenResult();
            GenResult secResult = new GenResult();

            for (int x = 0; x < pixmap.getWidth(); x++) {
                for (int y = 0; y < pixmap.getHeight(); y++) {
                    int toX = x * sectorSize / sectorImageSize;
                    int toY = y * sectorSize / sectorImageSize;

                    world.generator.generateTile(result, sector.x, sector.y, toX, toY, false, null, null);
                    world.generator.generateTile(secResult, sector.x, sector.y, toX, ((y + 1) * sectorSize / sectorImageSize), false, null, null);

                    int color = ColorMapper.colorFor(result.floor, result.wall, Team.none, result.elevation, secResult.elevation > result.elevation ? (byte) (1 << 6) : (byte) 0);

                    if(sector != null && sector.isInfected()){
                        Block floor = result.floor;
                        Block wall = result.wall;
                        if(floor instanceof Floor && ((Floor) floor).infectedVariant != null){
                            floor = ((Floor) floor).infectedVariant;
                        }
                        if(wall instanceof Rock && ((Rock) wall).infectedVariant != null){
                            wall = ((Rock) wall).infectedVariant;
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
        return "sector-data-2-" + campaignName;
    }

    private void loadCampaignSectors(String campaignName){
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
            createTexture(sector);
            initSector(sector);
            grid.put(sector.x, sector.y, sector);
        }

        if(out.size == 0){
            createSector(0, 0);
            save();
        }
    }
}
