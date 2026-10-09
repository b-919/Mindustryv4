package io.anuke.mindustry.world.blocks.units;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.ai.mass.MassAdrenaline;
import io.anuke.mindustry.ai.mass.MassAIConfig;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.graphics.Layer;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.ItemStack;
import io.anuke.mindustry.world.BarType;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.storage.HiveBlock;
import io.anuke.mindustry.world.meta.BlockBar;
import io.anuke.mindustry.world.meta.BlockFlag;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Lines;
import io.anuke.ucore.scene.style.TextureRegionDrawable;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.EnumSet;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class UnitHiveSpawner extends Block {
    public UnitType[] types;
    public ItemStack[][] consumerStacks;
    public float[] producerTimes;
    public int[] maxSpawn;
    /**Minimum hive evolution level required to spawn each unit type. Null = all unlocked.*/
    public int[] evoUnlock;

    public UnitHiveSpawner(String name) {
        super(name);
        update = true;
        solid = false;
        configurable = true;
        flags = EnumSet.of(BlockFlag.producer, BlockFlag.target);
        layer = Layer.back;
    }

    @Override
    public void buildTable(Tile tile, Table table) {
        UnitHiveSpawnerEntity entity = tile.entity();
        if (types == null || types.length == 0) return;

        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        Table cont = new Table();

        for (int i = 0; i < types.length; i++) {
            final int idx = i;
            UnitType type = types[i];

            ImageButton button = cont.addImageButton("white", "clear-toggle", 24, () -> {
                addToQueue(tile, idx);
            }).size(38).group(group).get();
            button.getStyle().imageUp = new TextureRegionDrawable(type.iconRegion);

            if ((i + 1) % 4 == 0) cont.row();
        }

        table.add(cont).row();

        table.add("Queue: " + entity.queue.size).pad(4f).row();

        table.addImageButton("icon-cancel", "clear", 24, () -> cancelLast(tile)).pad(4f);
    }

    @Override
    public void setBars() {
        super.setBars();
        bars.add(new BlockBar(BarType.production, true, tile -> {
            UnitHiveSpawnerEntity e = tile.entity();
            if (e.queue.size == 0) return 0f;
            int typeIdx = e.queue.first();
            float time = getProduceTime(typeIdx);
            return time <= 0f ? 0f : e.progress / time;
        }));
        bars.remove(BarType.inventory);
    }

    public boolean addToQueue(Tile tile, int typeIdx) {
        UnitHiveSpawnerEntity entity = tile.entity();
        if (entity == null) return false;
        typeIdx = clampType(typeIdx);

        if (entity.spawned == null) entity.spawned = new int[types.length];
        if (maxSpawn != null && typeIdx < maxSpawn.length && entity.spawned[typeIdx] >= maxSpawn[typeIdx]) return false;

        //check evolution lock
        int evo = getHiveEvolution(tile);
        if(!isTypeUnlocked(typeIdx, evo)) return false;

        Tile core = Geometry.findClosest(tile.drawx(), tile.drawy(), Vars.state.teams.get(tile.getTeam()).cores);
        if (core == null || core.entity == null) return false;

        ItemStack[] reqs = getConsumerStacks(typeIdx);
        for (ItemStack req : reqs) {
            if (!core.entity.items.has(req.item, req.amount)) return false;
        }

        for (ItemStack req : reqs) {
            core.entity.items.remove(req.item, req.amount);
        }

        entity.queue.add(typeIdx);
        return true;
    }

    public void cancelLast(Tile tile) {
        UnitHiveSpawnerEntity entity = tile.entity();
        if (entity == null || entity.queue.size == 0) return;

        int typeIdx = entity.queue.removeIndex(entity.queue.size - 1);
        ItemStack[] reqs = getConsumerStacks(typeIdx);
        Tile core = Geometry.findClosest(tile.drawx(), tile.drawy(), Vars.state.teams.get(tile.getTeam()).cores);
        if (core != null && core.entity != null) {
            for (ItemStack req : reqs) {
                core.entity.items.add(req.item, req.amount);
            }
        }

        if (entity.queue.size == 0) entity.progress = 0f;
    }

    @Override
    public void update(Tile tile) {
        if(Net.client()) return;
        UnitHiveSpawnerEntity entity = tile.entity();

        if (entity.spawned == null || entity.spawned.length < types.length) {
            entity.spawned = new int[types.length];
        }

        if (Vars.state.teams.get(tile.getTeam()).cores.isEmpty()) return;
        if (entity.queue.size == 0) return;

        int typeIdx = entity.queue.first();

        //skip if type is no longer unlocked by evolution
        int evo = getHiveEvolution(tile);
        if(!isTypeUnlocked(typeIdx, evo)){
            entity.queue.removeIndex(0);
            if (entity.queue.size == 0) entity.progress = 0f;
            return;
        }

        float produceTime = getProduceTime(typeIdx);

        if (produceTime <= 0f) {
            spawnUnit(tile, typeIdx);
            entity.spawned[typeIdx]++;
            entity.queue.removeIndex(0);
            return;
        }

        if (maxSpawn != null && typeIdx < maxSpawn.length && entity.spawned[typeIdx] >= maxSpawn[typeIdx]) {
            entity.queue.removeIndex(0);
            if (entity.queue.size == 0) entity.progress = 0f;
            return;
        }

        float spawnSpeed = Vars.state.difficulty == null ? 1f : Vars.state.difficulty.massScaling;
        if(tile.getTeam() == Team.themass && MassAdrenaline.isBonusActiveAt(tile)){
            spawnSpeed *= MassAIConfig.ADRENALINE_FACTORY_SPEED;
        }
        entity.progress += entity.delta() * spawnSpeed;

        if (entity.progress >= produceTime) {
            entity.progress = 0f;
            spawnUnit(tile, typeIdx);
            entity.spawned[typeIdx]++;
            entity.queue.removeIndex(0);
            if (entity.queue.size == 0) entity.progress = 0f;
        }
    }

    public void spawnUnit(Tile tile, int typeIdx) {
        if(Net.client()) return;
        UnitType type = getType(typeIdx);
        if (type == null) return;

        BaseUnit unit = type.create(tile.getTeam());
        unit.setSpawner(tile);
        unit.set(tile.drawx() + Mathf.range(Vars.tilesize * 2), tile.drawy() + Mathf.range(Vars.tilesize * 2));
        unit.add();
    }

    @Override
    public void drawLayer(Tile tile) {
        if (Vars.state.teams.get(tile.getTeam()).cores.isEmpty()) return;
        Tile core = Geometry.findClosest(tile.drawx(), tile.drawy(), Vars.state.teams.get(tile.getTeam()).cores);
        if (core == null) return;

        Draw.color(Color.valueOf("3c0e0e"));
        Lines.stroke(1f + Mathf.absin(Timers.time(), 4f, 1f));
        Lines.line(tile.drawx(), tile.drawy(), core.drawx(), core.drawy());
        Draw.reset();
    }

    @Override
    public void unitRemoved(Tile tile, Unit unit) {
        UnitHiveSpawnerEntity entity = tile.entity();
        for (int i = 0; i < types.length; i++) {
            if (((BaseUnit) unit).getType().equals(types[i])) {
                entity.spawned[i]--;
                entity.spawned[i] = Math.max(entity.spawned[i], 0);
            }
        }
    }

    public UnitType getType(int index) {
        if (types == null || index < 0 || index >= types.length) return null;
        return types[index];
    }

    public float getProduceTime(int index) {
        if (producerTimes == null || index < 0 || index >= producerTimes.length) return 0f;
        return producerTimes[index];
    }

    public ItemStack[] getConsumerStacks(int index) {
        if (consumerStacks == null || index < 0 || index >= consumerStacks.length) return new ItemStack[0];
        return consumerStacks[index];
    }

    protected int clampType(int typeIdx) {
        if (types == null || types.length == 0) return 0;
        return Math.max(0, Math.min(typeIdx, types.length - 1));
    }

    /**Returns the minimum evolution required to unlock this unit type. Returns 0 if no lock.*/
    public int getRequiredEvo(int typeIdx){
        if(evoUnlock == null || typeIdx < 0 || typeIdx >= evoUnlock.length) return 0;
        return evoUnlock[typeIdx];
    }

    /**Returns true if this unit type is unlocked at the given evolution level.*/
    public boolean isTypeUnlocked(int typeIdx, int evolution){
        return evolution >= getRequiredEvo(typeIdx);
    }

    /**Returns the highest evolution level among all nearby hives for this tile's team.*/
    public int getHiveEvolution(Tile tile){
        return HiveBlock.getMaxEvolutionNearby(tile, 999f);
    }

    public TileEntity newEntity() {
        return new UnitHiveSpawnerEntity();
    }

    public static class UnitHiveSpawnerEntity extends TileEntity {
        public Array<Integer> queue = new Array<>();
        public float progress;
        public int[] spawned;

        @Override
        public void write(DataOutput stream) throws IOException {
            stream.writeInt(queue.size);
            for (int i = 0; i < queue.size; i++) {
                stream.writeInt(queue.get(i));
            }
            stream.writeFloat(progress);
        }

        @Override
        public void read(DataInput stream) throws IOException {
            int qsize = stream.readInt();
            queue.clear();
            for (int i = 0; i < qsize; i++) {
                queue.add(stream.readInt());
            }
            progress = stream.readFloat();
        }
    }
}
