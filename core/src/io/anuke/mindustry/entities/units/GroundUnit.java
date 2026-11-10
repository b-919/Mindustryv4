package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Predict;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Weapon;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.util.Geometry;

import static io.anuke.mindustry.Vars.*;

public class GroundUnit extends BaseUnit{
    protected static Translator vec = new Translator();
    protected float walkTime;
protected float baseRotation;
protected Weapon weapon;

    public final UnitState

    attack = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health < maxHealth() * 0.5f && timer.get(timerRetreat, 30f)){
                Tile repair = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair));
                Unit healer = Units.getClosest(team, x, y, getType().healRange, u -> u.isHealer() && u != GroundUnit.this);
                if(repair != null && distanceTo(repair) < getType().healRange){
                    state.set(retreat);
                    return;
                }else if(healer != null){
                    state.set(retreat);
                    return;
                }
            }

            TileEntity core = getClosestEnemyCore();

            if(getOrderType() == UnitOrderType.attackTarget && target != null){
                if(distanceTo(target) > engageRange()){
                    path.moveWithPathfinding(GroundUnit.this, target.getX(), target.getY());
                }else{
                    velocity.scl(0.85f);
                }
                return;
            }

            if(target == null && core != null){
                target = core;
            }

            if(target == null && timer.get(timerFallback, 20f)){
                targetClosestEnemyFlag(BlockFlag.producer);
                if(target == null) targetClosestEnemyFlag(BlockFlag.turret);
                if(target == null) targetClosestEnemyFlag(BlockFlag.target);
            }

            if(core != null && distanceTo(core) > getWeapon().getAmmo().getRange() * 0.5f){
                moveToEnemyCore();
            }else if(core == null && target != null){
                path.moveWithPathfinding(GroundUnit.this, target.getX(), target.getY());
            }
        }
    },
    patrol = new UnitState(){
        public void update(){
            TileEntity target = getClosestCore();
            if(target != null){
                if(distanceTo(target) > 400f){
                    moveAwayFromCore();
                }else{
                    patrol();
                }
            }
        }
    },
    retreat = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health >= maxHealth()){
                clearMovePath();
                if(isCommanded()){
                    onCommand(getCommand());
                }else{
                    state.set(attack);
                }
                return;
            }

            if(retarget() || target == null || (target instanceof TileEntity && (((TileEntity)target).getTile() == null || ((TileEntity)target).getTile().target().block().flags == null || !((TileEntity)target).getTile().target().block().flags.contains(BlockFlag.repair))) || (target instanceof Unit && !((Unit)target).isHealer())){
                Unit healer = Units.getClosest(team, x, y, getType().healRange, u -> u.isHealer() && u != GroundUnit.this);
                Tile repair = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair));
                clearMovePath();
                if(repair != null) target = repair.entity();
                else if(healer != null) target = healer;
                else target = getClosestCore();
            }

            if(target != null){
                float dst = distanceTo(target);
                if(dst > 7f){
                    if(target instanceof TileEntity && ((TileEntity)target).getTile() != null && ((TileEntity)target).getTile().target().block().flags != null && ((TileEntity)target).getTile().target().block().flags.contains(BlockFlag.repair)){
                        path.moveWithPathfinding(GroundUnit.this, target.getX(), target.getY());
                    }else if(target instanceof Unit && ((Unit)target).isHealer()){
                        if(dst > type.healRange){
                            moveToHome();
                        }else{
                            path.moveWithPathfinding(GroundUnit.this, target.getX(), target.getY());
                        }
                    }else{
                        moveToHome();
                    }
                }else{
                    velocity.setZero();
                }
            }else{
                moveToHome();
            }
        }
    },

    hold = new UnitState(){
        public void update(){
            velocity.scl(0.9f);
            if(retarget()){
                targetClosest();
            }
        }
    };

    @Override
    public void onCommand(UnitCommand command){
        if(controller instanceof GroundAI){
            ((GroundAI)controller).command(command);
        }
        state.set(command == UnitCommand.retreat ? retreat :
                  command == UnitCommand.attack ? attack :
                  command == UnitCommand.patrol ? patrol :
                  null);
    }

    @Override
    public void init(UnitType type, Team team){
        super.init(type, team);
        this.weapon = type.weapon;
    }

    @Override
    public void interpolate(){
        super.interpolate();

        if(interpolator.values.length > 1){
            baseRotation = interpolator.values[1];
        }
    }

    @Override
    public void move(float x, float y){
        if(Mathf.dst(x, y) > 0.01f){
            baseRotation = Mathf.slerpDelta(baseRotation, Mathf.atan2(x, y), type.baseRotateSpeed);
        }
        super.move(x, y);
    }

    @Override
    public UnitState getStartState(){
        return attack;
    }

    @Override
    public void update(){
        super.update();

        stuckTime = !vec.set(x, y).sub(lastPosition()).isZero(0.0001f) ? 0f : stuckTime + Timers.delta();

        if(stuckTime > 20f){
            clearMovePath();
        }

        if(!velocity.isZero()){
            baseRotation = Mathf.slerpDelta(baseRotation, velocity.angle(), 0.05f);
        }

        if(stuckTime < 1f){
            walkTime += Timers.delta();
        }
    }

    @Override
    public Weapon getWeapon(){
        return weapon;
    }

    public void setWeapon(Weapon weapon){
        this.weapon = weapon;
    }

    private float orderArrivalDst(float x, float y){
        Tile t = world.tileWorld(x, y);
        float blockRadius = t != null && t.block() != null && t.block().size > 0 ? t.block().size * tilesize / 2f : 0f;
        return Math.max(type.hitsize, 10f) + blockRadius;
    }

    @Override
    protected boolean updateOrder(){
        if(!hasOrder()){
            clearOrderPath();
            return false;
        }

        float arrivalDst = orderArrivalDst(getOrderX(), getOrderY());

        if(getOrderType() == UnitOrderType.move){
            float dst = distanceTo(getOrderX(), getOrderY());
            if(dst <= arrivalDst){
                clearOrder();
                clearOrderPath();
                velocity.scl(0.5f);
                if(controller instanceof GroundAI){
                    ((GroundAI)controller).setMode(GroundAI.Mode.hold);
                }else{
                    state.set(hold);
                }
                return false;
            }

            path.followOrderPath(this);
            return true;
        }

        if(getOrderType() == UnitOrderType.attackMove){
            if(retarget()){
                targetClosest();
            }

            float dst = distanceTo(getOrderX(), getOrderY());
            if(dst <= arrivalDst){
                clearOrder();
                clearOrderPath();
                if(controller instanceof GroundAI){
                    ((GroundAI)controller).setMode(GroundAI.Mode.hold);
                }else{
                    state.set(hold);
                }
                return false;
            }

            path.followOrderPath(this);
            return true;
        }

        if(getOrderType() == UnitOrderType.attackTarget){
            if(target == null || target.isDead() || target.getTeam() == team){
                clearOrder();
                clearOrderPath();
                if(controller instanceof GroundAI){
                    ((GroundAI)controller).setMode(GroundAI.Mode.hold);
                }else{
                    state.set(hold);
                }
                return false;
            }

            //leave movement to the unit's attack controller/state so custom behavior is preserved
            return false;
        }

        return false;
    }

    protected void clearOrderPath(){
        path.clearOrder();
    }

    protected void clearMovePath(){
        path.clearMove();
    }

    public int getOrderPathCursor(){
        return path.orderPathCursor;
    }

    public int getOrderPathSize(){
        return path.orderPath.size;
    }

    public long getOrderPathTilePacked(int index){
        return path.orderPath.get(index);
    }

    @Override
    public void draw(){
        UnitRenderer.drawGround(this);
    }

    @Override
    public void behavior(){
        if(health <= health * type.retreatPercent && !isCommanded()){
            setState(retreat);
        }

        if(!Units.invalidateTarget(target, this)){
            if(isAiming()){
                rotate(angleTo(target));
            }else if(!velocity.isZero()){
                rotation = Mathf.slerpDelta(rotation, velocity.angle(), type.rotatespeed);
            }
        }else if(!velocity.isZero()){
            rotation = Mathf.slerpDelta(rotation, velocity.angle(), type.rotatespeed);
        }
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

        if(ammo != null && target != null && !Units.invalidateTarget(target, team, x, y, Math.max(range, type.range))){
            Vector2 to = Predict.intercept(this, target, ammo.bullet.speed);
            aimX = to.x;
            aimY = to.y;
            shoot = !Units.invalidateTarget(target, team, x, y, range);
        }

        //rotating mounts track independently of the hull, fixed weapons need the hull aimed at the target
        aimWeaponMounts(aimX, aimY);
        controlWeaponMounts(type.rotateWeapon, shoot);
    }

    @Override
    public boolean isRetreating(){
        return controller instanceof GroundAI ? ((GroundAI)controller).isRetreating() : state.is(retreat);
    }

    @Override
    public void updateTargeting(){
        super.updateTargeting();

        if(!isRetreating() && Units.invalidateTarget(target, team, x, y, Float.MAX_VALUE)){
            target = null;
        }

        if(retarget()){
            targetClosest();
        }
    }

    @Override
    public void write(DataOutput data) throws IOException{
        super.write(data);
        data.writeByte(weapon.id);
    }

    @Override
    public void read(DataInput data, long time) throws IOException{
        super.read(data, time);
        weapon = content.getByID(ContentType.weapon, data.readByte() & 0xFF);
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        stream.writeByte(weapon.id);
        super.writeSave(stream);
    }

    @Override
    public void readSave(DataInput stream) throws IOException{
        weapon = content.getByID(ContentType.weapon, stream.readByte() & 0xFF);
        super.readSave(stream);
    }

    protected void moveTo(float x, float y){
        float angle = avoidAngle(angleTo(x, y));
        velocity.add(vec.trns(angle, type.speed * Timers.delta()));
        if(!isAiming()) rotation = Mathf.slerpDelta(rotation, angle, type.rotatespeed);
    }

    protected void getBehindTarget(Unit target, float behindDist, Translator out){
        float targetAngle;
        float tvx = target.getTargetVelocityX();
        float tvy = target.getTargetVelocityY();
        if(Mathf.dst(tvx, tvy) > 0.15f){
            targetAngle = Mathf.atan2(tvx, tvy);
        }else{
            targetAngle = target.rotation;
        }
        float behindAngle = targetAngle + 180f;
        out.set(Angles.trnsx(behindAngle, behindDist), Angles.trnsy(behindAngle, behindDist));
    }

}
