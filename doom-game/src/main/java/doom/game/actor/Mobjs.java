package doom.game.actor;

import doom.game.data.MobjType;
import doom.game.data.State;
import doom.game.data.StateId;
import doom.game.data.ThingType;
import doom.game.physics.Physics;
import doom.game.player.Player;
import doom.game.view.CheatFlags;
import doom.game.view.Card;
import doom.game.view.MobjFlags;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.SpawnPoint;
import doom.game.world.Subsector;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameVersion;

/**
 * Map objects: spawning, removal, the state machine and {@code P_MobjThinker} with its XY and Z movement
 * ({@code p_mobj.c}, {@code p_tick.c}).
 */
public final class Mobjs {

    /** {@code ONFLOORZ}. */
    public static final int ONFLOORZ = Integer.MIN_VALUE;
    /** {@code ONCEILINGZ}. */
    public static final int ONCEILINGZ = Integer.MAX_VALUE;
    /** {@code GRAVITY}. */
    public static final int GRAVITY = Fixed.FRACUNIT;
    /** {@code MAXMOVE}. */
    public static final int MAXMOVE = 30 * Fixed.FRACUNIT;
    /** {@code FLOATSPEED}. */
    public static final int FLOATSPEED = 4 * Fixed.FRACUNIT;
    /** {@code VIEWHEIGHT}. */
    public static final int VIEWHEIGHT = 41 * Fixed.FRACUNIT;
    private static final int STOPSPEED = 0x1000;
    private static final int FRICTION = 0xe800;
    private static final int MOBJ_CYCLE_LIMIT = 1000000;

    private final World world;
    /** {@code dummy_mobj} of {@code P_SubstNullMobj}. */
    private Actor dummyMobj;

    public Mobjs(World world) {
        this.world = world;
    }

    // ------------------------------------------------------------------------------------------ state machine

    /**
     * {@code P_SetMobjState}: enters {@code state}, running its action, and chains through zero-tic states.
     *
     * @return false if the actor was removed ({@code S_NULL})
     */
    @VanillaRef("p_mobj.c:P_SetMobjState")
    public boolean setMobjState(Actor mobj, int state) {
        State[] states = world.defs.states();
        int cycleCounter = 0;
        do {
            if (state == StateId.NULL) {
                mobj.state = null;
                mobj.stateNum = StateId.NULL;
                removeMobj(mobj);
                return false;
            }
            State st = states[state];
            mobj.state = st;
            mobj.stateNum = state;
            mobj.tics = st.tics;
            mobj.sprite = st.sprite;
            mobj.frame = st.frame;

            // Modified handling: call action functions when the state is set
            world.actions.mobjAction(st.action, mobj);

            state = st.nextState;

            if (cycleCounter++ > MOBJ_CYCLE_LIMIT) {
                throw new DoomFatalException("P_SetMobjState: Infinite state cycle detected!");
            }
        } while (mobj.tics == 0);
        return true;
    }

    // ------------------------------------------------------------------------------------------ thinker

    /** {@code P_MobjThinker}. */
    @VanillaRef("p_mobj.c:P_MobjThinker")
    public void mobjThinker(Actor mobj) {
        // momentum movement
        if (mobj.momX != 0 || mobj.momY != 0 || (mobj.flags & MobjFlags.SKULLFLY) != 0) {
            xyMovement(mobj);
            if (mobj.isRemoved()) {
                return; // mobj was removed
            }
        }
        if (mobj.z != mobj.floorZ || mobj.momZ != 0) {
            zMovement(mobj);
            if (mobj.isRemoved()) {
                return; // mobj was removed
            }
        }

        // cycle through states, calling action functions at transitions
        if (mobj.tics != -1) {
            mobj.tics--;
            // you can cycle through multiple states in a tic
            if (mobj.tics == 0) {
                if (!setMobjState(mobj, mobj.state.nextState)) {
                    return; // freed itself
                }
            }
        } else {
            // check for nightmare respawn
            if ((mobj.flags & MobjFlags.COUNTKILL) == 0) {
                return;
            }
            if (!world.respawnMonsters) {
                return;
            }
            mobj.moveCount++;
            if (mobj.moveCount < 12 * World.TICRATE) {
                return;
            }
            if ((world.levelTime & 31) != 0) {
                return;
            }
            if (world.rng.game() > 4) {
                return;
            }
            world.ai.nightmareRespawn(mobj);
        }
    }

    /** {@code P_XYMovement}. */
    @VanillaRef("p_mobj.c:P_XYMovement")
    public void xyMovement(Actor mo) {
        Physics p = world.physics;
        if (mo.momX == 0 && mo.momY == 0) {
            if ((mo.flags & MobjFlags.SKULLFLY) != 0) {
                // the skull slammed into something
                mo.flags &= ~MobjFlags.SKULLFLY;
                mo.momX = mo.momY = mo.momZ = 0;
                setMobjState(mo, mo.info.spawnState);
            }
            return;
        }

        Player player = mo.player;

        if (mo.momX > MAXMOVE) {
            mo.momX = MAXMOVE;
        } else if (mo.momX < -MAXMOVE) {
            mo.momX = -MAXMOVE;
        }
        if (mo.momY > MAXMOVE) {
            mo.momY = MAXMOVE;
        } else if (mo.momY < -MAXMOVE) {
            mo.momY = -MAXMOVE;
        }

        int xmove = mo.momX;
        int ymove = mo.momY;
        do {
            int ptryx;
            int ptryy;
            if (xmove > MAXMOVE / 2 || ymove > MAXMOVE / 2) {
                ptryx = mo.x + xmove / 2;
                ptryy = mo.y + ymove / 2;
                xmove >>= 1;
                ymove >>= 1;
            } else {
                ptryx = mo.x + xmove;
                ptryy = mo.y + ymove;
                xmove = ymove = 0;
            }

            if (!p.tryMove(mo, ptryx, ptryy)) {
                // blocked move
                if (mo.player != null) {
                    // try to slide along it
                    p.slideMove(mo);
                } else if ((mo.flags & MobjFlags.MISSILE) != 0) {
                    // explode a missile
                    Line ceilingLine = p.ceilingLine;
                    if (ceilingLine != null && ceilingLine.backSector != null
                            && ceilingLine.backSector.ceilingPic == world.skyFlatNum) {
                        // Hack to prevent missiles exploding against the sky. Does not handle sky floors.
                        removeMobj(mo);
                        return;
                    }
                    world.combat.explodeMissile(mo);
                } else {
                    mo.momX = mo.momY = 0;
                }
            }
        } while (xmove != 0 || ymove != 0);

        // slow down
        if (player != null && (player.cheats & CheatFlags.NOMOMENTUM) != 0) {
            // debug option for no sliding at all
            mo.momX = mo.momY = 0;
            return;
        }

        if ((mo.flags & (MobjFlags.MISSILE | MobjFlags.SKULLFLY)) != 0) {
            return; // no friction for missiles ever
        }
        if (mo.z > mo.floorZ) {
            return; // no friction when airborne
        }
        if ((mo.flags & MobjFlags.CORPSE) != 0) {
            // do not stop sliding if halfway off a step with some momentum
            if (mo.momX > Fixed.FRACUNIT / 4 || mo.momX < -Fixed.FRACUNIT / 4
                    || mo.momY > Fixed.FRACUNIT / 4 || mo.momY < -Fixed.FRACUNIT / 4) {
                if (mo.floorZ != mo.subsector.sector.floorHeight) {
                    return;
                }
            }
        }

        if (mo.momX > -STOPSPEED && mo.momX < STOPSPEED && mo.momY > -STOPSPEED && mo.momY < STOPSPEED
                && (player == null || (player.cmd.forwardMove == 0 && player.cmd.sideMove == 0))) {
            // if in a walking frame, stop moving
            if (player != null && Integer.compareUnsigned(player.mo.stateNum - StateId.PLAY_RUN1, 4) < 0) {
                setMobjState(player.mo, StateId.PLAY);
            }
            mo.momX = 0;
            mo.momY = 0;
        } else {
            mo.momX = Fixed.mul(mo.momX, FRICTION);
            mo.momY = Fixed.mul(mo.momY, FRICTION);
        }
    }

    /** {@code P_ZMovement}. */
    @VanillaRef("p_mobj.c:P_ZMovement")
    public void zMovement(Actor mo) {
        // check for smooth step up
        if (mo.player != null && mo.z < mo.floorZ) {
            mo.player.viewHeight -= mo.floorZ - mo.z;
            mo.player.deltaViewHeight = (VIEWHEIGHT - mo.player.viewHeight) >> 3;
        }

        // adjust height
        mo.z += mo.momZ;

        if ((mo.flags & MobjFlags.FLOAT) != 0 && mo.target != null) {
            // float down towards target if too close
            if ((mo.flags & MobjFlags.SKULLFLY) == 0 && (mo.flags & MobjFlags.INFLOAT) == 0) {
                int dist = Trig.approxDistance(mo.x - mo.target.x, mo.y - mo.target.y);
                int delta = (mo.target.z + (mo.height >> 1)) - mo.z;
                if (delta < 0 && dist < -(delta * 3)) {
                    mo.z -= FLOATSPEED;
                } else if (delta > 0 && dist < (delta * 3)) {
                    mo.z += FLOATSPEED;
                }
            }
        }

        // clip movement
        if (mo.z <= mo.floorZ) {
            // hit the floor
            boolean correctLostSoulBounce = world.version.isAtLeast(GameVersion.ULTIMATE);

            if (correctLostSoulBounce && (mo.flags & MobjFlags.SKULLFLY) != 0) {
                // the skull slammed into something
                mo.momZ = -mo.momZ;
            }

            if (mo.momZ < 0) {
                if (mo.player != null && mo.momZ < -GRAVITY * 8) {
                    // Squat down. Decrease viewheight for a moment after hitting the ground (hard),
                    // and utter appropriate sound.
                    mo.player.deltaViewHeight = mo.momZ >> 3;
                    world.startSound(mo, SoundId.OOF);
                }
                mo.momZ = 0;
            }
            mo.z = mo.floorZ;

            // VANILLA-BUG: Doom 1.9 / Doom II reverse the momentum after zeroing it (lost soul bounce).
            if (!correctLostSoulBounce && (mo.flags & MobjFlags.SKULLFLY) != 0) {
                mo.momZ = -mo.momZ;
            }

            if ((mo.flags & MobjFlags.MISSILE) != 0 && (mo.flags & MobjFlags.NOCLIP) == 0) {
                world.combat.explodeMissile(mo);
                return;
            }
        } else if ((mo.flags & MobjFlags.NOGRAVITY) == 0) {
            if (mo.momZ == 0) {
                mo.momZ = -GRAVITY * 2;
            } else {
                mo.momZ -= GRAVITY;
            }
        }

        if (mo.z + mo.height > mo.ceilingZ) {
            // hit the ceiling
            if (mo.momZ > 0) {
                mo.momZ = 0;
            }
            mo.z = mo.ceilingZ - mo.height;

            if ((mo.flags & MobjFlags.SKULLFLY) != 0) {
                // the skull slammed into something
                mo.momZ = -mo.momZ;
            }

            if ((mo.flags & MobjFlags.MISSILE) != 0 && (mo.flags & MobjFlags.NOCLIP) == 0) {
                world.combat.explodeMissile(mo);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ spawn / remove

    /** {@code P_SpawnMobj}. */
    @VanillaRef("p_mobj.c:P_SpawnMobj")
    public Actor spawnMobj(int x, int y, int z, int type) {
        Actor mobj = new Actor(this);
        ThingType info = world.defs.things()[type];

        mobj.type = type;
        mobj.info = info;
        mobj.x = x;
        mobj.y = y;
        mobj.radius = info.radius;
        mobj.height = info.height;
        mobj.flags = info.flags;
        mobj.health = info.spawnHealth;

        if (world.gameSkill != 4) { // sk_nightmare
            mobj.reactionTime = info.reactionTime;
        }

        mobj.lastLook = world.rng.game() % World.MAXPLAYERS;
        // do not set the state with P_SetMobjState, because action routines can not be called yet
        State st = world.defs.states()[info.spawnState];

        mobj.state = st;
        mobj.stateNum = info.spawnState;
        mobj.tics = st.tics;
        mobj.sprite = st.sprite;
        mobj.frame = st.frame;

        // set subsector and/or block links
        world.physics.setThingPosition(mobj);

        mobj.floorZ = mobj.subsector.sector.floorHeight;
        mobj.ceilingZ = mobj.subsector.sector.ceilingHeight;

        if (z == ONFLOORZ) {
            mobj.z = mobj.floorZ;
        } else if (z == ONCEILINGZ) {
            mobj.z = mobj.ceilingZ - mobj.info.height;
        } else {
            mobj.z = z;
        }

        world.thinkers.add(mobj);
        world.actorIds.register(mobj);
        mobj.snapInterpolation();
        return mobj;
    }

    /** {@code P_RemoveMobj}. */
    @VanillaRef("p_mobj.c:P_RemoveMobj")
    public void removeMobj(Actor mobj) {
        if ((mobj.flags & MobjFlags.SPECIAL) != 0
                && (mobj.flags & MobjFlags.DROPPED) == 0
                && mobj.type != MobjType.INV
                && mobj.type != MobjType.INS) {
            world.itemRespawnQue[world.iqueHead].set(mobj.spawnPoint);
            world.itemRespawnTime[world.iqueHead] = world.levelTime;
            world.iqueHead = (world.iqueHead + 1) & (World.ITEMQUESIZE - 1);

            // lose one off the end?
            if (world.iqueHead == world.iqueTail) {
                world.iqueTail = (world.iqueTail + 1) & (World.ITEMQUESIZE - 1);
            }
        }

        // unlink from sector and block lists
        world.physics.unsetThingPosition(mobj);

        // stop any playing sound
        if (mobj.id >= 0) {
            world.events.stopSound(mobj.id);
            world.actorIds.release(mobj);
        }

        // free block
        world.thinkers.remove(mobj);
    }

    /** {@code P_RespawnSpecials}: item respawn in altdeath. */
    @VanillaRef("p_mobj.c:P_RespawnSpecials")
    public void respawnSpecials() {
        // only respawn items in deathmatch
        if (world.deathmatch != 2) {
            return;
        }
        // nothing left to respawn?
        if (world.iqueHead == world.iqueTail) {
            return;
        }
        // wait at least 30 seconds
        if (world.levelTime - world.itemRespawnTime[world.iqueTail] < 30 * World.TICRATE) {
            return;
        }

        SpawnPoint mthing = world.itemRespawnQue[world.iqueTail];

        int x = mthing.x << Fixed.FRACBITS;
        int y = mthing.y << Fixed.FRACBITS;

        // spawn a teleport fog at the new spot
        Subsector ss = world.physics.pointInSubsector(x, y);
        Actor mo = spawnMobj(x, y, ss.sector.floorHeight, MobjType.IFOG);
        world.startSound(mo, SoundId.ITMBK);

        // find which type to spawn
        ThingType[] things = world.defs.things();
        int i;
        for (i = 0; i < MobjType.COUNT; i++) {
            if (mthing.type == things[i].doomedNum) {
                break;
            }
        }
        if (i >= MobjType.COUNT) {
            throw new DoomFatalException("P_RespawnSpecials: Failed to find mobj type with doomednum "
                    + mthing.type + " when respawning thing. This would cause a buffer overrun in vanilla Doom");
        }

        // spawn it
        int z = (things[i].flags & MobjFlags.SPAWNCEILING) != 0 ? ONCEILINGZ : ONFLOORZ;

        mo = spawnMobj(x, y, z, i);
        mo.spawnPoint.set(mthing);
        mo.angle = Angle.ANG45 * (mthing.angle / 45);

        // pull it from the que
        world.iqueTail = (world.iqueTail + 1) & (World.ITEMQUESIZE - 1);
    }

    /** {@code P_SpawnPlayer}: called when a player is spawned on the level. */
    @VanillaRef("p_mobj.c:P_SpawnPlayer")
    public void spawnPlayer(SpawnPoint mthing) {
        if (mthing.type == 0) {
            return;
        }
        // not playing?
        if (!world.playerInGame[mthing.type - 1]) {
            return;
        }

        Player p = world.players[mthing.type - 1];

        if (p.playerState == Player.PST_REBORN) {
            world.playerSim.playerReborn(mthing.type - 1);
        }

        int x = mthing.x << Fixed.FRACBITS;
        int y = mthing.y << Fixed.FRACBITS;
        Actor mobj = spawnMobj(x, y, ONFLOORZ, MobjType.PLAYER);

        // set color translations for player sprites
        if (mthing.type > 1) {
            mobj.flags |= (mthing.type - 1) << MobjFlags.TRANSSHIFT;
        }

        mobj.angle = Angle.ANG45 * (mthing.angle / 45);
        mobj.player = p;
        mobj.health = p.health;
        mobj.snapInterpolation();

        p.mo = mobj;
        p.playerState = Player.PST_LIVE;
        p.refire = 0;
        p.message = null;
        p.damageCount = 0;
        p.bonusCount = 0;
        p.extraLight = 0;
        p.fixedColormap = 0;
        p.viewHeight = VIEWHEIGHT;

        // setup gun psprite
        world.weapons.setupPsprites(p);

        // give all cards in death match mode
        if (world.deathmatch != 0) {
            for (int i = 0; i < Card.COUNT; i++) {
                p.cards[i] = true;
            }
        }
        // ST_Start / HU_Start for the console player: the presentation reacts to levelLoaded().
    }

    /** {@code P_SpawnMapThing}. */
    @VanillaRef("p_mobj.c:P_SpawnMapThing")
    public void spawnMapThing(SpawnPoint mthing) {
        // count deathmatch start positions
        if (mthing.type == 11) {
            if (world.deathmatchCount < World.MAX_DM_STARTS) {
                world.deathmatchStarts[world.deathmatchCount].set(mthing);
                world.deathmatchCount++;
            }
            return;
        }

        if (mthing.type <= 0) {
            // Thing type 0 is actually "player -1 start". For some reason, Vanilla Doom accepts/ignores this.
            return;
        }

        // check for players specially
        if (mthing.type <= 4) {
            // save spots for respawning in network games
            world.playerStarts[mthing.type - 1].set(mthing);
            world.playerStartsInGame[mthing.type - 1] = true;
            if (world.deathmatch == 0) {
                spawnPlayer(mthing);
            }
            return;
        }

        // check for appropriate skill level
        if (!world.netGame && (mthing.options & 16) != 0) {
            return;
        }

        int bit;
        if (world.gameSkill == 0) { // sk_baby
            bit = 1;
        } else if (world.gameSkill == 4) { // sk_nightmare
            bit = 4;
        } else {
            // emulate what doom.exe did: reduce the shift count mod 32
            bit = 1 << ((world.gameSkill - 1) & 0x1F);
        }
        if ((mthing.options & bit) == 0) {
            return;
        }

        // find which type to spawn
        ThingType[] things = world.defs.things();
        int i;
        for (i = 0; i < MobjType.COUNT; i++) {
            if (mthing.type == things[i].doomedNum) {
                break;
            }
        }
        if (i == MobjType.COUNT) {
            throw new DoomFatalException("P_SpawnMapThing: Unknown type " + mthing.type + " at (" + mthing.x + ", "
                    + mthing.y + ")");
        }

        // don't spawn keycards and players in deathmatch
        if (world.deathmatch != 0 && (things[i].flags & MobjFlags.NOTDMATCH) != 0) {
            return;
        }

        // don't spawn any monsters if -nomonsters
        if (world.noMonsters && (i == MobjType.SKULL || (things[i].flags & MobjFlags.COUNTKILL) != 0)) {
            return;
        }

        // spawn it
        int x = mthing.x << Fixed.FRACBITS;
        int y = mthing.y << Fixed.FRACBITS;
        int z = (things[i].flags & MobjFlags.SPAWNCEILING) != 0 ? ONCEILINGZ : ONFLOORZ;

        Actor mobj = spawnMobj(x, y, z, i);
        mobj.spawnPoint.set(mthing);

        if (mobj.tics > 0) {
            mobj.tics = 1 + (world.rng.game() % mobj.tics);
        }
        if ((mobj.flags & MobjFlags.COUNTKILL) != 0) {
            world.totalKills++;
        }
        if ((mobj.flags & MobjFlags.COUNTITEM) != 0) {
            world.totalItems++;
        }

        mobj.angle = Angle.ANG45 * (mthing.angle / 45);
        if ((mthing.options & 8) != 0) { // MTF_AMBUSH
            mobj.flags |= MobjFlags.AMBUSH;
        }
        mobj.snapInterpolation();
    }

    /**
     * {@code P_SubstNullMobj}: Chocolate Doom's stand-in for code that dereferences a null map object (vanilla reads
     * whatever is at address 0). Like Chocolate's {@code static dummy_mobj}, one dummy per session is reused; only its
     * position and flags are reset.
     */
    @VanillaRef("p_mobj.c:P_SubstNullMobj")
    public Actor substNullMobj(Actor mobj) {
        if (mobj != null) {
            return mobj;
        }
        if (dummyMobj == null) {
            dummyMobj = new Actor(this);
        }
        Actor dummy = dummyMobj;
        dummy.x = 0;
        dummy.y = 0;
        dummy.z = 0;
        dummy.flags = 0;
        return dummy;
    }
}
