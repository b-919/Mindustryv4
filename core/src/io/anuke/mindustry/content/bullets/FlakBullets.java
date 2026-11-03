package io.anuke.mindustry.content.bullets;

import com.badlogic.gdx.graphics.Color;
import io.anuke.mindustry.content.fx.BulletFx;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.bullet.BulletType;
import io.anuke.mindustry.entities.bullet.FlakBulletType;
import io.anuke.mindustry.entities.effect.Lightning;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.game.ContentList;
import io.anuke.ucore.util.Mathf;

public class FlakBullets extends BulletList implements ContentList{
    public static BulletType scrap, lead, obsidian, plastic, explosive, surge,
    //Mass
    blood, explosiveblood;

    @Override
    public void load(){
        scrap = new FlakBulletType(4f, 2.5f) {{
          splashDamage =22f;
          splashDamageRadius =24f;
          lifetime = 60f;
          bulletWidth = 6f;
          bulletHeight = 8f;
          hiteffect = BulletFx.flakExplosion;
        }};

        lead = new FlakBulletType(4f, 3f) {{
            lifetime = 60f;
            bulletWidth = 6f;
            bulletHeight = 8f;
            hiteffect = BulletFx.flakExplosion;
            splashDamage = 27f;
            splashDamageRadius = 15f;
        }};

        obsidian = new FlakBulletType(4f, 5f) {{
            lifetime = 70f;
            bulletWidth = 6f;
            bulletHeight = 8f;
            hiteffect = BulletFx.flakExplosion;
            splashDamage = 30f;
            splashDamageRadius = 26f;
            fragBullet = StandardBullets.obsidianFrag;
            fragBullets = 6;
            backColor = Palette.lightishGray;
            frontColor = Color.LIGHT_GRAY;
        }};

        plastic = new FlakBulletType(4f, 5){
            {
                splashDamageRadius = 40f;
                fragBullet = ArtilleryBullets.plasticFrag;
                fragBullets = 6;
                hiteffect = BulletFx.plasticExplosion;
                frontColor = Palette.plastaniumFront;
                backColor = Palette.plastaniumBack;
            }
        };

        explosive = new FlakBulletType(4f, 5){
            {
                //default bullet type, no changes
            }
        };

        surge = new FlakBulletType(4f, 7){
            {
                splashDamage = 33f;
            }

            @Override
            public void despawned(Bullet b) {
                super.despawned(b);

                for (int i = 0; i < 2; i++) {
                    Lightning.create(b.getTeam(), Palette.surge, damage, b.x, b.y, Mathf.random(360f), 12);
                }
            }
        };
        blood = new FlakBulletType(4.2f, 5){
            {
                splashDamageRadius = 40f;
                fragBullet = ArtilleryBullets.bloodfrag;
                fragBullets = 6;
                hiteffect = BulletFx.flakExplosion;
                frontColor = Color.valueOf("bc1a12");
                backColor = Color.valueOf("8a1a14");
            }
        };

        explosiveblood = new FlakBulletType(4.2f, 5){
            {
                frontColor = Color.valueOf("bc3912");
                backColor = Color.valueOf("8a2914");
            }
        };
    }
}
