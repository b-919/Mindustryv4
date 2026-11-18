package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.traits.MinerTrait;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.logic.MiningPost;
import io.anuke.mindustry.world.blocks.logic.MiningPost.MiningPostEntity;

/**
 * Mine/drop controller for mining-post drones: gathers the spawner's selected ore and delivers it
 * back to the {@link MiningPost}.
 */
public class MinerAI extends CommandAI{
    public enum Mode{
        mine, drop
    }

    protected Mode mode = Mode.mine;

    public MinerAI(BaseUnit unit){
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
        Tile spawner = unit.getSpawner();
        if(spawner == null || !(spawner.block() instanceof MiningPost)){
            unit.damage(9999999999f);
            return;
        }

        MiningPostEntity entity = spawner.entity();
        Item targetItem = entity.selectedItem;

        if(targetItem == null){
            flying.circle(40f, unit.type.speed);
            if(!unit.inventory.isEmpty()){
                mode = Mode.drop;
            }
            return;
        }

        if(unit.inventory.isFull() || (!unit.inventory.isEmpty() && unit.inventory.getItem().item != targetItem)){
            mode = Mode.drop;
            return;
        }

        if(unit.retarget()){
            unit.target = Vars.world.indexer.findClosestOre(unit.x, unit.y, targetItem);
        }

        if(unit.target instanceof Tile tile){
            flying.approach(MinerTrait.mineDistance * 0.9f);
            if(unit.distanceTo(tile.worldx(), tile.worldy()) < MinerTrait.mineDistance){
                flying.setMineTile(tile);
            }
        }
    }

    private void updateDrop(){
        FlyingUnit flying = (FlyingUnit)unit;
        Tile spawner = unit.getSpawner();
        if(spawner == null || !(spawner.block() instanceof MiningPost)){
            unit.damage(9999999999f);
            return;
        }

        if(unit.inventory.isEmpty()){
            mode = Mode.mine;
            return;
        }

        unit.target = spawner;
        flying.moveTo(0f);

        if(unit.distanceTo(spawner.worldx(), spawner.worldy()) < Vars.tilesize * 1.5f){
            Item item = unit.inventory.getItem().item;
            int amount = unit.inventory.getItem().amount;
            int accepted = spawner.block().acceptStack(item, amount, spawner, unit);
            if(accepted > 0){
                Call.transferItemTo(item, accepted, unit.x, unit.y, spawner);
                unit.inventory.getItem().amount -= accepted;
                if(unit.inventory.getItem().amount <= 0) unit.inventory.clearItem();
            }
        }
    }
}
