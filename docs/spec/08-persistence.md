# 08 — Persistence: demos and save games

## 1. Demos (`.lmp`)

### 1.1 Format (version 1.9)

| Field | Size |
|-------|------|
| Version (`109`) | 1 byte |
| Difficulty, episode, map | 1 byte each |
| Deathmatch, respawn, fast, nomonsters | 1 byte each |
| Camera player | 1 byte |
| Players present [4] | 4 bytes |
| For each tic, for each player present: `forwardMove`, `sideMove`, high byte of `angleTurn`, `buttons` | 4 bytes |
| End | `0x80` |

- **Reading:** accepts version `109` and `110` (linuxdoom). Other versions → error with a clear message, as in the original.
- **Writing:** always `109`. The command effectively used by the simulation is the one that was recorded ([03 §10](03-simulation.md#10-ticcmd-and-demos-the-simulations-view)).
- **Size limit:** the original ends the recording when the buffer fills up; the remake has no limit. It does not affect the simulation. Documented as a `DIVERGE`.
- **Sources:** file (`-playdemo file.lmp`) or lump (`-playdemo demo1`). The title loop plays `DEMO1`…`DEMO4` according to the `GameMode`.
- **End of the demo:** returns to the title loop (or exits, in `-timedemo`, printing `tics`, real time and average FPS).
- **Key during the title demo:** opens the menu, as in the original.

### 1.2 Integration

`DemoTicSource` (reading) and `DemoRecorder` (writing) are implementations of the `TicCmdSource` seams ([02 §7](02-architecture.md#7-main-loop)). The simulation does not know whether the command came from the keyboard or from a file.

**(ADR-022)** Demo playback and recording live in the session, not in engine `TicCmdSource` implementations (this amends the paragraph above): like `G_ReadDemoTiccmd` / `G_WriteDemoTiccmd` inside `G_Ticker`, because the title loop and deferred game actions start demos inside a tic. The engine's `TicCmdSource` provides local input only; its commands are ignored while a demo plays (see [`docs/contracts.md` §11](../contracts.md#11-cross-module-api-notes)).

## 2. Save games

### 2.1 Principles

- **Custom** format (ADR-013). Not compatible with DOS saves, on purpose.
- The snapshot is generated **synchronously** at the end of a tic, into a `byte[]`. The disk write is done on a virtual thread (ADR-020).
- Saving **more** than the original is allowed (for example, `target`/`tracer` and the RNG indices), because saves do not take part in demo sync. Each extra field is listed here.
- Loading = rebuilding the `Level` from the map lumps and **overlaying** the saved state.

### 2.2 File structure

Little-endian, via `DataOutputStream` over `ByteArrayOutputStream`. Each section carries a tag + size, which allows skipping unknown sections.

```
Header
  magic "DJSV", formatVersion (u16), gameVersion, gameMode
  wads: [name, SHA-256] in load order
  description (24 chars), timestamp
Session
  skill, episode, map, players present, levelTime
  game and "misc" RNG indices                          ← extra compared to the original
  level totals (kills/items/secrets)
Players [4]
  full state: health, armor, weapons, ammo, keycards, powers, psprites, extralight,
  fixedcolormap, damage/bonus counters, viewheight/deltaviewheight, …
World
  sectors: heights, flats, light, special, tag, soundtarget (by actor index)
  lines: flags, special, tag
  sides: offsets, textures
Thinkers (in ThinkerList order)
  [type, fields…]; references to other actors/sectors by index
  Actor.target / Actor.tracer by index                 ← extra compared to the original
Specials
  active buttons (line, texture, time), lists of active ceilings and platforms
```

### 2.3 Compatibility and validation

- `formatVersion` goes up on every change. Old readers are kept as migrations as long as it makes sense.
- **Different WADs** from those of the save → warning with the names. The player can try to load anyway (original behaviour: the save does not store the WADs, so loading with the wrong WAD is possible and unpredictable).
- Corrupted file → clear error, never a *crash*.
- Mandatory test: save → load → **compare the state** (trace hash) immediately and after N tics with the same commands. The two paths have to match.
