package io.anuke.mindustry.entities.units.types;

import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.FlyingUnit;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class MissileFlyer extends FlyingUnit{// i need a better name

    public MissileFlyer(){
        itWobbles = false;
    }

    @Override
    protected void attack(float circleLength){
        moveTo(circleLength);
    }

    @Override
    protected void updateRotation(){
        if(!Units.invalidateTarget(target, this)){
            rotation = Mathf.slerpDelta(rotation, angleTo(target), type.rotatespeed);
        }else{
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), type.baseRotateSpeed);
        }
    }

    @Override
    protected void wobble(){
        if(Net.client()) return;

        x += Mathf.sin(Timers.time() + id * 999, 25f, 0.08f) * Timers.delta();
        y += Mathf.cos(Timers.time() + id * 999, 25f, 0.08f) * Timers.delta();
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeByte(type.weapon.id);
    }

    @Override
    public void read(DataInput data, long time) throws IOException{
        super.read(data, time);
        data.readByte();
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        //kept from the old Lich/Revenant layout so pre-merge saves stay byte-compatible
        stream.writeByte(type.weapon.id);
        super.writeSave(stream);
    }

    @Override
    public void readSave(DataInput stream) throws IOException{
        stream.readByte();
        super.readSave(stream);
    }
}
