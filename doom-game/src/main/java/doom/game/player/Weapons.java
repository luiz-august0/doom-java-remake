package doom.game.player;

import doom.game.api.TicCmd;
import doom.game.data.State;
import doom.game.data.StateId;
import doom.game.data.WeaponInfo;
import doom.game.view.AmmoType;
import doom.game.view.SoundId;
import doom.game.view.Weapon;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMode;

/**
 * The weapon sprite state machine and the non-offensive weapon actions ({@code p_pspr.c}). Firing actions belong to
 * {@link doom.game.combat.Combat}.
 */
public final class Weapons {

    public static final int LOWERSPEED = Fixed.FRACUNIT * 6;
    public static final int RAISESPEED = Fixed.FRACUNIT * 6;
    public static final int WEAPONBOTTOM = 128 * Fixed.FRACUNIT;
    public static final int WEAPONTOP = 32 * Fixed.FRACUNIT;

    private final World world;

    public Weapons(World world) {
        this.world = world;
    }

    private WeaponInfo info(int weapon) {
        return world.defs.weapons()[weapon];
    }

    /** {@code P_SetPsprite}: enters a state, running its action, chaining through zero-tic states. */
    @VanillaRef("p_pspr.c:P_SetPsprite")
    public void setPsprite(Player player, int position, int stnum) {
        PSprite psp = player.psprites[position];
        State[] states = world.defs.states();
        do {
            if (stnum == 0) {
                // object removed itself
                psp.state = null;
                psp.stateNum = -1;
                break;
            }
            State state = states[stnum];
            psp.state = state;
            psp.stateNum = stnum;
            psp.tics = state.tics; // could be 0

            if (state.misc1 != 0) {
                // coordinate set
                psp.sx = state.misc1 << Fixed.FRACBITS;
                psp.sy = state.misc2 << Fixed.FRACBITS;
            }

            // Call action routine. Modified handling.
            if (state.action != doom.game.data.StateAction.NONE) {
                world.actions.pspAction(state.action, player, psp);
                if (psp.state == null) {
                    break;
                }
            }
            stnum = psp.state.nextState;
        } while (psp.tics == 0);
        // an initial state of 0 could cycle through
    }

    /** {@code P_BringUpWeapon}: starts bringing the pending weapon up from the bottom of the screen. */
    @VanillaRef("p_pspr.c:P_BringUpWeapon")
    public void bringUpWeapon(Player player) {
        if (player.pendingWeapon == Weapon.NO_CHANGE) {
            player.pendingWeapon = player.readyWeapon;
        }
        if (player.pendingWeapon == Weapon.CHAINSAW) {
            world.startSound(player.mo, SoundId.SAWUP);
        }
        int newstate = info(player.pendingWeapon).upState;

        player.pendingWeapon = Weapon.NO_CHANGE;
        player.psprites[Player.PS_WEAPON].sy = WEAPONBOTTOM;

        setPsprite(player, Player.PS_WEAPON, newstate);
    }

    /**
     * {@code P_CheckAmmo}: returns true if there is enough ammo to shoot; if not, selects the next weapon to use.
     */
    @VanillaRef("p_pspr.c:P_CheckAmmo")
    public boolean checkAmmo(Player player) {
        int ammo = info(player.readyWeapon).ammo;

        // Minimal amount for one shot varies.
        int count;
        if (player.readyWeapon == Weapon.BFG) {
            count = world.defs.misc().bfgCellsPerShot();
        } else if (player.readyWeapon == Weapon.SUPERSHOTGUN) {
            count = 2; // Double barrel.
        } else {
            count = 1; // Regular.
        }

        // Some do not need ammunition anyway. Return if current ammunition sufficient.
        if (ammo == AmmoType.NO_AMMO || player.ammo[ammo] >= count) {
            return true;
        }

        // Out of ammo, pick a weapon to change to. Preferences are set here.
        do {
            if (player.weaponOwned[Weapon.PLASMA] && player.ammo[AmmoType.CELL] != 0
                    && world.mode != GameMode.SHAREWARE) {
                player.pendingWeapon = Weapon.PLASMA;
            } else if (player.weaponOwned[Weapon.SUPERSHOTGUN] && player.ammo[AmmoType.SHELL] > 2
                    && world.mode == GameMode.COMMERCIAL) {
                player.pendingWeapon = Weapon.SUPERSHOTGUN;
            } else if (player.weaponOwned[Weapon.CHAINGUN] && player.ammo[AmmoType.CLIP] != 0) {
                player.pendingWeapon = Weapon.CHAINGUN;
            } else if (player.weaponOwned[Weapon.SHOTGUN] && player.ammo[AmmoType.SHELL] != 0) {
                player.pendingWeapon = Weapon.SHOTGUN;
            } else if (player.ammo[AmmoType.CLIP] != 0) {
                player.pendingWeapon = Weapon.PISTOL;
            } else if (player.weaponOwned[Weapon.CHAINSAW]) {
                player.pendingWeapon = Weapon.CHAINSAW;
            } else if (player.weaponOwned[Weapon.MISSILE] && player.ammo[AmmoType.MISSILE] != 0) {
                player.pendingWeapon = Weapon.MISSILE;
            } else if (player.weaponOwned[Weapon.BFG] && player.ammo[AmmoType.CELL] > 40
                    && world.mode != GameMode.SHAREWARE) {
                player.pendingWeapon = Weapon.BFG;
            } else {
                // If everything fails.
                player.pendingWeapon = Weapon.FIST;
            }
        } while (player.pendingWeapon == Weapon.NO_CHANGE);

        // Now set appropriate weapon overlay.
        setPsprite(player, Player.PS_WEAPON, info(player.readyWeapon).downState);
        return false;
    }

    /** {@code P_FireWeapon}. */
    @VanillaRef("p_pspr.c:P_FireWeapon")
    public void fireWeapon(Player player) {
        if (!checkAmmo(player)) {
            return;
        }
        world.mobjs.setMobjState(player.mo, StateId.PLAY_ATK1);
        int newstate = info(player.readyWeapon).atkState;
        setPsprite(player, Player.PS_WEAPON, newstate);
        world.ai.noiseAlert(player.mo, player.mo);
    }

    /** {@code P_DropWeapon}: the player died, so put the weapon away. */
    @VanillaRef("p_pspr.c:P_DropWeapon")
    public void dropWeapon(Player player) {
        setPsprite(player, Player.PS_WEAPON, info(player.readyWeapon).downState);
    }

    /** {@code A_WeaponReady}: the player can fire the weapon or change to another weapon at this time. */
    @VanillaRef("p_pspr.c:A_WeaponReady")
    public void weaponReady(Player player, PSprite psp) {
        // get out of attack state
        if (player.mo.stateNum == StateId.PLAY_ATK1 || player.mo.stateNum == StateId.PLAY_ATK2) {
            world.mobjs.setMobjState(player.mo, StateId.PLAY);
        }

        if (player.readyWeapon == Weapon.CHAINSAW && psp.stateNum == StateId.SAW) {
            world.startSound(player.mo, SoundId.SAWIDL);
        }

        // check for change; if player is dead, put the weapon away
        if (player.pendingWeapon != Weapon.NO_CHANGE || player.health == 0) {
            // change weapon (pending weapon should allready be validated)
            int newstate = info(player.readyWeapon).downState;
            setPsprite(player, Player.PS_WEAPON, newstate);
            return;
        }

        // check for fire; the missile launcher and bfg do not auto fire
        if ((player.cmd.buttons & TicCmd.BT_ATTACK) != 0) {
            if (!player.attackDown
                    || (player.readyWeapon != Weapon.MISSILE && player.readyWeapon != Weapon.BFG)) {
                player.attackDown = true;
                fireWeapon(player);
                return;
            }
        } else {
            player.attackDown = false;
        }

        // bob the weapon based on movement speed
        int angle = (128 * world.levelTime) & Angle.FINEMASK;
        psp.sx = Fixed.FRACUNIT + Fixed.mul(player.bob, Tables.fineCosine(angle));
        angle &= Angle.FINEANGLES / 2 - 1;
        psp.sy = WEAPONTOP + Fixed.mul(player.bob, Tables.fineSine(angle));
    }

    /** {@code A_ReFire}: the player can re-fire the weapon without lowering it entirely. */
    @VanillaRef("p_pspr.c:A_ReFire")
    public void reFire(Player player, PSprite psp) {
        // check for fire (if a weaponchange is pending, let it go through instead)
        if ((player.cmd.buttons & TicCmd.BT_ATTACK) != 0 && player.pendingWeapon == Weapon.NO_CHANGE
                && player.health != 0) {
            player.refire++;
            fireWeapon(player);
        } else {
            player.refire = 0;
            checkAmmo(player);
        }
    }

    /** {@code A_CheckReload}. */
    @VanillaRef("p_pspr.c:A_CheckReload")
    public void checkReload(Player player, PSprite psp) {
        checkAmmo(player);
    }

    /** {@code A_Lower}: lowers the current weapon, and changes weapon at bottom. */
    @VanillaRef("p_pspr.c:A_Lower")
    public void lower(Player player, PSprite psp) {
        psp.sy += LOWERSPEED;

        // Is already down.
        if (psp.sy < WEAPONBOTTOM) {
            return;
        }

        // Player is dead.
        if (player.playerState == Player.PST_DEAD) {
            psp.sy = WEAPONBOTTOM;
            // don't bring weapon back up
            return;
        }

        // The old weapon has been lowered off the screen, so change the weapon and start raising it
        if (player.health == 0) {
            // Player is dead, so keep the weapon off screen.
            setPsprite(player, Player.PS_WEAPON, StateId.NULL);
            return;
        }

        player.readyWeapon = player.pendingWeapon;
        bringUpWeapon(player);
    }

    /** {@code A_Raise}. */
    @VanillaRef("p_pspr.c:A_Raise")
    public void raise(Player player, PSprite psp) {
        psp.sy -= RAISESPEED;
        if (psp.sy > WEAPONTOP) {
            return;
        }
        psp.sy = WEAPONTOP;

        // The weapon has been raised all the way, so change to the ready state.
        int newstate = info(player.readyWeapon).readyState;
        setPsprite(player, Player.PS_WEAPON, newstate);
    }

    /** {@code A_GunFlash}. */
    @VanillaRef("p_pspr.c:A_GunFlash")
    public void gunFlash(Player player, PSprite psp) {
        world.mobjs.setMobjState(player.mo, StateId.PLAY_ATK2);
        setPsprite(player, Player.PS_FLASH, info(player.readyWeapon).flashState);
    }

    /** {@code A_Light0} / {@code A_Light1} / {@code A_Light2}. */
    @VanillaRef({"p_pspr.c:A_Light0", "p_pspr.c:A_Light1", "p_pspr.c:A_Light2"})
    public void light(Player player, int level) {
        player.extraLight = level;
    }

    /** {@code A_OpenShotgun2} (in {@code p_enemy.c}). */
    @VanillaRef("p_enemy.c:A_OpenShotgun2")
    public void openShotgun2(Player player, PSprite psp) {
        world.startSound(player.mo, SoundId.DBOPN);
    }

    /** {@code A_LoadShotgun2} (in {@code p_enemy.c}). */
    @VanillaRef("p_enemy.c:A_LoadShotgun2")
    public void loadShotgun2(Player player, PSprite psp) {
        world.startSound(player.mo, SoundId.DBLOAD);
    }

    /** {@code A_CloseShotgun2} (in {@code p_enemy.c}). */
    @VanillaRef("p_enemy.c:A_CloseShotgun2")
    public void closeShotgun2(Player player, PSprite psp) {
        world.startSound(player.mo, SoundId.DBCLS);
        reFire(player, psp);
    }

    /** {@code A_BFGsound}. */
    @VanillaRef("p_pspr.c:A_BFGsound")
    public void bfgSound(Player player, PSprite psp) {
        world.startSound(player.mo, SoundId.BFG);
    }

    /** {@code P_SetupPsprites}: called at start of level for each player. */
    @VanillaRef("p_pspr.c:P_SetupPsprites")
    public void setupPsprites(Player player) {
        // remove all psprites
        for (int i = 0; i < Player.NUMPSPRITES; i++) {
            player.psprites[i].state = null;
            player.psprites[i].stateNum = -1;
        }
        // spawn the gun
        player.pendingWeapon = player.readyWeapon;
        bringUpWeapon(player);
    }

    /** {@code P_MovePsprites}: called every tic by player thinking routine. */
    @VanillaRef("p_pspr.c:P_MovePsprites")
    public void movePsprites(Player player) {
        for (int i = 0; i < Player.NUMPSPRITES; i++) {
            PSprite psp = player.psprites[i];
            // a null state means not active
            if (psp.state != null) {
                // drop tic count and possibly change state; a -1 tic count never changes
                if (psp.tics != -1) {
                    psp.tics--;
                    if (psp.tics == 0) {
                        setPsprite(player, i, psp.state.nextState);
                    }
                }
            }
        }
        player.psprites[Player.PS_FLASH].sx = player.psprites[Player.PS_WEAPON].sx;
        player.psprites[Player.PS_FLASH].sy = player.psprites[Player.PS_WEAPON].sy;
    }
}
