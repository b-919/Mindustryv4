package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.planet.PlanetHover;
import io.anuke.mindustry.graphics.planet.PlanetRenderer;
import io.anuke.mindustry.maps.Sector;
import io.anuke.mindustry.maps.campaign.CampaignManager;
import io.anuke.mindustry.maps.campaign.CampaignRegistry;
import io.anuke.mindustry.maps.campaign.PlanetDefinition;
import io.anuke.mindustry.maps.campaign.PlanetMap;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.Element;
import io.anuke.ucore.scene.Group;
import io.anuke.ucore.scene.event.InputEvent;
import io.anuke.ucore.scene.event.InputListener;
import io.anuke.ucore.scene.event.Touchable;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.layout.Cell;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.scene.ui.layout.Unit;
import io.anuke.ucore.scene.utils.Cursors;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.core.Core;

import static io.anuke.mindustry.Vars.world;

public class SectorsDialog extends FloatingDialog{
    private static final float sectorSize = Unit.dp.scl(32 * 5);
    private Sector selected;
    private Table sectorTable;
    private Table campaignTable;
    private Table exportedTable;
    private ScrollPane campaignPane;
    private SectorView view;
    private final CampaignManager campaignManager = new CampaignManager();
    private final PlanetRenderer planetRenderer = new PlanetRenderer();
    private PlanetDefinition selectedPlanet;
    private float selectedDrawX, selectedDrawY;

    public SectorsDialog(){
        super("");

        sectorTable = new Table(){
            @Override
            public float getPrefWidth(){
                return sectorSize * 2f;
            }
        };
        sectorTable.visible(() -> selected != null);
        sectorTable.update(() -> {
            if(selected != null){
                float x = Mathf.clamp(selectedDrawX, sectorTable.getWidth() / 2f, width - sectorTable.getWidth() / 2f);
                float y = Mathf.clamp(selectedDrawY - sectorSize / 2f + 1, 10f, height - 90f + sectorTable.getHeight());
                sectorTable.setPosition(x, y, Align.top);
            }
        });

        campaignTable = new Table("button");
        campaignTable.top().left().margin(6f);

        campaignPane = new ScrollPane(campaignTable);
        campaignPane.setFadeScrollBars(false);
        campaignPane.setScrollingDisabled(true, false);
        campaignPane.update(() -> campaignPane.setPosition(10f, height - 10f, Align.topLeft));

        exportedTable = new Table("button");
        exportedTable.top().right().margin(6f);
        exportedTable.update(() -> exportedTable.setPosition(width - 10f, height - 10f, Align.topRight));

        Group container = new Group();
        container.setTouchable(Touchable.childrenOnly);
        container.setFillParent(true);
        container.addChild(sectorTable);
        container.addChild(campaignPane);
        container.addChild(exportedTable);

        margin(0);
        getTitleTable().clear();
        clear();
        stack(content(), container, buttons()).grow();

        shown(this::setup);
        hidden(() -> {
            if(Core.scene.getScrollFocus() == view){
                Core.scene.setScrollFocus(null);
            }
            teardown3dScene();
        });
    }

    void setup(){
        teardown3dScene();
        selected = null;
        selectedPlanet = CampaignRegistry.planetForCampaign(world.sectors.getActiveCampaign());
        world.sectors.updateAllSectorLights();

        sectorTable.clear();
        campaignTable.clear();
        exportedTable.clear();
        content().clear();
        buttons().clear();
        buttons().bottom().margin(15);

        addCloseButton();
        setupCampaigns();
        Vars.launchManager.load();
        setupExported();
        content().add(view = new SectorView()).grow();
        view.rebuildPlanetModels();
        Core.scene.setScrollFocus(view);
    }

    void setupCampaigns(){
        campaignManager.loadCampaigns();

        campaignTable.defaults().height(52f).pad(2f);
        campaignTable.add("$text.campaigns").left().pad(4f);
        campaignTable.row();

        for(PlanetDefinition planet : CampaignRegistry.planets()){
            float buttonWidth = Math.max(180f, planet.name.length() * 15f + 36f);
            campaignTable.addButton(planet.name, () -> {
                if(!planet.campaign.equals(world.sectors.getActiveCampaign())){
                    world.sectors.setActiveCampaign(planet.campaign);
                    selectedPlanet = planet;
                    selected = null;
                    world.sectors.updateAllSectorLights();
                    sectorTable.clear();
                    view.resetCamera();
                    view.rebuildPlanetModels();
                    Vars.launchManager.load();
                    setupExported();
                }
            }).width(buttonWidth);
            campaignTable.row();
        }

        campaignTable.pack();
        campaignPane.setSize(campaignTable.getPrefWidth() + 10f, Math.min(campaignTable.getPrefHeight(), Gdx.graphics.getHeight() - 130f));
    }

    void setupExported(){
        exportedTable.clear();
        exportedTable.defaults().pad(2f);
        exportedTable.add("$text.exported").left().pad(4f);
        exportedTable.row();

        Table items = new Table();
        items.left();
        int i = 0;
        for(io.anuke.mindustry.type.Item item : Vars.launchManager.getInventory().keys()){
            int amount = Vars.launchManager.getAmount(item);
            if(amount > 0){
                items.addImage(item.region).size(8 * 3).padRight(4);
                items.add(amount + " / " + Vars.launchManager.getCapacity()).left().padRight(10);
                if(++i % 2 == 0) items.row();
            }
        }

        if(i == 0){
            items.add("$text.none").color(Color.GRAY);
        }

        exportedTable.add(items).left();
        exportedTable.row();

        exportedTable.pack();
    }

    void selectSector(Sector sector){
        selected = sector;

        sectorTable.clear();
        sectorTable.background("button").margin(5);
        sectorTable.defaults().pad(3);
        sectorTable.add(Bundles.format("text.sector", sector.x + ", " + sector.y));
        sectorTable.row();

        if(selected.missions.size > 0 && selected.completedMissions < selected.missions.size && !selected.complete){
            sectorTable.labelWrap(Bundles.format("text.mission", selected.getDominantMission().menuDisplayString())).growX();
            sectorTable.row();
        }

        if(selected.hasSave()){
            sectorTable.labelWrap(Bundles.format("text.sector.time", selected.getSave().getPlayTime())).growX();
            sectorTable.row();
        }

        sectorTable.table(t -> {
            boolean canDeploy = (selected.missions.size > 0 || selected.complete) && (view.isUnlocked(selected) || selected.hasSave());

            if(canDeploy){
                Cell<?> cell = t.addImageTextButton(selected.hasSave() ? "$text.sector.resume" : "$text.sector.deploy", "icon-play", 10 * 3, () -> {
                    hide();
                    Vars.ui.loadLogic(() -> world.sectors.playSector(selected));
                }).height(60f);

                if(selected.hasSave()){
                    t.addImageTextButton("$text.sector.abandon", "icon-cancel", 16 * 2, () ->
                        Vars.ui.showConfirm("$text.confirm", "$text.sector.abandon.confirm", () -> {
                            world.sectors.abandonSector(selected);
                            selectSector(selected);
                        })
                    ).width(sectorSize / Unit.dp.scl(1f)).height(60f);
                    cell.width(sectorSize / Unit.dp.scl(1f));
                }else{
                    cell.width(sectorSize * 2f / Unit.dp.scl(1f));
                }
            }else{
                t.add("$text.sector.locked").color(Color.GRAY).pad(10);
            }
        }).pad(-5).growX().padTop(0);

        sectorTable.pack();
        sectorTable.act(Gdx.graphics.getDeltaTime());
    }

    public Sector getSelected(){
        return selected;
    }

    void teardown3dScene(){
        if(view != null){
            view.disposeScene();
            view.remove();
            view = null;
        }
        content().clear();
        sectorTable.clear();
        campaignTable.clear();
    }

    class SectorView extends Element{
        float lastX, lastY;
        float rotLon = 0f, rotLat = 0f;
        float zoom = 1f, targetZoom = 1f;
        float pendingScroll = 0f;
        float downX, downY;
        boolean dragged;
        boolean pendingClick;
        Sector hoveredSector;
        float hoveredX, hoveredY;
        boolean meshFailed;

        void resetCamera(){
            rotLon = 0f;
            rotLat = 0f;
            zoom = 1f;
            targetZoom = 1f;
        }

        void rebuildPlanetModels(){
            PlanetDefinition planet = selectedPlanet == null ? CampaignRegistry.planetForCampaign(world.sectors.getActiveCampaign()) : selectedPlanet;
            planetRenderer.rebuildPlanetModels(planet);
        }

        SectorView(){
            addListener(new InputListener(){
                float pinchStartDistance = -1f;
                float pinchStartZoom = 1f;

                void updatePinch(){
                    if(pinchStartDistance <= 0f) return;

                    float newDistance = Vector2.dst(Gdx.input.getX(0), Gdx.input.getY(0),
                        Gdx.input.getX(1), Gdx.input.getY(1));

                    targetZoom = Mathf.clamp(pinchStartZoom * pinchStartDistance / Math.max(1f, newDistance), 0.75f, 2.1f);
                    zoom = targetZoom;
                    dragged = true;
                }

                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                    if(pointer > 1) return false;

                    if(pointer == 1){
                        pinchStartDistance = Vector2.dst(Gdx.input.getX(0), Gdx.input.getY(0), Gdx.input.getX(1), Gdx.input.getY(1));
                        pinchStartZoom = zoom;
                        dragged = true;
                        return true;
                    }
                    lastX = x;
                    lastY = y;
                    downX = x;
                    downY = y;
                    dragged = false;
                    pendingClick = false;

                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    if(pointer > 1) return;

                    if(pointer == 1){
                        updatePinch();
                        return;
                    }

                    if(pinchStartDistance > 0f){
                        updatePinch();
                        return;
                    }

                    if(Math.abs(x - downX) > 5f || Math.abs(y - downY) > 5f){
                        dragged = true;
                    }

                    float factor = 0.01f * zoom;
                    rotLon += (x - lastX) * factor * (float)Math.cos(rotLat);
                    rotLat = Mathf.clamp(rotLat - (y - lastY) * factor, -1.4f, 1.4f);

                    lastX = x;
                    lastY = y;
                }

                @Override
                public boolean scrolled(InputEvent event, float x, float y, int amount){
                    pendingScroll += amount * 0.06f;
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                    if(pointer > 1) return;
                    if(pointer == 0){
                        pendingClick = !dragged;
                    }
                    if(pointer == 1){
                        pinchStartDistance = -1f;
                    }
                    Cursors.restoreCursor();
                }
            });
        }

        @Override
        public void draw(){
            if(pendingScroll != 0f){
                targetZoom = Mathf.clamp(targetZoom + pendingScroll, 0.75f, 2.1f);
                pendingScroll = 0f;
            }

            zoom = Mathf.lerpDelta(zoom, targetZoom, 0.4f);
            if(Mathf.in(zoom, targetZoom, 0.001f)){
                zoom = targetZoom;
            }

            Draw.color(0.02f, 0.03f, 0.06f, 1f);
            Fill.crect(x, y, width, height);
            Draw.reset();

            PlanetDefinition planet = selectedPlanet == null ? CampaignRegistry.planetForCampaign(world.sectors.getActiveCampaign()) : selectedPlanet;
            PlanetHover hovered;
            try{
                hovered = planetRenderer.render(x, y, width, height, planet, rotLon, rotLat, zoom, selected,
                    world.sectors.getPlanetTime());
                meshFailed = false;
            }catch(Throwable t){
                if(!meshFailed){
                    Log.err(t);
                }
                meshFailed = true;
                hovered = new PlanetHover();
            }
            if(meshFailed){
                drawSerpuloFallback(planet);
            }

            if(pendingClick && hovered.sector != null){
                selectedDrawX = hovered.x;
                selectedDrawY = hovered.y;
                selectSector(hovered.sector);
            }
            pendingClick = false;
            hoveredSector = hovered.sector;
            hoveredX = hovered.x;
            hoveredY = hovered.y;
        }

        void drawSerpuloFallback(PlanetDefinition planet){
            if(planet == null) return;
            float cx = x + width / 2f;
            float cy = y + height / 2f;
            float radius = Math.min(width, height) * 0.23f * zoom;

            Draw.color(planet.colorR * 0.45f, planet.colorG * 0.45f, planet.colorB * 0.45f, 0.9f);
            Draw.rect("icon-mission-background", cx + radius * 0.08f, cy - radius * 0.08f, radius * 2.08f, radius * 2.08f);
            Draw.color(planet.colorR, planet.colorG, planet.colorB, 1f);
            Draw.rect("icon-mission-background", cx, cy, radius * 2f, radius * 2f);
            Draw.color(Palette.accent);
            Draw.alpha(0.22f);
            Draw.rect("sector-select", cx, cy, radius * 2.02f, radius * 2.02f);
            Draw.reset();
        }

        void disposeScene(){}

        public boolean isUnlocked(Sector sector){
            return isUnlockedStatic(sector);
        }
    }

    public static boolean isUnlockedStatic(Sector sector){
        if(sector == null) return false;
        PlanetMap map = CampaignRegistry.planetMap(world.sectors.getActiveCampaign());
        return map != null ? map.isUnlocked(sector) : sector.complete;
    }

    public void dispose(){
        planetRenderer.dispose();
    }
}
