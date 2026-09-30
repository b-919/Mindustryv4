package io.anuke.mindustry.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.input.GestureDetector.GestureListener;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.mindustry.game.Schematic;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.input.PlaceUtils.NormalizeDrawResult;
import io.anuke.mindustry.input.PlaceUtils.NormalizeResult;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.ui.dialogs.FloatingDialog;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.*;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.*;
import static io.anuke.mindustry.input.PlaceMode.*;

public class MobileInput extends InputHandler implements GestureListener{
    /** Maximum speed the player can pan. */
    private static final float maxPanSpeed = 1.3f;
    private static Rectangle r1 = new Rectangle(), r2 = new Rectangle();
    /** Distance to edge of screen to start panning. */
    private final float edgePan = io.anuke.ucore.scene.ui.layout.Unit.dp.scl(60f);

    //gesture data
    private Vector2 vector = new Vector2();
    private boolean canPan;
    private boolean zoomed = false;
    /** Camera scale the current pinch gesture started from, so the zoom is relative to it. */
    private float pinchStartScale = 1f;
    /** Set of completed guides. */
    private ObjectSet<String> guides = new ObjectSet<>();

    /** Position where the player started dragging a line. */
    private int lineStartX, lineStartY;

    /** Animation scale for line. */
    private float lineScale;
    /** Animation data for crosshair. */
    private float crosshairScale;
    private TargetTrait lastTarget;

    /** List of currently selected tiles to place. */
    private Array<PlaceRequest> selection = new Array<>();
    /** Place requests to be removed. */
    private Array<PlaceRequest> removals = new Array<>();
    /** Whether or not the player is currently shifting all placed tiles. */
    private boolean selecting;
    /** Whether the player is currently in line-place mode. */
    private boolean lineMode;
    /** Whether no recipe was available when switching to break mode. */
    private Recipe lastRecipe;
    private float schemX, schemY;
    private boolean draggingSchematic;
    /** Last placed request. Used for drawing block overlay. */
    private PlaceRequest lastPlaced;

    public MobileInput(Player player){
        super(player);
        Inputs.addProcessor(new GestureDetector(20, 0.5f, 0.4f, 0.15f, this));
    }

    //region utility methods

    /** Check and assign targets for a specific position. */
    void checkTargets(float x, float y){
        Unit unit = Units.getClosestEnemy(player.getTeam(), x, y, 20f, u -> !u.isDead());

        if(unit != null){
            player.setMineTile(null);
            player.target = unit;
        }else{
            Tile tile = world.tileWorld(x, y);
            if(tile != null) tile = tile.target();

            if(tile != null && tile.synthetic() && state.teams.areEnemies(player.getTeam(), tile.getTeam())){
                TileEntity entity = tile.entity;
                player.setMineTile(null);
                player.target = entity;
            }
        }
    }

    /** Returns whether this tile is in the list of requests, or at least colliding with one. */
    boolean hasRequest(Tile tile){
        return getRequest(tile) != null;
    }

    /** Returns whether this block overlaps any selection requests. */
    boolean checkOverlapPlacement(int x, int y, Block block){
        r2.setSize(block.size * tilesize);
        r2.setCenter(x * tilesize + block.offset(), y * tilesize + block.offset());

        for(PlaceRequest req : selection){
            Tile other = req.tile();

            if(other == null || req.remove) continue;

            r1.setSize(req.recipe.result.size * tilesize);
            r1.setCenter(other.worldx() + req.recipe.result.offset(), other.worldy() + req.recipe.result.offset());

            if(r2.overlaps(r1)){
                return true;
            }
        }
        return false;
    }

    /** Returns the selection request that overlaps this tile, or null. */
    PlaceRequest getRequest(Tile tile){
        r2.setSize(tilesize);
        r2.setCenter(tile.worldx(), tile.worldy());

        for(PlaceRequest req : selection){
            Tile other = req.tile();

            if(other == null) continue;

            if(!req.remove){
                r1.setSize(req.recipe.result.size * tilesize);
                r1.setCenter(other.worldx() + req.recipe.result.offset(), other.worldy() + req.recipe.result.offset());

                if(r2.overlaps(r1)){
                    return req;
                }
            }else{

                r1.setSize(other.block().size * tilesize);
                r1.setCenter(other.worldx() + other.block().offset(), other.worldy() + other.block().offset());

                if(r2.overlaps(r1)){
                    return req;
                }
            }
        }
        return null;
    }

    void removeRequest(PlaceRequest request){
        selection.removeValue(request, true);
        removals.add(request);
    }

    void drawPlace(int x, int y, Block block, int rotation){
        if(block == null) return;
        if(validPlace(x, y, block, rotation)){
            Draw.color();

            TextureRegion[] regions = block.getBlockIcon();

            for(TextureRegion region : regions){
                Draw.rect(region, x * tilesize + block.offset(), y * tilesize + block.offset(),
                        region.getRegionWidth(), region.getRegionHeight(), block.rotate ? rotation * 90 : 0);
            }
        }else{
            Draw.color(Palette.removeBack);
            Lines.square(x * tilesize + block.offset(), y * tilesize + block.offset() - 1, block.size * tilesize / 2f);
            Draw.color(Palette.remove);
            Lines.square(x * tilesize + block.offset(), y * tilesize + block.offset(), block.size * tilesize / 2f);
        }
    }

    void drawRequest(PlaceRequest request){
        Tile tile = request.tile();

        if(!request.remove){
            //draw placing request
            float offset = request.recipe.result.offset();
            TextureRegion[] regions = request.recipe.result.getBlockIcon();

            Draw.alpha(Mathf.clamp((1f - request.scale) / 0.5f));
            Draw.tint(Color.WHITE, Palette.breakInvalid, request.redness);

            for(TextureRegion region : regions){
                Draw.rect(region, tile.worldx() + offset, tile.worldy() + offset,
                        region.getRegionWidth() * request.scale, region.getRegionHeight() * request.scale,
                        request.recipe.result.rotate ? request.rotation * 90 : 0);
            }
        }else{
            float rad = (tile.block().size * tilesize / 2f - 1) * request.scale;
            Draw.alpha(0f);
            //draw removing request
            Draw.tint(Palette.removeBack);
            Lines.square(tile.drawx(), tile.drawy()-1, rad);
            Draw.tint(Palette.remove);
            Lines.square(tile.drawx(), tile.drawy(), rad);
        }
    }

    void showGuide(String type){
        if(!guides.contains(type) && !Settings.getBool(type, false)){
            FloatingDialog dialog = new FloatingDialog("$text." + type + ".title");
            dialog.addCloseButton();
            dialog.content().left();
            dialog.content().add("$text." + type).growX().wrap();
            dialog.content().row();
            dialog.content().addCheck("$text.showagain", false, checked -> {
                Settings.putBool(type, checked);
                Settings.save();
            }).growX().left().get().left();
            dialog.show();
            guides.add(type);
        }
    }

    //endregion

    //region UI and drawing

    @Override
    public void buildUI(Table table){
        table.addImage("blank").color(Palette.accent).height(3f).colspan(4).growX();
        table.row();
        table.left().margin(0f).defaults().size(48f);

        table.addImageButton("icon-break", "clear-toggle-partial", 16 * 2f, () -> {
            mode = mode == breaking ? recipe == null ? none : placing : breaking;
            lastRecipe = recipe;
            if(mode == breaking){
                showGuide("deconstruction");
            }
        }).update(l -> l.setChecked(mode == breaking));

        table.addImageButton("icon-copy", "clear-partial", 16 * 2f, () -> {
            mode = mode == copying ? (recipe == null ? none : placing) : copying;
        }).update(b -> b.setChecked(mode == copying));

        //rotate button
        table.addImageButton("icon-arrow", "clear-partial", 16 * 2f, () -> {
            if(mode == PlaceMode.schematic && schematic != null){
                schematic.rotate();
            }else{
                rotation = Mathf.mod(rotation + 1, 4);
            }
        })
        .update(i -> i.getImage().setRotationOrigin(rotation * 90, Align.center))
        .visible(() -> (recipe != null && recipe.result.rotate) || (mode == PlaceMode.schematic && schematic != null));

        //cancel button
        table.addImageButton("icon-cancel", "clear-partial", 16 * 2f, () -> {
            player.clearBuilding();
            mode = none;
            recipe = null;
            schematic = null;
        }).visible(() -> player.isBuilding() || recipe != null || mode == breaking || mode == PlaceMode.schematic);

        //confirm button
        table.addImageButton("icon-check", "clear-partial", 16 * 2f, () -> {
            if(mode == PlaceMode.schematic && schematic != null){
                schematics.place(schematic, tileX(0), tileY(0), player.getTeam());
                mode = PlaceMode.none;
                schematic = null;
                return;
            }
            for(PlaceRequest request : selection){
                Tile tile = request.tile();

                //actually place/break all selected blocks
                if(tile != null){
                    if(!request.remove){
                        rotation = request.rotation;
                        Recipe before = recipe;
                        recipe = request.recipe;
                        tryPlaceBlock(tile.x, tile.y);
                        recipe = before;
                    }else{
                        tryBreakBlock(tile.x, tile.y);
                    }
                }
            }

            //move all current requests to removal array so they fade out
            removals.addAll(selection);
            selection.clear();
            selecting = false;
        }).visible(() -> !selection.isEmpty() || (mode == PlaceMode.schematic && schematic != null));
    }

    @Override
    public boolean isDrawing(){
        return selection.size > 0 || removals.size > 0 || lineMode || player.target != null || mode != PlaceMode.none;
    }

    @Override
    public boolean isPlacing(){
        return super.isPlacing() && (mode == placing || mode == PlaceMode.schematic || mode == copying);
    }

    @Override
    public void drawOutlined(){
        Lines.stroke(1f);

        Shaders.mix.color.set(Palette.accent);
        Graphics.shader(Shaders.mix);

        //draw removals
        for(PlaceRequest request : removals){
            Tile tile = request.tile();

            if(tile == null) continue;

            request.scale = Mathf.lerpDelta(request.scale, 0f, 0.2f);
            request.redness = Mathf.lerpDelta(request.redness, 0f, 0.2f);

            drawRequest(request);
        }

        //draw list of requests
        for(PlaceRequest request : selection){
            Tile tile = request.tile();

            if(tile == null) continue;

            if((!request.remove && validPlace(tile.x, tile.y, request.recipe.result, request.rotation))
                    || (request.remove && validBreak(tile.x, tile.y))){
                request.scale = Mathf.lerpDelta(request.scale, 1f, 0.2f);
                request.redness = Mathf.lerpDelta(request.redness, 0f, 0.2f);
            }else{
                request.scale = Mathf.lerpDelta(request.scale, 0.5f, 0.1f);
                request.redness = Mathf.lerpDelta(request.redness, 1f, 0.2f);
            }


            drawRequest(request);

            //draw last placed request
            if(!request.remove && request == lastPlaced && request.recipe != null){
                request.recipe.result.drawPlace(tile.x, tile.y, rotation, validPlace(tile.x, tile.y, request.recipe.result, rotation));
            }
        }

        Graphics.shader();

        Draw.color(Palette.accent);

        //Draw lines
        if(lineMode){
            int tileX = tileX(Gdx.input.getX());
            int tileY = tileY(Gdx.input.getY());

            //draw placing
            if(mode == placing && recipe != null){
                NormalizeDrawResult dresult = PlaceUtils.normalizeDrawArea(recipe.result, lineStartX, lineStartY, tileX, tileY, true, maxLength, lineScale);

                Lines.rect(dresult.x, dresult.y, dresult.x2 - dresult.x, dresult.y2 - dresult.y);

                NormalizeResult result = PlaceUtils.normalizeArea(lineStartX, lineStartY, tileX, tileY, rotation, true, maxLength);

                //go through each cell and draw the block to place if valid
                for(int i = 0; i <= result.getLength(); i += recipe.result.size){
                    int x = lineStartX + i * Mathf.sign(tileX - lineStartX) * Mathf.bool(result.isX());
                    int y = lineStartY + i * Mathf.sign(tileY - lineStartY) * Mathf.bool(!result.isX());

                    if(!checkOverlapPlacement(x, y, recipe.result) && validPlace(x, y, recipe.result, result.rotation)){
                        Draw.color();

                        TextureRegion[] regions = recipe.result.getBlockIcon();

                        for(TextureRegion region : regions){
                            Draw.rect(region, x * tilesize + recipe.result.offset(), y * tilesize + recipe.result.offset(),
                                    region.getRegionWidth() * lineScale, region.getRegionHeight() * lineScale, recipe.result.rotate ? result.rotation * 90 : 0);
                        }
                    }else{
                        Draw.color(Palette.removeBack);
                        Lines.square(x * tilesize + recipe.result.offset(), y * tilesize + recipe.result.offset() - 1, recipe.result.size * tilesize / 2f);
                        Draw.color(Palette.remove);
                        Lines.square(x * tilesize + recipe.result.offset(), y * tilesize + recipe.result.offset(), recipe.result.size * tilesize / 2f);
                    }
                }

            }else if(mode == breaking){
                //draw breaking
                NormalizeDrawResult result = PlaceUtils.normalizeDrawArea(Blocks.air, lineStartX, lineStartY, tileX, tileY, false, maxLength, 1f);
                NormalizeResult dresult = PlaceUtils.normalizeArea(lineStartX, lineStartY, tileX, tileY, rotation, false, maxLength);

                for(int x = dresult.x; x <= dresult.x2; x++){
                    for(int y = dresult.y; y <= dresult.y2; y++){
                        Tile other = world.tile(x, y);
                        if(other == null || !validBreak(other.x, other.y)) continue;
                        other = other.target();

                        Draw.color(Palette.removeBack);
                        Lines.square(other.drawx(), other.drawy()-1, other.block().size * tilesize / 2f - 1);
                        Draw.color(Palette.remove);
                        Lines.square(other.drawx(), other.drawy(), other.block().size * tilesize / 2f - 1);
                    }
                }

                Draw.color(Palette.removeBack);
                Lines.rect(result.x, result.y - 1, result.x2 - result.x, result.y2 - result.y);
                Draw.color(Palette.remove);
                Lines.rect(result.x, result.y, result.x2 - result.x, result.y2 - result.y);

            }else if(mode == copying){
                NormalizeDrawResult result = PlaceUtils.normalizeDrawArea(Blocks.air, lineStartX, lineStartY, tileX, tileY, false, maxLength, 1f);
                NormalizeResult dresult = PlaceUtils.normalizeArea(lineStartX, lineStartY, tileX, tileY, rotation, false, maxLength);

                for(int x = dresult.x; x <= dresult.x2; x++){
                    for(int y = dresult.y; y <= dresult.y2; y++){
                        Tile tile = world.tile(x, y);
                        if(tile == null || tile.block() == Blocks.air) continue;

                        Draw.color(Palette.accentBack);
                        Lines.square(tile.drawx(), tile.drawy()-1, tile.block().size * tilesize / 2f - 1);
                        Draw.color(Palette.accent);
                        Lines.square(tile.drawx(), tile.drawy(), tile.block().size * tilesize / 2f - 1);
                    }
                }

                Draw.color(Palette.accentBack);
                Lines.rect(result.x, result.y - 1, result.x2 - result.x, result.y2 - result.y);
                Draw.color(Palette.accent);
                Lines.rect(result.x, result.y, result.x2 - result.x, result.y2 - result.y);
                Draw.reset();
            }

        }

        if(mode == PlaceMode.schematic && schematic != null){
            int tileX = tileX(Gdx.input.getX());
            int tileY = tileY(Gdx.input.getY());

            for(Schematic.Stile tile : schematic.tiles){
                int ox = tileX + tile.x + (tile.block.size - 1) / 2;
                int oy = tileY + tile.y + (tile.block.size - 1) / 2;
                drawPlace(ox, oy, tile.block, tile.rotation);
            }
        }

        TargetTrait target = player.target;

        //draw targeting crosshair
        if(target != null){
            if(target != lastTarget){
                crosshairScale = 0f;
                lastTarget = target;
            }

            crosshairScale = Mathf.lerpDelta(crosshairScale, 1f, 0.2f);

            Draw.color(Palette.remove);
            Lines.stroke(1f);

            float radius = Interpolation.swingIn.apply(crosshairScale);

            Lines.poly(target.getX(), target.getY(), 4, 7f * radius, Timers.time() * 1.5f);
            Lines.spikes(target.getX(), target.getY(), 3f * radius, 6f * radius, 4, Timers.time() * 1.5f);
        }

        Draw.reset();
    }

    //endregion

    //region input events

    @Override
    public int tileX(float cursorX){
        if(mode == PlaceMode.schematic && schematic != null){
            return world.toTile(schemX - (schematic.width - 1) * tilesize / 2f);
        }
        return super.tileX(cursorX);
    }

    @Override
    public int tileY(float cursorY){
        if(mode == PlaceMode.schematic && schematic != null){
            return world.toTile(schemY - (schematic.height - 1) * tilesize / 2f);
        }
        return super.tileY(cursorY);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button){
        if(state.is(State.menu) || player.isDead()) return false;

        //get tile on cursor
        Tile cursor = tileAt(screenX, screenY);

        float worldx = Graphics.world(screenX, screenY).x, worldy = Graphics.world(screenX, screenY).y;

        if(mode == PlaceMode.schematic && schematic != null){
            float sw = schematic.width * tilesize;
            float sh = schematic.height * tilesize;
            if(worldx >= schemX - sw/2f && worldx <= schemX + sw/2f && worldy >= schemY - sh/2f && worldy <= schemY + sh/2f){
                draggingSchematic = true;
                return true;
            }else{
                draggingSchematic = false;
            }
        }

        //ignore off-screen taps
        if(cursor == null || ui.hasMouse(screenX, screenY)) return false;

        //only begin selecting if the tapped block is a request
        selecting = hasRequest(cursor) && isPlacing() && mode == placing;

        if(mode == copying){
            lineStartX = cursor.x;
            lineStartY = cursor.y;
            lineMode = true;
        }

        //call tap events
        if(pointer == 0 && !selecting && mode == none){
            tryTapPlayer(worldx, worldy);
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button){
        //place down a line if in line mode
        if(lineMode){
            int tileX = tileX(screenX);
            int tileY = tileY(screenY);

            if(mode == placing && recipe != null){
                //normalize area
                NormalizeResult result = PlaceUtils.normalizeArea(lineStartX, lineStartY, tileX, tileY, rotation, true, 100);

                rotation = result.rotation;

                //collect all placement positions
                Array<int[]> plans = new Array<>();
                for(int i = 0; i <= result.getLength(); i += recipe.result.size){
                    int x = lineStartX + i * Mathf.sign(tileX - lineStartX) * Mathf.bool(result.isX());
                    int y = lineStartY + i * Mathf.sign(tileY - lineStartY) * Mathf.bool(!result.isX());
                    plans.add(new int[]{x, y, result.rotation, 0});
                }

                //let the block handle line placement (e.g. bridge replacement)
                recipe.result.handlePlacementLine(plans);

                //place blocks on line, handling bridge/junction replacements
                for(int[] plan : plans){
                    int px = plan[0], py = plan[1], prot = plan[2];

                    io.anuke.mindustry.world.Block bridgeBlock = null;
                    io.anuke.mindustry.world.Block junctionBlock = null;

                    if(recipe.result instanceof io.anuke.mindustry.world.blocks.distribution.Conveyor){
                        io.anuke.mindustry.world.blocks.distribution.Conveyor conv = (io.anuke.mindustry.world.blocks.distribution.Conveyor) recipe.result;
                        bridgeBlock = conv.bridgeReplacement;
                        junctionBlock = conv.junctionReplacement;
                    }else if(recipe.result instanceof io.anuke.mindustry.world.blocks.distribution.Conduit){
                        io.anuke.mindustry.world.blocks.distribution.Conduit conduit = (io.anuke.mindustry.world.blocks.distribution.Conduit) recipe.result;
                        bridgeBlock = conduit.bridgeReplacement;
                        junctionBlock = conduit.junctionReplacement;
                    }

                    Recipe placeRecipe = recipe;
                    if(plan.length > 3 && plan[3] == -1 && bridgeBlock != null){
                        Recipe bridgeRecipe = Recipe.getByResult(bridgeBlock);
                        if(bridgeRecipe != null){
                            placeRecipe = bridgeRecipe;
                        }
                    }else if(plan.length > 3 && plan[3] == -2 && junctionBlock != null){
                        Recipe junctionRecipe = Recipe.getByResult(junctionBlock);
                        if(junctionRecipe != null){
                            placeRecipe = junctionRecipe;
                        }
                    }

                    if(!checkOverlapPlacement(px, py, placeRecipe.result) && validPlace(px, py, placeRecipe.result, prot)){
                        PlaceRequest request = new PlaceRequest(px * tilesize + placeRecipe.result.offset(), py * tilesize + placeRecipe.result.offset(), placeRecipe, prot);
                        request.scale = 1f;
                        selection.add(request);
                    }
                }

                //reset last placed for convenience
                lastPlaced = null;

            }else if(mode == breaking){
                //normalize area
                NormalizeResult result = PlaceUtils.normalizeArea(lineStartX, lineStartY, tileX, tileY, rotation, false, maxLength);

                //break everything in area
                for(int x = 0; x <= Math.abs(result.x2 - result.x); x++){
                    for(int y = 0; y <= Math.abs(result.y2 - result.y); y++){
                        int wx = lineStartX + x * Mathf.sign(tileX - lineStartX);
                        int wy = lineStartY + y * Mathf.sign(tileY - lineStartY);

                        Tile tar = world.tile(wx, wy);

                        if(tar == null) continue;

                        tar = tar.target();

                        if(!hasRequest(world.tile(tar.x, tar.y)) && validBreak(tar.x, tar.y)){
                            PlaceRequest request = new PlaceRequest(tar.worldx(), tar.worldy());
                            request.scale = 1f;
                            selection.add(request);
                        }
                    }
                }
            }else if(mode == copying){
                schematic = schematics.create(lineStartX, lineStartY, tileX, tileY);
                recipe = null;
                mode = PlaceMode.schematic;
                if(schematic != null){
                    schemX = (lineStartX + (tileX - lineStartX + 1)/2f) * tilesize;
                    schemY = (lineStartY + (tileY - lineStartY + 1)/2f) * tilesize;
                }
            }

            lineMode = false;
        }else{
            Tile tile = tileAt(screenX, screenY);

            if(tile != null){
                tryDropItems(tile.target(), Graphics.world(screenX, screenY).x, Graphics.world(screenX, screenY).y);
            }
        }
        draggingSchematic = false;
        return false;
    }

    @Override
    public boolean longPress(float x, float y){
        if(state.is(State.menu) || mode == none || player.isDead()) return false;

        //get tile on cursor
        Tile cursor = tileAt(x, y);

        //ignore off-screen taps
        if(cursor == null || ui.hasMouse(x, y)) return false;

        //remove request if it's there
        //long pressing enables line mode otherwise
        lineStartX = cursor.x;
        lineStartY = cursor.y;
        lineMode = true;

        if(mode == breaking){
            Effects.effect(Fx.tapBlock, cursor.worldx(), cursor.worldy(), 1f);
        }else if(recipe != null){
            Effects.effect(Fx.tapBlock, cursor.worldx() + recipe.result.offset(), cursor.worldy() + recipe.result.offset(), recipe.result.size);
        }

        return false;
    }

    @Override
    public boolean tap(float x, float y, int count, int button){
        if(state.is(State.menu) || lineMode) return false;

        float worldx = Graphics.world(x, y).x, worldy = Graphics.world(x, y).y;

        //get tile on cursor
        Tile cursor = tileAt(x, y);

        //ignore off-screen taps
        if(cursor == null || ui.hasMouse(x, y)) return false;

        checkTargets(worldx, worldy);

        //remove if request present
        if(hasRequest(cursor)){
            removeRequest(getRequest(cursor));
        }else if(mode == placing && isPlacing() && validPlace(cursor.x, cursor.y, recipe.result, rotation) && !checkOverlapPlacement(cursor.x, cursor.y, recipe.result)){
            //add to selection queue if it's a valid place position
            selection.add(lastPlaced = new PlaceRequest(cursor.worldx() + recipe.result.offset(), cursor.worldy() + recipe.result.offset(), recipe, rotation));
        }else if(mode == breaking && validBreak(cursor.target().x, cursor.target().y) && !hasRequest(cursor.target())){
            //add to selection queue if it's a valid BREAK position
            cursor = cursor.target();
            selection.add(new PlaceRequest(cursor.worldx(), cursor.worldy()));
        }else if(!canTapPlayer(worldx, worldy)){
            boolean consumed = false;
            //else, try and carry units
            if(player.mech.flying){
                if(player.getCarry() != null){
                    consumed = true;
                    player.dropCarry(); //drop off unit
                }else{
                    Unit unit = Units.getClosest(player.getTeam(), Graphics.world(x, y).x, Graphics.world(x, y).y, 4f, u -> !u.isFlying() && u.getMass() <= player.mech.carryWeight);

                    if(unit != null){
                        consumed = true;
                        player.moveTarget = unit;
                        Effects.effect(Fx.select, unit.getX(), unit.getY());
                    }
                }
            }

            if(!consumed && !tileTapped(cursor.target())){
                tryBeginMine(cursor);
            }
        }

        return false;
    }

    @Override
    public void update(){
        //mobile pans by dragging, so the camera is never 'detached' from following the player;
        //this also clears the flag if the input handler was swapped out for a desktop one
        renderer.detached = false;

        if(state.is(State.menu) || player.isDead()){
            selection.clear();
            removals.clear();
            mode = none;
        }

        if(mode == PlaceMode.schematic && schematic != null && schemX == -1){
            schemX = Core.camera.position.x;
            schemY = Core.camera.position.y;
        }

        if(mode != PlaceMode.schematic && mode != copying){
            schemX = -1;
            schemY = -1;
        }

        //reset state when not placing
        if(mode == none){
            selecting = false;
            lineMode = false;
            removals.addAll(selection);
            selection.clear();
        }

        if(lineMode && mode == placing && recipe == null){
            lineMode = false;
        }

        //if there is no mode and there's a recipe, switch to placing
        if(recipe != null && mode == none){
            mode = placing;
        }

        if(recipe != null){
            showGuide("construction");
        }

        //automatically switch to placing after a new recipe is selected
        if(lastRecipe != recipe && (mode == breaking || mode == copying) && recipe != null){
            mode = placing;
            lastRecipe = recipe;
        }

        if(lineMode){
            lineScale = Mathf.lerpDelta(lineScale, 1f, 0.1f);

            //When in line mode, pan when near screen edges automatically
            if(Gdx.input.isTouched(0) && lineMode){
                float screenX = Graphics.mouse().x, screenY = Graphics.mouse().y;

                float panX = 0, panY = 0;

                if(screenX <= edgePan){
                    panX = -(edgePan - screenX);
                }

                if(screenX >= Gdx.graphics.getWidth() - edgePan){
                    panX = (screenX - Gdx.graphics.getWidth()) + edgePan;
                }

                if(screenY <= edgePan){
                    panY = -(edgePan - screenY);
                }

                if(screenY >= Gdx.graphics.getHeight() - edgePan){
                    panY = (screenY - Gdx.graphics.getHeight()) + edgePan;
                }

                vector.set(panX, panY).scl((Core.camera.viewportWidth) / Gdx.graphics.getWidth());
                vector.limit(maxPanSpeed);

                //pan view
                Core.camera.position.x += vector.x;
                Core.camera.position.y += vector.y;
            }
        }else{
            lineScale = 0f;
        }

        //remove place requests that have disappeared
        for(int i = removals.size - 1; i >= 0; i--){
            PlaceRequest request = removals.get(i);

            if(request.scale <= 0.0001f){
                removals.removeIndex(i);
                i--;
            }
        }
    }

    @Override
    public boolean pan(float x, float y, float deltaX, float deltaY){
        if(!canPan) return false;

        //can't pan in line mode with one finger or while dropping items!
        if((lineMode && !Gdx.input.isTouched(1)) || droppingItem){
            return false;
        }

        //screen pixels become world units by dividing by the display scale, which includes the
        //render scale setting and any in-progress zoom animation
        float displayScale = renderer.getDisplayScale();
        float dx = deltaX / displayScale, dy = deltaY / displayScale;

        if(draggingSchematic){
            schemX += dx;
            schemY -= dy;
            return true;
        }

        if(selecting){ //pan all requests
            for(PlaceRequest req : selection){
                if(req.remove) continue; //don't shift removal requests
                req.x += dx;
                req.y -= dy;
            }
        }else{
            //pan player
            Core.camera.position.x -= dx;
            Core.camera.position.y += dy;
        }

        return false;
    }

    @Override
    public boolean panStop(float x, float y, int pointer, int button){
        return false;
    }

    @Override
    public boolean pinch(Vector2 initialPointer1, Vector2 initialPointer2, Vector2 pointer1, Vector2 pointer2){
        return false;
    }

    @Override
    public boolean zoom(float initialDistance, float distance){
        if(initialDistance <= 0f) return false;

        if(!zoomed){
            //first event of this gesture: remember the scale it started from
            pinchStartScale = renderer.getScale();
            zoomed = true;
        }

        //proportional zoom: the scale is set directly from how far apart the fingers are, so the
        //view tracks the pinch exactly instead of jumping through whole zoom levels
        renderer.setScale(pinchStartScale * distance / initialDistance);
        return true;
    }

    @Override
    public void pinchStop(){
        zoomed = false;
    }

    @Override
    public boolean touchDown(float x, float y, int pointer, int button){
        canPan = !ui.hasMouse();
        return false;
    }

    @Override
    public boolean fling(float velocityX, float velocityY, int button){
        return false;
    }

    //endregion

    class PlaceRequest{
        float x, y;
        Recipe recipe;
        int rotation;
        boolean remove;

        //animation variables
        float scale;
        float redness;

        PlaceRequest(float x, float y, Recipe recipe, int rotation){
            this.x = x;
            this.y = y;
            this.recipe = recipe;
            this.rotation = rotation;
            this.remove = false;
        }

        PlaceRequest(float x, float y){
            this.x = x;
            this.y = y;
            this.remove = true;
        }

        Tile tile(){
            return world.tileWorld(x - (recipe == null ? 0 : recipe.result.offset()), y - (recipe == null ? 0 : recipe.result.offset()));
        }
    }
}