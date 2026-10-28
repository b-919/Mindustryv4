package io.anuke.mindustry.entities.traits;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Queue;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.game.EventType.BuildSelectEvent;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.world.Build;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BuildBlock;
import io.anuke.mindustry.world.blocks.BuildBlock.BuildEntity;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.trait.Entity;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.graphics.Shapes;
import io.anuke.ucore.util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

import static io.anuke.mindustry.Vars.*;

/**
 * Interface for units that build, break or mine things.
 */
public interface BuilderTrait extends Entity{
    //these are not instance variables!
    /** Maximum distance at which a plan can actually be constructed. */
    float buildRange = 220f;
    float mineDistance = 70f;

    /**Returns the queue for storing build requests.*/
    Queue<BuildRequest> getPlaceQueue();

    /**Returns the tile this builder is currently mining.*/
    Tile getMineTile();

    /**Sets the tile this builder is currently mining.*/
    void setMineTile(Tile tile);

    /**Returns the minining speed of this miner. 1 = standard, 0.5 = half speed, 2 = double speed, etc.*/
    float getMinePower();

    /**Build power, can be any float. 1 = builds recipes in normal time, 0 = doesn't build at all.*/
    float getBuildPower(Tile tile);

    /**Returns whether or not this builder can mine a specific item type.*/
    boolean canMine(Item item);

    /**Whether this type of builder can begin creating new blocks.*/
    default boolean canCreateBlocks(){
        return true;
    }

    default void writeBuilding(DataOutput output) throws IOException{
        BuildRequest request = getCurrentRequest();

        if(request != null){
            output.writeByte(request.breaking ? 1 : 0);
            output.writeLong(world.toPacked(request.x, request.y));
            output.writeFloat(request.progress);
            if(!request.breaking){
                output.writeByte(request.recipe.id);
                output.writeByte(request.rotation);
            }
        }else{
            output.writeByte(-1);
        }
    }

    default void readBuilding(DataInput input) throws IOException{
        readBuilding(input, true);
    }

    default void readBuilding(DataInput input, boolean applyChanges) throws IOException{
        if(applyChanges) getPlaceQueue().clear();

        byte type = input.readByte();
        if(type != -1){
            long position = input.readLong();
            float progress = input.readFloat();
            BuildRequest request;

            if(type == 1){ //remove
                int px = (int)(position >> 32);
                int py = (int)(position & 0xFFFFFFFFL);
                request = new BuildRequest(px, py);
            }else{ //place
                byte recipe = input.readByte();
                byte rotation = input.readByte();
                int px = (int)(position >> 32);
                int py = (int)(position & 0xFFFFFFFFL);
                request = new BuildRequest(px, py, rotation, content.recipe(recipe & 0xFF));
            }

            request.progress = progress;

            if(applyChanges){
                getPlaceQueue().addLast(request);
            }else if(isBuilding()){
                getCurrentRequest().progress = progress;
            }
        }
    }

    /**Return whether this builder's place queue contains items.*/
    default boolean isBuilding(){
        return getPlaceQueue().size != 0;
    }

    /**
     * If a place request matching this position is present, it is replaced by the new request.
     * Otherwise, a new place request is added to the queue.
     */
    default void replaceBuilding(int x, int y, int rotation, Recipe recipe){
        addBuildRequest(new BuildRequest(x, y, rotation, recipe));
    }

    /**Clears the placement queue.*/
    default void clearBuilding(){
        getPlaceQueue().clear();
    }

    /**
     * Returns the queued request overlapping this tile, or null.
     * Multiblock plans are matched against their whole footprint, like modern Mindustry plans.
     */
    default BuildRequest getBuildRequest(int x, int y){
        for(BuildRequest request : getPlaceQueue()){
            Tile tile = world.tile(request.x, request.y);
            if(tile == null || (!request.breaking && request.recipe == null)) continue;

            int size = request.breaking ? tile.block().size : request.recipe.result.size;
            float offset = request.breaking ? tile.block().offset() : request.recipe.result.offset();
            float half = size * tilesize / 2f;
            float cx = request.x * tilesize + offset, cy = request.y * tilesize + offset;

            //cursor tile is treated as a 1x1 block centered on the tile origin, matching existing UI code
            if(Math.abs(cx - x * tilesize) < half + tilesize / 2f
                    && Math.abs(cy - y * tilesize) < half + tilesize / 2f){
                return request;
            }
        }
        return null;
    }

    /** Removes a queued request if it matches this position and type. */
    default boolean removeBuildRequest(int x, int y, boolean breaking){
        BuildRequest request = getBuildRequest(x, y);
        if(request != null && request.breaking == breaking){
            getPlaceQueue().removeValue(request, true);
            return true;
        }
        return false;
    }

    /** Whether two queued requests overlap in the world. */
    default boolean buildRequestOverlaps(BuildRequest a, BuildRequest b){
        Tile at = world.tile(a.x, a.y);
        Tile bt = world.tile(b.x, b.y);
        if(at == null || bt == null || (!a.breaking && a.recipe == null) || (!b.breaking && b.recipe == null)) return false;

        int asize = a.breaking ? at.block().size : a.recipe.result.size;
        float aoffset = a.breaking ? at.block().offset() : a.recipe.result.offset();
        int bsize = b.breaking ? bt.block().size : b.recipe.result.size;
        float boffset = b.breaking ? bt.block().offset() : b.recipe.result.offset();

        float ah = asize * tilesize / 2f, bh = bsize * tilesize / 2f;
        float ax = a.x * tilesize + aoffset, ay = a.y * tilesize + aoffset;
        float bx = b.x * tilesize + boffset, by = b.y * tilesize + boffset;

        return Math.abs(ax - bx) < ah + bh && Math.abs(ay - by) < ah + bh;
    }

    /**
     * Adds a build request to the tail of the queue, replacing any existing request that overlaps it.
     * This mirrors modern Mindustry's addBuild(), which lets players override plans (including rotation
     * and block type) instead of silently ignoring the click.
     */
    default void addBuildRequest(BuildRequest place){
        Tile tile = world.tile(place.x, place.y);
        if(tile != null && tile.entity instanceof BuildEntity){
            BuildEntity existing = tile.entity();
            //only preserve progress when this is the same construction
            if(!place.breaking && existing.recipe != null && place.recipe != null
                    && existing.recipe.result == place.recipe.result && tile.getRotation() == place.rotation){
                place.progress = existing.progress;
            }
        }

        for(int i = getPlaceQueue().size - 1; i >= 0; i--){
            if(buildRequestOverlaps(place, getPlaceQueue().get(i))){
                getPlaceQueue().removeIndex(i);
            }
        }

        getPlaceQueue().addLast(place);
    }

    /**
     * Return the build requests currently active, or the one at the top of the queue.
     * May return null.
     */
    default BuildRequest getCurrentRequest(){
        return getPlaceQueue().size == 0 ? null : getPlaceQueue().first();
    }

    /**
     * Update building mechanism for this unit.
     * This includes mining.
     */
    default void updateBuilding(Unit unit){
        BuildRequest current = getCurrentRequest();

        //update mining here
        if(current == null){
            if(getMineTile() != null){
                updateMining(unit);
            }
            return;
        }else{
            setMineTile(null);
        }

        //drop invalid requests (e.g. a recipe that no longer exists)
        if(!current.breaking && current.recipe == null){
            getPlaceQueue().removeFirst();
            return;
        }

        Tile tile = world.tile(current.x, current.y);
        if(tile == null){
            getPlaceQueue().removeFirst();
            return;
        }

        //if the current plan can't be reached, cycle the queue so a reachable plan is picked
        //instead of letting the first queued plan block the whole queue (modern builder behavior).
        //only one step is taken per update so long queues stay O(1) per frame.
        if(unit.distanceTo(tile) > buildRange && getPlaceQueue().size > 1){
            getPlaceQueue().addLast(getPlaceQueue().removeFirst());

            current = getCurrentRequest();
            tile = world.tile(current.x, current.y);

            if(!current.breaking && current.recipe == null){
                getPlaceQueue().removeFirst();
                return;
            }

            if(tile == null || unit.distanceTo(tile) > buildRange){
                return;
            }
        }

        if(unit.distanceTo(tile) > buildRange){
            return;
        }

        if(!(tile.block() instanceof BuildBlock)){
            if(canCreateBlocks() && !current.breaking && Build.validPlace(unit.getTeam(), current.x, current.y, current.recipe.result, current.rotation)){
                Build.beginPlace(unit.getTeam(), current.x, current.y, current.recipe, current.rotation);
            }else if(canCreateBlocks() && current.breaking && Build.validBreak(unit.getTeam(), current.x, current.y)){
                Build.beginBreak(unit.getTeam(), current.x, current.y);
            }else{
                getPlaceQueue().removeFirst();
                return;
            }
        }else if(canCreateBlocks() && !current.breaking){
            //a construction is already in progress here; override it if this plan is different
            BuildEntity existing = tile.entity();

            if(existing != null && existing.deconstructing){
                //do not override a deconstruction site with a placement plan
                getPlaceQueue().removeFirst();
                return;
            }

            if(existing != null && (existing.recipe == null || existing.recipe.result != current.recipe.result || tile.getRotation() != current.rotation)){
                Build.beginPlace(unit.getTeam(), current.x, current.y, current.recipe, current.rotation);

                BuildEntity updated = tile.entity();
                if(tile.block() instanceof BuildBlock && (updated == null || updated.recipe == null
                        || updated.recipe.result != current.recipe.result || tile.getRotation() != current.rotation)){
                    //the override could not be applied to the in-progress construction; drop it rather than
                    //finishing the wrong block
                    getPlaceQueue().removeFirst();
                    return;
                }
            }
        }

        TileEntity core = unit.getClosestCore();

        //if there is no core to build with, stop building!
        if(core == null){
            return;
        }

        //otherwise, update it.
        BuildEntity entity = tile.entity();

        if(entity == null){
            getPlaceQueue().removeFirst();
            return;
        }

        if(unit.distanceTo(tile) <= buildRange){
            unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(entity), 0.4f);
        }

        //progress is synced, thus not updated clientside
        if(!Net.client()){
            //deconstructing is 2x as fast
            if(current.breaking){
                entity.deconstruct(unit, core, 2f / entity.buildCost * Timers.delta() * getBuildPower(tile));
            }else{
                entity.construct(unit, core, 1f / entity.buildCost * Timers.delta() * getBuildPower(tile));
            }

            current.progress = entity.progress();
        }else{
            entity.progress = current.progress;
        }

        if(!current.initialized){
            final BuildRequest request = current;
            final Tile target = tile;
            Gdx.app.postRunnable(() -> Events.fire(new BuildSelectEvent(target, unit.getTeam(), this, request.breaking)));
            current.initialized = true;
        }
    }

    /**Do not call directly.*/
    default void updateMining(Unit unit){
        Tile tile = getMineTile();
        TileEntity core = unit.getClosestCore();

        if(core == null || tile.block() != Blocks.air || unit.distanceTo(tile.worldx(), tile.worldy()) > mineDistance
                || tile.floor().drops == null || !unit.inventory.canAcceptItem(tile.floor().drops.item) || !canMine(tile.floor().drops.item)){
            setMineTile(null);
        }else{
            Item item = tile.floor().drops.item;
            unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(tile.worldx(), tile.worldy()), 0.4f);

            if(Mathf.chance(Timers.delta() * (0.06 - item.hardness * 0.01) * getMinePower())){

                if(unit.distanceTo(core) < mineTransferRange && core.tile.block().acceptStack(item, 1, core.tile, unit) == 1){
                    Call.transferItemTo(item, 1,
                        tile.worldx() + Mathf.range(tilesize / 2f),
                        tile.worldy() + Mathf.range(tilesize / 2f), core.tile);
                }else if(unit.inventory.canAcceptItem(item)){
                    Call.transferItemToUnit(item,
                        tile.worldx() + Mathf.range(tilesize / 2f),
                        tile.worldy() + Mathf.range(tilesize / 2f),
                        unit);
                }
            }

            if(Mathf.chance(0.06 * Timers.delta())){
                Effects.effect(BlockFx.pulverizeSmall,
                        tile.worldx() + Mathf.range(tilesize / 2f),
                        tile.worldy() + Mathf.range(tilesize / 2f), 0f, item.color);
            }
        }
    }

    /**Draw placement effects for an entity. This includes mining*/
    default void drawBuilding(Unit unit){
        BuildRequest request;
        if(!isBuilding()){
            if(getMineTile() != null){
                drawMining(unit);
            }
            return;
        }

        request = getCurrentRequest();

        Tile tile = world.tile(request.x, request.y);

        if(unit.distanceTo(tile) > buildRange){
            return;
        }

        Draw.color(Palette.accent);
        float focusLen = 3.8f + Mathf.absin(Timers.time(), 1.1f, 0.6f);
        float px = unit.x + Angles.trnsx(unit.rotation, focusLen);
        float py = unit.y + Angles.trnsy(unit.rotation, focusLen);

        float sz = Vars.tilesize * tile.block().size / 2f;
        float ang = unit.angleTo(tile);

        tmptr[0].set(tile.drawx() - sz, tile.drawy() - sz);
        tmptr[1].set(tile.drawx() + sz, tile.drawy() - sz);
        tmptr[2].set(tile.drawx() - sz, tile.drawy() + sz);
        tmptr[3].set(tile.drawx() + sz, tile.drawy() + sz);

        Arrays.sort(tmptr, (a, b) -> -Float.compare(Angles.angleDist(Angles.angle(unit.x, unit.y, a.x, a.y), ang),
                Angles.angleDist(Angles.angle(unit.x, unit.y, b.x, b.y), ang)));

        float x1 = tmptr[0].x, y1 = tmptr[0].y,
                x3 = tmptr[1].x, y3 = tmptr[1].y;

        Draw.alpha(1f);

        Lines.line(px, py, x1, y1);
        Lines.line(px, py, x3, y3);

        Fill.circle(px, py, 1.6f + Mathf.absin(Timers.time(), 0.8f, 1.5f));

        Draw.color();
    }

    /**Internal use only.*/
    default void drawMining(Unit unit){
        Tile tile = getMineTile();

        if(tile == null) return;

        float focusLen = 4f + Mathf.absin(Timers.time(), 1.1f, 0.5f);
        float swingScl = 12f, swingMag = tilesize / 8f;
        float flashScl = 0.3f;

        float px = unit.x + Angles.trnsx(unit.rotation, focusLen);
        float py = unit.y + Angles.trnsy(unit.rotation, focusLen);

        float ex = tile.worldx() + Mathf.sin(Timers.time() + 48, swingScl, swingMag);
        float ey = tile.worldy() + Mathf.sin(Timers.time() + 48, swingScl + 2f, swingMag);

        Draw.color(Color.LIGHT_GRAY, Color.WHITE, 1f - flashScl + Mathf.absin(Timers.time(), 0.5f, flashScl));
        Shapes.laser("minelaser", "minelaser-end", px, py, ex, ey);

        if(unit instanceof Player && ((Player) unit).isLocal){
            Draw.color(Palette.accent);
            Lines.poly(tile.worldx(), tile.worldy(), 4, tilesize / 2f * Mathf.sqrt2, Timers.time());
        }

        Draw.color();
    }

    /**Class for storing build requests. Can be either a place or remove request.*/
    class BuildRequest{
        public final int x, y;
        public int rotation;
        public final Recipe recipe;
        public final boolean breaking;

        public float progress;
        public boolean initialized;

        /**This creates a build request.*/
        public BuildRequest(int x, int y, int rotation, Recipe recipe){
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.recipe = recipe;
            this.breaking = false;
        }

        /**This creates a remove request.*/
        public BuildRequest(int x, int y){
            this.x = x;
            this.y = y;
            this.rotation = -1;
            this.recipe = Recipe.getByResult(world.tile(x, y).block());
            this.breaking = true;
        }
    }
}
