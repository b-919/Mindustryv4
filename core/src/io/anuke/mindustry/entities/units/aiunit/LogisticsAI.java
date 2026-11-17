package io.anuke.mindustry.entities.units.aiunit;

import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.logic.LogicExporter.LogicExporterEntity;
import io.anuke.mindustry.world.blocks.logic.LogicImporter.LogicImporterEntity;

/**
 * Fetch/deliver controller for the logistics drone: carries items from its
 * {@link LogicExporterEntity} spawner to a matching {@link LogicImporterEntity}.
 */
public class LogisticsAI extends CommandAI{
    public enum Mode{
        fetch, deliver
    }

    protected Mode mode = Mode.fetch;

    public LogisticsAI(BaseUnit unit){
        super(unit);
    }

    @Override
    public void updateUnit(){
        if(mode == Mode.fetch){
            updateFetch();
        }else{
            updateDeliver();
        }
    }

    private void updateFetch(){
        FlyingUnit flying = (FlyingUnit)unit;
        Tile spawner = unit.getSpawner();
        if(spawner == null || !(spawner.entity() instanceof LogicExporterEntity)){
            unit.damage(999999f);
            return;
        }

        LogicExporterEntity exporter = spawner.entity();
        LogicImporterEntity targetImporter = exporter.targetImporter;

        if(exporter.targetPos != -1){
            Tile targetTile = Vars.world.tile(exporter.targetPos);
            if(targetTile != null && targetTile.entity() instanceof LogicImporterEntity){
                targetImporter = targetTile.entity();
            }
        }

        if(targetImporter == null){
            flying.circle(40f, unit.type.speed);
            if(unit.inventory.hasItem()){
                mode = Mode.deliver;
            }
            return;
        }

        Item targetItem = targetImporter.selectedItem;

        //manual target with no filter - pick any item from the exporter
        if(targetItem == null){
            if(unit.inventory.isFull()){
                mode = Mode.deliver;
                return;
            }

            if(unit.inventory.hasItem()){
                targetItem = unit.inventory.getItem().item;
            }else{
                targetItem = exporter.items.first();
            }

            if(targetItem == null){
                flying.circle(20f, unit.type.speed);
                return;
            }
        }

        if(unit.inventory.isFull() || (unit.inventory.hasItem() && unit.inventory.getItem().item != targetItem)){
            mode = Mode.deliver;
            return;
        }

        unit.target = spawner;
        flying.moveTo(0f);

        if(unit.distanceTo(spawner.worldx(), spawner.worldy()) < Vars.tilesize * 1.5f){
            int amount = Math.min(exporter.items.get(targetItem), unit.type.itemCapacity - unit.inventory.getItem().amount);
            if(amount > 0){
                exporter.items.remove(targetItem, amount);
                unit.inventory.addItem(targetItem, amount);
            }else{
                flying.circle(20f, unit.type.speed);
            }
        }
    }

    private void updateDeliver(){
        FlyingUnit flying = (FlyingUnit)unit;
        Tile spawner = unit.getSpawner();
        if(spawner == null || !(spawner.entity() instanceof LogicExporterEntity)){
            unit.damage(999999f);
            return;
        }

        if(!unit.inventory.hasItem()){
            mode = Mode.fetch;
            return;
        }

        LogicExporterEntity exporter = spawner.entity();
        LogicImporterEntity targetImporter = exporter.targetImporter;

        if(exporter.targetPos != -1){
            Tile targetTile = Vars.world.tile(exporter.targetPos);
            if(targetTile != null && targetTile.entity() instanceof LogicImporterEntity){
                targetImporter = targetTile.entity();
            }
        }

        if(targetImporter == null || targetImporter.tile == null){
            Item item = unit.inventory.getItem().item;
            //only search for other importers if there is no manual target
            if(exporter.targetPos == -1){
                for(int x = 0; x < Vars.world.width(); x++){
                    for(int y = 0; y < Vars.world.height(); y++){
                        Tile other = Vars.world.tile(x, y);
                        if(other != null && other.entity() instanceof LogicImporterEntity importer){
                            if(importer.selectedItem == item){
                                targetImporter = importer;
                                break;
                            }
                        }
                    }
                    if(targetImporter != null) break;
                }
            }
        }

        if(targetImporter == null || targetImporter.tile == null){
            flying.circle(40f, unit.type.speed);
            return;
        }

        unit.target = targetImporter.tile;
        flying.moveTo(0f);

        if(unit.distanceTo(targetImporter.tile.worldx(), targetImporter.tile.worldy()) < Vars.tilesize * 1.5f){
            Item item = unit.inventory.getItem().item;
            int amount = unit.inventory.getItem().amount;
            int accepted = targetImporter.tile.block().acceptStack(item, amount, targetImporter.tile, unit);
            if(accepted > 0){
                targetImporter.items.add(item, accepted);
                unit.inventory.getItem().amount -= accepted;
            }
        }
    }
}
