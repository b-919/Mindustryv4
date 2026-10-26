package io.anuke.mindustry.world.special;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.entities.bullet.BulletType;
import io.anuke.mindustry.entities.bullet.RailBulletType;

public class ApathySentryLaserBulletType extends RailBulletType{
    public ApathySentryLaserBulletType(){
        super(900f);
        length = 1400f;
        pierce = true;
    }
}
