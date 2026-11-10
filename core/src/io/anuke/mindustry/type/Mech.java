package io.anuke.mindustry.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.content.Weapons;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.units.UnitType;
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
    /** Reusable player behaviours; replaces the old hard-coded Mech hook overrides. */
    public final Array<MechAbility> abilities = new Array<>();
    /** Unit data used when the player is treated as a unit. */
    public UnitType unitType;
    /** Cached result of {@link #allWeapons(Weapon)} for the mech's own weapon, which is queried every frame. */
    private Weapon[] cachedAllWeapons;

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
        if(override == weapon && cachedAllWeapons != null){
            return cachedAllWeapons;
        }

        Weapon[] result;

        if(weapons.length == 0){
            result = new Weapon[]{override};
        }else if(override == null){
            result = weapons;
        }else{
            result = new Weapon[weapons.length + 1];
            result[0] = override;
            System.arraycopy(weapons, 0, result, 1, weapons.length);
        }

        if(override == weapon){
            cachedAllWeapons = result;
        }

        return result;
    }

    public String localizedName(){
        return Bundles.get("mech." + name + ".name");
    }

    public void updateAlt(Player player){
        for(int i = 0; i < abilities.size; i++){
            abilities.get(i).update(player);
        }
    }

    public void draw(Player player){
        for(int i = 0; i < abilities.size; i++){
            abilities.get(i).draw(player);
        }
    }

    public void drawOver(Player player){
        for(int i = 0; i < abilities.size; i++){
            abilities.get(i).drawOver(player);
        }
    }

    public void drawBloom(Player player){
        for(int i = 0; i < abilities.size; i++){
            abilities.get(i).drawBloom(player);
        }
    }

    public float getExtraArmor(Player player){
        float armor = 0f;
        for(int i = 0; i < abilities.size; i++){
            armor += abilities.get(i).getExtraArmor(player);
        }
        return armor;
    }

    public float spreadX(Player player){
        float spread = 0f;
        for(int i = 0; i < abilities.size; i++){
            spread += abilities.get(i).spreadX(player);
        }
        return spread;
    }

    public float getRotationAlpha(Player player){
        float alpha = 1f;
        for(int i = 0; i < abilities.size; i++){
            alpha *= abilities.get(i).getRotationAlpha(player);
        }
        return alpha;
    }

    public boolean canShoot(Player player){
        for(int i = 0; i < abilities.size; i++){
            if(!abilities.get(i).canShoot(player)) return false;
        }
        return true;
    }

    public void onLand(Player player){
        for(int i = 0; i < abilities.size; i++){
            abilities.get(i).onLand(player);
        }
    }

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

        for(int i = 0; i < abilities.size; i++){
            abilities.get(i).load(this);
        }
    }

    @Override
    public String toString(){
        return localizedName();
    }
}
