package io.anuke.mindustry.entities.units.types;

import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.traits.MinerTrait;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.entities.units.UnitCommand;
import io.anuke.mindustry.entities.units.UnitState;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemType;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Structs;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;

public class DroneMiner extends FlyingUnit implements MinerTrait {

    @Override
    public boolean canShootWeapons(){
        return !state.is(mine);
    }

    protected Item targetItem;
    protected Tile mineTile;


    public final UnitState



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
            if(targetItem != null && entity.tile.block().acceptStack(targetItem, 1, entity.tile, DroneMiner.this) == 0){
                setState(drop);
                return;
            }

            //if inventory is full, drop it off.
            if(inventory.isFull()){
                setState(drop);
            }else{
                if(targetItem != null && !inventory.canAcceptItem(targetItem)){
                    setState(mine);
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
                        setState(mine);
                        return;
                    }

                    if(inventory.getItem().item.type != ItemType.material){
                        inventory.clearItem();
                        setState(mine);
                        return;
                    }

                    target = getClosestCore();

                    if(target == null) return;

                    TileEntity tile = (TileEntity) target;

                    if(distanceTo(target) < type.range){
                        if(tile.tile.block().acceptStack(inventory.getItem().item, inventory.getItem().amount, tile.tile, DroneMiner.this) == inventory.getItem().amount){
                            Call.transferItemTo(inventory.getItem().item, inventory.getItem().amount, x, y, tile.tile);
                            inventory.clearItem();
                        }

                        setState(mine);
                    }

                    circle(type.range / 1.4f);
                }
            };
    /*retreat = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health >= maxHealth()){
                state.set(mine);
            }else{
                circle(40f);
            }
        }
    };*/




    @Override
    public void onCommand(UnitCommand command){
        //no
    }

    @Override
    public boolean canMine(Item item){
        return type.toMine.contains(item);
    }


    @Override
    public float getMinePower(){
        return type.minePower;
    }


    @Override
    public Tile getMineTile(){
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile){
        mineTile = tile;
    }

    /*@Override
    public void update(){
        super.update();
        mine.entered();
    }*/


    @Override
    public void behavior(){
        if(health <= health * type.retreatPercent &&
                Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair)) != null){
            setState(mine);
        }
    }



    @Override
    public UnitState getStartState(){
        return mine;
    }

    @Override
    public void drawOver(){
        trail.draw(Palette.lightFlame, 3f);
        drawMining(this);
    }
    @Override
    public void draw(){
        Draw.alpha(hitTime / hitDuration);

        Draw.rect(type.name, x, y, rotation - 90);

        drawItems();

        Draw.alpha(1f);
    }

    @Override
    public float drawSize(){
        return isMining() ? mineDistance * 2f : 30f;
    }

    protected void findItem(){
        TileEntity entity = getClosestCore();
        if(entity == null){
            return;
        }
        targetItem = Structs.findMin(type.toMine, (a, b) -> -Integer.compare(entity.items.get(a), entity.items.get(b)));
    }

    public boolean shouldRotate(){
        return isMining();
    }
    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeLong(mineTile == null || !state.is(mine) ? -1 : mineTile.packedPosition());
    }


    @Override
    public void update(){
        if(isMining()) updateMining(); super.update();

    }
    @Override
    protected void updateRotation(){
        if(mineTile != null && shouldRotate() && mineTile.distanceTo(this) < type.range){
            rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.3f);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), 0.3f);
        }

    }


    @Override
    public void read(DataInput data, long time) throws IOException{
        super.read(data, time);
        long mined = data.readLong();


        if(mined != -1){
            mineTile = world.tile(mined);
        }
    }

}


