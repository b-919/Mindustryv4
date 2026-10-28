package io.anuke.mindustry.entities.units.types;

import com.badlogic.gdx.utils.Queue;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.entities.units.UnitCommand;
import io.anuke.mindustry.entities.units.UnitState;
import io.anuke.mindustry.game.EventType.BuildSelectEvent;
import io.anuke.mindustry.game.Teams.BlockPlan;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.ItemType;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.world.Build;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BuildBlock;
import io.anuke.mindustry.world.blocks.BuildBlock.BuildEntity;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Events;
import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Structs;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.unitGroups;
import static io.anuke.mindustry.Vars.world;

public class Drone extends FlyingUnit implements BuilderTrait{
    protected static int timerRepairEffect = timerIndex++;
    protected static int timerRebuildCheck = timerIndex++;

    @Override
    public boolean canShootWeapons(){
        //don't shoot while mining, building or rebuilding you baka!
        return !(state.is(mine) || state.is(build) || state.is(rebuild));
    }

    protected Item targetItem;
    protected Tile mineTile;
    protected Queue<BuildRequest> placeQueue = new Queue<>();
    protected boolean isBreaking;
    protected boolean followPlayerMode = false;
    protected int followPlayerID = -1;
    protected UnitState previousState;
    protected UnitState rebuildReturnState;
    protected boolean rebuilding;

    public UnitState

    build = new UnitState(){

        public void entered(){
            if(!(target instanceof BuildEntity)){
                target = null;
            }
        }

        public void update(){
            BuildEntity entity = (BuildEntity) target;
            TileEntity core = getClosestCore();

            if(entity == null){
                checkRetreat();
                if(rebuilding){
                    restoreAfterRebuild();
                }else{
                    setState(repair);
                }
                return;
            }

            if(core == null) return;

            if((entity.progress() < 1f || entity.progress() > 0f) && entity.tile.block() instanceof BuildBlock){ //building is valid
                if(!isBuilding() && distanceTo(target) < buildRange * 0.9f){ //within distance, begin placing
                    checkRetreat();
                    if(isBreaking){
                        addBuildRequest(new BuildRequest(entity.tile.x, entity.tile.y));
                    }else{
                        addBuildRequest(new BuildRequest(entity.tile.x, entity.tile.y, entity.tile.getRotation(), entity.recipe));
                    }
                }

                //if it's missing requirements, try and mine them
                if(entity.recipe != null){
                    for(ItemStack stack : entity.recipe.requirements){
                        if(!core.items.has(stack.item, stack.amount) && type.toMine.contains(stack.item)){
                            targetItem = stack.item;
                            getPlaceQueue().clear();
                            checkRetreat();
                            setState(mine);
                            return;
                        }
                    }
                }

                circle(buildRange * 0.7f);
            }else{ //building isn't valid
                checkRetreat();
                if(rebuilding){
                    restoreAfterRebuild();
                }else{
                    setState(repair);
                }
            }
        }
    },

    repair = new UnitState(){

        public void entered(){
            target = null;
        }

        public void update(){

            retarget(() -> {
                target = Units.findDamagedTile(team, x, y);

                if(target == null){
                    if(followPlayerMode || !startRebuild()){
                        checkRetreat();
                        setState(mine);
                    }
                }
            });

            //state may have changed to rebuild inside retarget
            if(state.is(rebuild)) return;

            if(target == null) return;

            if(target.distanceTo(Drone.this) > type.range){
                circle(type.range*0.9f);
            }else{
                getWeapon().update(Drone.this, target.getX(), target.getY());
            }
        }
    },

    rebuild = new UnitState(){

        public void entered(){
        }

        public void update(){
            BuildRequest request = getCurrentRequest();

            if(request == null){
                restoreAfterRebuild();
                return;
            }

            Tile tile = world.tile(request.x, request.y);

            if(tile == null){
                getPlaceQueue().clear();
                restoreAfterRebuild();
                return;
            }

            target = tile;
            circle(buildRange * 0.7f);

            //construction has started; switch to the normal build assistance state
            if(tile.entity instanceof BuildEntity){
                target = tile.entity;
                setState(build);
            }
        }
    },

    mine = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            TileEntity entity = getClosestCore();

            if(entity == null) return;

            if(targetItem == null){
                findItem();
            }

            //core full
            if(targetItem != null && entity.tile.block().acceptStack(targetItem, 1, entity.tile, Drone.this) == 0){
                checkRetreat();
                setState(repair);
                return;
            }

            //if inventory is full, drop it off.
            if(inventory.isFull()){
                checkRetreat();
                setState(drop);
            }else{
                if(targetItem != null && !inventory.canAcceptItem(targetItem)){
                    checkRetreat();
                    setState(drop);
                    return;
                }

                retarget(() -> {
                    if(getMineTile() == null){
                        findItem();
                    }

                    if(targetItem == null) return;

                    target = world.indexer.findClosestOre(x, y, targetItem);
                });

                if(target instanceof Tile){
                    moveTo(type.range / 1.5f);

                    if(distanceTo(target) < type.range && mineTile != target){
                        setMineTile((Tile) target);
                    }

                    if(((Tile) target).block() != Blocks.air){
                        checkRetreat();
                        setState(drop);
                    }
                }
            }
        }

        public void exited(){
            setMineTile(null);
        }
    },
    drop = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(inventory.isEmpty()){
                checkRetreat();
                setState(mine);
                return;
            }

            if(inventory.getItem().item.type != ItemType.material){
                inventory.clearItem();
                checkRetreat();
                setState(mine);
                return;
            }

            target = getClosestCore();

            if(target == null) return;

            TileEntity tile = (TileEntity) target;

            if(distanceTo(target) < type.range){
                if(tile.tile.block().acceptStack(inventory.getItem().item, inventory.getItem().amount, tile.tile, Drone.this) == inventory.getItem().amount){
                    Call.transferItemTo(inventory.getItem().item, inventory.getItem().amount, x, y, tile.tile);
                    inventory.clearItem();
                }

                checkRetreat();
                setState(repair);
            }

            circle(type.range / 1.8f);
        }
    },
    retreat = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health >= maxHealth()){
                setState(previousState == null ? repair : previousState);
            }else if(!targetHasFlag(BlockFlag.repair)){
                if(retarget()){
                    targetClosestAllyFlag(BlockFlag.repair);
                    if(target == null){
                        target = Units.getClosest(team, x, y, getType().healRange, u -> u.isHealer() && u != Drone.this);
                    }
                }
            }else{
                circle(40f);
            }
        }
    };

    static{

        Events.on(BuildSelectEvent.class, event -> {
            EntityGroup<BaseUnit> group = unitGroups[event.team.ordinal()];

            if(!(event.builder instanceof Player) || !(event.tile.entity instanceof BuildEntity)) return;
            BuildEntity entity = event.tile.entity();

            for(BaseUnit unit : group.all()){
                if(unit instanceof Drone){
                    Drone drone = (Drone)unit;
                    if(drone.isBuilding()){
                        //stop building if opposite building begins.
                        BuildRequest req = drone.getCurrentRequest();
                        if(req.breaking != event.breaking && req.x == event.tile.x && req.y == event.tile.y){
                            drone.clearBuilding();
                            drone.setState(drone.repair);
                        }
                    }

                    drone.notifyPlaced(entity, event.breaking);
                }
            }
        });
    }

    /** Picks the closest valid destroyed-block plan and starts rebuilding it. */
    protected boolean startRebuild(){
        BlockPlan best = null;
        float bestDst = Float.MAX_VALUE;
        BlockPlan needItems = null;
        float needDst = Float.MAX_VALUE;
        TileEntity core = getClosestCore();

        for(BlockPlan plan : Vars.state.teams.get(team).plans){
            if(!canRebuild(plan)) continue;

            float dst = Mathf.dst(plan.x * Vars.tilesize - x, plan.y * Vars.tilesize - y);

            if(hasRequiredItems(plan, core)){
                if(dst < bestDst){
                    bestDst = dst;
                    best = plan;
                }
            }else if(dst < needDst){
                needDst = dst;
                needItems = plan;
            }
        }

        //no affordable rebuild available; gather the missing resources for the closest one
        if(best == null){
            if(needItems != null){
                mineForPlan(needItems, core);
            }
            return false;
        }

        Tile tile = world.tile(best.x, best.y);
        Recipe recipe = Recipe.getByResult(best.block);

        if(tile == null || recipe == null) return false;

        if(best.config != null){
            Vars.schematics.queueConfig(best.x, best.y, best.block, best.config);
        }

        //remember where to resume once the rebuild is done
        if(!state.is(rebuild) && !rebuilding){
            rebuildReturnState = state.get();
            rebuilding = true;
        }

        addBuildRequest(new BuildRequest(best.x, best.y, best.rotation, recipe));
        target = tile;
        setState(rebuild);
        return true;
    }

    /** Whether the core has at least one of every item required by this plan. */
    protected boolean hasRequiredItems(BlockPlan plan, TileEntity core){
        Recipe recipe = Recipe.getByResult(plan.block);
        if(recipe == null || core == null) return false;

        for(ItemStack stack : recipe.requirements){
            if(!core.items.has(stack.item, 1)){
                return false;
            }
        }

        return true;
    }

    /** Switches to mining if a rebuild plan is missing resources this drone can gather. */
    protected void mineForPlan(BlockPlan plan, TileEntity core){
        Recipe recipe = Recipe.getByResult(plan.block);
        if(recipe == null || core == null) return;

        for(ItemStack stack : recipe.requirements){
            if(!core.items.has(stack.item, 1) && type.toMine.contains(stack.item)){
                targetItem = stack.item;
                getPlaceQueue().clear();

                if(!state.is(mine)){
                    setState(mine);
                }
                return;
            }
        }
    }

    /** Returns to the state the drone was in before it started rebuilding. */
    protected void restoreAfterRebuild(){
        UnitState next = rebuildReturnState == null ? repair : rebuildReturnState;
        rebuildReturnState = null;
        rebuilding = false;
        setState(next);
    }

    /** Whether this destroyed block can currently be rebuilt. */
    protected boolean canRebuild(BlockPlan plan){
        Tile tile = world.tile(plan.x, plan.y);
        Recipe recipe = Recipe.getByResult(plan.block);

        if(tile == null || recipe == null || !recipe.available()) return false;

        if(tile.block() == Blocks.air){
            return Build.validPlace(team, plan.x, plan.y, plan.block, plan.rotation);
        }

        if(tile.entity instanceof BuildEntity){
            BuildEntity entity = tile.entity();
            return !entity.deconstructing && entity.recipe != null && entity.recipe.result == plan.block;
        }

        return false;
    }

    public void notifyPlaced(BuildEntity entity, boolean isBreaking){
        float dist = Math.min(entity.distanceTo(x, y) - buildRange, 0);

        if(!state.is(build) && !state.is(rebuild) && dist / type.maxVelocity < entity.buildCost * 0.9f){
            target = entity;
            this.isBreaking = isBreaking;
            setState(build);
        }
    }

    @Override
    public void onCommand(UnitCommand command){
        //no
    }

    @Override
    public boolean canMine(Item item){
        return type.toMine.contains(item);
    }

    @Override
    public float getBuildPower(Tile tile){
        return type.buildPower;
    }

    @Override
    public float getMinePower(){
        return type.minePower;
    }

    @Override
    public Queue<BuildRequest> getPlaceQueue(){
        return placeQueue;
    }

    @Override
    public Tile getMineTile(){
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile){
        mineTile = tile;
    }

    @Override
    public void update(){
        super.update();

        if(followPlayerMode){
            Player p = Vars.playerGroup.getByID(followPlayerID);
            if(p != null && !p.isDead()){
                target = p;
                moveTo(55f);

                //assist combat and repairs only; disable mining/drop behavior in follow mode
                if(retarget()){
                    TileEntity damaged = Units.findDamagedTile(team, x, y);
                    if(damaged != null && distanceTo(damaged) < type.range * 1.3f){
                        this.target = damaged;
                    }else{
                        targetClosest();
                    }
                }

                if(this.target != null && this.target != p && distanceTo(this.target) < type.range){
                    getWeapon().update(this, this.target.getX(), this.target.getY());
                }
            }
        }

        if(state.is(repair) && target != null && target.getTeam() != team){
            target = null;
        }

        updateBuilding(this);
    }

    @Override
    public void updateDefaultAI(){
        if(!updateOrder()){
            //repairing/mining drones may pause to rebuild destroyed blocks.
            //building and retreat are never interrupted.
            if(!followPlayerMode && (state.is(repair) || state.is(mine)) && timer.get(timerRebuildCheck, 20)){
                startRebuild();
            }

            state.update();
        }
    }

    @Override
    protected void updateRotation(){
        if(target != null && ((state.is(repair) && target.distanceTo(this) < type.range)
                || state.is(mine) || (state.is(rebuild) && target.distanceTo(this) < buildRange))){
            rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.3f);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), 0.3f);
        }
    }

    @Override
    public void behavior(){
        if(health < maxHealth() * type.retreatPercent){
            boolean hasRepairPoint = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair)) != null ||
                    Units.getClosest(team, x, y, 400f, u -> u.isHealer() && u != this) != null;

            if(hasRepairPoint){
                if(!state.is(retreat)){
                    previousState = state.get();
                    setState(retreat);
                }
            }
        }
    }

    protected void checkRetreat(){
        if(health < maxHealth() * type.retreatPercent){
            boolean hasRepairPoint = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair)) != null ||
                    Units.getClosest(team, x, y, 400f, u -> u.isHealer() && u != this) != null;
            if(hasRepairPoint){
                previousState = state.get();
                setState(retreat);
            }
        }
    }

    @Override
    public UnitState getStartState(){
        return repair;
    }

    @Override
    public void draw(){
        Draw.alpha(hitTime / hitDuration);

        Draw.rect(type.name, x, y, rotation - 90);

        drawItems();

        Draw.alpha(1f);
    }

    @Override
    public void drawEngine(){
        if(type != null && type.drawEngine){
            trail.draw(Palette.lightTrail, 3f);
        }
    }

    @Override
    public void drawOver(){
        drawEngine();
        drawBuilding(this);
    }

    @Override
    public float drawSize(){
        return isBuilding() ? buildRange * 2f : 30f;
    }

    protected void findItem(){
        TileEntity entity = getClosestCore();
        if(entity == null){
            return;
        }
        targetItem = Structs.findMin(type.toMine, (a, b) -> -Integer.compare(entity.items.get(a), entity.items.get(b)));
    }

    public void setFollowPlayer(boolean follow, int playerID){
        this.followPlayerMode = follow;
        this.followPlayerID = follow ? playerID : -1;
        if(follow){
            setState(repair);
            targetItem = null;
            setMineTile(null);
            getPlaceQueue().clear();
            rebuildReturnState = null;
            rebuilding = false;
        }
    }

    public boolean isFollowPlayerMode(){
        return followPlayerMode;
    }

    @Override
    public boolean canCreateBlocks(){
        //builder drones can now start rebuild plans for destroyed team blocks
        return true;
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeLong(mineTile == null || !state.is(mine) ? -1 : mineTile.packedPosition());
        data.writeLong(state.is(repair) && target instanceof TileEntity ? ((TileEntity)target).tile.packedPosition() : -1);
        writeBuilding(data);
    }

    @Override
    public void read(DataInput data, long time) throws IOException{
        super.read(data, time);
        long mined = data.readLong();
        long repairing = data.readLong();

        readBuilding(data);

        if(mined != -1){
            mineTile = world.tile(mined);
        }

        if(repairing != -1){
            Tile tile = world.tile(repairing);
            target = tile.entity;
            state.set(repair);
        }else{
            state.set(retreat);
        }
    }

}
