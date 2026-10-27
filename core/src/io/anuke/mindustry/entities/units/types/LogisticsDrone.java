package io.anuke.mindustry.entities.units.types;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.entities.units.UnitCommand;
import io.anuke.mindustry.entities.units.UnitState;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.logic.LogicExporter.LogicExporterEntity;
import io.anuke.mindustry.world.blocks.logic.LogicImporter.LogicImporterEntity;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;

import static io.anuke.ucore.core.Timers.delta;

public class LogisticsDrone extends FlyingUnit {
    @Override
    public boolean canShootWeapons() {
        return false;
    }
    float propRot;

    @Override
    public void update(){
        super.update();
        propRot += 25f * delta();
    }
    @Override
    public void draw(){
        Draw.alpha(hitTime / hitDuration);

        Draw.rect(type.name, x, y, rotation - 90);

        drawItems();

        Draw.alpha(1f);
        float x = 5.3f;
        float y = 5.3f;

        drawProp(-x,  y,  propRot);
        drawProp(-x,  -y,  propRot);
        drawProp( x,  y, -propRot);
        drawProp( x, -y , -propRot);
    }

    void drawProp(float localX, float localY, float spin){
        float wx = x + Angles.trnsx(rotation, localX, localY);
        float wy = y + Angles.trnsy(rotation, localX, localY);

        Draw.alpha(hitTime / hitDuration);
        Draw.color();
        Draw.alpha(0f);
        Draw.rect(type.name + "-propeller", wx, wy, spin);
    }

        public final UnitState
        fetch = new UnitState() {
            @Override
            public void update() {
                Tile spawner = getSpawner();
                if (spawner == null || !(spawner.entity() instanceof LogicExporterEntity)) {
                    damage(999999f);
                    return;
                }

                LogicExporterEntity exporter = spawner.entity();
                LogicImporterEntity targetImporter = exporter.targetImporter;

                if (exporter.targetPos != -1) {
                    Tile targetTile = Vars.world.tile(exporter.targetPos);
                    if (targetTile != null && targetTile.entity() instanceof LogicImporterEntity) {
                        targetImporter = (LogicImporterEntity) targetTile.entity();
                    }
                }

                if (targetImporter == null) {
                    circle(40f, type.speed);
                    if (inventory.hasItem()) {
                        setState(deliver);
                    }
                    return;
                }

                Item targetItem = targetImporter.selectedItem;

                if (targetItem == null) { // Manual target with no filter - pick any item from exporter
                    if (inventory.isFull()) {
                        setState(deliver);
                        return;
                    }

                    if (inventory.hasItem()) {
                        targetItem = inventory.getItem().item;
                    } else {
                        targetItem = exporter.items.first(); // Just pick first available item
                    }

                    if (targetItem == null) {
                        circle(20f, type.speed);
                        return;
                    }
                }

                if (inventory.isFull() || (inventory.hasItem() && inventory.getItem().item != targetItem)) {
                    setState(deliver);
                    return;
                }

                target = spawner;
                moveTo(0f);

                if (distanceTo(spawner.worldx(), spawner.worldy()) < Vars.tilesize * 1.5f) {
                    int amount = Math.min(exporter.items.get(targetItem), type.itemCapacity - inventory.getItem().amount);
                    if (amount > 0) {
                        exporter.items.remove(targetItem, amount);
                        inventory.addItem(targetItem, amount);
                    } else {
                        circle(20f, type.speed);
                    }
                }
            }
        },
        deliver = new UnitState() {
            @Override
            public void update() {
                Tile spawner = getSpawner();
                if (spawner == null || !(spawner.entity() instanceof LogicExporterEntity)) {
                    damage(999999f);
                    return;
                }

                if (!inventory.hasItem()) {
                    setState(fetch);
                    return;
                }

                LogicExporterEntity exporter = spawner.entity();
                LogicImporterEntity targetImporter = exporter.targetImporter;

                if (exporter.targetPos != -1) {
                    Tile targetTile = Vars.world.tile(exporter.targetPos);
                    if (targetTile != null && targetTile.entity() instanceof LogicImporterEntity) {
                        targetImporter = (LogicImporterEntity) targetTile.entity();
                    }
                }

                if (targetImporter == null || targetImporter.tile == null) {
                    Item item = inventory.getItem().item;
                    // Only search for other importers if we don't have a manual target
                    if (exporter.targetPos == -1) {
                        for (int x = 0; x < Vars.world.width(); x++) {
                            for (int y = 0; y < Vars.world.height(); y++) {
                                Tile other = Vars.world.tile(x, y);
                                if (other != null && other.entity() instanceof LogicImporterEntity) {
                                    LogicImporterEntity importer = (LogicImporterEntity) other.entity();
                                    if (importer.selectedItem == item) {
                                        targetImporter = importer;
                                        break;
                                    }
                                }
                            }
                            if (targetImporter != null) break;
                        }
                    }
                }

                if (targetImporter == null || targetImporter.tile == null) {
                    circle(40f, type.speed);
                    return;
                }

                target = targetImporter.tile;
                moveTo(0f);

                if (distanceTo(targetImporter.tile.worldx(), targetImporter.tile.worldy()) < Vars.tilesize * 1.5f) {
                    Item item = inventory.getItem().item;
                    int amount = inventory.getItem().amount;
                    
                    int accepted = targetImporter.tile.block().acceptStack(item, amount, targetImporter.tile, LogisticsDrone.this);
                    if (accepted > 0) {
                        targetImporter.items.add(item, accepted);
                        inventory.getItem().amount -= accepted;
                    }
                }
            }
        };
    @Override
    public void onCommand(UnitCommand command){
        //no
    }

    @Override
    public UnitState getStartState() {
        return fetch;
    }
}
