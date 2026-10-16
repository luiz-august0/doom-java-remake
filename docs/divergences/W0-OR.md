# W0-OR: oracle notes and deviations

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
