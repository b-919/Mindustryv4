package io.anuke.mindustry.content;

import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.content.fx.ShootFx;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Weapon;

public class Weapons implements ContentList{
    public static Weapon blaster, blasterSmall, glaiveBlaster, droneBlaster, healBlaster, healBlasterDrone, scrapBlaster, scrapLockBlaster, chainBlaster, shockgun,
    kamikaze, kamikazeDrone, revenantMissiles, lichMissiles, reaperGun, chaos, eradicator,
    sapper, swarmer, bomber, bomberTrident, flakgun, flamethrower, missiles, artillery, laserBurster, healBlasterDrone2, healBlasterDrone3, mineBlaster,
    attraxSpitter,
    debugtankturret,
    artilleryBiomass, exterminatorweapon, mosquitoweapon, evilDaggerWeapon, evilTankyWeapon;// the mass

    @Override
    public void load(){

        kamikaze = new Weapon("kamikaze"){{
            reload = 12f;
            roundrobin = false;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.explode;
            setShootSound("explosionCrawler");
        }};

        kamikazeDrone = new Weapon("kamikaze"){{
            reload = 12f;
            roundrobin = false;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.explode;
            setShootSound("explosionCrawler");
        }};

        blaster = new Weapon("blaster"){{
            length = 0f;
            reload = 14f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletMech;
        }};

        blasterSmall = new Weapon("blaster"){{
            length = 0f;
            width = 3f;
            reload = 15f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
            setShootSound("shootDart");
        }};

        glaiveBlaster = new Weapon("bomber"){{
            length = 1.5f;
            reload = 10f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletGlaive;
        }};

        droneBlaster = new Weapon("blaster"){{
            length = 2f;
            reload = 25f;
            width = 1f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        healBlaster = new Weapon("heal-blaster"){{
            length = 1.5f;
            reload = 24f;
            roundrobin = false;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        missiles = new Weapon("missiles"){{
            length = 1.5f;
            reload = 60f;
            shots = 4;
            inaccuracy = 2f;
            roundrobin = true;
            ejectEffect = Fx.none;
            velocityRnd = 0.2f;
            spacing = 1f;
            setShootSound("shootMissileSmall");
            ammo = AmmoTypes.weaponMissile;
        }};

        revenantMissiles = new Weapon("revenant-missiles"){{
            length = 3f;
            reload = 70f;
            width = 10f;
            shots = 2;
            inaccuracy = 2f;
            roundrobin = true;
            ejectEffect = Fx.none;
            velocityRnd = 0.2f;
            spacing = 1f;
            setShootSound("shootMissileSmall");
            ammo = AmmoTypes.weaponMissileSwarm;
        }};

        lichMissiles = new Weapon("lich-missiles"){{
            length = 4f;
            reload = 160f;
            shots = 16;
            spacing = 1f;
            velocityRnd = 0.2f;
            width = 22f;
            roundrobin = true;
            inaccuracy = 10f;
            ejectEffect = Fx.none;
            setShootSound("shootArtillery");
            ammo = AmmoTypes.weaponMissileSwarm;
        }};

        swarmer = new Weapon("swarmer"){{
            length = 1.5f;
            recoil = 4f;
            reload = 60f;
            shots = 4;
            spacing = 8f;
            inaccuracy = 8f;
            roundrobin = true;
            ejectEffect = Fx.none;
            shake = 3f;
            setShootSound("shootMissileSmall");
            ammo = AmmoTypes.weaponMissileSwarm;
        }};

        scrapLockBlaster = new Weapon("scrap-lock-blaster"){{
            length = 2.5f;
            reload = 64f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        chainBlaster = new Weapon("chain-blaster"){{
            length = 1.5f;
            reload = 28f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        attraxSpitter = new Weapon("attrax-weapon"){{
            width = 7f;
            length = 3f;
            reload = 9f;
            roundrobin = true;
            ejectEffect = Fx.none;
            recoil = 1f;
            ammo = AmmoTypes.slag;
            setShootSound("shootDart");
        }};

        scrapBlaster = new Weapon("scrap-blaster"){{
            length = 1.5f;
            reload = 48f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        shockgun = new Weapon("shockgun"){{
            length = 1f;
            reload = 40f;
            roundrobin = true;
            shots = 1;
            inaccuracy = 0f;
            velocityRnd = 0.2f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.shock;
            setShootSound("shootArc");
        }};

        flakgun = new Weapon("flakgun"){{
            length = 1f;
            reload = 70f;
            roundrobin = true;
            shots = 1;
            inaccuracy = 3f;
            recoil = 3f;
            velocityRnd = 0.1f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.shellCarbide;
        }};

        flamethrower = new Weapon("flamethrower"){{
            length = 1f;
            reload = 14f;
            roundrobin = true;
            recoil = 1f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.flamerThermite;
        }};

        artillery = new Weapon("artillery"){{
            length = 1f;
            reload = 60f;
            roundrobin = true;
            recoil = 4f;
            shake = 2f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.unitArtillery;
            setShootSound("shootArtillery");
        }};

        sapper = new Weapon("sapper"){{
            length = 1.5f;
            reload = 12f;
            roundrobin = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletDense;
        }};

        bomber = new Weapon("bomber"){{
            length = 0f;
            width = 2f;
            reload = 12f;
            roundrobin = true;
            ejectEffect = Fx.none;
            velocityRnd = 1f;
            inaccuracy = 40f;
            ammo = AmmoTypes.bombExplosive;
        }};

        bomberTrident = new Weapon("bomber"){{
            length = 0f;
            width = 2f;
            reload = 9f;
            shots = 2;
            roundrobin = true;
            ejectEffect = Fx.none;
            velocityRnd = 1f;
            inaccuracy = 40f;
            ammo = AmmoTypes.bombExplosive;
        }};

        laserBurster = new Weapon("bomber"){{
            reload = 80f;
            shake = 3f;
            width = 0f;
            roundrobin = true;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.lancerLaser;
            setShootSound("shootLancer");
        }};

        healBlasterDrone = new Weapon("heal-blaster"){{
            length = 1.5f;
            reload = 40f;
            width = 0.5f;
            roundrobin = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        healBlasterDrone2 = new Weapon("heal-blaster"){{
            length = 1.5f;
            reload = 5f;
            width = 0.5f;
            roundrobin = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        healBlasterDrone3 = new Weapon("heal-blaster"){{
            length = 1.5f;
            reload = 20f;
            width = 0.5f;
            roundrobin = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        mineBlaster = new Weapon("mine-blaster"){{
            length = 1.5f;
            reload = 20f;
            width = 0.5f;
            roundrobin = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
        }};

        chaos = new Weapon("chaos"){{
            length = 8;
            width = 17f;
            reload = 50f;
            roundrobin = true;
            recoil = 3f;
            shake = 2f;
            shots = 4;
            spacing = 4f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.flakSurge;
            setShootSound("shootBig");
        }};

        reaperGun = new Weapon("reaper-gun"){{
            length = 3f;
            reload = 10f;
            width = 32f;
            shots = 1;
            shake = 1f;
            roundrobin = true;
            inaccuracy = 3f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.flakSurge;
            setShootSound("shootBig");
        }};

        debugtankturret = new Weapon("debugtankturret"){{
            length = 1f;
            reload = 60f;
            roundrobin = false;
            recoil = 5f;
            shake = 2f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.unitArtillery;
            setShootSound("shootArtillery");
        }};
        //Mass
        artilleryBiomass = new Weapon("artillery-biomass"){{
            length = 1f;
            reload = 60f;
            roundrobin = true;
            recoil = 3.5f;
            shake = 2f;
            width = -4f;
            length = 5.5f;
            weaponMirror = false;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.unitArtillery;
            setShootSound("shootArtillery");
        }};
        exterminatorweapon = new Weapon("exterminator-weapon"){{
            length = 8;
            width = 17f;
            length = 10f;
            reload = 50f;
            roundrobin = true;
            recoil = 3f;
            shake = 2f;
            shots = 4;
            spacing = 4f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.flakSurge;
        }};
        mosquitoweapon = new Weapon("mosquito-weapon"){{
            weaponMirror = false;
            length = 8f;
            reload = 20f;
            width = -0f;
            roundrobin = false;
            recoil = 1f;
            ejectEffect = Fx.none;

            ammo = AmmoTypes.bulletPyratite;
        }};

        evilDaggerWeapon = new Weapon("evil-dagger-weapon"){{
            shootY = 1.5f;
            x = 3f;
            reload = 28f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        evilTankyWeapon = new Weapon("evil-tanky-weapon"){{
            shootY = 4f;
            reload = 14f;
            x = 15f;
            alternate = true;
            recoil = 1f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.flamerThermite;
        }};

        eradicator = new Weapon("eradicator-weapon"){{
            length = 13f;
            reload = 30f;
            width = 22f;
            roundrobin = true;
            recoil = 3f;
            shake = 2f;
            inaccuracy = 3f;
            shots = 4;
            spacing = 0f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.bulletThoriumBig;
            setShootSound("shootBig");
        }};
    }

    @Override
    public ContentType type(){
        return ContentType.weapon;
    }
}
