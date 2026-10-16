package doom.game.ai;

import doom.game.actor.Actor;
import doom.game.actor.Thinker;
import doom.game.data.MobjType;
import doom.game.data.StateId;
import doom.game.mapspecial.SpecialTypes;
import doom.game.view.SoundId;
import doom.game.world.Line;
import doom.game.world.World;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.annotation.VanillaRef;
import doom.wad.iwad.GameVersion;
import java.util.Arrays;

/**
 * The boss, level-end and brain-spitter actions of {@code p_enemy.c}: {@code A_BossDeath}, {@code A_KeenDie},
 * {@code A_Brain*}, {@code A_SpawnSound} and {@code A_SpawnFly}. Owns the session state of the original's file-level
 * variables {@code braintargets[]}, {@code numbraintargets}, {@code braintargeton} and the static {@code easy} flag of
 * {@code A_BrainSpit} (contracts §10: never reset).
 */
final class BossActions {

    private static final int SK_EASY = 1;

    private final World world;
    private final MonsterAiImpl ai;

    /** {@code junk}: the dummy line of the boss deaths; only its tag is read by the door and floor code. */
    private final Line junk = new Line();

    private Actor[] brainTargets = new Actor[32];
    private int numBrainTargets;
    private int brainTargetOn;
    /** The function-local {@code static int easy} of {@code A_BrainSpit}. */
    private int easy;

    BossActions(World world, MonsterAiImpl ai) {
        this.world = world;
        this.ai = ai;
    }

    // ================================================================================== save games (W4-P)

    Actor[] savedTargets() {
        return Arrays.copyOf(brainTargets, numBrainTargets);
    }

    int savedTargetOn() {
        return brainTargetOn;
    }

    int savedEasy() {
        return easy;
    }

    void restore(Actor[] targets, int targetOn, int easyFlag) {
        brainTargets = Arrays.copyOf(targets, targets.length > 32 ? targets.length : 32);
        numBrainTargets = targets.length;
        brainTargetOn = targetOn;
        easy = easyFlag;
    }

    // ================================================================================== Keen and boss deaths

    /** {@code A_KeenDie}: DOOM II special, map 32. Uses special tag 666. */
    @VanillaRef("p_enemy.c:A_KeenDie")
    void keenDie(Actor mo) {
        ai.aFall(mo);

        // scan the remaining thinkers to see if all Keens are dead
        if (otherLiveOfTypeExists(mo)) {
            return; // other Keen not dead
        }

        junk.tag = 666;
        world.specials.doDoor(junk, SpecialTypes.Door.OPEN);
    }

    /** True when a map object other than {@code mo} of the same type has health left. */
    private boolean otherLiveOfTypeExists(Actor mo) {
        for (Thinker th = world.thinkers.first(); th != null; th = world.thinkers.next(th)) {
            if (th instanceof Actor mo2 && !mo2.isRemoved() && mo2 != mo && mo2.type == mo.type && mo2.health > 0) {
                return true;
            }
        }
        return false;
    }

    /** {@code CheckBossEnd}: whether the death of this monster type may trigger the end-of-episode special. */
    @VanillaRef("p_enemy.c:CheckBossEnd")
    private boolean checkBossEnd(int motype) {
        if (!world.version.isAtLeast(GameVersion.ULTIMATE)) {
            if (world.gameMap != 8) {
                return false;
            }

            // Baron death on later episodes is nothing special.
            if (motype == MobjType.BRUISER && world.gameEpisode != 1) {
                return false;
            }

            return true;
        }

        // New logic that appeared in Ultimate Doom: bosses only trigger on their specific episode.
        return switch (world.gameEpisode) {
            case 1 -> world.gameMap == 8 && motype == MobjType.BRUISER;
            case 2 -> world.gameMap == 8 && motype == MobjType.CYBORG;
            case 3 -> world.gameMap == 8 && motype == MobjType.SPIDER;
            case 4 -> (world.gameMap == 6 && motype == MobjType.CYBORG)
                    || (world.gameMap == 8 && motype == MobjType.SPIDER);
            default -> world.gameMap == 8;
        };
    }

    /** {@code A_BossDeath}: possibly trigger special effects if on first boss level. */
    @VanillaRef("p_enemy.c:A_BossDeath")
    void bossDeath(Actor mo) {
        if (world.commercial()) {
            if (world.gameMap != 7) {
                return;
            }

            if (mo.type != MobjType.FATSO && mo.type != MobjType.BABY) {
                return;
            }
        } else if (!checkBossEnd(mo.type)) {
            return;
        }

        // make sure there is a player alive for victory
        int i;
        for (i = 0; i < World.MAXPLAYERS; i++) {
            if (world.playerInGame[i] && world.players[i].health > 0) {
                break;
            }
        }

        if (i == World.MAXPLAYERS) {
            return; // no one left alive, so do not end game
        }

        // scan the remaining thinkers to see if all bosses are dead
        if (otherLiveOfTypeExists(mo)) {
            return; // other boss not dead
        }

        // victory!
        if (world.commercial()) {
            if (world.gameMap == 7) {
                if (mo.type == MobjType.FATSO) {
                    junk.tag = 666;
                    world.specials.doFloor(junk, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST);
                    return;
                }

                if (mo.type == MobjType.BABY) {
                    junk.tag = 667;
                    world.specials.doFloor(junk, SpecialTypes.Floor.RAISE_TO_TEXTURE);
                    return;
                }
            }
        } else {
            switch (world.gameEpisode) {
                case 1 -> {
                    junk.tag = 666;
                    world.specials.doFloor(junk, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST);
                    return;
                }
                case 4 -> {
                    switch (world.gameMap) {
                        case 6 -> {
                            junk.tag = 666;
                            world.specials.doDoor(junk, SpecialTypes.Door.BLAZE_OPEN);
                            return;
                        }
                        case 8 -> {
                            junk.tag = 666;
                            world.specials.doFloor(junk, SpecialTypes.Floor.LOWER_FLOOR_TO_LOWEST);
                            return;
                        }
                        default -> {
                        }
                    }
                }
                default -> {
                }
            }
        }

        world.flow.exitLevel();
    }

    // ================================================================================== brain

    /** {@code A_BrainAwake}: find all the target spots. */
    @VanillaRef("p_enemy.c:A_BrainAwake")
    void brainAwake(Actor mo) {
        numBrainTargets = 0;
        brainTargetOn = 0;

        for (Thinker thinker = world.thinkers.first(); thinker != null; thinker = world.thinkers.next(thinker)) {
            if (!(thinker instanceof Actor m) || m.isRemoved()) {
                continue; // not a mobj
            }

            if (m.type == MobjType.BOSSTARGET) {
                if (numBrainTargets == brainTargets.length) {
                    // DIVERGE: the original overflows braintargets[32] into other memory; ours grows
                    // (see docs/divergences/W3-A.md).
                    brainTargets = Arrays.copyOf(brainTargets, numBrainTargets * 2);
                }
                brainTargets[numBrainTargets] = m;
                numBrainTargets++;
            }
        }

        world.startSound(null, SoundId.BOSSIT);
    }

    @VanillaRef("p_enemy.c:A_BrainPain")
    void brainPain(Actor mo) {
        world.startSound(null, SoundId.BOSPN);
    }

    @VanillaRef("p_enemy.c:A_BrainScream")
    void brainScream(Actor mo) {
        for (int x = mo.x - 196 * Fixed.FRACUNIT; x < mo.x + 320 * Fixed.FRACUNIT; x += Fixed.FRACUNIT * 8) {
            int y = mo.y - 320 * Fixed.FRACUNIT;
            int z = 128 + world.rng.game() * 2 * Fixed.FRACUNIT;
            Actor th = world.mobjs.spawnMobj(x, y, z, MobjType.ROCKET);
            th.momZ = world.rng.game() * 512;

            world.mobjs.setMobjState(th, StateId.BRAINEXPLODE1);

            th.tics -= world.rng.game() & 7;
            if (th.tics < 1) {
                th.tics = 1;
            }
        }

        world.startSound(null, SoundId.BOSDTH);
    }

    @VanillaRef("p_enemy.c:A_BrainExplode")
    void brainExplode(Actor mo) {
        int x = mo.x + world.rng.subRandom() * 2048;
        int y = mo.y;
        int z = 128 + world.rng.game() * 2 * Fixed.FRACUNIT;
        Actor th = world.mobjs.spawnMobj(x, y, z, MobjType.ROCKET);
        th.momZ = world.rng.game() * 512;

        world.mobjs.setMobjState(th, StateId.BRAINEXPLODE1);

        th.tics -= world.rng.game() & 7;
        if (th.tics < 1) {
            th.tics = 1;
        }
    }

    @VanillaRef("p_enemy.c:A_BrainDie")
    void brainDie(Actor mo) {
        world.flow.exitLevel();
    }

    @VanillaRef("p_enemy.c:A_BrainSpit")
    void brainSpit(Actor mo) {
        easy ^= 1;
        if (world.gameSkill <= SK_EASY && easy == 0) {
            return;
        }

        // shoot a cube at current target
        Actor targ = brainTargetOn < brainTargets.length ? brainTargets[brainTargetOn] : null;
        if (numBrainTargets == 0) {
            throw new DoomFatalException("A_BrainSpit: numbraintargets was 0 (vanilla crashes here)");
        }
        brainTargetOn = (brainTargetOn + 1) % numBrainTargets;

        // spawn brain missile
        Actor newMobj = world.combat.spawnMissile(mo, targ, MobjType.SPAWNSHOT);
        newMobj.target = targ;
        if (newMobj.momY == 0 || newMobj.state.tics == 0) {
            throw new DoomFatalException("A_BrainSpit: division by zero (vanilla crashes here)");
        }
        newMobj.reactionTime = ((targ.y - mo.y) / newMobj.momY) / newMobj.state.tics;

        world.startSound(null, SoundId.BOSPIT);
    }

    /** {@code A_SpawnSound}: travelling cube sound. */
    @VanillaRef("p_enemy.c:A_SpawnSound")
    void spawnSound(Actor mo) {
        world.startSound(mo, SoundId.BOSCUB);
        spawnFly(mo);
    }

    @VanillaRef("p_enemy.c:A_SpawnFly")
    void spawnFly(Actor mo) {
        if (--mo.reactionTime != 0) {
            return; // still flying
        }

        Actor targ = world.mobjs.substNullMobj(mo.target);

        // First spawn teleport fog.
        Actor fog = world.mobjs.spawnMobj(targ.x, targ.y, targ.z, MobjType.SPAWNFIRE);
        world.startSound(fog, SoundId.TELEPT);

        // Randomly select monster to spawn.
        int r = world.rng.game();

        // Probability distribution (kind of :), decreasing likelihood.
        int type;
        if (r < 50) {
            type = MobjType.TROOP;
        } else if (r < 90) {
            type = MobjType.SERGEANT;
        } else if (r < 120) {
            type = MobjType.SHADOWS;
        } else if (r < 130) {
            type = MobjType.PAIN;
        } else if (r < 160) {
            type = MobjType.HEAD;
        } else if (r < 162) {
            type = MobjType.VILE;
        } else if (r < 172) {
            type = MobjType.UNDEAD;
        } else if (r < 192) {
            type = MobjType.BABY;
        } else if (r < 222) {
            type = MobjType.FATSO;
        } else if (r < 246) {
            type = MobjType.KNIGHT;
        } else {
            type = MobjType.BRUISER;
        }

        Actor newMobj = world.mobjs.spawnMobj(targ.x, targ.y, targ.z, type);
        if (ai.lookForPlayers(newMobj, true)) {
            world.mobjs.setMobjState(newMobj, newMobj.info.seeState);
        }

        // telefrag anything in this spot
        world.physics.teleportMove(newMobj, newMobj.x, newMobj.y);

        // remove self (i.e., cube).
        world.mobjs.removeMobj(mo);
    }
}
