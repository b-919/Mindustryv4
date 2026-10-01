package io.anuke.mindustry.type;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.fx.Fx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.bullet.Bullet;
import io.anuke.mindustry.entities.traits.ShooterTrait;
import io.anuke.mindustry.entities.weapon.WeaponMount;
import io.anuke.mindustry.entities.weapon.WeaponMounts;
import io.anuke.mindustry.game.Content;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.sounds.Sounds;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.core.Effects;
import io.anuke.ucore.core.Effects.Effect;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;

public class Weapon extends Content{
    public final String name;

    /**minimum cursor distance from player, fixes 'cross-eyed' shooting.*/
    protected static float minPlayerDist = 20f;
    /**ammo type map. set with setAmmo()*/
    protected AmmoType ammo;
    /**shell ejection effect*/
    protected Effect ejectEffect = Fx.none;
    /**weapon reload in frames*/
    protected float reload;
    /**amount of shots per fire*/
    protected int shots = 1;
    /**spacing in degrees between multiple shots, if applicable*/
    protected float spacing = 12f;
    /**inaccuracy of degrees of each shot*/
    protected float inaccuracy = 0f;
    /**intensity and duration of each shot's screen shake*/
    protected float shake = 0f;
    /**visual weapon knockback.*/
    protected float recoil = 1.5f;
    //modern weapon mount/barrel offsets
    /**mount lateral offset from unit center*/
    public float x = 5f;
    /**mount forward offset from unit center*/
    public float y = 0f;
    /**barrel lateral offset from mount center*/
    public float shootX = 0f;
    /**barrel forward offset from mount center*/
    public float shootY = 3f;

    //legacy fields (I'm lazy to update everything)
    /**@deprecated use {@link #x}*/
    @Deprecated
    public float width = 4f;
    /**@deprecated use {@link #shootY}*/
    @Deprecated
    public float length = 3f;
    /**@deprecated use {@link #mirror}*/
    @Deprecated
    public boolean weaponMirror = true;
    /**@deprecated use {@link #alternate}*/
    @Deprecated
    public boolean roundrobin = false;

    /**whether the weapon is mirrored on both sides (modern name)*/
    public boolean mirror = true;
    /**whether to shoot the weapons in different arms one after another (modern name)*/
    public boolean alternate = true;
    /**whether this weapon's mount rotates independently of its owner.*/
    public boolean rotate = false;
    /**mount rotation speed, in degrees per tick.*/
    public float rotateSpeed = 10f;
    /**resting mount rotation, relative to the owner.*/
    public float baseRotation = 0f;
    /**overrides the owner's shoot cone, in degrees. Negative means "use the owner's cone".*/
    public float shootCone = -1f;
    /**whether this weapon fires regardless of the owner's shoot cone. Use for bullets that correct their own velocity.*/
    public boolean ignoreShootCone = false;
    /**Cone in which the weapon can rotate relative to its mount. 361 = unlimited.*/
    public float rotationLimit = 361f;
    /**radius of shadow drawn under the weapon; <0 to disable*/
    public float shadow = -1f;
    /**fraction of velocity that is random*/
    protected float velocityRnd = 0f;
    /**translator for vector calculations*/
    protected Translator tr = new Translator();
    public Sound shootSound;
    public String shootSoundName;

    public TextureRegion equipRegion, region;

    public Weapon(String name){
        this.name = name;
        syncLegacyToModern();
    }

    /**Sync legacy fields to modern ones after content initialization.*/
    public void syncLegacyToModern(){
        if(width != 4f || x == 5f) x = width;
        if(length != 3f || shootY == 3f) shootY = length;
        if(!weaponMirror || !mirror) mirror = weaponMirror;
        if(roundrobin || !alternate) alternate = roundrobin;
    }

    public void setShootSound(String name){
        shootSoundName = name;
        shootSound = Sounds.get(name);
    }

    @Remote(targets = Loc.server, called = Loc.both, unreliable = true)
    public static void onPlayerShootWeapon(Player player, float x, float y, float rotation, boolean left){
        if(player == null) return;
        //clients do not see their own shoot events: they are simulated completely clientside to prevent laggy visuals
        //messing with the firerate or any other stats does not affect the server (take that, script kiddies!)
        if(Net.client() && player == Vars.players[0]){
            return;
        }

        shootDirect(player, x, y, rotation, left);
    }

    @Remote(targets = Loc.server, called = Loc.both, unreliable = true)
    public static void onGenericShootWeapon(ShooterTrait shooter, float x, float y, float rotation, boolean left){
        if(shooter == null) return;
        shootDirect(shooter, x, y, rotation, left);
    }

    @Remote(targets = Loc.server, called = Loc.both, unreliable = true)
    public static void onPlayerShootWeaponMount(Player player, int mount, float x, float y, float rotation){
        if(player == null) return;
        //clients do not see their own shoot events, see onPlayerShootWeapon
        if(Net.client() && player == Vars.players[0]) return;

        shootDirectMount(player, mount, x, y, rotation);
    }

    @Remote(targets = Loc.server, called = Loc.both, unreliable = true)
    public static void onGenericShootWeaponMount(ShooterTrait shooter, int mount, float x, float y, float rotation){
        if(shooter == null) return;
        shootDirectMount(shooter, mount, x, y, rotation);
    }

    /**Fires mount {@code index} of {@code shooter}, identified by index rather than by side, and syncs its remote cooldown/recoil.*/
    public static void shootDirectMount(ShooterTrait shooter, int index, float offsetX, float offsetY, float rotation){
        if(shooter == null) return;

        WeaponMount[] mounts = shooter.getWeaponMounts();
        if(index < 0 || index >= mounts.length) return;

        WeaponMount mount = mounts[index];
        Weapon weapon = mount.weapon;
        if(weapon == null) return;

        float x = shooter.getX() + offsetX;
        float y = shooter.getY() + offsetY;

        Angles.shotgun(weapon.shots, weapon.spacing, rotation, f -> weapon.bullet(shooter, x, y, f + Mathf.range(weapon.inaccuracy)));

        AmmoType ammo = weapon.ammo;
        if(ammo != null){
            weapon.tr.trns(rotation + 180f, ammo.recoil);
            shooter.getVelocity().add(weapon.tr);
        }

        weapon.tr.trns(rotation, 3f);

        Effects.shake(weapon.shake, weapon.shake, x, y);
        Effects.effect(weapon.ejectEffect, x, y, rotation * -Mathf.sign(mount.flip));
        if(ammo != null){
            Effects.effect(ammo.shootEffect, x + weapon.tr.x, y + weapon.tr.y, rotation, shooter);
            Effects.effect(ammo.smokeEffect, x + weapon.tr.x, y + weapon.tr.y, rotation, shooter);
        }
        Sound sound = weapon.shootSound == null ? Sounds.shoot : weapon.shootSound;
        if(Vars.soundController != null && sound != null){
            Vars.soundController.at(sound, x, y, 1f, 1f);
        }

        //reload and recoil are per mount, so remote clients only need the index
        mount.recoil = 1f;
        mount.reload = weapon.reload;
    }

    public static void shootDirect(ShooterTrait shooter, float offsetX, float offsetY, float rotation, boolean left){
        float x = shooter.getX() + offsetX;
        float y = shooter.getY() + offsetY;

        Weapon weapon = shooter.getWeapon();

        Angles.shotgun(weapon.shots, weapon.spacing, rotation, f -> weapon.bullet(shooter, x, y, f + Mathf.range(weapon.inaccuracy)));
        AmmoType ammo = weapon.ammo;

        weapon.tr.trns(rotation + 180f, ammo.recoil);

        shooter.getVelocity().add(weapon.tr);

        weapon.tr.trns(rotation, 3f);

        Effects.shake(weapon.shake, weapon.shake, x, y);
        Effects.effect(weapon.ejectEffect, x, y, rotation * -Mathf.sign(left));
        Effects.effect(ammo.shootEffect, x + weapon.tr.x, y + weapon.tr.y, rotation, shooter);
        Effects.effect(ammo.smokeEffect, x + weapon.tr.x, y + weapon.tr.y, rotation, shooter);
        Sound sound = weapon.shootSound == null ? Sounds.shoot : weapon.shootSound;
        if(Vars.soundController != null && sound != null){
            Vars.soundController.at(sound, x, y, 1f, 1f);
        }

        //reset timer for remote players
        shooter.getTimer().get(shooter.getShootTimer(left), weapon.reload);
    }

    @Override
    public void load(){
        equipRegion = Draw.region(name + "-equip");
        region = Draw.region(name);
        syncLegacyToModern();
    }

    @Override
    public ContentType getContentType(){
        return ContentType.weapon;
    }

    public AmmoType getAmmo(){
        return ammo;
    }

    public void update(ShooterTrait shooter, float pointerX, float pointerY){
        update(shooter, true, pointerX, pointerY);
        if(mirror) update(shooter, false, pointerX, pointerY);
    }

    private void update(ShooterTrait shooter, boolean left, float pointerX, float pointerY){
        if(shooter.getTimer().get(shooter.getShootTimer(left), reload)){
            if(alternate){
                shooter.getTimer().reset(shooter.getShootTimer(!left), reload / 2f);
            }

            tr.set(pointerX, pointerY).sub(shooter.getX(), shooter.getY());
            if(tr.len() < minPlayerDist) tr.setLength(minPlayerDist);

            float cx = tr.x + shooter.getX(), cy = tr.y + shooter.getY();

            float ang = tr.angle();
            tr.trns(ang - 90, x * Mathf.sign(left), shootY);

            shoot(shooter, tr.x, tr.y, Angles.angle(shooter.getX() + tr.x, shooter.getY() + tr.y, cx, cy), left);
        }
    }

    public void update(ShooterTrait shooter, float mountX, float mountY, float angle, boolean left){
        if(shooter.getTimer().get(shooter.getShootTimer(left), reload)){
            if(alternate){
                shooter.getTimer().reset(shooter.getShootTimer(!left), reload / 2f);
            }

            shoot(shooter, mountX - shooter.getX(), mountY - shooter.getY(), angle, left);
        }
    }

    public float getRecoil(ShooterTrait player, boolean left){
        return (1f - Mathf.clamp(player.getTimer().getTime(player.getShootTimer(left)) / reload)) * recoil;
    }

    public float getRecoil(){
        return recoil;
    }

    public float getReload(){
        return reload;
    }

    public int getShots(){
        return shots;
    }

    /**Whether this weapon's mount rotates relative to its owner.*/
    public boolean rotates(WeaponMounts owner){
        return rotate || owner.rotatesWeapons();
    }

    public float getShootCone(WeaponMounts owner){
        return shootCone >= 0f ? shootCone : owner.getShootCone();
    }

    /**
     * Advances one mount: reload cooldown, recoil decay, mount rotation and, when aimed well enough, firing.
     */
    public void update(WeaponMounts owner, WeaponMount mount){
        float delta = Timers.delta();
        float mult = owner.getReloadMultiplier();

        mount.reload = Math.max(mount.reload - delta * mult, 0f);
        mount.recoil = Math.max(mount.recoil - delta * mult / Math.max(1f, reload), 0f);

        boolean rotating = rotates(owner);
        float bodyRotation = owner.getRotation();
        float frame = bodyRotation - 90f;

        float mountX = owner.getX() + Angles.trnsx(frame, lateral(owner, mount), mount.mountY() + owner.getMountOffsetY(mount));
        float mountY = owner.getY() + Angles.trnsy(frame, lateral(owner, mount), mount.mountY() + owner.getMountOffsetY(mount));

        float aimAngle = Angles.angle(owner.getX(), owner.getY(), mount.aimX, mount.aimY);

        if(rotating){
            if(owner.canShootWeapons() && (mount.rotate || mount.shoot)){
                mount.targetRotation = Angles.angle(mountX, mountY, mount.aimX, mount.aimY) - bodyRotation;
                mount.rotation = Angles.moveToward(mount.rotation, mount.targetRotation, rotateSpeed * delta);
            }
        }else{
            mount.rotation = baseRotation;
            mount.targetRotation = aimAngle;
        }

        float weaponRotation = bodyRotation + mount.rotation;
        float barrelX = mountX + Angles.trnsx(weaponRotation - 90f, shootX, length);
        float barrelY = mountY + Angles.trnsy(weaponRotation - 90f, shootX, length);

        if(!mount.shoot || !owner.canShootWeapons() || mount.reload > 0f) return;

        //a mount may only fire once it is pointing close enough at the aim point
        float cone = getShootCone(owner);
        float current = rotating ? mount.rotation : aimAngle;
        if(!ignoreShootCone && cone < 360f && Angles.angleDist(current, rotating ? mount.targetRotation : aimAngle) > cone) return;

        float shootAngle = rotating ? weaponRotation
            : Angles.angle(barrelX, barrelY, mount.aimX, mount.aimY) + (bodyRotation - aimAngle) + baseRotation;

        shootMount(owner, mount, barrelX - owner.getX(), barrelY - owner.getY(), shootAngle);

        mount.reload = reload;

        //mirror weapons alternate: the partner becomes eligible halfway through the reload
        if(roundrobin && mount.otherSide >= 0 && mount.otherSide < owner.getWeaponMounts().length){
            WeaponMount other = owner.getWeaponMounts()[mount.otherSide];
            other.reload = Math.min(other.reload, reload / 2f);
        }
    }

    public void shootMount(WeaponMounts p, WeaponMount mount, float x, float y, float angle){
        if(Net.client()){
            //call it directly, don't invoke on server
            shootDirectMount((ShooterTrait)p, mount.index, x, y, angle);
        }else{
            if(p instanceof Player){ //players need special weapon handling logic
                Call.onPlayerShootWeaponMount((Player)p, mount.index, x, y, angle);
            }else{
                Call.onGenericShootWeaponMount((ShooterTrait)p, mount.index, x, y, angle);
            }
        }
    }

    /**Draws this weapon's equip region at its mount position.*/
    public void draw(WeaponMounts owner, WeaponMount mount){
        if(equipRegion == null) return;
        drawRegion(owner, mount, equipRegion);
    }

    /**Draws this weapon's barrel region at its mount position, after the body.*/
    public void drawBarrel(WeaponMounts owner, WeaponMount mount){
        if(region == null) return;
        drawRegion(owner, mount, region);
    }

    private void drawRegion(WeaponMounts owner, WeaponMount mount, TextureRegion reg){
        float bodyRotation = owner.getRotation();
        float frame = bodyRotation - 90f;
        float weaponRotation = bodyRotation + mount.rotation;
        float offset = -mount.getRecoil();

        float x = owner.getX()
                + Angles.trnsx(frame, lateral(owner, mount), mount.mountY() + owner.getMountOffsetY(mount))
                + Angles.trnsx(weaponRotation - 90f, 0f, offset);
        float y = owner.getY()
                + Angles.trnsy(frame, lateral(owner, mount), mount.mountY() + owner.getMountOffsetY(mount))
                + Angles.trnsy(weaponRotation - 90f, 0f, offset);

        float w = reg.getRegionWidth() * (mount.flip ? -1f : 1f);
        float h = reg.getRegionHeight();

        Draw.rect(reg, x, y, w, h, weaponRotation - 90f);
    }

    /**Lateral offset of this weapon's mount, including any per-owner adjustment.*/
    private float lateral(WeaponMounts owner, WeaponMount mount){
        return mount.mountX() + owner.getMountOffsetX(mount);
    }

    public void shoot(ShooterTrait p, float x, float y, float angle, boolean left){
        if(Net.client()){
            //call it directly, don't invoke on server
            shootDirect(p, x, y, angle, left);
        }else{
            if(p instanceof Player){ //players need special weapon handling logic
                Call.onPlayerShootWeapon((Player) p, x, y, angle, left);
            }else{
                Call.onGenericShootWeapon(p, x, y, angle, left);
            }
        }
    }

    void bullet(ShooterTrait owner, float x, float y, float angle){
        if(owner == null) return;

        tr.trns(angle, 3f);
        Bullet.create(ammo.bullet,
                owner, owner.getTeam(), x + tr.x, y + tr.y, angle, (1f - velocityRnd) + Mathf.random(velocityRnd));
    }
}
