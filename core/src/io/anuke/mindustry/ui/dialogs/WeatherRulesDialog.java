package io.anuke.mindustry.ui.dialogs;

import io.anuke.mindustry.type.Weather;
import io.anuke.ucore.scene.ui.CheckBox;
import io.anuke.ucore.scene.ui.Label;
import io.anuke.ucore.scene.ui.ScrollPane;
import io.anuke.ucore.scene.ui.Slider;
import io.anuke.ucore.scene.ui.TextButton;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Bundles;

import static io.anuke.mindustry.Vars.weather;

/**Lets the player configure the weather events of a custom game*/
public class WeatherRulesDialog extends FloatingDialog{
    private final Table list = new Table();
    private final Label status = new Label("");
    private CheckBox auto;

    public WeatherRulesDialog(){
        super("$text.weather.title");

        content().add(new ScrollPane(list)).grow();
        content().row();
        addCloseButton();

        shown(this::rebuild);
    }

    private void rebuild(){
        list.clearChildren();

        list.top().defaults().pad(3f).top().left();

        auto = new CheckBox("$text.weather.auto");
        auto.setChecked(weather.isAutoWeather());
        auto.changed(() -> weather.setAutoWeather(auto.isChecked()));
        list.add(auto).left().pad(3f).padBottom(4f);
        list.row();

        CheckBox daynight = new CheckBox("$text.weather.daynight");
        daynight.setChecked(weather.isDayNight());
        daynight.changed(() -> weather.setDayNight(daynight.isChecked()));
        list.add(daynight).left().pad(3f).padBottom(4f);
        list.row();

        int cycle = (int) weather.getCycleDuration();
        Label cycleLabel = new Label(Bundles.get("text.weather.cycle", "Cycle duration") + ": " + cycle + "min");
        Slider cycleSlider = new Slider(1, 60, 1, false);
        cycleSlider.setValue(cycle);
        cycleSlider.changed(() -> {
            int val = (int) cycleSlider.getValue();
            weather.setCycleDuration(val);
            cycleLabel.setText(Bundles.get("text.weather.cycle", "Cycle duration") + ": " + val + "min");
        });

        Table cycleRow = new Table();
        cycleRow.add(cycleLabel).left().pad(2f);
        cycleRow.add(cycleSlider).width(220f).pad(2f);
        list.add(cycleRow).left().pad(3f).padBottom(4f);
        list.row();

        updateStatus();
        list.add(status).left().pad(3f).padBottom(8f);
        list.row();

        Weather[] weathers = weather.weathers();
        for(int i = 0; i < weathers.length; i++){
            list.add(card(weathers[i], i)).top().left();
            list.row();
        }
    }

    private void updateStatus(){
        int sel = weather.selected();
        String name = sel < 0 ? Bundles.get("text.weather.none", "None")
                : Bundles.get("text.weather." + weather.weathers()[sel].name,
                weather.weathers()[sel].name);
        status.setText("[accent]" + Bundles.get("text.weather.selected", "Selected") + ":[] " + name);
    }

    private Table card(Weather type, int index){
        String name = Bundles.get("text.weather." + type.name, type.name);

        Table card = new Table();
        card.background("button").margin(8f).top().left();

        card.add(name).colspan(2).left().padTop(2f).padBottom(6f);
        card.row();

        TextButton pick = new TextButton("$text.weather.pick");
        pick.getLabel().setWrap(false);
        pick.clicked(() -> {
            weather.select(weather.selected() == index ? -1 : index);
            weather.setAutoWeather(false);
            auto.setChecked(false);
            updateStatus();
        });
        card.add(pick).colspan(2).growX().pad(2f);
        card.row();

        CheckBox always = new CheckBox("$text.weather.always");
        always.setChecked(weather.isAlways(type));
        always.changed(() -> {
            weather.setAlways(type, always.isChecked());
            if(always.isChecked()){
                weather.select(index);
            }
            updateStatus();
        });
        card.add(always).colspan(2).left().pad(2f);
        card.row();

        int minFreq = (int) weather.getMinFrequency(type);
        int maxFreq = (int) weather.getMaxFrequency(type);
        int duration = (int) weather.getDuration(type);

        Label minLabel = new Label(Bundles.get("text.weather.minfreq", "Min time") + ": " + minFreq + "min");
        Label maxLabel = new Label(Bundles.get("text.weather.maxfreq", "Max time") + ": " + maxFreq + "min");
        Label durLabel = new Label(Bundles.get("text.weather.duration", "Duration") + ": " + duration + "min");

        Slider minSlider = new Slider(0, 60, 1, false);
        minSlider.setValue(minFreq);
        Slider maxSlider = new Slider(0, 60, 1, false);
        maxSlider.setValue(maxFreq);
        Slider durSlider = new Slider(1, 60, 1, false);
        durSlider.setValue(duration);

        minSlider.changed(() -> {
            int val = (int) minSlider.getValue();
            int maxVal = (int) maxSlider.getValue();
            if(val > maxVal){
                minSlider.setValue(maxVal);
                val = (int) minSlider.getValue();
            }
            weather.setMinFrequency(type, val);
            minLabel.setText(Bundles.get("text.weather.minfreq", "Min time") + ": " + val + "min");
        });

        maxSlider.changed(() -> {
            int val = (int) maxSlider.getValue();
            int minVal = (int) minSlider.getValue();
            if(val < minVal){
                maxSlider.setValue(minVal);
                val = (int) maxSlider.getValue();
            }
            weather.setMaxFrequency(type, val);
            maxLabel.setText(Bundles.get("text.weather.maxfreq", "Max time") + ": " + val + "min");
        });

        durSlider.changed(() -> {
            int val = (int) durSlider.getValue();
            weather.setDuration(type, val);
            durLabel.setText(Bundles.get("text.weather.duration", "Duration") + ": " + val + "min");
        });

        card.add(minLabel).left().pad(2f);
        card.add(minSlider).width(220f).pad(2f);
        card.row();
        card.add(maxLabel).left().pad(2f);
        card.add(maxSlider).width(220f).pad(2f);
        card.row();
        card.add(durLabel).left().pad(2f);
        card.add(durSlider).width(220f).pad(2f);

        return card;
    }
}
