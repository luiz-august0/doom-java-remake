# W3-C divergences (map specials)

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| D-C01 `P_UpdateSpecials` button reset sound | `S_StartSound(&buttonlist[i].soundorg, sfx_swtchn)` passes the address of the pointer field as the origin, so the sound position is heap garbage (non-deterministic, practically inaudible) | no sound outside map 8; on map 8 (no distance cut-off) one `startSound(NONE)` so the misc RNG draw is kept | garbage origin cannot be encoded as a `SoundOrigins` value | `p_spec.c P_UpdateSpecials`, `s_sound.c S_AdjustSoundParams` |
| D-C02 level timer | `-timer` / `timelimit` with deathmatch ends the level | not implemented (`levelTimer` always false) | `timelimit` is a user setting; the simulation takes no configuration | `p_spec.c P_SpawnSpecials/P_UpdateSpecials` |
| D-C03 donut with one-sided first line / NULL back sector | vanilla reads invalid memory; Chocolate prints a warning and uses the Win98 defaults (height 0, flat 0x16) | same as Chocolate defaults (`-donut` override not supported) | no command line in the simulation | `p_spec.c DonutOverrun` |
| D-C04 `EV_VerticalDoor` on a non-door `specialdata` | writes `vldoor_t.direction` over whatever struct is there (32-bit vs 64-bit layouts differ) | door: direction, platform: `wait = -1` (as Chocolate), ceiling: `speed = -1`, floor: `texture = -1` (64-bit oracle layout) | emulate the oracle build | `p_doors.c EV_VerticalDoor` |
| D-C05 `P_ChangeSwitchTexture` sound origin | `buttonlist->soundorg` (first button slot, not the switch) | kept: sector origin of slot 0's front sector when slot 0 is active, else no origin | vanilla bug reproduced, not a divergence (listed for the sound author) | `p_switch.c` |
