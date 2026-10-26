package io.anuke.mindustry.world.special;

import com.badlogic.gdx.audio.Sound;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.*;
import io.anuke.mindustry.entities.*;
import io.anuke.mindustry.sounds.Sounds;

public class FlameSounds{
    public static Sound apathyDeath, apathyDeathCry, apathyBleed, laserCharge, bigCharge, laserSmall, laserBig, aoeShoot, transform, largeTransform, clang, idle,
        empathyParry, empathyBigLaser, empathyCharge, empathySquareCharge, empathySquareShoot, empathyHologramActive, empathySmallEnd, empathyBlast,
        empathyRendSlash, empathyRendSwing, empathyDash, empathyDash2, empathyRico, empathyBlackHole, empathyShine, empathyTeleport,
        portalOrder, portalChaos, screams, silence,

        desSpearHit, desSpearCry, desLaser, desLaserShoot, desRailgun, desRailHit, desNukeShoot, desNukeHit, desNukeHitFar,

        expOrganic, expExotic, expElectric, expDecoy;

    static void load(){
        apathyBleed = sound("apathy-bleed");
        apathyDeath = sound("apathy-death");
        apathyDeathCry = sound("apathy-death-cry");

        laserCharge = sound("apathy-laser-charge");
        bigCharge = sound("apathy-big-charge");
        laserSmall = sound("apathy-laser-small");
        laserBig = sound("apathy-laser-large");
        aoeShoot = sound("apathy-aoe-shoot");
        transform = sound("apathy-transform");
        largeTransform = sound("apathy-transform-large");
        clang = sound("apathy-base");
        idle = sound("apathy-idle");

        empathyParry = sound("empathy-parry");
        empathyBigLaser = sound("empathy-big-laser");
        empathyCharge = sound("empathy-charge");
        empathySquareCharge = sound("empathy-square-charge");
        empathySquareShoot = sound("empathy-square-shoot");
        empathyHologramActive = sound("empathy-hologram-active");
        empathySmallEnd = sound("empathy-end-small");
        empathyBlast = sound("empathy-blast");

        empathyRendSlash = sound("empathy-rend-slash");
        empathyRendSwing = sound("empathy-rend-swing");
        empathyDash = sound("empathy-dash");
        empathyDash2 = sound("empathy-dash-altb");
        empathyRico = sound("empathy-ricochet");
        empathyBlackHole = sound("empathy-blackhole");
        empathyShine = sound("empathy-shine");
        empathyTeleport = sound("empathy-teleport");

        portalOrder = sound("portal-order");
        portalChaos = sound("portal-chaos");

        screams = sound("screams-of-the-damned");
        silence = sound("flameout-silence");

        desSpearHit = sound("des-spear-hit");
        desSpearCry = sound("des-spear-cry");

        desLaser = sound("des-laser");
        desLaserShoot = sound("des-laser-shoot");
        desRailgun = sound("des-railgun");
        desRailHit = sound("des-railgun-hit");
        desNukeShoot = sound("des-nuke-shoot");
        desNukeHit = sound("des-nuke-hit");
        desNukeHitFar = sound("des-nuke-hit-far");

        expOrganic = sound("explosion-organic");
        expExotic = sound("explosion-exotic");
        expElectric = sound("explosion-electric");
        expDecoy = sound("explosion-empathy-decoy");
    }

    private static Sound sound(String name){
        Sound s = Vars.soundController.get(name);
        return s == null ? Sounds.none : s;
    }
}
