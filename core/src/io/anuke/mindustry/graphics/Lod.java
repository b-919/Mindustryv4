package io.anuke.mindustry.graphics;

import io.anuke.ucore.util.Mathf;

import static io.anuke.mindustry.Vars.renderer;

/**
 * Level of detail. Tracks how many screen pixels one world unit covers, and derives two
 * fade levels from it so that detail which is sub-pixel when zoomed out can be faded out
 * (and then skipped entirely) instead of being drawn as a shimmering mess.
 */
public class Lod{
    /** Scale (pixels per world unit) at which level 1 and 2 content stop being drawn. */
    private static final float threshold1 = 1.4f, threshold2 = 0.8f;
    /** Width of the fade range, in scale units. */
    private static final float fade = 0.2f;

    /** When true, all LOD content is drawn at full opacity. */
    public static boolean disable = false;

    /** Whether level 1 content should be drawn at all. */
    public static boolean l1 = true;
    /** Opacity of level 1 content. */
    public static float alpha1 = 1f;

    /** Whether level 2 content should be drawn at all. */
    public static boolean l2 = true;
    /** Opacity of level 2 content. */
    public static float alpha2 = 1f;

    public static void update(){
        if(disable){
            l1 = l2 = true;
            alpha1 = alpha2 = 1f;
            return;
        }

        float scale = renderer.getDisplayScale();

        alpha1 = Mathf.clamp((scale - threshold1) / fade, 0f, 1f);
        alpha2 = Mathf.clamp((scale - threshold2) / fade, 0f, 1f);
        l1 = alpha1 >= 1f / 255f;
        l2 = alpha2 >= 1f / 255f;
    }
}
