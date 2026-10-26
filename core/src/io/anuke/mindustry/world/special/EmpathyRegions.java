package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.graphics.Draw;

public class EmpathyRegions{
    public static TextureRegion sword, swordSide, swordTrail, portal, magicCircle, magicCircle2, circle, hcircle, flash, decoy, hand;
    static TextureRegion[] countDown = new TextureRegion[6], endAPI = new TextureRegion[3];
    static SegmentedRegion swordSeg, swordSideSeg;

    public static void load(){
        sword = Draw.region("flameout-sword");
        swordSide = Draw.region("flameout-sword-side");
        swordSeg = new SegmentedRegion("flameout-sword");
        swordSideSeg = new SegmentedRegion("flameout-sword-side");
        swordTrail = Draw.region("flameout-sword-trail");
        portal = Draw.region("flameout-portal");
        magicCircle = Draw.region("flameout-magic-circle");
        magicCircle2 = Draw.region("flameout-magic-circle-2");
        circle = Draw.region("circle");
        hcircle = Draw.region("hcircle");
        flash = Draw.region("flameout-flash");
        decoy = Draw.region("flameout-empathy-decoy");
        hand = Draw.region("flameout-hand");

        for(int i = 0; i < countDown.length; i++){
            countDown[i] = Draw.region("flameout-count-down-" + i);
        }
        for(int i = 0; i < endAPI.length; i++){
            endAPI[i] = Draw.region("flameout-end-api-" + i);
        }
    }
}
