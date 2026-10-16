package doom.game.combat;

import doom.game.actor.Actor;
import doom.game.actor.Mobjs;
import doom.game.data.MobjType;
import doom.game.data.StateAction;
import doom.game.data.StateId;
import doom.game.data.ThingType;
import doom.game.data.WeaponInfo;
import doom.game.physics.Physics;
import doom.game.player.PSprite;
import doom.game.player.Player;
import doom.game.view.AmmoType;
import doom.game.view.CheatFlags;
import doom.game.view.MobjFlags;
import doom.game.view.Power;
import doom.game.view.SoundId;
import doom.game.view.Weapon;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;

/**
 * Damage and death ({@code p_inter.c}), the projectile and effect spawn functions ({@code p_mobj.c}) and the offensive
 * weapon actions ({@code p_pspr.c}). Every RNG call is made in the order of the original.
 */
public final class CombatImpl implements Combat {

    /** {@code BASETHRESHOLD}. */
    private static final int BASETHRESHOLD = 100;
    /** {@code 16 * 64 * FRACUNIT}: range of the autoaim sweeps. */
    private static final int AIM_RANGE = 16 * 64 * Fixed.FRACUNIT;
    /** {@code NUMAMMO}. */
    private static final int NUMAMMO = AmmoType.COUNT;

    private final World world;

    public CombatImpl(World world) {
        this.world = world;
    }

    // ================================================================================================ p_inter.c

    /** {@code P_KillMobj}: handles the death of a map object (counts, corpse flags, state change and drops). */
    @Override
    @VanillaRef("p_inter.c:P_KillMobj")
    public void killMobj(Actor source, Actor target) {
        target.flags &= ~(MobjFlags.SHOOTABLE | MobjFlags.FLOAT | MobjFlags.SKULLFLY);

        if (target.type != MobjType.SKULL) {
            target.flags &= ~MobjFlags.NOGRAVITY;
        }

        target.flags |= MobjFlags.CORPSE | MobjFlags.DROPOFF;
        target.height >>= 2;

        if (source != null && source.player != null) {
            // count for intermission
            if ((target.flags & MobjFlags.COUNTKILL) != 0) {
                source.player.killCount++;
            }
            if (target.player != null) {
                source.player.frags[target.player.index]++;
            }
        } else if (!world.netGame && (target.flags & MobjFlags.COUNTKILL) != 0) {
            // count all monster deaths, even those caused by other monsters
            world.players[0].killCount++;
        }

        if (target.player != null) {
            // count environment kills against you
            if (source == null) {
                target.player.frags[target.player.index]++;
            }

            target.flags &= ~MobjFlags.SOLID;
            target.player.playerState = Player.PST_DEAD;
            world.weapons.dropWeapon(target.player);
            // AM_Stop() for the console player is presentation: the view sees playerState == PST_DEAD.
        }

        ThingType info = target.info;
        if (target.health < -info.spawnHealth && info.xDeathState != 0) {
            world.mobjs.setMobjState(target, info.xDeathState);
        } else {
            world.mobjs.setMobjState(target, info.deathState);
        }
        target.tics -= world.rng.game() & 3;

        if (target.tics < 1) {
            target.tics = 1;
        }

        // Drop stuff. This determines the kind of object spawned during the death frame of a thing.
        int item;
        switch (target.type) {
            case MobjType.WOLFSS, MobjType.POSSESSED -> item = MobjType.CLIP;
            case MobjType.SHOTGUY -> item = MobjType.SHOTGUN;
            case MobjType.CHAINGUY -> item = MobjType.CHAINGUN;
            default -> {
                return;
            }
        }

        Actor mo = world.mobjs.spawnMobj(target.x, target.y, Mobjs.ONFLOORZ, item);
        mo.flags |= MobjFlags.DROPPED; // special versions of items
    }

    /**
     * {@code P_DamageMobj}: damages both enemies and players. {@code inflictor} is the thing that caused the damage
     * (creature or missile, may be null); {@code source} is the thing to target after taking damage (creature or null,
     * the same as the inflictor for melee attacks).
     */
    @Override
    @VanillaRef("p_inter.c:P_DamageMobj")
    public void damageMobj(Actor target, Actor inflictor, Actor source, int damage) {
        if ((target.flags & MobjFlags.SHOOTABLE) == 0) {
            return; // shouldn't happen...
        }

        if (target.health <= 0) {
            return;
        }

        if ((target.flags & MobjFlags.SKULLFLY) != 0) {
            target.momX = 0;
            target.momY = 0;
            target.momZ = 0;
        }

        Player player = target.player;
        if (player != null && world.gameSkill == 0) { // sk_baby
            damage >>= 1; // take half damage in trainer mode
        }

        // Some close combat weapons should not inflict thrust and push the victim out of reach, thus kick away
        // unless using the chainsaw.
        if (inflictor != null
                && (target.flags & MobjFlags.NOCLIP) == 0
                && (source == null || source.player == null || source.player.readyWeapon != Weapon.CHAINSAW)) {
            int ang = Trig.pointToAngle2(inflictor.x, inflictor.y, target.x, target.y);

            int thrust = damage * (Fixed.FRACUNIT >> 3) * 100 / target.info.mass;

            // make fall forwards sometimes
            if (damage < 40
                    && damage > target.health
                    && target.z - inflictor.z > 64 * Fixed.FRACUNIT
                    && (world.rng.game() & 1) != 0) {
                ang += Angle.ANG180;
                thrust *= 4;
            }

            ang >>>= Angle.ANGLETOFINESHIFT;
            target.momX += Fixed.mul(thrust, Tables.fineCosine(ang));
            target.momY += Fixed.mul(thrust, Tables.fineSine(ang));
        }

        // player specific
        if (player != null) {
            // end of game hell hack
            if (target.subsector.sector.special == 11 && damage >= target.health) {
                damage = target.health - 1;
            }

            // Below certain threshold, ignore damage in GOD mode, or with INVUL power.
            if (damage < 1000
                    && ((player.cheats & CheatFlags.GODMODE) != 0 || player.powers[Power.INVULNERABILITY] != 0)) {
                return;
            }

            if (player.armorType != 0) {
                int saved;
                if (player.armorType == 1) {
                    saved = damage / 3;
                } else {
                    saved = damage / 2;
                }

                if (player.armorPoints <= saved) {
                    // armor is used up
                    saved = player.armorPoints;
                    player.armorType = 0;
                }
                player.armorPoints -= saved;
                damage -= saved;
            }
            player.health -= damage; // mirror mobj health here for Dave
            if (player.health < 0) {
                player.health = 0;
            }

            player.attacker = source;
            player.damageCount += damage; // add damage after armor / invuln

            if (player.damageCount > 100) {
                player.damageCount = 100; // teleport stomp does 10k points...
            }
            // I_Tactile() is a no-op here.
        }

        // do the damage
        target.health -= damage;
        if (target.health <= 0) {
            killMobj(source, target);
            return;
        }

        if (world.rng.game() < target.info.painChance && (target.flags & MobjFlags.SKULLFLY) == 0) {
            target.flags |= MobjFlags.JUSTHIT; // fight back!

            world.mobjs.setMobjState(target, target.info.painState);
        }

        target.reactionTime = 0; // we're awake now...

        // gameversion < exe_doom_1_5 can never hold: the oldest emulated executable is 1.9.
        if ((target.threshold == 0 || target.type == MobjType.VILE)
                && source != null
                && source != target
                && source.type != MobjType.VILE) {
            // if not intent on another player, chase after this one
            target.target = source;
            target.threshold = BASETHRESHOLD;
            if (target.state == world.defs.states()[target.info.spawnState]
                    && target.info.seeState != StateId.NULL) {
                world.mobjs.setMobjState(target, target.info.seeState);
            }
        }
    }

    // ================================================================================================ p_mobj.c

    /** {@code P_ExplodeMissile}. */
    @Override
    @VanillaRef("p_mobj.c:P_ExplodeMissile")
    public void explodeMissile(Actor mo) {
        mo.momX = 0;
        mo.momY = 0;
        mo.momZ = 0;

        world.mobjs.setMobjState(mo, world.defs.things()[mo.type].deathState);

        mo.tics -= world.rng.game() & 3;

        if (mo.tics < 1) {
            mo.tics = 1;
        }

        mo.flags &= ~MobjFlags.MISSILE;

        if (mo.info.deathSound != 0) {
            world.startSound(mo, mo.info.deathSound);
        }
    }

    /** {@code P_SpawnPuff}. */
    @Override
    @VanillaRef("p_mobj.c:P_SpawnPuff")
    public void spawnPuff(int x, int y, int z) {
        z += world.rng.subRandom() << 10;

        Actor th = world.mobjs.spawnMobj(x, y, z, MobjType.PUFF);
        th.momZ = Fixed.FRACUNIT;
        th.tics -= world.rng.game() & 3;

        if (th.tics < 1) {
            th.tics = 1;
        }

        // don't make punches spark on the wall
        if (world.physics.attackRange == Physics.MELEERANGE) {
            world.mobjs.setMobjState(th, StateId.PUFF3);
        }
    }

    /** {@code P_SpawnBlood}. */
    @Override
    @VanillaRef("p_mobj.c:P_SpawnBlood")
    public void spawnBlood(int x, int y, int z, int damage) {
        z += world.rng.subRandom() << 10;
        Actor th = world.mobjs.spawnMobj(x, y, z, MobjType.BLOOD);
        th.momZ = Fixed.FRACUNIT * 2;
        th.tics -= world.rng.game() & 3;

        if (th.tics < 1) {
            th.tics = 1;
        }

        if (damage <= 12 && damage >= 9) {
            world.mobjs.setMobjState(th, StateId.BLOOD2);
        } else if (damage < 9) {
            world.mobjs.setMobjState(th, StateId.BLOOD3);
        }
    }

    /** {@code P_CheckMissileSpawn}: moves the missile forward a bit and possibly explodes it right there. */
    @Override
    @VanillaRef("p_mobj.c:P_CheckMissileSpawn")
    public void checkMissileSpawn(Actor th) {
        th.tics -= world.rng.game() & 3;
        if (th.tics < 1) {
            th.tics = 1;
        }

        // move a little forward so an angle can be computed if it immediately explodes
        th.x += th.momX >> 1;
        th.y += th.momY >> 1;
        th.z += th.momZ >> 1;

        if (!world.physics.tryMove(th, th.x, th.y)) {
            explodeMissile(th);
        }
    }

    /** {@code P_SpawnMissile}. */
    @Override
    @VanillaRef("p_mobj.c:P_SpawnMissile")
    public Actor spawnMissile(Actor source, Actor dest, int type) {
        Actor th = world.mobjs.spawnMobj(source.x, source.y, source.z + 4 * 8 * Fixed.FRACUNIT, type);

        if (th.info.seeSound != 0) {
            world.startSound(th, th.info.seeSound);
        }

        th.target = source; // where it came from
        int an = Trig.pointToAngle2(source.x, source.y, dest.x, dest.y);

        // fuzzy player
        if ((dest.flags & MobjFlags.SHADOW) != 0) {
            an += world.rng.subRandom() << 20;
        }

        th.angle = an;
        an >>>= Angle.ANGLETOFINESHIFT;
        th.momX = Fixed.mul(th.info.speed, Tables.fineCosine(an));
        th.momY = Fixed.mul(th.info.speed, Tables.fineSine(an));

        int dist = Trig.approxDistance(dest.x - source.x, dest.y - source.y);
        dist = dist / th.info.speed;

        if (dist < 1) {
            dist = 1;
        }

        th.momZ = (dest.z - source.z) / dist;
        checkMissileSpawn(th);

        return th;
    }

    /** {@code P_SpawnPlayerMissile}: tries to aim at a nearby monster. */
    @Override
    @VanillaRef("p_mobj.c:P_SpawnPlayerMissile")
    public void spawnPlayerMissile(Actor source, int type) {
        Physics physics = world.physics;

        // see which target is to be aimed at
        int an = source.angle;
        int slope = physics.aimLineAttack(source, an, AIM_RANGE);

        if (physics.lineTarget == null) {
            an += 1 << 26;
            slope = physics.aimLineAttack(source, an, AIM_RANGE);

            if (physics.lineTarget == null) {
                an -= 2 << 26;
                slope = physics.aimLineAttack(source, an, AIM_RANGE);
            }

            if (physics.lineTarget == null) {
                an = source.angle;
                slope = 0;
            }
        }

        int x = source.x;
        int y = source.y;
        int z = source.z + 4 * 8 * Fixed.FRACUNIT;

        Actor th = world.mobjs.spawnMobj(x, y, z, type);

        if (th.info.seeSound != 0) {
            world.startSound(th, th.info.seeSound);
        }

        th.target = source;
        th.angle = an;
        th.momX = Fixed.mul(th.info.speed, Tables.fineCosine(an >>> Angle.ANGLETOFINESHIFT));
        th.momY = Fixed.mul(th.info.speed, Tables.fineSine(an >>> Angle.ANGLETOFINESHIFT));
        th.momZ = Fixed.mul(th.info.speed, slope);

        checkMissileSpawn(th);
    }

    // ================================================================================================ p_pspr.c

    /** {@code DecreaseAmmo}: an ammo type beyond {@code NUMAMMO} overflows into {@code maxammo} (Chocolate Doom). */
    @VanillaRef("p_pspr.c:DecreaseAmmo")
    private static void decreaseAmmo(Player player, int ammoNum, int amount) {
        if (ammoNum < NUMAMMO) {
            player.ammo[ammoNum] -= amount;
        } else if (ammoNum - NUMAMMO < player.maxAmmo.length) {
            player.maxAmmo[ammoNum - NUMAMMO] -= amount;
        }
        // Further out the original writes past the end of the player structure; nothing to emulate.
    }

    /** {@code player->ammo[n]} where {@code n >= NUMAMMO} reads {@code maxammo[n - NUMAMMO]}. */
    private static int ammoOf(Player player, int ammoNum) {
        if (ammoNum < NUMAMMO) {
            return player.ammo[ammoNum];
        }
        int i = ammoNum - NUMAMMO;
        return i < player.maxAmmo.length ? player.maxAmmo[i] : 0;
    }

    @Override
    public void weaponAction(StateAction action, Player player, PSprite psp) {
        switch (action) {
            case A_Punch -> punch(player);
            case A_Saw -> saw(player);
            case A_FirePistol -> firePistol(player);
            case A_FireShotgun -> fireShotgun(player);
            case A_FireShotgun2 -> fireShotgun2(player);
            case A_FireCGun -> fireCGun(player, psp);
            case A_FireMissile -> fireMissile(player);
            case A_FirePlasma -> firePlasma(player);
            case A_FireBFG -> fireBfg(player);
            default -> {
                // not an offensive weapon action
            }
        }
    }

    private WeaponInfo weaponInfo(Player player) {
        return world.defs.weapons()[player.readyWeapon];
    }

    /** {@code A_Punch}. */
    @VanillaRef("p_pspr.c:A_Punch")
    private void punch(Player player) {
        int damage = (world.rng.game() % 10 + 1) << 1;

        if (player.powers[Power.STRENGTH] != 0) {
            damage *= 10;
        }

        int angle = player.mo.angle;
        angle += world.rng.subRandom() << 18;
        int slope = world.physics.aimLineAttack(player.mo, angle, Physics.MELEERANGE);
        world.physics.lineAttack(player.mo, angle, Physics.MELEERANGE, slope, damage);

        // turn to face target
        Actor target = world.physics.lineTarget;
        if (target != null) {
            world.startSound(player.mo, SoundId.PUNCH);
            player.mo.angle = Trig.pointToAngle2(player.mo.x, player.mo.y, target.x, target.y);
        }
    }

    /** {@code A_Saw}. */
    @VanillaRef("p_pspr.c:A_Saw")
    private void saw(Player player) {
        int damage = 2 * (world.rng.game() % 10 + 1);
        int angle = player.mo.angle;
        angle += world.rng.subRandom() << 18;

        // use meleerange + 1 se the puff doesn't skip the flash
        int slope = world.physics.aimLineAttack(player.mo, angle, Physics.MELEERANGE + 1);
        world.physics.lineAttack(player.mo, angle, Physics.MELEERANGE + 1, slope, damage);

        Actor target = world.physics.lineTarget;
        if (target == null) {
            world.startSound(player.mo, SoundId.SAWFUL);
            return;
        }
        world.startSound(player.mo, SoundId.SAWHIT);

        // turn to face target
        angle = Trig.pointToAngle2(player.mo.x, player.mo.y, target.x, target.y);
        int diff = angle - player.mo.angle;
        if (Integer.compareUnsigned(diff, Angle.ANG180) > 0) {
            // ANG90 is a signed int constant in the original, so -ANG90/20 is negative
            if (diff < -Angle.ANG90 / 20) {
                player.mo.angle = angle + Angle.ANG90 / 21;
            } else {
                player.mo.angle -= Angle.ANG90 / 20;
            }
        } else {
            if (Integer.compareUnsigned(diff, Angle.ANG90 / 20) > 0) {
                player.mo.angle = angle - Angle.ANG90 / 21;
            } else {
                player.mo.angle += Angle.ANG90 / 20;
            }
        }
        player.mo.flags |= MobjFlags.JUSTATTACKED;
    }

    /** {@code A_FireMissile}. */
    @VanillaRef("p_pspr.c:A_FireMissile")
    private void fireMissile(Player player) {
        decreaseAmmo(player, weaponInfo(player).ammo, 1);
        spawnPlayerMissile(player.mo, MobjType.ROCKET);
    }

    /** {@code A_FireBFG}. */
    @VanillaRef("p_pspr.c:A_FireBFG")
    private void fireBfg(Player player) {
        decreaseAmmo(player, weaponInfo(player).ammo, world.defs.misc().bfgCellsPerShot());
        spawnPlayerMissile(player.mo, MobjType.BFG);
    }

    /** {@code A_FirePlasma}. */
    @VanillaRef("p_pspr.c:A_FirePlasma")
    private void firePlasma(Player player) {
        decreaseAmmo(player, weaponInfo(player).ammo, 1);

        world.weapons.setPsprite(player, Player.PS_FLASH, weaponInfo(player).flashState + (world.rng.game() & 1));

        spawnPlayerMissile(player.mo, MobjType.PLASMA);
    }

    /**
     * {@code P_BulletSlope}: sets a slope so a near miss is at approximately the height of the intended target
     * (stored in {@code physics.bulletSlope}, which the intercepts overrun emulation can overwrite).
     */
    @VanillaRef("p_pspr.c:P_BulletSlope")
    private void bulletSlope(Actor mo) {
        Physics physics = world.physics;

        // see which target is to be aimed at
        int an = mo.angle;
        physics.bulletSlope = physics.aimLineAttack(mo, an, AIM_RANGE);

        if (physics.lineTarget == null) {
            an += 1 << 26;
            physics.bulletSlope = physics.aimLineAttack(mo, an, AIM_RANGE);
            if (physics.lineTarget == null) {
                an -= 2 << 26;
                physics.bulletSlope = physics.aimLineAttack(mo, an, AIM_RANGE);
            }
        }
    }

    /** {@code P_GunShot}. */
    @VanillaRef("p_pspr.c:P_GunShot")
    private void gunShot(Actor mo, boolean accurate) {
        int damage = 5 * (world.rng.game() % 3 + 1);
        int angle = mo.angle;

        if (!accurate) {
            angle += world.rng.subRandom() << 18;
        }

        world.physics.lineAttack(mo, angle, Physics.MISSILERANGE, world.physics.bulletSlope, damage);
    }

    /** {@code A_FirePistol}. */
    @VanillaRef("p_pspr.c:A_FirePistol")
    private void firePistol(Player player) {
        world.startSound(player.mo, SoundId.PISTOL);

        world.mobjs.setMobjState(player.mo, StateId.PLAY_ATK2);
        decreaseAmmo(player, weaponInfo(player).ammo, 1);

        world.weapons.setPsprite(player, Player.PS_FLASH, weaponInfo(player).flashState);

        bulletSlope(player.mo);
        gunShot(player.mo, player.refire == 0);
    }

    /** {@code A_FireShotgun}. */
    @VanillaRef("p_pspr.c:A_FireShotgun")
    private void fireShotgun(Player player) {
        world.startSound(player.mo, SoundId.SHOTGN);
        world.mobjs.setMobjState(player.mo, StateId.PLAY_ATK2);

        decreaseAmmo(player, weaponInfo(player).ammo, 1);

        world.weapons.setPsprite(player, Player.PS_FLASH, weaponInfo(player).flashState);

        bulletSlope(player.mo);

        for (int i = 0; i < 7; i++) {
            gunShot(player.mo, false);
        }
    }

    /** {@code A_FireShotgun2}. */
    @VanillaRef("p_pspr.c:A_FireShotgun2")
    private void fireShotgun2(Player player) {
        world.startSound(player.mo, SoundId.DSHTGN);
        world.mobjs.setMobjState(player.mo, StateId.PLAY_ATK2);

        decreaseAmmo(player, weaponInfo(player).ammo, 2);

        world.weapons.setPsprite(player, Player.PS_FLASH, weaponInfo(player).flashState);

        bulletSlope(player.mo);

        for (int i = 0; i < 20; i++) {
            int damage = 5 * (world.rng.game() % 3 + 1);
            int angle = player.mo.angle;
            angle += world.rng.subRandom() << Angle.ANGLETOFINESHIFT;
            world.physics.lineAttack(player.mo, angle, Physics.MISSILERANGE,
                    world.physics.bulletSlope + (world.rng.subRandom() << 5), damage);
        }
    }

    /** {@code A_FireCGun}. */
    @VanillaRef("p_pspr.c:A_FireCGun")
    private void fireCGun(Player player, PSprite psp) {
        world.startSound(player.mo, SoundId.PISTOL);

        if (ammoOf(player, weaponInfo(player).ammo) == 0) {
            return;
        }

        world.mobjs.setMobjState(player.mo, StateId.PLAY_ATK2);
        decreaseAmmo(player, weaponInfo(player).ammo, 1);

        world.weapons.setPsprite(player, Player.PS_FLASH,
                weaponInfo(player).flashState + psp.stateNum - StateId.CHAIN1);

        bulletSlope(player.mo);

        gunShot(player.mo, player.refire == 0);
    }

    /** {@code A_BFGSpray}: spawns a BFG explosion on every monster in view. */
    @Override
    @VanillaRef("p_pspr.c:A_BFGSpray")
    public void bfgSpray(Actor mo) {
        Physics physics = world.physics;
        // offset angles from its attack angle
        for (int i = 0; i < 40; i++) {
            int an = mo.angle - Angle.ANG90 / 2 + Angle.ANG90 / 40 * i;

            // mo->target is the originator (player) of the missile
            physics.aimLineAttack(mo.target, an, AIM_RANGE);

            Actor lineTarget = physics.lineTarget;
            if (lineTarget == null) {
                continue;
            }

            world.mobjs.spawnMobj(lineTarget.x, lineTarget.y, lineTarget.z + (lineTarget.height >> 2),
                    MobjType.EXTRABFG);

            int damage = 0;
            for (int j = 0; j < 15; j++) {
                damage += (world.rng.game() & 7) + 1;
            }

            damageMobj(lineTarget, mo.target, mo.target, damage);
        }
    }
}
