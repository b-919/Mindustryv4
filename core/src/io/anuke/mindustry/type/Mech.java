package io.anuke.mindustry.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.mindustry.content.Weapons;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.game.UnlockableContent;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.ui.ContentDisplay;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Bundles;

public class Mech extends UnlockableContent{
    public final String name;
    public final String description;

    public boolean flying;
    public float speed = 1.1f;
    public float maxSpeed = 10f;
    public float boostSpeed = 0.75f;
    public float drag = 0.4f;
    public float mass = 1f;
    public float shake = 0f;
    public float armor = 1f;

    public boolean isHealer;
    public float hitsize = 6f;
    public float cellTrnsY = 0f;
    public float mineSpeed = 1f;
    public int drillPower = -1;
    public float carryWeight = 10f;
    public float buildPower = 1f;
    public Color trailColor = Palette.boostFrom;
    public Color trailColorTo = Palette.boostTo;
    public int itemCapacity = 30;
    public boolean turnCursor = true;

    public float healTurretOffsetX, healTurretOffsetY;
    public Weapon weapon = Weapons.blaster;
    /**additional weapon mounts. {@link #weapon} stays the first mount, so existing code keeps working.*/
    public Weapon[] weapons = new Weapon[0];

    public TextureRegion baseRegion, legRegion, region, iconRegion;

    public Mech(String name, boolean flying){
        this.flying = flying;
        this.name = name;
        this.description = Bundles.get("mech." + name + ".description");
    }

    /**Appends weapons to this mech's mount array, after {@link #weapon}.*/
    public Mech addWeapons(Weapon... weapons){
        if(weapons.length == 0) return this;

        Weapon[] combined = new Weapon[this.weapons.length + weapons.length];
        System.arraycopy(this.weapons, 0, combined, 0, this.weapons.length);
        System.arraycopy(weapons, 0, combined, this.weapons.length, weapons.length);
        this.weapons = combined;
        return this;
    }

    /**Replaces this mech's mount array. {@link #weapon} is still prepended unless it is null.*/
    public Mech setWeapons(Weapon... weapons){
        this.weapons = weapons;
        return this;
    }

    /**Every weapon this mech mounts, {@link #weapon} first.*/
    public Weapon[] allWeapons(){
        return allWeapons(weapon);
    }

    /**Every weapon a player using this mech mounts when the weapon field is {@code override}.*/
    public Weapon[] allWeapons(Weapon override){
        if(weapons.length == 0){
            return new Weapon[]{override};
        }
        if(override == null){
            return weapons;
        }

        Weapon[] combined = new Weapon[weapons.length + 1];
        combined[0] = override;
        System.arraycopy(weapons, 0, combined, 1, weapons.length);
        return combined;
    }

    public String localizedName(){
        return Bundles.get("mech." + name + ".name");
    }

    public void updateAlt(Player player){}

    public void draw(Player player){}

    public void drawOver(Player player){}

    public void drawBloom(Player player){}

    public float getExtraArmor(Player player){
        return 0f;
    }

    public float spreadX(Player player){
        return 0f;
    }

    public float getRotationAlpha(Player player){return 1f;}

    public boolean canShoot(Player player){
        return true;
    }

    public void onLand(Player player){}

    @Override
    public void displayInfo(Table table){
        ContentDisplay.displayMech(table, this);
    }

    @Override
    public TextureRegion getContentIcon(){
        return iconRegion;
    }

    @Override
    public String getContentName(){
        return name;
    }

    @Override
    public ContentType getContentType(){
        return ContentType.mech;
    }

    @Override
    public void load(){
        if(!flying){
            legRegion = Draw.region(name + "-leg");
            baseRegion = Draw.region(name + "-base");
        }

        region = Draw.region(name);
        iconRegion = Draw.region("mech-icon-" + name);
    }

    @Override
    public String toString(){
        return localizedName();
    }
}
