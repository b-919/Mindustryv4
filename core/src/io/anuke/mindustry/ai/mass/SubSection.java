package io.anuke.mindustry.ai.mass;

import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.blocks.DistributionBlocks;
import io.anuke.mindustry.content.blocks.ProductionBlocks;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;

/** A small drill/generator sub-build grown from a line toward a target tile.*/
class SubSection{
    Tile startTile;
    final Tile targetTile;
    final Block targetBlock;
    final Item targetOre;
    final Array<Tile> path = new Array<>();
    int progress = 0;
    float timer = 0;
    float stuckTimer = 0;
    boolean drillPlaced = false;
    boolean failed = false;
    boolean destroying = false;

    SubSection(Tile startTile, Tile targetTile, Item targetOre, Array<Tile> path){
        this(startTile, targetTile, ProductionBlocks.biomassBulb, targetOre, path);
    }

    SubSection(Tile startTile, Tile targetTile, Block targetBlock, Array<Tile> path){
        this(startTile, targetTile, targetBlock, null, path);
    }

    SubSection(Tile startTile, Tile targetTile, Block targetBlock, Item targetOre, Array<Tile> path){
        this.startTile = startTile;
        this.targetTile = targetTile;
        this.targetBlock = targetBlock;
        this.targetOre = targetOre;
        this.path.clear();
        this.path.addAll(path);
    }

    void update(){
        if(failed) return;

        if(destroying){
            if(targetTile.block() == Blocks.air || targetTile.entity == null || targetTile.entity.isDead()){
                failed = true;
                return;
            }
            targetTile.entity.damage(20000f);
            return;
        }

        if(drillPlaced){
            if(targetTile.block() != targetBlock || targetTile.getTeam() != Team.themass){
                if(targetOre != null) MassEconomy.boostOre(targetOre);
                failed = true;
                return;
            }

            // check if it's stuck or (full capacity, biomass generator is considered stuck if capacity = 2)
            if(targetTile.entity != null && targetTile.entity.items.total() >= targetTile.block().itemCapacity){
                stuckTimer += Timers.delta();
                if(stuckTimer >= MassAIConfig.DRILL_STUCK_TIME){ // 15 minutes
                    // try to reconnect
                    Array<Tile> newPath = MassBuilder.findPathToAnyLine(targetTile);
                    if(newPath != null && newPath.size > 1){
                        this.startTile = newPath.get(0);
                        path.clear();
                        for(int k = 1; k < newPath.size; k++){
                            path.add(newPath.get(k));
                        }
                        progress = 0;
                        drillPlaced = false;
                        stuckTimer = 0;
                    }else{
                        // FAILED to reconnect -> destroy
                        destroying = true;
                        if(targetOre != null) MassEconomy.boostOre(targetOre);
                    }
                }
            }else{
                stuckTimer = 0;
            }
            return;
        }

        timer += Timers.delta();
        if(timer >= MassAIConfig.LINE_ACTION_INTERVAL){
            timer = 0;
            if(progress < path.size){
                Tile next = path.get(progress);
                if(MassUtil.isNearEnemyCore(next)){
                    failed = true;
                    return;
                }
                if(next.block() == Blocks.air || (next.getTeam() == Team.themass && next.block() == DistributionBlocks.veins)){
                    Tile prev = (progress == 0) ? startTile : path.get(progress - 1);
                    int rotation = next.relativeTo(prev.x, prev.y);

                    next.setBlock(DistributionBlocks.veins, Team.themass, rotation);
                    MassBuilder.markLineTile(next);
                    progress++;
                }else{
                    failed = true;
                }
            }else{
                if(targetBlock == ProductionBlocks.biomassBulb){
                    if(MassUtil.isValid2x2(targetTile.x, targetTile.y, targetOre)){
                        Vars.world.setBlock(targetTile, ProductionBlocks.biomassBulb, Team.themass);
                        drillPlaced = true;
                    }else if(targetTile.block() == ProductionBlocks.biomassBulb && targetTile.getTeam() == Team.themass){
                        drillPlaced = true; // reconnect the drill
                    }else{
                        failed = true;
                    }
                }else{
                    Vars.world.setBlock(targetTile, targetBlock, Team.themass);
                    drillPlaced = true;
                }
            }
        }
    }

    void write(DataOutputStream stream) throws IOException{
        stream.writeLong(startTile.packedPosition());
        stream.writeLong(targetTile.packedPosition());
        stream.writeInt(targetBlock == null ? -1 : targetBlock.id);
        stream.writeInt(targetOre == null ? -1 : targetOre.id);
        stream.writeInt(path.size);
        for(Tile t : path){
            stream.writeLong(t.packedPosition());
        }
        stream.writeInt(progress);
        stream.writeFloat(timer);
        stream.writeFloat(stuckTimer);
        stream.writeBoolean(drillPlaced);
        stream.writeBoolean(failed);
        stream.writeBoolean(destroying);
    }

    static SubSection read(DataInputStream stream) throws IOException{
        Tile startTile = world.tile(stream.readLong());
        Tile targetTile = world.tile(stream.readLong());
        int blockId = stream.readInt();
        Block targetBlock = blockId == -1 ? null : Vars.content.block(blockId);
        int oreId = stream.readInt();
        Item targetOre = oreId == -1 ? null : Vars.content.item(oreId);
        int pathSize = stream.readInt();
        Array<Tile> path = new Array<>();
        for(int i = 0; i < pathSize; i++){
            path.add(world.tile(stream.readLong()));
        }
        SubSection sub = new SubSection(startTile, targetTile, targetBlock, targetOre, path);
        sub.progress = stream.readInt();
        sub.timer = stream.readFloat();
        sub.stuckTimer = stream.readFloat();
        sub.drillPlaced = stream.readBoolean();
        sub.failed = stream.readBoolean();
        sub.destroying = stream.readBoolean();
        return sub;
    }
}