package io.anuke.mindustry.ai.mass;

import io.anuke.mindustry.world.Tile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static io.anuke.mindustry.Vars.world;

class PathTile{
    final Tile tile;
    final int rotation;

    PathTile(Tile tile, int rotation){
        this.tile = tile;
        this.rotation = rotation;
    }

    void write(DataOutputStream stream) throws IOException{
        stream.writeLong(tile.packedPosition());
        stream.writeInt(rotation);
    }

    static PathTile read(DataInputStream stream) throws IOException{
        return new PathTile(world.tile(stream.readLong()), stream.readInt());
    }
}
