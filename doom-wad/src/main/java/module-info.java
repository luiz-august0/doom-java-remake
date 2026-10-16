/**
 * WAD files: reading (memory-mapped, zero-copy lumps), the lump directory (IWAD + PWADs, last one wins),
 * IWAD discovery and identification, and decoders for every lump format the game uses (spec 05).
 * Knows formats only, never game rules.
 */
module doom.wad {
    exports doom.wad;
    exports doom.wad.iwad;
    exports doom.wad.map;
    exports doom.wad.texture;
    exports doom.wad.demo;
    exports doom.wad.text;
    exports doom.wad.gfx;
    exports doom.wad.audio;
}
