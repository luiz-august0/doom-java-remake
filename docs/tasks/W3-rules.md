# Wave 3 — game rules (four parallel agents)

Read `docs/agent-guide.md`, `docs/contracts.md` and **`docs/notes/sim-kernel.md`** (the kernel and area interfaces built by
W2-A) first. The simulation core is on `main`: `World` (session/level globals and service wiring), `Actor`, `ThinkerList`,
`Mobjs`, `Physics` (all of p_map/p_maputl incl. attacks, `ChangeSector`, `RadiusAttack`), `Sight`, `PlayerSim`, `Weapons`,
`Pickups`, `Game` (session skeleton), `TraceWriter`, views. Each area currently runs a stub that calls `Unimplemented.hit`.

Rules: reproduce Chocolate Doom 3.1.1 (`$DOOM_HOME/tools/oracle/work/chocolate-doom/src/doom/`) exactly — same RNG calls in
the same order (incl. short-circuit), fixed-point arithmetic, `// VANILLA-BUG:` kept, `@VanillaRef` on every rule,
contracts §9 version conditionals and overflow emulations, §10 session state. Special thinkers: subclasses of
`SpecialThinker` with `traceCode()`/`traceFields()` exactly as contracts §6.5 table; created zero-filled (§7).

**Wiring:** switch your area from the stub to your implementation in your own factory class only
(`doom.game.ai.AiFactory`, `doom.game.combat.CombatFactory`, `doom.game.mapspecial.SpecialsFactory`). Do not edit other
areas' packages; if you need a kernel change (`doom.game.world`, `actor`, `physics`, `player`, `impl`, `trace`), keep it
minimal, mention it clearly in the report, and prefer adding over changing.

**Testing:** `doom.it.sync.DemoSyncIT` (14 IWAD demos + synthetic corpus, `-Dsync.synthetic=all` for all 387) reports per demo
the first divergent tic/key and the first stub hit. Until the other areas land, divergences right at another area's stub are
expected; **your gate: no divergence caused by your area** (verify with the verbose trace: oracle
`tools/oracle/run.sh <iwad> <demo> -traceverbose f -tracefrom T -traceto T`, ours via the session's verbose trace).
The orchestrator merges each finished area into `main` and tells the others to `git merge main` so you can test together.
Run: `DOOM_IWAD_DIR=$DOOM_HOME/.iwads ./mvnw -o -q -pl doom-it -am verify -Dit.test=DemoSyncIT`.

## W3-A — monster AI (`doom.game.ai`)
All of `p_enemy.c`: `A_Look`/`A_Chase`/`A_FaceTarget`, `P_CheckMeleeRange`, `P_CheckMissileRange`, `P_Move`/`P_TryWalk`/
`P_NewChaseDir` (exact RNG order), `P_LookForPlayers`, `P_NoiseAlert`/`P_RecursiveSound`, every monster attack action
(they call `Combat` for missiles/damage), death/pain/scream/explode sounds, `A_Fall`, boss actions (`A_BossDeath` with
version rules, `A_KeenDie`, `A_BrainAwake/Pain/Scream/Explode/Die/Spit`, `A_SpawnSound/Fly`, cube spawns), Arch-vile
(`A_VileChase/Start/Target/Attack`, `A_Fire`), Revenant tracer (`gametic & 3`), Pain Elemental (20 lost souls limit),
Mancubus, skull fly, `P_NightmareRespawn`. Calls into `MapSpecials` (`doFloor`/`doDoor`) for boss deaths.

## W3-B — combat and weapons (`doom.game.combat`)
`p_inter.c` `P_DamageMobj` (incl. thrust, armor, invulnerability, god mode, pain chance RNG order, target/threshold
infighting, player damage/attacker) and `P_KillMobj` (drops, frags, corpse flags, `A_Fall`-related height change, items);
`p_mobj.c` missiles: `P_SpawnMissile`, `P_SpawnPlayerMissile` (autoaim sweep via `Physics`), `P_CheckMissileSpawn`,
`P_ExplodeMissile`, `P_SpawnPuff`, `P_SpawnBlood`; weapon firing actions of `p_pspr.c` (`A_Punch`, `A_Saw`,
`A_FirePistol/Shotgun/Shotgun2/CGun/Missile/Plasma/BFG`, `A_BFGSpray`, `A_BFGsound`, `P_BulletSlope`, `P_GunShot`, flash
states), ammo use.

## W3-C — map specials (`doom.game.mapspecial`)
`p_spec.c` (line specials cross/use/shoot for every vanilla type, `P_PlayerInSpecialSector` damage/secret/exit,
`P_SpawnSpecials`, `P_UpdateSpecials` animations/switch timers/scroll, find-sector helpers incl. the 20-adjoining overflow,
donut overflow), `p_doors.c`, `p_floor.c` (incl. stairs, donut, `T_MovePlane` with `ChangeSector`), `p_ceilng.c`, `p_plats.c`,
`p_lights.c`, `p_switch.c` (switch list per version incl. retail), `p_telept.c` (version z rule); limits and their failure
modes (MAXPLATS/MAXBUTTONS/MAXLINEANIMS fatal, MAXCEILINGS untracked). Texture/flat translations for the renderer via the
`WorldView`. Sound events at the original points.

## W3-D — session flow (`doom.game.session`, plus `doom.game.impl` view classes for intermission/finale/title)
Complete `G_Ticker`: `G_DoCompleted` (next-map/secret tables, E?M8 victory, par times incl. the MAP33/E4 quirks),
`G_WorldDone`, `G_DoWorldDone`, exits, `G_PlayerFinishLevel`, intermission logic of `wi_stuff.c` (`WI_Start`/`WI_Ticker`
state machine, counters, acceleration via ticcmd buttons, sounds, animated backgrounds using `MiscRandom` in original order)
exposed through `IntermissionView`; finale logic of `f_finale.c` (text timing incl. `strlen` of DEHACKED text, art stages,
cast call with states, `finaleKey()`) via `FinaleView`; title loop `D_PageTicker`/`D_DoAdvanceDemo` per version (DEMO4 for
Ultimate/Final) via `pageName()`; `endGame`, `loadGame`/`requestSave` plumbing (save format itself is Wave 4);
`GameSetup.noIwadDehacked` (`-nodeh`) honoured in `SessionFactory`; audit against contracts §9 table; `docs/divergences`.
