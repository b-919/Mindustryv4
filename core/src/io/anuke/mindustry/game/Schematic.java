package io.anuke.mindustry.game;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.type.Recipe;
import io.anuke.mindustry.world.Block;
import io.anuke.ucore.function.Consumer;

import static io.anuke.mindustry.Vars.schematics;

public class Schematic {
    public final Array<Stile> tiles;
    public ObjectMap<String, String> tags;
    public int width, height;
    public FileHandle file;

    public Schematic(Array<Stile> tiles, ObjectMap<String, String> tags, int width, int height) {
        this.tiles = tiles;
        this.tags = tags;
        this.width = width;
        this.height = height;
    }

    /** Packs a tile offset relative to another tile into a single int. */
    public static int packOffset(int dx, int dy){
        return (dx << 16) | (dy & 0xFFFF);
    }

    /** Unpacks a value produced by {@link #packOffset} into a mutable relative offset. */
    public static GridPoint2 unpackOffset(int packed){
        return new GridPoint2((short)(packed >> 16), (short)(packed & 0xFFFF));
    }

    public String name() {
        return tags.get("name", "unknown");
    }

    public String description() {
        return tags.get("description", "");
    }

    public void save() {
        schematics.save(this);
    }

    public void rotate() {
        Consumer<GridPoint2> transformer = point -> point.set(-point.y, point.x);

        for (Stile tile : tiles) {
            if (tile.block == null) continue;
            int temp = tile.x;
            tile.x = (short) (height - tile.block.size - tile.y);
            tile.y = (short) temp;
            tile.rotation = (byte) ((tile.rotation + 1) % 4);
            tile.config = tile.block.pointConfig(tile.config, transformer);
        }

        int temp = width;
        width = height;
        height = temp;
    }

    public void flipX() {
        Consumer<GridPoint2> transformer = point -> point.set(-point.x, point.y);

        for (Stile tile : tiles) {
            if (tile.block == null) continue;
            tile.x = (short) (width - tile.block.size - tile.x);
            if (tile.rotation % 2 == 0) {
                tile.rotation = (byte) ((tile.rotation + 2) % 4);
            }
            tile.config = tile.block.pointConfig(tile.config, transformer);
        }
    }

    public void flipY() {
        Consumer<GridPoint2> transformer = point -> point.set(point.x, -point.y);

        for (Stile tile : tiles) {
            if (tile.block == null) continue;
            tile.y = (short) (height - tile.block.size - tile.y);
            if (tile.rotation % 2 != 0) {
                tile.rotation = (byte) ((tile.rotation + 2) % 4);
            }
            tile.config = tile.block.pointConfig(tile.config, transformer);
        }
    }

    public Schematic copy() {
        Array<Stile> newTiles = new Array<>(tiles.size);
        for (Stile tile : tiles) {
            newTiles.add(tile.copy());
        }
        return new Schematic(newTiles, new ObjectMap<>(tags), width, height);
    }

    public Array<ItemStack> requirements() {
        ObjectMap<Item, Integer> reqs = new ObjectMap<>();
        for (Stile tile : tiles) {
            Recipe recipe = Recipe.getByResult(tile.block);
            if (recipe != null && recipe.requirements != null) {
                for (ItemStack stack : recipe.requirements) {
                    reqs.put(stack.item, reqs.get(stack.item, 0) + stack.amount);
                }
            }
        }
        Array<ItemStack> result = new Array<>();
        for (ObjectMap.Entry<Item, Integer> entry : reqs.entries()) {
            result.add(new ItemStack(entry.key, entry.value));
        }
        return result;
    }

    public static class Stile {
        public Block block;
        public short x, y;
        public Object config;
        public byte rotation;

        public Stile(Block block, int x, int y, Object config, byte rotation) {
            this.block = block;
            this.x = (short) x;
            this.y = (short) y;
            this.config = config;
            this.rotation = rotation;
        }

        public Stile copy() {
            return new Stile(block, x, y, config, rotation);
        }
    }
}
