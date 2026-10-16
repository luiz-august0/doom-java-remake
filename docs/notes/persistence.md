# Save games (`doom.game.persist`)

As built (format version 1). The specification is `docs/spec/08-persistence.md` section 2; the differences from the
original are in `docs/divergences/W4-P.md`.

## Flow

- Save: the menu calls `GameSession.requestSave(slot, description)`; the console player's next ticcmd carries
  `BTS_SAVEGAME`; `G_Ticker` of the following tic runs `G_DoSaveGame` before anything else, which calls
  `SaveGameCodec.save` (synchronous, about 1 ms, 100 KB), and reports the bytes through `SimEvents.gameSaved`. The
  engine writes `doomsav<N>.dsg` (+ `.txt` with the description) on a virtual thread, with a temp file and an atomic
  move.
- Load: `GameSession.loadGame(bytes)` sets `ga_loadgame`; `G_DoLoadGame` calls `SaveGameCodec.load`, which validates the
  whole file, then runs `G_InitNew(skill, episode, map)` (the map is rebuilt from the lumps, `levelLoaded` /
  `forceWipe` / music events fire as for any new level) and overlays the saved state (`SnapshotReader`). The rest of the
  loading tic runs on the loaded state, exactly like the tic that follows a save in the running game.
- Interpolation is snapped (`prev*` = current), so nothing slides on the first frame.

## File

Little-endian. `"DJSV"`, `u16 formatVersion`, strings (`u16` length + UTF-8) for game version and game mode, `u8` WAD
count and per WAD name + 32-byte SHA-256, description, `i64` timestamp. Then sections `tag[4] u32 length payload`;
unknown sections are skipped; the last section `"CRC "` is the CRC-32 of every byte before it. Section payloads are
sequences of `int32` (booleans are 0/1); counts are checked against the bytes left, ranges against the game tables.

| Tag | Content |
|---|---|
| `SESS` | skill, episode, map, `gametic`, `leveltime`, level start tic, totals, both RNG indices, game parameters (net game, deathmatch, respawn, fast, nomonsters, console / display player, players in game), sky, `validcount`, `onground`, the tics of the `SARG_RUN1..SARG_PAIN2` states and the three projectile speeds that `-fast` / Nightmare change |
| `PLYR` | the four `player_t` (counts of the arrays first), psprites as state index / tics / position, references to `mo` and `attacker` |
| `WRLD` | sizes (sectors, lines, sides, blockmap columns / rows), per sector heights / pics / light / special / tag / sound state / `soundtarget` / `specialdata`, per line flags / special / tag, per side offsets and textures, the thing list of every sector and every non-empty blockmap cell in link order, body queue, player and deathmatch starts, item respawn queue |
| `THNK` | the thinker table: every live thinker in list order (`KIND_ACTOR` = 33-int map object record, `KIND_SPECIAL` = sector effect as `kind` + fields from `MapSpecials.saveThinker`), then objects that are only referenced (`KIND_GHOST_*`). A reference is an index into this table, -1 for none |
| `SPEC` | `activeceilings[]` and `activeplats[]` slots, `buttonlist[]`, boss brain targets / `braintargeton` / `easy` |

## Code map

- `SaveFormat` (container, header, CRC), `ByteSink` / `ByteSource` (bounds-checked I/O), `WadFingerprint`.
- `SnapshotWriter` (state to bytes), `SessionData` + `SnapshotReader` (bytes to state), `SaveGameCodec` (the
  `SaveGameHandler`, compatibility checks, warnings), `SaveInfo` / `SaveGameCodec.inspect` (header only).
- Accessors added to other areas: `MapSpecials` (`saveThinker`, `restoreThinker`, active slots, buttons; implemented in
  `SpecialsPersistence`), `MonsterAi` (boss brain), `PlayerSim.onGround`, `Game.saveGameHandler()`.

## Tests

- `doom.game.persist.SaveFormatTest`: container, truncation at every length, flipped bits, unknown sections.
- `doom.game.world.SaveGameRoundTripTest`: synthetic two-room map: every kind of sector effect (including stasis
  ceilings and the `crush = 10` of stairs), thing-list order, stale references, players, `-fast` / Nightmare tables,
  1500 damaged files.
- `doom.it.persist.SaveLoadRoundTripIT`: every map of doom1, freedoom1 and freedoom2 plus multiplayer, deathmatch,
  respawn, fast and Nightmare variants; random-walk demo, real save path, fresh session, trace equal tic by tic and the
  snapshots equal byte for byte.
- `doom.it.persist.SaveRobustnessIT`: damaged files leave the running game untouched, different game / map, different
  WADs warn, header contents, saving outside a level, loading from the title, another map and the intermission.
- `doom.it.persist.EngineSaveLoadIT`: F2 / F3 menus, `-loadgame`, empty slot, corrupt file.
