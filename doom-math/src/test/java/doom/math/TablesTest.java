package doom.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class TablesTest {

    /**
     * SHA-256 of {@code doom/math/tables.bin} as written by {@code doom-codegen gen-tables} from linuxdoom-1.10
     * (verified identical to Chocolate Doom 3.1.1). Changing the resource requires updating this value on purpose.
     */
    static final String TABLES_SHA256 = "3c2cd2ca9c63a993f4cad2131a8febaa0c60ff45e7d9306b6ae459b7d5b6f4b4";

    @Test
    void resourceChecksumIsPinned() throws IOException, NoSuchAlgorithmException {
        byte[] bytes;
        try (InputStream in = Tables.class.getResourceAsStream(Tables.RESOURCE)) {
            assertThat(in).isNotNull();
            bytes = in.readAllBytes();
        }
        assertThat(bytes).hasSize(67148);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(sha).isEqualTo(TABLES_SHA256);
    }

    @Test
    void spotValuesMatchTheSource() {
        // tables.c: first and last values of each table.
        assertThat(Tables.fineTangent(0)).isEqualTo(-170910304);
        assertThat(Tables.fineTangent(1)).isEqualTo(-56965752);
        assertThat(Tables.fineTangent(4095)).isEqualTo(170910304);
        assertThat(Tables.fineSine(0)).isEqualTo(25);
        assertThat(Tables.fineSine(1)).isEqualTo(75);
        assertThat(Tables.fineSine(10239)).isEqualTo(65535);
        assertThat(Tables.tanToAngle(0)).isZero();
        assertThat(Tables.tanToAngle(1)).isEqualTo(333772);
        assertThat(Tables.tanToAngle(2048)).isEqualTo(536870912).isEqualTo(Angle.ANG45);
        // m_random.c
        assertThat(Tables.rnd(0)).isZero();
        assertThat(Tables.rnd(1)).isEqualTo(8);
        assertThat(Tables.rnd(2)).isEqualTo(109);
        assertThat(Tables.rnd(255)).isEqualTo(249);
        // v_video.c
        assertThat(Tables.gamma(0, 0)).isEqualTo(1);
        assertThat(Tables.gamma(0, 255)).isEqualTo(255);
        assertThat(Tables.gamma(4, 255)).isEqualTo(255);
    }

    @Test
    void sineTableHasTheExpectedShape() {
        // finesine is symmetric around a quarter turn and antisymmetric around a half turn, up to one unit: the
        // original generator rounded each entry on its own (for example finesine[4096 + 51] is -2588 while
        // finesine[51] is 2587). This only guards against a shifted or truncated resource.
        int asymmetric = 0;
        for (int i = 0; i < Angle.FINEANGLES / 2; i++) {
            int s = Tables.fineSine(i);
            assertThat(Tables.fineSine(Angle.FINEANGLES / 2 - 1 - i)).isBetween(s - 1, s + 1);
            assertThat(Tables.fineSine(Angle.FINEANGLES / 2 + i)).isBetween(-s - 1, -s + 1);
            if (Tables.fineSine(Angle.FINEANGLES / 2 + i) != -s) {
                asymmetric++;
            }
        }
        assertThat(asymmetric).isPositive().isLessThan(Angle.FINEANGLES / 8);
        for (int i = 0; i < Angle.FINEANGLES; i++) {
            assertThat(Tables.fineCosine(i)).isEqualTo(Tables.fineSine(i + 2048));
        }
        assertThat(Tables.fineSine(Tables.FINE_SINE_SIZE - 1)).isEqualTo(Tables.fineSine(Angle.FINEANGLES / 4 - 1));
        for (int i = 0; i < Tables.FINE_TANGENT_SIZE / 2; i++) {
            assertThat(Tables.fineTangent(Tables.FINE_TANGENT_SIZE - 1 - i)).isEqualTo(-Tables.fineTangent(i));
        }
        for (int i = 1; i <= Tables.SLOPERANGE; i++) {
            assertThat(Tables.tanToAngle(i)).isGreaterThan(Tables.tanToAngle(i - 1));
        }
    }

    @Test
    void rndAndGammaStayInByteRange() {
        for (int i = 0; i < Tables.RND_TABLE_SIZE; i++) {
            assertThat(Tables.rnd(i)).isBetween(0, 255);
        }
        for (int level = 0; level < Tables.GAMMA_LEVELS; level++) {
            for (int v = 0; v < 256; v++) {
                assertThat(Tables.gamma(level, v)).isBetween(0, 255);
                if (v > 0) {
                    assertThat(Tables.gamma(level, v)).isGreaterThanOrEqualTo(Tables.gamma(level, v - 1));
                }
            }
        }
        assertThatThrownBy(() -> Tables.gamma(5, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> Tables.gamma(0, 256)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> Tables.gamma(-1, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void slopeDivUsesUnsignedArithmetic() {
        assertThat(Tables.slopeDiv(0, 0)).isEqualTo(Tables.SLOPERANGE);
        assertThat(Tables.slopeDiv(100, 511)).isEqualTo(Tables.SLOPERANGE);
        assertThat(Tables.slopeDiv(0, 512)).isZero();
        assertThat(Tables.slopeDiv(256, 512)).isEqualTo(1024); // (256 << 3) / 2
        assertThat(Tables.slopeDiv(512, 512)).isEqualTo(Tables.SLOPERANGE);
        assertThat(Tables.slopeDiv(1000, 512)).isEqualTo(Tables.SLOPERANGE); // clamped
        // A "negative" denominator is a huge unsigned value: no early return, tiny quotient.
        assertThat(Tables.slopeDiv(Fixed.FRACUNIT, -1)).isZero();
        // num << 3 wraps around in 32 bits, as in C.
        assertThat(Tables.slopeDiv(0x2000_0000, 0x10_0000)).isZero();
        assertThat(Tables.slopeDiv(0x1fff_ffff, 0x7fff_ffff)).isEqualTo((int) ((0xffff_fff8L) / 0x7f_ffffL));
    }
}
