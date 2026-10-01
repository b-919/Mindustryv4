package io.anuke.mindustry.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.content.bullets.TurretBullets;
import io.anuke.mindustry.entities.units.*;
import io.anuke.mindustry.entities.units.types.*;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.Weapon;
import io.anuke.ucore.util.Mathf;

public class UnitTypes implements ContentList{
    public static UnitType
        scavenger, draug, spirit, ghost, phantom,
        alphaDrone, defenseDrone,
        scrapper , wraith, ghoul, revenant, lich, reaper,
        crawler, bombDrone,
        scrappeon, dagger, titan, fortress, chaosarray, eradicator,
        debugtank, nova,
        atrax,
        trainEngine,
        minerDroneT1, minerDroneT2, logisticsDrone,
        evilDraug, evilDagger, evilWraith, explosiveBiomass, FlyingExplosiveBiomass, evilTanky, exterminatorBiomass, evilSwarmDrone, artilleryBiomass, acidMosquito; // the mass units btw

    @Override
    public void load(){
        alphaDrone = new UnitType("alpha-drone", AlphaDrone.class, AlphaDrone::new){
            {
                isFlying = true;
                drag = 0.005f;
                speed = 0.6f;
                maxVelocity = 1.7f;
                range = 40f;
                health = 45;
                hitsize = 4f;
                mass = 0.1f;
                weapon = Weapons.droneBlaster;
                trailColor = Color.valueOf("ffd37f");
                spawnsInSiegeMode = false;
                playerControllable = false;
                rtsAIControllable = false;
            }

            @Override
            public boolean isHidden() {
                return true;
            }
        };

        defenseDrone = new UnitType("defense-drone", BlockDefenseDrone.class, BlockDefenseDrone::new){
            {
                isFlying = true;
                drag = 0.005f;
                speed = 0.7f;
                maxVelocity = 2.0f;
                range = 80f;
                health = 60;
                hitsize = 4f;
                mass = 0.1f;
                weapon = Weapons.droneBlaster;
                trailColor = Color.valueOf("ffd37f");
                spawnsInSiegeMode = false;
                playerControllable = false;
                rtsAIControllable = false;
            }

            @Override
            public boolean isHidden() {
                return true;
            }
        };

        scavenger = new UnitType("scavenger", DroneMiner.class, DroneMiner::new){{
            weapon = Weapons.mineBlaster;
            isFlying = true;
            drag = 0.01f;
            speed = 0.18f;
            maxVelocity = 0.6f;
            range = 50f;
            health = 20;
            toMine = ObjectSet.with(Items.scrap);
            itemCapacity = 50;
            spawnsInSiegeMode = false;
            playerControllable = false;
            rtsAIControllable = false;
        }};

        draug = new UnitType("draug", DroneMiner.class, DroneMiner::new){{
            weapon = Weapons.mineBlaster;
            isFlying = true;
            drag = 0.01f;
            speed = 0.3f;
            maxVelocity = 1.2f;
            range = 55f;
            health = 80;
            toMine = ObjectSet.with(Items.copper, Items.lead);
            spawnsInSiegeMode = false;
            playerControllable = false;
            rtsAIControllable = false;
        }};

        spirit = new UnitType("spirit", Spirit.class, Spirit::new){{
            weapon = Weapons.healBlasterDrone;
            isFlying = true;
            drag = 0.01f;
            speed = 0.42f;
            maxVelocity = 1.6f;
            range = 50f;
            health = 100;
            toMine = ObjectSet.with(Items.lead, Items.copper);
            spawnsInSiegeMode = false;
            rtsAIControllable = false;
        }};

        ghost = new UnitType("ghost", Ghost.class, Ghost::new){{
            weapon = Weapons.healBlasterDrone2;
            isFlying = true;
            drag = 0.01f;
            mass = 1.5f;
            speed = 0.2f;
            maxVelocity = 1.4f;
            range = 60f;
            itemCapacity = 55;
            health = 90;
            buildPower = 0.6f;
            minePower = 0.98f;
            toMine = ObjectSet.with(Items.thorium, Items.titanium);
            spawnsInSiegeMode = false;
            rtsAIControllable = false;
        }};

        phantom = new UnitType("phantom", Phantom.class, Phantom::new){{
            weapon = Weapons.healBlasterDrone2;
            isFlying = true;
            drag = 0.01f;
            mass = 2f;
            speed = 0.45f;
            maxVelocity = 1.9f;
            range = 70f;
            itemCapacity = 70;
            health = 400;
            buildPower = 0.4f;
            toMine = ObjectSet.with(Items.lead, Items.copper, Items.titanium, Items.thorium);
            spawnsInSiegeMode = false;
            rtsAIControllable = false;
        }};

        scrappeon = new UnitType("scrappeon", Scrappeon.class, Scrappeon::new){{
            maxVelocity = 0.98f;
            speed = 0.142f;
            drag = 0.4f;
            hitsize = 7.8f;
            mass = 1.25f;
            weapon = Weapons.scrapLockBlaster;
            health = 60;
            unitCost = 5;
        }};

        crawler = new UnitType("crawler", Crawler.class, Crawler::new){{
            weapon = Weapons.kamikaze;
            maxVelocity = 1.27f;
            speed = 0.285f;
            drag = 0.4f;
            hitsize = 8f;
            mass = 1.75f;
            health = 120f;
            unitCost = 30;
        }};

        bombDrone = new UnitType("bomb_drone", BombDrone.class, BombDrone::new){{
            isFlying = true;
            weapon = Weapons.kamikazeDrone;
            maxVelocity = 1.25f;
            speed = 0.308f;
            drag = 0.01f;
            hitsize = 7.89f;
            mass = 1.25f;
            health = 50;
            unitCost = 30;
        }};


        nova = new UnitType("nova", GroundHealUnit.class, GroundHealUnit::new){{
            maxVelocity = 1.0f;
            speed = 0.18f;
            drag = 0.4f;
            hitsize = 9f;
            mass = 2.0f;
            weapon = Weapons.healBlaster;
            health = 250;
            healRange = 80f;
            isHealer = true;
            healTurretOffsetX = 5f;
            healTurretOffsetY = -2f;
            unitCost = 15;
        }};

        dagger = new UnitType("dagger", Dagger.class, Dagger::new){{
            maxVelocity = 1.1f;
            speed = 0.2f;
            drag = 0.4f;
            hitsize = 8f;
            mass = 1.75f;
            weapon = Weapons.chainBlaster;
            health = 130;
        }};

        titan = new UnitType("titan", Titan.class, Titan::new){{
            maxVelocity = 0.8f;
            speed = 0.22f;
            drag = 0.4f;
            mass = 3.5f;
            hitsize = 9f;
            rotatespeed = 0.1f;
            weapon = Weapons.flamethrower;
            health = 460;
            unitCost = 25;
            immunities.add(StatusEffects.burning);
        }};

        fortress = new UnitType("fortress", Fortress.class, Fortress::new){{
            maxVelocity = 0.78f;
            speed = 0.15f;
            drag = 0.4f;
            mass = 5f;
            hitsize = 10f;
            rotatespeed = 0.06f;
            targetAir = false;
            weapon = Weapons.artillery;
            health = 750;
            unitCost = 100;
        }};

        atrax = new UnitType("atrax", Atrax.class, Atrax::new){{
            speed = 0.6f;
            drag = 0.4f;
            maxVelocity = 1.3f;
            hitsize = 13f;
            mass = 2.5f;
            targetAir = false;
            health = 600;
            armor = 3f;
            weapon = Weapons.attraxSpitter;
            unitCost = 60;
            legCount = 4;
            legLength = 15;
            legForwardScl = 1.2f;
            legMoveSpace = 1.5f;
            immunities.add(StatusEffects.burning);
            immunities.add(StatusEffects.melting);
        }};

        scrapper = new UnitType("scrapper", Scrapper.class, Scrapper::new){{
            speed = 0.2f;
            maxVelocity = 1.2f;
            drag = 0.01f;
            mass = 0.59f;
            weapon = Weapons.scrapBlaster;
            isFlying = true;
            health = 30;
            unitCost = 10;
        }};

        wraith = new UnitType("wraith", Wraith.class, Wraith::new){{
            speed = 0.3f;
            maxVelocity = 1.9f;
            drag = 0.01f;
            mass = 1.5f;
            weapon = Weapons.chainBlaster;
            isFlying = true;
            health = 75;
            unitCost = 20;
        }};

        ghoul = new UnitType("ghoul", Ghoul.class, Ghoul::new){{
            health = 220;
            speed = 0.2f;
            maxVelocity = 1.4f;
            mass = 3f;
            drag = 0.01f;
            isFlying = true;
            targetAir = false;
            weapon = Weapons.bomber;
            unitCost = 75;
        }};

        revenant = new UnitType("revenant", Revenant.class, Revenant::new){{
            health = 1000;
            mass = 5f;
            hitsize = 20f;
            speed = 0.1f;
            maxVelocity = 1f;
            drag = 0.01f;
            range = 80f;
            shootCone = 40f;
            isFlying = true;
            rotateWeapon = true;
            rotatespeed = 0.01f;
            attackLength = 90f;
            baseRotateSpeed = 0.06f;
            weapon = Weapons.revenantMissiles;
            unitCost = 300;
            engineOffsetY = -10f;
        }};

        lich = new UnitType("lich", Lich.class, Lich::new){{
            health = 6000;
            mass = 20f;
            hitsize = 40f;
            speed = 0.01f;
            maxVelocity = 0.6f;
            drag = 0.02f;
            range = 80f;
            isFlying = true;
            rotatespeed = 0.01f;
            baseRotateSpeed = 0.04f;
            attackLength = 90f;
            shootCone = 20f;
            rotateWeapon = true;
            weapon = Weapons.lichMissiles;
            unitCost = 2000;
            engineOffsetY = -21f;
            engineSize = 8f;
            trailColor = Palette.lighterOrange;
            isBoss = true;
        }};

        reaper = new UnitType("reaper", Lich.class, Lich::new ){{
            health = 11000;
            mass = 30f;
            hitsize = 56f;
            speed = 0.01f;
            maxVelocity = 0.6f;
            drag = 0.02f;
            range = 80f;
            shootCone = 30f;
            isFlying = true;
            rotateWeapon = true;
            engineOffsetY = -40f;
            engineSize = 7.3f;
            rotatespeed = 0.01f;
            baseRotateSpeed = 0.04f;
            trailColor = Palette.lighterOrange;
            weapon = Weapons.reaperGun;
            addWeapons(//Weapons.reaperGun,
                    new Weapon("large-laser"){{
                        shake = 4f;
                        shootY = 9f;
                        x = 18f;
                        y = 5f;
                        rotateSpeed = 2f;
                        reload = 45f;
                        recoil = 4f;
                        setShootSound("shootLaser");
                        shadow = 20f;
                        rotate = true;
                        ammo = AmmoTypes.bulletThoriumBig;
                    }});
            unitCost = 4000;
            isBoss = true;
        }};

        chaosarray = new UnitType("chaos-array", ChaosArray.class, ChaosArray::new){{
            health = 3000;
            mass = 5f;
            hitsize = 20;
            speed = 0.12f;
            maxVelocity = 0.68f;
            drag = 0.4f;
            rotatespeed = 0.06f;
            weapon = Weapons.chaos;
            unitCost = 1500;
            isBoss = true;
        }};

        eradicator = new UnitType("eradicator", Eradicator.class, Eradicator::new){{
            health = 9000;
            maxVelocity = 0.68f;
            speed = 0.12f;
            drag = 0.4f;
            mass = 5f;
            hitsize = 20f;
            rotatespeed = 0.06f;
            unitCost = 3500;
            weapon = Weapons.eradicator;
            isBoss = true;
        }};

        debugtank = new UnitType("debugtank", TankUnit.class, TankUnit::new){{
            isTank = true;
            mass = 1f;
            hitsize = 2f;
            speed = 0.2f;
            maxVelocity = 2f;
            drag = 0.4f;
            rotatespeed = 0.06f;
            baseRotateSpeed = 0.04f;
            range = 80f;
            weapon = Weapons.debugtankturret;
            weapon.weaponMirror = false;
            spawnsInSiegeMode = false;

        }
            @Override
            public boolean isHidden() {
                return true;
            }};

        trainEngine = new UnitType("train-engine", TrainEngine.class, TrainEngine::new){{
            maxVelocity = 0.9f;
            speed = 0.12f;
            drag = 0.35f;
            hitsize = 10f;
            mass = 4f;
            health = 500f;
            weapon = Weapons.blaster;
            spawnsInSiegeMode = false;
        }
            @Override
            public boolean isHidden() {
                return true;
            }};

        minerDroneT1 = new UnitType("miner-drone-t1", MiningPostDrone.class, MiningPostDrone::new){{
            weapon = Weapons.mineBlaster;
            isFlying = true;
            drag = 0.01f;
            speed = 0.25f;
            maxVelocity = 0.90f;
            range = 55f;
            health = 40;
            toMine = ObjectSet.with(Items.lead, Items.copper);
            spawnsInSiegeMode = false;
            playerControllable = false;
            rtsAIControllable = false;
        }};

        minerDroneT2 = new UnitType("miner-drone-t2", MiningPostDrone.class, MiningPostDrone::new){{
            weapon = Weapons.healBlasterDrone2;
            isFlying = true;
            drag = 0.01f;
            mass = 2f;
            speed = 0.30f;
            maxVelocity = 1.1f;
            range = 70f;
            itemCapacity = 70;
            health = 220;
            minePower = 1.2f;
            toMine = ObjectSet.with(Items.lead, Items.copper);
            spawnsInSiegeMode = false;
            playerControllable = false;
            rtsAIControllable = false;
        }};

        logisticsDrone = new UnitType("logistics-drone", LogisticsDrone.class, LogisticsDrone::new){{
            isFlying = true;
            drag = 0.01f;
            speed = 0.5f;
            maxVelocity = 1.5f;
            health = 100;
            itemCapacity = 30;
            spawnsInSiegeMode = false;
            playerControllable = false;
            rtsAIControllable = false;
        }};
        // The Mass Units
        evilDagger = new UnitType("evil-dagger", BiomassGroundUnit.class, BiomassGroundUnit::new){{
            maxVelocity = 1.1f;
            speed = 0.2f;
            drag = 0.4f;
            hitsize = 8f;
            mass = 1.75f;
            weapon = Weapons.evilDaggerWeapon;
            health = 130;
            spawnsInSiegeMode = false;
        }

            @Override
            public boolean isHidden() {
                return true;
            }};

        FlyingExplosiveBiomass = new UnitType("flying-explosive-biomass", BiomassAirUnit.class, BiomassAirUnit::new){{
            isFlying = true;
            weapon = Weapons.kamikaze;
            trailColor = Color.valueOf("871e1e");
            maxVelocity = 1.50f;
            speed = 0.32f;
            drag = 0.01f;
            hitsize = 11f;
            mass = 1.25f;
            health = 60;
            spawnsInSiegeMode = false;
        }

            @Override
            public boolean isHidden() {
                return true;
            }};

        explosiveBiomass = new UnitType("explosive-biomass", BiomassGroundUnit.class, BiomassGroundUnit::new){{
            weapon = Weapons.kamikaze;
            maxVelocity = 1.25f;
            speed = 0.36f;
            drag = 0.01f;
            hitsize = 8f;
            mass = 1.75f;
            health = 100f;
            spawnsInSiegeMode = false;
            living = true;
        }

            @Override
            public boolean isHidden() {
                return true;
            }};

        evilTanky = new UnitType("evil-tanky", BiomassGroundUnit.class, BiomassGroundUnit::new){{
            maxVelocity = 0.8f;
            speed = 0.18f;
            drag = 0.4f;
            mass = 3.5f;
            hitsize = 10.8f;
            rotatespeed = 0.1f;
            weapon = Weapons.evilTankyWeapon;
            health = Mathf.random(820, 1280);
            spawnsInSiegeMode = false;
            living = true;
        }

            @Override
            public boolean isHidden() {
                return true;
            }};

        evilDraug = new UnitType("evil-draug", BiomassMiner.class, BiomassMiner::new){{
            weapon = Weapons.mineBlaster;
            isFlying = true;
            drag = 0.01f;
            speed = 0.19f;
            maxVelocity = 0.61f;
            range = 55f;
            health = 40;
            trailColor = Color.valueOf("871e1e");
            toMine = ObjectSet.with(Items.copper, Items.lead);
            spawnsInSiegeMode = false;
            living = true;
        }

            @Override
            public boolean isHidden() {
                return true;
            }};

        evilWraith = new UnitType("evil-wraith", BiomassAirUnit.class, BiomassAirUnit::new){{
            speed = 0.3f;
            maxVelocity = 1.9f;
            drag = 0.01f;
            mass = 1.5f;
            weapon = Weapons.chainBlaster;
            trailColor = Color.valueOf("871e1e");
            isFlying = true;
            health = 70;
            spawnsInSiegeMode = false;
            living = true;
        }

            @Override
            public boolean isHidden() {
                return true;
            }};

        exterminatorBiomass  = new UnitType("exterminator-biomass", BiomassExterminator.class, BiomassExterminator::new){{
            health = 3000;
            mass = 5f;
            hitsize = 20;
            speed = 0.12f;
            maxVelocity = 0.68f;
            drag = 0.4f;
            rotatespeed = 0.06f;
            weapon = Weapons.exterminatorweapon;
            spawnsInSiegeMode = false;
            living = true;
        }

            @Override
            public boolean isHidden() {
                return true;
            }
        };
        evilSwarmDrone = new UnitType("evil-swarm-drone", BiomassSwarm.class, BiomassSwarm::new){
            {
                isFlying = true;
                drag = 0.005f;
                speed = 0.6f;
                maxVelocity = 1.7f;
                range = 40f;
                health = 45;
                hitsize = 4f;
                mass = 0.1f;
                weapon = Weapons.droneBlaster;
                trailColor = Color.valueOf("871e1e");
                spawnsInSiegeMode = false;
                living = true;
            }

            @Override
            public boolean isHidden() {
                return true;
            }
        };
        artilleryBiomass = new UnitType("artillery-biomass", BiomassArtillery.class, BiomassArtillery::new){{
            maxVelocity = 0.8f;
            speed = 0.15f;
            drag = 0.4f;
            mass = 5f;
            hitsize = 10f;
            rotatespeed = 0.06f;
            targetAir = false;
            weapon = Weapons.artilleryBiomass;

            health = 800;
            spawnsInSiegeMode = false;
            living = true;
        }};

        acidMosquito = new UnitType("acid-mosquito", BiomassMosquito.class, BiomassMosquito::new){{
            isFlying = true;
            health = 100;
            hitsize = 4f;
            mass = 0.1f;
            range = 30f;
            weapon = Weapons.mosquitoweapon;
            trailColor = Color.valueOf("871e1e");
            engineOffsetY = -21f;
            spawnsInSiegeMode = false;
            living = true;
        }

            @Override
            public boolean isHidden() {
                return true;
            }
        };
    }

    @Override
    public ContentType type(){
        return ContentType.unit;
    }
}
