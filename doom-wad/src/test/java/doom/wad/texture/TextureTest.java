package doom.wad.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.LumpDirectory;
import doom.wad.LumpReader;
import doom.wad.WadFormatException;
import doom.wad.WadWriter;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextureTest {

    private static byte[] pnames(String... names) {
        ByteBuffer b = ByteBuffer.allocate(4 + 8 * names.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(names.length);
        for (String n : names) {
            byte[] raw = n.getBytes(StandardCharsets.ISO_8859_1);
            b.put(raw).put(new byte[8 - raw.length]);
        }
        return b.array();
    }

    /** One texture: name, width, height, patch indices. */
    private record Tex(String name, int width, int height, int... patches) {
    }

    private static byte[] textureLump(Tex... textures) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int[] offsets = new int[textures.length];
        int pos = 4 + 4 * textures.length;
        for (int i = 0; i < textures.length; i++) {
            Tex t = textures[i];
            offsets[i] = pos;
            ByteBuffer b = ByteBuffer.allocate(22 + 10 * t.patches.length).order(ByteOrder.LITTLE_ENDIAN);
            byte[] raw = t.name.getBytes(StandardCharsets.ISO_8859_1);
            b.put(raw).put(new byte[8 - raw.length]);
            b.putInt(0).putShort((short) t.width).putShort((short) t.height).putInt(0)
                    .putShort((short) t.patches.length);
            for (int p : t.patches) {
                b.putShort((short) 0).putShort((short) 0).putShort((short) p).putShort((short) 1)
                        .putShort((short) 0);
            }
            body.writeBytes(b.array());
            pos += b.capacity();
        }
        ByteBuffer head = ByteBuffer.allocate(4 + 4 * textures.length).order(ByteOrder.LITTLE_ENDIAN);
        head.putInt(textures.length);
        for (int o : offsets) {
            head.putInt(o);
        }
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.writeBytes(head.array());
        all.writeBytes(body.toByteArray());
        return all.toByteArray();
    }

    private static LumpDirectory directory(byte[] tex1, byte[] tex2) {
        WadWriter w = WadWriter.iwad().add("PNAMES", pnames("WALL00", "wall01", "NOSUCH"))
                .add("TEXTURE1", tex1);
        if (tex2 != null) {
            w.add("TEXTURE2", tex2);
        }
        w.marker("P_START").add("WALL00", new byte[8]).add("WALL01", new byte[8]).marker("P_END");
        return LumpDirectory.of(List.of(w.toWadFile("t.wad")));
    }

    @Test
    void texturesAreNumberedTexture1ThenTexture2() {
        LumpDirectory d = directory(textureLump(new Tex("AAA", 64, 128, 0), new Tex("BBB", 33, 72, 0, 1)),
                textureLump(new Tex("CCC", 8, 8, 1)));
        TextureDirectory t = TextureDirectory.load(d);
        assertThat(t.count()).isEqualTo(3);
        assertThat(t.name(2)).isEqualTo("CCC");
        assertThat(t.numForName("AAA")).isZero();
        assertThat(t.numForName("bbb")).isEqualTo(1);
        assertThat(t.numForName("CCC")).isEqualTo(2);
        assertThat(t.width(1)).isEqualTo(33);
        assertThat(t.heightPixels(1)).isEqualTo(72);
        assertThat(t.height(1)).isEqualTo(72 << 16);
        assertThat(t.widthMask(0)).isEqualTo(63);
        assertThat(t.widthMask(1)).isEqualTo(31);
        TextureDefs.TexturePatch p = t.def(1).patches()[1];
        assertThat(p.patchIndex()).isEqualTo(1);
        assertThat(p.lump()).isEqualTo(d.find("WALL01"));
    }

    @Test
    void noTextureMarkerIsZeroAndUnknownNamesFail() {
        TextureDirectory t = TextureDirectory.load(directory(textureLump(new Tex("AAA", 64, 128, 0)), null));
        assertThat(t.checkNumForName("-")).isZero();
        assertThat(t.numForName("-")).isZero();
        assertThat(t.checkNumForName("MISSING")).isEqualTo(-1);
        assertThatThrownBy(() -> t.numForName("MISSING")).isInstanceOf(WadFormatException.class)
                .hasMessage("R_TextureNumForName: MISSING not found");
    }

    @Test
    void firstTextureWinsOnDuplicateNames() {
        TextureDirectory t = TextureDirectory.load(directory(textureLump(new Tex("DUP", 64, 64, 0),
                new Tex("OTHER", 8, 8, 0), new Tex("DUP", 32, 32, 0)), null));
        assertThat(t.numForName("DUP")).isZero();
    }

    @Test
    void missingPatchLumpIsReportedLikeVanilla() {
        assertThatThrownBy(() -> TextureDirectory.load(directory(textureLump(new Tex("BAD", 64, 64, 2)), null)))
                .isInstanceOf(WadFormatException.class)
                .hasMessage("R_InitTextures: Missing patch in texture BAD");
    }

    @Test
    void patchIndexBeyondPnamesIsMalformed() {
        assertThatThrownBy(() -> TextureDefs.read(directory(textureLump(new Tex("BAD", 64, 64, 9)), null)))
                .isInstanceOf(WadFormatException.class).hasMessageContaining("TEXTURE1")
                .hasMessageContaining("PNAMES");
    }

    @Test
    void badTextureDirectoryIsMalformed() {
        byte[] lump = textureLump(new Tex("AAA", 64, 128, 0));
        lump[4] = 0x7F; // offset of the first texture beyond the lump
        assertThatThrownBy(() -> TextureDefs.read(directory(lump, null))).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("bad texture directory");

        byte[] truncated = textureLump(new Tex("AAA", 64, 128, 0, 0));
        byte[] cut = java.util.Arrays.copyOf(truncated, truncated.length - 4);
        assertThatThrownBy(() -> TextureDefs.read(directory(cut, null))).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("patches of texture AAA");

        byte[] hugeCount = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F};
        assertThatThrownBy(() -> TextureDefs.read(directory(hugeCount, null))).isInstanceOf(WadFormatException.class)
                .hasMessageContaining("texture directory");
    }

    @Test
    void patchNamesRejectTruncatedAndNegative() {
        byte[] ok = pnames("A", "B");
        assertThat(PatchNames.read(new LumpReader(ByteBuffer.wrap(ok), "t.wad", "PNAMES")).names())
                .containsExactly("A", "B");
        byte[] cut = java.util.Arrays.copyOf(ok, ok.length - 1);
        assertThatThrownBy(() -> PatchNames.read(new LumpReader(ByteBuffer.wrap(cut), "t.wad", "PNAMES")))
                .isInstanceOf(WadFormatException.class).hasMessageContaining("PNAMES");
        byte[] negative = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertThatThrownBy(() -> PatchNames.read(new LumpReader(ByteBuffer.wrap(negative), "t.wad", "PNAMES")))
                .isInstanceOf(WadFormatException.class);
    }

    @Test
    void flatsUseTheLastMarkersAndIgnoreNamespaces() {
        WadWriter w = WadWriter.iwad().marker("F_START").add("OLD", new byte[4096]).marker("F_END")
                .marker("F_START").add("FLAT1", new byte[4096]).add("flat2", new byte[4096]).marker("F_END");
        LumpDirectory d = LumpDirectory.of(List.of(w.toWadFile("t.wad")));
        FlatDirectory f = FlatDirectory.load(d);
        assertThat(f.count()).isEqualTo(2);
        assertThat(f.numForName("FLAT1")).isZero();
        assertThat(f.numForName("FLAT2")).isEqualTo(1);
        assertThat(f.name(1)).isEqualTo("FLAT2");
        assertThat(f.lump(1)).isEqualTo(d.find("FLAT2"));
        // Vanilla does not range check: a lump outside the markers still has a (negative) flat number.
        assertThat(f.numForName("OLD")).isNegative();
        assertThat(f.isValid(f.numForName("OLD"))).isFalse();
        assertThatThrownBy(() -> f.numForName("NOPE")).isInstanceOf(WadFormatException.class)
                .hasMessage("R_FlatNumForName: NOPE not found");
        LumpDirectory noMarkers = LumpDirectory.of(List.of(WadWriter.iwad().add("A", new byte[1]).toWadFile("n.wad")));
        assertThatThrownBy(() -> FlatDirectory.load(noMarkers)).isInstanceOf(WadFormatException.class);
    }

    @Test
    void spritesAreTheLumpsBetweenTheMarkers() {
        WadWriter w = WadWriter.iwad().marker("S_START").add("TROOA1", new byte[8]).add("TROOA2A8", new byte[8])
                .marker("S_END").add("OTHER", new byte[1]);
        LumpDirectory d = LumpDirectory.of(List.of(w.toWadFile("t.wad")));
        SpriteDirectory s = SpriteDirectory.load(d);
        assertThat(s.count()).isEqualTo(2);
        assertThat(s.firstLump()).isEqualTo(1);
        assertThat(s.name(1)).isEqualTo("TROOA2A8");
        assertThat(s.find("trooa1")).isZero();
        assertThat(s.find("OTHER")).isEqualTo(-1);
        assertThat(s.lump(1)).isEqualTo(2);
    }
}
