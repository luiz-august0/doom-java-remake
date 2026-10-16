# Simulation kernel (W2-A) — notes for the Wave 3 agents

The simulation core is a close port of Chocolate Doom 3.1.1 `p_mobj.c`, `p_tick.c`, `p_map.c`, `p_maputl.c`,
`p_sight.c`, `p_user.c`, `p_pspr.c` (non-offensive part), `p_inter.c` (pickups) and the game-tic part of `g_game.c`.
The original's globals live in one object per session, `doom.game.world.World`; every rule area receives it in its
constructor and reaches the others through its service fields.

## The `World` object

| Field | Original |
|---|---|
| `rng` (`game()`, `subRandom()`, `misc()`) | `P_Random`, `P_SubRandom`, `M_Random` |
| `defs`, `strings` | `states[]`, `mobjinfo[]`, `weaponinfo[]`, `deh_*` (`defs.misc()`), texts (after DEHACKED) |
| `mode`, `mission`, `variant`, `version` | `gamemode`, `gamemission`, `gamevariant`, `gameversion` (`version.isAtLeast(...)`) |
| `gameSkill` (0-based), `gameEpisode`, `gameMap`, `netGame`, `deathmatch`, `respawnMonsters`, `fastParm`, `noMonsters`, `consolePlayer`, `playerInGame[]`, `players[]` | same names |
| `level` (`sectors`, `lines`, `sides`, `subsectors`, `nodes`, `blockmap`, `reject`) | the level arrays |
| `thinkers` (`ThinkerList`) | `thinkercap` |
| `levelTime`, `totalKills/Items/Secret`, `gametic`, `validCount`, `blockLinks` | same |
| `skyFlatNum`, `skyTexture`, `textures`, `flats` | `skyflatnum`, `skytexture`, `R_TextureNumForName`, `R_FlatNumForName` |
| `bodyQue`, `playerStarts`, `deathmatchStarts`, `itemRespawnQue` | same |
| `events` (`SimEvents`, never null) | `S_StartSound` etc.; use `world.startSound(actor, sfx)` / `world.startSectorSound(sector, sfx)` |
| `unimplemented` | stub-hit recorder (remove your area's stub, do not call it) |
| `flow` (`GameFlow`: `exitLevel()`, `secretExitLevel()`, `deferredInitNew(...)`) | `G_ExitLevel`, `G_SecretExitLevel`, `G_DeferedInitNew` |

Services: `mobjs` (`Mobjs`: `spawnMobj`, `removeMobj`, `setMobjState`, `spawnPlayer`, `spawnMapThing`,
`substNullMobj`, `xyMovement`, `zMovement`), `physics` (`Physics`: everything of `p_map.c`/`p_maputl.c`, see below),
`sight` (`Sight.checkSight`), `playerSim`, `weapons` (`setPsprite`, `checkAmmo`, `fireWeapon`, `dropWeapon`,
`bringUpWeapon`), `pickups` (`giveAmmo`, `giveWeapon`, `giveBody`, `giveArmor`, `giveCard`, `givePower`),
`actions` (the `StateAction` dispatcher), `ai`, `combat`, `specials` (the three Wave 3 areas).

`Physics` exposes the original globals as public fields with camel-case names: `tmFloorZ`, `tmCeilingZ`,
`tmDropoffZ`, `floatOk`, `ceilingLine`, `specHit[]`/`numSpecHit`, `lineTarget`, `attackRange`, `aimSlope`,
`bulletSlope` (lives here because the intercepts overrun writes it), `openTop`/`openBottom`/`openRange`/`lowFloor`,
`trace`, `crushChange`/`noFit` (ints, the spechit overrun stores addresses in them). Methods: `checkPosition`,
`tryMove`, `teleportMove`, `thingHeightClip`, `slideMove`, `aimLineAttack`, `lineAttack`, `useLines`,
`radiusAttack`, `changeSector`, `lineOpening`, `pointOnLineSide`, `boxOnLineSide`, `pointOnDivlineSide`,
`interceptVector`, `pointInSubsector`, `setThingPosition`/`unsetThingPosition`, `blockLinesIterator`,
`blockThingsIterator`, `pathTraverse` (callbacks are `Physics.LineVisitor` / `ThingVisitor` / `InterceptVisitor`;
allocate them once, e.g. as method-reference fields, never per call). Remember to `world.validCount++` before a new
`blockLinesIterator` pass, exactly where the original does.

## Area interfaces (implement them; replace the stubs in `doom.game.impl.SessionFactory`)

### `doom.game.ai.MonsterAi` (W3-A, `p_enemy.c`)
- `void action(StateAction action, Actor actor)` — every map-object action of `p_enemy.c` (`A_Look`, `A_Chase`,
  `A_FaceTarget`, attacks, `A_Explode`, `A_Fall`, `A_Scream`, `A_XScream`, `A_Pain`, `A_PlayerScream`,
  `A_BossDeath`, `A_KeenDie`, `A_Brain*`, `A_SpawnSound`, `A_SpawnFly`, `A_Tracer`, `A_Vile*`, `A_Fire*`, ...).
  `A_OpenShotgun2`/`A_LoadShotgun2`/`A_CloseShotgun2` (in `p_enemy.c` but weapon actions) are done by `Weapons`.
- `void nightmareRespawn(Actor actor)` — `P_NightmareRespawn`; `P_MobjThinker` already did the checks and the
  `P_Random() > 4` call.
- `void noiseAlert(Actor target, Actor emitter)` — `P_NoiseAlert` (called by `P_FireWeapon` and by combat).

### `doom.game.combat.Combat` (W3-B)
- `damageMobj(target, inflictor, source, damage)`, `killMobj(source, target)` — `p_inter.c`.
- `explodeMissile`, `spawnPuff` (reads `physics.attackRange` for the `MELEERANGE` test), `spawnBlood`,
  `spawnMissile`, `spawnPlayerMissile`, `checkMissileSpawn` — the spawn functions of `p_mobj.c`.
- `weaponAction(StateAction, Player, PSprite)` — `A_Punch`, `A_Saw`, `A_FirePistol`, `A_FireShotgun`,
  `A_FireShotgun2`, `A_FireCGun`, `A_FireMissile`, `A_FirePlasma`, `A_FireBFG` (use `physics.bulletSlope`).
- `bfgSpray(Actor)` — `A_BFGSpray` (a map-object action).

### `doom.game.mapspecial.MapSpecials` (W3-C)
- `init()` (`P_InitPicAnims`, `P_InitSwitchList`, once per session), `spawnSpecials()` (end of `P_SetupLevel`),
  `updateSpecials()` (each tic after the thinkers).
- `crossSpecialLine(line, side, thing)`, `useSpecialLine(thing, line, side)`, `shootSpecialLine(thing, line)`,
  `playerInSpecialSector(player)` — the trigger entry points, called at the original call sites.
- `doFloor(line, type)`, `doDoor(line, type)` — `EV_DoFloor` / `EV_DoDoor` for the boss deaths (AI calls them).
- `textureTranslation(tex)`, `flatTranslation(flat)` — for the `WorldView`.
- Sector effects extend `doom.game.actor.SpecialThinker`: implement `think()`, `traceCode()` and
  `traceFields(TraceSink)` with the fields of contracts §6.5 in order (sector references as `sector.index`);
  a ceiling/platform in stasis returns `hasNullFunction() == true` and code 102/104. Create them with all fields
  zero (the oracle zero-fills `PU_LEVSPEC`), add them with `world.thinkers.add(t)`, remove with
  `world.thinkers.remove(t)`; set `sector.specialData`. Moving planes call `world.physics.changeSector(sector,
  crunch)`.

### Game flow (W3-D, `doom.game.session.Game`)
`Game` is a skeleton: `doCompleted`, the intermission/finale tickers, `doWorldDone` and the title loop
(`startTitle`, `doAdvanceDemo`) record stub hits. `G_InitNew`, `G_DoLoadLevel`, `P_SetupLevel`, `G_Ticker`,
`P_Ticker`, `G_DoReborn`, `G_CheckSpot`, `G_DeathMatchSpawnPlayer`, demo playback/recording and the cheats are done.

## Conventions kept by the kernel
- `Actor.stateNum` is the state index (the trace prints it); `Actor.state` is `null` only after `S_NULL`.
- Actor ids (`Actor.id`) are for the presentation only; `removeMobj` emits `stopSound(id)` and frees the id.
- The `prev*` fields (actors, sectors, sides, player view, psprites) are written at the start of each tic and never
  read by the simulation.
- Stubs call `world.unimplemented.hit("area.method")`; the demo sync ITs (`doom.it.sync`) report the first hit.
