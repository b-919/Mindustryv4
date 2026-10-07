package io.anuke.mindustry.entities;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.ai.MassAI;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.effect.ScorchDecal;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.mindustry.entities.units.types.BlockDefenseDrone;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.entities.trait.Entity;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Edges;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.defense.Wall;
import io.anuke.mindustry.world.consumers.Consume;
import io.anuke.mindustry.world.modules.ConsumeModule;
import io.anuke.mindustry.world.modules.ItemModule;
import io.anuke.mindustry.world.modules.LiquidModule;
import io.anuke.mindustry.world.modules.PowerModule;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.entities.impl.BaseEntity;
import io.anuke.ucore.entities.trait.HealthTrait;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Timer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.tileGroup;
import static io.anuke.mindustry.Vars.world;

public class TileEntity extends BaseEntity implements TargetTrait, HealthTrait{
    public static final float timeToSleep = 60f * 4; //4 seconds to fall asleep
    private static final ObjectSet<Tile> tmpTiles = new ObjectSet<>();
    /**This value is only used for debugging.*/
    public static int sleepingEntities = 0;

    public Tile tile;
    public Timer timer;
    public float health;
    public float timeScale = 1f, timeScaleDuration;
    public long ambientSoundId = -1L;
    public boolean ambientSoundEnabled = true;
    public float ambientSoundFade = 0f;

    public PowerModule power;
    public ItemModule items;
    public LiquidModule liquids;
    public ConsumeModule cons;

    /**Light overrides for this block instance; null/negative values use the block type's values instead.*/
    public Boolean emitLight = null;
    public float lightRadius = -1f;
    public float lightOpacity = -1f;
    public Color lightColor = null;
    /**Layer light overrides for this block instance; null/negative values use the block type's values instead.*/
    public Boolean layerLight = null;
    public float layerLightRadius = -1f;
    public float layerLightOpacity = -1f;
    public Color layerLightColor = null;
    /*public Sound ambientSound;
    public String ambientSoundName;
    public float ambientSoundVolume = 1f;*/


    /**List of (cached) tiles with entities in proximity, used for outputting to*/
    private Array<Tile> proximity = new Array<>(8);
    private boolean dead = false;
    private boolean sleeping;
    private float sleepTime;

    public Entity lastDamager;
    public int defenseDronesCount;

    @Remote(called = Loc.server)
    public static void onTileDamage(Tile tile, float health){
        if(tile.entity != null){
            tile.entity.health = health;
        }
    }
    /*public void setAmbientSound(String name){
        ambientSoundName = name;
        ambientSound = Sounds.get(name);
    }*/

    @Remote(called = Loc.server)
    public static void onTileDestroyed(Tile tile){
        if(tile.entity == null) return;
        tile.entity.onDeath();
    }

    /**Sets this tile entity data to this tile, and adds it if necessary.*/
    public TileEntity init(Tile tile, boolean added){
        this.tile = tile;
        x = tile.drawx();
        y = tile.drawy();

        health = tile.block().health;

        timer = new Timer(tile.block().timers);

        if(added){
            add();
        }

        return this;
    }

    /**Scaled delta.*/
    public float delta(){
        return Timers.delta() * timeScale;
    }

    /**Call when nothing is happening to the entity. This increments the internal sleep timer.*/
    public void sleep(){
        sleepTime += Timers.delta();
        if(!sleeping && sleepTime >= timeToSleep){
            remove();
            sleeping = true;
            sleepingEntities++;
        }
    }

    /**Call when this entity is updating. This wakes it up.*/
    public void noSleep(){
        sleepTime = 0f;
        if(sleeping){
            add();
            sleeping = false;
            sleepingEntities--;
        }
    }

    public boolean isSleeping(){
        return sleeping;
    }

    public boolean isDead(){
        return dead || tile.entity != this;
    }

    public void write(DataOutput stream) throws IOException{}

    public void writeConfig(DataOutput stream) throws IOException{}

    public void read(DataInput stream) throws IOException{}

    public void readConfig(DataInput stream) throws IOException{}

    /** Returns the configuration object for schematic serialization, or null if no config. */
    public Object config(){
        return null;
    }

    /** Applies a configuration object after schematic placement. */
    public void configured(Object config){
    }

    public boolean collide(Bullet other){
        return true;
    }

    public void collision(Bullet other){
        lastDamager = other.getOwner();
        tile.block().handleBulletHit(this, other);
    }

    public void kill(){
        Call.onTileDestroyed(tile);
    }

    public void damage(float damage){
        if(dead) return;

        float preHealth = health;

        Call.onTileDamage(tile, health - tile.block().handleDamage(tile, damage));

        if(health <= 0){
            Call.onTileDestroyed(tile);
        }else{//spawns units/drones when is damaged
            if(preHealth >= maxHealth() - 0.00001f && health < maxHealth()){
                world.indexer.notifyTileDamaged(this);
            }

           if(tile.getTeam() == Team.themass && !Net.client()){
                MassAI.onDamage();

                if(Mathf.chance(0.4)){
                    boolean air = lastDamager instanceof Unit && ((Unit) lastDamager).isFlying();
                    MassAI.trySpawnTurret(true, air, lastDamager != null ? lastDamager.getX() : tile.worldx(), lastDamager != null ? lastDamager.getY() : tile.worldy());
                }
            }

            if(tile.block().defenseDrones && !Net.client()){
                for(int i = defenseDronesCount; i < tile.block().maxDefenseDrones; i++){
                    BlockDefenseDrone drone = (BlockDefenseDrone) tile.block().defenseDroneType.create(tile.getTeam());
                    drone.leader = this;
                    drone.set(tile.worldx() + Mathf.range(4f), tile.worldy() + Mathf.range(4f));
                    drone.add();
                    defenseDronesCount++;
                }
            }
        }
    }

    public Tile getTile(){
        return tile;
    }

    public boolean consumed(Class<? extends Consume> type){
        return tile.block().consumes.get(type).valid(tile.block(), this);
    }

    public void removeFromProximity(){
        tile.block().onProximityRemoved(tile);

        GridPoint2[] nearby = Edges.getEdges(tile.block().size);
        for(GridPoint2 point : nearby){
            Tile other = world.tile(tile.x + point.x, tile.y + point.y);
            //remove this tile from all nearby tile's proximities
            if(other != null){
                other = other.target();
                other.block().onProximityUpdate(other);
            }
            if(other != null && other.entity != null){
                other.entity.proximity.removeValue(tile, true);
            }
        }
    }

    public void updateProximity(){
        tmpTiles.clear();
        proximity.clear();

        GridPoint2[] nearby = Edges.getEdges(tile.block().size);
        for(GridPoint2 point : nearby){
            Tile other = world.tile(tile.x + point.x, tile.y + point.y);

            if(other == null) continue;
            other = other.target();
            if(other.entity == null || other.getTeamID() != tile.getTeamID()) continue;

            other.block().onProximityUpdate(other);

            tmpTiles.add(other);

            //add this tile to proximity of nearby tiles
            if(!other.entity.proximity.contains(tile, true)){
                other.entity.proximity.add(tile);
            }
        }

        //using a set to prevent duplicates
        for(Tile tile : tmpTiles){
            proximity.add(tile);
        }

        tile.block().onProximityAdded(tile);
        tile.block().onProximityUpdate(tile);
    }

    public Array<Tile> proximity(){
        return proximity;
    }

    @Override
    public void health(float health){
        this.health = health;
    }

    @Override
    public float health(){
        return health;
    }

    @Override
    public float maxHealth(){
        return tile.block().health;
    }

    @Override
    public void setDead(boolean dead){
        this.dead = dead;
    }

    @Override
    public void onDeath(){
        if(!dead){
            dead = true;
            Block block = tile.block();

            if(block.living){
                ScorchDecal.create(x, y, Color.valueOf("2b0000"));
            }

            block.onDestroyed(tile);
            world.removeBlock(tile);
            block.afterDestroyed(tile, this);
            remove();
        }
    }

    @Override
    public Team getTeam(){
        return tile.getTeam();
    }

    @Override
    public Vector2 getVelocity(){
        return Vector2.Zero;
    }

    @Override
    public void update(){
        //TODO better smoke effect, this one is awful
        if(health != 0 && health < tile.block().health && !(tile.block() instanceof Wall) &&
                Mathf.chance(0.009f * Timers.delta() * (1f - health / tile.block().health))){

            Effects.effect(Fx.smoke, x + Mathf.range(4), y + Mathf.range(4));
        }

        timeScaleDuration -= Timers.delta();
        if(timeScaleDuration <= 0f || !tile.block().canOverdrive){
            timeScale = 1f;
        }

        if(health <= 0){
            onDeath();
        }
        Block previous = tile.block();
        tile.block().update(tile);
        if(tile.block() == previous && cons != null){
            cons.update(this);
        }
    }

    @Override
    public EntityGroup targetGroup(){
        return tileGroup;
    }

    @Override
    public void removed(){
        if(tile != null){
            tile.block().stopAmbientSound(tile);
        }
    }
}
