package io.anuke.mindustry.type;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Rectangle;
import io.anuke.mindustry.game.Content;
import io.anuke.ucore.core.Core;

import static io.anuke.mindustry.Vars.renderer;

/**
 * Base class for weather content.
 * Weather is rendered and managed by {@link io.anuke.mindustry.graphics.WeatherRenderer}.
 * Adapted from modern Mindustry's Weather class.
 */
public abstract class Weather extends Content{
    /**Shared random used for rendering. Seeded every frame for a consistent animation.*/
    public static final RandomXS128 rand = new RandomXS128();
    /**Name of this weather, e.g. "rain".*/
    public final String name;
    /**Current opacity of this weather, managed by the renderer.*/
    public float opacity;
    /**Area the particles are laid out over, larger than the screen so they can wrap around the view.*/
    protected final Rectangle rect = new Rectangle();
    /**Area the player can actually see, used to skip particles that are off screen.*/
    protected final Rectangle visible = new Rectangle();

    public Weather(String name){
        this.name = name;
    }

    @Override
    public ContentType getContentType(){
        return ContentType.weather;
    }

    /**
     * Sets up {@link #rect} and {@link #visible} for drawing particles this frame.
     * The particle field is sized from the most zoomed-out camera scale rather than the current
     * one, so that zooming never changes the grid the particles wrap around and the pattern
     * doesn't jump or reset when the player changes zoom. {@link #visible} stays based on the
     * current viewport, so nothing outside the screen is drawn.
     */
    protected void viewRect(float padding){
        float scale = renderer.minScale();
        float width = Gdx.graphics.getWidth() / scale, height = Gdx.graphics.getHeight() / scale;

        rect.set(Core.camera.position.x - width / 2f - padding,
                Core.camera.position.y - height / 2f - padding,
                width + padding * 2f,
                height + padding * 2f);

        visible.set(Core.camera.position.x - Core.camera.viewportWidth / 2f,
                Core.camera.position.y - Core.camera.viewportHeight / 2f,
                Core.camera.viewportWidth,
                Core.camera.viewportHeight);
    }

    /**Called every game tick while this weather is active.*/
    public void update(float delta){
    }

    /**Draws this weather on top of the world, e.g. rain streaks.*/
    public void drawOver(float alpha){
    }

    /**Draws weather effects that appear under units, e.g. splashes.*/
    public void drawUnder(float alpha){
    }
}
