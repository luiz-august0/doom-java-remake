# W3-D divergences (session flow)

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| G_DoCompleted | `exe_chex` ends after map 5 and uses `chexpars[]` | not reproduced: `GameVersion` has no Chex Quest | Chex Quest is not a supported game version | g_game.c:G_DoCompleted |
| G_DoCompleted | par time of a commercial map outside 1..33 reads unrelated memory | par time 0 (cannot happen with real maps: IDCLEV and warp stop at MAP32/MAP33) | no out-of-bounds access | g_game.c:G_DoCompleted |
| G_DoCompleted | `StatCopy` (`-statdump`) | not implemented | `-statdump` is not supported | g_game.c:G_DoCompleted |
| WI_drawNoState | drawing sets the static `snl_pointeron = true` | set when the NoState countdown starts (`WI_initNoState`) | drawing is presentation; the simulation must not depend on frame rate | wi_stuff.c:WI_drawNoState |
| F_StartFinale | with no matching text screen the previous (or NULL) text stays | previous text stays, initially an empty text and `FLOOR4_8` | cannot happen for finales started by `G_DoCompleted` / `G_WorldDone` | f_finale.c:F_StartFinale |
| D_StartTitle / endGame | state changes in the next `D_DoAdvanceDemo` | same (kept); the initial state before the first tick is `DEMOSCREEN` / `TITLEPIC` | the original also starts in the demo screen state | d_main.c:D_StartTitle |
| G_DoSaveGame / G_DoLoadGame | save format of the original | delegated to a `SaveGameHandler` installed by the persistence layer (W4-P); without a handler the actions are recorded as unimplemented stub hits | the save format is Wave 4 | g_game.c |
| Title-loop demos | `D_DoAdvanceDemo` + `G_DoPlayDemo` reject version 110 | demos are read through the session's `G_DoPlayDemo` which accepts 110 as 109 (D-001) | existing divergence D-001 | g_game.c:G_DoPlayDemo |
