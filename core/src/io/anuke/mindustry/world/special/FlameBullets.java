package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.ucore.graphics.*;
import io.anuke.mindustry.entities.bullet.*;

public class FlameBullets{
    public static BulletType smallLaser, sweep, aoe, bigLaser, sentryLaser, pin, tracker, sword;

    public static void load(){
        smallLaser = new ApathySmallLaserBulletType();
        sweep = new ApathySweepLaserBulletType();
        aoe = new ApathyAoEBulletType();
        bigLaser = new ApathyBigLaserBulletType();

        sentryLaser = new ApathySentryLaserBulletType();

        pin = new EmpathyPinBulletType();
        tracker = new EmpathyTrackerBulletType();
        sword = new EmpathySwordBulletType();

    }
}
