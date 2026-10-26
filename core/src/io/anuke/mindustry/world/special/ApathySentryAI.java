package io.anuke.mindustry.world.special;

import io.anuke.mindustry.Vars;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.ai.AIController;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Tmp;

public class ApathySentryAI extends AIController{
    private final ApathySentryUnit sentry;

    public ApathySentryAI(BaseUnit unit){
        super(unit);
        sentry = (ApathySentryUnit)unit;
    }

    @Override
    public void updateUnit(){
        if(sentry.owner == null || !sentry.owner.isValid()){
            sentry.remove();
            return;
        }

        float lx = (sentry.moveX - sentry.x) * 0.25f * sentry.moveTime;
        float ly = (sentry.moveY - sentry.y) * 0.25f * sentry.moveTime;
        sentry.moveTime = Mathf.clamp(sentry.moveTime + Timers.delta() / 20f);
        sentry.x += lx;
        sentry.y += ly;

        if(!sentry.active){
            return;
        }

        if(sentry.healDelay > 0){
            sentry.rotation = Angles.moveToward(sentry.rotation, sentry.angleTo(sentry.owner), 15f);
            sentry.healDelay -= Timers.delta();
            sentry.healFade = Mathf.clamp(sentry.healFade + Timers.delta() / 12f);
            if(sentry.healDelay <= 0f){
                sentry.owner.healBy(sentry.owner.maxHealth() / 20f);
                sentry.reload = 0f;
            }
        }else{
            if(Units.invalidateTarget(sentry.target, sentry.getTeam(), sentry.x, sentry.y, FlameBullets.sentryLaser.length - 50f)){
                sentry.target = null;
            }

            if(sentry.target != null){
                sentry.rotation = Angles.moveToward(sentry.rotation, sentry.angleTo(sentry.target), 15f);
                if(sentry.reload > 0){
                    sentry.reload -= Timers.delta();
                    if(sentry.reload <= 0f){
                        Tmp.v1.trns(sentry.rotation, sentry.type.hitsize / 1.5f).add(sentry.x, sentry.y);
                        Vars.soundController.at(Sounds.shootLaser, Tmp.v1.x, Tmp.v1.y, 1f, 1f);
                        Bullet.create(FlameBullets.sentryLaser, sentry, sentry.getTeam(), Tmp.v1.x, Tmp.v1.y, sentry.rotation);
                    }
                }
            }else{
                sentry.rotation = Angles.moveToward(sentry.rotation, sentry.angleTo(sentry.owner), 15f);
            }
        }
    }
}
