package io.anuke.mindustry.entities.traits;


import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.trait.Entity;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.graphics.Shapes;
import io.anuke.ucore.util.*;


import static io.anuke.mindustry.Vars.*;

public interface MinerTrait extends Entity{
    /** Returns the range at which this miner can mine blocks.*/
    float mineDistance = 70f;

    default boolean isMining(){
        return getMineTile() != null;
    }

    /** Returns the tile this builder is currently mining. */
    Tile getMineTile();

    /** Sets the tile this builder is currently mining. */
    void setMineTile(Tile tile);

    /** Returns the mining speed of this miner. 1 = standard, 0.5 = half speed, 2 = double speed, etc. */
    float getMinePower();

    /** Returns whether or not this builder can mine a specific item type. */
    boolean canMine(Item item);

    //default void MinerUpdate

    default void updateMining(){
        Unit unit = (Unit)this;
        Tile tile = getMineTile();
        TileEntity core = unit.getClosestCore();

        if(core == null || tile.block() != Blocks.air || unit.distanceTo(tile.worldx(), tile.worldy()) > mineDistance
                || tile.floor().drops == null || !unit.inventory.canAcceptItem(tile.floor().drops.item) || !canMine(tile.floor().drops.item)){
            setMineTile(null);
        }else{
            Item item = tile.floor().drops.item;
            unit.rotation = Mathf.slerpDelta(unit.rotation, unit.angleTo(tile.worldx(), tile.worldy()), 0.4f);

            if(Mathf.chance(Timers.delta() * (0.06 - item.hardness * 0.01) * getMinePower())){

                if(unit.distanceTo(core) < mineTransferRange && core.tile.block().acceptStack(item, 1, core.tile, unit) == 1){
                    Call.transferItemTo(item, 1,
                            tile.worldx() + Mathf.range(tilesize / 2f),
                            tile.worldy() + Mathf.range(tilesize / 2f), core.tile);
                }else if(unit.inventory.canAcceptItem(item)){
                    Call.transferItemToUnit(item,
                            tile.worldx() + Mathf.range(tilesize / 2f),
                            tile.worldy() + Mathf.range(tilesize / 2f),
                            unit);
                }
            }

            if(Mathf.chance(0.06 * Timers.delta())){
                Effects.effect(BlockFx.pulverizeSmall,
                        tile.worldx() + Mathf.range(tilesize / 2f),
                        tile.worldy() + Mathf.range(tilesize / 2f), 0f, item.color);
            }
        }
    }
    default void drawMining(Unit unit){
        Tile tile = getMineTile();

        if(tile == null) return;

        float focusLen = 4f + Mathf.absin(Timers.time(), 1.1f, 0.5f);
        float swingScl = 12f, swingMag = tilesize / 8f;
        float flashScl = 0.3f;

        float px = unit.x + Angles.trnsx(unit.rotation, focusLen);
        float py = unit.y + Angles.trnsy(unit.rotation, focusLen);

        float ex = tile.worldx() + Mathf.sin(Timers.time() + 48, swingScl, swingMag);
        float ey = tile.worldy() + Mathf.sin(Timers.time() + 48, swingScl + 2f, swingMag);

        Draw.color(Color.LIGHT_GRAY, Color.WHITE, 1f - flashScl + Mathf.absin(Timers.time(), 0.5f, flashScl));
        Shapes.laser("minelaser", "minelaser-end", px, py, ex, ey);

        if(unit instanceof Player && ((Player) unit).isLocal){
            Draw.color(Palette.accent);
            Lines.poly(tile.worldx(), tile.worldy(), 4, tilesize / 2f * Mathf.sqrt2, Timers.time());
        }

        Draw.color();
    }
}
