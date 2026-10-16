# Map specials (W3-C) — notes

Package `doom.game.mapspecial`, wired by `SpecialsFactory` (`MapSpecialsImpl`). One class per original file:
`Sectors` (p_spec.c helpers + `T_MovePlane`), `Doors`, `Floors` (floors, stairs, donut), `Ceilings`, `Plats`, `Lights`,
`Switches` (switch list, buttons); triggers, teleport, `P_SpawnSpecials`, `P_UpdateSpecials`, animations are in
`MapSpecialsImpl`. Thinkers are inner classes extending `SpecialThinker` (trace codes of contracts §6.5).

- `SpecialTypes.Door/Floor/Ceiling/Plat/Stair` hold the `p_spec.h` enum values; `doFloor(line, int)` /
  `doDoor(line, int)` take them (boss deaths: `Floor.LOWER_FLOOR_TO_LOWEST`, `Floor.RAISE_FLOOR`, `Door.OPEN`...).
- `crush` of a floor is an int: stairs leave 10 in it (e6y), which crushes things but is not `== true`.
- Stasis ceilings/platforms: `hasNullFunction()` is true; trace codes 102/104.
- Limits: MAXPLATS / MAXBUTTONS / MAXLINEANIMS throw `DoomFatalException`; a 31st ceiling is untracked.
- Translation tables (`textureTranslation`, `flatTranslation`) are session state, identity after `init()`.
- Unit tests: `doom.game.world.MapSpecialsTest` (synthetic two-room map).
