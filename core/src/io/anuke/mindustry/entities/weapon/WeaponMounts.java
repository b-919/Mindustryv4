package io.anuke.mindustry.entities.weapon;

import com.badlogic.gdx.utils.Array;
import io.anuke.mindustry.entities.traits.TargetTrait;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.type.Weapon;

import java.util.ArrayList;

/**
 * Implemented by anything that can carry an array of independently controlled weapon mounts.
 * <p>
 * Backport of Mindustry's {@code mindustry.entities.comp.WeaponsComp}. Implementations must supply their weapon definitions through
 * {@link #getWeaponDefinitions()} and their mount array through {@link #getWeaponMounts()}.
 */
public interface WeaponMounts extends TargetTrait{

    /**Mounts currently attached. Never null, but may be empty.*/
    WeaponMount[] getWeaponMounts();

    /**Replaces the mount array. Called by {@link #buildWeaponMounts(Weapon[])}.*/
    void setWeaponMounts(WeaponMount[] mounts);

    /**Body rotation in world space, in degrees, 0 = up.*/
    float getRotation();

    /**
     * Every weapon this owner should be able to fire, in mount order. Mirrored weapons may be returned once; mount expansion
     * creates the second side. The default returns the legacy single weapon.
     */
    default Weapon[] getWeaponDefinitions(){
        return new Weapon[0];
    }

    /**Reload multiplier applied to every mount of this owner.*/
    default float getReloadMultiplier(){
        return 1f;
    }

    /**Maximum angle (half cone, in degrees) between a mount and its aim point before it refuses to fire.*/
    default float getShootCone(){
        return 15f;
    }

    /**Minimum distance from the owner to its aim point, prevents cross-eyed shooting.*/
    default float getMinAimDistance(){
        return 20f;
    }

    /**Whether weapons are currently allowed to fire at all.*/
    default boolean canShootWeapons(){
        return true;
    }

    /**
     * Whether every mounted weapon gets an independently rotating mount, regardless of its own {@link Weapon#rotate} flag.
     * Unit types use this to keep their existing "rotating turret" behaviour without per-weapon opt-in.
     */
    default boolean rotatesWeapons(){
        return false;
    }

    /**Extra lateral offset added to this mount. Mechs use it to widen weapon spread.*/
    default float getMountOffsetX(WeaponMount mount){
        return 0f;
    }

    /**Extra forward offset added to this mount.*/
    default float getMountOffsetY(WeaponMount mount){
        return 0f;
    }

    /**
     * Expands {@link #getWeaponDefinitions()} into runtime mounts, duplicating mirrored weapons and linking both sides together.
     */
    default void buildWeaponMounts(Weapon[] definitions){
        Array<WeaponMount> mounts = new Array<>(definitions.length * 2);
        for(Weapon weapon : definitions){
            if(weapon == null) continue;

            WeaponMount main = new WeaponMount(weapon, mounts.size);
            mounts.add(main);

            if(weapon.mirror){
                WeaponMount mirror = new WeaponMount(weapon, mounts.size);
                mirror.flip = true;
                mirror.otherSide = main.index;
                main.otherSide = mirror.index;
                mounts.add(mirror);
            }
        }
        setWeaponMounts(mounts.toArray(WeaponMount.class));
    }

    /**Sets the world space point every mount of this owner aims at, clamped to {@link #getMinAimDistance()}.*/
    default void aimWeaponMounts(float x, float y){
        float dx = x - getX(), dy = y - getY();
        float len = (float)Math.sqrt(dx * dx + dy * dy);
        float min = getMinAimDistance();
        if(len < min){
            if(len == 0f){
                dx = 0f;
                dy = min;
            }else{
                dx = dx / len * min;
                dy = dy / len * min;
            }
        }
        float aimX = getX() + dx, aimY = getY() + dy;

        for(WeaponMount mount : getWeaponMounts()){
            mount.aimX = aimX;
            mount.aimY = aimY;
        }
    }

    /**Tells every mount whether it should track its aim point and whether it should fire.*/
    default void controlWeaponMounts(boolean rotate, boolean shoot){
        for(WeaponMount mount : getWeaponMounts()){
            mount.rotate = rotate;
            mount.shoot = shoot;
        }
    }

    /**Advances reload, recoil and rotation of every mount, firing the ones that are ready and aimed.*/
    default void updateWeaponMounts(){
        for(WeaponMount mount : getWeaponMounts()){
            mount.weapon.update(this, mount);
        }
    }

    /**Draws every mount's equip region. Callers should already be inside their draw batch.*/
    default void drawWeaponMounts(){
        for(WeaponMount mount : getWeaponMounts()){
            mount.weapon.draw(this, mount);
        }
    }

    /**Draws every mount's barrel region, after the body has been drawn.*/
    default void drawWeaponMountsOver(){
        for(WeaponMount mount : getWeaponMounts()){
            mount.weapon.drawBarrel(this, mount);
        }
    }

    /**Largest range of any mounted weapon, or 0 if nothing is mounted.*/
    default float getWeaponMountRange(){
        float range = 0f;
        for(WeaponMount mount : getWeaponMounts()){
            AmmoType ammo = mount.weapon.getAmmo();
            if(ammo == null) continue;
            range = Math.max(range, ammo.getRange());
        }
        return range;
    }

    /**Ammo type of the first mounted weapon that has one, or null.*/
    default AmmoType getWeaponMountAmmo(){
        for(WeaponMount mount : getWeaponMounts()){
            AmmoType ammo = mount.weapon.getAmmo();
            if(ammo != null) return ammo;
        }
        return null;
    }
}