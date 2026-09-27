package io.anuke.mindustry.maps.filters;

import com.badlogic.gdx.utils.Align;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.blocks.Blocks;
import io.anuke.mindustry.game.TechTree;
import io.anuke.mindustry.ui.ImageStack;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.blocks.Floor;
import io.anuke.mindustry.world.blocks.OreBlock;
import io.anuke.ucore.function.BooleanProvider;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.function.Predicate;
import io.anuke.ucore.function.Supplier;
import io.anuke.ucore.scene.ui.ButtonGroup;
import io.anuke.ucore.scene.ui.ImageButton;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.Slider;
import io.anuke.ucore.scene.ui.TextButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Strings;

public abstract class FilterOption{
    public static final Predicate<Block> floorsOnly = b -> (b instanceof Floor && !(b instanceof OreBlock) && b != Blocks.air);
    public static final Predicate<Block> wallsOnly = b -> (!b.synthetic() && !(b instanceof Floor) && b != Blocks.air);
    public static final Predicate<Block> floorsOptional = b -> b == Blocks.air || (b instanceof Floor && !(b instanceof OreBlock));
    public static final Predicate<Block> wallsOptional = b -> b == Blocks.air || (!b.synthetic() && !(b instanceof Floor));
    public static final Predicate<Block> oresOnly = b -> b instanceof OreBlock && ((OreBlock)b).base == Blocks.stone;
    public static final Predicate<Block> oresFloorsOptional = b -> b instanceof Floor;
    public static final Predicate<Block> oresCapable = b -> b instanceof Floor && !(b instanceof OreBlock) && ((Floor)b).hasOres;
    public static final Predicate<Block> anyOptional = b -> b == Blocks.air || (b instanceof Floor) || (!b.synthetic() && !(b instanceof Floor));

    public Runnable changed = () -> {};

    public abstract void build(Table table);

    public static class SliderOption extends FilterOption{
        final String name;
        final Supplier<Float> getter;
        final Consumer<Float> setter;
        final float min, max, step;

        public SliderOption(String name, Supplier<Float> getter, Consumer<Float> setter, float min, float max){
            this(name, getter, setter, min, max, (max - min) / 15f);
        }

        public SliderOption(String name, Supplier<Float> getter, Consumer<Float> setter, float min, float max, float step){
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.step = step;
        }

        @Override
        public void build(Table table){
            Slider slider = new Slider(min, max, step, false);
            slider.moved(setter);
            slider.setValue(getter.get());

            Label valLabel = new Label(Strings.toFixed(getter.get(), 2));
            valLabel.setAlignment(Align.right);

            slider.moved(f -> valLabel.setText(Strings.toFixed(f, 2)));

            table.defaults().left();
            table.add("$text.filter.option." + name).width(150f).padRight(10);
            table.add(slider).growX().padRight(10);
            table.add(valLabel).width(60f).right();
            table.row();
        }
    }

    public static class BlockOption extends FilterOption{
        final String name;
        final Supplier<Block> supplier;
        final Consumer<Block> consumer;
        final Predicate<Block> filter;

        public BlockOption(String name, Supplier<Block> supplier, Consumer<Block> consumer, Predicate<Block> filter){
            this.name = name;
            this.supplier = supplier;
            this.consumer = consumer;
            this.filter = filter;
        }

        @Override
        public void build(Table table){
            Block current = supplier.get();
            ImageButton button = new ImageButton("white", "clear-toggle");
            if(current != null && current != Blocks.air){
                button.getImageCell().size(32f);
                button.replaceImage(new ImageStack(current.getCompactIcon()));
            }
            button.clicked(() -> {
                FloatingBlockDialog dialog = new FloatingBlockDialog(name, filter, consumer, block -> {
                    if(block != null && block != Blocks.air){
                        button.replaceImage(new ImageStack(block.getCompactIcon()));
                    }
                });
                dialog.show();
                changed.run();
            });
            table.defaults().left();
            table.add(button).pad(4).size(40f);
            table.add("$text.filter.option." + name).padLeft(8);
            table.row();
        }
    }

    public static class ToggleOption extends FilterOption{
        final String name;
        final BooleanProvider getter;
        final Consumer<Boolean> setter;

        public ToggleOption(String name, BooleanProvider getter, Consumer<Boolean> setter){
            this.name = name;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void build(Table table){
            table.defaults().left();
            table.addCheck("$text.filter.option." + name, b -> {
                setter.accept(b);
                changed.run();
            }).checked(getter.get());
            table.row();
        }
    }

    /**Selects a tech tree from a group of buttons.*/
    public static class TechOption extends FilterOption{
        final String name;
        final Supplier<String> getter;
        final Consumer<String> setter;

        public TechOption(String name, Supplier<String> getter, Consumer<String> setter){
            this.name = name;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public void build(Table table){
            table.defaults().left();
            table.add("$text.filter.option." + name).width(150f).padRight(10);

            Table techs = new Table();
            techs.defaults().left().padRight(2f).padLeft(2f);
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            int i = 0;
            for(String tree : TechTree.all()){
                techs.addButton(TechTree.localizedName(tree), "toggle", () -> {
                    setter.accept(tree);
                    changed.run();
                }).update(b -> b.setChecked(current(getter).equals(tree))).group(group).size(100f, 34f);
                if(i++ % 2 == 1) techs.row();
            }
            table.add(techs).growX();
            table.row();
        }

        private String current(Supplier<String> getter){
            String value = getter.get();
            return value == null || value.isEmpty() || !TechTree.contains(value) ? TechTree.defaultTech : value;
        }
    }

    private static class FloatingBlockDialog extends io.anuke.mindustry.ui.dialogs.FloatingDialog{
        FloatingBlockDialog(String title, Predicate<Block> filter, Consumer<Block> consumer, Consumer<Block> onSelected){
            super("$text.filter.option." + title);
            Table blockTable = new Table();
            blockTable.defaults().left();
            blockTable.top();

            blockTable.addButton("[LIGHT_GRAY]None[]", () -> {
                consumer.accept(Blocks.air);
                if(onSelected != null) onSelected.accept(Blocks.air);
                hide();
            }).size(80f, 36f).pad(2);
            blockTable.row();

            int i = 0;
            int cols = 5;
            for(Block block : Vars.content.blocks()){
                if(!filter.test(block)) continue;

                ImageButton btn = new ImageButton("white", "clear-toggle");
                btn.getImageCell().size(32f);
                btn.replaceImage(new ImageStack(block.getCompactIcon()));
                btn.clicked(() -> {
                    consumer.accept(block);
                    if(onSelected != null) onSelected.accept(block);
                    hide();
                });
                blockTable.add(btn).size(50f).pad(2);

                if(++i % cols == 0) blockTable.row();
            }
            content().add(new ScrollPane(blockTable)).size(400f, 300f);
            addCloseButton();
        }
    }
}
