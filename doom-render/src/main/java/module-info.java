/**
 * Software renderer (ADR-008): BSP walls, visplanes, sky, sprites and psprites into an indexed framebuffer of any
 * size, plus the 2D layer (a virtual 320x200 canvas), the melt wipe and PNG screenshots.
 */
module doom.render {
    requires transitive doom.math;
    requires transitive doom.wad;
    requires transitive doom.game;

    exports doom.render;
    exports doom.render.canvas;
    exports doom.render.image;
    exports doom.render.wipe;
}
