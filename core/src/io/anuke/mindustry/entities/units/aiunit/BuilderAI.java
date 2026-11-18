package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.game.EventType.BuildSelectEvent;
import io.anuke.mindustry.game.Teams.BlockPlan;
import io.anuke.mindustry.gen.Call;
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
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Structs;

import static io.anuke.mindustry.Vars.unitGroups;
import static io.anuke.mindustry.Vars.world;

/** Builder/miner/repairer controller for the builder drones. */
public class BuilderAI extends CommandAI{
    public enum Mode{
        repair, rebuild, build, mine, drop, retreat
    }

    protected static int timerRebuildCheck = BaseUnit.timerIndex++;

    public Mode mode = Mode.repair;
    protected Item targetItem;
    protected boolean isBreaking;
    public boolean followPlayerMode = false;
    protected int followPlayerID = -1;
    protected Mode previousMode;
    protected Mode rebuildReturnMode;
    protected boolean rebuilding;

    public BuilderAI(BaseUnit unit){
        super(unit);
    }

    private FlyingUnit fly(){
        return (FlyingUnit)unit;
    }

    public void setMode(Mode mode){
        this.mode = mode;
    }

    public void setFollowPlayer(boolean follow, int playerID){
        this.followPlayerMode = follow;
        this.followPlayerID = follow ? playerID : -1;
        if(follow){
            mode = Mode.repair;
            targetItem = null;
            fly().setMineTile(null);
            fly().getPlaceQueue().clear();
            rebuildReturnMode = null;
            rebuilding = false;
        }
    }

    @Override
    public void updateUnit(){
        if(!unit.updateOrder()){
            if(!followPlayerMode && mode == Mode.mine && timer.get(timerRebuildCheck, 20)){
                startRebuild();
            }

            updateMode();
        }

        if(followPlayerMode){
            updateFollow();
        }

        if(mode == Mode.repair && unit.target != null && unit.target.getTeam() != unit.getTeam()){
            unit.target = null;
        }

        if(mode != Mode.mine){
            fly().setMineTile(null);
        }
    }

    public boolean canShoot(){
        return mode == Mode.repair && !fly().isBuilding() && unit.target instanceof TileEntity
                && unit.target.getTeam() == unit.getTeam()
                && ((TileEntity)unit.target).healthf() < 1f;
    }

    protected void updateMode(){
        switch(mode){
            case repair:
                updateRepair();
                break;
            case rebuild:
                updateRebuild();
                break;
            case build:
                updateBuild();
                break;
            case mine:
                updateMine();
                break;
            case drop:
                updateDrop();
                break;
            case retreat:
                updateRetreat();
                break;
        }
    }

    private void updateRepair(){
        FlyingUnit flying = (FlyingUnit)unit;

        if(unit.retarget()){
            unit.target = Units.findDamagedTile(unit.getTeam(), unit.x, unit.y);

            if(unit.target == null){
                if(followPlayerMode || !startRebuild()){
                    checkRetreat();
                    mode = Mode.mine;
                }
            }
        }

        if(mode == Mode.rebuild) return;
        if(unit.target == null) return;

        if(unit.target.distanceTo(unit) > unit.getType().range){
            flying.circle(unit.getType().range * 0.9f);
        }else if(canShoot()){
            unit.shootAt(unit.target);
        }
    }

    private void updateRebuild(){
        FlyingUnit flying = (FlyingUnit)unit;
        BuilderTrait.BuildRequest request = fly().getCurrentRequest();

        if(request == null){
            restoreAfterRebuild();
            return;
        }

        Tile tile = world.tile(request.x, request.y);

        if(tile == null){
            fly().getPlaceQueue().clear();
            restoreAfterRebuild();
            return;
        }

        if(!validTarget(tile, request)){
            fly().getPlaceQueue().clear();
            restoreAfterRebuild();
            return;
        }

        unit.target = tile;
        flying.circle(BuilderTrait.buildRange * 0.7f);

        if(tile.entity instanceof BuildEntity){
            unit.target = tile.entity;
            mode = Mode.build;
        }
    }

    private void updateBuild(){
        FlyingUnit flying = (FlyingUnit)unit;
        BuildEntity entity = unit.target instanceof BuildEntity ? (BuildEntity)unit.target : null;
        TileEntity core = unit.getClosestCore();
        BuilderTrait.BuildRequest request = fly().getCurrentRequest();

        if(entity == null){
            fly().getPlaceQueue().clear();
            checkRetreat();
            if(rebuilding){
                restoreAfterRebuild();
            }else{
                mode = Mode.repair;
            }
            return;
        }

        if(request != null && !validTarget(entity.tile, request)){
            fly().getPlaceQueue().clear();
            checkRetreat();
            if(rebuilding){
                restoreAfterRebuild();
            }else{
                mode = Mode.repair;
            }
            return;
        }

        if(core == null) return;

        if((entity.progress() < 1f || entity.progress() > 0f) && entity.tile.block() instanceof io.anuke.mindustry.world.blocks.BuildBlock){
            if(!fly().isBuilding() && unit.distanceTo(unit.target) < BuilderTrait.buildRange * 0.9f){
                checkRetreat();
                if(isBreaking){
                    fly().addBuildRequest(new BuilderTrait.BuildRequest(entity.tile.x, entity.tile.y));
                }else{
                    fly().addBuildRequest(new BuilderTrait.BuildRequest(entity.tile.x, entity.tile.y, entity.tile.getRotation(), entity.recipe));
                }
            }

            if(entity.recipe != null && !cheats()){
                for(ItemStack stack : entity.recipe.requirements){
                    if(!core.items.has(stack.item, stack.amount) && unit.getType().toMine.contains(stack.item)){
                        targetItem = stack.item;
                        fly().getPlaceQueue().clear();
                        checkRetreat();
                        mode = Mode.mine;
                        return;
                    }
                }
            }

            flying.circle(BuilderTrait.buildRange * 0.7f);
        }else{
            checkRetreat();
            if(rebuilding){
                restoreAfterRebuild();
            }else{
                mode = Mode.repair;
            }
        }
    }

    private void updateMine(){
        FlyingUnit flying = (FlyingUnit)unit;
        TileEntity core = unit.getClosestCore();

        if(core == null) return;

        if(targetItem == null){
            findItem();
        }

        if(targetItem != null && core.tile.block().acceptStack(targetItem, 1, core.tile, unit) == 0){
            checkRetreat();
            mode = Mode.repair;
            return;
        }

        if(unit.inventory.isFull()){
            checkRetreat();
            mode = Mode.drop;
            return;
        }

        if(targetItem != null && !unit.inventory.canAcceptItem(targetItem)){
            checkRetreat();
            mode = Mode.drop;
            return;
        }

        if(unit.retarget()){
            if(fly().getMineTile() == null){
                findItem();
            }
            if(targetItem != null){
                unit.target = world.indexer.findClosestOre(unit.x, unit.y, targetItem);
            }
        }

        if(unit.target instanceof Tile tile){
            flying.approach(unit.getType().range * 0.9f);

            if(unit.distanceTo(tile) < unit.getType().range && fly().getMineTile() != tile){
                fly().setMineTile(tile);
            }

            if(tile.block() != Blocks.air){
                checkRetreat();
                mode = Mode.drop;
            }
        }
    }

    private void updateDrop(){
        FlyingUnit flying = (FlyingUnit)unit;

        if(unit.inventory.isEmpty()){
            checkRetreat();
            mode = Mode.mine;
            return;
        }

        if(unit.inventory.getItem().item.type != ItemType.material){
            unit.inventory.clearItem();
            checkRetreat();
            mode = Mode.mine;
            return;
        }

        unit.target = unit.getClosestCore();

        if(unit.target == null) return;

        TileEntity tile = (TileEntity)unit.target;

        if(unit.distanceTo(unit.target) < unit.getType().range){
            if(tile.tile.block().acceptStack(unit.inventory.getItem().item, unit.inventory.getItem().amount, tile.tile, unit) == unit.inventory.getItem().amount){
                Call.transferItemTo(unit.inventory.getItem().item, unit.inventory.getItem().amount, unit.x, unit.y, tile.tile);
                unit.inventory.clearItem();
            }

            checkRetreat();
            mode = Mode.repair;
        }

        flying.circle(unit.getType().range / 1.8f);
    }

    private void updateRetreat(){
        FlyingUnit flying = (FlyingUnit)unit;

        if(unit.health >= unit.maxHealth()){
            mode = previousMode == null ? Mode.repair : previousMode;
        }else if(!unit.targetHasFlag(BlockFlag.repair)){
            if(unit.retarget()){
                unit.targetClosestAllyFlag(BlockFlag.repair);
                if(unit.target == null){
                    unit.target = Units.getClosest(unit.getTeam(), unit.x, unit.y, unit.getType().healRange, u -> u.isHealer() && u != unit);
                }
            }
        }else{
            flying.circle(40f);
        }
    }

    private void updateFollow(){
        Player p = Vars.playerGroup.getByID(followPlayerID);

        if(p != null && !p.isDead()){
            unit.target = p;
            ((FlyingUnit)unit).moveTo(55f);

            //assist combat and repairs only; disable mining/drop behavior in follow mode
            if(unit.retarget()){
                TileEntity damaged = Units.findDamagedTile(unit.getTeam(), unit.x, unit.y);
                if(damaged != null && unit.distanceTo(damaged) < unit.getType().range * 1.3f){
                    unit.target = damaged;
                }else{
                    unit.targetClosest();
                }
            }

            if(canShoot() && unit.target != null && unit.target != p && unit.distanceTo(unit.target) < unit.getType().range){
                unit.shootAt(unit.target);
            }
        }
    }

    protected void checkRetreat(){
        if(unit.health < unit.maxHealth() * unit.getType().retreatPercent){
            boolean hasRepairPoint = Geometry.findClosest(unit.x, unit.y, world.indexer.getAllied(unit.getTeam(), BlockFlag.repair)) != null ||
                    Units.getClosest(unit.getTeam(), unit.x, unit.y, 400f, u -> u.isHealer() && u != unit) != null;

            if(hasRepairPoint){
                previousMode = mode;
                mode = Mode.retreat;
            }
        }
    }

    /** Picks the closest valid destroyed-block plan and starts rebuilding it. */
    protected boolean startRebuild(){
        BlockPlan best = null;
        float bestDst = Float.MAX_VALUE;
        BlockPlan needItems = null;
        float needDst = Float.MAX_VALUE;
        TileEntity core = unit.getClosestCore();

        for(BlockPlan plan : Vars.state.teams.get(unit.getTeam()).plans){
            if(!canRebuild(plan)) continue;

            float dst = Mathf.dst(plan.x * Vars.tilesize - unit.x, plan.y * Vars.tilesize - unit.y);

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

        if(mode != Mode.rebuild && !rebuilding){
            rebuildReturnMode = mode;
            rebuilding = true;
        }

        fly().addBuildRequest(new BuilderTrait.BuildRequest(best.x, best.y, best.rotation, recipe));
        unit.target = tile;
        mode = Mode.rebuild;
        return true;
    }

    protected boolean hasRequiredItems(BlockPlan plan, TileEntity core){
        Recipe recipe = Recipe.getByResult(plan.block);
        if(recipe == null || core == null) return false;
        if(cheats()) return true;

        for(ItemStack stack : recipe.requirements){
            if(!core.items.has(stack.item, 1)){
                return false;
            }
        }

        return true;
    }

    protected boolean cheats(){
        return Vars.state.mode.infiniteResources || (Vars.state.mode.enemyCheat && unit.getTeam() != Vars.defaultTeam);
    }

    protected void mineForPlan(BlockPlan plan, TileEntity core){
        Recipe recipe = Recipe.getByResult(plan.block);
        if(recipe == null || core == null) return;

        for(ItemStack stack : recipe.requirements){
            if(!core.items.has(stack.item, 1) && unit.getType().toMine.contains(stack.item)){
                targetItem = stack.item;
                fly().getPlaceQueue().clear();

                if(mode != Mode.mine){
                    mode = Mode.mine;
                }
                return;
            }
        }
    }

    protected void restoreAfterRebuild(){
        Mode next = rebuildReturnMode == null ? Mode.repair : rebuildReturnMode;
        rebuildReturnMode = null;
        rebuilding = false;
        mode = next;
    }
    protected boolean validTarget(Tile tile, BuilderTrait.BuildRequest request){
        if(tile == null || request.breaking) return tile != null;
        if(request.recipe == null) return false;
        if(tile.block() == Blocks.air) return true;
        if(tile.block() instanceof BuildBlock){
            BuildEntity existing = tile.entity();
            return existing == null || existing.recipe == null || existing.recipe.result == request.recipe.result;
        }
        return false;
    }

    protected boolean canRebuild(BlockPlan plan){
        Tile tile = world.tile(plan.x, plan.y);
        Recipe recipe = Recipe.getByResult(plan.block);

        if(tile == null || recipe == null || !recipe.available()) return false;

        if(tile.block() == Blocks.air){
            return Build.validPlace(unit.getTeam(), plan.x, plan.y, plan.block, plan.rotation);
        }

        if(tile.entity instanceof BuildEntity){
            BuildEntity entity = tile.entity();
            return !entity.deconstructing && entity.recipe != null && entity.recipe.result == plan.block;
        }

        return false;
    }

    public void notifyPlaced(BuildEntity entity, boolean isBreaking){
        float dist = Math.min(entity.distanceTo(unit.x, unit.y) - BuilderTrait.buildRange, 0);

        if(mode != Mode.build && mode != Mode.rebuild && dist / unit.getType().maxVelocity < entity.buildCost * 0.9f){
            unit.target = entity;
            this.isBreaking = isBreaking;
            mode = Mode.build;
        }
    }

    protected void findItem(){
        TileEntity core = unit.getClosestCore();
        if(core == null) return;

        targetItem = Structs.findMin(unit.getType().toMine, (a, b) -> -Integer.compare(core.items.get(a), core.items.get(b)));
    }

    static{
        Events.on(BuildSelectEvent.class, event -> {
            EntityGroup<BaseUnit> group = unitGroups[event.team.ordinal()];

            if(!(event.builder instanceof Player) || !(event.tile.entity instanceof BuildEntity)) return;
            BuildEntity entity = event.tile.entity();

            for(BaseUnit unit : group.all()){
                if(!(unit instanceof BuilderTrait) || !(unit.getController() instanceof BuilderAI ai)) continue;

                if(((BuilderTrait)unit).isBuilding()){
                    BuilderTrait.BuildRequest req = ((BuilderTrait)unit).getCurrentRequest();
                    if(req.breaking != event.breaking && req.x == event.tile.x && req.y == event.tile.y){
                        ((BuilderTrait)unit).clearBuilding();
                        ai.setMode(Mode.repair);
                    }
                }

                ai.notifyPlaced(entity, event.breaking);
            }
        });
    }
}
