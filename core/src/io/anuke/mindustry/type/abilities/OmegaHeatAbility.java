package io.anuke.mindustry.type.abilities;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.type.Mech;
import io.anuke.mindustry.type.MechAbility;
import io.anuke.ucore.core.Core;
import io.anuke.ucore.core.Graphics;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;

/** Shoot-heat based armor: slows the mech, adds armor and draws the heated armor plate. */
public class OmegaHeatAbility implements MechAbility{
    public float armorPerHeat = 30f;
    public float spreadPerHeat = 2f;

    private TextureRegion armorRegion;

    @Override
    public void load(Mech mech){
        armorRegion = Draw.region(mech.name + "-armor");
    }

    @Override
    public void update(Player player){
        float scl = 1f - player.shootHeat / 2f;
        player.getVelocity().scl(scl);
    }

    @Override
    public void draw(Player player){
        if(player.shootHeat <= 0.01f) return;

        float alpha = Core.batch.getColor().a;
        Shaders.build.progress = player.shootHeat;
        Shaders.build.region = armorRegion;
        Shaders.build.time = Timers.time() / 10f;
        Shaders.build.color.set(Palette.accent).a = player.shootHeat;
        Graphics.shader(Shaders.build);
        Draw.alpha(1f);
        Draw.rect(armorRegion, player.snappedX(), player.snappedY(), player.rotation);
        Graphics.shader(Shaders.mix);
        Draw.color(1f, 1f, 1f, alpha);
    }

    @Override
    public float getExtraArmor(Player player){
        return player.shootHeat * armorPerHeat;
    }

    @Override
    public float spreadX(Player player){
        return player.shootHeat * spreadPerHeat;
    }

    @Override
    public float getRotationAlpha(Player player){
        return 0.6f - player.shootHeat * 0.3f;
    }
}
