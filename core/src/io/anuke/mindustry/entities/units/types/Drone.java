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
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.ItemType;
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

    @Override
    public boolean canShootWeapons(){
        //don't shoot while mining or building!
        return !(state.is(mine) || state.is(build));
    }

    protected Item targetItem;
    protected Tile mineTile;
    protected Queue<BuildRequest> placeQueue = new Queue<>();
    protected boolean isBreaking;
    protected boolean followPlayerMode = false;
    protected int followPlayerID = -1;
    protected UnitState previousState;

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
                setState(repair);
                return;
            }

            if(core == null) return;

            if((entity.progress() < 1f || entity.progress() > 0f) && entity.tile.block() instanceof BuildBlock){ //building is valid
                if(!isBuilding() && distanceTo(target) < placeDistance * 0.9f){ //within distance, begin placing
                    checkRetreat();
                    if(isBreaking){
                        getPlaceQueue().addLast(new BuildRequest(entity.tile.x, entity.tile.y));
                    }else{
                        getPlaceQueue().addLast(new BuildRequest(entity.tile.x, entity.tile.y, entity.tile.getRotation(), entity.recipe));
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

                circle(placeDistance * 0.7f);
            }else{ //building isn't valid
                checkRetreat();
                setState(repair);
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
                    checkRetreat();
                    setState(mine);
                }
            });

            if(target == null) return;

            if(target.distanceTo(Drone.this) > type.range){
                circle(type.range*0.9f);
            }else{
                getWeapon().update(Drone.this, target.getX(), target.getY());
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

    public void notifyPlaced(BuildEntity entity, boolean isBreaking){
        float dist = Math.min(entity.distanceTo(x, y) - placeDistance, 0);

        if(!state.is(build) && dist / type.maxVelocity < entity.buildCost * 0.9f){
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
    protected void updateRotation(){
        if(target != null && ((state.is(repair) && target.distanceTo(this) < type.range) || state.is(mine))){
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
    public void drawOver(){
        trail.draw(Palette.lightTrail, 3f);
        drawBuilding(this);
    }

    @Override
    public float drawSize(){
        return isBuilding() ? placeDistance * 2f : 30f;
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
        }
    }

    public boolean isFollowPlayerMode(){
        return followPlayerMode;
    }

    @Override
    public boolean canCreateBlocks(){
        return false;
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
