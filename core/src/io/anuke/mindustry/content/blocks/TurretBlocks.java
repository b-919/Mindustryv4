package io.anuke.mindustry.content.blocks;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.AmmoTypes;
import io.anuke.mindustry.content.fx.BlockFx;
import io.anuke.mindustry.content.fx.ShootFx;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.defense.turrets.*;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;

public class TurretBlocks extends BlockList implements ContentList{
    public static Block duo, scatter,
            scorch, hail, wave, lancer, arc, swarmer, salvo, fuse, ripple, cyclone, spectre, meltdown, foreshadow,
            //The mass
        evilScatter, evilDuo, evilSalvo, evilRipple, evilFuse, evilCyclone;
            /*TODO
            *  Turrets For Infection
            *  Basic ground/air target turret
            *  Artillery Turret
            *  Medium ground/air target turret
            *
            *  */

    @Override
    public void load(){
        duo = new DoubleTurret("duo"){{
            ammoTypes = new AmmoType[]{AmmoTypes.bulletCopper, AmmoTypes.bulletDense, AmmoTypes.bulletPyratite, AmmoTypes.bulletSilicon};
            reload = 25f;
            restitution = 0.03f;
            range = 90f;
            shootCone = 15f;
            setShootSound("shootDuo");
            ammoUseEffect = ShootFx.shellEjectSmall;
            health = 80;
            inaccuracy = 2f;
            rotatespeed = 10f;
        }};

        scatter = new BurstTurret("scatter"){{
            ammoTypes = new AmmoType[]{AmmoTypes.flakScrap, AmmoTypes.flakLead, AmmoTypes.flakObsidian};
            reload = 18f;
            range = 170f;
            size = 2;
            burstSpacing = 5f;
            shots = 3;
            targetGround = false;
            targetAir = true;
            ammoUseEffect = ShootFx.shellEjectMedium;
            setShootSound("shootScatter");
            recoil = 2f;
            rotatespeed = 15f;
            inaccuracy = 17f;
            shootCone = 35f;
            health = 20 * size * size;
        }};

        hail = new ArtilleryTurret("hail"){{
            ammoTypes = new AmmoType[]{AmmoTypes.artilleryDense, AmmoTypes.artilleryHoming, AmmoTypes.artilleryIncindiary};
            setShootSound("shootArtillerySmall");
            reload = 60f;
            recoil = 2f;
            range = 230f;
            inaccuracy = 1f;
            shootCone = 10f;
            health = 120;
            targetAir = false;
        }};

        scorch = new LiquidTurret("scorch"){
            protected TextureRegion shootRegion;

            @Override
            public void load(){
                super.load();
                shootRegion = Draw.region(name + "-shoot");
            }

            {
                ammoTypes = new AmmoType[]{AmmoTypes.basicFlame};
                recoil = 0f;
                reload = 4f;
                shootCone = 50f;
                ammoUseEffect = ShootFx.shellEjectSmall;
                health = 160;
                targetAir = false;

                drawer = (tile, entity) -> Draw.rect(entity.target != null ? shootRegion : region, tile.drawx() + tr2.x, tile.drawy() + tr2.y, entity.rotation - 90);
            }
        };

        wave = new LiquidTurret("wave"){{
            ammoTypes = new AmmoType[]{AmmoTypes.water, AmmoTypes.lava, AmmoTypes.cryofluid, AmmoTypes.oil};
            size = 2;
            recoil = 0f;
            reload = 4f;
            inaccuracy = 5f;
            shootCone = 50f;
            shootEffect = ShootFx.shootLiquid;
            range = 70f;
            health = 360;

            drawer = (tile, entity) -> {
                Draw.rect(region, tile.drawx() + tr2.x, tile.drawy() + tr2.y, entity.rotation - 90);

                Draw.color(entity.liquids.current().color);
                Draw.alpha(entity.liquids.total() / liquidCapacity);
                Draw.rect(name + "-liquid", tile.drawx() + tr2.x, tile.drawy() + tr2.y, entity.rotation - 90);
                Draw.color();
            };
        }};

        lancer = new ChargeTurret("lancer"){{
            range = 90f;
            chargeTime = 60f;
            chargeMaxDelay = 30f;
            chargeEffects = 7;
            shootType = AmmoTypes.lancerLaser;
            recoil = 2f;
            reload = 100f;
            cooldown = 0.03f;
            powerUsed = 20f;
            powerCapacity = 60f;
            shootShake = 2f;
            setShootSound("shootLancer");
            shootEffect = ShootFx.lancerLaserShoot;
            smokeEffect = ShootFx.lancerLaserShootSmoke;
            chargeEffect = ShootFx.lancerLaserCharge;
            chargeBeginEffect = ShootFx.lancerLaserChargeBegin;
            heatColor = Color.RED;
            size = 2;
            health = 320;
            targetAir = false;
            emitLight = true;
        }};

        arc = new PowerTurret("arc"){{
            shootType = AmmoTypes.arc;
            reload = 85f;
            shootShake = 1f;
            shootCone = 40f;
            rotatespeed = 8f;
            powerUsed = 10f;
            powerCapacity = 30f;
            range = 150f;
            setShootSound("shootArc");
            shootEffect = ShootFx.lightningShoot;
            heatColor = Color.RED;
            recoil = 1f;
            size = 1;
            targetAir = false;
            emitLight = true;
        }};

        swarmer = new BurstTurret("swarmer"){{
            ammoTypes = new AmmoType[]{AmmoTypes.missileExplosive, AmmoTypes.missileIncindiary, AmmoTypes.missileSurge};
            reload = 50f;
            shots = 4;
            burstSpacing = 5;
            setShootSound("shootMissileSmall");
            inaccuracy = 10f;
            range = 140f;
            xRand = 6f;
            size = 2;
            health = 380;
        }};

        salvo = new BurstTurret("salvo"){
            TextureRegion[] panels = new TextureRegion[2];

            @Override
            public void load() {
                super.load();
                panels[0] = Draw.region(name + "-panel-left");
                panels[1] = Draw.region(name + "-panel-right");
            }

            {
                size = 2;
                range = 120f;
                ammoTypes = new AmmoType[]{AmmoTypes.bulletCopper, AmmoTypes.bulletDense, AmmoTypes.bulletPyratite, AmmoTypes.bulletThorium, AmmoTypes.bulletSilicon};
                reload = 35f;
                restitution = 0.03f;
                ammoEjectBack = 3f;
                cooldown = 0.03f;
                recoil = 3f;
                shootShake = 2f;
                burstSpacing = 4;
                shots = 3;
                ammoUseEffect = ShootFx.shellEjectBig;
                setShootSound("shootSalvo");

                drawer = (tile, entity) -> {
                    Draw.rect(region, tile.drawx() + tr2.x, tile.drawy() + tr2.y, entity.rotation - 90);
                    float offsetx = (int) (Mathf.abscurve(Mathf.curve(entity.reload / reload, 0.3f, 0.2f)) * 3f);
                    float offsety = -(int) (Mathf.abscurve(Mathf.curve(entity.reload / reload, 0.3f, 0.2f)) * 2f);

                    for(int i : Mathf.signs){
                        float rot = entity.rotation + 90 * i;
                        Draw.rect(panels[i == -1 ? 0 : 1],
                                tile.drawx() + tr2.x + Angles.trnsx(rot, offsetx, offsety),
                                tile.drawy() + tr2.y + Angles.trnsy(rot, -offsetx, offsety), entity.rotation - 90);
                    }
                };

                health = 360;
            }
        };

        ripple = new ArtilleryTurret("ripple"){{
            ammoTypes = new AmmoType[]{AmmoTypes.artilleryDense, AmmoTypes.artilleryHoming, AmmoTypes.artilleryIncindiary, AmmoTypes.artilleryExplosive, AmmoTypes.artilleryPlastic};
            size = 3;
            shots = 4;
            inaccuracy = 12f;
            reload = 60f;
            ammoEjectBack = 5f;
            ammoUseEffect = ShootFx.shellEjectBig;
            setShootSound("shootRipple");
            cooldown = 0.03f;
            velocityInaccuracy = 0.2f;
            restitution = 0.02f;
            recoil = 6f;
            shootShake = 2f;
            range = 320f;
            targetAir = false;
            health = 550;
        }};

        cyclone = new ItemTurret("cyclone"){{
            ammoTypes = new AmmoType[]{AmmoTypes.flakObsidian, AmmoTypes.flakExplosive, AmmoTypes.flakPlastic, AmmoTypes.flakSurge};
            setShootSound("shootCyclone");
            xRand = 4f;
            reload = 8f;
            range = 145f;
            size = 3;
            recoil = 3f;
            rotatespeed = 10f;
            inaccuracy = 13f;
            shootCone = 30f;

            health = 145 * size * size;
        }};

        fuse = new ItemTurret("fuse"){{
            ammoTypes = new AmmoType[]{AmmoTypes.fuseShotgun};
            setShootSound("shootFuse");
            reload = 50f;
            shootShake = 4f;
            range = 80f;
            recoil = 5f;
            restitution = 0.1f;
            size = 3;

            health = 155 * size * size;
        }};

        spectre = new DoubleTurret("spectre"){{
            ammoTypes = new AmmoType[]{AmmoTypes.bulletDenseBig, AmmoTypes.bulletPyratiteBig, AmmoTypes.bulletThoriumBig};
            reload = 6f;
            coolantMultiplier = 0.5f;
            maxCoolantUsed = 1.5f;
            restitution = 0.1f;
            ammoUseEffect = ShootFx.shellEjectBig;
            range = 200f;
            inaccuracy = 3f;
            recoil = 3f;
            xRand = 3f;
            shotWidth = 4f;
            shootShake = 2f;
            shots = 2;
            size = 4;
            shootCone = 24f;

            health = 155 * size * size;
        }};

        meltdown = new LaserTurret("meltdown"){{
            shootType = AmmoTypes.meltdownLaser;
            shootEffect = ShootFx.shootBigSmoke2;
            setShootSound("shootMeltdown");
            shootCone = 40f;
            recoil = 4f;
            size = 4;
            shootShake = 2f;
            powerUsed = 60f;
            powerCapacity = 120f;
            range = 160f;
            reload = 200f;
            firingMoveFract = 0.1f;
            shootDuration = 220f;
            emitLight = true;
            lightColor = Color.valueOf("ff9c5a");

            health = 165 * size * size;
        }};

        foreshadow = new ItemTurret("foreshadow"){
            float powerUsed = 30f;

            {
                ammoTypes = new AmmoType[]{AmmoTypes.surgeRail};
                setShootSound("shootForeshadow");
                range = 500f;
                reload = 200f;
                size = 4;
                maxAmmo = 40;
                ammoUseEffect = ShootFx.casing3Double;
                rotatespeed = 2.5f;
                shootCone = 2f;
                recoil = 5f;
                restitution = 0.009f;
                cooldown = 0.009f;
                shootShake = 4f;
                coolantMultiplier = 0.2f;
                health = 150 * size * size;
                hasPower = true;
                powerCapacity = 60f;
                emitLight = true;
            }

            @Override
            public boolean hasAmmo(Tile tile){
                return super.hasAmmo(tile) && tile.entity.power.amount >= powerUsed;
            }

            @Override
            public AmmoType useAmmo(Tile tile){
                if(!tile.isEnemyCheat()) tile.entity.power.amount -= powerUsed;
                return super.useAmmo(tile);
            }
        };
        // The mass
        evilDuo = new ItemTurret("evil-duo"){{
            ammoTypes = new AmmoType[]{AmmoTypes.bulletCopper, AmmoTypes.bulletDense, AmmoTypes.bulletPyratite, AmmoTypes.bulletSilicon};
            reload = 25f;
            restitution = 0.03f;
            range = 90f;
            shootCone = 15f;
            setShootSound("shootDuo");
            ammoUseEffect = ShootFx.shellEjectSmall;
            health = 80;
            inaccuracy = 2f;
            rotatespeed = 10f;
            living = true;
        }};

        evilScatter = new BurstTurret("evil-scatter"){{
            ammoTypes = new AmmoType[]{AmmoTypes.flakScrap, AmmoTypes.flakLead, AmmoTypes.flakObsidian};
            reload = 18f;
            range = 170f;
            size = 2;
            burstSpacing = 5f;
            shots = 3;
            targetGround = false;
            targetAir = true;
            ammoUseEffect = BlockFx.biomassSpore;
            setShootSound("shootScatter");
            recoil = 2f;
            rotatespeed = 15f;
            inaccuracy = 17f;
            shootCone = 35f;
            health = 200 * size * size;
            living = true;
        }};
        evilSalvo = new BurstTurret("evil-salvo"){{
            size = 2;
            range = 120f;
            ammoTypes = new AmmoType[]{AmmoTypes.bulletCopper, AmmoTypes.bulletDense, AmmoTypes.bulletPyratite, AmmoTypes.bulletThorium, AmmoTypes.bulletSilicon};
            reload = 35f;
            restitution = 0.03f;
            ammoEjectBack = 3f;
            cooldown = 0.03f;
            recoil = 3f;
            shootShake = 2f;
            burstSpacing = 4;
            shots = 3;
            ammoUseEffect = ShootFx.shellEjectBig;
            setShootSound("shootSalvo");
            health = 360;
            living = true;
            }};
        evilRipple = new ArtilleryTurret("evil-ripple"){{
            ammoTypes = new AmmoType[]{AmmoTypes.artilleryDense, AmmoTypes.artilleryHoming, AmmoTypes.artilleryIncindiary, AmmoTypes.artilleryExplosive, AmmoTypes.artilleryPlastic};
            size = 3;
            shots = 4;
            inaccuracy = 12f;
            reload = 60f;
            ammoEjectBack = 5f;
            ammoUseEffect = ShootFx.shellEjectBig;
            setShootSound("shootRipple");
            cooldown = 0.03f;
            velocityInaccuracy = 0.2f;
            restitution = 0.02f;
            recoil = 6f;
            shootShake = 2f;
            range = 320f;
            targetAir = false;
            health = 550;
            living = true;
        }};

        evilCyclone = new ItemTurret("evil-cyclone"){{
            ammoTypes = new AmmoType[]{AmmoTypes.explosivebloodflak, AmmoTypes.bloodflak};
            setShootSound("shootCyclone");
            xRand = 4f;
            reload = 8f;
            range = 145f;
            size = 3;
            recoil = 3f;
            rotatespeed = 10f;
            inaccuracy = 13f;
            shootCone = 30f;
            living = true;
            health = 145 * size * size;
        }};

        evilFuse = new ItemTurret("evil-fuse"){{
            ammoTypes = new AmmoType[]{AmmoTypes.evilFuseShotgun};
            setShootSound("shootFuse");
            reload = 50f;
            shootShake = 4f;
            range = 80f;
            recoil = 5f;
            restitution = 0.1f;
            size = 3;
            living = true;
            health = 155 * size * size;
        }};


    }
}
