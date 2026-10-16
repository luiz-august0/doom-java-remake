# 05 — Data (WAD)

All of the game's content (maps, graphics, sounds, music, demos, end texts) comes from WADs supplied by the player. The remake includes no game content.
The `doom-wad` module is pure Java on top of `java.nio` and knows no game rules, only formats.

## 1. WAD file

| Part | Format (little-endian) |
|------|------------------------|
| Header (12 bytes) | `char[4]` identification (`IWAD` or `PWAD`), `int32` number of lumps, `int32` directory offset |
| Directory (16 bytes per entry) | `int32` offset, `int32` size, `char[8]` name (ASCII, padded with `\0`, compared in uppercase) |

- Opening through `FileChannel.map(READ_ONLY)` → little-endian `ByteBuffer`. Each lump is a `slice()` without copying.
- `LumpDirectory` concatenates IWAD + PWADs in load order. **Name lookup goes from the end to the beginning** (the last one wins), as in the original.
- Maps: marker lump (`E1M1`, `MAP01`) followed by the map's lumps in a fixed order: THINGS, LINEDEFS, SIDEDEFS, VERTEXES, SEGS, SSECTORS, NODES, SECTORS, REJECT, BLOCKMAP.
- All parsing validates bounds and sizes. A malformed lump produces a clear message (`WadFormatException` with file, lump and offset), never a generic exception.

## 2. IWAD discovery and identification

**Search order:** `-iwad <file>` → directory in `DOOMWADDIR` → directories in `DOOMWADPATH` → current directory → the app's data directory ([07](07-ui-input-config.md)).

**Names searched** (in this order): `doom2.wad`, `plutonia.wad`, `tnt.wad`, `doom.wad`, `doom1.wad`, `freedoom2.wad`, `freedoom1.wad`.

**Identification** (`GameMode` + mission):

| Condition | Mode |
|-----------|------|
| Has `MAP01` | `COMMERCIAL` (DOOM II; TNT and Plutonia by file name, as in the original, confirmed by characteristic lumps) |
| Has `E4M1` | `RETAIL` (Ultimate DOOM) |
| Has `E3M1` **(ADR-022)** | `REGISTERED` |
| Otherwise | `SHAREWARE` |

Then `GameVersion` is inferred (`ULTIMATE` for retail, `FINAL` for TNT/Plutonia, `V1_9` for the rest) and can be overridden by `-gameversion` ([03 §9](03-simulation.md#9-gameversion-per-executable-variations)).
Shareware imposes the original's restrictions: no PWADs, episode 1 only.

## 3. PWADs

- `-file a.wad b.wad`: appended in order; the last one wins in the name lookup.
- **Sprites and flats:** in the original, PWADs that bring new sprites or flats between `S_START/S_END` and `F_START/F_END` markers do not work without an external tool, because only those of the IWAD enter the lists.
  The remake replicates this by default and offers `-merge` (Chocolate Doom semantics), which merges the sprite and flat *namespaces*.
- Embedded **DEHACKED** (`DEHACKED` lump) and `-deh`: ~~post-v1 backlog~~ **(ADR-022)** minimal, Chocolate-compatible support in v1 (ADR-014 prepares the tables).

## 4. Lump formats

| Lump | Format | Decoded to |
|------|--------|------------|
| `PLAYPAL` | 14 palettes × 256 × RGB (768 bytes each) | `Palette[14]` |
| `COLORMAP` | 34 × 256 bytes (32 light levels + invulnerability + black) | flat `byte[]` |
| `ENDOOM` | 80×25 text cells in DOS mode (character + attribute) | `TextScreen`. Displaying it on exit is optional (backlog) |
| `THINGS` | 10 bytes: x, y, angle, type, flags (`int16` each) | `MapThing[]` |
| `LINEDEFS` | 14 bytes: v1, v2, flags, special, tag, right side, left side | `MapLine[]` |
| `SIDEDEFS` | 30 bytes: x offset, y offset, upper/lower/middle texture (`char[8]`), sector | `MapSide[]` |
| `VERTEXES` | 4 bytes: x, y (`int16`, in whole units) | `int[]` → fixed-point |
| `SEGS` | 12 bytes: v1, v2, angle, linedef, side, offset | `MapSeg[]` |
| `SSECTORS` | 4 bytes: number of segs, first seg | `MapSubsector[]` |
| `NODES` | 28 bytes: partition (x, y, dx, dy), 2 bboxes, 2 children (bit 15 = subsector) | `MapNode[]` |
| `SECTORS` | 26 bytes: floor, ceiling, floor flat, ceiling flat, light, special, tag | `MapSector[]` |
| `REJECT` | Sector × sector bit matrix | `byte[]` (a short size is handled, [03 §6](03-simulation.md#6-physics-and-collision-invariants)) |
| `BLOCKMAP` | Header (origin, columns, rows) + offsets + line lists terminated by `-1` | `Blockmap` |
| Pictures (patches, sprites, UI graphics) | Width, height, left/top offsets + column offsets + *posts* (`topdelta`, length, pixels) | `Picture` (columns with *posts*) |
| Flats | Raw 64×64 bytes between `F_START`/`F_END` | `byte[4096]` |
| `TEXTURE1`/`TEXTURE2` + `PNAMES` | Composite texture definitions made of patches | `TextureDef[]` → composition in the renderer |
| `DS*` (sound effects) | DMX format: header, rate, number of samples, unsigned 8-bit PCM (with padding at the ends, handled as Chocolate handles it) | `SoundSample` |
| `DP*` (PC speaker) | Tone sequence | Backlog |
| `D_*` (music) | MUS (`MUS\x1A`) or MIDI (`MThd`) | `MusicScore` ([06](06-audio.md)) |
| `GENMIDI` | `#OPL_II#` + 175 OPL instruments (2 voices each) + names | `OplInstrumentBank` |
| `DEMO1`…`DEMO4` | Demo ([08](08-persistence.md)) | `DemoLump` |
| `DMXGUS`, `DMXGUSC` | Gravis UltraSound configuration | Ignored |

**"Tall" patches** (more than 254 pixels in height, DeePsea convention) do not exist in vanilla. The remake accepts them outside vanilla mode; this affects only PWADs.

## 5. Asset cache

- `AssetCache` per session: decodes on demand and stores by lump index (Pictures, Flats, Sounds, Music).
- Composed textures: generated at level load for the textures used on the map, in parallel with virtual threads (ADR-020); the rest on demand.
- Nothing is freed during a level. The total memory of a decoded IWAD is in the tens of MB, which is acceptable.
