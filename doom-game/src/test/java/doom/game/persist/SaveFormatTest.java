package doom.game.persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The DJSV container: header, sections, checksum, and what the reader does with damaged input. */
class SaveFormatTest {

    private static final String HASH = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

    private static SaveFormat.Header header() {
        return new SaveFormat.Header(SaveFormat.FORMAT_VERSION, "1.9", "COMMERCIAL",
                List.of(new SaveFormat.WadId("doom2.wad", HASH), new SaveFormat.WadId("extra.wad", HASH)),
                "my game", 1234567890123L);
    }

    private static ByteSink payload(int... values) {
        ByteSink s = new ByteSink(64);
        for (int v : values) {
            s.putInt(v);
        }
        return s;
    }

    /** A complete file: header, the given sections, checksum. */
    private static byte[] file(SaveFormat.Header h, Object... tagsAndPayloads) {
        ByteSink out = new ByteSink(256);
        SaveFormat.writeHeader(out, h);
        for (int i = 0; i < tagsAndPayloads.length; i += 2) {
            out.putSection((String) tagsAndPayloads[i], (ByteSink) tagsAndPayloads[i + 1]);
        }
        ByteSink crc = new ByteSink(4);
        crc.putInt(out.crc32());
        out.putSection(SaveFormat.CRC, crc);
        return out.toByteArray();
    }

    @Test
    void headerAndSectionsRoundTrip() {
        byte[] data = file(header(), "SESS", payload(1, 2, 3), "WRLD", payload(4));
        SaveFormat.Parsed p = SaveFormat.parse(data);
        assertThat(p.header()).isEqualTo(header());
        assertThat(p.sections()).extracting(SaveFormat.Section::tag).containsExactly("SESS", "WRLD");
        ByteSource sess = p.require("SESS");
        assertThat(sess.getInt()).isEqualTo(1);
        assertThat(sess.getInt()).isEqualTo(2);
        assertThat(sess.getInt()).isEqualTo(3);
        sess.expectEnd();
    }

    @Test
    void requireGivesAFreshReaderEachTime() {
        SaveFormat.Parsed p = SaveFormat.parse(file(header(), "SESS", payload(7)));
        assertThat(p.require("SESS").getInt()).isEqualTo(7);
        assertThat(p.require("SESS").getInt()).isEqualTo(7);
    }

    @Test
    void unknownSectionsAreSkippedAndMissingOnesReported() {
        byte[] data = file(header(), "XTRA", payload(9, 9), "SESS", payload(1));
        SaveFormat.Parsed p = SaveFormat.parse(data);
        assertThat(p.require("SESS").getInt()).isEqualTo(1);
        assertThatThrownBy(() -> p.require("PLYR")).isInstanceOf(SaveGameException.class)
                .hasMessageContaining("PLYR");
    }

    @Test
    void badSignatureAndTooShortAreRejected() {
        assertThatThrownBy(() -> SaveFormat.parse(new byte[0])).isInstanceOf(SaveGameException.class);
        assertThatThrownBy(() -> SaveFormat.parse(null)).isInstanceOf(SaveGameException.class);
        byte[] data = file(header(), "SESS", payload(1));
        data[0] = 'X';
        assertThatThrownBy(() -> SaveFormat.parse(data)).isInstanceOf(SaveGameException.class)
                .hasMessageContaining("signature");
        // a vanilla save game is not one of ours
        byte[] vanilla = new byte[200];
        System.arraycopy("version 109".getBytes(), 0, vanilla, 24, 11);
        assertThatThrownBy(() -> SaveFormat.parse(vanilla)).isInstanceOf(SaveGameException.class);
    }

    @Test
    void aNewerFormatIsRejectedWithAClearMessage() {
        SaveFormat.Header newer = new SaveFormat.Header(SaveFormat.FORMAT_VERSION + 1, "1.9", "COMMERCIAL", List.of(),
                "x", 0);
        assertThatThrownBy(() -> SaveFormat.parse(file(newer, "SESS", payload(1))))
                .isInstanceOf(SaveGameException.class).hasMessageContaining("newer version");
    }

    @Test
    void everyTruncationIsAnErrorNotACrash() {
        byte[] data = file(header(), "SESS", payload(1, 2, 3, 4, 5), "WRLD", payload(6, 7, 8));
        for (int length = 0; length < data.length; length++) {
            byte[] cut = Arrays.copyOf(data, length);
            assertThatThrownBy(() -> SaveFormat.parse(cut)).as("length %d", length)
                    .isInstanceOf(SaveGameException.class);
        }
    }

    @Test
    void everyFlippedByteIsDetected() {
        byte[] data = file(header(), "SESS", payload(1, 2, 3, 4, 5), "WRLD", payload(6, 7, 8));
        for (int i = 0; i < data.length; i++) {
            for (int bit = 0; bit < 8; bit += 3) {
                byte[] bad = data.clone();
                bad[i] ^= (byte) (1 << bit);
                assertThatThrownBy(() -> SaveFormat.parse(bad)).as("byte %d bit %d", i, bit)
                        .isInstanceOf(SaveGameException.class);
            }
        }
    }

    @Test
    void dataAfterTheChecksumIsRejected() {
        byte[] data = file(header(), "SESS", payload(1));
        byte[] more = Arrays.copyOf(data, data.length + 1);
        assertThatThrownBy(() -> SaveFormat.parse(more)).isInstanceOf(SaveGameException.class);
    }

    @Test
    void sectionLengthsBeyondTheFileAreRejected() {
        ByteSink out = new ByteSink(64);
        SaveFormat.writeHeader(out, header());
        out.putTag("SESS");
        out.putInt(Integer.MAX_VALUE);
        ByteSink crc = new ByteSink(4);
        crc.putInt(out.crc32());
        out.putSection(SaveFormat.CRC, crc);
        byte[] data = out.toByteArray();
        assertThatThrownBy(() -> SaveFormat.parse(data)).isInstanceOf(SaveGameException.class);
        // negative length too
        out = new ByteSink(64);
        SaveFormat.writeHeader(out, header());
        out.putTag("SESS");
        out.putInt(-5);
        byte[] negative = out.toByteArray();
        assertThatThrownBy(() -> SaveFormat.parse(negative)).isInstanceOf(SaveGameException.class);
    }

    @Test
    void readerRejectsOverrunsAndBadCounts() {
        ByteSource s = new ByteSource(new byte[] {1, 0, 0, 0, 2}, 0, 5, "test");
        assertThat(s.getInt()).isEqualTo(1);
        assertThatThrownBy(s::getInt).isInstanceOf(SaveGameException.class);
        ByteSink big = payload(1_000_000);
        ByteSource counts = new ByteSource(big.toByteArray(), 0, 4, "test");
        assertThatThrownBy(() -> counts.getCount(4)).isInstanceOf(SaveGameException.class);
        ByteSource negative = new ByteSource(payload(-1).toByteArray(), 0, 4, "test");
        assertThatThrownBy(() -> negative.getCount(4)).isInstanceOf(SaveGameException.class);
        ByteSource range = new ByteSource(payload(9).toByteArray(), 0, 4, "test");
        assertThatThrownBy(() -> range.getRange(0, 4, "x")).isInstanceOf(SaveGameException.class);
        ByteSource flag = new ByteSource(payload(2).toByteArray(), 0, 4, "test");
        assertThatThrownBy(flag::getBool).isInstanceOf(SaveGameException.class);
    }

    @Test
    void inspectReadsTheHeaderWithoutRestoring() {
        byte[] data = file(header(), "SESS", payload(1));
        SaveInfo info = SaveGameCodec.inspect(data);
        assertThat(info.description()).isEqualTo("my game");
        assertThat(info.timestamp()).isEqualTo(1234567890123L);
        assertThat(info.gameMode()).isEqualTo("COMMERCIAL");
        assertThat(info.wads()).containsExactly("doom2.wad", "extra.wad");
        assertThat(info.wadHashes()).containsExactly(HASH, HASH);
        assertThat(data[info.payloadOffset()]).isEqualTo((byte) 'S');
    }

    @Test
    void wadDifferencesAreReportedByContentNotByName() {
        SaveFormat.WadId a = new SaveFormat.WadId("doom2.wad", HASH);
        SaveFormat.WadId renamed = new SaveFormat.WadId("DOOM2.WAD", HASH);
        SaveFormat.WadId other = new SaveFormat.WadId("doom2.wad", "ff" + HASH.substring(2));
        assertThat(WadFingerprint.difference(List.of(a), List.of(a))).isNull();
        assertThat(WadFingerprint.difference(List.of(a), List.of(renamed))).isNull();
        assertThat(WadFingerprint.difference(List.of(a), List.of(other))).contains("doom2.wad");
        assertThat(WadFingerprint.difference(List.of(a), List.of(a, renamed))).isNotNull();
    }
}
