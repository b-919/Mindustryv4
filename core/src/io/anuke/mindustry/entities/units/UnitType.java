package io.anuke.mindustry.entities.units;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectSet;
import io.anuke.mindustry.content.Weapons;
import io.anuke.mindustry.entities.traits.TypeTrait;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.game.UnlockableContent;
import io.anuke.mindustry.type.ContentType;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.StatusEffect;
import io.anuke.mindustry.type.Weapon;
import io.anuke.mindustry.ui.ContentDisplay;
import io.anuke.ucore.function.Supplier;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.scene.ui.layout.Table;
import io.anuke.ucore.util.Bundles;
import io.anuke.ucore.util.Log;
import io.anuke.ucore.util.Strings;

public class UnitType extends UnlockableContent{
    protected final Supplier<? extends BaseUnit> constructor;

    public final String name;
    public final String description;
    public float health = 60;
    public float hitsize = 7f;
    public float hitsizeTile = 4f;
    public float speed = 0.4f;
    public float range = 0, attackLength = 150, pursueRange = 250f, healRange = 200f;
    public float rotatespeed = 0.2f;
    public float baseRotateSpeed = 0.1f;
    public float mass = 1f;
    public boolean isFlying;
    public boolean isTank;
    public boolean living;
    public boolean isHealer;
    public boolean isBoss = false;
    public boolean rotateWeapon = false;
    public boolean playerControllable = true;
    public boolean rtsAIControllable = true;
    public boolean targetAir = true;
    public boolean spawnsInSiegeMode = true;
    /** Cost for the ExtraSurvival Mode*/
    public int unitCost = 10;
    public float drag = 0.1f;
    public float maxVelocity = 5f;
    public float retreatPercent = 0.2f;
    public float armor = 0f;
    public float carryWeight = 1f;
    public int itemCapacity = 30;
    public float shootCone = 15f;
    public ObjectSet<Item> toMine = new ObjectSet<>();
    public float buildPower = 0.3f, minePower = 0.7f;
    public Weapon weapon = Weapons.blaster;
    public Weapon[] weapons = new Weapon[0];
    public float healTurretOffsetX, healTurretOffsetY;
    public Color trailColor = Color.valueOf("ffa665");
    public float engineOffsetX = 0f;
    public float engineOffsetY = -6f;
    public boolean engineMirror = false;
    public float engineSize = 5f;
    public boolean emitLight = true;
    public float lightRadius = 60f;
    public Color lightColor = Color.WHITE;
    public float lightOpacity = 0.5f;
    public ObjectSet<StatusEffect> immunities = new ObjectSet<>();

    //leg unit fields
    /**number of legs this unit has*/
    public int legCount = 4;
    /**size of groups in which legs move. for example, insects (6 legs) usually move legs in groups of 3.*/
    public int legGroupSize = 2;
    /**total length of a leg (both segments)*/
    public float legLength = 10f;
    /**how fast individual legs move towards their destination (non-linear)*/
    public float legSpeed = 0.1f;
    /**scale for how far in front (relative to unit velocity) legs try to place themselves; if legs lag behind a unit, increase this number*/
    public float legForwardScl = 1f;
    /**leg offset from the center of the unit*/
    public float legBaseOffset = 0f;
    /**scaling for space between leg movements*/
    public float legMoveSpace = 1f;
    /**for legs without "joints", this is how much the second leg sprite is moved "back" by, so it covers the joint region*/
    public float legExtension = 0f;
    /**higher values of this field make groups of legs move less in-sync with each other.*/
    public float legPairOffset = 0f;
    /**scaling for how far away legs *try* to be from the body (not their actual length); e.g. if set to 0.5, legs will appear somewhat folded*/
    public float legLengthScl = 1f;
    /**if legStraightness > 0, this is the scale for how far away legs are from the body horizontally*/
    public float legStraightLength = 1f;
    /**maximum length of an individual leg as fraction of real length*/
    public float legMaxLength = 1.75f;
    /**minimum length of an individual leg as fraction of real length*/
    public float legMinLength = 0f;
    /**splash damage dealt when a leg touches the ground*/
    public float legSplashDamage = 0f;
    /**splash damage radius of legs*/
    public float legSplashRange = 5f;
    /**how straight the leg base/origin is (0 = circular, 1 = line)*/
    public float baseLegStraightness = 0f;
    /**how straight the leg outward angles are (0 = circular, 1 = horizontal line)*/
    public float legStraightness = 0f;
    /**if true, legs are locked to the base of the unit instead of being on an implicit rotating "mount".*/
    public boolean lockLegBase = false;
    /**if true, legs always try to move around even when the unit is not moving (leads to more natural behavior)*/
    public boolean legContinuousMove = false;
    public boolean flipBackLegs = true, flipLegSide = false;

    public TextureRegion iconRegion, legRegion, treadRegion, baseRegion, region, jointRegion, footRegion, legBaseRegion, baseJointRegion;

public <T extends BaseUnit> UnitType(String name, Class<T> type, Supplier<T> mainConstructor){
        this.name = name;
        this.constructor = mainConstructor;
        this.description = Bundles.getOrNull("unit." + name + ".description");

        TypeTrait.registerType(type, mainConstructor);

        if(!Bundles.has("unit." + this.name + ".name")){
            Log.err("Warning: unit '" + name + "' is missing a localized name. Add the follow to bundle.properties:");
            Log.err("unit." + name + ".name=" + Strings.capitalize(name.replace('-', '_')));
        }
    }

    /**
     * Appends weapons to this unit's mount array, after {@link #weapon}.
     */
    public void addWeapons(Weapon... weapons){
        if(weapons.length == 0) return;

        Weapon[] combined = new Weapon[this.weapons.length + weapons.length];
        System.arraycopy(this.weapons, 0, combined, 0, this.weapons.length);
        System.arraycopy(weapons, 0, combined, this.weapons.length, weapons.length);
        this.weapons = combined;
    }

    /**Replaces this unit's mount array. {@link #weapon} is still prepended unless it is null.*/
    public UnitType setWeapons(Weapon... weapons){
        this.weapons = weapons;
        return this;
    }

    /**Every weapon this unit mounts, {@link #weapon} first.*/
    public Weapon[] allWeapons(){
        return allWeapons(weapon);
    }

    /**Every weapon a unit of this type mounts when its weapon field is {@code override}.*/
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

    @Override
    public void displayInfo(Table table){
        ContentDisplay.displayUnit(table, this);
    }

    @Override
    public String localizedName(){
        return Bundles.get("unit." + name + ".name");
    }

    @Override
    public TextureRegion getContentIcon(){
        return iconRegion;
    }

    @Override
    public void load(){
        iconRegion = Draw.region("unit-icon-" + name);
        region = Draw.region(name);

        if(!isFlying){
            if(!isTank)legRegion = Draw.region(name + "-leg");
            baseRegion = Draw.region(name + "-base");
            if(isTank)treadRegion = Draw.region(name + "-tread");
            jointRegion = Draw.region(name + "-joint");
            baseJointRegion = Draw.region(name + "-joint-base");
            footRegion = Draw.region(name + "-foot");
            legBaseRegion = Draw.region(name + "-leg-base", legRegion);
        }
    }

    @Override
    public ContentType getContentType(){
        return ContentType.unit;
    }

    @Override
    public String getContentName(){
        return name;
    }

    public BaseUnit create(Team team){
        BaseUnit unit = constructor.get();
        unit.init(this, team);
        return unit;
    }
}
