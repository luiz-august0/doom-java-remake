package doom.sound;

import doom.game.api.MiscRandom;
import doom.game.api.SimEvents;
import doom.game.api.SoundOrigins;
import doom.game.view.ActorView;
import doom.game.view.DefsView;
import doom.game.view.GameView;
import doom.game.view.MusicId;
import doom.game.view.SoundId;
import doom.math.Angle;
import doom.math.DoomFatalException;
import doom.math.Fixed;
import doom.math.Tables;
import doom.math.Trig;
import doom.math.annotation.VanillaRef;
import doom.sound.mixer.SfxSample;
import doom.wad.audio.MusicScore;
import doom.wad.iwad.GameMode;

/**
 * The channel logic of {@code s_sound.c} (spec 06 section 1), running on the main thread.
 *
 * <p>It receives the sound part of {@link SimEvents} synchronously (so that the misc RNG is consumed at the original
 * call points, contracts section 8 and 11), keeps the channel table with the original priority rules, and sends
 * commands to a {@link SoundBackend}. With a {@link NullSoundBackend} nothing is audible but the logic, including the
 * RNG use, is the same, which is what headless demo runs need.
 *
 * <p>Volumes follow the original: the menu levels 0..15 become {@code level * 8} (0..120); a started sound gets a
 * volume 0..127 that already contains the SFX volume, and a separation 0..254 (128 = centre).
 *
 * <p>Usage: construct, {@link #bind} once the session exists, forward the {@link SimEvents} to this object, call
 * {@link #updateSounds()} once per frame. No allocation after construction.
 */
public final class SoundSystem implements SimEvents {

    private static final System.Logger LOG = System.getLogger(SoundSystem.class.getName());

    /** {@code S_CLIPPING_DIST}: when to clip out sounds. */
    static final int CLIPPING_DIST = 1200 * Fixed.FRACUNIT;
    /** {@code S_CLOSE_DIST}: distance at which sounds are at full volume. */
    static final int CLOSE_DIST = 200 * Fixed.FRACUNIT;
    /** {@code S_ATTENUATOR}: the range over which sound attenuates. */
    static final int ATTENUATOR = (CLIPPING_DIST - CLOSE_DIST) >> Fixed.FRACBITS;
    /** {@code S_STEREO_SWING}. */
    static final int STEREO_SWING = 96 * Fixed.FRACUNIT;
    /** {@code NORM_SEP}: centred. */
    public static final int NORM_SEP = 128;
    /** {@code NORM_PITCH}. */
    public static final int NORM_PITCH = 128;
    /** Most channels supported (the mixer's limit). */
    public static final int MAX_CHANNELS = 32;

    /**
     * Start-up settings.
     *
     * @param channels       number of sound channels, 1..32 (vanilla default 8)
     * @param sfxVolume      0..15
     * @param musicVolume    0..15
     * @param pitchVariation pass the randomised pitch to the mixer (vanilla does not: {@code snd_pitchshift} is off);
     *                       the random numbers are drawn either way
     */
    public record Settings(int channels, int sfxVolume, int musicVolume, boolean pitchVariation) {

        public static Settings defaults() {
            return new Settings(8, 8, 8, false);
        }
    }

    private final SoundBackend backend;
    private final SoundData data;
    private final boolean pitchVariation;
    private final int channelCount;

    // channel_t[]
    private final boolean[] inUse;
    private final int[] chanSfx;
    private final int[] chanOrigin;
    private final int[] chanPitch;

    private final SfxSample[] sampleCache = new SfxSample[SoundId.COUNT];
    private final boolean[] sampleLoaded = new boolean[SoundId.COUNT];

    private int sfxLevel;
    /** {@code snd_SfxVolume}: sfx level * 8. */
    private int snd_SfxVolume;
    private int musicLevel;
    private boolean musPaused;
    /** {@code mus_playing}: the music number, or {@link MusicId#NONE}. */
    private int musPlaying = MusicId.NONE;

    private GameView view;
    private MiscRandom misc;

    public SoundSystem(SoundBackend backend, SoundData data, Settings settings) {
        this.backend = backend;
        this.data = data;
        this.pitchVariation = settings.pitchVariation();
        this.channelCount = Math.max(1, Math.min(MAX_CHANNELS, settings.channels()));
        this.inUse = new boolean[channelCount];
        this.chanSfx = new int[channelCount];
        this.chanOrigin = new int[channelCount];
        this.chanPitch = new int[channelCount];
        setSfxVolume(settings.sfxVolume());
        setMusicVolume(settings.musicVolume());
    }

    /** Connects the system to a session. Events that arrive before this call are ignored. */
    public void bind(GameView gameView, MiscRandom miscRandom) {
        this.view = gameView;
        this.misc = miscRandom;
    }

    // ------------------------------------------------------------------ volumes

    /** {@code S_SetSfxVolume(level * 8)}; {@code level} is the menu value 0..15. */
    public void setSfxVolume(int level) {
        sfxLevel = Math.max(0, Math.min(15, level));
        snd_SfxVolume = sfxLevel * 8;
    }

    public int sfxVolume() {
        return sfxLevel;
    }

    /** {@code S_SetMusicVolume(level * 8)}; {@code level} is the menu value 0..15. */
    public void setMusicVolume(int level) {
        musicLevel = Math.max(0, Math.min(15, level));
        backend.setMusicVolume(musicLevel);
    }

    public int musicVolume() {
        return musicLevel;
    }

    // ------------------------------------------------------------------ channels

    public int channelCount() {
        return channelCount;
    }

    /** Whether channel {@code cnum} holds a sound ({@code channels[cnum].sfxinfo != NULL}). */
    public boolean channelInUse(int cnum) {
        return inUse[cnum];
    }

    /** Sound number on a channel (valid when in use). */
    public int channelSfx(int cnum) {
        return chanSfx[cnum];
    }

    /** Origin code on a channel (valid when in use). */
    public int channelOrigin(int cnum) {
        return chanOrigin[cnum];
    }

    /** Pitch chosen for the sound on a channel (valid when in use). */
    public int channelPitch(int cnum) {
        return chanPitch[cnum];
    }

    /** The music number now playing, or {@link MusicId#NONE}. */
    public int musicPlaying() {
        return musPlaying;
    }

    public boolean musicPaused() {
        return musPaused;
    }

    @VanillaRef("s_sound.c:S_StopChannel")
    private void stopChannel(int cnum) {
        if (inUse[cnum]) {
            if (backend.isPlaying(cnum)) {
                backend.stopChannel(cnum);
            }
            inUse[cnum] = false;
            chanOrigin[cnum] = SoundOrigins.NONE;
        }
    }

    /** Stops every sound effect ({@code S_Start}'s first loop, also used when leaving the game). */
    public void stopAllSounds() {
        for (int cnum = 0; cnum < channelCount; cnum++) {
            if (inUse[cnum]) {
                stopChannel(cnum);
            }
        }
        backend.stopAllSfx();
    }

    /**
     * {@code S_StopSound(origin)}: stops the first channel playing for {@code origin}. As in the original, a "no
     * origin" request matches the first channel that has no origin.
     */
    @VanillaRef("s_sound.c:S_StopSound")
    public void stopSoundFor(int origin) {
        for (int cnum = 0; cnum < channelCount; cnum++) {
            if (inUse[cnum] && chanOrigin[cnum] == origin) {
                stopChannel(cnum);
                break;
            }
        }
    }

    /** {@code S_GetChannel}: a free or stealable channel for the sound, or -1. */
    @VanillaRef("s_sound.c:S_GetChannel")
    private int getChannel(int origin, int sfx) {
        DefsView defs = view.defs();
        int cnum;
        for (cnum = 0; cnum < channelCount; cnum++) {
            if (!inUse[cnum]) {
                break;
            } else if (origin != SoundOrigins.NONE && chanOrigin[cnum] == origin) {
                stopChannel(cnum);
                break;
            }
        }

        if (cnum == channelCount) {
            // Look for lower priority.
            int priority = defs.sfxPriority(sfx);
            for (cnum = 0; cnum < channelCount; cnum++) {
                if (defs.sfxPriority(chanSfx[cnum]) >= priority) {
                    break;
                }
            }
            if (cnum == channelCount) {
                return -1; // no lower priority: the new sound is dropped
            }
            stopChannel(cnum);
        }

        inUse[cnum] = true;
        chanSfx[cnum] = sfx;
        chanOrigin[cnum] = origin;
        return cnum;
    }

    // ------------------------------------------------------------------ audibility

    /**
     * {@code S_AdjustSoundParams}: volume and separation of a sound as heard from {@code listener}.
     *
     * @return whether the sound is audible; {@link #adjVolume} and {@link #adjSeparation} hold the results
     */
    @VanillaRef("s_sound.c:S_AdjustSoundParams")
    private boolean adjustSoundParams(int listenerX, int listenerY, int listenerAngle, int sourceX, int sourceY) {
        // calculate the distance to sound origin and clip it if necessary
        int adx = Math.abs(listenerX - sourceX);
        int ady = Math.abs(listenerY - sourceY);

        // From _GG1_ p.428. Approximate euclidean distance, fast.
        int approxDist = adx + ady - ((adx < ady ? adx : ady) >> 1);

        int gamemap = view.map();
        if (gamemap != 8 && approxDist > CLIPPING_DIST) {
            return false;
        }

        // angle of source to listener
        int angle = Trig.pointToAngle2(listenerX, listenerY, sourceX, sourceY);
        if (Angle.gt(angle, listenerAngle)) {
            angle = angle - listenerAngle;
        } else {
            angle = angle + (0xffffffff - listenerAngle);
        }
        angle >>>= Angle.ANGLETOFINESHIFT;

        // stereo separation
        adjSeparation = 128 - (Fixed.mul(STEREO_SWING, Tables.fineSine(angle)) >> Fixed.FRACBITS);

        // volume calculation
        if (approxDist < CLOSE_DIST) {
            adjVolume = snd_SfxVolume;
        } else if (gamemap == 8) {
            if (approxDist > CLIPPING_DIST) {
                approxDist = CLIPPING_DIST;
            }
            adjVolume = 15 + ((snd_SfxVolume - 15) * ((CLIPPING_DIST - approxDist) >> Fixed.FRACBITS)) / ATTENUATOR;
        } else {
            // distance effect
            adjVolume = (snd_SfxVolume * ((CLIPPING_DIST - approxDist) >> Fixed.FRACBITS)) / ATTENUATOR;
        }
        return adjVolume > 0;
    }

    // results of adjustSoundParams (the C code uses out parameters)
    private int adjVolume;
    private int adjSeparation;

    private int originX(int origin) {
        if (SoundOrigins.isSector(origin)) {
            return view.world().sectorSoundOriginX(SoundOrigins.sectorOf(origin));
        }
        return view.actors().x(origin);
    }

    private int originY(int origin) {
        if (SoundOrigins.isSector(origin)) {
            return view.world().sectorSoundOriginY(SoundOrigins.sectorOf(origin));
        }
        return view.actors().y(origin);
    }

    /** Actor id of the console player's body (the listener), or -1. */
    private int listenerActor() {
        return view.player(view.consolePlayer()).actor();
    }

    private boolean isLive(int origin) {
        return SoundOrigins.isSector(origin) || (origin >= 0 && view.actors().exists(origin));
    }

    // ------------------------------------------------------------------ SimEvents

    /** {@code S_StartSound(origin, sfx)}. */
    @Override
    @VanillaRef("s_sound.c:S_StartSound")
    public void startSound(int origin, int sfx) {
        if (view == null) {
            return;
        }
        // check for bogus sound #
        if (sfx < 1 || sfx >= SoundId.COUNT) {
            throw new DoomFatalException("Bad sfx #: " + sfx);
        }
        DefsView defs = view.defs();
        int volume = snd_SfxVolume;

        // Initialize sound parameters
        int pitch = NORM_PITCH;
        int link = defs.sfxLink(sfx);
        if (link >= 0) {
            volume += defs.sfxLinkVolume(sfx);
            pitch = defs.sfxLinkPitch(sfx);
            if (volume < 1) {
                return;
            }
            if (volume > snd_SfxVolume) {
                volume = snd_SfxVolume;
            }
        }

        // Check to see if it is audible, and if not, modify the params
        int sep;
        int listener = listenerActor();
        if (origin != SoundOrigins.NONE && origin != listener && listener >= 0) {
            ActorView actors = view.actors();
            int lx = actors.x(listener);
            int ly = actors.y(listener);
            boolean rc = adjustSoundParams(lx, ly, actors.angle(listener), originX(origin), originY(origin));
            volume = adjVolume;
            sep = adjSeparation;
            if (originX(origin) == lx && originY(origin) == ly) {
                sep = NORM_SEP;
            }
            if (!rc) {
                return;
            }
        } else {
            sep = NORM_SEP;
        }

        // hacks to vary the sfx pitches
        if (sfx >= SoundId.SAWUP && sfx <= SoundId.SAWHIT) {
            pitch += 8 - (misc.next() & 15);
        } else if (sfx != SoundId.ITEMUP && sfx != SoundId.TINK) {
            pitch += 16 - (misc.next() & 31);
        }
        pitch = clamp(pitch);

        // kill old sound
        stopSoundFor(origin);

        // try to find a channel
        int cnum = getChannel(origin, sfx);
        if (cnum < 0) {
            return;
        }

        chanPitch[cnum] = pitch;
        backend.startSfx(cnum, sample(sfx), volume, sep, pitchVariation ? pitch : NORM_PITCH);
    }

    private SfxSample sample(int sfx) {
        if (!sampleLoaded[sfx]) {
            sampleCache[sfx] = data.sfx(view.defs().sfxName(sfx));
            sampleLoaded[sfx] = true;
        }
        return sampleCache[sfx];
    }

    /** Drops cached samples (for example after the sound definitions changed). */
    public void clearSampleCache() {
        java.util.Arrays.fill(sampleLoaded, false);
        java.util.Arrays.fill(sampleCache, null);
    }

    private static int clamp(int x) {
        return x < 0 ? 0 : Math.min(x, 255);
    }

    @Override
    public void stopSound(int origin) {
        if (view != null) {
            stopSoundFor(origin);
        }
    }

    @Override
    public void changeMusic(int music, boolean looping) {
        if (view != null) {
            changeMusicInternal(music, looping);
        }
    }

    @Override
    public void pauseSound(boolean paused) {
        if (paused) {
            pauseMusic();
        } else {
            resumeMusic();
        }
    }

    /** {@code G_DoLoadLevel} reached {@code P_SetupLevel}'s {@code S_Start}: stop sounds, start the level's music. */
    @Override
    public void levelLoaded() {
        if (view != null) {
            start();
        }
    }

    // ------------------------------------------------------------------ music

    /** {@code S_Start}: kills all playing sounds and starts the music of the current level. */
    @VanillaRef("s_sound.c:S_Start")
    public void start() {
        for (int cnum = 0; cnum < channelCount; cnum++) {
            if (inUse[cnum]) {
                stopChannel(cnum);
            }
        }
        // start new music for the level
        if (musPaused) {
            musPaused = false;
            if (musPlaying != MusicId.NONE) {
                backend.resumeMusic();
            }
        }
        changeMusicInternal(levelMusic(view.mode(), view.episode(), view.map()), true);
    }

    /** The song {@code S_Start} picks for a level. */
    static int levelMusic(GameMode mode, int episode, int map) {
        if (mode == GameMode.COMMERCIAL) {
            return MusicId.RUNNIN + map - 1;
        }
        if (episode < 4) {
            return MusicId.E1M1 + (episode - 1) * 9 + map - 1;
        }
        // Song - Who? - Where?
        final int[] spmus = {
            MusicId.E3M4, // American     e4m1
            MusicId.E3M2, // Romero       e4m2
            MusicId.E3M3, // Shawn        e4m3
            MusicId.E1M5, // American     e4m4
            MusicId.E2M7, // Tim          e4m5
            MusicId.E2M4, // Romero       e4m6
            MusicId.E2M6, // J.Anderson   e4m7 CHIRON.WAD
            MusicId.E2M5, // Shawn        e4m8
            MusicId.E1M9, // Tim          e4m9
        };
        return spmus[Math.floorMod(map - 1, spmus.length)];
    }

    /** {@code S_StartMusic}: plays a song once. */
    public void startMusic(int musicId) {
        changeMusic(musicId, false);
    }

    @VanillaRef("s_sound.c:S_ChangeMusic")
    private void changeMusicInternal(int musicnum, boolean looping) {
        DefsView defs = view.defs();
        // The Doom IWAD has two versions of the intro music: d_intro and d_introa. The latter is for OPL playback.
        if (musicnum == MusicId.INTRO && data.hasMusic(defs.musicName(MusicId.INTROA))) {
            musicnum = MusicId.INTROA;
        }
        if (musicnum <= MusicId.NONE || musicnum >= MusicId.COUNT) {
            throw new DoomFatalException("Bad music number " + musicnum);
        }
        if (musPlaying == musicnum) {
            return;
        }

        // shutdown old music
        stopMusic();

        MusicScore score = data.music(defs.musicName(musicnum));
        if (score == null) {
            return; // missing or undecodable song: logged by the data source; play nothing
        }
        backend.playMusic(score, looping);
        musPlaying = musicnum;
    }

    /** {@code S_StopMusic}. */
    @VanillaRef("s_sound.c:S_StopMusic")
    public void stopMusic() {
        if (musPlaying != MusicId.NONE) {
            if (musPaused) {
                backend.resumeMusic();
                musPaused = false;
            }
            backend.stopMusic();
            musPlaying = MusicId.NONE;
        }
    }

    /** {@code S_PauseSound}. */
    public void pauseMusic() {
        if (musPlaying != MusicId.NONE && !musPaused) {
            backend.pauseMusic();
            musPaused = true;
        }
    }

    /** {@code S_ResumeSound}. */
    public void resumeMusic() {
        if (musPlaying != MusicId.NONE && musPaused) {
            backend.resumeMusic();
            musPaused = false;
        }
    }

    // ------------------------------------------------------------------ per-frame update

    /** {@code S_UpdateSounds(players[consoleplayer].mo)}. */
    public void updateSounds() {
        if (view == null) {
            return;
        }
        updateSounds(listenerActor());
    }

    /**
     * {@code S_UpdateSounds(listener)}: recomputes volume and separation of channels with a moving origin and frees
     * the channels whose sound has finished.
     *
     * @param listener actor id of the listener, or -1 for none (positional sounds are then left as they are)
     */
    @VanillaRef("s_sound.c:S_UpdateSounds")
    public void updateSounds(int listener) {
        if (view == null) {
            return;
        }
        backend.update(view.gametic());
        DefsView defs = view.defs();
        ActorView actors = view.actors();
        for (int cnum = 0; cnum < channelCount; cnum++) {
            if (!inUse[cnum]) {
                continue;
            }
            int sfx = chanSfx[cnum];
            if (backend.isPlaying(cnum)) {
                // initialize parameters
                int volume = snd_SfxVolume;
                int sep = NORM_SEP;

                if (defs.sfxLink(sfx) >= 0) {
                    volume += defs.sfxLinkVolume(sfx);
                    if (volume < 1) {
                        stopChannel(cnum);
                        continue;
                    } else if (volume > snd_SfxVolume) {
                        volume = snd_SfxVolume;
                    }
                }

                // check non-local sounds for distance clipping or modify their params
                int origin = chanOrigin[cnum];
                if (origin != SoundOrigins.NONE && listener != origin && listener >= 0) {
                    if (!isLive(origin)) {
                        stopChannel(cnum);
                        continue;
                    }
                    boolean audible = adjustSoundParams(actors.x(listener), actors.y(listener),
                            actors.angle(listener), originX(origin), originY(origin));
                    if (!audible) {
                        stopChannel(cnum);
                    } else {
                        backend.updateChannel(cnum, adjVolume, adjSeparation);
                    }
                }
            } else {
                // if channel is allocated but sound has stopped, free it
                stopChannel(cnum);
            }
        }
    }
}
