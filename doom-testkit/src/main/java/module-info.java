/**
 * Test support used by tests and tools: IWAD locator ({@code DOOM_IWAD_DIR}), trace format v1 codec and comparison,
 * oracle (Chocolate Doom) runner and the synthetic demo generator. Depends only on doom.wad (ADR-022).
 */
module doom.testkit {
    requires transitive doom.wad;

    exports doom.testkit;
    exports doom.testkit.trace;
    exports doom.testkit.oracle;
    exports doom.testkit.demo;
}
