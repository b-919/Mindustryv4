package io.anuke.mindustry.world.blocks.logic;

import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.scene.style.TextureRegionDrawable;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.layout.Table;

import com.badlogic.gdx.utils.ObjectSet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LogicImporter extends LogicBlock {
    public LogicImporter(String name) {
        super(name);
        hasItems = true;
        itemCapacity = 100;
    }

    @Remote(targets = Loc.both, called = Loc.both, forward = true)
    public static void setImporterItem(Player player, Tile tile, Item item) {
        LogicImporterEntity entity = tile.entity();
        if (entity != null) entity.selectedItem = item;
    }

    @Override
    public void update(Tile tile) {
        LogicImporterEntity entity = tile.entity();
        if (entity.items.total() > 0) {
            tryDump(tile);
        }
    }

    @Override
    public void buildTable(Tile tile, Table table) {
        LogicImporterEntity entity = tile.entity();
        table.add("$text.logic.importer.select").row();

        table.table(t -> {
            ButtonGroup<ImageButton> group = new ButtonGroup<>();
            ObjectSet<Item> availableItems = new ObjectSet<>();
            for (int x = 0; x < Vars.world.width(); x++) {
                for (int y = 0; y < Vars.world.height(); y++) {
                    Tile other = Vars.world.tile(x, y);
                    if (other != null && other.entity() instanceof LogicExporter.LogicExporterEntity) {
                        LogicExporter.LogicExporterEntity exporter = other.entity();
                        exporter.items.forEach((item, amount) -> {
                            if (amount > 0) availableItems.add(item);
                        });
                    }
                }
            }

            int i = 0;
            for (Item item : availableItems) {
                ImageButton button = t.addImageButton("white", "clear-toggle", 24, () -> {
                    setImporterItem(null, tile, item);
                }).size(38).group(group).get();
                button.getStyle().imageUp = new TextureRegionDrawable(item.getContentIcon());
                button.setChecked(entity.selectedItem == item);
                if (++i % 4 == 0) t.row();
            }
            //null
            ImageButton naButton = t.addImageButton("icon-cancel", "clear-toggle", 24, () -> {
                setImporterItem(null, tile, null);
            }).size(38).group(group).get();
            naButton.setChecked(entity.selectedItem == null);
        }).row();
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source) {
        LogicImporterEntity entity = tile.entity();
        // If it's a manual connection, we might not have a selected item, so accept anything
        // However, we don't have easy access to the exporter here without searching.
        // But the drone is the one calling acceptStack usually.
        return (entity.selectedItem == null || item == entity.selectedItem) && entity.items.get(item) < getMaximumAccepted(tile, item);
    }

    @Override
    public TileEntity newEntity() {
        return new LogicImporterEntity();
    }

    public static class LogicImporterEntity extends LogicEntity {
        public Item selectedItem;

        @Override
        public void write(DataOutput stream) throws IOException {
            super.write(stream);
            stream.writeShort(selectedItem == null ? -1 : selectedItem.id);
        }

        @Override
        public void read(DataInput stream) throws IOException {
            super.read(stream);
            int id = stream.readShort();
            selectedItem = id == -1 ? null : Vars.content.item(id);
        }

        @Override
        public void writeConfig(DataOutput stream) throws IOException {
            stream.writeShort(selectedItem == null ? -1 : selectedItem.id);
        }

        @Override
        public void readConfig(DataInput stream) throws IOException {
            int id = stream.readShort();
            selectedItem = id == -1 ? null : Vars.content.item(id);
        }

        @Override
        public Object config(){
            return selectedItem;
        }

        @Override
        public void configured(Object config){
            if(config instanceof Item){
                selectedItem = (Item)config;
            }else{
                selectedItem = null;
            }
        }
    }
}
