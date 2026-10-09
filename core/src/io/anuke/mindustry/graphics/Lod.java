package io.anuke.mindustry.graphics;

import io.anuke.ucore.core.Settings;
import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.baseCameraScale;
import static io.anuke.mindustry.Vars.renderer;

/**
 * Level of detail. Tracks how many screen pixels one world unit covers, and derives two
 * fade levels from it so that detail which is sub-pixel when zoomed out can be faded out
 * (and then skipped entirely) instead of being drawn as a shimmering mess.
 */
public class Lod{
    private static final float end1 = 0.5f, start1 = 0.9f, end2 = 0.5f, start2 = 0.78f;

    /** When true, all LOD content is drawn at full opacity. Used to force full detail while
     *  rendering a map screenshot; the player's own preference is read from settings instead. */
    public static boolean disable = false;

    /** The settings key of the preference that turns LOD off entirely. */
    private static final String setting = "lod";

    /** Whether level 1 content should be drawn at all. */
    public static boolean l1 = true;
    /** Opacity of level 1 content. */
    public static float alpha1 = 1f;

    /** Whether level 2 content should be drawn at all. */
    public static boolean l2 = true;
    /** Opacity of level 2 content. */
    public static float alpha2 = 1f;

    public static void update(){
        //the preference is read here rather than applied through a settings callback so that it
        //takes effect on the same frame it changes, and survives a reset to defaults
        if(disable || !Settings.getBool(setting, true)){
            l1 = l2 = true;
            alpha1 = alpha2 = 1f;
            return;
        }

        float scale = renderer.getDisplayScale() / baseCameraScale;

        alpha1 = 1f - Mathf.clamp((start1 - scale) / (start1 - end1), 0f, 1f);
        alpha2 = 1f - Mathf.clamp((start2 - scale) / (start2 - end2), 0f, 1f);
        l1 = alpha1 >= 1f / 255f;
        l2 = alpha2 >= 1f / 255f;
    }
}