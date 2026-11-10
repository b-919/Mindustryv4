package io.anuke.mindustry.content;

import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.content.fx.ShootFx;
import io.anuke.mindustry.game.ContentList;
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
            alternate = false;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.explode;
            setShootSound("explosionCrawler");
        }};

        kamikazeDrone = new Weapon("kamikaze"){{
            reload = 12f;
            alternate = false;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.explode;
            setShootSound("explosionCrawler");
        }};

        blaster = new Weapon("blaster"){{
            shootY = 0f;
            reload = 14f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletMech;
        }};

        blasterSmall = new Weapon("blaster"){{
            shootY = 0f;
            x = 3f;
            reload = 15f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
            setShootSound("shootDart");
        }};

        glaiveBlaster = new Weapon("bomber"){{
            shootY = 1.5f;
            reload = 10f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletGlaive;
        }};

        droneBlaster = new Weapon("blaster"){{
            shootY = 2f;
            reload = 25f;
            x = 1f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        healBlaster = new Weapon("heal-blaster"){{
            shootY = 1.5f;
            reload = 24f;
            alternate = false;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        missiles = new Weapon("missiles"){{
            shootY = 1.5f;
            reload = 60f;
            shots = 4;
            inaccuracy = 2f;
            alternate = true;
            ejectEffect = Fx.none;
            velocityRnd = 0.2f;
            spacing = 1f;
            setShootSound("shootMissileSmall");
            ammo = AmmoTypes.weaponMissile;
        }};

        revenantMissiles = new Weapon("revenant-missiles"){{
            shootY = 3f;
            reload = 70f;
            x = 10f;
            shots = 2;
            inaccuracy = 2f;
            alternate = true;
            ejectEffect = Fx.none;
            velocityRnd = 0.2f;
            spacing = 1f;
            setShootSound("shootMissileSmall");
            ammo = AmmoTypes.weaponMissileSwarm;
        }};

        lichMissiles = new Weapon("lich-missiles"){{
            shootY = 4f;
            reload = 160f;
            shots = 16;
            spacing = 1f;
            velocityRnd = 0.2f;
            x = 22f;
            alternate = true;
            inaccuracy = 10f;
            ejectEffect = Fx.none;
            setShootSound("shootArtillery");
            ammo = AmmoTypes.weaponMissileSwarm;
        }};

        swarmer = new Weapon("swarmer"){{
            shootY = 1.5f;
            recoil = 4f;
            reload = 60f;
            shots = 4;
            spacing = 8f;
            inaccuracy = 8f;
            alternate = true;
            ejectEffect = Fx.none;
            shake = 3f;
            setShootSound("shootMissileSmall");
            ammo = AmmoTypes.weaponMissileSwarm;
        }};

        scrapLockBlaster = new Weapon("scrap-lock-blaster"){{
            shootY = 2.5f;
            reload = 64f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        chainBlaster = new Weapon("chain-blaster"){{
            shootY = 1.5f;
            reload = 28f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        attraxSpitter = new Weapon("attrax-weapon"){{
            x = 7f;
            shootY = 3f;
            reload = 9f;
            alternate = true;
            ejectEffect = Fx.none;
            recoil = 1f;
            ammo = AmmoTypes.slag;
            setShootSound("shootDart");
        }};

        scrapBlaster = new Weapon("scrap-blaster"){{
            shootY = 1.5f;
            reload = 48f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletCopper;
        }};

        shockgun = new Weapon("shockgun"){{
            shootY = 1f;
            reload = 40f;
            alternate = true;
            shots = 1;
            inaccuracy = 0f;
            velocityRnd = 0.2f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.shock;
            setShootSound("shootArc");
        }};

        flakgun = new Weapon("flakgun"){{
            shootY = 1f;
            reload = 70f;
            alternate = true;
            shots = 1;
            inaccuracy = 3f;
            recoil = 3f;
            velocityRnd = 0.1f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.shellCarbide;
        }};

        flamethrower = new Weapon("flamethrower"){{
            shootY = 1f;
            reload = 14f;
            alternate = true;
            recoil = 1f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.flamerThermite;
        }};

        artillery = new Weapon("artillery"){{
            shootY = 1f;
            reload = 60f;
            alternate = true;
            recoil = 4f;
            shake = 2f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.unitArtillery;
            setShootSound("shootArtillery");
        }};

        sapper = new Weapon("sapper"){{
            shootY = 1.5f;
            reload = 12f;
            alternate = true;
            ejectEffect = ShootFx.shellEjectSmall;
            ammo = AmmoTypes.bulletDense;
        }};

        bomber = new Weapon("bomber"){{
            shootY = 0f;
            x = 2f;
            reload = 12f;
            alternate = true;
            ejectEffect = Fx.none;
            velocityRnd = 1f;
            inaccuracy = 40f;
            ammo = AmmoTypes.bombExplosive;
        }};

        bomberTrident = new Weapon("bomber"){{
            shootY = 0f;
            x = 2f;
            reload = 9f;
            shots = 2;
            alternate = true;
            ejectEffect = Fx.none;
            velocityRnd = 1f;
            inaccuracy = 40f;
            ammo = AmmoTypes.bombExplosive;
        }};

        laserBurster = new Weapon("bomber"){{
            reload = 80f;
            shake = 3f;
            x = 0f;
            alternate = true;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.lancerLaser;
            setShootSound("shootLancer");
        }};

        healBlasterDrone = new Weapon("heal-blaster"){{
            shootY = 1.5f;
            reload = 40f;
            x = 0.5f;
            alternate = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        healBlasterDrone2 = new Weapon("heal-blaster"){{
            shootY = 1.5f;
            reload = 5f;
            x = 0.5f;
            alternate = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        healBlasterDrone3 = new Weapon("heal-blaster"){{
            shootY = 1.5f;
            reload = 20f;
            x = 0.5f;
            alternate = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
            setShootSound("shootLaser");
        }};

        mineBlaster = new Weapon("mine-blaster"){{
            shootY = 1.5f;
            reload = 20f;
            x = 0.5f;
            alternate = true;
            ejectEffect = Fx.none;
            recoil = 2f;
            ammo = AmmoTypes.healBlaster;
        }};

        chaos = new Weapon("chaos"){{
            shootY = 8;
            x = 17f;
            reload = 50f;
            alternate = true;
            recoil = 3f;
            shake = 2f;
            shots = 4;
            spacing = 4f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.flakSurge;
            setShootSound("shootBig");
        }};

        reaperGun = new Weapon("reaper-gun"){{
            shootY = 3f;
            reload = 10f;
            x = 32f;
            shots = 1;
            shake = 1f;
            alternate = true;
            inaccuracy = 3f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.flakSurge;
            setShootSound("shootBig");
        }};

        debugtankturret = new Weapon("debugtankturret"){{
            shootY = 1f;
            reload = 60f;
            alternate = false;
            recoil = 5f;
            shake = 2f;
            ejectEffect = ShootFx.shellEjectMedium;
            ammo = AmmoTypes.unitArtillery;
            setShootSound("shootArtillery");
        }};
        //Mass
        artilleryBiomass = new Weapon("artillery-biomass"){{
            shootY = 1f;
            reload = 60f;
            alternate = true;
            recoil = 3.5f;
            shake = 2f;
            x = -4f;
            shootY = 5.5f;
            mirror = false;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.unitArtillery;
            setShootSound("shootArtillery");
        }};
        exterminatorweapon = new Weapon("exterminator-weapon"){{
            shootY = 8;
            x = 17f;
            shootY = 10f;
            reload = 50f;
            alternate = true;
            recoil = 3f;
            shake = 2f;
            shots = 4;
            spacing = 4f;
            ejectEffect = Fx.none;
            ammo = AmmoTypes.flakSurge;
        }};
        mosquitoweapon = new Weapon("mosquito-weapon"){{
            mirror = false;
            shootY = 8f;
            reload = 20f;
            x = -0f;
            alternate = false;
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
            shootY = 13f;
            reload = 30f;
            x = 22f;
            alternate = true;
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
