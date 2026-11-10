package io.anuke.mindustry.entities.units.types;

import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.BiomassAirUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Weapon;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


public class BiomassMosquito extends BiomassAirUnit {
    protected TextureRegion wing1, wing2;

    // After Crashing out for 2 hours why a good damn sprite region crashes, I think all this stupid stuff should fix it
    protected void ensureInitialized(){
        if(type == null) return;

        if(wing1 == null){
            wing1 = Draw.region(type.name + "-wing1");
            if(wing1 == null) wing1 = type.region; // crash are annoying
        }
        if(wing2 == null){
            wing2 = Draw.region(type.name + "-wing2");
            if(wing2 == null) wing2 = wing1; // CRASH SHALL NO PASS
        }
    }

    public void init(UnitType type, Team team){
        super.init(type, team);
        this.wing1 = Draw.region(type.name + "-wing1");
        this.wing2 = Draw.region(type.name + "-wing2");
        ensureInitialized();
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
        {
            x += Mathf.sin(Timers.time() + id * 999, 25f, 0.08f)*Timers.delta();
            y += Mathf.cos(Timers.time() + id * 999, 25f, 0.08f)*Timers.delta();

            if(velocity.len() <= 0.05f){
                // rotation += Mathf.sin(Timers.time() + id * 99, 10f, 2.5f)*Timers.delta();
            }
        }
    }
    @Override
    public void draw(){
        ensureInitialized();
        if(type == null || type.region == null) return;

        Draw.alpha(hitTime / hitDuration);

        float baseRotation = rotation - 90;
        float wingAnim = Mathf.sin(Timers.time(), 1.5f, 25f);

        if(wing1 != null) Draw.rect(wing1, x, y, baseRotation + wingAnim);
        if(wing2 != null) Draw.rect(wing2, x, y, baseRotation - wingAnim);

        Draw.rect(type.region, x, y, baseRotation);

        drawItems();

        Draw.alpha(1f);



        for(int i : Mathf.signs){
            if(!getWeapon().mirror && i < 0) continue;
            Draw.alpha(hitTime / hitDuration);
            float tra = rotation - 90,
                    trY = -getWeapon().getRecoil(this, i > 0);
            float wx = x + Angles.trnsx(tra, getWeapon().x * i, trY),
                    wy = y + Angles.trnsy(tra, getWeapon().x * i, trY);
            if(getWeapon() != null && getWeapon().equipRegion != null){
                Draw.rect(getWeapon().equipRegion, wx , wy, rotation - 90);
            }

        }
    }

    @Override
    public void update(){
        super.update();
        float back = -21f;
        float side = 0f;
        trail.update(
                x + Angles.trnsx(rotation, back, side),
                y + Angles.trnsy(rotation, back, side)
        );
    }

    @Override
    public void drawOver(){}

    @Override
    public void write(DataOutput data) throws IOException {
        super.write(data);
        data.writeByte(type.weapon.id);
    }

    @Override
    public void read(DataInput data, long time) throws IOException{
        super.read(data, time);
        data.readByte();
        ensureInitialized();
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        stream.writeByte(type.weapon.id);
        super.writeSave(stream);
    }

    @Override
    public void readSave(DataInput stream) throws IOException{
        stream.readByte();
        super.readSave(stream);
        ensureInitialized();
    }
}
