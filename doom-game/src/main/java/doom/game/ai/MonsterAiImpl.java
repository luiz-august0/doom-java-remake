package doom.game.ai;

import doom.game.actor.Actor;
import doom.game.actor.Mobjs;
import doom.game.actor.Thinker;
import doom.game.data.MobjType;
import doom.game.data.StateAction;
import doom.game.data.StateId;
import doom.game.data.ThingType;
import doom.game.physics.Physics;
import doom.game.physics.Sight;
import doom.game.player.Player;
import doom.game.view.LineFlags;
import doom.game.view.MobjFlags;
import doom.game.view.SoundId;
import doom.game.world.Blockmap;
import doom.game.world.Level;
import doom.game.world.Line;
import doom.game.world.Sector;
import doom.game.world.World;
import doom.math.Angle;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;

/**
 * Monster behaviour: a close port of Chocolate Doom 3.1.1 {@code p_enemy.c} (the map-object actions, sound
 * propagation, movement and target selection) and {@code P_NightmareRespawn} of {@code p_mobj.c}. The boss and
 * brain-spitter actions live in {@link BossActions}.
 */
public final class MonsterAiImpl implements MonsterAi {

    // dirtype_t
    static final int DI_EAST = 0;
    static final int DI_NORTHEAST = 1;
    static final int DI_NORTH = 2;
    static final int DI_NORTHWEST = 3;
    static final int DI_WEST = 4;
    static final int DI_SOUTHWEST = 5;
    static final int DI_SOUTH = 6;
    static final int DI_SOUTHEAST = 7;
    static final int DI_NODIR = 8;

    /** {@code opposite[]}. */
    private static final int[] OPPOSITE = {
        DI_WEST, DI_SOUTHWEST, DI_SOUTH, DI_SOUTHEAST, DI_EAST, DI_NORTHEAST, DI_NORTH, DI_NORTHWEST, DI_NODIR
    };
    /** {@code diags[]}. */
    private static final int[] DIAGS = {DI_NORTHWEST, DI_NORTHEAST, DI_SOUTHWEST, DI_SOUTHEAST};
    /** {@code xspeed[]}. */
    private static final int[] XSPEED = {Fixed.FRACUNIT, 47000, 0, -47000, -Fixed.FRACUNIT, -47000, 0, 47000};
    /** {@code yspeed[]}. */
    private static final int[] YSPEED = {0, 47000, Fixed.FRACUNIT, 47000, 0, -47000, -Fixed.FRACUNIT, -47000};

    /** {@code TRACEANGLE}. */
    private static final int TRACEANGLE = 0xc000000;
    /** {@code FATSPREAD}. */
    private static final int FATSPREAD = Angle.ANG90 / 8;
    /** {@code SKULLSPEED}. */
    private static final int SKULLSPEED = 20 * Fixed.FRACUNIT;

    private static final int SK_NIGHTMARE = 4;

    private final World world;
    private final BossActions boss;

    /** {@code soundtarget}: the target of the current {@code P_NoiseAlert}. */
    private Actor soundTarget;

    // PIT_VileCheck state
    private Actor corpseHit;
    private int vileTryX;
    private int vileTryY;
    private final Physics.ThingVisitor pitVileCheck = this::pitVileCheck;

    public MonsterAiImpl(World world) {
        this.world = world;
        this.boss = new BossActions(world, this);
    }

    // ================================================================================== MonsterAi

    @Override
    public void action(StateAction action, Actor actor) {
        switch (action) {
            case A_Look -> aLook(actor);
            case A_Chase -> aChase(actor);
            case A_FaceTarget -> aFaceTarget(actor);
            case A_PosAttack -> aPosAttack(actor);
            case A_SPosAttack -> aSPosAttack(actor);
            case A_CPosAttack -> aCPosAttack(actor);
            case A_CPosRefire -> aCPosRefire(actor);
            case A_SpidRefire -> aSpidRefire(actor);
            case A_BspiAttack -> aBspiAttack(actor);
            case A_TroopAttack -> aTroopAttack(actor);
            case A_SargAttack -> aSargAttack(actor);
            case A_HeadAttack -> aHeadAttack(actor);
            case A_CyberAttack -> aCyberAttack(actor);
            case A_BruisAttack -> aBruisAttack(actor);
            case A_SkelMissile -> aSkelMissile(actor);
            case A_Tracer -> aTracer(actor);
            case A_SkelWhoosh -> aSkelWhoosh(actor);
            case A_SkelFist -> aSkelFist(actor);
            case A_VileChase -> aVileChase(actor);
            case A_VileStart -> world.startSound(actor, SoundId.VILATK);
            case A_StartFire -> aStartFire(actor);
            case A_FireCrackle -> aFireCrackle(actor);
            case A_Fire -> aFire(actor);
            case A_VileTarget -> aVileTarget(actor);
            case A_VileAttack -> aVileAttack(actor);
            case A_FatRaise -> aFatRaise(actor);
            case A_FatAttack1 -> aFatAttack1(actor);
            case A_FatAttack2 -> aFatAttack2(actor);
            case A_FatAttack3 -> aFatAttack3(actor);
            case A_SkullAttack -> aSkullAttack(actor);
            case A_PainAttack -> aPainAttack(actor);
            case A_PainDie -> aPainDie(actor);
            case A_Scream -> aScream(actor);
            case A_XScream -> world.startSound(actor, SoundId.SLOP);
            case A_Pain -> aPain(actor);
            case A_Fall -> aFall(actor);
            case A_Explode -> aExplode(actor);
            case A_Hoof -> aHoof(actor);
            case A_Metal -> aMetal(actor);
            case A_BabyMetal -> aBabyMetal(actor);
            case A_PlayerScream -> aPlayerScream(actor);
            case A_KeenDie -> boss.keenDie(actor);
            case A_BossDeath -> boss.bossDeath(actor);
            case A_BrainAwake -> boss.brainAwake(actor);
            case A_BrainPain -> boss.brainPain(actor);
            case A_BrainScream -> boss.brainScream(actor);
            case A_BrainExplode -> boss.brainExplode(actor);
            case A_BrainDie -> boss.brainDie(actor);
            case A_BrainSpit -> boss.brainSpit(actor);
            case A_SpawnSound -> boss.spawnSound(actor);
            case A_SpawnFly -> boss.spawnFly(actor);
            default -> {
                // NONE, or an action that belongs to another area (the dispatcher does not route those here).
            }
        }
    }

    @Override
    @VanillaRef("p_mobj.c:P_NightmareRespawn")
    public void nightmareRespawn(Actor mobj) {
        int x = mobj.spawnPoint.x << Fixed.FRACBITS;
        int y = mobj.spawnPoint.y << Fixed.FRACBITS;

        // something is occupying its position?
        if (!world.physics.checkPosition(mobj, x, y)) {
            return; // no respawn
        }

        // spawn a teleport fog at the old spot because of removal of the body?
        Actor mo = world.mobjs.spawnMobj(mobj.x, mobj.y, mobj.subsector.sector.floorHeight, MobjType.TFOG);
        // initiate teleport sound
        world.startSound(mo, SoundId.TELEPT);

        // spawn a teleport fog at the new spot
        Sector sector = world.physics.pointInSubsector(x, y).sector;
        mo = world.mobjs.spawnMobj(x, y, sector.floorHeight, MobjType.TFOG);
        world.startSound(mo, SoundId.TELEPT);

        // spawn the new monster
        int z = (mobj.info.flags & MobjFlags.SPAWNCEILING) != 0 ? Mobjs.ONCEILINGZ : Mobjs.ONFLOORZ;

        // inherit attributes from deceased one
        mo = world.mobjs.spawnMobj(x, y, z, mobj.type);
        mo.spawnPoint.set(mobj.spawnPoint);
        mo.angle = Angle.ANG45 * (mobj.spawnPoint.angle / 45);

        if ((mobj.spawnPoint.options & 8) != 0) { // MTF_AMBUSH
            mo.flags |= MobjFlags.AMBUSH;
        }

        mo.reactionTime = 18;

        // remove the old monster
        world.mobjs.removeMobj(mobj);
    }

    @Override
    @VanillaRef("p_enemy.c:P_NoiseAlert")
    public void noiseAlert(Actor target, Actor emitter) {
        soundTarget = target;
        world.validCount++;
        recursiveSound(emitter.subsector.sector, 0);
    }

    // ================================================================================== sound propagation

    /** {@code P_RecursiveSound}: floods adjacent sectors, sound-blocking lines cut off traversal. */
    @VanillaRef("p_enemy.c:P_RecursiveSound")
    private void recursiveSound(Sector sec, int soundBlocks) {
        // wake up all monsters in this sector
        if (sec.validCount == world.validCount && sec.soundTraversed <= soundBlocks + 1) {
            return; // already flooded
        }

        sec.validCount = world.validCount;
        sec.soundTraversed = soundBlocks + 1;
        sec.soundTarget = soundTarget;

        Level level = world.level;
        for (int i = 0; i < sec.lineCount; i++) {
            Line check = sec.lines[i];
            if ((check.flags & LineFlags.TWOSIDED) == 0) {
                continue;
            }

            world.physics.lineOpening(check);

            if (world.physics.openRange <= 0) {
                continue; // closed door
            }

            Sector other;
            if (level.sides[check.sideNum[0]].sector == sec) {
                other = level.sides[check.sideNum[1]].sector;
            } else {
                other = level.sides[check.sideNum[0]].sector;
            }

            if ((check.flags & LineFlags.SOUNDBLOCK) != 0) {
                if (soundBlocks == 0) {
                    recursiveSound(other, 1);
                }
            } else {
                recursiveSound(other, soundBlocks);
            }
        }
    }

    // ================================================================================== ranges and movement

    /** {@code P_CheckMeleeRange}. */
    @VanillaRef("p_enemy.c:P_CheckMeleeRange")
    boolean checkMeleeRange(Actor actor) {
        if (actor.target == null) {
            return false;
        }

        Actor pl = actor.target;
        int dist = Trig.approxDistance(pl.x - actor.x, pl.y - actor.y);

        // gameversion >= exe_doom_1_5 always holds for the supported versions
        int range = Physics.MELEERANGE - 20 * Fixed.FRACUNIT + pl.info.radius;

        if (dist >= range) {
            return false;
        }

        return world.sight.checkSight(actor, actor.target);
    }

    /** {@code P_CheckMissileRange}. */
    @VanillaRef("p_enemy.c:P_CheckMissileRange")
    boolean checkMissileRange(Actor actor) {
        if (!world.sight.checkSight(actor, actor.target)) {
            return false;
        }

        if ((actor.flags & MobjFlags.JUSTHIT) != 0) {
            // the target just hit the enemy, so fight back!
            actor.flags &= ~MobjFlags.JUSTHIT;
            return true;
        }

        if (actor.reactionTime != 0) {
            return false; // do not attack yet
        }

        // OPTIMIZE: get this from a global checksight
        int dist = Trig.approxDistance(actor.x - actor.target.x, actor.y - actor.target.y) - 64 * Fixed.FRACUNIT;

        if (actor.info.meleeState == 0) {
            dist -= 128 * Fixed.FRACUNIT; // no melee attack, so fire more
        }

        dist >>= Fixed.FRACBITS;

        if (actor.type == MobjType.VILE) {
            if (dist > 14 * 64) {
                return false; // too far away
            }
        }

        if (actor.type == MobjType.UNDEAD) {
            if (dist < 196) {
                return false; // close for fist attack
            }
            dist >>= 1;
        }

        if (actor.type == MobjType.CYBORG || actor.type == MobjType.SPIDER || actor.type == MobjType.SKULL) {
            dist >>= 1;
        }

        if (dist > 200) {
            dist = 200;
        }

        if (actor.type == MobjType.CYBORG && dist > 160) {
            dist = 160;
        }

        return world.rng.game() >= dist;
    }

    /** {@code P_Move}: moves in the current direction; false if the move is blocked. */
    @VanillaRef("p_enemy.c:P_Move")
    boolean move(Actor actor) {
        Physics physics = world.physics;

        if (actor.moveDir == DI_NODIR) {
            return false;
        }

        if (Integer.compareUnsigned(actor.moveDir, 8) >= 0) {
            throw new DoomFatalException("Weird actor->movedir!");
        }

        int tryX = actor.x + actor.info.speed * XSPEED[actor.moveDir];
        int tryY = actor.y + actor.info.speed * YSPEED[actor.moveDir];

        boolean tryOk = physics.tryMove(actor, tryX, tryY);

        if (!tryOk) {
            // open any specials
            if ((actor.flags & MobjFlags.FLOAT) != 0 && physics.floatOk) {
                // must adjust height
                if (actor.z < physics.tmFloorZ) {
                    actor.z += Mobjs.FLOATSPEED;
                } else {
                    actor.z -= Mobjs.FLOATSPEED;
                }

                actor.flags |= MobjFlags.INFLOAT;
                return true;
            }

            if (physics.numSpecHit == 0) {
                return false;
            }

            actor.moveDir = DI_NODIR;
            boolean good = false;
            while (physics.numSpecHit-- > 0) {
                Line ld = physics.specHit[physics.numSpecHit];
                // if the special is not a door that can be opened, return false
                if (world.specials.useSpecialLine(actor, ld, 0)) {
                    good = true;
                }
            }
            return good;
        }
        actor.flags &= ~MobjFlags.INFLOAT;

        if ((actor.flags & MobjFlags.FLOAT) == 0) {
            actor.z = actor.floorZ;
        }
        return true;
    }

    /**
     * {@code P_TryWalk}: attempts to move in the current direction; if blocked only by a door, the door is opened by
     * {@link #move}.
     */
    @VanillaRef("p_enemy.c:P_TryWalk")
    private boolean tryWalk(Actor actor) {
        if (!move(actor)) {
            return false;
        }

        actor.moveCount = world.rng.game() & 15;
        return true;
    }

    /** {@code P_NewChaseDir}. */
    @VanillaRef("p_enemy.c:P_NewChaseDir")
    private void newChaseDir(Actor actor) {
        if (actor.target == null) {
            throw new DoomFatalException("P_NewChaseDir: called with no target");
        }

        int olddir = actor.moveDir;
        int turnaround = OPPOSITE[olddir];

        int deltax = actor.target.x - actor.x;
        int deltay = actor.target.y - actor.y;

        int d1;
        int d2;
        if (deltax > 10 * Fixed.FRACUNIT) {
            d1 = DI_EAST;
        } else if (deltax < -10 * Fixed.FRACUNIT) {
            d1 = DI_WEST;
        } else {
            d1 = DI_NODIR;
        }

        if (deltay < -10 * Fixed.FRACUNIT) {
            d2 = DI_SOUTH;
        } else if (deltay > 10 * Fixed.FRACUNIT) {
            d2 = DI_NORTH;
        } else {
            d2 = DI_NODIR;
        }

        // try direct route
        if (d1 != DI_NODIR && d2 != DI_NODIR) {
            actor.moveDir = DIAGS[((deltay < 0 ? 1 : 0) << 1) + (deltax > 0 ? 1 : 0)];
            if (actor.moveDir != turnaround && tryWalk(actor)) {
                return;
            }
        }

        // try other directions
        if (world.rng.game() > 200 || Math.abs(deltay) > Math.abs(deltax)) {
            int tdir = d1;
            d1 = d2;
            d2 = tdir;
        }

        if (d1 == turnaround) {
            d1 = DI_NODIR;
        }
        if (d2 == turnaround) {
            d2 = DI_NODIR;
        }

        if (d1 != DI_NODIR) {
            actor.moveDir = d1;
            if (tryWalk(actor)) {
                // either moved forward or attacked
                return;
            }
        }

        if (d2 != DI_NODIR) {
            actor.moveDir = d2;

            if (tryWalk(actor)) {
                return;
            }
        }

        // there is no direct path to the player, so pick another direction
        if (olddir != DI_NODIR) {
            actor.moveDir = olddir;

            if (tryWalk(actor)) {
                return;
            }
        }

        // randomly determine direction of search
        if ((world.rng.game() & 1) != 0) {
            for (int tdir = DI_EAST; tdir <= DI_SOUTHEAST; tdir++) {
                if (tdir != turnaround) {
                    actor.moveDir = tdir;

                    if (tryWalk(actor)) {
                        return;
                    }
                }
            }
        } else {
            for (int tdir = DI_SOUTHEAST; tdir != (DI_EAST - 1); tdir--) {
                if (tdir != turnaround) {
                    actor.moveDir = tdir;

                    if (tryWalk(actor)) {
                        return;
                    }
                }
            }
        }

        if (turnaround != DI_NODIR) {
            actor.moveDir = turnaround;
            if (tryWalk(actor)) {
                return;
            }
        }

        actor.moveDir = DI_NODIR; // can not move
    }

    /**
     * {@code P_LookForPlayers}: if {@code allAround} is false, only looks 180 degrees in front.
     *
     * @return true if a player is targeted
     */
    @VanillaRef("p_enemy.c:P_LookForPlayers")
    boolean lookForPlayers(Actor actor, boolean allAround) {
        int c = 0;
        int stop = (actor.lastLook - 1) & 3;

        for (;; actor.lastLook = (actor.lastLook + 1) & 3) {
            if (!world.playerInGame[actor.lastLook]) {
                continue;
            }

            if (c++ == 2 || actor.lastLook == stop) {
                // done looking
                return false;
            }

            Player player = world.players[actor.lastLook];

            if (player.health <= 0) {
                continue; // dead
            }

            if (!world.sight.checkSight(actor, player.mo)) {
                continue; // out of sight
            }

            if (!allAround) {
                int an = Trig.pointToAngle2(actor.x, actor.y, player.mo.x, player.mo.y) - actor.angle;

                if (Angle.gt(an, Angle.ANG90) && Angle.lt(an, Angle.ANG270)) {
                    int dist = Trig.approxDistance(player.mo.x - actor.x, player.mo.y - actor.y);
                    // if real close, react anyway
                    if (dist > Physics.MELEERANGE) {
                        continue; // behind back
                    }
                }
            }

            actor.target = player.mo;
            return true;
        }
    }

    // ================================================================================== look and chase

    /** {@code A_Look}: stay in state until a player is sighted. */
    @VanillaRef("p_enemy.c:A_Look")
    private void aLook(Actor actor) {
        actor.threshold = 0; // any shot will wake up
        Actor targ = actor.subsector.sector.soundTarget;

        boolean seeYou = false;
        if (targ != null && (targ.flags & MobjFlags.SHOOTABLE) != 0) {
            actor.target = targ;

            if ((actor.flags & MobjFlags.AMBUSH) != 0) {
                if (world.sight.checkSight(actor, actor.target)) {
                    seeYou = true;
                }
            } else {
                seeYou = true;
            }
        }

        if (!seeYou && !lookForPlayers(actor, false)) {
            return;
        }

        // go into chase state
        if (actor.info.seeSound != 0) {
            int sound;

            switch (actor.info.seeSound) {
                case SoundId.POSIT1, SoundId.POSIT2, SoundId.POSIT3 -> sound = SoundId.POSIT1 + world.rng.game() % 3;
                case SoundId.BGSIT1, SoundId.BGSIT2 -> sound = SoundId.BGSIT1 + world.rng.game() % 2;
                default -> sound = actor.info.seeSound;
            }

            if (actor.type == MobjType.SPIDER || actor.type == MobjType.CYBORG) {
                // full volume
                world.startSound(null, sound);
            } else {
                world.startSound(actor, sound);
            }
        }

        world.mobjs.setMobjState(actor, actor.info.seeState);
    }

    /** {@code A_Chase}: the actor has a melee attack, so it tries to close as fast as possible. */
    @VanillaRef("p_enemy.c:A_Chase")
    void aChase(Actor actor) {
        if (actor.reactionTime != 0) {
            actor.reactionTime--;
        }

        // modify target threshold
        if (actor.threshold != 0) {
            // gameversion > exe_doom_1_2 always holds for the supported versions
            if (actor.target == null || actor.target.health <= 0) {
                actor.threshold = 0;
            } else {
                actor.threshold--;
            }
        }

        // turn towards movement direction if not there yet
        if (actor.moveDir < 8) {
            actor.angle &= (7 << 29);
            int delta = actor.angle - (actor.moveDir << 29);

            if (delta > 0) {
                actor.angle -= Angle.ANG90 / 2;
            } else if (delta < 0) {
                actor.angle += Angle.ANG90 / 2;
            }
        }

        if (actor.target == null || (actor.target.flags & MobjFlags.SHOOTABLE) == 0) {
            // look for a new target
            if (lookForPlayers(actor, true)) {
                return; // got a new target
            }

            world.mobjs.setMobjState(actor, actor.info.spawnState);
            return;
        }

        // do not attack twice in a row
        if ((actor.flags & MobjFlags.JUSTATTACKED) != 0) {
            actor.flags &= ~MobjFlags.JUSTATTACKED;
            if (world.gameSkill != SK_NIGHTMARE && !world.fastParm) {
                newChaseDir(actor);
            }
            return;
        }

        // check for melee attack
        if (actor.info.meleeState != 0 && checkMeleeRange(actor)) {
            if (actor.info.attackSound != 0) {
                world.startSound(actor, actor.info.attackSound);
            }

            world.mobjs.setMobjState(actor, actor.info.meleeState);
            return;
        }

        // check for missile attack (the original jumps to "nomissile" when it does not attack)
        if (actor.info.missileState != 0
                && !(world.gameSkill < SK_NIGHTMARE && !world.fastParm && actor.moveCount != 0)
                && checkMissileRange(actor)) {
            world.mobjs.setMobjState(actor, actor.info.missileState);
            actor.flags |= MobjFlags.JUSTATTACKED;
            return;
        }

        // possibly choose another target
        if (world.netGame && actor.threshold == 0 && !world.sight.checkSight(actor, actor.target)) {
            if (lookForPlayers(actor, true)) {
                return; // got a new target
            }
        }

        // chase towards player
        if (--actor.moveCount < 0 || !move(actor)) {
            newChaseDir(actor);
        }

        // make active sound
        if (actor.info.activeSound != 0 && world.rng.game() < 3) {
            world.startSound(actor, actor.info.activeSound);
        }
    }

    /** {@code A_FaceTarget}. */
    @VanillaRef("p_enemy.c:A_FaceTarget")
    void aFaceTarget(Actor actor) {
        if (actor.target == null) {
            return;
        }

        actor.flags &= ~MobjFlags.AMBUSH;

        actor.angle = Trig.pointToAngle2(actor.x, actor.y, actor.target.x, actor.target.y);

        if ((actor.target.flags & MobjFlags.SHADOW) != 0) {
            actor.angle += world.rng.subRandom() << 21;
        }
    }

    // ================================================================================== attacks

    @VanillaRef("p_enemy.c:A_PosAttack")
    private void aPosAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);
        int angle = actor.angle;
        int slope = world.physics.aimLineAttack(actor, angle, Physics.MISSILERANGE);

        world.startSound(actor, SoundId.PISTOL);
        angle += world.rng.subRandom() << 20;
        int damage = ((world.rng.game() % 5) + 1) * 3;
        world.physics.lineAttack(actor, angle, Physics.MISSILERANGE, slope, damage);
    }

    @VanillaRef("p_enemy.c:A_SPosAttack")
    private void aSPosAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        world.startSound(actor, SoundId.SHOTGN);
        aFaceTarget(actor);
        int bangle = actor.angle;
        int slope = world.physics.aimLineAttack(actor, bangle, Physics.MISSILERANGE);

        for (int i = 0; i < 3; i++) {
            int angle = bangle + (world.rng.subRandom() << 20);
            int damage = ((world.rng.game() % 5) + 1) * 3;
            world.physics.lineAttack(actor, angle, Physics.MISSILERANGE, slope, damage);
        }
    }

    @VanillaRef("p_enemy.c:A_CPosAttack")
    private void aCPosAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        world.startSound(actor, SoundId.SHOTGN);
        aFaceTarget(actor);
        int bangle = actor.angle;
        int slope = world.physics.aimLineAttack(actor, bangle, Physics.MISSILERANGE);

        int angle = bangle + (world.rng.subRandom() << 20);
        int damage = ((world.rng.game() % 5) + 1) * 3;
        world.physics.lineAttack(actor, angle, Physics.MISSILERANGE, slope, damage);
    }

    @VanillaRef("p_enemy.c:A_CPosRefire")
    private void aCPosRefire(Actor actor) {
        // keep firing unless target got out of sight
        aFaceTarget(actor);

        if (world.rng.game() < 40) {
            return;
        }

        if (actor.target == null || actor.target.health <= 0 || !world.sight.checkSight(actor, actor.target)) {
            world.mobjs.setMobjState(actor, actor.info.seeState);
        }
    }

    @VanillaRef("p_enemy.c:A_SpidRefire")
    private void aSpidRefire(Actor actor) {
        // keep firing unless target got out of sight
        aFaceTarget(actor);

        if (world.rng.game() < 10) {
            return;
        }

        if (actor.target == null || actor.target.health <= 0 || !world.sight.checkSight(actor, actor.target)) {
            world.mobjs.setMobjState(actor, actor.info.seeState);
        }
    }

    @VanillaRef("p_enemy.c:A_BspiAttack")
    private void aBspiAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);

        // launch a missile
        world.combat.spawnMissile(actor, actor.target, MobjType.ARACHPLAZ);
    }

    @VanillaRef("p_enemy.c:A_TroopAttack")
    private void aTroopAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);
        if (checkMeleeRange(actor)) {
            world.startSound(actor, SoundId.CLAW);
            int damage = (world.rng.game() % 8 + 1) * 3;
            world.combat.damageMobj(actor.target, actor, actor, damage);
            return;
        }

        // launch a missile
        world.combat.spawnMissile(actor, actor.target, MobjType.TROOPSHOT);
    }

    @VanillaRef("p_enemy.c:A_SargAttack")
    private void aSargAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);

        // gameversion >= exe_doom_1_5 and > exe_doom_1_2 always hold for the supported versions
        if (!checkMeleeRange(actor)) {
            return;
        }

        int damage = ((world.rng.game() % 10) + 1) * 4;
        world.combat.damageMobj(actor.target, actor, actor, damage);
    }

    @VanillaRef("p_enemy.c:A_HeadAttack")
    private void aHeadAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);
        if (checkMeleeRange(actor)) {
            int damage = (world.rng.game() % 6 + 1) * 10;
            world.combat.damageMobj(actor.target, actor, actor, damage);
            return;
        }

        // launch a missile
        world.combat.spawnMissile(actor, actor.target, MobjType.HEADSHOT);
    }

    @VanillaRef("p_enemy.c:A_CyberAttack")
    private void aCyberAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);
        world.combat.spawnMissile(actor, actor.target, MobjType.ROCKET);
    }

    @VanillaRef("p_enemy.c:A_BruisAttack")
    private void aBruisAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        if (checkMeleeRange(actor)) {
            world.startSound(actor, SoundId.CLAW);
            int damage = (world.rng.game() % 8 + 1) * 10;
            world.combat.damageMobj(actor.target, actor, actor, damage);
            return;
        }

        // launch a missile
        world.combat.spawnMissile(actor, actor.target, MobjType.BRUISERSHOT);
    }

    @VanillaRef("p_enemy.c:A_SkelMissile")
    private void aSkelMissile(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);
        actor.z += 16 * Fixed.FRACUNIT; // so missile spawns higher
        Actor mo = world.combat.spawnMissile(actor, actor.target, MobjType.TRACER);
        actor.z -= 16 * Fixed.FRACUNIT; // back to normal

        mo.x += mo.momX;
        mo.y += mo.momY;
        mo.tracer = actor.target;
    }

    @VanillaRef("p_enemy.c:A_Tracer")
    private void aTracer(Actor actor) {
        if ((world.gametic & 3) != 0) {
            return;
        }

        // spawn a puff of smoke behind the rocket
        world.combat.spawnPuff(actor.x, actor.y, actor.z);

        Actor th = world.mobjs.spawnMobj(actor.x - actor.momX, actor.y - actor.momY, actor.z, MobjType.SMOKE);

        th.momZ = Fixed.FRACUNIT;
        th.tics -= world.rng.game() & 3;
        if (th.tics < 1) {
            th.tics = 1;
        }

        // adjust direction
        Actor dest = actor.tracer;

        if (dest == null || dest.health <= 0) {
            return;
        }

        // change angle
        int exact = Trig.pointToAngle2(actor.x, actor.y, dest.x, dest.y);

        if (exact != actor.angle) {
            if (Angle.gt(exact - actor.angle, Angle.ANG180)) {
                actor.angle -= TRACEANGLE;
                if (Angle.lt(exact - actor.angle, Angle.ANG180)) {
                    actor.angle = exact;
                }
            } else {
                actor.angle += TRACEANGLE;
                if (Angle.gt(exact - actor.angle, Angle.ANG180)) {
                    actor.angle = exact;
                }
            }
        }

        int fine = actor.angle >>> Angle.ANGLETOFINESHIFT;
        actor.momX = Fixed.mul(actor.info.speed, Tables.fineCosine(fine));
        actor.momY = Fixed.mul(actor.info.speed, Tables.fineSine(fine));

        // change slope
        int dist = Trig.approxDistance(dest.x - actor.x, dest.y - actor.y);

        dist = dist / actor.info.speed;

        if (dist < 1) {
            dist = 1;
        }
        int slope = (dest.z + 40 * Fixed.FRACUNIT - actor.z) / dist;

        if (slope < actor.momZ) {
            actor.momZ -= Fixed.FRACUNIT / 8;
        } else {
            actor.momZ += Fixed.FRACUNIT / 8;
        }
    }

    @VanillaRef("p_enemy.c:A_SkelWhoosh")
    private void aSkelWhoosh(Actor actor) {
        if (actor.target == null) {
            return;
        }
        aFaceTarget(actor);
        world.startSound(actor, SoundId.SKESWG);
    }

    @VanillaRef("p_enemy.c:A_SkelFist")
    private void aSkelFist(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);

        if (checkMeleeRange(actor)) {
            int damage = ((world.rng.game() % 10) + 1) * 6;
            world.startSound(actor, SoundId.SKEPCH);
            world.combat.damageMobj(actor.target, actor, actor, damage);
        }
    }

    // ================================================================================== Arch-vile

    /** {@code PIT_VileCheck}: detects a corpse that could be raised. */
    @VanillaRef("p_enemy.c:PIT_VileCheck")
    private boolean pitVileCheck(Actor thing) {
        if ((thing.flags & MobjFlags.CORPSE) == 0) {
            return true; // not a monster
        }

        if (thing.tics != -1) {
            return true; // not lying still yet
        }

        if (thing.info.raiseState == StateId.NULL) {
            return true; // monster doesn't have a raise state
        }

        int maxDist = thing.info.radius + world.defs.things()[MobjType.VILE].radius;

        if (Math.abs(thing.x - vileTryX) > maxDist || Math.abs(thing.y - vileTryY) > maxDist) {
            return true; // not actually touching
        }

        corpseHit = thing;
        corpseHit.momX = corpseHit.momY = 0;
        corpseHit.height <<= 2;
        boolean check = world.physics.checkPosition(corpseHit, corpseHit.x, corpseHit.y);
        corpseHit.height >>= 2;

        if (!check) {
            return true; // doesn't fit here
        }

        return false; // got one, so stop checking
    }

    /** {@code A_VileChase}: check for resurrecting a body. */
    @VanillaRef("p_enemy.c:A_VileChase")
    private void aVileChase(Actor actor) {
        if (actor.moveDir != DI_NODIR) {
            // check for corpses to raise
            vileTryX = actor.x + actor.info.speed * XSPEED[actor.moveDir];
            vileTryY = actor.y + actor.info.speed * YSPEED[actor.moveDir];

            Blockmap bm = world.level.blockmap;
            int xl = (vileTryX - bm.originX - Physics.MAXRADIUS * 2) >> Physics.MAPBLOCKSHIFT;
            int xh = (vileTryX - bm.originX + Physics.MAXRADIUS * 2) >> Physics.MAPBLOCKSHIFT;
            int yl = (vileTryY - bm.originY - Physics.MAXRADIUS * 2) >> Physics.MAPBLOCKSHIFT;
            int yh = (vileTryY - bm.originY + Physics.MAXRADIUS * 2) >> Physics.MAPBLOCKSHIFT;

            for (int bx = xl; bx <= xh; bx++) {
                for (int by = yl; by <= yh; by++) {
                    // Call PIT_VileCheck to check whether object is a corpse that can be raised.
                    if (!world.physics.blockThingsIterator(bx, by, pitVileCheck)) {
                        // got one!
                        Actor temp = actor.target;
                        actor.target = corpseHit;
                        aFaceTarget(actor);
                        actor.target = temp;

                        world.mobjs.setMobjState(actor, StateId.VILE_HEAL1);
                        world.startSound(corpseHit, SoundId.SLOP);
                        ThingType info = corpseHit.info;

                        world.mobjs.setMobjState(corpseHit, info.raiseState);
                        corpseHit.height <<= 2;
                        corpseHit.flags = info.flags;
                        corpseHit.health = info.spawnHealth;
                        corpseHit.target = null;

                        return;
                    }
                }
            }
        }

        // Return to normal attack.
        aChase(actor);
    }

    @VanillaRef("p_enemy.c:A_StartFire")
    private void aStartFire(Actor actor) {
        world.startSound(actor, SoundId.FLAMST);
        aFire(actor);
    }

    @VanillaRef("p_enemy.c:A_FireCrackle")
    private void aFireCrackle(Actor actor) {
        world.startSound(actor, SoundId.FLAME);
        aFire(actor);
    }

    /** {@code A_Fire}: keep fire in front of player unless out of sight. */
    @VanillaRef("p_enemy.c:A_Fire")
    private void aFire(Actor actor) {
        Actor dest = actor.tracer;
        if (dest == null) {
            return;
        }

        Actor target = world.mobjs.substNullMobj(actor.target);

        // don't move it if the vile lost sight
        if (!world.sight.checkSight(target, dest)) {
            return;
        }

        int an = dest.angle >>> Angle.ANGLETOFINESHIFT;

        world.physics.unsetThingPosition(actor);
        actor.x = dest.x + Fixed.mul(24 * Fixed.FRACUNIT, Tables.fineCosine(an));
        actor.y = dest.y + Fixed.mul(24 * Fixed.FRACUNIT, Tables.fineSine(an));
        actor.z = dest.z;
        world.physics.setThingPosition(actor);
    }

    /** {@code A_VileTarget}: spawn the hellfire. */
    @VanillaRef("p_enemy.c:A_VileTarget")
    private void aVileTarget(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);

        // VANILLA-BUG: the fire is spawned at (target->x, target->x), the y coordinate is a typo in the original.
        Actor fog = world.mobjs.spawnMobj(actor.target.x, actor.target.x, actor.target.z, MobjType.FIRE);

        actor.tracer = fog;
        fog.target = actor;
        fog.tracer = actor.target;
        aFire(fog);
    }

    @VanillaRef("p_enemy.c:A_VileAttack")
    private void aVileAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);

        if (!world.sight.checkSight(actor, actor.target)) {
            return;
        }

        world.startSound(actor, SoundId.BAREXP);
        world.combat.damageMobj(actor.target, actor, actor, 20);
        actor.target.momZ = 1000 * Fixed.FRACUNIT / actor.target.info.mass;

        int an = actor.angle >>> Angle.ANGLETOFINESHIFT;

        Actor fire = actor.tracer;

        if (fire == null) {
            return;
        }

        // move the fire between the vile and the player
        fire.x = actor.target.x - Fixed.mul(24 * Fixed.FRACUNIT, Tables.fineCosine(an));
        fire.y = actor.target.y - Fixed.mul(24 * Fixed.FRACUNIT, Tables.fineSine(an));
        world.physics.radiusAttack(fire, actor, 70);
    }

    // ================================================================================== Mancubus

    @VanillaRef("p_enemy.c:A_FatRaise")
    private void aFatRaise(Actor actor) {
        aFaceTarget(actor);
        world.startSound(actor, SoundId.MANATK);
    }

    @VanillaRef("p_enemy.c:A_FatAttack1")
    private void aFatAttack1(Actor actor) {
        aFaceTarget(actor);

        // Change direction to ...
        actor.angle += FATSPREAD;
        Actor target = world.mobjs.substNullMobj(actor.target);
        world.combat.spawnMissile(actor, target, MobjType.FATSHOT);

        Actor mo = world.combat.spawnMissile(actor, target, MobjType.FATSHOT);
        mo.angle += FATSPREAD;
        redirect(mo);
    }

    @VanillaRef("p_enemy.c:A_FatAttack2")
    private void aFatAttack2(Actor actor) {
        aFaceTarget(actor);
        // Now here choose opposite deviation.
        actor.angle -= FATSPREAD;
        Actor target = world.mobjs.substNullMobj(actor.target);
        world.combat.spawnMissile(actor, target, MobjType.FATSHOT);

        Actor mo = world.combat.spawnMissile(actor, target, MobjType.FATSHOT);
        mo.angle -= FATSPREAD * 2;
        redirect(mo);
    }

    @VanillaRef("p_enemy.c:A_FatAttack3")
    private void aFatAttack3(Actor actor) {
        aFaceTarget(actor);

        Actor target = world.mobjs.substNullMobj(actor.target);

        Actor mo = world.combat.spawnMissile(actor, target, MobjType.FATSHOT);
        mo.angle -= FATSPREAD / 2;
        redirect(mo);

        mo = world.combat.spawnMissile(actor, target, MobjType.FATSHOT);
        mo.angle += FATSPREAD / 2;
        redirect(mo);
    }

    /** Recomputes the momentum of a missile from its (changed) angle at its own speed. */
    private static void redirect(Actor mo) {
        int an = mo.angle >>> Angle.ANGLETOFINESHIFT;
        mo.momX = Fixed.mul(mo.info.speed, Tables.fineCosine(an));
        mo.momY = Fixed.mul(mo.info.speed, Tables.fineSine(an));
    }

    // ================================================================================== Lost soul and Pain Elemental

    /** {@code A_SkullAttack}: fly at the player like a missile. */
    @VanillaRef("p_enemy.c:A_SkullAttack")
    private void aSkullAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        Actor dest = actor.target;
        actor.flags |= MobjFlags.SKULLFLY;

        world.startSound(actor, actor.info.attackSound);
        aFaceTarget(actor);
        int an = actor.angle >>> Angle.ANGLETOFINESHIFT;
        actor.momX = Fixed.mul(SKULLSPEED, Tables.fineCosine(an));
        actor.momY = Fixed.mul(SKULLSPEED, Tables.fineSine(an));
        int dist = Trig.approxDistance(dest.x - actor.x, dest.y - actor.y);
        dist = dist / SKULLSPEED;

        if (dist < 1) {
            dist = 1;
        }
        actor.momZ = (dest.z + (dest.height >> 1) - actor.z) / dist;
    }

    /** {@code A_PainShootSkull}: spawn a lost soul and launch it at the target. */
    @VanillaRef("p_enemy.c:A_PainShootSkull")
    private void painShootSkull(Actor actor, int angle) {
        // count total number of skulls currently on the level
        int count = 0;
        for (Thinker t = world.thinkers.first(); t != null; t = world.thinkers.next(t)) {
            if (t instanceof Actor a && !a.isRemoved() && a.type == MobjType.SKULL) {
                count++;
            }
        }

        // if there are already 20 skulls on the level, don't spit another one
        if (count > 20) {
            return;
        }

        // okay, there's room for another one
        int an = angle >>> Angle.ANGLETOFINESHIFT;

        int prestep = 4 * Fixed.FRACUNIT
                + 3 * (actor.info.radius + world.defs.things()[MobjType.SKULL].radius) / 2;

        int x = actor.x + Fixed.mul(prestep, Tables.fineCosine(an));
        int y = actor.y + Fixed.mul(prestep, Tables.fineSine(an));
        int z = actor.z + 8 * Fixed.FRACUNIT;

        Actor newMobj = world.mobjs.spawnMobj(x, y, z, MobjType.SKULL);

        // Check for movements.
        if (!world.physics.tryMove(newMobj, newMobj.x, newMobj.y)) {
            // kill it immediately
            world.combat.damageMobj(newMobj, actor, actor, 10000);
            return;
        }

        newMobj.target = actor.target;
        aSkullAttack(newMobj);
    }

    @VanillaRef("p_enemy.c:A_PainAttack")
    private void aPainAttack(Actor actor) {
        if (actor.target == null) {
            return;
        }

        aFaceTarget(actor);
        painShootSkull(actor, actor.angle);
    }

    @VanillaRef("p_enemy.c:A_PainDie")
    private void aPainDie(Actor actor) {
        aFall(actor);
        painShootSkull(actor, actor.angle + Angle.ANG90);
        painShootSkull(actor, actor.angle + Angle.ANG180);
        painShootSkull(actor, actor.angle + Angle.ANG270);
    }

    // ================================================================================== sounds, death, explosion

    @VanillaRef("p_enemy.c:A_Scream")
    private void aScream(Actor actor) {
        int sound;

        switch (actor.info.deathSound) {
            case 0 -> {
                return;
            }
            case SoundId.PODTH1, SoundId.PODTH2, SoundId.PODTH3 -> sound = SoundId.PODTH1 + world.rng.game() % 3;
            case SoundId.BGDTH1, SoundId.BGDTH2 -> sound = SoundId.BGDTH1 + world.rng.game() % 2;
            default -> sound = actor.info.deathSound;
        }

        // Check for bosses.
        if (actor.type == MobjType.SPIDER || actor.type == MobjType.CYBORG) {
            // full volume
            world.startSound(null, sound);
        } else {
            world.startSound(actor, sound);
        }
    }

    @VanillaRef("p_enemy.c:A_Pain")
    private void aPain(Actor actor) {
        if (actor.info.painSound != 0) {
            world.startSound(actor, actor.info.painSound);
        }
    }

    /** {@code A_Fall}: the actor is on the ground, it can be walked over. */
    @VanillaRef("p_enemy.c:A_Fall")
    void aFall(Actor actor) {
        actor.flags &= ~MobjFlags.SOLID;
    }

    @VanillaRef("p_enemy.c:A_Explode")
    private void aExplode(Actor thingy) {
        world.physics.radiusAttack(thingy, thingy.target, 128);
    }

    @VanillaRef("p_enemy.c:A_Hoof")
    private void aHoof(Actor mo) {
        world.startSound(mo, SoundId.HOOF);
        aChase(mo);
    }

    @VanillaRef("p_enemy.c:A_Metal")
    private void aMetal(Actor mo) {
        world.startSound(mo, SoundId.METAL);
        aChase(mo);
    }

    @VanillaRef("p_enemy.c:A_BabyMetal")
    private void aBabyMetal(Actor mo) {
        world.startSound(mo, SoundId.BSPWLK);
        aChase(mo);
    }

    @VanillaRef("p_enemy.c:A_PlayerScream")
    private void aPlayerScream(Actor mo) {
        // Default death sound.
        int sound = SoundId.PLDETH;

        if (world.commercial() && mo.health < -50) {
            // IF THE PLAYER DIES LESS THAN -50% WITHOUT GIBBING
            sound = SoundId.PDIEHI;
        }

        world.startSound(mo, sound);
    }

    @Override
    public Actor[] brainTargets() {
        return boss.savedTargets();
    }

    @Override
    public int brainTargetOn() {
        return boss.savedTargetOn();
    }

    @Override
    public int brainEasy() {
        return boss.savedEasy();
    }

    @Override
    public void restoreBrain(Actor[] targets, int targetOn, int easy) {
        boss.restore(targets, targetOn, easy);
    }
}
