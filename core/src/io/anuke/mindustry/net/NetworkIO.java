package io.anuke.mindustry.net;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectMap.Entry;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.GameMode;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.Teams;
import io.anuke.mindustry.game.Teams.TeamData;
import io.anuke.mindustry.game.Version;
import io.anuke.mindustry.io.SaveFileVersion;
import io.anuke.mindustry.maps.Map;
import io.anuke.mindustry.maps.MapMeta;
import io.anuke.mindustry.maps.generation.ChunkManager;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.BlockPart;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.Entities;
import io.anuke.ucore.entities.impl.BaseEntity;
import io.anuke.ucore.util.Bits;

import java.io.*;
import java.nio.ByteBuffer;

import static io.anuke.mindustry.Vars.*;

public class NetworkIO{
    /** Max chunks serialized into the initial open-world join payload (nearest spawn first); the rest is streamed via rate-limited chunk requests. */
    static final int MAX_JOIN_CHUNKS = 49;

    public static void writeWorld(Player player, OutputStream os){

        try(DataOutputStream stream = new DataOutputStream(os)){
            //--GENERAL STATE--
            stream.writeByte(state.mode.ordinal()); //gamemode
            boolean openWorld = world.isOpenWorld();
            stream.writeBoolean(openWorld); //open world flag

            stream.writeInt(state.wave); //wave
            stream.writeFloat(state.wavetime); //wave countdown
            stream.writeByte(state.enemyTeam.ordinal());

            //custom game settings from the host (game mode flags, difficulty, weather, infection, RTS AI)
            stream.writeByte(state.difficulty.ordinal());
            int customBits = 0;
            customBits |= (state.allowMassInfection ? 1 : 0) << 0;
            customBits |= (state.startWithBiomass ? 1 : 0) << 1;
            customBits |= (state.rain ? 1 : 0) << 2;
            customBits |= (state.mode.infiniteResources ? 1 : 0) << 3;
            customBits |= (state.mode.disableWaveTimer ? 1 : 0) << 4;
            customBits |= (state.mode.disableWaves ? 1 : 0) << 5;
            customBits |= (state.mode.showMission ? 1 : 0) << 6;
            customBits |= (state.mode.enemyCheat ? 1 : 0) << 7;
            stream.writeByte(customBits);
            int customBits2 = (state.mode.isPvp ? 1 : 0);
            stream.writeByte(customBits2);
            stream.writeFloat(state.darkness);
            stream.writeLong(state.rtsAIBits);
            stream.writeFloat(state.mode.enemyCoreBuildRadius);
            stream.writeFloat(state.mode.respawnTime);

            stream.writeInt(player.id);
            player.write(stream);

            if(openWorld){
                //--OPEN WORLD DATA--
                long seed = world.chunks() != null ? world.chunks().getSeed() : 0;
                float spawnX = players[0] == null ? world.width()*tilesize/2f : players[0].x;
                float spawnY = players[0] == null ? world.height()*tilesize/2f : players[0].y;
                stream.writeLong(seed);
                stream.writeFloat(spawnX);
                stream.writeFloat(spawnY);
                Array<ChunkManager.WorldChunk> loaded = new Array<>();
                for(ChunkManager.WorldChunk chunk : world.chunks().getLoadedChunks()){
                    if(chunk.tiles != null) loaded.add(chunk);
                }
                int scx = MathUtils.floor(spawnX / (ChunkManager.CHUNK_SIZE * tilesize));
                int scy = MathUtils.floor(spawnY / (ChunkManager.CHUNK_SIZE * tilesize));
                loaded.sort((a, b) -> {
                    int da = (a.cx - scx) * (a.cx - scx) + (a.cy - scy) * (a.cy - scy);
                    int db = (b.cx - scx) * (b.cx - scx) + (b.cy - scy) * (b.cy - scy);
                    return Integer.compare(da, db);
                });
                int limit = Math.min(loaded.size, MAX_JOIN_CHUNKS);
                stream.writeInt(limit);
                for(int i = 0; i < limit; i++){
                    ChunkManager.WorldChunk chunk = loaded.get(i);
                    byte[] data = world.chunks().getSaveManager().serializeChunk(chunk);
                    if(data == null) data = new byte[0];
                    stream.writeInt(chunk.cx);
                    stream.writeInt(chunk.cy);
                    stream.writeInt(data.length);
                    stream.write(data);
                }
            }else{
                //--MAP NAME / SECTOR--
                stream.writeUTF(world.getMap().name); //map name
                stream.writeInt(world.getSector() == null ? invalidSector : world.getSector().packedPosition()); //sector ID
                stream.writeInt(world.getSector() == null ? 0 : world.getSector().completedMissions);

                //write tags
                ObjectMap<String, String> tags = world.getMap().meta.tags;
                stream.writeByte(tags.size);
                for(Entry<String, String> entry : tags.entries()){
                    stream.writeUTF(entry.key);
                    stream.writeUTF(entry.value);
                }

                //--MAP DATA--

                //map size
                stream.writeShort(world.width());
                stream.writeShort(world.height());

                for(int i = 0; i < world.width() * world.height(); i++){
                    Tile tile = world.tile(i);

                    stream.writeShort(tile.getFloorID());
                    stream.writeShort(tile.getBlockID());
                    stream.writeByte(tile.getElevation());

                    if(tile.block() instanceof BlockPart){
                        stream.writeByte(tile.link);
                    }else if(tile.entity != null){
                        stream.writeByte(Bits.packByte(tile.getTeamID(), tile.getRotation())); //team + rotation
                        stream.writeShort((short) tile.entity.health); //health

                        if(tile.entity.items != null) tile.entity.items.write(stream);
                        if(tile.entity.power != null) tile.entity.power.write(stream);
                        if(tile.entity.liquids != null) tile.entity.liquids.write(stream);
                        if(tile.entity.cons != null) tile.entity.cons.write(stream);

                        tile.entity.writeConfig(stream);
                        tile.entity.write(stream);
                    }else if(tile.block() == Blocks.air){
                        int consecutives = 0;

                        for(int j = i + 1; j < world.width() * world.height() && consecutives < 255; j++){
                            Tile nextTile = world.tile(j);

                            if(nextTile.getFloorID() != tile.getFloorID() || nextTile.block() != Blocks.air || nextTile.getElevation() != tile.getElevation()){
                                break;
                            }

                            consecutives++;
                        }

                        stream.writeByte(consecutives);
                        i += consecutives;
                    }
                }

                //write visibility, length-run encoded
                for(int i = 0; i < world.width() * world.height(); i++){
                    Tile tile = world.tile(i);
                    boolean discovered = tile.discovered();

                    int consecutives = 0;

                    for(int j = i + 1; j < world.width() * world.height() && consecutives < 32767*2-1; j++){
                        Tile nextTile = world.tile(j);

                        if(nextTile.discovered() != discovered){
                            break;
                        }

                        consecutives++;
                    }

                    stream.writeBoolean(discovered);
                    stream.writeShort(consecutives);
                    i += consecutives;
                }
            }

            stream.write(Team.all.length);

            //write team data
            for(Team team : Team.all){
                TeamData data = state.teams.get(team);
                stream.writeByte(team.ordinal());

                stream.writeByte(data.enemies.size());
                for(Team enemy : data.enemies){
                    stream.writeByte(enemy.ordinal());
                }

                stream.writeByte(data.cores.size);
                for(Tile tile : data.cores){
                    stream.writeLong(tile.packedPosition());
                }
            }

            //now write a snapshot.
            if(openWorld){
                player.con.viewX = players[0] == null ? world.width()*tilesize/2f : players[0].x;
                player.con.viewY = players[0] == null ? world.height()*tilesize/2f : players[0].y;
            }else{
                player.con.viewX = world.width() * tilesize/2f;
                player.con.viewY = world.height() * tilesize/2f;
            }
            player.con.viewWidth = world.width() * tilesize;
            player.con.viewHeight = world.height() * tilesize;
            netServer.writeSnapshot(player, stream);

        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public static void loadWorld(InputStream is){

        Player player = players[0];

        try(DataInputStream stream = new DataInputStream(is)){
            Timers.clear();

            //network worlds always use the newest entity serialization format
            SaveFileVersion.currentVersion = Integer.MAX_VALUE;

            //general state
            byte mode = stream.readByte();
            boolean openWorld = stream.readBoolean();

            int wave = stream.readInt();
            float wavetime = stream.readFloat();
            state.enemyTeam = Team.all[stream.readByte()];

            state.wave = wave;
            state.wavetime = wavetime;
            state.mode = GameMode.values()[mode];
            //clear any stale local custom game settings, then restore the host's networked custom config below
            state.mode.reset();

            //custom game settings from the host
            state.difficulty = Difficulty.values()[stream.readByte()];
            int customBits = stream.readByte();
            state.allowMassInfection = (customBits & 1) != 0;
            state.startWithBiomass = (customBits & 2) != 0;
            state.rain = (customBits & 4) != 0;
            state.mode.infiniteResources = (customBits & 8) != 0;
            state.mode.disableWaveTimer = (customBits & 16) != 0;
            state.mode.disableWaves = (customBits & 32) != 0;
            state.mode.showMission = (customBits & 64) != 0;
            state.mode.enemyCheat = (customBits & 128) != 0;
            int customBits2 = stream.readByte();
            state.mode.isPvp = (customBits2 & 1) != 0;
            state.darkness = stream.readFloat();
            state.rtsAIBits = stream.readLong();
            state.mode.enemyCoreBuildRadius = stream.readFloat();
            state.mode.respawnTime = stream.readFloat();

            Entities.clear();
            int id = stream.readInt();
            player.resetNoAdd();
            player.read(stream, TimeUtils.millis());
            player.resetID(id);
            BaseEntity.checkNextId(id);
            player.add();

            world.beginMapLoad();

            float spawnX = 0f, spawnY = 0f;

            if(openWorld){
                //--OPEN WORLD LOAD--
                if(world.isOpenWorld()){
                    world.endOpenWorld();
                }

                long seed = stream.readLong();
                spawnX = stream.readFloat();
                spawnY = stream.readFloat();

                Map owMap = new Map("Open World", new MapMeta(0, new ObjectMap<>(),
                    ChunkManager.CHUNK_SIZE * (ChunkManager.RENDER_RADIUS * 2 + 1),
                    ChunkManager.CHUNK_SIZE * (ChunkManager.RENDER_RADIUS * 2 + 1), null), true, () -> null);
                world.setMap(owMap);

                world.beginOpenWorld(seed, null);
                world.chunks().setNetMode(true);

                int chunkCount = stream.readInt();
                for(int i = 0; i < chunkCount && chunkCount > 0; i++){
                    int cx = stream.readInt();
                    int cy = stream.readInt();
                    int len = stream.readInt();
                    byte[] data = new byte[len];
                    stream.readFully(data);
                    ChunkManager.WorldChunk chunk = world.chunks().getSaveManager().deserializeChunk(data);
                    if(chunk != null){
                        world.chunks().installChunk(chunk);
                    }
                }

                state.teams = new Teams();

                world.endMapLoad();

                //spawn near the host
                if(spawnX != 0f || spawnY != 0f){
                    players[0].set(spawnX, spawnY);
                    Core.camera.position.set(spawnX, spawnY, 0);
                }
            }else{
                //--MAP NAME / SECTOR--
                String map = stream.readUTF();
                int sector = stream.readInt();
                int missions = stream.readInt();

                if(sector != invalidSector){
                    world.sectors.createSector(Bits.getLeftShort(sector), Bits.getRightShort(sector));
                    world.setSector(world.sectors.get(sector));
                    world.getSector().completedMissions = missions;
                }else{
                    world.setSector(null);
                }

                ObjectMap<String, String> tags = new ObjectMap<>();

                byte tagSize = stream.readByte();
                for(int i = 0; i < tagSize; i++){
                    String key = stream.readUTF();
                    String value = stream.readUTF();
                    tags.put(key, value);
                }

                //map
                int width = stream.readShort();
                int height = stream.readShort();

                Map currentMap = new Map(map, new MapMeta(0, new ObjectMap<>(), width, height, null), true, () -> null);
                currentMap.meta.tags.clear();
                currentMap.meta.tags.putAll(tags);
                world.setMap(currentMap);
                state.darkness = Float.parseFloat(currentMap.meta.tags.get("darkness", "0"));
                if(weather != null){
                    weather.setRain(currentMap.meta.tags.get("rain", "0").equals("1"));
                }

                Tile[][] tiles = world.createTiles(width, height);

                for(int i = 0; i < width * height; i++){
                    int x = i % width, y = i / width;
                    short floorid = stream.readShort();
                    short wallid = stream.readShort();
                    byte elevation = stream.readByte();

                    Tile tile = new Tile(x, y, floorid, wallid);
                    tile.setElevation(elevation);

                    if(wallid == Blocks.blockpart.id){
                        tile.link = stream.readByte();
                    }else if(tile.entity != null){
                        byte tr = stream.readByte();
                        short health = stream.readShort();

                        byte team = Bits.getLeftByte(tr);
                        byte rotation = Bits.getRightByte(tr);

                        tile.setTeam(Team.all[team]);
                        tile.entity.health = health;
                        tile.setRotation(rotation);

                        if(tile.entity.items != null) tile.entity.items.read(stream);
                        if(tile.entity.power != null) tile.entity.power.read(stream);
                        if(tile.entity.liquids != null) tile.entity.liquids.read(stream);
                        if(tile.entity.cons != null) tile.entity.cons.read(stream);

                        tile.entity.readConfig(stream);
                        tile.entity.read(stream);
                    }else if(wallid == 0){
                        int consecutives = stream.readUnsignedByte();

                        for(int j = i + 1; j < i + 1 + consecutives; j++){
                            int newx = j % width, newy = j / width;
                            Tile newTile = new Tile(newx, newy, floorid, wallid);
                            newTile.setElevation(elevation);
                            tiles[newx][newy] = newTile;
                        }

                        i += consecutives;
                    }

                    tiles[x][y] = tile;
                }

                for(int i = 0; i < width * height; i++){
                    boolean discovered = stream.readBoolean();
                    int consecutives = stream.readUnsignedShort();
                    if(discovered){
                        for(int j = i + 1; j < i + 1 + consecutives; j++){
                            int newx = j % width, newy = j / width;
                            tiles[newx][newy].setVisibility((byte) 1);
                        }
                    }
                    i += consecutives;
                }

                state.teams = new Teams();
            }

            byte teams = stream.readByte();
            for(int i = 0; i < teams; i++){
                Team team = Team.all[stream.readByte()];

                byte enemies = stream.readByte();
                Team[] enemyArr = new Team[enemies];
                for(int j = 0; j < enemies; j++){
                    enemyArr[j] = Team.all[stream.readByte()];
                }

                state.teams.add(team, enemyArr);

                byte cores = stream.readByte();

                for(int j = 0; j < cores; j++){
                    state.teams.get(team).cores.add(world.tile(stream.readLong()));
                }

                if(team == players[0].getTeam() && cores > 0){
                    Core.camera.position.set(state.teams.get(team).cores.first().drawx(), state.teams.get(team).cores.first().drawy(), 0);
                }
            }

            if(!openWorld){
                world.endMapLoad();
            }else if(weather != null){
                //open world has no map tags to carry the weather, use the networked custom config
                weather.setRain(state.rain);
            }

            //read raw snapshot
            netClient.readSnapshot(stream);

        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public static ByteBuffer writeServerData(){
        int maxlen = 32;

        String host = (headless ? "Server" : players[0].name);
        String map = (world.getMap() == null ? (world.isOpenWorld() ? "Open World" : "Unknown") : world.getMap().name);

        host = host.substring(0, Math.min(host.length(), maxlen));
        map = map.substring(0, Math.min(map.length(), maxlen));

        ByteBuffer buffer = ByteBuffer.allocate(128);

        buffer.put((byte) host.getBytes().length);
        buffer.put(host.getBytes());

        buffer.put((byte) map.getBytes().length);
        buffer.put(map.getBytes());

        buffer.putInt(playerGroup.size());
        buffer.putInt(state.wave);
        buffer.putInt(Version.build);
        buffer.put((byte)Version.type.getBytes().length);
        buffer.put(Version.type.getBytes());
        return buffer;
    }

    public static Host readServerData(String hostAddress, ByteBuffer buffer){
        byte hlength = buffer.get();
        byte[] hb = new byte[hlength];
        buffer.get(hb);

        byte mlength = buffer.get();
        byte[] mb = new byte[mlength];
        buffer.get(mb);

        String host = new String(hb);
        String map = new String(mb);

        int players = buffer.getInt();
        int wave = buffer.getInt();
        int version = buffer.getInt();
        byte tlength = buffer.get();
        byte[] tb = new byte[tlength];
        buffer.get(tb);
        String vertype = new String(tb);

        return new Host(host, hostAddress, map, wave, players, version, vertype);
    }
}
