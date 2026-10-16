# Oracle output formats

Formats written by the patched Chocolate Doom 3.1.1 (`tools/oracle/chocolate-trace.patch`). The per-tic
trace is defined by `docs/contracts.md` section 6; this file records how the oracle realises it, the
extra files (frame dump, scene sidecar, definition dump), and the oracle-specific conventions.

All files are UTF-8 text with LF line endings, except the raw frames. All numbers are decimal `int32`
(unsigned values such as angles are printed as their signed bit pattern), unless stated otherwise.

## 1. Trace v1 (`-trace <file>`, `-traceverbose <file>`)

Contract: `docs/contracts.md` sections 6.1-6.6. The oracle writes it from `d_net.c RunTic()` right after
`G_Ticker()` (so `D_DoAdvanceDemo()` belongs to the same tic). A file name ending in `.gz` is compressed
through `gzip -n -c` (no name or timestamp in the header, so equal text gives equal bytes with one gzip build).
The stream is closed by an `I_AtExit(..., true)` handler, so the `I_Error` exit of `-timedemo` leaves a complete file.

Header: `#doom-trace v1 iwad=<file name> demo=<NAME> version=<1.9|ultimate|final|final2>`; `demo` is the lump name
or the `.lmp` base name in upper case.

### Number of lines versus demo length

A demo of *N* tics (`oracle-util.py demo-tics`) produces exactly **N lines**, `t=0 .. N-1`.
The demo lump holds *N* ticcmds followed by the `0x80` end marker. The *(N+1)*-th `G_Ticker` call reads the end
marker, calls `G_CheckDemoStatus()` and leaves the program through `I_Error`, so it produces no line
(contracts 6.1). `t=0` is the call in which `G_DoPlayDemo` loads the level; the first `P_Ticker` runs in the same call,
so line `t=0` already has `lt=1`, and the last line has `lt=N`.

All 14 IWAD demos are single-level recordings: every line has `gs=0` (no intermission or finale).

### Conventions of the oracle (where the contract leaves room)

* **Verbose lines** `  a<i>=...` / `  x<i>=...`: `<i>` is the **thinker index** of §6.3 (position among all live thinkers,
  mobjs and specials counted together), so a `target=17` reference in some `a` line points at the line `a17`.
  The `a` and `x` indices therefore interleave and are not consecutive within one series.
  The verbose file contains the normal line of **every** tic; the object lines appear only for
  `-tracefrom <tic>` <= t <= `-traceto <tic>` (default: all tics). `-trace` and `-traceverbose` may be used together.
* Pointers: `-1` NULL, `-2` a pointer that is not in the live thinker list. The oracle never dereferences them. A
  dangling pointer to a freed mobj whose memory has meanwhile been reused by a new thinker reads as that thinker's index
  (this is vanilla undefined behaviour and has not been observed to matter).
* Arrays (`s`, `l`, `d`) cover `numsectors`, `numlines`, `numsides` of the level data **currently in memory**: before the
  first level they are empty, during an intermission or finale they still hold the last level.
* `gameskill` is printed 0-based. `weaponowned`/`cards` become bit masks, `powers[]` are the six `int` values.
* A thinker with a NULL function that is in neither `activeceilings[]` nor `activeplats[]` is printed with code `0`.
  Any other non-mobj thinker whose function is not one of the eight known ones is also code `0` (does not occur in vanilla).
* **Uninitialised fields.** Vanilla allocates the special thinkers (doors, floors, ceilings, platforms, lights) with
  `Z_Malloc(..., PU_LEVSPEC, 0)` and leaves some fields uninitialised until the thinker reaches a state that sets
  them (for example `plat_t.count`, `floormove_t.texture`/`newspecial`). The raw values are heap garbage, so the
  oracle patch **zero-fills every `PU_LEVSPEC` block** (`z_zone.c`). Our engine must therefore create these
  structures zeroed (Java default). No vanilla code reads the garbage (otherwise demos could not sync).
* `m` (misc RNG index) is not gated. It depends on the sound and wipe code paths (see `README.md`).

## 2. Frame dump (`-dumpframe <tic>[,<tic>...] <dir>`)

`<dir>` is created. For each distinct real tic *T* the oracle writes

* `frame-<T as %06d>.raw`: `I_VideoBuffer`, 320 x 200 palette indices, row-major, 64000 bytes (full screen including
  the status bar, the HUD message and the menu if open);
* `frame-<T as %06d>.scene`: the scene sidecar (section 3).

Which frame is written: after the `D_Display()` call in `D_RunFrame()` (before `wipe_EndScreen`/`I_FinishUpdate`) the
oracle checks whether the last completed `G_Ticker` call index is >= the next requested tic. If so it writes that frame
and consumes every requested tic <= *T*. Consequences:

* **Frames of the melt wipe are never dumped** (they come from the early-return branch of `D_RunFrame`, which does not
  call `D_Display`). The frame produced by the `D_Display` call that *starts* a wipe is a normal complete frame of the
  new screen and can be dumped.
* In `-timedemo` (`singletics`) exactly one tic runs per `D_RunFrame`, so *T* = *N* for any requested *N* >= 1. Tic 0 is run
  before the main loop and tic 1 runs before the first display, therefore a request for tic 0 yields **tic 1**
  (`requested 0` in the sidecar); the file name always uses the real tic.
* A tic that is never displayed (menu/pause screens) is replaced by the next displayed one.

The palette is not stored in the raw file: use the sidecar's `palette` line with the `PLAYPAL` lump of the IWAD
(`oracle-util.py png <wad> <frame.raw> <out.png> <frame.scene>` does this). `usegamma` is pinned to 0.

## 3. Scene sidecar (`frame-<T>.scene`)

Line-oriented, fields separated by single spaces, one record per line, in this order:

```
#doom-scene v1 iwad=<file> demo=<NAME> version=<v>
tic <T> requested <N> gametic <gametic> gamestate <gs> automap <0|1> leveltime <lt>
raw <file name> 320 200
view <viewx> <viewy> <viewz> <viewangle> <extralight> <fixedcolormap> <displayplayer> <consoleplayer>
window <viewwindowx> <viewwindowy> <scaledviewwidth> <viewheight> <detailshift> <screenblocks> <usegamma>
palette <PLAYPAL index in use after the frame>
fuzzpos <fuzzpos at the start of D_Display, i.e. before the view was rendered>
sky <skytexture> <skyflatnum>
powers <powers[0]> ... <powers[5]>                  (of the display player)
psprite weapon <sprite> <frame> <sx> <sy>           (-1 -1 when the psprite has no state; frame may carry FF_FULLBRIGHT 0x8000)
psprite flash <sprite> <frame> <sx> <sy>
texturetranslation <numtextures> <value> ...        (one line)
flattranslation <numflats> <value> ...              (one line)
sectors <numsectors>
S <i> <floorheight> <ceilingheight> <floorpic> <ceilingpic> <lightlevel>      (numsectors lines)
sides <numsides>
D <i> <textureoffset> <rowoffset> <toptexture> <bottomtexture> <midtexture>   (numsides lines)
things <count>
T <sector> <x> <y> <z> <angle> <sprite> <frame> <flags> <player>              (count lines)
end
```

* `view*` values are those of the last `R_RenderPlayerView` (`R_SetupFrame`); when `automap` is 1 or the game state is
  not a level they are stale. `fixedcolormap` is the display player's `fixedcolormap` int (0 = none, 32 = invulnerability).
* `T` lines are written sector by sector (ascending sector index) following each sector's `thinglist` in **`snext` order**.
  `player` is the player number of `mo->player`, or -1; the renderer must not draw the display player's own mobj.
  `frame` is `mo->frame` (with the full-bright bit), `sprite` is `mo->sprite`.
* Sector `lightlevel`, pics, side offsets and textures are the live values at the time of the frame
  (they change with doors, lights, switches and scrolling walls).

## 4. Definition dump (`-dumpdefs <file>`)

Written once, after the IWAD, `-deh` and PWAD DEHACKED lumps were applied and before the game starts (so it shows exactly
what the simulation uses). Line-oriented; every section starts with a line `<section> <count>`; entries are
`key=value` lists so new keys can be added without breaking readers.

```
#doom-defs v1 iwad=<file> demo=<NAME> version=<v>
nodeh <0|1>                                           (1 when -nodeh was given)
sprites <NUMSPRITES>
sprite <i> <FOUR>                                     (sprnames[i])
states <NUMSTATES>
state <i> sprite=<n> frame=<n> tics=<n> action=<A_Name|NULL> next=<n> misc1=<n> misc2=<n>
mobjinfo <NUMMOBJTYPES>
mobj <i> doomednum= spawnstate= spawnhealth= seestate= seesound= reactiontime= attacksound= painstate= painchance=
         painsound= meleestate= missilestate= deathstate= xdeathstate= deathsound= speed= radius= height= mass= damage=
         activesound= flags= raisestate=             (all on one line)
weaponinfo <NUMWEAPONS>
weapon <i> ammo=<n> upstate= downstate= readystate= atkstate= flashstate=
ammo <NUMAMMO>
ammo <i> name=<clip|shell|cell|misl> max=<maxammo[i]> clip=<clipammo[i]>
sounds <NUMSFX>
sfx <i> name=<lump name without DS> priority= link=<index or -1> pitch= volume=
music <NUMMUSIC>
mus <i> name=<lump name without D_>
misc
<name> <value>                                        (16 lines: initial_health, initial_bullets, max_health, max_armor,
                                                       green_armor_class, blue_armor_class, max_soulsphere, soulsphere_health,
                                                       megasphere_health, god_mode_health, idfa_armor, idfa_armor_class,
                                                       idkfa_armor, idkfa_armor_class, bfg_cells_per_shot, species_infighting)
cheats 17
cheat "<name>" len=<n> params=<n> seq=<hex bytes> text="<escaped>"
strings <count>
string "<original>" -> "<replacement>"                 (only strings replaced by DEHACKED, sorted by original text)
end
```

* `action` is the name of the `A_*` function (74 distinct functions). A pointer that is not in the table prints `UNKNOWN`
  (cannot happen in a vanilla function set).
* `\\`, `\"`, `\n` and `\xNN` escapes are used in quoted strings.
* The cheat `seq` is the raw byte sequence: Freedoom's DEHACKED writes `idclip` into "No Clipping 1" **with a NUL in the
  middle** (`seq=6964636c6970006f7064`), a Chocolate quirk that the engine must reproduce when it does the same.
* `doom1` has no DEHACKED lump (`strings 0`, vanilla tables). The Freedoom IWADs show their DEHACKED changes
  (modified `Light1/Light2` tics, full-bright attack frames, renamed monsters and ~230 replaced strings).

## 5. Exit status

`-timedemo` ends through `I_Error("timed N gametics in M realtics")`: exit status 255 means success.
`run.sh` maps 255 to 0.
