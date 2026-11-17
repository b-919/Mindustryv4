package io.anuke.mindustry.entities.units;

import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.distribution.TrainRail;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;
import io.anuke.ucore.core.Timers;

import static io.anuke.mindustry.Vars.world;

public abstract class MainTrain extends GroundUnit{
    protected static final Translator moveVec = new Translator();
    protected MainTrain nextWagon;
    protected MainTrain previousWagon;

    protected boolean isOnRail(){
        Tile tile = world.tileWorld(x, y);
        if(tile == null) return false;
        return tile.target().block() instanceof TrainRail;
    }

    @Override
    public void update(){
        super.update();

        if(!isOnRail()){
            velocity.setZero();
        }
    }

    @Override
    public void updateTargeting(){
    }

    @Override
    public void behavior(){
    }

    public void moveOnRails(){
        Tile tile = world.tileWorld(x, y);
        if(tile == null) return;
        tile = tile.target();
        if(!(tile.block() instanceof TrainRail)) return;

        int rotation = tile.getRotation();
        moveVec.set(Geometry.d4[rotation].x, Geometry.d4[rotation].y).setLength(type.speed * Timers.delta());
        velocity.add(moveVec.x, moveVec.y);
        this.rotation = Mathf.slerpDelta(this.rotation, moveVec.angle(), type.rotatespeed);
    }

    public MainTrain getNextWagon(){
        return nextWagon;
    }

    public MainTrain getPreviousWagon(){
        return previousWagon;
    }

    public void linkNextWagon(MainTrain wagon){
        nextWagon = wagon;
        if(wagon != null){
            wagon.previousWagon = this;
        }
    }
}
