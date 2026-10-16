# Divergences recorded by W2-A (simulation core, session skeleton, trace)

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| State actions in the wrong kind of state | Chocolate calls a weapon action from `P_SetMobjState` (or a map-object action from `P_SetPsprite`) with mismatched arguments: undefined behaviour | ignored (`ActionDispatcher`) | only reachable with odd DEHACKED patches; no defined behaviour to reproduce | p_mobj.c:P_SetMobjState, p_pspr.c:P_SetPsprite |
| Intercepts overrun, third word | Chocolate writes the host address of the line / thing (`(intptr_t) d.thing`) into the overwritten variable | writes 0 | the host address is not reproducible (the oracle itself is not deterministic there); frac and isaline are emulated exactly | p_maputl.c:InterceptsOverrun |
| Intercepts / spechit arrays | Chocolate has fixed arrays of 189 intercepts and 20 spechits; going past them corrupts real memory | growable arrays (the documented overrun emulation of the first 8/128 + overrun entries is kept) | no defined behaviour beyond Chocolate's own limits | p_maputl.c, p_map.c |
| Blockmap after an intercepts overrun changed `bmapwidth`/`bmapheight` | out-of-range reads of `blocklinks`/`blockmap` | treated as empty cells | avoid raw `ArrayIndexOutOfBoundsException` (spec 02 §9) | p_maputl.c |
| `G_CheckSpot` first spawn | dereferences `players[i].mo` of players not in game (NULL) | skips null bodies | crash in the original | g_game.c:G_CheckSpot |
| Demo without end marker | `G_ReadDemoTiccmd` reads past the buffer | the end of the data acts as the end marker | undefined behaviour | g_game.c:G_ReadDemoTiccmd |
| Demo version 110 | Chocolate rejects it ("different game version") | read as 109 | `GameSession.playDemo` contract (linuxdoom demos) | g_game.c:G_DoPlayDemo |
| `G_DoNewGame` with full `GameParams` | the menu path resets netgame, deathmatch, -respawn, -fast, -nomonsters, consoleplayer; the command line sets them before `G_InitNew` | `newGame(params)` applies all parameters (the command-line path); the IDCLEV / menu path (`deferredInitNew`) resets them as the original | one API entry point for both callers | g_game.c:G_DoNewGame, d_main.c |
| Sound / misc RNG | `S_StartSound` draws `M_Random` inside the simulation | the simulation only emits `SimEvents.startSound`; the presentation draws the misc RNG | contracts §8/§11 (trace key `m` is informational; with `events == null` it stays 0) | s_sound.c:S_StartSound |
| HUD messages | `player->message` read by `HU_Ticker` | also emitted immediately through `SimEvents.playerMessage` | presentation hook | p_inter.c, st_stuff.c |
| Session skeleton | `G_DoCompleted`, intermission, finale, title loop, save/load | stubs recording `session.*` hits (`G_DoCompleted` still takes away cards/powers and enters GS_INTERMISSION) | W3-D / W4-P own them | g_game.c, d_main.c |
| `-nodeh` | Chocolate can skip the Freedoom IWAD DEHACKED lump | always applied (GameSetup has no flag for it) | contract request filed | d_main.c:LoadIwadDeh |
