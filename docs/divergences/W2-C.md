# W2-C divergences (doom-ui)

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| Menu key bindings | `key_menu_*` are configurable | fixed defaults (Esc, arrows, Enter, Backspace, y/n, F1-F11, `-`/`=`); automap keys fixed (Tab, arrows, `-`/`=`, 0, f, g, m, c) | the engine does not expose menu rebinding in v1 | m_menu.c, am_map.c |
| Save description typing | `ev->data1` (shift ignored) unless `vanilla_keyboard_mapping` is off | `Ui.charTyped` delivers the layout-aware code point (upper-cased, only characters the STCFN font has); with `setCharEvents(false)` the key code is used like vanilla | proper text entry on non-US layouts | m_menu.c `M_Responder` |
| Joystick menu input | `ev_joystick` data with `joywait` | `Ui.padButton` maps A/B/START/D-pad to Enter/Back/Esc/arrows (y/n inside questions, Enter/Esc inside text entry) | GLFW gamepad API instead of DOS joystick | m_menu.c |
| Mouse menu input | `mousewait < I_GetTime()` and Doom mouse units (y up) | same thresholds (30 counts, 5 and 15 tic waits) on raw counts (y down) | raw mouse motion | m_menu.c |
| Menu help pages | `V_DrawPatchDirect(0,0,...)` on a 320x200 screen | `drawPatchCentred` over a cleared frame (black bars, or the border flat) | widescreen pillarbox | spec 04 §6 |
| Extended options | none | "Extended Options" entry (9th row of Options, text) listing engine supplied pages; hidden in vanilla mode or when the engine supplies none | spec 07 §2 | - |
| Status bar redraw | dirty-rectangle updates through `st_backing_screen` | the whole bar is redrawn each frame; sides filled with the border flat (`FLOOR7_2` / `GRNROCK`) or a wide STBAR | no backing screen, resolution independent | spec 04 §6 |
| Face logic | `attacker`/`mo` pointers | `attacker` actor id must still exist (else the "being attacked" branch is skipped); `plyr->attackdown` comes from a hook (`Ui.Builder.attackDown`) until `PlayerView` exposes it | views are index based | st_stuff.c `ST_updateFaceWidget` |
| Full-screen HUD | only messages at view size 11 | optional minimal HUD (health, armor, ammo, keys) anchored to screen corners or the 4:3 area (`UiSettings.fullscreenHud/hudAlign`); off in vanilla mode | spec 04 §6 | - |
| View border | `R_DrawViewBorder` on a 320x200 back screen | `Ui.drawViewBorder` tiles the flat in the view border and in the pillarbox bars above the status bar, bezels at the original virtual positions | widescreen | r_draw.c |
| Automap resolution | 320x168 `fb` in the 8-bit screen | native W x (H - status bar); lines `max(1, round(s * 0.6))` px thick (`UiConfig.automapLineScale`); window is interpolated between tics (centre, scale, arrow, things); fixed-point map maths unchanged | spec 04 §6, 07 §2 | am_map.c |
| Automap `iddt` | `cht_CheckCheat` with the DEHACKED-able sequence, event-based | built-in matcher for `iddt` (not dehackable), only while the automap is open and not in deathmatch | sequence is not in `DefsView` | am_map.c `AM_Responder` |
| Automap marks | `V_DrawPatch` of the AMMNUM patch | integer pixel scaled blit at native resolution | native drawing | am_map.c `AM_drawMarks` |
| `ML_MAPPED` | line flag set by the renderer | `MappedLines` supplied by the engine from the renderer | the view does not carry it | contracts WorldView |
| Chat | `HU_Responder`, `w_chat`, chat macros | not implemented (no netcode in v1) | contracts non-goal | hu_stuff.c |
| Bunny scroll | `F_DrawPatchCol` column copy into the 320 column buffer | two patches drawn at `-scrolled` and `320-scrolled`, bars repainted black; the pistol shot of the "THE END" stage is played from `Ui.tick` instead of the drawer | same picture, sound tied to tics not frames | f_finale.c `F_BunnyScroll` |
| Wipe | n/a | not part of the UI | owned by doom.render.wipe | - |
| Quit | `I_WaitVBL(105)` after the sound | `UiActions.quit()` after starting the sound; the engine may wait | engine owns the process | m_menu.c `M_QuitResponse` |
| Quit messages | literals in `dstrings.c` | literals (index 0 uses `QUITMSG` after DEHACKED), `DOSY` from the strings | `DefsView` has no translate | dstrings.c |
