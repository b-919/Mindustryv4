package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemType;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.util.Structs;

import static io.anuke.mindustry.Vars.world;

/**
 * Mine/drop controller for core-mining drones: gathers the least-stocked ore of the type's
 * {@link UnitType#toMine} set and delivers it to the closest core.
 */
public class CoreMinerAI extends CommandAI{
    public enum Mode{
        mine, drop
    }

    protected Mode mode = Mode.mine;
    protected Item targetItem;

    public CoreMinerAI(BaseUnit unit){
        super(unit);
    }

    @Override
    public void updateUnit(){
        if(mode == Mode.mine){
            updateMine();
        }else{
            updateDrop();
        }
    }

    private void updateMine(){
        FlyingUnit flying = (FlyingUnit)unit;
        TileEntity core = unit.getClosestCore();
        if(core == null) return;

        if(targetItem == null){
            findItem();
        }

        //core is full of this item
        if(targetItem != null && core.tile.block().acceptStack(targetItem, 1, core.tile, unit) == 0){
            mode = Mode.drop;
            return;
        }

        if(unit.inventory.isFull()){
            mode = Mode.drop;
            return;
        }

        if(targetItem != null && !unit.inventory.canAcceptItem(targetItem)){
            return;
        }

        if(unit.retarget()){
            if(flying.getMineTile() == null){
                findItem();
            }
            if(targetItem != null){
                unit.target = world.indexer.findClosestOre(unit.x, unit.y, targetItem);
            }
        }

        if(unit.target instanceof Tile tile){
            flying.moveTo(unit.type.range / 1.5f);

            if(unit.distanceTo(tile) < unit.type.range && flying.getMineTile() != tile){
                flying.setMineTile(tile);
            }

            if(tile.block() != Blocks.air){
                mode = Mode.drop;
            }
        }
    }

    private void updateDrop(){
        FlyingUnit flying = (FlyingUnit)unit;

        if(unit.inventory.isEmpty()){
            mode = Mode.mine;
            return;
        }

        if(unit.inventory.getItem().item.type != ItemType.material){
            unit.inventory.clearItem();
            mode = Mode.mine;
            return;
        }

        unit.target = unit.getClosestCore();

        if(unit.target == null) return;

        TileEntity tile = (TileEntity)unit.target;

        if(unit.distanceTo(unit.target) < unit.type.range){
            if(tile.tile.block().acceptStack(unit.inventory.getItem().item, unit.inventory.getItem().amount, tile.tile, unit) == unit.inventory.getItem().amount){
                Call.transferItemTo(unit.inventory.getItem().item, unit.inventory.getItem().amount, unit.x, unit.y, tile.tile);
                unit.inventory.clearItem();
            }

            mode = Mode.mine;
        }

        flying.circle(unit.type.range / 1.4f);
    }

    protected void findItem(){
        TileEntity core = unit.getClosestCore();
        if(core == null) return;

        targetItem = Structs.findMin(unit.type.toMine, (a, b) -> -Integer.compare(core.items.get(a), core.items.get(b)));
    }
}
