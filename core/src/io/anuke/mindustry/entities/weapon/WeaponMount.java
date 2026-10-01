package io.anuke.mindustry.entities.weapon;

import io.anuke.mindustry.type.Weapon;

/**
 * Runtime state of a single weapon mounted on a unit.
 * <p>
 * Backport of Mindustry's {@code mindustry.entities.units.WeaponMount}. One mount exists per physical weapon barrel, which means a
 * mirrored {@link Weapon#mirror weapon} produces two mounts linked through {@link #otherSide}.
 */
public class WeaponMount{
    /**Weapon definition this mount fires. Never null.*/
    public final Weapon weapon;
    /**Index of this mount in the owner's mount array.*/
    public final int index;
    /**Index of the mirrored partner mount, or -1 when this weapon is not mirrored.*/
    public int otherSide = -1;
    /**Whether this is the mirrored copy of a mirrored weapon.*/
    public boolean flip = false;

    /**Ticks remaining before this mount may fire again.*/
    public float reload = 0f;
    /**Current rotation relative to the owner's body rotation.*/
    public float rotation = 0f;
    /**Rotation this mount is currently trying to reach, relative to the owner for rotating weapons, absolute otherwise.*/
    public float targetRotation = 0f;
    /**Visual recoil, 1 right after firing, decaying to 0.*/
    public float recoil = 0f;

    /**World space point every mount of the same owner aims at.*/
    public float aimX = 0f, aimY = 0f;
    /**Whether the owner wants this mount to fire.*/
    public boolean shoot = false;
    /**Whether the owner wants this mount to track its aim point.*/
    public boolean rotate = false;

    public WeaponMount(Weapon weapon, int index){
        this.weapon = weapon;
        this.index = index;
        this.rotation = weapon == null ? 0f : weapon.baseRotation;
    }

    /**Lateral offset of this mount from the owner's center, mirrored for flipped mounts.*/
    public float mountX(){
        return weapon == null ? 0f : weapon.x * (flip ? -1f : 1f);
    }

    /**Forward offset of this mount from the owner's center.*/
    public float mountY(){
        return weapon == null ? 0f : weapon.y;
    }

    /**Whether this mount is currently able to fire.*/
    public boolean ready(){
        return reload <= 0f;
    }

    /**Scaled recoil, in world units.*/
    public float getRecoil(){
        return weapon == null ? 0f : recoil * weapon.getRecoil();
    }

    /**Restores this mount to its freshly constructed state.*/
    public void reset(){
        reload = recoil = 0f;
        rotation = weapon == null ? 0f : weapon.baseRotation;
        targetRotation = 0f;
        shoot = rotate = false;
    }
}