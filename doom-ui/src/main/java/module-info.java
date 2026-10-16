/**
 * User interface (spec 07): menus, status bar, HUD, automap, intermission, finale and title pages, drawn on the 2D
 * canvas from the simulation views.
 */
module doom.ui {
    requires doom.math;
    requires doom.wad;
    requires transitive doom.game;
    requires transitive doom.render;

    exports doom.ui;
}
