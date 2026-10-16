/**
 * Audio (ADR-012): channel logic with the original priority/volume/separation rules, a software mixer, and music
 * through a MUS/MIDI sequencer driving a Java port of the Nuked OPL3 emulator with the GENMIDI instruments.
 */
module doom.sound {
    requires doom.math;
    requires doom.wad;
    requires transitive doom.game;
    requires transitive doom.platform;

    exports doom.sound;
    exports doom.sound.mixer;
    exports doom.sound.music;
    exports doom.sound.opl;
}
