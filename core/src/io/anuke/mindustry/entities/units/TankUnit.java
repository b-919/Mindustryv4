package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Predict;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Weapon;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class TankUnit extends BaseUnit{
    protected static Translator vec = new Translator();

    protected float walkTime;
    protected float baseRotation;
    protected float weaponRotation;
    protected float treadTime;
    protected Weapon weapon;

    public final UnitState

    attack = new UnitState(){
        public void entered(){
            target = null;
        }

        public void update(){
            if(health < maxHealth() * 0.5f && timer.get(timerRetreat, 30f)){
                Tile repair = Geometry.findClosest(x, y, world.indexer.getAllied(team, BlockFlag.repair));
                Unit healer = Units.getClosest(team, x, y, getType().healRange, u -> u.isHealer() && u != TankUnit.this);
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
                    path.moveWithPathfinding(TankUnit.this, target.getX(), target.getY());
                }else{
                    velocity.scl(0.85f);
                }
                return;
            }

            float dst = core == null ? 0 : distanceTo(core);

            if(core != null && dst < getWeapon().getAmmo().getRange() / 1.1f){
                target = core;
            }

            if(target == null && core == null && timer.get(timerFallback, 20f)){
                targetClosestEnemyFlag(BlockFlag.producer);
                if(target == null) targetClosestEnemyFlag(BlockFlag.turret);
                if(target == null) targetClosestEnemyFlag(BlockFlag.target);
                if(target != null){
                    path.moveWithPathfinding(TankUnit.this, target.getX(), target.getY());
                    return;
                }
            }

            if(dst > getWeapon().getAmmo().getRange() * 0.5f){
                moveToEnemyCore();
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
                Unit healer = Units.getClosest(team, x, y, getType().healRange, u -> u.isHealer() && u != TankUnit.this);
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
                        path.moveWithPathfinding(TankUnit.this, target.getX(), target.getY());
                    }else if(target instanceof Unit && ((Unit)target).isHealer()){
                        if(dst > type.healRange){
                            moveToHome();
                        }else{
                            path.moveWithPathfinding(TankUnit.this, target.getX(), target.getY());
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

    public TankUnit(){
        path.setTankLimits();
    }

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
    public UnitState getStartState(){
        return attack;
    }

    @Override
    public Weapon getWeapon(){
        return weapon;
    }

    @Override
    public float getDrag(){
        return type.drag * 1.5f;
    }

    @Override
    public boolean isRetreating(){
        return controller instanceof GroundAI ? ((GroundAI)controller).isRetreating() : state.is(retreat);
    }

    @Override
    public void interpolate(){
        super.interpolate();

        if(interpolator.values.length > 1){
            baseRotation = interpolator.values[1];
        }
        if(interpolator.values.length > 2){
            weaponRotation = interpolator.values[2];
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
    public void update(){
        super.update();

        stuckTime = !vec.set(x, y).sub(lastPosition()).isZero(0.0001f) ? 0f : stuckTime + Timers.delta();

        if(stuckTime > 20f){
            clearMovePath();
        }

        if(!velocity.isZero()){
            baseRotation = Mathf.slerpDelta(baseRotation, velocity.angle(), 0.08f);
        }

        if(stuckTime < 1f){
            walkTime += Timers.delta();
        }

        treadTime += velocity.len() * Timers.delta();

        if(Units.invalidateTarget(target, this)){
            rotation = Mathf.slerpDelta(rotation, baseRotation, type.rotatespeed);
        }

        if(!Net.client()){
            updateWeaponRotation();
        }
    }

    protected void updateWeaponRotation(){
        if(!Units.invalidateTarget(target, this)){
            weaponRotation = Mathf.slerpDelta(weaponRotation, angleTo(target), type.rotatespeed);
        }else{
            float targetRotation = velocity.isZero() ? baseRotation : velocity.angle();
            weaponRotation = Mathf.slerpDelta(weaponRotation, targetRotation, type.baseRotateSpeed);
        }
    }

    @Override
    public void behavior(){
        if(health <= health * type.retreatPercent && !isCommanded()){
            setState(retreat);
        }
    }

    @Override
    protected void updateShooting(){
        shootAt(target);
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

    protected void moveTo(float x, float y){
        float angle = angleTo(x, y);
        float curSpeed = velocity.len();
        float moveAngle = angle;

        if(curSpeed > 0.1f){
            float curAngle = velocity.angle();
            float delta = angle - curAngle;
            if(delta > 180f) delta -= 360f;
            if(delta < -180f) delta += 360f;
            moveAngle = curAngle + delta * 0.2f;
        }

        moveAngle = avoidAngle(moveAngle);
        velocity.add(vec.trns(moveAngle, type.speed * Timers.delta()));
    }

    protected void moveToEnemyCore(){
        Tile tile = world.tileWorld(x, y);
        if(tile == null) return;

        TileEntity core = getClosestEnemyCore();
        if(core == null) return;

        Tile targetTile = world.pathfinder.getTargetTile(team, tile);

        if(tile == targetTile){
            float ddx = core.getX() - x, ddy = core.getY() - y;
            boolean nearCore = ddx * ddx + ddy * ddy < (12 * tilesize) * (12 * tilesize);

            if(!nearCore && steerAlongChunkPath(core.getX(), core.getY())){
                return;
            }

            if(!hopClear(core.getX(), core.getY())){
                path.recenter(this);
                return;
            }

            float angle = avoidAngle(angleTo(core));
            velocity.add(vec.trns(angle, type.speed * Timers.delta()));
            return;
        }

        float gangle = avoidAngle(angleTo(targetTile));
        velocity.add(vec.trns(gangle, type.speed * Timers.delta()));
    }

    protected void moveToHome(){
        Team enemy = null;
        for(Team team : Vars.state.teams.enemiesOf(team)){
            if(Vars.state.teams.isActive(team)){
                enemy = team;
                break;
            }
        }

        if(enemy == null) return;

        Tile tile = world.tileWorld(x, y);
        if(tile == null) return;
        Tile targetTile = world.pathfinder.getTargetTile(enemy, tile);

        if(tile == targetTile) return;

        velocity.add(vec.trns(angleTo(targetTile), type.speed * Timers.delta()));
    }

    protected void moveAwayFromCore(){
        moveToHome();
    }

    protected void patrol(){
        vec.trns(baseRotation, type.speed * Timers.delta());
        velocity.add(vec.x, vec.y);
        vec.trns(baseRotation, type.hitsizeTile);
        Tile tile = world.tileWorld(x + vec.x, y + vec.y);
        if((tile == null || tile.solid() || tile.floor().drownTime > 0) || stuckTime > 10f){
            baseRotation += Mathf.sign(id % 2 - 0.5f) * Timers.delta() * 3f;
        }
    }

    protected void circle(float circleLength){
        if(target == null) return;

        vec.set(target.getX() - x, target.getY() - y);

        if(vec.len() < circleLength){
            vec.rotate((circleLength - vec.len()) / circleLength * 180f);
        }

        float moveAngle = avoidAngle(vec.angle());
        float len = type.speed * Timers.delta();
        vec.set(Angles.trnsx(moveAngle, len), Angles.trnsy(moveAngle, len));

        velocity.add(vec);
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

    public void draw(){
        UnitRenderer.drawTank(this);
    }

    @Override
    protected void drawItems(){
        float backTrns = 4f, itemSize = 5f;
        if(inventory.hasItem()){
            io.anuke.mindustry.type.ItemStack stack = inventory.getItem();
            int stored = Mathf.clamp(stack.amount / 6, 1, 8);

            for(int i = 0; i < stored; i++){
                float angT = i == 0 ? 0 : Mathf.randomSeedRange(i + 2, 60f);
                float lenT = i == 0 ? 0 : Mathf.randomSeedRange(i + 3, 1f) - 1f;
                Draw.rect(stack.item.region,
                    x + Angles.trnsx(baseRotation + 180f + angT, backTrns + lenT),
                    y + Angles.trnsy(baseRotation + 180f + angT, backTrns + lenT),
                    itemSize, itemSize, baseRotation);
            }
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
}
