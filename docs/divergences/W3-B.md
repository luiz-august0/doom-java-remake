# Divergences recorded by W3-B (combat and weapons)

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| `P_KillMobj` automap | `AM_Stop()` when the console player dies with the automap open | not done in the simulation; the presentation sees `playerState == PST_DEAD` | the automap is presentation state | p_inter.c:P_KillMobj |
| `P_DamageMobj` Chex Quest / `exe_doom_1_5` | `gameversion == exe_chex` returns before drops; `gameversion < exe_doom_1_5` allows self-targeting | not emulated (the oldest emulated executable is 1.9, Chex is out of scope), so `source != target` always | game versions below 1.9 / Chex do not exist in `GameVersion` | p_inter.c |
| Ammo type beyond `NUMAMMO` in `DecreaseAmmo` / `A_FireCGun` | Overflows into `maxammo[]` (emulated), and further into the player struct | `maxammo[]` overflow emulated; anything beyond is ignored / reads 0 | undefined memory | p_pspr.c:DecreaseAmmo |
| Dangling `tracer` pointers in the trace | An Arch-vile keeps `tracer` to its removed fire; when the zone allocator reuses the block for a new `mobj_t` (e.g. a bullet puff) the oracle prints that mobj's index | `-2` (not a live thinker) | Java objects have no reusable addresses; the pointer is never dereferenced by the simulation. Shows up as an `a` hash mismatch (synthetic demos F2M29A / F2M29B) | contracts section 6.3 |
