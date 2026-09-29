package io.anuke.mindustry.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Base64Coder;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Log;

import java.io.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static io.anuke.mindustry.Vars.content;

public class Schematics {
    private Array<Schematic> all = new Array<>();
    private LongMap<Object> pendingConfigs = new LongMap<>();
    private LongMap<Integer> configTries = new LongMap<>();
    private ObjectMap<Schematic, Texture> previews = new ObjectMap<>();
    private SpriteBatch spriteBatch;
    private Matrix4 matrix4 = new Matrix4();
    private FrameBuffer buffer;

    private static final int bufferSize = 256;
    private static final int maxPreviews = 48;
    /** How many frames to keep retrying a schematic config that references not-yet-built tiles. */
    private static final int configRetries = 1800;

    public void load() {
        for(Texture texture : previews.values()){
            texture.dispose();
        }
        previews.clear();
        all.clear();
        if (!Vars.schematicDirectory.exists()) return;

        for (FileHandle file : Vars.schematicDirectory.list()) {
            if (file.extension().equals("msch")) {
                try {
                    Schematic s = read(file);
                    all.add(s);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Array<Schematic> all() {
        return all;
    }

    public void save(Schematic schem) {
        if (!all.contains(schem, true)) all.add(schem);
        Vars.schematicDirectory.mkdirs();
        try {
            write(schem, Vars.schematicDirectory.child(schem.name() + ".msch"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void remove(Schematic schem) {
        Texture texture = previews.remove(schem);
        if (texture != null) texture.dispose();
        all.removeValue(schem, true);
        if (schem.file != null && schem.file.exists()) {
            schem.file.delete();
        }
    }

    /** Returns a cached preview texture of the schematic, rendering it lazily on the first call. */
    public Texture getPreview(Schematic schem){
        Texture texture = previews.get(schem);
        if(texture != null) return texture;
        if(Vars.headless) return null;
        texture = renderPreview(schem);
        if(texture == null) return null;
        previews.put(schem, texture);
        if(previews.size > maxPreviews){
            Schematic key = previews.keys().next();
            if(key != null){
                previews.remove(key).dispose();
            }
        }
        return texture;
    }

    public void dispose(){
        for(Texture texture : previews.values()){
            texture.dispose();
        }
        previews.clear();
        if(spriteBatch != null) spriteBatch.dispose();
        if(buffer != null) buffer.dispose();
        spriteBatch = null;
        buffer = null;
    }

    private Texture renderPreview(Schematic schem){
        int maxDim = Math.max(schem.width, schem.height);
        if(maxDim <= 0) return null;

        if(spriteBatch == null) spriteBatch = new SpriteBatch();
        if(buffer == null) buffer = new FrameBuffer(Pixmap.Format.RGBA8888, bufferSize, bufferSize, false);

        buffer.begin();

        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        matrix4.setToOrtho2D(0, 0, bufferSize, bufferSize);
        spriteBatch.setProjectionMatrix(matrix4);

        float scale = (bufferSize - 8f) / maxDim;
        float w = schem.width * scale, h = schem.height * scale;
        float ox = (bufferSize - w) / 2f, oy = (bufferSize - h) / 2f;

        spriteBatch.begin();

        TextureRegion floor = Draw.region("metalfloor1");
        for(int x = 0; x < schem.width; x++){
            for(int y = 0; y < schem.height; y++){
                spriteBatch.draw(floor, ox + x * scale, oy + y * scale, scale, scale);
            }
        }

        spriteBatch.setColor(Color.WHITE);

        for(Schematic.Stile stile : schem.tiles){
            if(stile.block == null) continue;
            float cx = ox + (stile.x + stile.block.size / 2f) * scale;
            float cy = oy + (stile.y + stile.block.size / 2f) * scale;
            for(TextureRegion region : stile.block.getBlockIcon()){
                float rw = region.getRegionWidth() * scale / 8f;
                float rh = region.getRegionHeight() * scale / 8f;
                spriteBatch.draw(region, cx - rw / 2f, cy - rh / 2f, rw / 2f, rh / 2f, rw, rh, 1f, 1f,
                    stile.block.rotate ? stile.rotation * 90f : 0f);
            }
        }

        spriteBatch.end();

        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, bufferSize, bufferSize);
        buffer.end();

        //crop the read-back image down to the actual schematic footprint, so there are no blank margins around the blocks
        int minX = bufferSize, minY = bufferSize, maxX = -1, maxY = -1;
        for(int x = 0; x < bufferSize; x++){
            for(int y = 0; y < bufferSize; y++){
                if(((pixmap.getPixel(x, y) >>> 24) & 0xff) > 8){
                    if(x < minX) minX = x;
                    if(x > maxX) maxX = x;
                    if(y < minY) minY = y;
                    if(y > maxY) maxY = y;
                }
            }
        }

        Texture texture;
        if(maxX >= minX && maxY >= minY){
            int pad = 2;
            minX = Math.max(0, minX - pad);
            minY = Math.max(0, minY - pad);
            maxX = Math.min(bufferSize - 1, maxX + pad);
            maxY = Math.min(bufferSize - 1, maxY + pad);

            int cw = maxX - minX + 1;
            int ch = maxY - minY + 1;

            Pixmap cropped = new Pixmap(cw, ch, Pixmap.Format.RGBA8888);
            cropped.setBlending(Pixmap.Blending.None);
            for(int x = 0; x < cw; x++){
                for(int y = 0; y < ch; y++){
                    cropped.drawPixel(x, y, pixmap.getPixel(x + minX, y + minY));
                }
            }
            pixmap.dispose();
            texture = new Texture(cropped);
            cropped.dispose();
        }else{
            texture = new Texture(pixmap);
            pixmap.dispose();
        }

        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    public void place(Schematic schem, int x, int y, Team team) {
        for (Schematic.Stile stile : schem.tiles) {
            if (stile.block == null) continue;
            Recipe recipe = Recipe.getByResult(stile.block);
            if (recipe == null || !recipe.available()) continue;
            int ox = x + stile.x + (stile.block.size - 1) / 2;
            int oy = y + stile.y + (stile.block.size - 1) / 2;
            //register every tile placed from the schematic, even without a config,
            //so blocks know not to apply their "last placed" presets while the
            //schematic config (or lack of one) is still pending
            pendingConfigs.put(key(ox, oy), stile.config);
            Vars.control.input(0).tryPlaceBlock(ox, oy, recipe, stile.rotation);
        }
    }

    public void applyConfig(Tile tile){
        long k = key(tile.x, tile.y);
        if(!pendingConfigs.containsKey(k)) return;
        Object config = pendingConfigs.get(k);

        boolean applied = true;

        if(config != null && tile.entity != null){
            tile.entity.configured(config);
            //link-type configs (bridges, power nodes) reference other tiles that may not be
            //constructed yet; only consider the config applied once it actually took effect
            applied = configMatches(tile.entity.config(), config);
        }

        if(applied){
            pendingConfigs.remove(k);
            configTries.remove(k);
        }else{
            //retry for a while, in case the referenced tiles are still being built
            int tries = configTries.get(k, 0) + 1;
            if(tries < configRetries){
                configTries.put(k, tries);
                Vars.threads.runDelay(() -> applyConfig(tile));
            }else{
                pendingConfigs.remove(k);
                configTries.remove(k);
            }
        }
    }

    /** Whether two config objects represent the same value. */
    private boolean configMatches(Object applied, Object expected){
        if(expected == null) return true;
        if(expected instanceof int[] && applied instanceof int[]){
            int[] a = (int[]) applied, b = (int[]) expected;
            if(a.length != b.length) return false;
            for(int i = 0; i < a.length; i++){
                if(a[i] != b[i]) return false;
            }
            return true;
        }
        return applied != null && applied.equals(expected);
    }

    /** Whether a block at this tile was just placed from a schematic and its config has not been applied yet. */
    public boolean hasPendingConfig(Tile tile){
        return pendingConfigs.containsKey(key(tile.x, tile.y));
    }

    private static long key(int x, int y){
        return ((long)x << 32) | (y & 0xffffffffL);
    }

    public Schematic create(int x, int y, int x2, int y2) {
        int minx = Math.min(x, x2);
        int miny = Math.min(y, y2);
        int maxx = Math.max(x, x2);
        int maxy = Math.max(y, y2);

        Array<Schematic.Stile> tiles = new Array<>();
        int minTileX = Integer.MAX_VALUE, minTileY = Integer.MAX_VALUE;
        int maxTileX = Integer.MIN_VALUE, maxTileY = Integer.MIN_VALUE;

        for (int ix = minx; ix <= maxx; ix++) {
            for (int iy = miny; iy <= maxy; iy++) {
                Tile tile = Vars.world.tile(ix, iy);
                if (tile == null || tile.block() == Blocks.air) continue;
                if (tile.isLinked()) continue;

                //Normalize to bottom-left
                int ox = ix - (tile.block().size - 1) / 2;
                int oy = iy - (tile.block().size - 1) / 2;

                tiles.add(new Schematic.Stile(tile.block(), ox, oy, tile.entity != null ? tile.entity.config() : null, tile.getRotation()));
                minTileX = Math.min(minTileX, ox);
                minTileY = Math.min(minTileY, oy);
                maxTileX = Math.max(maxTileX, ox + tile.block().size - 1);
                maxTileY = Math.max(maxTileY, oy + tile.block().size - 1);
            }
        }

        if (tiles.size == 0) return new Schematic(new Array<>(), new ObjectMap<>(), 0, 0);

        for (Schematic.Stile stile : tiles) {
            stile.x -= minTileX;
            stile.y -= minTileY;
        }

        int width = maxTileX - minTileX + 1;
        int height = maxTileY - minTileY + 1;

        ObjectMap<String, String> tags = new ObjectMap<>();
        tags.put("name", "Schematic " + (all.size + 1));
        return new Schematic(tiles, tags, width, height);
    }

    public Schematic read(FileHandle file) throws IOException {
        try (InputStream is = new InflaterInputStream(file.read())) {
            DataInputStream stream = new DataInputStream(is);
            Schematic s = read(stream);
            s.file = file;
            return s;
        }
    }

    public Schematic read(DataInputStream stream) throws IOException {
        byte[] header = new byte[4];
        stream.readFully(header);
        if (!new String(header).equals("MSCH")) throw new IOException("Not a schematic file");

        int version = stream.readUnsignedByte();
        short width = stream.readShort();
        short height = stream.readShort();

        int tagCount = stream.readUnsignedByte();
        ObjectMap<String, String> tags = new ObjectMap<>();
        for (int i = 0; i < tagCount; i++) {
            tags.put(stream.readUTF(), stream.readUTF());
        }

        int blockCount = stream.readUnsignedShort();
        Block[] blocks = new Block[blockCount];
        for (int i = 0; i < blockCount; i++) {
            String name = stream.readUTF();
            blocks[i] = content.getByName(ContentType.block, name);
            if(blocks[i] == null) Log.err("Block not found in schematic: {0}", name);
        }

        int tileCount = stream.readInt();
        Array<Schematic.Stile> tiles = new Array<>(tileCount);
        for (int i = 0; i < tileCount; i++) {
            int blockIndex = stream.readUnsignedByte();
            short x = stream.readShort();
            short y = stream.readShort();
            byte rotation = stream.readByte();
            Object config = version >= 2 ? readTileConfig(stream) : null;
            
            if(blockIndex < blocks.length && blocks[blockIndex] != null){
                tiles.add(new Schematic.Stile(blocks[blockIndex], x, y, config, rotation));
            }
        }

        return new Schematic(tiles, tags, width, height);
    }

    public void write(Schematic schematic, FileHandle file) throws IOException {
        try (OutputStream os = new DeflaterOutputStream(file.write(false))) {
            DataOutputStream stream = new DataOutputStream(os);
            write(schematic, stream);
        }
    }

    public void write(Schematic schematic, DataOutputStream stream) throws IOException {
        stream.writeBytes("MSCH");
        stream.writeByte(2); // version
        stream.writeShort(schematic.width);
        stream.writeShort(schematic.height);

        stream.writeByte(schematic.tags.size);
        for (ObjectMap.Entry<String, String> entry : schematic.tags.entries()) {
            stream.writeUTF(entry.key);
            stream.writeUTF(entry.value);
        }

        Array<Block> blocks = new Array<>();
        for (Schematic.Stile tile : schematic.tiles) {
            if (tile.block != null && !blocks.contains(tile.block, true)) {
                blocks.add(tile.block);
            }
        }

        stream.writeShort(blocks.size);
        for (Block block : blocks) {
            stream.writeUTF(block.name);
        }

        int count = 0;
        for(Schematic.Stile tile : schematic.tiles) if(tile.block != null) count++;
        stream.writeInt(count);

        for (Schematic.Stile tile : schematic.tiles) {
            if (tile.block == null) continue;
            stream.writeByte(blocks.indexOf(tile.block, true));
            stream.writeShort(tile.x);
            stream.writeShort(tile.y);
            stream.writeByte(tile.rotation);
            writeTileConfig(stream, tile.config);
        }
    }

    private void writeTileConfig(DataOutputStream stream, Object config) throws IOException {
        if (config == null) {
            stream.writeByte(0);
        } else if (config instanceof Item) {
            stream.writeByte(1);
            stream.writeShort(((Item) config).id);
        } else if (config instanceof Liquid) {
            stream.writeByte(2);
            stream.writeShort(((Liquid) config).id);
        } else if (config instanceof Integer) {
            stream.writeByte(3);
            stream.writeInt((Integer) config);
        } else if (config instanceof int[]) {
            stream.writeByte(4);
            int[] arr = (int[]) config;
            stream.writeShort(arr.length);
            for (int i : arr) stream.writeInt(i);
        } else {
            stream.writeByte(0);
        }
    }

    private Object readTileConfig(DataInputStream stream) throws IOException {
        byte type = stream.readByte();
        switch (type) {
            case 0: return null;
            case 1: return Vars.content.item(stream.readShort());
            case 2: return Vars.content.liquid(stream.readShort());
            case 3: return stream.readInt();
            case 4: {
                int len = stream.readUnsignedShort();
                int[] arr = new int[len];
                for (int i = 0; i < len; i++) arr[i] = stream.readInt();
                return arr;
            }
            default: return null;
        }
    }

    public String writeBase64(Schematic schematic) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DeflaterOutputStream def = new DeflaterOutputStream(out);
        write(schematic, new DataOutputStream(def));
        def.close();
        return new String(Base64Coder.encode(out.toByteArray()));
    }

    public Schematic readBase64(String base64) throws IOException {
        byte[] bytes = Base64Coder.decode(base64);
        return read(new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes))));
    }
}
