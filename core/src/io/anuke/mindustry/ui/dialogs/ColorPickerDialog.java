package io.anuke.mindustry.ui.dialogs;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.function.Consumer;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.graphics.Hue;
import io.anuke.ucore.graphics.Pixmaps;
import io.anuke.ucore.scene.Element;
import io.anuke.ucore.scene.style.BaseDrawable;
import io.anuke.ucore.scene.ui.Dialog;
import io.anuke.ucore.scene.ui.Image;
import io.anuke.ucore.scene.ui.Slider;
import io.anuke.ucore.scene.ui.Slider.SliderStyle;
import io.anuke.ucore.scene.ui.TextField;
import io.anuke.ucore.scene.ui.layout.Stack;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Tmp;

/**
 * HSV colour picker, ported from the modern Mindustry {@code ColorPicker}.
 * <p>
 * Provides a live preview, hue/saturation/value sliders and a hex input. Used by blocks that
 * store a configurable colour, such as the illuminator.
 */
public class ColorPickerDialog extends Dialog{
    private static Texture hueTex;
    private static Sprite gradientSprite;

    private Consumer<Color> cons = c -> {};
    private final Color current = new Color(Color.WHITE);
    private final float[] hsv = new float[3];
    private float h, s, v;
    /** Guards the slider/field feedback loop while values are being synchronised. */
    private boolean updating;
    private TextField hexField;
    private Slider hSlider, sSlider, vSlider;

    public ColorPickerDialog(){
        super("$text.pickcolor", "dialog");

        buttons().defaults().size(130f, 50f).pad(6f);
        buttons().addButton("$text.cancel", this::hide);
        buttons().addButton("$text.ok", () -> {
            current.a = 1f;
            cons.accept(current);
            hide();
        });

        keyDown(key -> {
            if(key == Keys.ESCAPE || key == Keys.BACK){
                hide();
            }
        });
    }

    public void show(Color color, Consumer<Color> cons){
        this.cons = cons;
        current.set(color);
        current.a = 1f;

        Hue.RGBtoHSB(current, hsv);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];

        content().clear();
        build();
        show();
    }

    private void build(){
        Table table = content();
        table.defaults().padBottom(6f);

        //live preview of the selected colour
        table.add(new Element(){
            @Override
            public void draw(){
                gradient(getX(), getY(), getWidth(), getHeight(), current, current, 1f);
            }
        }).size(280f, 56f).row();

        if(hueTex == null){
            hueTex = Pixmaps.hueTexture(128, 1);
            hueTex.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        }

        //hue
        hSlider = newSlider(h);
        hSlider.moved(value -> {
            if(updating) return;
            h = value;
            updateColor();
        });
        table.add(new Stack(new Image(new TextureRegion(hueTex)), hSlider)).size(280f, 26f).row();

        //saturation
        sSlider = newSlider(s);
        sSlider.moved(value -> {
            if(updating) return;
            s = value;
            updateColor();
        });
        table.add(new Stack(new Element(){
            @Override
            public void draw(){
                gradient(getX(), getY(), getWidth(), getHeight(),
                        Hue.fromHSB(h, 0f, v, Tmp.c1), Hue.fromHSB(h, 1f, v, Tmp.c2), 1f);
            }
        }, sSlider)).size(280f, 26f).row();

        //value (brightness)
        vSlider = newSlider(v);
        vSlider.moved(value -> {
            if(updating) return;
            v = value;
            updateColor();
        });
        table.add(new Stack(new Element(){
            @Override
            public void draw(){
                gradient(getX(), getY(), getWidth(), getHeight(),
                        Hue.fromHSB(h, s, 0f, Tmp.c1), Hue.fromHSB(h, s, 1f, Tmp.c2), 1f);
            }
        }, vSlider)).size(280f, 26f).row();

        //hex input
        hexField = new TextField(hex());
        hexField.setMaxLength(8);
        hexField.changed(() -> {
            if(updating) return;

            String text = hexField.getText();
            if(text.length() != 6 && text.length() != 8) return;

            try{
                current.set(Color.valueOf(text));
                current.a = 1f;

                Hue.RGBtoHSB(current, hsv);
                h = hsv[0];
                s = hsv[1];
                v = hsv[2];

                updating = true;
                hSlider.setValue(h);
                sSlider.setValue(s);
                vSlider.setValue(v);
                updating = false;
            }catch(Exception ignored){
                //invalid hex, keep the last valid colour
            }
        });
        table.add(hexField).size(170f, 44f).padTop(4f).row();
    }

    private Slider newSlider(float value){
        SliderStyle base = Core.skin.get("default-horizontal", SliderStyle.class);
        SliderStyle style = new SliderStyle();
        style.knob = base.knob;
        style.knobOver = base.knobOver;
        style.knobDown = base.knobDown;
        //transparent track: the gradient element behind the slider is the track
        style.background = new BaseDrawable();

        Slider slider = new Slider(0f, 1f, 0.0001f, false, style);
        slider.setValue(value);
        return slider;
    }

    private void updateColor(){
        Hue.fromHSB(h, s, v, current);
        current.a = 1f;

        if(hexField != null){
            updating = true;
            hexField.setText(hex());
            updating = false;
        }
    }

    private String hex(){
        return current.toString().substring(0, 6);
    }

    /** Horizontal gradient using the shared blank sprite. */
    private static void gradient(float x, float y, float width, float height, Color left, Color right, float alpha){
        if(gradientSprite == null){
            gradientSprite = new Sprite(Draw.getBlankRegion());
        }

        gradientSprite.setBounds(x, y, width, height);

        Tmp.c1.set(left);
        Tmp.c1.a = left.a * alpha;
        float cl = Tmp.c1.toFloatBits();

        Tmp.c1.set(right);
        Tmp.c1.a = right.a * alpha;
        float cr = Tmp.c1.toFloatBits();

        float[] vertices = gradientSprite.getVertices();
        vertices[SpriteBatch.C1] = cl;
        vertices[SpriteBatch.C4] = cl;
        vertices[SpriteBatch.C2] = cr;
        vertices[SpriteBatch.C3] = cr;

        gradientSprite.draw(Core.batch);
    }
}
