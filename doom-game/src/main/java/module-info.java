/**
 * The simulation (spec 03): a vanilla-exact model of DOOM v1.9. Only {@code doom.game.api} (commands) and
 * {@code doom.game.view} (read-only state) are exported; entities and rules stay internal (ADR-004).
 */
module doom.game {
    requires transitive doom.math;
    requires transitive doom.wad;

    exports doom.game.api;
    exports doom.game.view;
}
