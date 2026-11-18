package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Queue;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.Predict;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.units.aiunit.*;
import io.anuke.mindustry.entities.traits.BuilderTrait;
import io.anuke.mindustry.entities.traits.CarriableTrait;
import io.anuke.mindustry.entities.traits.CarryTrait;
import io.anuke.mindustry.entities.traits.MinerTrait;
import io.anuke.mindustry.graphics.Trail;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;

public class FlyingUnit extends BaseUnit implements CarryTrait, MinerTrait, BuilderTrait{
    @Override
    public boolean isRetreating(){
        return controller instanceof FlyingAI && ((FlyingAI)controller).isRetreating();
    }
    protected static Translator vec = new Translator();
    protected static float wobblyness = 0.6f;
    protected boolean itWobbles = true;
    public Trail trail = new Trail(8);
    public Trail trail2 = new Trail(8);
    protected CarriableTrait carrying;
    protected Tile mineTile;
    protected final Queue<BuildRequest> placeQueue = new Queue<>();

    @Override
    public void onCommand(UnitCommand command){
        if(controller instanceof FlyingAI){
            ((FlyingAI)controller).command(command);
        }
    }

    protected void holdState(){
        if(controller instanceof FlyingAI){
            ((FlyingAI)controller).setMode(FlyingAI.Mode.hold);
        }
    }

    @Override
    public CarriableTrait getCarry(){
        return carrying;
    }

    @Override
    public void setCarry(CarriableTrait unit){
        this.carrying = unit;
    }

    @Override
    public float getCarryWeight(){
        return type.carryWeight;
    }

    @Override
    public Tile getMineTile(){
        return mineTile;
    }

    @Override
    public void setMineTile(Tile tile){
        this.mineTile = tile;
    }

    @Override
    public float getMinePower(){
        return type == null ? 0.7f : type.minePower;
    }

    @Override
    public boolean canMine(Item item){
        return type == null || type.mineAnyOre || type.toMine.contains(item);
    }

    @Override
    public Queue<BuildRequest> getPlaceQueue(){
        return placeQueue;
    }

    @Override
    public float getBuildPower(Tile tile){
        return type == null ? 0.3f : type.buildPower;
    }

    @Override
    public boolean canCreateBlocks(){
        return true;
    }

    @Override
    public boolean canShootWeapons(){
        if(controller instanceof BuilderAI && !((BuilderAI)controller).canShoot()){
            return false;
        }
        return super.canShootWeapons() && getMineTile() == null;
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        if(type != null && type.syncMineTile){
            data.writeLong(mineTile == null ? -1L : mineTile.packedPosition());
        }
        if(type != null && type.syncBuilding){
            BuilderAI ai = controller instanceof BuilderAI ? (BuilderAI)controller : null;
            data.writeLong(ai != null && ai.mode == BuilderAI.Mode.mine && mineTile != null ? mineTile.packedPosition() : -1L);
            data.writeLong(ai != null && ai.mode == BuilderAI.Mode.repair && target instanceof TileEntity ? ((TileEntity)target).tile.packedPosition() : -1L);
            writeBuilding(data);
        }
        if(type != null && type.leaderSync == 1){
            Player leader = controller instanceof FollowAI ? ((FollowAI)controller).getPlayerLeader() : null;
            data.writeInt(leader == null ? -1 : leader.id);
        }else if(type != null && type.leaderSync == 2){
            TileEntity leader = controller instanceof DefenderAI ? ((DefenderAI)controller).getLeader() : null;
            data.writeLong(leader == null ? -1L : leader.tile.packedPosition());
        }
    }

    @Override
    public void read(DataInput data, long time) throws IOException{
        super.read(data, time);
        if(type != null && type.syncMineTile){
            long mined = data.readLong();
            mineTile = mined == -1L ? null : world.tile(mined);
        }
        if(type != null && type.syncBuilding){
            long mined = data.readLong();
            long repairing = data.readLong();
            readBuilding(data);

            if(mined != -1L){
                mineTile = world.tile(mined);
            }

            if(controller instanceof BuilderAI){
                BuilderAI ai = (BuilderAI)controller;
                if(repairing != -1L){
                    Tile tile = world.tile(repairing);
                    target = tile == null ? null : tile.entity;
                    ai.mode = BuilderAI.Mode.repair;
                }else{
                    ai.mode = BuilderAI.Mode.retreat;
                }
            }
        }
        if(type != null && type.leaderSync == 1){
            Player leader = Vars.playerGroup.getByID(data.readInt());
            if(controller instanceof FollowAI) ((FollowAI)controller).setLeader(leader);
        }else if(type != null && type.leaderSync == 2){
            long id = data.readLong();
            Tile tile = id == -1L ? null : world.tile(id);
            if(controller instanceof DefenderAI) ((DefenderAI)controller).setLeader(tile == null ? null : tile.entity);
        }
    }

    @Override
    public void update(){
        super.update();

        updateBuilding(this);

        if(!Net.client()){
            updateRotation();
            wobble();
        }

        if(type.drawEngine){
            if(type.engineMirror){
                trail.update(
                    x + Angles.trnsx(rotation, type.engineOffsetY, -type.engineOffsetX) + Mathf.range(wobblyness),
                    y + Angles.trnsy(rotation, type.engineOffsetY, -type.engineOffsetX) + Mathf.range(wobblyness)
                );
                trail2.update(
                    x + Angles.trnsx(rotation, type.engineOffsetY, type.engineOffsetX) + Mathf.range(wobblyness),
                    y + Angles.trnsy(rotation, type.engineOffsetY, type.engineOffsetX) + Mathf.range(wobblyness)
                );
            }else{
                trail.update(
                    x + Angles.trnsx(rotation, type.engineOffsetY, type.engineOffsetX) + Mathf.range(wobblyness),
                    y + Angles.trnsy(rotation, type.engineOffsetY, type.engineOffsetX) + Mathf.range(wobblyness)
                );
            }
        }
    }

    @Override
    public boolean updateOrder(){
        if(!hasOrder()){
            return false;
        }

        if(getOrderType() == UnitOrderType.move){
            if(retarget()){
                targetClosest();
            }

            vec.set(getOrderX() - x, getOrderY() - y);
            if(vec.len() <= Math.max(type.hitsize, 10f)){
                clearOrder();
                holdState();
                return false;
            }

            vec.setLength(movementSpeed());
            movePref(vec);
            return true;
        }

        if(getOrderType() == UnitOrderType.attackMove){
            if(retarget()){
                targetClosest();
            }

            vec.set(getOrderX() - x, getOrderY() - y);
            if(vec.len() <= Math.max(type.hitsize, 10f)){
                clearOrder();
                holdState();
                return false;
            }

            vec.setLength(movementSpeed());
            movePref(vec);
            return true;
        }

        if(getOrderType() == UnitOrderType.attackTarget){
            if(target == null || target.isDead() || target.getTeam() == team){
                clearOrder();
                holdState();
                return false;
            }

            //leave movement to the unit's attack controller/state so custom hit-and-run AI is preserved
            return false;
        }

        return false;
    }

    @Override
    public void draw(){
        UnitRenderer.drawFlying(this);
    }

    @Override
    protected void updateShooting(){
        ensureWeaponMounts();
        if(getWeaponMounts().length == 0) return;

        AmmoType ammo = getWeaponMountAmmo();
        float range = getWeaponMountRange();

        //idle mounts rest pointing forwards
        float aimX = x + Angles.trnsx(rotation, 100f), aimY = y + Angles.trnsy(rotation, 100f);
        boolean shoot = false;

        if(ammo != null && target != null){
            boolean healAlly = ammo.bullet.collidesTeam && target.getTeam() == team && target.isValid()
                    && target.distanceTo(x, y) <= Math.max(range, type.range);

            if(healAlly || !Units.invalidateTarget(target, team, x, y, Math.max(range, type.range))){
                Vector2 to = Predict.intercept(this, target, ammo.bullet.speed);
                aimX = to.x;
                aimY = to.y;
                shoot = healAlly || !Units.invalidateTarget(target, team, x, y, range);
            }
        }

        aimWeaponMounts(aimX, aimY);
        controlWeaponMounts(type.rotateWeapon, shoot);
    }

    @Override
    public void drawEngine(){
        if(type == null || !type.drawEngine) return;

        Color color = type.trailTeamColor ? getTeam().color : type.trailColor;
        trail.draw(color, type.engineSize);
        if(type.engineMirror){
            trail2.draw(color, type.engineSize);
        }
    }

    @Override
    public void drawOver(){
        drawEngine();
        if(type != null && type.drawMiningLaser){
            drawBuilding(this);
        }
    }

    @Override
    public void behavior(){
        if(squad != null){
            squad.direction.add(velocity.x / squad.units, velocity.y / squad.units);
            velocity.setAngle(Mathf.slerpDelta(velocity.angle(), squad.direction.angle(), 0.3f));
        }
    }

    @Override
    public float drawSize(){
        return 60;
    }

    protected void wobble(){
        if(Net.client()) return;
        if(itWobbles){
        x += Mathf.sin(Timers.time() + id * 999, 25f, 0.08f)*Timers.delta();
        y += Mathf.cos(Timers.time() + id * 999, 25f, 0.08f)*Timers.delta();

        if(velocity.len() <= 0.05f && (type == null || !type.missileMovement)){
            rotation += Mathf.sin(Timers.time() + id * 99, 10f, 2.5f)*Timers.delta();
        }}
    }

    protected void updateRotation(){
        if(type != null && type.missileMovement){
            if(!Units.invalidateTarget(target, this)){
                rotation = Mathf.slerpDelta(rotation, angleTo(target), type.rotatespeed);
            }else{
                rotation = Mathf.slerpDelta(rotation, velocity.angle(), type.baseRotateSpeed);
            }
        }else if(type != null && type.mineAimRotation && mineTile != null && target != null && mineTile.distanceTo(this) < type.range){
            rotation = Mathf.slerpDelta(rotation, angleTo(target), 0.3f);
        }else{
            rotation = velocity.angle();
        }
    }

    public void circle(float circleLength){
        circle(circleLength, movementSpeed());
    }

    public void circle(float circleLength, float speed){
        if(target == null) return;

        vec.set(target.getX() - x, target.getY() - y);

        if(vec.len() < circleLength){
            vec.rotate((circleLength - vec.len()) / circleLength * 180f);
        }

        vec.setLength(speed);

        movePref(vec);
    }

    public void moveTo(float circleLength){
        if(target == null) return;

        vec.set(target.getX() - x, target.getY() - y);

        float length = circleLength <= 0.001f ? 1f : Mathf.clamp((distanceTo(target) - circleLength) / 100f, -1f, 1f);

        vec.setLength(movementSpeed() * length);
        if(length < 0) vec.rotate(180f);

        movePref(vec);
    }

    public void attack(float circleLength){
        if(type != null && type.missileMovement){
            moveTo(circleLength);
            return;
        }

        vec.set(target.getX() - x, target.getY() - y);

        float ang = angleTo(target);
        float diff = Angles.angleDist(ang, rotation);

        if(diff > 100f && vec.len() < circleLength){
            vec.setAngle(velocity.angle());
        }else{
            vec.setAngle(Mathf.slerpDelta(velocity.angle(), vec.angle(), 0.44f));
        }

        vec.setLength(movementSpeed());

        movePref(vec);
    }
}
