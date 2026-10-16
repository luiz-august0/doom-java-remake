package doom.game.player;

import doom.game.actor.Mobjs;
import doom.game.api.TicCmd;
import doom.game.data.MiscSettings;
import doom.game.data.StateId;
import doom.game.view.AmmoType;
import doom.game.view.CheatFlags;
import doom.game.view.MobjFlags;
import doom.game.view.Power;
import doom.game.view.Weapon;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameMode;

/** Player movement and per-tic logic ({@code p_user.c}) plus {@code G_PlayerReborn}. */
public final class PlayerSim {

    /** {@code INVERSECOLORMAP}. */
    public static final int INVERSECOLORMAP = 32;
    /** {@code MAXBOB}: 16 pixels of bob. */
    private static final int MAXBOB = 0x100000;
    private static final int ANG5 = Angle.ANG90 / 18;

    private final World world;
    /** {@code onground}: the player is on the ground (set by the movement code, used by the view bob). */
    private boolean onGround;

    public PlayerSim(World world) {
        this.world = world;
    }

    /** {@code onground}: the static of {@code p_user.c}, which outlives a tic (save games). */
    public boolean onGround() {
        return onGround;
    }

    /** Restores {@link #onGround()} (save games). */
    public void setOnGround(boolean value) {
        onGround = value;
    }

    /** {@code P_Thrust}: moves the player along the given angle. */
    @VanillaRef("p_user.c:P_Thrust")
    public void thrust(Player player, int angle, int move) {
        angle >>>= Angle.ANGLETOFINESHIFT;
        player.mo.momX += Fixed.mul(move, Tables.fineCosine(angle));
        player.mo.momY += Fixed.mul(move, Tables.fineSine(angle));
    }

    /** {@code P_CalcHeight}: calculates the walking / running height adjustment. */
    @VanillaRef("p_user.c:P_CalcHeight")
    public void calcHeight(Player player) {
        // Regular movement bobbing (needs to be calculated for gun swing even if not on ground)
        player.bob = Fixed.mul(player.mo.momX, player.mo.momX) + Fixed.mul(player.mo.momY, player.mo.momY);
        player.bob >>= 2;
        if (player.bob > MAXBOB) {
            player.bob = MAXBOB;
        }

        if ((player.cheats & CheatFlags.NOMOMENTUM) != 0 || !onGround) {
            player.viewZ = player.mo.z + Mobjs.VIEWHEIGHT;
            if (player.viewZ > player.mo.ceilingZ - 4 * Fixed.FRACUNIT) {
                player.viewZ = player.mo.ceilingZ - 4 * Fixed.FRACUNIT;
            }
            player.viewZ = player.mo.z + player.viewHeight;
            return;
        }

        int angle = (Angle.FINEANGLES / 20 * world.levelTime) & Angle.FINEMASK;
        int bob = Fixed.mul(player.bob / 2, Tables.fineSine(angle));

        // move viewheight
        if (player.playerState == Player.PST_LIVE) {
            player.viewHeight += player.deltaViewHeight;

            if (player.viewHeight > Mobjs.VIEWHEIGHT) {
                player.viewHeight = Mobjs.VIEWHEIGHT;
                player.deltaViewHeight = 0;
            }
            if (player.viewHeight < Mobjs.VIEWHEIGHT / 2) {
                player.viewHeight = Mobjs.VIEWHEIGHT / 2;
                if (player.deltaViewHeight <= 0) {
                    player.deltaViewHeight = 1;
                }
            }
            if (player.deltaViewHeight != 0) {
                player.deltaViewHeight += Fixed.FRACUNIT / 4;
                if (player.deltaViewHeight == 0) {
                    player.deltaViewHeight = 1;
                }
            }
        }
        player.viewZ = player.mo.z + player.viewHeight + bob;

        if (player.viewZ > player.mo.ceilingZ - 4 * Fixed.FRACUNIT) {
            player.viewZ = player.mo.ceilingZ - 4 * Fixed.FRACUNIT;
        }
    }

    /** {@code P_MovePlayer}. */
    @VanillaRef("p_user.c:P_MovePlayer")
    public void movePlayer(Player player) {
        TicCmd cmd = player.cmd;

        player.mo.angle += cmd.angleTurn << Fixed.FRACBITS;

        // Do not let the player control movement if not onground.
        onGround = player.mo.z <= player.mo.floorZ;

        if (cmd.forwardMove != 0 && onGround) {
            thrust(player, player.mo.angle, cmd.forwardMove * 2048);
        }
        if (cmd.sideMove != 0 && onGround) {
            thrust(player, player.mo.angle - Angle.ANG90, cmd.sideMove * 2048);
        }
        if ((cmd.forwardMove != 0 || cmd.sideMove != 0) && player.mo.stateNum == StateId.PLAY) {
            world.mobjs.setMobjState(player.mo, StateId.PLAY_RUN1);
        }
    }

    /** {@code P_DeathThink}: fall on your face when dying; decrease POV height to floor height. */
    @VanillaRef("p_user.c:P_DeathThink")
    public void deathThink(Player player) {
        world.weapons.movePsprites(player);

        // fall to the ground
        if (player.viewHeight > 6 * Fixed.FRACUNIT) {
            player.viewHeight -= Fixed.FRACUNIT;
        }
        if (player.viewHeight < 6 * Fixed.FRACUNIT) {
            player.viewHeight = 6 * Fixed.FRACUNIT;
        }

        player.deltaViewHeight = 0;
        onGround = player.mo.z <= player.mo.floorZ;
        calcHeight(player);

        if (player.attacker != null && player.attacker != player.mo) {
            int angle = Trig.pointToAngle2(player.mo.x, player.mo.y, player.attacker.x, player.attacker.y);
            int delta = angle - player.mo.angle;

            if (Angle.lt(delta, ANG5) || Angle.gt(delta, -ANG5)) {
                // Looking at killer, so fade damage flash down.
                player.mo.angle = angle;
                if (player.damageCount != 0) {
                    player.damageCount--;
                }
            } else if (Angle.lt(delta, Angle.ANG180)) {
                player.mo.angle += ANG5;
            } else {
                player.mo.angle -= ANG5;
            }
        } else if (player.damageCount != 0) {
            player.damageCount--;
        }

        if ((player.cmd.buttons & TicCmd.BT_USE) != 0) {
            player.playerState = Player.PST_REBORN;
        }
    }

    /** {@code P_PlayerThink}. */
    @VanillaRef("p_user.c:P_PlayerThink")
    public void playerThink(Player player) {
        // fixme: do this in the cheat code
        if ((player.cheats & CheatFlags.NOCLIP) != 0) {
            player.mo.flags |= MobjFlags.NOCLIP;
        } else {
            player.mo.flags &= ~MobjFlags.NOCLIP;
        }

        // chain saw run forward
        TicCmd cmd = player.cmd;
        if ((player.mo.flags & MobjFlags.JUSTATTACKED) != 0) {
            cmd.angleTurn = 0;
            cmd.forwardMove = (byte) (0xc800 / 512);
            cmd.sideMove = 0;
            player.mo.flags &= ~MobjFlags.JUSTATTACKED;
        }

        if (player.playerState == Player.PST_DEAD) {
            deathThink(player);
            return;
        }

        // Move around. Reactiontime is used to prevent movement for a bit after a teleport.
        if (player.mo.reactionTime != 0) {
            player.mo.reactionTime--;
        } else {
            movePlayer(player);
        }

        calcHeight(player);

        if (player.mo.subsector.sector.special != 0) {
            world.specials.playerInSpecialSector(player);
        }

        // Check for weapon change.

        // A special event has no other buttons.
        if ((cmd.buttons & TicCmd.BT_SPECIAL) != 0) {
            cmd.buttons = 0;
        }

        int buttons = cmd.buttons & 0xFF;
        if ((buttons & TicCmd.BT_CHANGE) != 0) {
            // The actual changing of the weapon is done when the weapon psprite can do it
            // (read: not in the middle of an attack).
            int newweapon = (buttons & TicCmd.BT_WEAPONMASK) >> TicCmd.BT_WEAPONSHIFT;

            if (newweapon == Weapon.FIST && player.weaponOwned[Weapon.CHAINSAW]
                    && !(player.readyWeapon == Weapon.CHAINSAW && player.powers[Power.STRENGTH] != 0)) {
                newweapon = Weapon.CHAINSAW;
            }

            if (world.mode == GameMode.COMMERCIAL && newweapon == Weapon.SHOTGUN
                    && player.weaponOwned[Weapon.SUPERSHOTGUN] && player.readyWeapon != Weapon.SUPERSHOTGUN) {
                newweapon = Weapon.SUPERSHOTGUN;
            }

            if (player.weaponOwned[newweapon] && newweapon != player.readyWeapon) {
                // Do not go to plasma or BFG in shareware, even if cheated.
                if ((newweapon != Weapon.PLASMA && newweapon != Weapon.BFG) || world.mode != GameMode.SHAREWARE) {
                    player.pendingWeapon = newweapon;
                }
            }
        }

        // check for use
        if ((cmd.buttons & TicCmd.BT_USE) != 0) {
            if (!player.useDown) {
                world.physics.useLines(player);
                player.useDown = true;
            }
        } else {
            player.useDown = false;
        }

        // cycle psprites
        world.weapons.movePsprites(player);

        // Counters, time dependend power ups.

        // Strength counts up to diminish fade.
        if (player.powers[Power.STRENGTH] != 0) {
            player.powers[Power.STRENGTH]++;
        }
        if (player.powers[Power.INVULNERABILITY] != 0) {
            player.powers[Power.INVULNERABILITY]--;
        }
        if (player.powers[Power.INVISIBILITY] != 0) {
            if (--player.powers[Power.INVISIBILITY] == 0) {
                player.mo.flags &= ~MobjFlags.SHADOW;
            }
        }
        if (player.powers[Power.INFRARED] != 0) {
            player.powers[Power.INFRARED]--;
        }
        if (player.powers[Power.IRONFEET] != 0) {
            player.powers[Power.IRONFEET]--;
        }
        if (player.damageCount != 0) {
            player.damageCount--;
        }
        if (player.bonusCount != 0) {
            player.bonusCount--;
        }

        // Handling colormaps.
        if (player.powers[Power.INVULNERABILITY] != 0) {
            if (player.powers[Power.INVULNERABILITY] > 4 * 32 || (player.powers[Power.INVULNERABILITY] & 8) != 0) {
                player.fixedColormap = INVERSECOLORMAP;
            } else {
                player.fixedColormap = 0;
            }
        } else if (player.powers[Power.INFRARED] != 0) {
            if (player.powers[Power.INFRARED] > 4 * 32 || (player.powers[Power.INFRARED] & 8) != 0) {
                // almost full bright
                player.fixedColormap = 1;
            } else {
                player.fixedColormap = 0;
            }
        } else {
            player.fixedColormap = 0;
        }
    }

    /** {@code G_PlayerReborn}: called after a player dies; almost everything is cleared and initialized. */
    @VanillaRef("g_game.c:G_PlayerReborn")
    public void playerReborn(int playerNum) {
        Player p = world.players[playerNum];
        int[] frags = p.frags.clone();
        int killcount = p.killCount;
        int itemcount = p.itemCount;
        int secretcount = p.secretCount;

        p.clearAll();

        System.arraycopy(frags, 0, p.frags, 0, frags.length);
        p.killCount = killcount;
        p.itemCount = itemcount;
        p.secretCount = secretcount;

        MiscSettings misc = world.defs.misc();
        p.useDown = p.attackDown = true; // don't do anything immediately
        p.playerState = Player.PST_LIVE;
        p.health = misc.initialHealth(); // Use dehacked value
        p.readyWeapon = p.pendingWeapon = Weapon.PISTOL;
        p.weaponOwned[Weapon.FIST] = true;
        p.weaponOwned[Weapon.PISTOL] = true;
        p.ammo[AmmoType.CLIP] = misc.initialBullets();

        int[] maxAmmo = world.defs.maxAmmo();
        for (int i = 0; i < AmmoType.COUNT; i++) {
            p.maxAmmo[i] = maxAmmo[i];
        }
    }
}
