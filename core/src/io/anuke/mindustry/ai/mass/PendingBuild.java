package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.Vars;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.type.AmmoEntry;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.defense.turrets.ItemTurret;
import io.anuke.mindustry.world.blocks.defense.turrets.Turret.TurretEntity;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;

/** A delayed block placement, used for turrets and the veins */
class PendingBuild{
    Tile tile;
    Block block;
    Team team;
    int rotation;
    float delay;
    float timer;
    boolean fromDamage;
    float targetX, targetY;

    PendingBuild(Tile tile, Block block, Team team, int rotation, float delay){
        this(tile, block, team, rotation, delay, false, 0, 0);
    }

    PendingBuild(Tile tile, Block block, Team team, int rotation, float delay, boolean fromDamage, float targetX, float targetY){
        this.tile = tile;
        this.block = block;
        this.team = team;
        this.rotation = rotation;
        this.delay = delay;
        this.fromDamage = fromDamage;
        this.targetX = targetX;
        this.targetY = targetY;
    }

    void write(DataOutputStream stream) throws IOException{
        stream.writeLong(tile == null ? -1L : tile.packedPosition());
        stream.writeInt(block == null ? -1 : block.id);
        stream.writeInt(team == null ? -1 : team.ordinal());
        stream.writeInt(rotation);
        stream.writeFloat(delay);
        stream.writeFloat(timer);
        stream.writeBoolean(fromDamage);
        stream.writeFloat(targetX);
        stream.writeFloat(targetY);
    }

    static PendingBuild read(DataInputStream stream) throws IOException{
        long tilePos = stream.readLong();
        Tile tile = tilePos == -1L ? null : world.tile(tilePos);
        int blockId = stream.readInt();
        Block block = blockId == -1 ? null : Vars.content.block(blockId);
        int teamId = stream.readInt();
        Team team = teamId == -1 ? null : Team.all[teamId];
        int rotation = stream.readInt();
        float delay = stream.readFloat();
        float timer = stream.readFloat();
        boolean fromDamage = stream.readBoolean();
        float targetX = stream.readFloat();
        float targetY = stream.readFloat();

        PendingBuild build = new PendingBuild(tile, block, team, rotation, delay, fromDamage, targetX, targetY);
        build.timer = timer;
        return build;
    }

    void place(){
        if(tile == null) return;
        world.setBlock(tile, block, team);
        if(rotation != 0) tile.setRotation((byte) rotation);

        if(tile.entity instanceof TurretEntity){
            TurretEntity entity = (TurretEntity) tile.entity;
            if(fromDamage){
                entity.rotation = tile.angleTo(targetX, targetY);
            }else{
                Tile enemyCore = MassUtil.nearestEnemyCore(tile, team);
                if(enemyCore != null) entity.rotation = tile.angleTo(enemyCore);
            }

            if(block instanceof ItemTurret){
                ItemTurret it = (ItemTurret) block;
                AmmoType[] types = it.getAmmoTypes();
                if(types != null && types.length > 0){
                    AmmoType type = types[0];
                    entity.ammo.add(new AmmoEntry(type, 20));
                }
            }

            MassDefense.invalidateTurretCount();
        }
    }
}
