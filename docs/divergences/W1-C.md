# Divergences — W1-C (game definitions and DEHACKED)

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| Definition tables | C arrays in `info.c`, `d_items.c`, `sounds.c` with function pointers | Builder DSL in `doom.game.data` building flat tables; `StateAction` enum instead of function pointers; every table checked against a committed snapshot | ADR-014, ADR-022 §4 | `DefsEquivalenceTest` |
| Strings | `DEH_String` is a global hash table keyed by the original text | `GameStrings`, one per session, same semantics (`get(key)` translates the original text; `translate(text)` for lump names) | No mutable static state | `deh_str.c` |
| Sound `link` in DEHACKED | `Sound` section field `Zero 1` is a pointer-sized field: a 64-bit Chocolate Doom build rejects it with an error | Same (error `Unknown field type for 'Zero 1' (BUG)`), matching the 64-bit oracle | Bit-for-bit agreement with the oracle | `deh_sound.c`, `deh_mapping.c` |
| Sound `singularity` | Not present in Chocolate Doom's `sfxinfo_t` | Kept from linuxdoom `sounds.c` (data only; DEHACKED cannot change it) | Needed by the sound system if it wants the vanilla rule | `sounds.c` |
| DEHACKED parse errors | `I_Error` (patch from file) or silently stop parsing (IWAD lump) | `DoomFatalException("Error parsing dehacked file/lump")` unless `Options.allowError`; the changes made before the error stay applied | Same observable behaviour | `deh_main.c:DEH_LoadLump` |
| DEHACKED `Pointer` | `codeptrs[]` captured from `states[]` on the first DEHACKED load | Captured from a pristine `GameDefs.createDefault()` | Equivalent, avoids static state | `deh_ptr.c` |
| Lone CR at the very end of a patch | Chocolate Doom re-reads the CR forever | Returned once, then end of data | Chocolate bug (hang) | `deh_io.c:DEH_GetChar` |
| gen-defs output | n/a | Generated Java is hand-editable; re-running `gen-defs` overwrites hand edits | ADR-014 bootstrap | `doom-codegen` |
