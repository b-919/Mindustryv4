package io.anuke.mindustry.ai;

import io.anuke.mindustry.world.Tile;

/** Movement profile used by hierarchical and fine-grained pathfinding. */
public enum PathMode{
    /** Standard ground movement: solid blocks and cliffs block, liquids are avoided. */
    ground,
    /** Legged movement: constructed/destructible blocks are stepped over; static terrain still blocks. */
    legs,
    /** Special leg movement: all solids are passable; liquids are still avoided by coarse pathing. */
    ignoreSolids;

    public boolean passable(Tile tile){
        if(tile == null || tile.floor().isLiquid) return false;

        return switch (this) {
            case ignoreSolids -> true;
            case legs -> !tile.legSolid();
            default -> !tile.solid();
        };
    }
}
