package doom.sound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.game.api.SoundOrigins;
import doom.game.view.MusicId;
import doom.game.view.SoundId;
import doom.sound.FakeGame.DummyData;
import doom.sound.FakeGame.RecordingBackend;
import doom.wad.iwad.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SoundSystemTest {

    private static final int FU = 65536;

    private FakeGame game;
    private RecordingBackend backend;
    private DummyData data;
    private SoundSystem sound;

    @BeforeEach
    void setUp() {
        game = new FakeGame();
        backend = new RecordingBackend();
        data = new DummyData();
        sound = new SoundSystem(backend, data, SoundSystem.Settings.defaults());
        sound.bind(game.view, game::nextRandom);
        backend.log.clear();
    }

    private void actorAt(int id, int unitsX, int unitsY) {
        game.actorExists[id] = true;
        game.actorX[id] = unitsX * FU;
        game.actorY[id] = unitsY * FU;
    }

    // ------------------------------------------------------------------ misc RNG

    @Test
    void soundWithoutOriginDrawsOneNumberAndPlaysAtFullVolumeCentred() {
        game.rngValues = new int[] {37};
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        assertThat(game.rngCalls).isEqualTo(1);
        assertThat(backend.starts).hasSize(1);
        RecordingBackend.Start s = backend.starts.get(0);
        assertThat(s.vol()).isEqualTo(64); // sfx volume 8 * 8
        assertThat(s.sep()).isEqualTo(128);
        // pitch = 128 + 16 - (37 & 31); only recorded on the channel (variation is off for the mixer)
        assertThat(sound.channelPitch(0)).isEqualTo(128 + 16 - (37 & 31));
        assertThat(s.pitch()).isEqualTo(128);
    }

    @Test
    void pitchVariationReachesTheMixerWhenEnabled() {
        sound = new SoundSystem(backend, data, new SoundSystem.Settings(8, 8, 8, true));
        sound.bind(game.view, game::nextRandom);
        game.rngValues = new int[] {5};
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        assertThat(backend.starts.get(0).pitch()).isEqualTo(128 + 16 - 5);
    }

    @Test
    void sawSoundsUseAMask15AndOtherSoundsMask31() {
        game.rngValues = new int[] {0xFF};
        sound.startSound(SoundOrigins.NONE, SoundId.SAWUP);
        assertThat(sound.channelPitch(0)).isEqualTo(128 + 8 - 15);
        sound.startSound(SoundOrigins.NONE, SoundId.SAWHIT);
        assertThat(sound.channelPitch(0)).isEqualTo(128 + 8 - 15); // replaced the no-origin sound on channel 0
        sound.startSound(SoundOrigins.NONE, SoundId.SHOTGN);
        assertThat(sound.channelPitch(0)).isEqualTo(128 + 16 - 31);
        assertThat(game.rngCalls).isEqualTo(3);
    }

    @Test
    void itemUpAndTinkDrawNothing() {
        sound.startSound(SoundOrigins.NONE, SoundId.ITEMUP);
        sound.startSound(SoundOrigins.NONE, SoundId.TINK);
        assertThat(game.rngCalls).isZero();
        assertThat(sound.channelPitch(0)).isEqualTo(128);
        assertThat(backend.starts).hasSize(2);
    }

    @Test
    void inaudibleSoundDrawsNothingAndTakesNoChannel() {
        actorAt(1, 1300, 0); // beyond the 1200 unit clipping distance
        sound.startSound(1, SoundId.POSIT1);
        assertThat(game.rngCalls).isZero();
        assertThat(backend.starts).isEmpty();
        assertThat(sound.channelInUse(0)).isFalse();
    }

    @Test
    void audibleDistantSoundDrawsOnceAfterTheAudibilityCheck() {
        actorAt(1, 700, 0);
        sound.startSound(1, SoundId.POSIT1);
        assertThat(game.rngCalls).isEqualTo(1);
        assertThat(backend.starts).hasSize(1);
    }

    @Test
    void soundsFromTheListenersOwnBodyAreCentredFullVolume() {
        actorAt(0, 100, 100);
        sound.startSound(0, SoundId.OOF);
        RecordingBackend.Start s = backend.starts.get(0);
        assertThat(s.vol()).isEqualTo(64);
        assertThat(s.sep()).isEqualTo(128);
        assertThat(game.rngCalls).isEqualTo(1);
    }

    @Test
    void sectorOriginsUseTheSectorSoundOrigin() {
        game.sectorX[2] = 700 * FU;
        game.sectorY[2] = 0;
        sound.startSound(SoundOrigins.sector(2), SoundId.DOROPN);
        assertThat(backend.starts.get(0).vol()).isEqualTo(32);
    }

    // ------------------------------------------------------------------ attenuation and separation

    @Test
    void volumeFallsLinearlyBetweenCloseAndClippingDistance() {
        actorAt(1, 700, 0);
        sound.startSound(1, SoundId.POSIT1);
        // 64 * (1200 - 700) / 1000
        assertThat(backend.starts.get(0).vol()).isEqualTo(32);
    }

    @Test
    void closeSourcesPlayAtFullVolume() {
        actorAt(1, 150, 0);
        sound.startSound(1, SoundId.POSIT1);
        assertThat(backend.starts.get(0).vol()).isEqualTo(64);
    }

    @Test
    void distanceIsTheApproximationAdxPlusAdyMinusHalfTheSmaller() {
        actorAt(1, 600, 400); // 600 + 400 - 200 = 800
        sound.startSound(1, SoundId.POSIT1);
        assertThat(backend.starts.get(0).vol()).isEqualTo(64 * (1200 - 800) / 1000);
    }

    @Test
    void mapEightNeverClipsAndKeepsAMinimumVolume() {
        game.map = 8;
        actorAt(1, 5000, 0);
        sound.startSound(1, SoundId.POSIT1);
        // 15 + (64 - 15) * 0 / 1000
        assertThat(backend.starts.get(0).vol()).isEqualTo(15);
        assertThat(game.rngCalls).isEqualTo(1);
    }

    @Test
    void mapEightMidDistance() {
        game.map = 8;
        actorAt(1, 700, 0);
        sound.startSound(1, SoundId.POSIT1);
        assertThat(backend.starts.get(0).vol()).isEqualTo(15 + (64 - 15) * 500 / 1000);
    }

    @Test
    void separationFollowsTheAngleToTheSource() {
        // The listener looks along +x; a source straight ahead is centred, one to its left (+y) goes left.
        actorAt(1, 300, 0);
        sound.startSound(1, SoundId.POSIT1);
        // angle == listener angle takes the "angle + (0xffffffff - listener angle)" branch: fine index 8191
        assertThat(backend.starts.get(0).sep())
                .isEqualTo(128 - (doom.math.Fixed.mul(96 * FU, doom.math.Tables.fineSine(8191)) >> 16));

        actorAt(2, 0, 300);
        sound.startSound(2, SoundId.POSIT1);
        // angle to the source is 90 degrees: sine = 1.0
        assertThat(backend.starts.get(1).sep())
                .isEqualTo(128 - (doom.math.Fixed.mul(96 * FU, doom.math.Tables.fineSine(2048)) >> 16));
    }

    @Test
    void sourceOnTopOfTheListenerIsCentredEvenWhenInaudible() {
        // same x/y as the listener: distance 0, audible, separation forced to NORM_SEP
        actorAt(1, 0, 0);
        sound.startSound(1, SoundId.POSIT1);
        assertThat(backend.starts.get(0).sep()).isEqualTo(128);
    }

    @Test
    void sfxVolumeScalesTheStartVolume() {
        sound.setSfxVolume(15);
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        assertThat(backend.starts.get(0).vol()).isEqualTo(120);
        sound.setSfxVolume(0);
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        assertThat(backend.starts.get(1).vol()).isZero();
    }

    // ------------------------------------------------------------------ channels

    @Test
    void sameOriginReplacesItsChannel() {
        actorAt(1, 100, 0);
        sound.startSound(1, SoundId.POSIT1);
        sound.startSound(1, SoundId.POSIT2);
        assertThat(sound.channelInUse(0)).isTrue();
        assertThat(sound.channelSfx(0)).isEqualTo(SoundId.POSIT2);
        assertThat(sound.channelInUse(1)).isFalse();
        assertThat(backend.log).containsExactly("start 0", "stop 0", "start 0");
    }

    @Test
    void aSoundWithNoOriginStopsTheFirstChannelWithNoOrigin() {
        // S_StopSound(NULL) matches channels with a NULL origin: a faithful quirk of the original.
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        actorAt(1, 100, 0);
        sound.startSound(1, SoundId.POSIT1);
        sound.startSound(SoundOrigins.NONE, SoundId.SHOTGN);
        assertThat(sound.channelSfx(0)).isEqualTo(SoundId.SHOTGN);
        assertThat(sound.channelSfx(1)).isEqualTo(SoundId.POSIT1);
    }

    @Test
    void fullTableDropsLowerPriorityAndStealsForHigherPriority() {
        // priority: lower number = more important
        game.priority[SoundId.PISTOL] = 64;
        game.priority[SoundId.SHOTGN] = 70;
        game.priority[SoundId.DOROPN] = 100;
        for (int i = 1; i <= 8; i++) {
            actorAt(i, 10 * i, 0);
            sound.startSound(i, i == 5 ? SoundId.DOROPN : SoundId.PISTOL);
        }
        for (int c = 0; c < 8; c++) {
            assertThat(sound.channelInUse(c)).isTrue();
        }
        // a sound less important than every channel (priority 100 vs the DOROPN's 100: equal counts as "can replace")
        actorAt(9, 100, 0);
        game.priority[SoundId.SWTCHN] = 101;
        sound.startSound(9, SoundId.SWTCHN);
        assertThat(sound.channelOrigin(0)).isEqualTo(1);
        assertThat(sound.channelOrigin(4)).isEqualTo(5); // dropped: nothing was stolen
        // an important sound steals the first channel whose priority number is >= its own: channel 0 (64 >= 64)
        game.priority[SoundId.SGCOCK] = 64;
        sound.startSound(9, SoundId.SGCOCK);
        assertThat(sound.channelOrigin(0)).isEqualTo(9);
        assertThat(sound.channelSfx(0)).isEqualTo(SoundId.SGCOCK);
    }

    @Test
    void aSoundMoreImportantThanEverythingStealsTheFirstChannelOfEqualOrLowerImportance() {
        for (int i = 1; i <= 8; i++) {
            actorAt(i, 10 * i, 0);
            game.priority[SoundId.PISTOL] = 64;
            sound.startSound(i, SoundId.PISTOL);
        }
        game.priority[SoundId.BFG] = 30;
        actorAt(9, 50, 0);
        sound.startSound(9, SoundId.BFG);
        assertThat(sound.channelOrigin(0)).isEqualTo(9);
        assertThat(sound.channelSfx(0)).isEqualTo(SoundId.BFG);
    }

    @Test
    void droppedSoundStillConsumedItsRandomNumber() {
        game.priority[SoundId.PISTOL] = 10;
        for (int i = 1; i <= 8; i++) {
            actorAt(i, 10 * i, 0);
            sound.startSound(i, SoundId.PISTOL);
        }
        int before = game.rngCalls;
        game.priority[SoundId.SWTCHN] = 100;
        actorAt(9, 5, 0);
        sound.startSound(9, SoundId.SWTCHN);
        assertThat(game.rngCalls).isEqualTo(before + 1);
        assertThat(backend.starts).hasSize(8);
    }

    @Test
    void stopSoundFreesTheChannelOfTheOrigin() {
        actorAt(1, 100, 0);
        sound.startSound(1, SoundId.POSIT1);
        sound.stopSound(1);
        assertThat(sound.channelInUse(0)).isFalse();
        assertThat(backend.playing[0]).isFalse();
    }

    @Test
    void updateFreesFinishedChannelsAndRetunesMovingOrigins() {
        actorAt(1, 150, 0);
        sound.startSound(1, SoundId.POSIT1);
        assertThat(backend.starts.get(0).vol()).isEqualTo(64);

        game.actorX[1] = 700 * FU; // the monster walked away (dead ahead gives 129: the original's angle quirk)
        sound.updateSounds();
        assertThat(backend.log).contains("update 0 32 129");

        game.actorX[1] = 1300 * FU; // out of earshot: the channel is stopped
        sound.updateSounds();
        assertThat(sound.channelInUse(0)).isFalse();
        assertThat(backend.playing[0]).isFalse();

        // a sound that finished by itself frees its channel
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        backend.playing[0] = false;
        sound.updateSounds();
        assertThat(sound.channelInUse(0)).isFalse();
    }

    @Test
    void updateDoesNotMoveSoundsWithoutOriginOrFromTheListener() {
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        actorAt(0, 0, 0);
        sound.startSound(0, SoundId.OOF);
        backend.log.clear();
        sound.updateSounds();
        assertThat(backend.log).isEmpty();
    }

    @Test
    void stopAllSoundsFreesEverything() {
        actorAt(1, 100, 0);
        sound.startSound(1, SoundId.POSIT1);
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        sound.stopAllSounds();
        assertThat(sound.channelInUse(0)).isFalse();
        assertThat(sound.channelInUse(1)).isFalse();
    }

    @Test
    void badSoundNumbersAreFatal() {
        assertThatThrownBy(() -> sound.startSound(-1, 0)).hasMessageContaining("Bad sfx #");
        assertThatThrownBy(() -> sound.startSound(-1, SoundId.COUNT)).hasMessageContaining("Bad sfx #");
    }

    @Test
    void eventsBeforeBindAreIgnored() {
        SoundSystem unbound = new SoundSystem(backend, data, SoundSystem.Settings.defaults());
        unbound.startSound(-1, SoundId.PISTOL);
        unbound.updateSounds();
        unbound.levelLoaded();
        assertThat(backend.starts).isEmpty();
    }

    // ------------------------------------------------------------------ headless backend

    @Test
    void nullBackendKeepsChannelsForTheLengthOfTheSample() {
        SoundSystem headless = new SoundSystem(new NullSoundBackend(8), data, SoundSystem.Settings.defaults());
        headless.bind(game.view, game::nextRandom);
        headless.startSound(SoundOrigins.NONE, SoundId.PISTOL); // 1 s at 11025 Hz = 35 tics
        game.gametic = 20;
        headless.updateSounds();
        assertThat(headless.channelInUse(0)).isTrue();
        game.gametic = 36;
        headless.updateSounds();
        assertThat(headless.channelInUse(0)).isFalse();
    }

    @Test
    void headlessAndAudibleBackendsConsumeTheSameRandomNumbers() {
        FakeGame other = new FakeGame();
        SoundSystem headless = new SoundSystem(new NullSoundBackend(8), data, SoundSystem.Settings.defaults());
        headless.bind(other.view, other::nextRandom);
        for (int i = 0; i < 20; i++) {
            actorAt(1, 100 * i, 0);
            other.actorExists[1] = true;
            other.actorX[1] = 100 * i * FU;
            sound.startSound(1, SoundId.POSIT1);
            headless.startSound(1, SoundId.POSIT1);
        }
        assertThat(other.rngCalls).isEqualTo(game.rngCalls);
    }

    // ------------------------------------------------------------------ music

    @Test
    void levelMusicFollowsTheOriginalTables() {
        assertThat(SoundSystem.levelMusic(GameMode.SHAREWARE, 1, 1)).isEqualTo(MusicId.E1M1);
        assertThat(SoundSystem.levelMusic(GameMode.REGISTERED, 3, 4)).isEqualTo(MusicId.E3M4);
        assertThat(SoundSystem.levelMusic(GameMode.COMMERCIAL, 1, 1)).isEqualTo(MusicId.RUNNIN);
        assertThat(SoundSystem.levelMusic(GameMode.COMMERCIAL, 1, 30)).isEqualTo(MusicId.RUNNIN + 29);
        assertThat(SoundSystem.levelMusic(GameMode.RETAIL, 4, 1)).isEqualTo(MusicId.E3M4);
        assertThat(SoundSystem.levelMusic(GameMode.RETAIL, 4, 9)).isEqualTo(MusicId.E1M9);
    }

    @Test
    void levelLoadedStopsSoundsAndStartsTheLevelMusicLooping() {
        sound.startSound(SoundOrigins.NONE, SoundId.PISTOL);
        game.episode = 1;
        game.map = 3;
        sound.levelLoaded();
        assertThat(sound.channelInUse(0)).isFalse();
        assertThat(sound.musicPlaying()).isEqualTo(MusicId.E1M3);
        assertThat(backend.log).contains("music true");
        assertThat(data.musicRequests).containsExactly("m" + MusicId.E1M3);
    }

    @Test
    void sameMusicIsNotRestarted() {
        sound.changeMusic(MusicId.E1M1, true);
        sound.changeMusic(MusicId.E1M1, true);
        assertThat(backend.musicStarts).isEqualTo(1);
        sound.changeMusic(MusicId.E1M2, true);
        assertThat(backend.musicStarts).isEqualTo(2);
        assertThat(backend.log).contains("stopMusic");
    }

    @Test
    void introUsesTheOplVariantWhenPresent() {
        data.introaExists = true;
        sound.changeMusic(MusicId.INTRO, false);
        assertThat(sound.musicPlaying()).isEqualTo(MusicId.INTROA);
        assertThat(backend.log).contains("music false");
    }

    @Test
    void pauseAndResumeOnlyAffectPlayingMusic() {
        sound.pauseSound(true);
        assertThat(backend.log).doesNotContain("pause");
        sound.changeMusic(MusicId.E1M1, true);
        sound.pauseSound(true);
        sound.pauseSound(true);
        assertThat(sound.musicPaused()).isTrue();
        sound.pauseSound(false);
        assertThat(sound.musicPaused()).isFalse();
        assertThat(backend.log.stream().filter("pause"::equals).count()).isEqualTo(1);
        assertThat(backend.log.stream().filter("resume"::equals).count()).isEqualTo(1);
    }

    @Test
    void stoppingPausedMusicResumesFirst() {
        sound.changeMusic(MusicId.E1M1, true);
        sound.pauseMusic();
        sound.changeMusic(MusicId.E1M2, true);
        assertThat(backend.log).containsSequence("pause", "resume", "stopMusic", "music true");
        assertThat(sound.musicPaused()).isFalse();
    }

    @Test
    void badMusicNumbersAreFatal() {
        assertThatThrownBy(() -> sound.changeMusic(0, true)).hasMessageContaining("Bad music number");
        assertThatThrownBy(() -> sound.changeMusic(MusicId.COUNT, true)).hasMessageContaining("Bad music number");
    }

    @Test
    void musicVolumeGoesToTheBackendOnTheMenuScale() {
        sound.setMusicVolume(11);
        assertThat(backend.log).contains("musicVolume 11");
        assertThat(sound.musicVolume()).isEqualTo(11);
        sound.setMusicVolume(99);
        assertThat(sound.musicVolume()).isEqualTo(15);
    }
}
