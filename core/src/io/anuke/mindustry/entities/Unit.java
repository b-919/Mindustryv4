package io.anuke.mindustry.entities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.traits.*;
import io.anuke.mindustry.entities.weapon.WeaponMount;
import io.anuke.mindustry.entities.weapon.WeaponMounts;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams.TeamData;
import io.anuke.mindustry.net.Interpolator;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.StatusEffect;
import io.anuke.mindustry.type.Weapon;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.impl.DestructibleEntity;
import io.anuke.ucore.entities.trait.DamageTrait;
import io.anuke.ucore.entities.trait.DrawTrait;
import io.anuke.ucore.entities.trait.Entity;
import io.anuke.ucore.entities.trait.SolidTrait;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Fill;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Physics;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public abstract class Unit extends DestructibleEntity implements SaveTrait, TargetTrait, WeaponMounts, SyncTrait, DrawTrait, TeamTrait, CarriableTrait, InventoryTrait{
    /**Total duration of hit flash effect*/
    public static final float hitDuration = 9f;
    /**Percision divisor of velocity, used when writing. For example a value of '2' would mean the percision is 1/2 = 0.5-size chunks.*/
    public static final float velocityPercision = 8f;
    /**Maximum absolute value of a velocity vector component.*/
    public static final float maxAbsVelocity = 127f / velocityPercision;

    private static final Rectangle queryRect = new Rectangle();
    private static final Vector2 moveVector = new Vector2();
    /** Reused separation callback; allocating this lambda once per unit per frame is avoidable. */
    private final Consumer<Unit> avoidConsumer = this::avoidOther;

    //leg-step collision scratch; the logic thread is single-threaded like the other shared scratch above
    private static final Rectangle legRect = new Rectangle(), legRect2 = new Rectangle(), legTmp = new Rectangle();

    /** Multiplier applied to ground displacement by movement-type logic (tank terrain slowdown). */
    public float terrainSpeedMultiplier = 1f;

    public final UnitInventory inventory = new UnitInventory(this);
    public Boolean emitLight = null;
    public float lightRadius = -1f;
    public Color lightColor = null;
    public float lightOpacity = -1f;
    public float rotation;
    public float hitTime;
    public boolean isPlayerControllable = true;
    public boolean isRTSAIControllable = true;
    /**If true, the unit is drawn above trees (like air units). */
    public boolean highAltitude;

    private transient WeaponMount[] weaponMounts = new WeaponMount[0];
    private transient Weapon[] weaponMountDefinitions = new Weapon[0];

    protected final Interpolator interpolator = new Interpolator();
    protected final StatusController status = new StatusController();
    protected Team team = Team.blue;

    protected CarryTrait carrier;
    protected float drownTime;
    /**Last unit/entity that hit this unit, used by the Mass AI for retaliation targets.*/
    public transient Entity lastDamager;

    @Override
    public boolean movable(){
        return !isDead();
    }

    @Override
    public UnitInventory getInventory(){
        return inventory;
    }

    @Override
    public CarryTrait getCarrier(){
        return carrier;
    }

    @Override
    public void setCarrier(CarryTrait carrier){
        this.carrier = carrier;
    }

    @Override
    public Team getTeam(){
        return team;
    }

    @Override
    public float getRotation(){
        return rotation;
    }

    @Override
    public WeaponMount[] getWeaponMounts(){
        return weaponMounts;
    }

    @Override
    public void setWeaponMounts(WeaponMount[] mounts){
        this.weaponMounts = mounts == null ? new WeaponMount[0] : mounts;
    }

    public void ensureWeaponMounts(){
        Weapon[] definitions = getWeaponDefinitions();
        //getWeaponDefinitions() returns a cached array for every owner, so a reference compare avoids a per-tick Arrays.equals
        if(definitions != weaponMountDefinitions){
            weaponMountDefinitions = definitions;
            buildWeaponMounts(definitions);
        }
    }

    @Override
    public void interpolate(){
        interpolator.update();

        x = interpolator.pos.x;
        y = interpolator.pos.y;

        if(interpolator.values.length > 0){
            rotation = interpolator.values[0];
        }
    }

    @Override
    public Interpolator getInterpolator(){
        return interpolator;
    }

    @Override
    public void onHit(SolidTrait entity){
        if(entity instanceof Bullet){
            Entity owner = ((Bullet) entity).getOwner();
            if(owner != null) lastDamager = owner;
        }
    }

    @Override
    public void damage(float amount){
        super.damage(calculateDamage(amount));
        hitTime = hitDuration;

        if (team == Team.themass) {
            MassAI.onDamage(this.x, this.y, lastDamager);
            MassAI.onUnitDamage(this, lastDamager);
            lastDamager = null;
        }
    }

    @Override
    public boolean collides(SolidTrait other){
        if(isDead()) return false;

        if(other instanceof DamageTrait){
            return other instanceof TeamTrait && state.teams.areEnemies((((TeamTrait) other).getTeam()), team);
        }else{
            return other instanceof Unit && ((Unit) other).isFlying() == isFlying();
        }
    }

    @Override
    public void onDeath(){
        inventory.clear();
        drownTime = 0f;
        status.clear();
    }

    @Override
    public Vector2 getVelocity(){
        return velocity;
    }

    /**Current drowning progress, 0 to 1. Read by the shared unit renderer.*/
    public float getDrownTime(){
        return drownTime;
    }

    @Override
    public void move(float x, float y){
        if(!isFlying()){
            if(ignoresSolids()){
                moveBy(x, y);
            }else if(usesLegStep()){
                moveLegs(x, y);
            }else{
                super.move(x, y);
            }
        }else{
            moveBy(x, y);
        }
    }

    /** True when this unit moves and pathfinds through solid blocks, walls and cliffs. */
    public boolean ignoresSolids(){
        return false;
    }

    /** True when this unit can step over constructed blocks instead of colliding with them. */
    public boolean usesLegStep(){
        return false;
    }

    /** How strongly this unit is affected by floor speed modifiers. */
    public float getFloorMultiplier(){
        return 1f;
    }

    /** Tile collision for legged units: constructed/destructible blocks are stepped over. */
    private void moveLegs(float deltax, float deltay){
        if((Math.abs(deltax) < 0.0001f && Math.abs(deltay) < 0.0001f) || deltax != deltax || deltay != deltay){
            return;
        }

        deltax = Mathf.clamp(deltax, -1000f, 1000f);
        deltay = Mathf.clamp(deltay, -1000f, 1000f);

        boolean movedx = false;
        while(Math.abs(deltax) > 0 || !movedx){
            movedx = true;
            moveLegInternal(Math.min(Math.abs(deltax), 1f) * Mathf.sign(deltax), 0f, true);

            if(Math.abs(deltax) >= 1f){
                deltax -= Mathf.sign(deltax);
            }else{
                deltax = 0f;
            }
        }

        boolean movedy = false;
        while(Math.abs(deltay) > 0 || !movedy){
            movedy = true;
            moveLegInternal(0f, Math.min(Math.abs(deltay), 1f) * Mathf.sign(deltay), false);

            if(Math.abs(deltay) >= 1f){
                deltay -= Mathf.sign(deltay);
            }else{
                deltay = 0f;
            }
        }
    }

    private void moveLegInternal(float deltax, float deltay, boolean xAxis){
        getHitboxTile(legRect);
        getHitboxTile(legRect2);
        legRect.x += deltax;
        legRect.y += deltay;

        int r = Math.max((int)Math.ceil(Math.max(legRect.width, legRect.height) / tilesize), 1);
        int tilex = Mathf.scl2(legRect.x + legRect.width / 2f, tilesize);
        int tiley = Mathf.scl2(legRect.y + legRect.height / 2f, tilesize);

        for(int dx = -r; dx <= r; dx++){
            for(int dy = -r; dy <= r; dy++){
                int wx = dx + tilex, wy = dy + tiley;
                Tile tile = world.tile(wx, wy);
                if(legSolid(tile)){
                    legTmp.setSize(tilesize).setCenter(wx * tilesize, wy * tilesize);

                    if(legTmp.overlaps(legRect)){
                        Vector2 v = Physics.overlap(legRect, legTmp, xAxis);
                        legRect.x += v.x;
                        legRect.y += v.y;
                    }
                }
            }
        }

        x += legRect.x - legRect2.x;
        y += legRect.y - legRect2.y;
    }

    private static boolean legSolid(Tile tile){
        return tile == null || tile.legSolid();
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        writeSave(stream, false);
    }

    @Override
    public void readSave(DataInput stream) throws IOException{
        readSaveBase(stream);
    }

    /** Reads only the shared unit body; used by subclasses with their own save layout. */
    public void readSaveBase(DataInput stream) throws IOException{
        byte team = stream.readByte();
        boolean dead = stream.readBoolean();
        float x = stream.readFloat();
        float y = stream.readFloat();
        byte xv = stream.readByte();
        byte yv = stream.readByte();
        float rotation = stream.readShort() / 2f;
        int health = stream.readShort();

        this.status.readSave(stream);
        this.inventory.readSave(stream);
        this.dead = dead;
        this.team = Team.all[team];
        this.health = health;
        this.x = x;
        this.y = y;
        this.velocity.set(xv / velocityPercision, yv / velocityPercision);
        this.rotation = rotation;
    }

    public void writeSave(DataOutput stream, boolean net) throws IOException{
        stream.writeByte(team.ordinal());
        stream.writeBoolean(isDead());
        stream.writeFloat(net ? interpolator.target.x : x);
        stream.writeFloat(net ? interpolator.target.y : y);
        stream.writeByte((byte) (Mathf.clamp(velocity.x, -maxAbsVelocity, maxAbsVelocity) * velocityPercision));
        stream.writeByte((byte) (Mathf.clamp(velocity.y, -maxAbsVelocity, maxAbsVelocity) * velocityPercision));
        stream.writeShort((short) (rotation * 2));
        stream.writeShort((short) health);
        status.writeSave(stream);
        inventory.writeSave(stream);
    }

    public float calculateDamage(float amount){
        return amount * Mathf.clamp(1f - getArmor() / 100f * status.getArmorMultiplier());
    }

    public float getDamageMultipler(){
        return status.getDamageMultiplier();
    }

    public boolean hasEffect(StatusEffect effect){
        return status.hasEffect(effect);
    }

    public void avoidOthers(float scaling){
        getHitbox(queryRect);
        queryRect.setSize(queryRect.getWidth() * scaling);

        Units.getNearby(queryRect, avoidConsumer);
    }

    /** Applies the separation impulse for one nearby unit; see {@link #avoidOthers(float)}. */
    private void avoidOther(Unit t){
        if(t == this || t.getCarrier() == this || getCarrier() == t || t.isFlying() != isFlying()) return;

        float dx = x - t.getX(), dy = y - t.getY();
        float dstSq = dx * dx + dy * dy;
        float size = queryRect.getWidth();

        //the tree returns false positives; skip them (and exact overlaps) before the sqrt
        if(dstSq >= size * size || dstSq < 0.0001f) return;

        float dst = (float)Math.sqrt(dstSq);
        moveVector.set(dx, dy).setLength(1f - (dst / size));
        applyImpulse(moveVector.x, moveVector.y);
    }

    public TileEntity getClosestCore(){
        TeamData data = state.teams.get(team);

        Tile tile = Geometry.findClosest(x, y, data.cores);
        if(tile == null){
            return null;
        }else{
            return tile.entity;
        }
    }

    public Floor getFloorOn(){
        Tile tile = world.tileWorld(x, y);
        return tile == null ? (Floor) Blocks.air : tile.floor();
    }

    @Override
    public boolean isValid(){
        return !isDead() && isAdded();
    }

    /**Updates velocity and status effects.*/
    public void updateVelocityStatus(){
        if(isCarried()){ //carried units do not take into account velocity normally
            set(carrier.getX(), carrier.getY());
            velocity.set(carrier.getVelocity());
            return;
        }

        //one tile lookup for both the floor and the block underneath
        Tile tile = world.tileWorld(x, y);
        Floor floor = tile == null ? (Floor) Blocks.air : tile.floor();

        status.update(this);

        velocity.limit(getMaxVelocity()).scl(1f + (status.getSpeedMultiplier()-1f) * Timers.delta());

        if(isFlying()){
            x += velocity.x * Timers.delta();
            y += velocity.y * Timers.delta();
        }else{
            boolean onLiquid = floor.isLiquid;

            if(tile != null){
                tile.block().unitOn(tile, this);
                if(tile.block() != Blocks.air){
                    onLiquid = false;
                }

                //on slope
                if(tile.getElevation() == -1){
                    velocity.scl(0.7f);
                }
            }

            if(onLiquid && velocity.len() > 0.4f && Mathf.chance((velocity.len() * floor.speedMultiplier) * 0.06f * Timers.delta())){
                Effects.effect(floor.walkEffect, floor.liquidColor, x, y);
            }

            if(onLiquid){
                status.handleApply(this, floor.status, floor.statusIntensity);

                if(floor.damageTaken > 0f){
                    damagePeriodic(floor.damageTaken);
                }
            }

            if(onLiquid && floor.drownTime > 0){
                drownTime += Timers.delta() * 1f / floor.drownTime;
                if(Mathf.chance(Timers.delta() * 0.05f)){
                    Effects.effect(floor.drownUpdateEffect, floor.liquidColor, x, y);
                }
            }else{
                drownTime = Mathf.lerpDelta(drownTime, 0f, 0.03f);
            }

            drownTime = Mathf.clamp(drownTime);

            if(drownTime >= 0.999f && !Net.client()){
                damage(health + 1);
            }

            float px = x, py = y;
            float floorSpeed = getFloorMultiplier() == 1f ? floor.speedMultiplier : (float)Math.pow(floor.speedMultiplier, getFloorMultiplier());
            floorSpeed *= terrainSpeedMultiplier;
            move(velocity.x * floorSpeed * Timers.delta(), velocity.y * floorSpeed * Timers.delta());
            if(Math.abs(px - x) <= 0.0001f) velocity.x = 0f;
            if(Math.abs(py - y) <= 0.0001f) velocity.y = 0f;
        }

        velocity.scl(Mathf.clamp(1f - getDrag() * (isFlying() ? 1f : floor.dragMultiplier) * Timers.delta()));
    }

    public void applyEffect(StatusEffect effect, float intensity){
        if(dead || Net.client()) return; //effects are synced and thus not applied through clients
        status.handleApply(this, effect, intensity);
    }

    public void damagePeriodic(float amount){
        damage(amount * Timers.delta(), hitTime <= -20 + hitDuration);
    }

    public void damage(float amount, boolean withEffect){
        float pre = hitTime;

        damage(amount);

        if(!withEffect){
            hitTime = pre;
        }
    }

    public void drawUnder(){
    }

    public void drawOver(){
    }

    public void drawStats(){
        float scale = getCellScale();
        TextureRegion cell = getPowerCellRegion();

        Draw.color(Color.BLACK, team.color, healthf() + Mathf.absin(Timers.time(), healthf()*5f, 1f - healthf()));
        Draw.alpha(hitTime);
        Draw.rect(cell, x, y, cell.getRegionWidth() * scale, cell.getRegionHeight() * scale, rotation - 90);
        Draw.color();
    }

    /** Scale applied to the power cell; overridden by units with pulsing biomass cells. */
    public float getCellScale(){
        return 1f;
    }

    public void drawLight(){
    }

    public TextureRegion getPowerCellRegion(){
        return Draw.region("power-cell");
    }

    public void drawAll(){
        if(!isDead()){
            draw();
            drawStats();
        }
    }

    public void drawShadow(float offsetX, float offsetY){
        Draw.rect(getIconRegion(), x + offsetX, y + offsetY, rotation - 90);
    }

    public void drawView(){
        Fill.circle(x, y, getViewDistance());
    }

    public float getViewDistance(){
        return 135f;
    }

    public abstract TextureRegion getIconRegion();

    public abstract Weapon getWeapon();

    public abstract int getItemCapacity();

    public abstract float getArmor();

    public abstract float getMass();

    public abstract boolean isFlying();

    public abstract float getSize();

    public boolean isHealer(){
        return false;
    }
}
