# W1-E divergences

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| `V_DrawPatch` range check | `RANGECHECK` builds call `I_Error("Bad V_DrawPatch")` for a patch outside 320×200; non-RANGECHECK builds write out of bounds | `Canvas2D.drawPatch` clips silently against the real frame | patches may legitimately spill into the pillarbox (wide pictures) and the canvas is not limited to 320×200 | spec 04 §6 |
| Patch scaling | none (1:1 pixels) | virtual 320×200 scaled by `s = H/200` with nearest sampling; virtual column `v` covers physical `[px(v), px(v+1))`, `px(v) = round(W/2 + H(v-160)/200)` (half up) | resolution independent 2D layer, centred in X | spec 04 §6, ADR-010 |
| Tall patches | `topdelta` is absolute; a post can not start below row 254 | outside vanilla mode a `topdelta` not greater than the previous post's top is relative to it (DeePsea convention); `Picture.decode(.., tallPatches)` | support taller graphics in PWADs; never applied in vanilla mode | spec 05 §4 |
| Keyboard mapping | Chocolate maps SDL scancodes (US layout positions) | GLFW key tokens mapped by position in the same way (`GlfwKeys`); super keys produce nothing | same behaviour as `vanilla_keyboard_mapping`; CHAR events carry the real typed code point | `i_input.c TranslateKey` |
| Typed characters | `shiftxform` table applied to the US scancode translation | `CHAR` events deliver the layout-aware Unicode code point from GLFW | proper text entry on non-US layouts (save names, cheats) | spec 07 §3 |
| Mouse | SDL relative mouse mode with acceleration in `i_input.c` | raw (unaccelerated) motion deltas; acceleration/threshold are the engine's job | spec 07 §3 | `GLFW_RAW_MOUSE_MOTION` |
| Presentation | SDL software scaler with aspect-ratio correction | `GL_R8` index texture + palette texture, FIT/INTEGER/STRETCH with pixel aspect 1.2 or 1.0, NEAREST or sharp bilinear | ADR-009 | spec 04 §8 |
