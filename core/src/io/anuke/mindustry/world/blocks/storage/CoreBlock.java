package io.anuke.mindustry.world.blocks.storage;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.content.fx.CoreFx;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.traits.SpawnerTrait;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.maps.TutorialSector;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.BarType;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.EnumSet;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class CoreBlock extends StorageBlock{
    protected float droneRespawnDuration = 60 * 6;
    protected UnitType droneType = UnitTypes.spirit;

    protected TextureRegion openRegion;
    protected TextureRegion topRegion;
    public TextureRegion thruster1, thruster2;
    public Sound buildPlayerSound;
    public String buildPlayerSoundName;
    //TODO finish someday but not here
    public float landDuration = 160f;
    public float landShake = 5f;
    public float thrusterLength = 14f / 4f;
    public String landSoundName = "explosionArtilleryShock";

    private static final float[] thrusterSizes = {0f, 0f, 0f, 0f, 0.3f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f};

    public CoreBlock(String name){
        super(name);
        hasBloom = true;

        solid = false;
        solidifes = true;
        update = true;
        size = 3;
        hasItems = true;
        itemCapacity = 2000;
        viewRange = 200f;
        flags = EnumSet.of(BlockFlag.resupplyPoint, BlockFlag.target);
        setAmbientSound("loopUnitBuilding", 0.09f);
        setBuildPlayerSound("unitCreate");

        emitLight = true;
        lightAmplification = 4f;
    }

    public void setBuildPlayerSound(String name){
        buildPlayerSoundName = name;
        buildPlayerSound = Sounds.get(name);
    }
    @Remote(called = Loc.server)
    public static void onUnitRespawn(Tile tile, Unit player){
        if(player == null || tile.entity == null) return;

        CoreEntity entity = tile.entity();
        Effects.effect(Fx.spawn, entity);
        CoreBlock block = (CoreBlock) tile.block();
        if(block != null && Vars.soundController != null && block.buildPlayerSound != null){
            Vars.soundController.at(block.buildPlayerSound, tile.drawx(), tile.drawy(), 1f, 0.2f);
        }
        entity.solid = false;
        entity.progress = 0;
        entity.currentUnit = player;
        entity.currentUnit.heal();
        entity.currentUnit.rotation = 90f;
        entity.currentUnit.setNet(tile.drawx(), tile.drawy());
        entity.currentUnit.add();
        entity.currentUnit = null;

        if(player instanceof Player){
            ((Player) player).endRespawning();
        }
    }

    @Remote(called = Loc.server)
    public static void setCoreSolid(Tile tile, boolean solid){
        if(tile == null) return;
        CoreEntity entity = tile.entity();
        if(entity != null) entity.solid = solid;
    }

    @Override
    public void onProximityUpdate(Tile tile) {
        state.teams.get(tile.getTeam()).cores.add(tile);
    }

    @Override
    public boolean canBreak(Tile tile){
        return state.teams.get(tile.getTeam()).cores.size > 1;
    }

    @Override
    public void removed(Tile tile){
        state.teams.get(tile.getTeam()).cores.remove(tile);
        if(tile.entity instanceof CoreEntity){
            ((CoreEntity) tile.entity).releaseCurrentUnit();
        }
    }

    @Override
    public void placed(Tile tile){
        state.teams.get(tile.getTeam()).cores.add(tile);
        //Core landing animation is disabled for now; see ai_audit/CORE_LANDING_ANIMATION.md.
        //startLanding(tile);
    }

    public void startLanding(Tile tile){
        if(headless || tile == null || !(tile.entity instanceof CoreEntity)) return;

        CoreEntity entity = tile.entity();
        entity.landing = 1f;
        entity.thrusterTime = 0f;
        entity.landParticleTimer = 0f;
        entity.landImpacted = false;
    }
    public static void startLandingForTeam(Team team){
        if(headless || team == null) return;

        for(Tile tile : state.teams.get(team).cores){
            if(tile.block() instanceof CoreBlock){
                ((CoreBlock)tile.block()).startLanding(tile);
            }
        }
    }

    @Override
    public void unitRemoved(Tile tile, Unit unit){
        if(!(tile.entity instanceof CoreEntity entity)) return;

        if(entity.droneID == unit.getID() ||
                (droneType != null && unit instanceof BaseUnit && ((BaseUnit) unit).getType().id == droneType.id)){
            entity.droneID = -1;
        }
        if(entity.currentUnit == unit){
            entity.currentUnit = null;
            entity.progress = 0f;
        }
    }

    @Override
    public void setBars(){
        super.setBars();

        bars.remove(BarType.inventory);
    }

    @Override
    public void load(){
        super.load();

        openRegion = Draw.region(name + "-open");
        topRegion = Draw.region(name + "-top");
        thruster1 = Draw.region(name + "-thruster1");
        thruster2 = Draw.region(name + "-thruster2");
    }

    @Override
    public void draw(Tile tile){
        CoreEntity entity = tile.entity();

        /*if(entity.landing > 0f || entity.thrusterTime > 0f){
            drawLanding(tile, entity);
            return;
        }*/

        Draw.rect(entity.solid ? Draw.region(name) : openRegion, tile.drawx(), tile.drawy());

        Draw.alpha(entity.heat);
        Draw.rect(topRegion, tile.drawx(), tile.drawy());
        Draw.color();

        if(entity.currentUnit != null){
            Unit player = entity.currentUnit;

            TextureRegion region = player.getIconRegion();

            Shaders.build.region = region;
            Shaders.build.progress = entity.progress;
            Shaders.build.color.set(Palette.accent);
            Shaders.build.time = -entity.time / 10f;

            Graphics.shader(Shaders.build, false);
            Shaders.build.apply();
            Draw.rect(region, tile.drawx(), tile.drawy());
            Graphics.shader();

            Draw.color(Palette.accent);

            Lines.lineAngleCenter(
                    tile.drawx() + Mathf.sin(entity.time, 6f, Vars.tilesize / 3f * size),
                    tile.drawy(),
                    90,
                    size * Vars.tilesize / 2f);

            Draw.reset();
        }
    }

    private void drawLanding(Tile tile, CoreEntity entity){
        float x = tile.drawx(), y = tile.drawy();
        TextureRegion base = Draw.region(name);
        float w = base.getRegionWidth(), h = base.getRegionHeight();
        float land = Mathf.clamp(entity.landing);
        float fin = 1f - land;
        float offset = Interpolation.pow3Out.apply(land) * tilesize * 8f;
        float rotation = Interpolation.pow2In.apply(land) * 135f;
        float frame = fin >= 0.25f ? 1f : fin / 0.25f;
        float strength = sampleThruster(fin);

        if(land > 0f){
            Draw.color(0f, 0f, 0f, 0.30f * land);
            float shadow = size * tilesize * (2.2f + land * 1.4f);
            Draw.rect("circle", x, y, shadow, shadow);
            Draw.reset();

            if(strength > 0.01f && thruster1 != null && thruster2 != null){
                drawLandingThrusters(x, y + offset, rotation, frame, strength, 0);
                Draw.rect(base, x, y + offset, w, h, rotation);
                drawLandingThrusters(x, y + offset, rotation, frame, strength, 1);
            }else{
                Draw.rect(base, x, y + offset, w, h, rotation);
            }

            if(topRegion != null){
                Draw.alpha(1f);
                Draw.rect(topRegion, x, y + offset, w, h, rotation);
            }
        }else{
            if(entity.thrusterTime > 0f && thruster1 != null && thruster2 != null){
                drawThrusters(x, y, entity.thrusterTime);
            }

            Draw.rect(entity.solid ? base : openRegion, x, y);
            if(topRegion != null){
                Draw.alpha(entity.heat);
                Draw.rect(topRegion, x, y);
            }
        }

        Draw.reset();
    }

    private void drawLandingThrusters(float x, float y, float rotation, float frame, float strength, int pass){
        float length = thrusterLength * (frame - 1f) - 0.25f;

        for(int i = 0; i < 4; i++){
            TextureRegion reg = i >= 2 ? thruster2 : thruster1;
            float rot = i * 90f + rotation % 90f;
            float dx = Angles.trnsx(rot, length);
            float dy = Angles.trnsy(rot, length);

            if(pass == 1){
                float nx = dy, ny = -dx;
                dx = nx;
                dy = ny;
                rot -= 90f;
                Draw.alpha((rotation % 90f) / 90f * strength);
            }else{
                Draw.alpha(strength);
            }

            Draw.rect(reg, x + dx, y + dy, rot);
        }

        Draw.alpha(1f);
    }

    private void drawThrusters(float x, float y, float frame){
        float length = thrusterLength * (frame - 1f) - 0.25f;

        for(int i = 0; i < 4; i++){
            TextureRegion reg = i >= 2 ? thruster2 : thruster1;
            Draw.rect(reg, x + Angles.trnsx(i * 90f, length), y + Angles.trnsy(i * 90f, length), i * 90f);
        }
    }

    private static float sampleThruster(float progress){
        float f = Mathf.clamp(progress, 0f, 1f) * (thrusterSizes.length - 1);
        int i = (int)f;
        int j = Math.min(i + 1, thrusterSizes.length - 1);
        return Mathf.lerp(thrusterSizes[i], thrusterSizes[j], f - i);
    }

    @Override
    public void drawBloom(Tile tile){
        CoreEntity entity = tile.entity();

        if(entity.currentUnit != null){
            float progress = entity.progress;

            Draw.color(Palette.accent);
            Draw.alpha(progress * 0.15f);
            Draw.rect("circle", tile.drawx(), tile.drawy(), size * tilesize * 1.2f, size * tilesize * 1.2f);

            Draw.color(Palette.accent);
            Draw.alpha(progress * 0.5f);
            Lines.stroke(2f);
            Lines.lineAngleCenter(
                    tile.drawx() + Mathf.sin(entity.time, 6f, Vars.tilesize / 3f * size),
                    tile.drawy(),
                    90,
                    size * Vars.tilesize / 2f);

            Draw.color(Color.WHITE);
            Draw.alpha(progress * 0.3f);
            Lines.stroke(1f);
            Lines.lineAngleCenter(
                    tile.drawx() + Mathf.sin(entity.time, 6f, Vars.tilesize / 3f * size),
                    tile.drawy(),
                    90,
                    size * Vars.tilesize / 2f);

            Draw.reset();
        }
    }

    @Override
    public boolean isSolidFor(Tile tile){
        CoreEntity entity = tile.entity();

        return entity.solid;
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        if(Net.server() || !Net.active()) super.handleItem(item, tile, source);
    }

    @Override
    public void update(Tile tile){
        CoreEntity entity = tile.entity();

        //updateLanding(tile, entity);

        if(!entity.solid && !Units.anyEntities(tile)){
            Call.setCoreSolid(tile, true);
        }

        if(entity.currentUnit != null){
            if(!entity.currentUnit.isDead()){
                entity.ambientSoundEnabled = false;
                entity.currentUnit = null;
                return;
            }
            entity.heat = Mathf.lerpDelta(entity.heat, 1f, 0.1f);
            entity.time += entity.delta();
            entity.ambientSoundEnabled = true;
            entity.progress += 1f / (entity.currentUnit instanceof Player ? state.mode.respawnTime : droneRespawnDuration) * entity.delta();

            if(entity.progress >= 1f && (Net.server() || !Net.active())){
                entity.progress = 0f;
                Call.onUnitRespawn(tile, entity.currentUnit);
                entity.ambientSoundEnabled = false;
            }
        }else if(!netServer.isWaitingForPlayers()){
            entity.warmup += Timers.delta();

            if(droneType != null && entity.solid && entity.warmup > 60f && !Net.client() && entity.getDrone() == null){
                if(!TutorialSector.supressDrone()){
                    BaseUnit unit = droneType.create(tile.getTeam());
                    unit.setSpawner(tile);
                    unit.setDead(true);
                    unit.add();

                    useContent(tile, droneType);

                    entity.droneID = unit.id;
                    entity.ambientSoundEnabled = true;
                }
            }

            entity.heat = Mathf.lerpDelta(entity.heat, 0f, 0.1f);
        }
    }

    private void updateLanding(Tile tile, CoreEntity entity){
        if(entity.landing > 0f){
            entity.landing = Mathf.clamp(entity.landing - entity.delta() / landDuration, 0f, 1f);
            entity.landParticleTimer += entity.delta() * (1f + (1f - entity.landing) * 3f);

            if(entity.landParticleTimer >= 1f){
                entity.landParticleTimer = 0f;
                spawnLandingDust(tile, 1);
            }

            if(entity.landing <= 0f && !entity.landImpacted){
                entity.landImpacted = true;
                landImpact(tile);
            }
        }

        if(entity.thrusterTime > 0f){
            entity.thrusterTime = Mathf.clamp(entity.thrusterTime - entity.delta() / 90f, 0f, 1f);
        }
    }

    private void landImpact(Tile tile){
        float x = tile.drawx(), y = tile.drawy();

        Effects.effect(CoreFx.coreLand, x, y, size);
        Effects.shake(landShake, landShake, x, y);
        spawnLandingDust(tile, 8);

        if(tile.entity instanceof CoreEntity){
            ((CoreEntity)tile.entity).thrusterTime = 1f;
        }

        if(Vars.soundController != null){
            Sound sound = Sounds.get(landSoundName);
            if(sound != null){
                Vars.soundController.at(sound, x, y, 1.1f, 0.8f);
            }
        }
    }

    private void spawnLandingDust(Tile tile, int count){
        Color color = tile.floor().minimapColor;
        if(color.a < 0.01f){
            color = Palette.lightishGray;
        }

        for(int i = 0; i < count; i++){
            float x = tile.drawx() + Mathf.range(size * tilesize / 2f);
            float y = tile.drawy() + Mathf.range(size * tilesize / 2f);
            Effects.effect(CoreFx.coreLandDust, color, x, y, Mathf.random(360f));
        }
    }

    @Override
    public TileEntity newEntity(){
        return new CoreEntity();
    }

    public class CoreEntity extends StorageEntity implements SpawnerTrait{
        public Unit currentUnit;
        int droneID = -1;
        boolean solid = true;
        float warmup;
        float progress;
        float time;
        float heat;
        float landing;
        float thrusterTime;
        float landParticleTimer;
        boolean landImpacted;

        @Override
        public void updateSpawning(Unit unit){
            if(!netServer.isWaitingForPlayers() && currentUnit == null){
                currentUnit = unit;
                progress = 0f;
                unit.set(tile.drawx(), tile.drawy());
            }
        }

        @Override
        public float getSpawnProgress(){
            return progress;
        }

        BaseUnit getDrone(){
            if(droneType == null) return null;

            BaseUnit drone = unitGroups[tile.getTeamID()].getByID(droneID);

            if(drone != null && drone.getType().id == droneType.id && isSpawnerOf(drone)){
                return drone;
            }

            for(BaseUnit unit : unitGroups[tile.getTeamID()].all()){
                if(unit.getType().id == droneType.id && isSpawnerOf(unit)){
                    droneID = unit.id;
                    return unit;
                }
            }

            droneID = -1;
            return null;
        }

        private boolean isSpawnerOf(BaseUnit unit){
            Tile spawner = unit.getSpawner();
            return spawner != null && spawner.packedPosition() == tile.packedPosition();
        }

        void releaseCurrentUnit(){
            if(droneType != null){
                BaseUnit drone = getDrone();
                if(drone != null){
                    drone.remove();
                    if(currentUnit == drone){
                        currentUnit = null;
                    }
                }
                droneID = -1;
            }
            if(currentUnit != null){
                if(currentUnit instanceof Player){
                    ((Player) currentUnit).endRespawning();
                }else{
                    currentUnit.remove();
                }
                currentUnit = null;
            }

            progress = 0f;
            time = 0f;
            heat = 0f;
            ambientSoundEnabled = false;
        }

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeBoolean(solid);
            stream.writeInt(droneID);
        }

        @Override
        public void read(DataInput stream) throws IOException{
            solid = stream.readBoolean();
            droneID = stream.readInt();
        }
    }
}
