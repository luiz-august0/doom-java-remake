# 03 — Simulation

The simulation is the part of the remake with **free form and fixed content** (ADR-007).
This document lists the determinism contract, the invariants that must be preserved and the known pitfalls.
The game rules themselves (each weapon's damage, each monster's speed…) are **not** rewritten here: their specification is the original code and the definitions of ADR-014.

## 1. Determinism contract

**Inputs** (and only these):
1. the set of loaded WADs (IWAD + PWADs, in order);
2. `GameVersion` (1.9, Ultimate, Final, Final2) and `GameMode` (shareware, registered, retail, commercial);
3. start parameters: difficulty, episode, map, `nomonsters`, `respawn`, `fast`, `deathmatch`, players present, local player;
4. the sequence of `TicCmd`s per player.

**Output:** the complete world state after every tic. With the same inputs, it must be identical to DOOM.EXE v1.9's.

**Forbidden inside the simulation** (packages `world`…`game`), with static verification:
- `float`/`double`, `Math.*` (except `Math.abs` on `int` and `Math.floorMod` when the rule calls for it), `StrictMath`;
- clock (`System.nanoTime`, `currentTimeMillis`, `Instant`), randomness outside `Rng`;
- iterating over `HashMap`/`HashSet` (order is not deterministic between runs); use arrays or `LinkedHashMap` with explicit order;
- threads, `parallel()`, `ConcurrentHashMap`;
- reading user configuration. Options that affect gameplay come in **only** through the start parameters.

## 2. Arithmetic

### 2.1 Fixed-point (16.16)

| Operation | Java implementation | Note |
|-----------|---------------------|------|
| `mul(a, b)` | `(int) (((long) a * b) >> 16)` | Identical to the original |
| `div(a, b)` | if `(abs(a) >> 14) >= abs(b)` → `(a ^ b) < 0 ? MIN_VALUE : MAX_VALUE`; otherwise `(int) (((long) a << 16) / b)` | The original uses `double` in the second part; Chocolate Doom uses a 64-bit integer. **Follow Chocolate** |
| `+`, `-` | pure `int` | *Wrap-around* overflow is the expected behaviour (x86) |
| `>>` | Arithmetic (`>>`) for `fixed`; logical (`>>>`) for BAM | See §2.2 |
| `/` and `%` on `int` | Same as C (truncate toward zero) | Do not "fix" them into `floorDiv` |

C → Java types: `signed char` → `byte`; `unsigned char` → `int` with `& 0xFF`; `short` → `short`; `unsigned short` → `int` with `& 0xFFFF`; `int`/`long` (32 bits on DOS) → `int`; `boolean` (`int` in C) → `boolean`, except when the numeric value is used.

### 2.2 Angles (BAM)

- Angle = `int` interpreted as **unsigned**: `ANG45 = 0x20000000`, `ANG90 = 0x40000000`, `ANG180 = 0x80000000` (negative in Java!).
- Index into the fine tables: `angle >>> 19` (8,192 angles). `finecosine[i] = finesine[i + 2048]`.
- The original's angle comparisons (`if (angle > ANG180)`) → `Integer.compareUnsigned(angle, ANG180) > 0`. **Every** angle comparison goes through `Angle.gt/lt/...`.
- `ticcmd.angleturn` is a `short`; the player turns by `angleturn << 16`.

### 2.3 Tables

`finesine` (10,240), `finetangent` (4,096), `tantoangle` (2,049), `rndtable` (256), `gammatable` and the view tables are **constants copied** from the original (ADR-014). `SlopeDiv` and `R_PointToAngle`/`R_PointToAngle2` (octants + `tantoangle`) live in `math` and are used by the simulation **exactly** as in the original.

## 3. RNG

- `Rng.game()`: 8-bit index over `rndtable`, advances before reading. **All** of the simulation's randomness goes through it.
- `Rng.misc()`: separate index for effects without consequence for the game (for example, parts of the menu and sound).
- Both indices are reset at the same points as in the original (start of a new game and of demo playback).
- **Invariant:** same number of calls, in the same order, per tic. Two practical consequences:
  1. Expressions with more than one call use helpers that fix the order: `rng.sub()` = first − second.
  2. An `if` that the original evaluates with short-circuiting (`if (a && P_Random() < x)`) has to keep the short-circuit. Changing the order of the operands changes the number of calls.
- The test trace records the index after every tic, so any violation shows up at the first tic in which it happens.

## 4. Update order per tic

Sequence of a game tic (`GS_LEVEL`), the same as the original:

1. if paused or with the menu open in single-player: does not advance;
2. for each player present, **in index order**: apply the `TicCmd` and run the player's "think" (movement, weapons, use, sector damage);
3. run the `ThinkerList` in creation order. Thinkers marked for removal are removed when the iteration passes through them;
4. update level specials: texture and flat animations, buttons with a timer, wall scroll;
5. item respawn (deathmatch "altdeath");
6. `levelTime++`.

The game flow around this (`Game.tick`) takes care of: pending actions (load level, complete, death → restart), intermission, finale, the demo/title screen and demo checking.

## 5. Actors and the state machine

- Every actor has `state` and `tics`. When `tics` reaches zero, it advances to `state.next`. States with `tics = 0` chain in the **same** tic; `tics = -1` means "forever".
- The state's action runs **on entering** the state. If the action removes the actor, the chaining stops.
- Respawn in Nightmare/`-respawn` and fast monsters (`-fast`) change state durations and projectile speeds according to the original's rules. Since `-fast` modifies the tables, each `Game` has its own copy (ADR-014).
- Spawn order when loading the map: in the order of the THINGS lump, applying the filters for difficulty, mode (single/coop/deathmatch) and `nomonsters`.

## 6. Physics and collision: invariants

- XY movement in sub-steps when the momentum exceeds the original's limit; wall sliding with the same algorithm (trace through the corners of the box); identical friction and stopping.
- **Infinite height** of actors for XY collision (original behaviour): monsters cannot pass over or under others.
- Special lines crossed during a movement go into a list of limited size and are processed **after** the movement, in the reverse order of collection. The overflow of this limit is emulated as in Chocolate Doom.
- Intercepts for *hitscan* and use: collected and sorted by distance, with the same tie-breaking criterion. The limit and the overflow follow Chocolate.
- Line of sight: REJECT first, then BSP traversal. A REJECT smaller than required is padded the way Chocolate does it.
- Gravity, falling and "crushing" (sectors closing on actors) with the same values and the same order of checks.

## 7. Map specials

- Line triggers: cross (W1/WR), use (S1/SR), shoot (G1/GR), manual doors (D1/DR). Each vanilla DOOM line type has a case; unknown types are ignored, as in the original.
- Sector effects (`SectorEffect`): doors, floors, ceilings, platforms, stairs, *donut*, lights. The limits on active ceilings and platforms and on pressed buttons follow the original, including **what happens when they overflow** (fatal error in some, ignored in others). See the original case by case.
- Special sectors: damage (5/10/20 %), secret, blinking lights and the end of level of E1M8 (sector 11).
- Teleport: destination by *tag*, destination angle, the player's `reactionTime`, telefrag. The difference in height (`z`) between versions depends on `GameVersion` (§9).
- Special boss actions (end of E1M8, E2M8, E3M8, E4M6, E4M8, MAP07, MAP32/MAP30) according to the original's rules for each `GameMode`.

## 8. Player

- `viewHeight`, *bob* (sine of `levelTime`) and `deltaViewHeight` exactly as in the original. The *bob* is part of the simulation: it affects `viewz`, which is saved and visible.
- Weapons: `PSprite` (weapon and flash) as small state machines. This includes *autoaim* with the same slope sweep, ammo, weapon switching and pickup priorities.
- Inventory, keycards, powers with timers and the damage or item palette are derived from the player state. The effective palette is decided by the renderer from that state.
- Cheats (`iddqd`, `idkfa`, `idclev`…) come in as keyboard events processed by the game, just like the original. This includes being disabled in demos and in Nightmare where the original disables them.

## 9. `GameVersion`: per-executable variations

Some rules change between the 1.9, Ultimate DOOM and Final DOOM executables. The remake handles them with `if (version.isAtLeast(ULTIMATE))` and similar, **never** with subclasses.
Known examples (the complete list is to be compiled from Chocolate Doom, looking for the uses of `gameversion` in `src/doom/`):

- Lost Soul *bounce* when hitting the floor (changed in Ultimate);
- `z` height after a teleport (changed in Final DOOM);
- number of episodes and maps, texts and end screens.

This survey is an **explicit task** of milestone M3 ([10](10-roadmap.md)) and the result goes into `docs/divergences.md`.

## 10. TicCmd and demos (the simulation's view)

- Fields: `forwardMove` (`byte`), `sideMove` (`byte`), `angleTurn` (`short`), `consistency` (`short`), `chatChar` (`byte`), `buttons` (`byte`, with bits for attack, use, weapon change and the pause and save specials).
- Building it from the local input follows the original: walking and running speeds, *strafe*, turbo, keyboard turn acceleration in the first tics, mouse.
- **Demo recording quantizes the turn**: only the high byte of `angleTurn` is recorded (with rounding) and the command effectively used by the simulation becomes the one that was recorded. Recording a demo slightly changes the turn resolution, as in the original.
- Format and life cycle of the demos: [08](08-persistence.md).

## 11. Suggested implementation order

1. `math` + tests against known values (tables, `mul`/`div`, `PointToAngle`).
2. `world.LevelLoader` + `Blockmap` + `Reject`, with tests on E1M1 (entity counts, bbox, sector of a point).
3. `actor` (states, `ThinkerList`) + `data` (DSL and equivalence).
4. `physics` (movement, collision, sight, traversal).
5. `player` (movement, weapons) → the first demo **without monsters** syncs.
6. `ai` + `combat` → demos with combat.
7. `mapspecial` → complete IWAD demos.
8. `game` (flow, intermission without UI, endings) → the title demo loop running headless.

At each step, the per-tic trace against Chocolate Doom ([09](09-testing-quality.md)) is the done criterion.
