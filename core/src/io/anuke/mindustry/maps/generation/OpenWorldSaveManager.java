package io.anuke.mindustry.maps.generation;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.anuke.mindustry.game.Difficulty;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.Floor;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.anuke.mindustry.Vars.*;

public class OpenWorldSaveManager{
    private static final String SAVES_DIR = "openworlds";
    private static final String META_FILE = "meta.json";
    private static final String CHUNKS_DIR = "chunks";
    private static final String ENTITIES_FILE = "entities.dat";
    private static final String PLANS_FILE = "plans.dat";
    private static final byte CHUNK_FORMAT_VERSION = 3;

    private FileHandle getRootDir(){
        return saveDirectory.child(SAVES_DIR);
    }

    private FileHandle getWorldDir(String name){
        return getRootDir().child(name);
    }

    private FileHandle getChunksDir(String name){
        return getWorldDir(name).child(CHUNKS_DIR);
    }

    /** Returns list of all saved open world names. */
    public Array<String> listSaves(){
        Array<String> result = new Array<>();
        FileHandle root = getRootDir();
        if(!root.exists()){
            root.mkdirs();
            return result;
        }
        for(FileHandle dir : root.list()){
            if(dir.isDirectory() && dir.child(META_FILE).exists()){
                result.add(dir.name());
            }
        }
        return result;
    }

    /** Read metadata for a saved world. Returns null if not found. */
    public OpenWorldMeta readMeta(String name){
        FileHandle metaFile = getWorldDir(name).child(META_FILE);
        if(!metaFile.exists()) return null;
        try{
            JsonValue val = new JsonReader().parse(metaFile);
            OpenWorldMeta meta = new OpenWorldMeta();
            meta.name = name;
            meta.seed = val.getLong("seed");
            meta.timePlayed = val.getLong("timePlayed");
            meta.difficulty = Difficulty.valueOf(val.getString("difficulty"));
            meta.dateCreated = val.getString("dateCreated", "");
            meta.chunksSaved = val.getInt("chunksSaved", 0);
            meta.build = val.getInt("build", 0);
            meta.playerX = val.getFloat("playerX", 0f);
            meta.playerY = val.getFloat("playerY", 0f);
            meta.playerTeam = val.getInt("playerTeam", Team.blue.ordinal());
            meta.techTree = val.getString("techTree", "");
            return meta;
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    /** Write metadata for a saved world. */
    public void writeMeta(String name, OpenWorldMeta meta){
        FileHandle metaFile = getWorldDir(name).child(META_FILE);
        getWorldDir(name).mkdirs();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"seed\": ").append(meta.seed).append(",\n");
        sb.append("  \"timePlayed\": ").append(meta.timePlayed).append(",\n");
        sb.append("  \"difficulty\": \"").append(meta.difficulty.name()).append("\",\n");
        sb.append("  \"dateCreated\": \"").append(meta.dateCreated).append("\",\n");
        sb.append("  \"chunksSaved\": ").append(meta.chunksSaved).append(",\n");
        sb.append("  \"build\": ").append(meta.build).append(",\n");
        sb.append("  \"playerX\": ").append(meta.playerX).append(",\n");
        sb.append("  \"playerY\": ").append(meta.playerY).append(",\n");
        sb.append("  \"playerTeam\": ").append(meta.playerTeam).append(",\n");
        sb.append("  \"techTree\": \"").append(meta.techTree == null ? "" : meta.techTree).append("\"\n");
        sb.append("}");
        metaFile.writeString(sb.toString(), false);
    }

    /** Save a single chunk to disk. */
    public void saveChunk(String worldName, ChunkManager.WorldChunk chunk){
        FileHandle chunksDir = getChunksDir(worldName);
        chunksDir.mkdirs();
        FileHandle file = chunksDir.child(chunk.cx + "_" + chunk.cy + ".dat");

        byte[] data = serializeChunk(chunk);
        if(data == null) return;
        file.writeBytes(data, false);
    }

    /** Write a pre-serialized chunk payload directly to disk (used for cold-stored chunks). */
    public void saveRawChunk(String worldName, int cx, int cy, byte[] data){
        FileHandle chunksDir = getChunksDir(worldName);
        chunksDir.mkdirs();
        chunksDir.child(cx + "_" + cy + ".dat").writeBytes(data, false);
    }

    /** Load a single chunk from disk. Returns null if not found. */
    public ChunkManager.WorldChunk loadChunk(String worldName, int cx, int cy){
        FileHandle file = getChunksDir(worldName).child(cx + "_" + cy + ".dat");
        if(!file.exists()) return null;
        return deserializeChunk(file.readBytes());
    }

    /** Serialize a chunk into a compressed in-memory payload. Returns null on failure. */
    public byte[] serializeChunk(ChunkManager.WorldChunk chunk){
        if(chunk.tiles == null) return null;
        try{
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(new GZIPOutputStream(baos));
            try{
                writeChunk(chunk, out);
            }finally{
                out.flush();
                out.close();
            }
            return baos.toByteArray();
        }catch(IOException e){
            e.printStackTrace();
            return null;
        }
    }

    /** Deserialize a payload produced by {@link #serializeChunk}. Accepts legacy uncompressed data. Returns null on failure. */
    public ChunkManager.WorldChunk deserializeChunk(byte[] raw){
        if(raw == null || raw.length == 0) return null;
        try{
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(decompress(raw)));
            try{
                return parseChunk(in);
            }finally{
                in.close();
            }
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private void writeChunk(ChunkManager.WorldChunk chunk, DataOutputStream out) throws IOException{
        out.writeByte(CHUNK_FORMAT_VERSION);
        out.writeInt(chunk.cx);
        out.writeInt(chunk.cy);

        for(int i = 0; i < chunk.tiles.length; i++){
            Tile tile = chunk.tiles[i];
            out.writeShort(tile.getFloorID());
            out.writeShort(tile.block().id);
            out.writeByte(tile.getElevation());
            out.writeByte(tile.getRotation());
            out.writeByte(tile.getTeamID());
            out.writeByte(tile.link);
            out.writeByte(tile.getVisibility());

            if(tile.entity != null && !(tile.block() instanceof io.anuke.mindustry.world.blocks.BlockPart)){
                out.writeBoolean(true);
                out.writeShort((short) tile.entity.health);

                if(tile.entity.items != null) tile.entity.items.write(out);
                if(tile.entity.power != null) tile.entity.power.write(out);
                if(tile.entity.liquids != null) tile.entity.liquids.write(out);
                if(tile.entity.cons != null) tile.entity.cons.write(out);

                tile.entity.writeConfig(out);
                tile.entity.write(out);
            }else{
                out.writeBoolean(false);
            }
        }
    }

    private ChunkManager.WorldChunk parseChunk(DataInputStream in) throws IOException{
        byte version = in.readByte();
        int fileCx = in.readInt();
        int fileCy = in.readInt();

        ChunkManager.WorldChunk chunk = new ChunkManager.WorldChunk(fileCx, fileCy);
        chunk.tiles = new Tile[ChunkManager.CHUNK_SIZE * ChunkManager.CHUNK_SIZE];

        for(int i = 0; i < chunk.tiles.length; i++){
            short floorId = in.readShort();
            short wallId = in.readShort();
            byte elevation = in.readByte();
            byte rotation = in.readByte();
            byte teamId = in.readByte();
            byte link = in.readByte();

            Floor floor = (Floor)content.block(floorId);
            chunk.tiles[i] = Tile.createRaw(
                fileCx * ChunkManager.CHUNK_SIZE + (i % ChunkManager.CHUNK_SIZE),
                fileCy * ChunkManager.CHUNK_SIZE + (i / ChunkManager.CHUNK_SIZE),
                floor,
                content.block(wallId),
                elevation
            );
            chunk.tiles[i].setRotation(rotation);
            chunk.tiles[i].setTeam(Team.all[teamId]);
            chunk.tiles[i].link = link;

            if(version >= 3){
                chunk.tiles[i].setVisibility(in.readByte());
            }

            if(version >= 2){
                boolean hasEntity = in.readBoolean();
                if(hasEntity){
                    chunk.tiles[i].rebuildEntity();
                    if(chunk.tiles[i].entity != null){
                        chunk.tiles[i].entity.health = in.readShort();

                        if(chunk.tiles[i].entity.items != null) chunk.tiles[i].entity.items.read(in);
                        if(chunk.tiles[i].entity.power != null) chunk.tiles[i].entity.power.read(in);
                        if(chunk.tiles[i].entity.liquids != null) chunk.tiles[i].entity.liquids.read(in);
                        if(chunk.tiles[i].entity.cons != null) chunk.tiles[i].entity.cons.read(in);

                        chunk.tiles[i].entity.readConfig(in);
                        chunk.tiles[i].entity.read(in);
                    }
                }
            }
        }

        chunk.generated = true;
        return chunk;
    }

    private static boolean isGzip(byte[] data){
        return data.length > 2 && (data[0] & 0xFF) == 0x1f && (data[1] & 0xFF) == 0x8b;
    }

    /** Compress a payload with gzip. Falls back to the raw input on failure. */
    public static byte[] compress(byte[] src){
        try{
            ByteArrayOutputStream baos = new ByteArrayOutputStream(src.length / 2 + 64);
            GZIPOutputStream gz = new GZIPOutputStream(baos);
            gz.write(src);
            gz.finish();
            gz.close();
            return baos.toByteArray();
        }catch(IOException e){
            e.printStackTrace();
            return src;
        }
    }

    /** Decompress a gzip payload; returns the input unchanged if it isn't gzip. */
    public static byte[] decompress(byte[] src){
        if(!isGzip(src)) return src;
        try{
            GZIPInputStream gin = new GZIPInputStream(new ByteArrayInputStream(src));
            ByteArrayOutputStream baos = new ByteArrayOutputStream(src.length * 4);
            byte[] buffer = new byte[4096];
            int n;
            while((n = gin.read(buffer)) > 0){
                baos.write(buffer, 0, n);
            }
            gin.close();
            return baos.toByteArray();
        }catch(IOException e){
            e.printStackTrace();
            return src;
        }
    }

    /** Save all global entities (units, fire, puddles) to disk. */
    public void saveEntities(String worldName){
        FileHandle file = getWorldDir(worldName).child(ENTITIES_FILE);
        try(DataOutputStream out = new DataOutputStream(file.write(false))){
            io.anuke.mindustry.io.SaveIO.getVersion().writeEntities(out);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /** Load all global entities from disk. */
    public void loadEntities(String worldName){
        FileHandle file = getWorldDir(worldName).child(ENTITIES_FILE);
        if(!file.exists()) return;
        try(DataInputStream in = new DataInputStream(file.read())){
            io.anuke.mindustry.io.SaveIO.getVersion().readEntities(in);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /** Save destroyed-block rebuild plans for all teams. */
    public void savePlans(String worldName){
        FileHandle file = getWorldDir(worldName).child(PLANS_FILE);
        getWorldDir(worldName).mkdirs();
        try(DataOutputStream out = new DataOutputStream(file.write(false))){
            out.writeByte(1); //plans file format version
            io.anuke.mindustry.io.SaveIO.getVersion().writePlans(out);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /** Load destroyed-block rebuild plans for all teams. */
    public void loadPlans(String worldName){
        FileHandle file = getWorldDir(worldName).child(PLANS_FILE);
        if(!file.exists()) return;
        try(DataInputStream in = new DataInputStream(file.read())){
            int version = in.readUnsignedByte();
            if(version != 1) return;
            io.anuke.mindustry.io.SaveIO.getVersion().readPlans(in);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /** Check if a chunk file exists on disk. */
    public boolean hasChunk(String worldName, int cx, int cy){
        return getChunksDir(worldName).child(cx + "_" + cy + ".dat").exists();
    }

    /** Delete an entire world save. */
    public void deleteWorld(String name){
        FileHandle dir = getWorldDir(name);
        if(dir.exists()){
            dir.deleteDirectory();
        }
    }

    /** Get the number of chunk files saved for a world. */
    public int countChunks(String worldName){
        FileHandle chunksDir = getChunksDir(worldName);
        if(!chunksDir.exists()) return 0;
        return chunksDir.list().length;
    }

    public static class OpenWorldMeta{
        public String name = "";
        public long seed;
        public long timePlayed;
        public Difficulty difficulty = Difficulty.hard;
        public String dateCreated = "";
        public int chunksSaved = 0;
        public int build = 0;
        public float playerX, playerY;
        public int playerTeam = Team.blue.ordinal();
        public String techTree = "";
    }
}
