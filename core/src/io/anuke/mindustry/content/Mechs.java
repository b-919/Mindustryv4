package io.anuke.mindustry.content;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.content.fx.ShootFx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.game.ContentList;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Mech;
import io.anuke.mindustry.type.Weapon;
import io.anuke.mindustry.type.abilities.*;


public class Mechs implements ContentList{
    public static Mech alpha, delta, tau, omega, dart, javelin, trident, glaive, debugGround, debugAir;

    /**These are not new mechs, just re-assignments for convenience.*/
    public static Mech starterDesktop, starterMobile;

    @Override
    public void load(){

        alpha = new Mech("alpha-mech", false){{

            drillPower = 1;
            mineSpeed = 1.5f;
            mass = 1.2f;
            speed = 0.5f;
            boostSpeed = 0.85f;
            weapon = Weapons.blaster;
            trailColorTo = Color.valueOf("ffd37f");
            armor = 20f;
            abilities.add(new AlphaDroneAbility());
        }};

        delta = new Mech("delta-mech", false){{

            drillPower = -1;
            speed = 0.75f;
            boostSpeed = 0.95f;
            itemCapacity = 15;
            mass = 0.9f;
            armor = 30f;
            weapon = Weapons.shockgun;
            trailColorTo = Color.valueOf("d3ddff");
            abilities.add(new LandShockAbility());
        }};

        tau = new Mech("tau-mech", false){{

            drillPower = 4;
            mineSpeed = 3f;
            itemCapacity = 70;
            healTurretOffsetX = 5f;
            healTurretOffsetY = -2f;
            mass = 1.75f;
            speed = 0.44f;
            drag = 0.35f;
            boostSpeed = 0.8f;
            weapon = Weapons.healBlaster;
            armor = 15f;
            trailColorTo = Palette.heal;
            isHealer = true;
            abilities.add(new TauHealAbility());
        }};

        omega = new Mech("omega-mech", false){{

            drillPower = 2;
            mineSpeed = 1.5f;
            itemCapacity = 50;
            speed = 0.36f;
            boostSpeed = 0.6f;
            mass = 4f;
            shake = 4f;
            weapon = Weapons.swarmer;
            trailColorTo = Color.valueOf("feb380");
            armor = 45f;
            abilities.add(new OmegaHeatAbility());
        }};

        dart = new Mech("dart-ship", true){{
            drillPower = 1;
            mineSpeed = 0.9f;
            speed = 0.4f;
            drag = 0.1f;
            armor = 10f;
            weapon = Weapons.blasterSmall;
            trailColor = Palette.lightTrail;
            cellTrnsY = 1f;
        }};

        javelin = new Mech("javelin-ship", true){{

            drillPower = -1;
            speed = 0.11f;
            drag = 0.01f;
            mass = 2f;
            armor = 5f;
            weapon = Weapons.missiles;
            trailColor = Color.valueOf("d3ddff");
            cellTrnsY = 1f;
            abilities.add(new JavelinShieldAbility());
        }};

        trident = new Mech("trident-ship", true){{

            drillPower = 2;
            speed = 0.12f;
            drag = 0.035f;
            mass = 2.5f;
            turnCursor = false;
            armor = 20f;
            itemCapacity = 30;
            trailColor = Color.valueOf("84f491");
            weapon = Weapons.bomberTrident;
            cellTrnsY = 1f;
            abilities.add(new VelocityShootAbility());
        }};

        glaive = new Mech("glaive-ship", true){
            {
                weapon = Weapons.glaiveBlaster;
                drillPower = 4;
                mineSpeed = 1.3f;
                speed = 0.32f;
                drag = 0.06f;
                mass = 3f;
                armor = 30f;
                itemCapacity = 60;
                trailColor = Color.valueOf("feb380");
                cellTrnsY = 1f;
            }
        };
        //debug
        debugAir = new Mech("debug-ship", true){
            {
                weapon = new Weapon("debug-air"){{
                    shootY = 1.5f;
                    reload = 0f;
                    alternate = true;
                    ejectEffect = ShootFx.shellEjectSmall;
                    ammo = AmmoTypes.bulletGlaive;
                }};
                drillPower = 20;
                mineSpeed = 20f;
                speed = 5f;
                drag = 0.06f;
                mass = 1f;
                armor = 30000000f;
                itemCapacity = 200000;
                trailColor = Color.valueOf("feb380");
                cellTrnsY = 1f;
            }

            @Override
            public void updateAlt(Player player){
                if(player.health < player.maxHealth()){
                    player.healBy(player.maxHealth() - player.health);
                }
            }
        };
        debugGround = new Mech("debug-mech", false){
            {
                weapon = new Weapon("debug-ground"){{
                    shootY = 1.5f;
                    reload = 0f;
                    alternate = true;
                    ejectEffect = ShootFx.shellEjectSmall;
                    ammo = AmmoTypes.bulletGlaive;
                }};
                drillPower = 20;
                mineSpeed = 20f;
                speed = 5f;
                drag = 0.06f;
                mass = 1f;
                armor = 30000000f;
                itemCapacity = 200000;
                trailColor = Color.valueOf("fe8390");
                cellTrnsY = 1f;
            }

            @Override
            public void updateAlt(Player player){
                if(player.health < player.maxHealth()){
                    player.healBy(player.maxHealth() - player.health);
                }
            }
        };

        starterDesktop = alpha;
        starterMobile = dart;
    }

    @Override
    public ContentType type(){
        return ContentType.mech;
    }
}
