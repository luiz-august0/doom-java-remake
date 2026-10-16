/**
 * Audio output through javax.sound.sampled (ADR-012).
 */
module doom.platform.javasound {
    requires doom.platform;
    requires java.desktop;

    provides doom.platform.AudioProvider with doom.platform.javasound.JavaSoundAudioProvider;
}
