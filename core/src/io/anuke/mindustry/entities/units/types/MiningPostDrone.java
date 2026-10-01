package io.anuke.mindustry.entities.units.types;

import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.traits.MinerTrait;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.entities.units.UnitCommand;
import io.anuke.mindustry.entities.units.UnitState;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.logic.MiningPost;
import io.anuke.mindustry.world.blocks.logic.MiningPost.MiningPostEntity;
import io.anuke.ucore.graphics.Draw;

public class MiningPostDrone extends FlyingUnit implements MinerTrait {
    private Tile mineTile;
    //3rd drone type that mines stuff, yay no more original ideas XD

    @Override
    public boolean canShootWeapons(){
        return !isMining();
    }

    public final UnitState 
        mine = new UnitState() {
            @Override
            public void update() {
                Tile spawner = getSpawner();
                if (spawner == null || !(spawner.block() instanceof MiningPost)) {
                    damage(9999999999f);
                    return;
                }

                MiningPostEntity entity = spawner.entity();
                Item targetItem = entity.selectedItem;

                if (targetItem == null) {
                    circle(40f, type.speed);
                    if (!inventory.isEmpty()) {
                        setState(drop);
                    }
                    return;
                }

                if (inventory.isFull() || (!inventory.isEmpty() && inventory.getItem().item != targetItem)) {
                    setState(drop);
                    return;
                }

                retarget(() -> {
                    target = Vars.world.indexer.findClosestOre(x, y, targetItem);
                });

                if (target instanceof Tile) {
                    Tile tile = (Tile) target;
                    moveTo(mineDistance * 0.8f);
                    if (distanceTo(tile.worldx(), tile.worldy()) < mineDistance) {
                        setMineTile(tile);
                    }
                }
            }
        },
        drop = new UnitState() {
            @Override
            public void update() {
                Tile spawner = getSpawner();
                if (spawner == null || !(spawner.block() instanceof MiningPost)) {
                    damage(9999999999f);
                    return;
                }

                if (inventory.isEmpty()) {
                    setState(mine);
                    return;
                }

                target = spawner;
                moveTo(0f);

                if (distanceTo(spawner.worldx(), spawner.worldy()) < Vars.tilesize * 1.5f) {
                    Item item = inventory.getItem().item;
                    int amount = inventory.getItem().amount;
                    
                    int accepted = spawner.block().acceptStack(item, amount, spawner, MiningPostDrone.this);
                    if (accepted > 0) {
                        Call.transferItemTo(item, accepted, x, y, spawner);
                        inventory.getItem().amount -= accepted;
                        if (inventory.getItem().amount <= 0) inventory.clearItem();
                    }
                }
            }
        };

    @Override
    public void update() {
        super.update();
        if (getMineTile() != null) {
            updateMining();
        }
    }

    @Override
    public void onCommand(UnitCommand command){
        //no
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
        trail.draw(this.getTeam().color, 3f);
        drawMining(this);
    }
    @Override
    public UnitState getStartState() {
        return mine;
    }

    @Override
    public Tile getMineTile() {
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile) {
        this.mineTile = tile;
    }

    @Override
    public float getMinePower() {
        return type.minePower;
    }

    @Override
    public boolean canMine(Item item) {
        return true;
    }
}
