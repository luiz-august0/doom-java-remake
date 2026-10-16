package doom.wad.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.WadFormatException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class AudioFormatsTest {

    private static byte[] dmx(int rate, int count, int lumpExtra) {
        byte[] lump = new byte[8 + count + lumpExtra];
        lump[0] = 3;
        lump[2] = (byte) rate;
        lump[3] = (byte) (rate >> 8);
        lump[4] = (byte) count;
        lump[5] = (byte) (count >> 8);
        for (int i = 0; i < count; i++) {
            lump[8 + i] = (byte) i;
        }
        return lump;
    }

    @Test
    void dmxDropsSixteenBytesOfPaddingAtBothEnds() {
        DmxSound s = DmxSound.tryParse(dmx(11025, 100, 0));
        assertThat(s).isNotNull();
        assertThat(s.sampleRate()).isEqualTo(11025);
        assertThat(s.sampleCount()).isEqualTo(68);
        assertThat(s.sample(0)).isEqualTo(16);
        assertThat(s.sample(67)).isEqualTo(83);
        assertThat(s.toSigned16()[0]).isEqualTo((short) ((16 - 128) << 8));
    }

    @Test
    void dmxRejectsInvalidLumps() {
        assertThat(DmxSound.tryParse(dmx(11025, 48, 0))).isNull();
        assertThat(DmxSound.tryParse(dmx(11025, 49, 0))).isNotNull();
        byte[] tooLong = dmx(11025, 100, 0);
        tooLong[4] = 101;
        assertThat(DmxSound.tryParse(tooLong)).isNull();
        byte[] wrongFormat = dmx(11025, 100, 0);
        wrongFormat[0] = 0;
        assertThat(DmxSound.tryParse(wrongFormat)).isNull();
        assertThat(DmxSound.tryParse(new byte[4])).isNull();
        assertThatThrownBy(() -> DmxSound.parse(ByteBuffer.wrap(new byte[4]), "DSFOO"))
                .isInstanceOf(WadFormatException.class).hasMessageContaining("DSFOO");
    }

    private static byte[] mus(int... score) {
        byte[] lump = new byte[16 + score.length];
        lump[0] = 'M';
        lump[1] = 'U';
        lump[2] = 'S';
        lump[3] = 0x1A;
        lump[4] = (byte) score.length;
        lump[6] = 16;
        for (int i = 0; i < score.length; i++) {
            lump[16 + i] = (byte) score[i];
        }
        return lump;
    }

    @Test
    void musConversionMatchesMus2MidSemantics() {
        // channel 0: press key 60 with velocity 100 (last-bit set), delay 35 ticks, release key 60 (last), delay 0, end.
        byte[] midi = MusConverter.toMidi(mus(0x90, 60 | 0x80, 100, 35, 0x80, 60, 0, 0x60));
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        byte[] header = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 0x46, 'M', 'T', 'r', 'k'};
        expected.writeBytes(header);
        byte[] track = {
            0x00, (byte) 0xB0, 0x7B, 0x00,          // all notes off on the new channel
            0x00, (byte) 0x90, 60, 100,             // note on
            35, (byte) 0x80, 60, 0,                 // note off after 35 ticks
            0x00, (byte) 0xFF, 0x2F, 0x00           // end of track
        };
        expected.writeBytes(new byte[] {0, 0, 0, (byte) track.length});
        expected.writeBytes(track);
        assertThat(midi).isEqualTo(expected.toByteArray());

        MusicScore score = MidiFile.parse(midi).toScore();
        assertThat(score.division()).isEqualTo(70);
        assertThat(score.ticksPerSecond()).isEqualTo(140.0);
        assertThat(score.size()).isEqualTo(4);
        assertThat(score.tick(2)).isEqualTo(35);
        assertThat(score.type(2)).isEqualTo(MidiFile.NOTE_OFF);
        assertThat(score.type(3)).isEqualTo(MusicScore.TYPE_END);
    }

    @Test
    void musPercussionAndChannelAllocation() {
        // MUS channel 5 first (gets MIDI 0), then percussion channel 15 (MIDI 9), then MUS channel 6 (MIDI 1).
        byte[] midi = MusConverter.toMidi(mus(0x05, 40, 0x1F | 0, 0x00, 0x06, 1, 0x60));
        MusicScore score = MidiFile.parse(midi).toScore();
        boolean sawPercussion = false;
        for (int i = 0; i < score.size(); i++) {
            if (score.type(i) == MidiFile.NOTE_ON && score.channel(i) == 9) {
                sawPercussion = true;
            }
        }
        assertThat(score.channel(0)).isEqualTo(0);
        assertThat(sawPercussion).isTrue();
    }

    @Test
    void musRejectsTruncatedAndInvalidData() {
        assertThatThrownBy(() -> MusConverter.toMidi(new byte[8])).isInstanceOf(WadFormatException.class);
        assertThatThrownBy(() -> MusConverter.toMidi(mus(0x10, 60))).isInstanceOf(WadFormatException.class);
        assertThatThrownBy(() -> MusConverter.toMidi(mus(0x70))).isInstanceOf(WadFormatException.class);
    }

    @Test
    void midiParsingSupportsRunningStatusAndMultipleTracks() {
        byte[] file = {
            'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 1, 0, 2, 0, 96,
            'M', 'T', 'r', 'k', 0, 0, 0, 12,
            0, (byte) 0xFF, 0x51, 3, 0x07, (byte) 0xA1, 0x20,       // tempo 500000
            0, (byte) 0xFF, 0x2F, 0,
            'M', 'T', 'r', 'k', 0, 0, 0, 14,
            0, (byte) 0x90, 60, 100, 10, 62, 100, 10, 60, 0,       // running status note ons
            0, (byte) 0xFF, 0x2F, 0
        };
        MidiFile f = MidiFile.parse(file);
        assertThat(f.format()).isEqualTo(1);
        assertThat(f.trackCount()).isEqualTo(2);
        assertThat(f.division()).isEqualTo(96);
        MusicScore s = f.toScore();
        assertThat(s.size()).isEqualTo(6);
        assertThat(s.type(0)).isEqualTo(MusicScore.TYPE_TEMPO);
        assertThat(s.param1(0)).isEqualTo(500_000);
        assertThat(s.tick(4)).isEqualTo(20);
        assertThat(s.param1(4)).isEqualTo(60);
        assertThat(s.type(s.size() - 1)).isEqualTo(MusicScore.TYPE_END);
        assertThat(s.tick(s.size() - 1)).isEqualTo(20);
    }

    @Test
    void midiRejectsGarbage() {
        assertThatThrownBy(() -> MidiFile.parse(new byte[] {1, 2, 3})).isInstanceOf(WadFormatException.class);
        byte[] truncated = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k', 0, 0, 0, 4, 0, (byte) 0x90};
        assertThatThrownBy(() -> MidiFile.parse(truncated)).isInstanceOf(WadFormatException.class);
    }

    @Test
    void genMidiDecodesInstrumentRecords() {
        byte[] lump = new byte[8 + 175 * 36 + 175 * 32];
        byte[] magic = "#OPL_II#".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, lump, 0, 8);
        int at = 8 + 36 * 3; // instrument 3
        lump[at] = 5;       // flags: fixed + two voices
        lump[at + 2] = (byte) 130;
        lump[at + 3] = 38;
        int v0 = at + 4;
        lump[v0] = 0x21;
        lump[v0 + 1] = (byte) 0xF2;
        lump[v0 + 5] = 0x10;
        lump[v0 + 6] = 0x0E;       // feedback
        lump[v0 + 7] = 0x22;       // carrier tremolo
        lump[v0 + 14] = (byte) 0xF4;
        lump[v0 + 15] = (byte) 0xFF; // base note offset -12
        byte[] name = "Honky-tonk".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, lump, 8 + 175 * 36 + 32 * 3, name.length);

        GenMidi g = GenMidi.parse(lump);
        assertThat(g.hasValidHeader()).isTrue();
        assertThat(g.size()).isEqualTo(175);
        GenMidi.Instrument i = g.instrument(3);
        assertThat(i.fixed()).isTrue();
        assertThat(i.twoVoice()).isTrue();
        assertThat(i.fineTuning()).isEqualTo(130);
        assertThat(i.fixedNote()).isEqualTo(38);
        assertThat(i.voice(0).modulator().tremolo()).isEqualTo(0x21);
        assertThat(i.voice(0).modulator().attack()).isEqualTo(0xF2);
        assertThat(i.voice(0).modulator().level()).isEqualTo(0x10);
        assertThat(i.voice(0).feedback()).isEqualTo(0x0E);
        assertThat(i.voice(0).carrier().tremolo()).isEqualTo(0x22);
        assertThat(i.voice(0).baseNoteOffset()).isEqualTo(-12);
        assertThat(i.name()).isEqualTo("Honky-tonk");
        assertThat(g.percussion(35).index()).isEqualTo(128);
        assertThat(g.percussion(81).index()).isEqualTo(174);
        assertThatThrownBy(() -> GenMidi.parse(new byte[100])).isInstanceOf(WadFormatException.class);
    }
}
