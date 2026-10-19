# Recorded divergences

Every point where the remake (1) follows DOOM.EXE v1.9 / Chocolate Doom instead of linuxdoom-1.10, or (2) intentionally diverges from the original (`// DIVERGE:` in the code, ADR-005 and ADR-007), or (3) replaces undefined behaviour of the original by a defined one.

This file is generated: the source of truth is one file per agent in [`docs/divergences/`](divergences/). Run `python3 tools/docs/consolidate-divergences.py` after editing one (`--check` verifies that this file is current). Columns: ID, area, original behaviour, ours, reason, reference. Rows citing an older local ID (`D-C01`) show it as `(was D-C01)`.

## Design decisions (D-001 to D-005)

| ID | Area | Original (linuxdoom-1.10) | Remake | Reason | Reference |
|----|------|---------------------------|--------|--------|-----------|
| D-001 | Demos | Records and accepts only version `110` | Accepts `109` and `110`; records `109` | Compatibility with the demos in the IWADs | ADR-005 |
| D-002 | Render | Fixed limits on visplanes/drawsegs/vissprites (fails or corrupts the image) | Growable structures | Does not affect the simulation; needed at high resolution | ADR-008 |
| D-003 | Render | Medusa effect on multi-patch midtextures | Not reproduced | Memory artifact | 04 §4 |
| D-004 | Demos | Recording stops when the buffer fills up | No limit | Does not affect the simulation | 08 §1.1 |
| D-005 | Save | Does not save `target`/`tracer` or the RNG | Saves them | Saves are outside demo sync | ADR-013 |

## W1-A: WAD format layer and level geometry loader

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-006 | Map lumps | `P_SetupLevel` reads `lumpnum + ML_*` without checking the lump names | `MapData.load` verifies that THINGS ... BLOCKMAP follow the marker and throws `WadFormatException` otherwise | A mis-ordered PWAD would load garbage in vanilla; a clear error is better and no IWAD map is affected | p_setup.c:P_SetupLevel |
| D-007 | Level loading | Out-of-range indices (vertex, sidedef, sector, linedef, subsector, node child, blockmap offset/line number) read arbitrary memory or crash | `WadFormatException` naming file, lump and index (spec 02 §9) | Memory-safe loader; maps that load in vanilla are unaffected | p_setup.c |
| D-008 | Level loading | A linedef without a front sidedef dereferences NULL in `P_GroupLines` (crash) | `WadFormatException` | Same as above | p_setup.c:P_GroupLines |
| D-009 | Segs | Back sector of a two-sided line whose back side is missing ("glass hack") is the sector at DOS address 0 (`GetSectorAtNullAddress`) | Same sector (heights `0x00199257` / `0x007006F4`, DOS 6.22 memory dump, Chocolate's default); in `WorldView.segBackSector` it is reported as index `-1`, the same as a one-sided line | `WorldView` has no index for a sector that is not in SECTORS | p_setup.c:P_LoadSegs, i_system.c:I_GetMemoryValue |
| D-010 | Reject | A short REJECT lump is padded with the zone-block header of the next allocation (`PadRejectArray`), the rest with 0, or 0xFF under `-reject_pad_with_ff` | Same padding; `-reject_pad_with_ff` is not supported | Rare PWAD compatibility option | p_setup.c:PadRejectArray |
| D-011 | Map lumps | A trailing partial record is ignored (`length / sizeof`) | Same | Vanilla behaviour kept (no divergence) | p_setup.c |
| D-012 | Flats | `R_FlatNumForName` does not range-check; a sector may use any lump as a flat | The loader requires the flat to be inside F_START..F_END | A non-flat lump would index `flattranslation[]` out of range | r_data.c:R_FlatNumForName |
| D-013 | Textures | `R_InitTextures` accepts any texture directory offset not above the lump length and any patch index | Offsets must leave room for the header and all patches; patch indices must be below the PNAMES count; negative sizes rejected | Memory safety | r_data.c:R_InitTextures |
| D-014 | Sky texture | `skytexture` is chosen in `G_InitNew` from the first map of the game (never changes in a Doom II session) | `LevelGeometryLoader` picks the sky of the loaded map's own episode or map number | A geometry-only load has no game history; the session (W2-A) must apply the `G_InitNew` rule | g_game.c:G_InitNew |
| D-015 | Things | `P_LoadThings` stops at the first Doom II-only monster in non-commercial modes (`break`, a vanilla bug that Chocolate keeps) | `Level.things` holds all THINGS entries; the spawner (W2-A) must reproduce the `break` | Parsing and spawning are separate | p_setup.c:P_LoadThings |
| D-016 | Demos | Pre-1.4 headerless demos (versions 0-4) and the 1.91 long-tics format (111) are handled by Chocolate | Rejected with "Demo is from a different game version" | Out of scope (D-001) | g_game.c:G_DoPlayDemo |
| D-017 | Demos | A missing end marker reads past the buffer | Accepted; `Demo.hasEndMarker()` is false; a partial last tic is an error | Robustness | g_game.c:G_ReadDemoTiccmd |
| D-018 | Demos | Recording stops when the buffer is full | `DemoWriter` has no limit | See D-004 | g_game.c:G_WriteDemoTiccmd |
| D-019 | IWAD identification | Chocolate inspects demo lumps to select executables older than v1.9 | Not emulated: such IWADs are identified as v1.9 | Out of scope for v1 | d_main.c:InitGameVersion |

## W1-B: Math foundation and test tooling

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-020 | `FixedDiv(INT_MIN, 0)` | `abs(INT_MIN)` is still negative, so `(abs(a) >> 14) >= abs(b)` is false and the code divides by zero (undefined behaviour in C, crashes on x86) | `Fixed.div` throws `DoomFatalException` in that single case | no vanilla demo can reach it; a defined failure beats undefined behaviour | `m_fixed.c:FixedDiv` |
| D-021 | `Tables` | trig tables are C array literals | one generated big-endian resource `doom/math/tables.bin` (SHA-256 pinned by `TablesTest`), produced by `gen-tables` from `tables.c`, `m_random.c`, `v_video.c` | a 10,240-element literal exceeds the 64 KB `<clinit>` limit (ADR-022 §8) | `tables.c`, `m_random.c`, `v_video.c` |
| D-022 | Trace comparison | `trace-diff` gates every key but `m` | the comparator also reports key-order and missing-key differences as divergences; header differences (iwad, version, demo name case-insensitive) are notes only | the contract says lines must be byte-identical apart from `m`; a reordered key would hide a format bug | contracts §6.2, §6.7 |
| D-023 | Synthetic demos | recorded by playing | generated from a seed (`SplittableRandom`), commands limited to vanilla keyboard values; the demo stores `angleturn >> 8` only | reproducible corpus; what the file holds is what the game reads back | `g_game.c:G_ReadDemoTiccmd` |
| D-024 | Oracle runner | n/a | calls `tools/oracle/run.sh <iwad> <demo.lmp> -trace ...` with `ORACLE_LOG` set and decides success from the `timed N gametics` line in the console log | `run.sh` maps exit 255 to 0, and an engine `I_Error` (for example too few deathmatch starts) also exits 255, so the exit status alone cannot tell a finished demo from an engine error | `tools/oracle/run.sh`, `d_main.c`, `g_game.c:G_CheckDemoStatus` |
| D-025 | Corpus: Freedoom E4M2/E4M3 | a player respawning in coop on a start with thing angle 360 reaches `I_Error("G_CheckSpot: unexpected angle 8192")` (Chocolate's silent-spawn emulation) | the corpus avoids it (E4M2 reseeded, E4M3 `b` is a deathmatch) so every oracle trace is complete; the game module must still reproduce this fatal error | an engine that ends the demo early cannot be compared tic by tic | `g_game.c:G_CheckSpot` (Chocolate 3.1.1) |
| D-026 | `Iwads.assume*` | n/a | reaches JUnit's `Assumptions` reflectively | doom-testkit has no JUnit dependency in its POM (JUnit is test scope only); see the contract request in the W1-B report | ADR-015 |

## W1-C: Game definitions and DEHACKED

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-027 | Definition tables | C arrays in `info.c`, `d_items.c`, `sounds.c` with function pointers | Builder DSL in `doom.game.data` building flat tables; `StateAction` enum instead of function pointers; every table checked against a committed snapshot | ADR-014, ADR-022 §4 | `DefsEquivalenceTest` |
| D-028 | Strings | `DEH_String` is a global hash table keyed by the original text | `GameStrings`, one per session, same semantics (`get(key)` translates the original text; `translate(text)` for lump names) | No mutable static state | `deh_str.c` |
| D-029 | Sound `link` in DEHACKED | `Sound` section field `Zero 1` is a pointer-sized field: a 64-bit Chocolate Doom build rejects it with an error | Same (error `Unknown field type for 'Zero 1' (BUG)`), matching the 64-bit oracle | Bit-for-bit agreement with the oracle | `deh_sound.c`, `deh_mapping.c` |
| D-030 | Sound `singularity` | Not present in Chocolate Doom's `sfxinfo_t` | Kept from linuxdoom `sounds.c` (data only; DEHACKED cannot change it) | Needed by the sound system if it wants the vanilla rule | `sounds.c` |
| D-031 | DEHACKED parse errors | `I_Error` (patch from file) or silently stop parsing (IWAD lump) | `DoomFatalException("Error parsing dehacked file/lump")` unless `Options.allowError`; the changes made before the error stay applied | Same observable behaviour | `deh_main.c:DEH_LoadLump` |
| D-032 | DEHACKED `Pointer` | `codeptrs[]` captured from `states[]` on the first DEHACKED load | Captured from a pristine `GameDefs.createDefault()` | Equivalent, avoids static state | `deh_ptr.c` |
| D-033 | Lone CR at the very end of a patch | Chocolate Doom re-reads the CR forever | Returned once, then end of data | Chocolate bug (hang) | `deh_io.c:DEH_GetChar` |
| D-034 | gen-defs output | n/a | Generated Java is hand-editable; re-running `gen-defs` overwrites hand edits | ADR-014 bootstrap | `doom-codegen` |

## W1-D: Audio: formats, OPL3, music driver, mixer, Java Sound

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-035 | mus2mid | Per-channel velocities and the queued time are static: a song converted after another inherits the last velocities of the previous song | Every conversion starts from velocity 127 on all channels | Deterministic per song; identical to Chocolate for the first song of a process, which is what the byte-for-byte test compares (each lump is converted in a fresh process) | mus2mid.c:channelvelocities |
| D-036 | mus2mid | `CHECK_MUS_HEADER` is not defined: the MUS signature is not checked, header fields are read as is | Same (no signature check), but a score start beyond the lump or truncated data throws `WadFormatException` | Never an uncontrolled exception on bad PWAD data (spec 02 §9) | mus2mid.c:mus2mid |
| D-037 | Music lump type | `MThd` lumps up to 96 KiB are MIDI; larger ones are treated as MUS and fail | A lump starting with `MThd` is MIDI at any size | Larger PWAD MIDI files simply work | i_oplmusic.c:I_OPL_RegisterSong |
| D-038 | MIDI parsing | `program change` / controller / key parameters are used unmasked and index tables out of range if bit 7 is set | Parameters are masked to 7 bits where they index a table (instrument number, volume tables) | Memory safety; valid files are unaffected | i_oplmusic.c |
| D-039 | Sequencer | Tracks of a format 1 file run as separate timer chains with microsecond callbacks | Same: `MusicScore` keeps the per-track chain and delta, the player schedules one callback per track in the same 64-entry heap. Merged order is only informative | Needed to reproduce Chocolate's register order bit for bit | opl_sdl.c, opl_queue.c |
| D-040 | Music timing | Callbacks are quantised to audio samples and depend on the SDL_mixer chunk size (4096 frames at 44.1/48 kHz) | Same algorithm; the quantisation depends on the block size the engine renders (256-512 frames) | Differences are below one sample per block; tests compare with the same block size on both sides | opl_sdl.c:OPL_Mix_Callback |
| D-041 | OPL init | `OPL_Init` detects the chip with timer reads and `OPL_Delay`, then `OPL_InitRegisters` | Detection skipped; `OPL_InitRegisters` writes are reproduced; writes to registers 2, 3, 4 (timers) are not sent to the chip, as in `opl_sdl.c` | There is no hardware to detect | opl.c, opl_sdl.c |
| D-042 | Music volume | `S_SetMusicVolume(level * 8)` | `setVolumes(sfx, music)` takes 0-15 and passes `music * 8`; initial level 8 (64) | Menu scale of the original | s_sound.c |
| D-043 | SFX resampling | SDL_mixer / SDL_AudioCVT conversion, nearest sample for the ratios DOOM uses | Linear interpolation by default (spec 06 section 2), nearest selectable (`linearInterpolation = false`) | Spec choice; nearest is closest to the raw sound | i_sdlsound.c:ExpandSoundData |
| D-044 | SFX pan law | `Mix_SetPanning(left = (254 - sep) * vol / 127, right = sep * vol / 127)` linear gains | Same linear law (a centred sound is 6 dB below hard-panned) | Spec 06 says "constant-power"; contracts say Chocolate wins. Behaviour matches DOS DMX | i_sdlsound.c:I_SDL_UpdateSoundParams |
| D-045 | SFX mixing | SDL_mixer sums in 16 bit with saturation | `int` accumulation and an integer soft limiter (linear below 24576, rational knee towards 32767) | Spec 06 section 2 | - |
| D-046 | SFX pitch | `snd_pitchshift` (off by default) resamples the sound to `(2 - pitch/128)` of its length | If a pitch other than 128 is passed, playback speed is `128 / (256 - pitch)` (capped at 4x) | Equivalent, done at mix time without a second sample copy | i_sdlsound.c:PitchShift |
| D-047 | SFX channels | 16 SDL_mixer channels | 8 by default, up to 32 | Spec 06 section 1 | - |
| D-048 | DMX sound padding | The first and last 16 bytes of the sample area are skipped; lumps with 48 samples or fewer are ignored | Same; additionally a sample rate of 0 is rejected | A zero rate divides by zero in Chocolate | i_sdlsound.c:CacheSFX |
| D-049 | Output latency | SDL audio device | `SourceDataLine` with a 2-block (1024 frame, 21 ms) buffer; see docs/notes/audio-latency.md | ADR-012 | - |

## W1-E: 2D foundation and LWJGL platform

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-050 | `V_DrawPatch` range check | `RANGECHECK` builds call `I_Error("Bad V_DrawPatch")` for a patch outside 320×200; non-RANGECHECK builds write out of bounds | `Canvas2D.drawPatch` clips silently against the real frame | patches may legitimately spill into the pillarbox (wide pictures) and the canvas is not limited to 320×200 | spec 04 §6 |
| D-051 | Patch scaling | none (1:1 pixels) | virtual 320×200 scaled by `s = H/200` with nearest sampling; virtual column `v` covers physical `[px(v), px(v+1))`, `px(v) = round(W/2 + H(v-160)/200)` (half up) | resolution independent 2D layer, centred in X | spec 04 §6, ADR-010 |
| D-052 | Tall patches | `topdelta` is absolute; a post can not start below row 254 | outside vanilla mode a `topdelta` not greater than the previous post's top is relative to it (DeePsea convention); `Picture.decode(.., tallPatches)` | support taller graphics in PWADs; never applied in vanilla mode | spec 05 §4 |
| D-053 | Keyboard mapping | Chocolate maps SDL scancodes (US layout positions) | GLFW key tokens mapped by position in the same way (`GlfwKeys`); super keys produce nothing | same behaviour as `vanilla_keyboard_mapping`; CHAR events carry the real typed code point | `i_input.c TranslateKey` |
| D-054 | Typed characters | `shiftxform` table applied to the US scancode translation | `CHAR` events deliver the layout-aware Unicode code point from GLFW | proper text entry on non-US layouts (save names, cheats) | spec 07 §3 |
| D-055 | Mouse | SDL relative mouse mode with acceleration in `i_input.c` | raw (unaccelerated) motion deltas; acceleration/threshold are the engine's job | spec 07 §3 | `GLFW_RAW_MOUSE_MOTION` |
| D-056 | Presentation | SDL software scaler with aspect-ratio correction | `GL_R8` index texture + palette texture, FIT/INTEGER/STRETCH with pixel aspect 1.2 or 1.0, NEAREST or sharp bilinear | ADR-009 | spec 04 §8 |

## W2-A: Simulation core, session skeleton, trace

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-057 | State actions in the wrong kind of state | Chocolate calls a weapon action from `P_SetMobjState` (or a map-object action from `P_SetPsprite`) with mismatched arguments: undefined behaviour | ignored (`ActionDispatcher`) | only reachable with odd DEHACKED patches; no defined behaviour to reproduce | p_mobj.c:P_SetMobjState, p_pspr.c:P_SetPsprite |
| D-058 | Intercepts overrun, third word | Chocolate writes the host address of the line / thing (`(intptr_t) d.thing`) into the overwritten variable | writes 0 | the host address is not reproducible (the oracle itself is not deterministic there); frac and isaline are emulated exactly | p_maputl.c:InterceptsOverrun |
| D-059 | Intercepts / spechit arrays | Chocolate has fixed arrays of 189 intercepts and 20 spechits; going past them corrupts real memory | growable arrays (the documented overrun emulation of the first 8/128 + overrun entries is kept) | no defined behaviour beyond Chocolate's own limits | p_maputl.c, p_map.c |
| D-060 | Blockmap after an intercepts overrun changed `bmapwidth`/`bmapheight` | out-of-range reads of `blocklinks`/`blockmap` | treated as empty cells | avoid raw `ArrayIndexOutOfBoundsException` (spec 02 §9) | p_maputl.c |
| D-061 | `G_CheckSpot` first spawn | dereferences `players[i].mo` of players not in game (NULL) | skips null bodies | crash in the original | g_game.c:G_CheckSpot |
| D-062 | Demo without end marker | `G_ReadDemoTiccmd` reads past the buffer | the end of the data acts as the end marker | undefined behaviour | g_game.c:G_ReadDemoTiccmd |
| D-063 | Demo version 110 | Chocolate rejects it ("different game version") | read as 109 | `GameSession.playDemo` contract (linuxdoom demos) | g_game.c:G_DoPlayDemo |
| D-064 | `G_DoNewGame` with full `GameParams` | the menu path resets netgame, deathmatch, -respawn, -fast, -nomonsters, consoleplayer; the command line sets them before `G_InitNew` | `newGame(params)` applies all parameters (the command-line path); the IDCLEV / menu path (`deferredInitNew`) resets them as the original | one API entry point for both callers | g_game.c:G_DoNewGame, d_main.c |
| D-065 | Sound / misc RNG | `S_StartSound` draws `M_Random` inside the simulation | the simulation only emits `SimEvents.startSound`; the presentation draws the misc RNG | contracts §8/§11 (trace key `m` is informational; with `events == null` it stays 0) | s_sound.c:S_StartSound |
| D-066 | HUD messages | `player->message` read by `HU_Ticker` | also emitted immediately through `SimEvents.playerMessage` | presentation hook | p_inter.c, st_stuff.c |
| D-067 | Session skeleton | `G_DoCompleted`, intermission, finale, title loop, save/load | stubs recording `session.*` hits (`G_DoCompleted` still takes away cards/powers and enters GS_INTERMISSION) | W3-D / W4-P own them | g_game.c, d_main.c |
| D-068 | `-nodeh` | Chocolate can skip the Freedoom IWAD DEHACKED lump | always applied (GameSetup has no flag for it) | contract request filed | d_main.c:LoadIwadDeh |

## W2-B: 3D renderer and wipe

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-069 | Wipe RNG draws | contracts §8 / task brief say 160 draws | `Wipe` draws **320** (`y[0]` + 319 columns), exactly as `wipe_initMelt` loops `i < width` with `width = SCREENWIDTH = 320`; only the first 160 values drive strips at 320x200 | the oracle trace confirms it: the misc index jumps by 65 = 1 (`ST_Ticker`) + 64 at the first wipe, and 320 mod 256 = 64 (160 draws would give 161); contract §8 needs correcting | `f_wipe.c:wipe_initMelt`, `testdata/traces/doom1-demo1.trace.gz` |
| D-070 | Wipe resolution | 2-pixel strips of 320x200 | state kept in 320x200 virtual units (identical timing at every size); strips are `2·s` px wide, mapped centred; widescreen strips outside the 4:3 area reuse the other random-walk values of the same 320 draws (index modulo 320) | resolution independence without extra RNG draws | spec 04 §6 |
| D-071 | Projection | `projection = centerxfrac` (half the window width) | `projection = vw0·H/400` in fixed point (`W43/2` of the window's 4:3 size), `centerx = W/2`: Hor+; identical at 320x200 | spec 04 §2, ADR-010 | `r_main.c:R_ExecuteSetViewSize` |
| D-072 | Light by scale | `scalelight` indexed by `scale >> 12` | scale multiplied by `200/H` before indexing (spec 04 §4); table built with the virtual window width | light falls off with distance exactly as in the original at any height | spec 04 §4 |
| D-073 | Scale limits | wall scale clamped to `[256, 64·FRACUNIT]` | upper clamp `64·FRACUNIT·H/200` outside vanilla (same value at H = 200) | scale grows with `H` | `r_main.c:R_ScaleFromGlobalAngle` |
| D-074 | `viewangletox` range | tangent clamp at ±2.0 | clamp grows with the aspect (`ceil(centerx/projection)+1`) outside vanilla | fields of view wider than 127 degrees | `r_main.c:R_InitTextureMapping` |
| D-075 | Span drawer | Chocolate's packed 6.10/6.10 position (carry of the v fraction into u) | reproduced in vanilla mode; plain 16.16 u/v outside it | exactness at 320x200, precision at high resolution | `r_draw.c:R_DrawSpan` |
| D-076 | Texture wrap | `dc_source[(frac>>16) & 127]` | reproduced in vanilla mode (single-patch columns read the lump bytes after the post, short composite columns read on into the next composite column); outside it walls wrap at the real height and posts clamp | "tutti-frutti" kept only in vanilla mode | spec 04 §4 |
| D-077 | Composite memory past the end | the last composite column reads heap garbage | reads zeros (128 bytes of padding) | undefined in the original; not seen in the oracle frames | `r_data.c:R_GenerateComposite` |
| D-078 | Masked multi-patch textures (Medusa) | masked mid textures made of several patches are read as posts from raw composite pixels (garbage) | posts are derived from the pixels the patches covered | spec 04 §4 (DIVERGE) | `r_segs.c:R_RenderMaskedSegRange` |
| D-079 | Column without a patch | `R_GenerateLookup` prints a message and stops filling the lookup | the column is composed as transparent/zero and the rest of the texture is processed | robustness | `r_data.c:R_GenerateLookup` |
| D-080 | Pool limits | 128 visplanes (error), 256 drawsegs (silently dropped), 128 vissprites (overflow sprite), `32` solid segs (Chocolate error), `64·320` openings | all pools grow on demand; nothing is dropped or fatal | spec 04 §4 (visplane overflow is not reproduced) | `r_plane.c`, `r_bsp.c`, `r_things.c` |
| D-081 | Visplane columns | `byte top[]/bottom[]` with 0xff as "unset" (limits height to 255) | `short[]` with `Short.MAX_VALUE` as unset | frames taller than 255 rows | `r_plane.c:visplane_t` |
| D-082 | Sprite range checks | `RANGECHECK` builds `I_Error` on invalid sprite/frame/column | invalid sprite or frame: the thing is not drawn; sprite column clamped | never fail on bad data | `r_things.c` |
| D-083 | Own sprite | the viewer's own mobj is skipped only because `tz < MINZ` | skipped explicitly (`Camera.viewActor`) | stays correct with an interpolated camera | `r_things.c:R_ProjectSprite` |
| D-084 | Sprite lumps | `R_InitSpriteLumps` reads all headers at start | pictures decoded on first use (`precache()` loads all) | start-up time | `r_things.c:R_InitSpriteLumps` |
| D-085 | Fuzz at high resolution | one row up/down per pixel | `FuzzStyle.SCALED` (default outside vanilla, only when `H >= 400`): offset and table position in blocks of `round(H/200)` rows and columns; `PIXEL` keeps the original | spec 04 §4 | `r_draw.c:R_DrawFuzzColumn` |
| D-086 | HUD message line in the golden frames | the oracle frame is the whole screen | `GoldenFrameIT` ignores differences in the first 10 rows of the view window (the HU message line, drawn by the 2D layer); everything else must be identical (91 of 167 frames contain a message) | the 3D renderer does not draw HUD | `hu_stuff.c` |
| D-087 | `precisionFixes` | n/a | accepted in `RenderOptions` and ignored for now | task brief ("off for now") | spec 04 §3 |
| D-088 | Texture composition threads | single thread | `precache()` composes textures on virtual threads | start-up time | spec 04 §4 |

## W2-C: UI: menus, HUD, status bar, automap, finale

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-089 | Menu key bindings | `key_menu_*` are configurable | fixed defaults (Esc, arrows, Enter, Backspace, y/n, F1-F11, `-`/`=`); automap keys fixed (Tab, arrows, `-`/`=`, 0, f, g, m, c) | the engine does not expose menu rebinding in v1 | m_menu.c, am_map.c |
| D-090 | Save description typing | `ev->data1` (shift ignored) unless `vanilla_keyboard_mapping` is off | `Ui.charTyped` delivers the layout-aware code point (upper-cased, only characters the STCFN font has); with `setCharEvents(false)` the key code is used like vanilla | proper text entry on non-US layouts | m_menu.c `M_Responder` |
| D-091 | Joystick menu input | `ev_joystick` data with `joywait` | `Ui.padButton` maps A/B/START/D-pad to Enter/Back/Esc/arrows (y/n inside questions, Enter/Esc inside text entry) | GLFW gamepad API instead of DOS joystick | m_menu.c |
| D-092 | Mouse menu input | `mousewait < I_GetTime()` and Doom mouse units (y up) | same thresholds (30 counts, 5 and 15 tic waits) on raw counts (y down) | raw mouse motion | m_menu.c |
| D-093 | Menu help pages | `V_DrawPatchDirect(0,0,...)` on a 320x200 screen | `drawPatchCentred` over a cleared frame (black bars, or the border flat) | widescreen pillarbox | spec 04 §6 |
| D-094 | Extended options | none | "Extended Options" entry (9th row of Options, text) listing engine supplied pages; hidden in vanilla mode or when the engine supplies none | spec 07 §2 | - |
| D-095 | Status bar redraw | dirty-rectangle updates through `st_backing_screen` | the whole bar is redrawn each frame; sides filled with the border flat (`FLOOR7_2` / `GRNROCK`) or a wide STBAR | no backing screen, resolution independent | spec 04 §6 |
| D-096 | Face logic | `attacker`/`mo` pointers | `attacker` actor id must still exist (else the "being attacked" branch is skipped); `plyr->attackdown` comes from a hook (`Ui.Builder.attackDown`) until `PlayerView` exposes it | views are index based | st_stuff.c `ST_updateFaceWidget` |
| D-097 | Full-screen HUD | only messages at view size 11 | optional minimal HUD (health, armor, ammo, keys) anchored to screen corners or the 4:3 area (`UiSettings.fullscreenHud/hudAlign`); off in vanilla mode | spec 04 §6 | - |
| D-098 | View border | `R_DrawViewBorder` on a 320x200 back screen | `Ui.drawViewBorder` tiles the flat in the view border and in the pillarbox bars above the status bar, bezels at the original virtual positions | widescreen | r_draw.c |
| D-099 | Automap resolution | 320x168 `fb` in the 8-bit screen | native W x (H - status bar); lines `max(1, round(s * 0.6))` px thick (`UiConfig.automapLineScale`); window is interpolated between tics (centre, scale, arrow, things); fixed-point map maths unchanged | spec 04 §6, 07 §2 | am_map.c |
| D-100 | Automap `iddt` | `cht_CheckCheat` with the DEHACKED-able sequence, event-based | built-in matcher for `iddt` (not dehackable), only while the automap is open and not in deathmatch | sequence is not in `DefsView` | am_map.c `AM_Responder` |
| D-101 | Automap marks | `V_DrawPatch` of the AMMNUM patch | integer pixel scaled blit at native resolution | native drawing | am_map.c `AM_drawMarks` |
| D-102 | `ML_MAPPED` | line flag set by the renderer | `MappedLines` supplied by the engine from the renderer | the view does not carry it | contracts WorldView |
| D-103 | Chat | `HU_Responder`, `w_chat`, chat macros | not implemented (no netcode in v1) | contracts non-goal | hu_stuff.c |
| D-104 | Bunny scroll | `F_DrawPatchCol` column copy into the 320 column buffer | two patches drawn at `-scrolled` and `320-scrolled`, bars repainted black; the pistol shot of the "THE END" stage is played from `Ui.tick` instead of the drawer | same picture, sound tied to tics not frames | f_finale.c `F_BunnyScroll` |
| D-105 | Wipe | n/a | not part of the UI | owned by doom.render.wipe | - |
| D-106 | Quit | `I_WaitVBL(105)` after the sound | `UiActions.quit()` after starting the sound; the engine may wait | engine owns the process | m_menu.c `M_QuitResponse` |
| D-107 | Quit messages | literals in `dstrings.c` | literals (index 0 uses `QUITMSG` after DEHACKED), `DOSY` from the strings | `DefsView` has no translate | dstrings.c |

## W2-D: Engine, main loop, sound system, app

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-108 | Mouse input | `G_Responder` assigns `mousex`/`mousey` from each event: when several mouse events arrive between two tics only the last counts | Motion is accumulated between tics; the sub-unit remainder of `(sensitivity + 5) / 10` scaling is carried to the next tic | No lost motion at frame rates above 35 Hz (several frames per tic) | g_game.c:G_Responder |
| D-109 | Mouse acceleration | Chocolate applies `mouse_acceleration` / `mouse_threshold` | Not implemented; sensitivity only | Modern raw input; spec 07 section 3 asks for X/Y sensitivity | i_input.c |
| D-110 | Per-frame camera turn | Camera angle is the player angle of the last tic | The renderer camera adds the mouse turn not yet consumed by a tic (`LocalTicSource.pendingTurn`), on top of the interpolated angle | Spec 04 section 7 | - |
| D-111 | Double click to use | `dclick_use`: double click on the forward or strafe mouse button sends BT_USE | Not implemented | Not part of the spec 07 binding model | g_game.c:G_BuildTiccmd |
| D-112 | Key releases during menu | `G_Responder` is not reached while the menu eats events, but key-up events "always filter down" | Same for key-up; additionally focus loss releases every key and button | Avoid stuck keys after Alt-Tab | g_game.c:G_Responder |
| D-113 | Always run | `joyb_speed >= MAX_JOY_BUTTONS` hack makes speed permanently 1 | `input.alwaysRun` inverts the run key (the key then walks) | Usual modern behaviour | g_game.c:G_BuildTiccmd |
| D-114 | Gamepad | DOS joystick axes, digital or analog (`use_analog`) | GLFW gamepad: right stick turns, left stick moves and strafes, dead zone, cubic turn response, analog path of `G_BuildTiccmd` with `joystick_*_sensitivity` | spec 07 section 3 | g_game.c:G_BuildTiccmd |
| D-115 | Pause key | `sendpause` writes a special ticcmd for the console player | The engine calls `GameSession.requestPause()` once per key press, before the next tic | The session owns the special command (contracts section 11) | g_game.c:G_BuildTiccmd |
| D-116 | Sound pitch | `snd_pitchshift` is off: the pitch passed to `I_StartSound` is ignored | The random pitch is always drawn (misc RNG) and stored on the channel, but reaches the mixer only with `sound.pitchVariation = true` | Keeps the RNG sequence of vanilla (contracts section 8) while sounding like DOS | s_sound.c:S_StartSound |
| D-117 | Sound channel lifetime | `I_SoundIsPlaying` asks SDL_mixer | A sound counts as playing until the audio thread has mixed two blocks after the start command, then the mixer's report decides; headless runs use the sample length in tics | The audio thread reports once per block, a just-started sound is not visible yet | s_sound.c:S_UpdateSounds |
| D-118 | Level music | `S_Start` (from `P_SetupLevel`) picks the level's song | `SoundSystem.levelLoaded()` does the same on the `levelLoaded` event; a `changeMusic` of the same song from the session is a no-op | Works whichever of the two the session emits | s_sound.c:S_Start |
| D-119 | Music pause on level start | `S_Start` clears `mus_paused` without resuming the player | A paused song is resumed first | A song stuck paused is never wanted | s_sound.c:S_Start |
| D-120 | Missing sound lump | `W_GetNumForName` error | Warning at debug level; the sound is silent (channel logic and RNG draw unchanged) | Shareware and Freedoom lack some DOS sounds that DEHACKED or PWADs may still request | i_sound.c:I_GetSfxLumpNum |
| D-121 | Quit | `I_Quit` after the quit sound, waiting 105 tics | The loop continues for 1.5 s when audio is active, then exits; closing the window quits at once (no "quit?" confirmation) | Window system convention | m_menu.c:M_QuitResponse |
| D-122 | Wipes | `wipegamestate` set in `D_Display`; time keeps running (tics are caught up afterwards) | Same trigger (game state change or `forceWipe`); the clock is shifted by the wipe time so no tics are caught up ("the wipe pauses the simulation", spec 02 section 7) | Spec | d_main.c:D_Display |
| D-123 | Headless runs | Chocolate with the dummy SDL driver draws, wipes and updates the status bar | No drawing, wipes or UI: the misc RNG index (trace key `m`, informational) differs; gated keys are unaffected | Speed; contracts section 6.2 | - |
| D-124 | Timedemo output | `I_Error("timed %i gametics in %i realtics (%f fps)")`, exit status 255 | Prints the same text (one decimal) on the output stream and exits 0; frames per second is printed too when windowed | Not an error | g_game.c:G_CheckDemoStatus |
| D-125 | Config file | `default.cfg` (`name value`) | `doom-java.cfg` (`key = value`, `#` comments), per-OS data directory, lenient parsing with warnings | ADR-017, spec 07 section 4 | m_config.c |
| D-126 | Window resolution | 320x200 | Internal resolution follows the output size (spec 04 section 2); `-vanilla` keeps 320x200 | ADR-010 | - |

## W3-A: Monster AI

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-127 | `A_BrainAwake` target list | `braintargets[32]` is a fixed array; a map with more than 32 `MT_BOSSTARGET` things overflows it into other memory | growable array | no defined behaviour beyond the original's limit | p_enemy.c:A_BrainAwake |
| D-128 | `A_BrainSpit` division | `((targ->y - mo->y) / newmobj->momy) / newmobj->state->tics` crashes (SIGFPE) when `momy` or the state's tics is 0 | `DoomFatalException("A_BrainSpit: division by zero ...")` | a fatal error instead of a host crash | p_enemy.c:A_BrainSpit |
| D-129 | Boss-death dummy line | `line_t junk` on the stack with only `tag` set | one zero-filled `Line` per session with only `tag` set | the floor/door code reads only the tag for the types used | p_enemy.c:A_BossDeath, A_KeenDie |

Notes from W3-A:

- Game versions older than 1.9 (`exe_doom_1_2`, `exe_doom_1_5`) are not supported by `GameVersion`, so the conditionals
  `gameversion < exe_doom_1_5`, `<= exe_doom_1_2`, `> exe_doom_1_2` of `P_CheckMeleeRange`, `A_SargAttack` and `A_Chase`
  are resolved to their 1.9 outcome. `A_BossDeath`/`CheckBossEnd` keep the `< exe_ultimate` / `>= exe_ultimate` rule.
- **Oracle trace artefact (dangling `tracer`)**: the Arch-vile keeps `tracer` pointing at its `MT_FIRE` after the fire is
  freed. In the oracle, the freed zone block can be reused by a mobj spawned later (e.g. a bullet puff), so the trace prints
  the thinker index of that unrelated mobj, while we print `-2` (not a live thinker). The synthetic demos `f2m29a`
  (tic 1036) and `f2m29b` (tic 1201) differ only in this field of the Arch-vile (`a` hash); the simulation is otherwise
  identical. Not reproducible without emulating the zone allocator.

## W3-B: Combat and weapons

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-130 | `P_KillMobj` automap | `AM_Stop()` when the console player dies with the automap open | not done in the simulation; the presentation sees `playerState == PST_DEAD` | the automap is presentation state | p_inter.c:P_KillMobj |
| D-131 | `P_DamageMobj` Chex Quest / `exe_doom_1_5` | `gameversion == exe_chex` returns before drops; `gameversion < exe_doom_1_5` allows self-targeting | not emulated (the oldest emulated executable is 1.9, Chex is out of scope), so `source != target` always | game versions below 1.9 / Chex do not exist in `GameVersion` | p_inter.c |
| D-132 | Ammo type beyond `NUMAMMO` in `DecreaseAmmo` / `A_FireCGun` | Overflows into `maxammo[]` (emulated), and further into the player struct | `maxammo[]` overflow emulated; anything beyond is ignored / reads 0 | undefined memory | p_pspr.c:DecreaseAmmo |
| D-133 | Dangling `tracer` pointers in the trace | An Arch-vile keeps `tracer` to its removed fire; when the zone allocator reuses the block for a new `mobj_t` (e.g. a bullet puff) the oracle prints that mobj's index | `-2` (not a live thinker) | Java objects have no reusable addresses; the pointer is never dereferenced by the simulation. Shows up as an `a` hash mismatch (synthetic demos F2M29A / F2M29B) | contracts section 6.3 |

## W3-C: Map specials

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-134 (was D-C01) | `P_UpdateSpecials` button reset sound | `S_StartSound(&buttonlist[i].soundorg, sfx_swtchn)` passes the address of the pointer field as the origin, so the sound position is heap garbage (non-deterministic, practically inaudible) | no sound outside map 8; on map 8 (no distance cut-off) one `startSound(NONE)` so the misc RNG draw is kept | garbage origin cannot be encoded as a `SoundOrigins` value | `p_spec.c P_UpdateSpecials`, `s_sound.c S_AdjustSoundParams` |
| D-135 (was D-C02) | level timer | `-timer` / `timelimit` with deathmatch ends the level | not implemented (`levelTimer` always false) | `timelimit` is a user setting; the simulation takes no configuration | `p_spec.c P_SpawnSpecials/P_UpdateSpecials` |
| D-136 (was D-C03) | donut with one-sided first line / NULL back sector | vanilla reads invalid memory; Chocolate prints a warning and uses the Win98 defaults (height 0, flat 0x16) | same as Chocolate defaults (`-donut` override not supported) | no command line in the simulation | `p_spec.c DonutOverrun` |
| D-137 (was D-C04) | `EV_VerticalDoor` on a non-door `specialdata` | writes `vldoor_t.direction` over whatever struct is there (32-bit vs 64-bit layouts differ) | door: direction, platform: `wait = -1` (as Chocolate), ceiling: `speed = -1`, floor: `texture = -1` (64-bit oracle layout) | emulate the oracle build | `p_doors.c EV_VerticalDoor` |
| D-138 (was D-C05) | `P_ChangeSwitchTexture` sound origin | `buttonlist->soundorg` (first button slot, not the switch) | kept: sector origin of slot 0's front sector when slot 0 is active, else no origin | vanilla bug reproduced, not a divergence (listed for the sound author) | `p_switch.c` |

## W3-D: Session flow

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-139 | G_DoCompleted | `exe_chex` ends after map 5 and uses `chexpars[]` | not reproduced: `GameVersion` has no Chex Quest | Chex Quest is not a supported game version | g_game.c:G_DoCompleted |
| D-140 | G_DoCompleted | par time of a commercial map outside 1..33 reads unrelated memory | par time 0 (cannot happen with real maps: IDCLEV and warp stop at MAP32/MAP33) | no out-of-bounds access | g_game.c:G_DoCompleted |
| D-141 | G_DoCompleted | `StatCopy` (`-statdump`) | not implemented | `-statdump` is not supported | g_game.c:G_DoCompleted |
| D-142 | WI_drawNoState | drawing sets the static `snl_pointeron = true` | set when the NoState countdown starts (`WI_initNoState`) | drawing is presentation; the simulation must not depend on frame rate | wi_stuff.c:WI_drawNoState |
| D-143 | F_StartFinale | with no matching text screen the previous (or NULL) text stays | previous text stays, initially an empty text and `FLOOR4_8` | cannot happen for finales started by `G_DoCompleted` / `G_WorldDone` | f_finale.c:F_StartFinale |
| D-144 | D_StartTitle / endGame | state changes in the next `D_DoAdvanceDemo` | same (kept); the initial state before the first tick is `DEMOSCREEN` / `TITLEPIC` | the original also starts in the demo screen state | d_main.c:D_StartTitle |
| D-145 | G_DoSaveGame / G_DoLoadGame | save format of the original | delegated to a `SaveGameHandler` installed by the persistence layer (W4-P); without a handler the actions are recorded as unimplemented stub hits | the save format is Wave 4 | g_game.c |
| D-146 | Title-loop demos | `D_DoAdvanceDemo` + `G_DoPlayDemo` reject version 110 | demos are read through the session's `G_DoPlayDemo` which accepts 110 as 109 (D-001) | existing divergence D-001 | g_game.c:G_DoPlayDemo |

## F1: see the source file

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-147 | Reference traces `testdata/traces/title-*.trace.gz` | The oracle title-loop runs (no demo, no natural exit) were stopped from outside, so the stdio buffer was cut mid-line: the last line of each file was incomplete (e.g. `...;l=2952f3c3bfa40`) | The incomplete last line was removed from the three files (now 13726 / 26253 / 14429 complete lines) | The cut line made `TitleLoopSyncIT` report a bogus `l` divergence at the last tic and `ReferenceTracesIT` fail its key check; the simulation was correct | contracts section 6.1 |
| D-148 | `FlowSyncIT` PWADs | `exit.wad` / `secret.wad` were produced manually by `make-flow-testdata.py` and (being `*.wad`) git-ignored | `FlowWads` (doom-it tests) regenerates them from Freedoom E1M1 into `target/sync-traces/flow-wads` at test time (byte-identical to the script output) | the IT must run without manual steps and PWADs must not be committed | `testdata/flow/make-flow-testdata.py` |

## W4-P: see the source file

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-149 | Save format | `.dsg` memory dump of the structs, version string `version 109`; the WADs are not recorded | custom DJSV container (header, tagged sections, CRC-32); not compatible with DOS saves (a DOS file is refused with "bad signature") | ADR-013; saves carry more state than the original and must be checkable | g_game.c:G_DoSaveGame, p_saveg.c |
| D-150 | Extra state saved | only what the DOS game archives | also: the game and misc RNG indices, `target` / `tracer` / `soundtarget` / `attacker` (by thinker index), the order of every sector thing list and blockmap cell, the item respawn queue, body queue, player starts (deathmatch starts are mutated by `G_DeathMatchSpawnPlayer`), `validcount`, `onground`, boss brain targets and the `easy` flag of `A_BrainSpit`, the full `-fast` / Nightmare changes of the state and mobjinfo tables, `sky texture`, the `-respawn` / `-fast` / `-nomonsters` / deathmatch / net-game parameters | a loaded game must continue exactly like the running one (the save/load tests compare it with the uninterrupted run) | p_saveg.c, p_enemy.c, g_game.c |
| D-151 | Removed objects still referenced | vanilla archives only live mobjs; pointers to freed mobjs dangle and become NULL on load | objects that were removed but are still referenced (a dead target, a tracer, the sound target of a sector, `player->attacker`, the body queue) are saved as "ghost" entries and come back marked removed with no actor id | the trace and the simulation see the same stale object after a load; no dangling pointer exists in Java | p_saveg.c:P_UnArchiveThinkers |
| D-152 | `gametic` | not archived: loading keeps the running `gametic` | the saved `gametic` is restored | `A_Tracer` and the turbo check depend on `gametic & 3 / & 31`, so a load of the same file must behave the same whenever it is done | g_game.c:G_DoLoadGame |
| D-153 | Load | `G_DoLoadGame` reads the file, `G_InitNew` loads the map, then the archives are overlaid | same order; the file is fully validated (signature, version, CRC, game mode, map exists, sector/line/side/blockmap sizes) before the running game is touched | "corrupt file: clear error, never a crash" | spec 08 section 2.3 |
| D-154 | Corrupt or unusable file | `I_Error` ("Bad savegame") ends the program | a message to the player (`Couldn't load game: ...`) and the game goes on; if the failure happens after the running game was replaced the session returns to the title loop | no crash | g_game.c:G_DoLoadGame |
| D-155 | Different WADs | the save does not record them, a wrong WAD loads and misbehaves | the header records name and SHA-256 of every WAD (hash over lump names, sizes and contents, so a renamed file is the same WAD); a difference gives the HUD message `WARNING: save made with different WADs` and a log line, the load goes on. A save of another game mode (shareware vs commercial ...) is refused | spec 08 section 2.3 | - |
| D-156 | Different game version | not recorded | recorded; a difference is a warning like different WADs | - | - |
| D-157 | Save outside a level | `M_SaveGame` only offers saving in a level | the session ignores a `BTS_SAVEGAME` outside `GS_LEVEL` (nothing is written) | robustness against a stale request | g_game.c:G_DoSaveGame |
| D-158 | Automap | `ML_MAPPED` is a line flag and is archived | not archived: the renderer keeps the seen lines (`MappedLines`) outside the simulation | the simulation does not hold the flag | p_saveg.c:P_ArchiveWorld |
| D-159 | `player->message`, `cmd` | not archived | not archived (the loaded game starts without a HUD message; a save warning is shown instead) | - | - |
| D-160 | Save file write (engine) | `M_WriteFile` in place | temp file + atomic move, so a crash keeps the previous save of the slot | robustness | m_misc.c:M_WriteFile |
| D-161 | Timestamp / description | description only (24 characters) | description (24 characters) and a wall-clock timestamp in the header, taken from a `Clock` injected into the codec | spec 08 section 2.2 | - |
| D-162 | First save | - | the first save of a session hashes the WADs (about 90 ms for a 27 MB IWAD) on the tic thread; later saves take under 1 ms | simple, once per session | - |

## W4-R: see the source file

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-163 | Wall scale and columns (`precisionFixes`, not in vanilla mode) | `R_ScaleFromGlobalAngle`: 32-bit fixed point at the wall ends, linear 16.16 step, texture column from `finetangent` of an 8192-step angle | exact line geometry in `double`: scale `(r.n)/dist` and texture position `(eye-v1).T + dist*(r.T)/(r.n)` per column from the level's line (not the rounded seg vertices); wall columns step the texture with 32 fractional bits | removes wall wiggle and swimming textures at high resolution | spec 04 section 3; `BspWalls.preciseWallSetup` |
| D-164 | Vertex angles, view columns (`precisionFixes`) | `R_PointToAngle` through the 2048-entry `tantoangle` table; `viewangletox` / `xtoviewangle` on the 8192-step fine angle grid | `atan2` and exact tangent; `xtoviewangle` computed per column at full BAM precision | error of up to 1.4 pixels at 3440 columns | spec 04 section 3 |
| D-165 | Floor and ceiling spans (`precisionFixes`) | span start from a fine-angle cosine/sine, `basexscale = cos/projection` in 16.16 (a 2 to 5% error at 2560+ columns), 16.16 steps | start and steps from the view direction in `double`, 32.32 position and step in the span drawer | finer span steps | spec 04 section 3; `Planes.mapPlanePrecise` |
| D-166 | Sprites and masked walls (`precisionFixes`) | camera space from `finecosine`/`finesine` of the view angle, `FixedMul`/`FixedDiv` 32-bit | exact view angle, 64-bit screen positions and clamps, 32.32 texture steps; masked mid textures use the exact wall scale | no overflow near the camera, no sprite jitter against walls | spec 04 section 3 |
| D-167 | Texture wrap | `& 127` | real texture height (already in W2-B, now `RenderOptions.correctTextureWrap()` documented) | spec 04 section 4 |  |
| D-168 | `finetangent` index overrun (vanilla mode) | index 4096 to 8191 reads the table that follows `finetangent` in memory (`finesine` from its start); crashes our bounds check | reproduced: `fineTangentOverrun` reads `finesine[i - 4096]` | found by the timedemo of freedoom2 DEMO1 at 320x200; also safe for Hor+ | `tables.c`, `r_segs.c:R_RenderSegLoop` |
| D-169 | Square pixels (`video.aspectCorrection = false`) | n/a (320x200 always has 5:6 pixels) | projection from a `H x 4/3` 4:3 view (factor 5/6 of the corrected `H x 1.6`) so a 4:3 screen keeps a 90 degree horizontal field of view; the 2D layer keeps the uniform `H/200` scale (16:10 menus, as modern ports) | ADR-010 | spec 04 section 2 |
| D-170 | Screen size 10 on a wide frame | `R_DrawViewBorder` draws no border when the view spans the width | same, so the 3D view fills the whole frame width above the status bar (Hor+); the status bar stays 320 virtual wide with the border flat at its sides | the UI used to paint side borders over the wide view | `Ui.drawViewBorder` |
| D-171 | Interpolation | none | `doom.render.Interpolation` decorators over the views: actor position and angle, sector floor and ceiling heights, scrolling wall offsets, psprite offsets, eye height. Steps larger than any real motion (sectors 32 units, scrollers 16, psprites 48, eye height 32) are not interpolated, because the simulation does not snap the previous values of a new level or a loaded game | ADR-011; presentation only, the simulation is untouched (demos stay in sync) | spec 04 section 7 |
| D-172 | Render pipeline settings | n/a | `render.precisionFixes` and `video.aspectCorrection` are read by the render adapter every frame | spec 04 sections 2, 3 |  |

## W6-R: see the source file

| ID | Area | Original | Ours | Reason | Reference |
|----|------|----------|------|--------|-----------|
| D-173 | Loose files on the command line | `M_AddLooseFiles` (Windows drag and drop): only when every argument is an absolute Windows path (drive or UNC); the IWAD is chosen by file name (`D_IsIWADName`) | `LooseFiles`: the arguments before the first parameter, on every OS; a `.wad` is the IWAD when its header says `IWAD`; `.wad` → `-merge`, `.deh`/`.bex` → `-deh`, `.lmp` → `-playdemo`; the parameters are appended, so explicit ones win | The file association of the Windows installer and dropping a file on a launcher pass relative or Unix paths too; a renamed IWAD is still found | m_argv.c:M_AddLooseFiles |

## W0-OR: oracle notes

Notes for the orchestrator and for whoever consumes the oracle output (not game divergences).

1. **PU_LEVSPEC zero-fill (oracle deviates from stock Chocolate).** `Z_Malloc` in the patched oracle zero-fills
   blocks with tag `PU_LEVSPEC` (door, floor, ceiling, platform and light thinkers). Vanilla leaves fields such as
   `plat_t.count` or `floormove_t.texture` uninitialised until first use; the heap garbage made the `x` hash of the
   trace differ from run to run. Rule for the Java engine: these structures are created with all fields zero
   (Java default), and the `x` hash includes them from creation on.
2. **Verbose trace indices** are thinker indices (see `tools/oracle/FORMAT.md` section 1), an interpretation of
   contracts 6.6 (`a<i>`, `x<i>`).
3. **Frame request for tic 0** is satisfied by the frame of tic 1 (nothing is displayed between the two); the
   sidecar records `tic` (real) and `requested`.
4. **Trace line count** equals the demo tic count (the end-marker call produces no line, contracts 6.1).
5. **Dangling mobj pointers** (e.g. `sector.soundtarget` after the target was removed): Chocolate prints the index of
   whatever live thinker now occupies the freed memory, or -2. A Java implementation cannot reproduce address reuse;
   this is unverified for the 14 reference demos; if a divergence is traced to such a pointer, compare the verbose traces.
6. `-headless` was not needed: SDL dummy video and audio drivers work.
7. `-nosound` does not change the trace in this build (SDL2_mixer disabled), but `run.sh` does not use it.

## Accepted demo sync exceptions

The synthetic demos `F2M29A` and `F2M29B` differ from the oracle in the `a` hash only (a dangling Arch-vile `tracer`, see the W3-A and W3-B rows above). They are listed with their justification in `testdata/demos/exceptions.tsv`, which `DemoSyncIT` reads; every other demo (399 of 401, including all 14 IWAD demos) is identical to the oracle.
