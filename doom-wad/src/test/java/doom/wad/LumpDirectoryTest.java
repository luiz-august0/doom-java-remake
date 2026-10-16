package doom.wad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LumpDirectoryTest {

    private static WadFile iwad() {
        return WadWriter.iwad()
                .add("PLAYPAL", new byte[] {1})
                .marker("S_START")
                .add("TROOA1", new byte[] {1})
                .add("TROOB1", new byte[] {1})
                .add("POSSA2A8", new byte[] {1})
                .marker("S_END")
                .marker("F_START")
                .add("FLOOR0_1", new byte[] {1})
                .add("NUKAGE1", new byte[] {1})
                .marker("F_END")
                .toWadFile("iwad.wad");
    }

    private static List<String> names(LumpDirectory d) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < d.size(); i++) {
            out.add(d.name(i) + "@" + d.wadOf(i).fileName());
        }
        return out;
    }

    @Test
    void lookupIsCaseInsensitiveAndUsesEightCharacters() {
        LumpDirectory d = LumpDirectory.of(List.of(iwad()));
        assertThat(d.find("possa2a8")).isEqualTo(4);
        assertThat(d.find("POSSA2A8EXTRA")).isEqualTo(4);
        assertThat(d.find("TROOA1\0junk")).isEqualTo(2);
        assertThat(d.find("nope")).isEqualTo(-1);
        assertThatThrownBy(() -> d.get("nope"))
                .isInstanceOf(WadFormatException.class)
                .hasMessage("W_GetNumForName: NOPE not found!");
    }

    @Test
    void lastLumpWinsAndNamespacesComeFromTheOwnFile() {
        WadFile pwad = WadWriter.pwad().add("NUKAGE1", new byte[] {2}).toWadFile("p.wad");
        LumpDirectory d = LumpDirectory.of(List.of(iwad(), pwad));
        int n = d.find("NUKAGE1");
        assertThat(n).isEqualTo(d.size() - 1);
        assertThat(d.namespace(n)).isEqualTo(LumpNamespace.GLOBAL);
        assertThat(d.find("NUKAGE1", LumpNamespace.FLATS)).isEqualTo(8);
        assertThat(d.isIwadLump(8)).isTrue();
        assertThat(d.isIwadLump(n)).isFalse();
        Lump l = d.lump(n);
        assertThat(l.name()).isEqualTo("NUKAGE1");
        assertThat(l.size()).isEqualTo(1);
        assertThat(l.wad().fileName()).isEqualTo("p.wad");
        assertThat(l.indexInWad()).isZero();
    }

    @Test
    void mergeFoldsFlatsAndSpritesIntoTheIwadSections() {
        WadFile pwad = WadWriter.pwad()
                .add("MAP01", new byte[0])
                .marker("FF_START")
                .add("NUKAGE1", new byte[] {7})
                .add("NEWFLAT", new byte[] {7})
                .marker("FF_END")
                .marker("SS_START")
                .add("TROOA1", new byte[] {7})
                .add("POSSA2", new byte[] {7})
                .marker("SS_END")
                .add("DEHACKED", new byte[] {7})
                .toWadFile("p.wad");
        LumpDirectory d = LumpDirectory.builder().add(iwad()).merge(pwad).build();
        assertThat(names(d)).containsExactly(
                "PLAYPAL@iwad.wad",
                "S_START@iwad.wad",
                "TROOB1@iwad.wad",
                "POSSA2A8@iwad.wad",
                "TROOA1@p.wad",
                "POSSA2@p.wad",
                "S_END@iwad.wad",
                "F_START@iwad.wad",
                "FLOOR0_1@iwad.wad",
                "NUKAGE1@p.wad",
                "NEWFLAT@p.wad",
                "F_END@iwad.wad",
                "MAP01@p.wad",
                "DEHACKED@p.wad");
        assertThat(d.wads()).hasSize(2);
    }

    @Test
    void mergeKeepsAnIwadSpriteStillUsedForAnotherRotation() {
        // POSSA2A8 provides frame A rotation 2 and frame A rotation 8; the PWAD replaces only rotation 2,
        // so the IWAD lump is still needed for rotation 8.
        WadFile pwad = WadWriter.pwad().marker("S_START").add("POSSA2", new byte[] {7}).marker("S_END")
                .toWadFile("p.wad");
        LumpDirectory d = LumpDirectory.builder().add(iwad()).merge(pwad).build();
        assertThat(names(d)).contains("POSSA2A8@iwad.wad", "POSSA2@p.wad");
        // A full replacement (rotation 0) removes the IWAD lump.
        WadFile pwad0 = WadWriter.pwad().marker("S_START").add("POSSA0", new byte[] {7}).marker("S_END")
                .toWadFile("p0.wad");
        LumpDirectory d0 = LumpDirectory.builder().add(iwad()).merge(pwad0).build();
        assertThat(names(d0)).doesNotContain("POSSA2A8@iwad.wad").contains("POSSA0@p0.wad");
    }

    @Test
    void mergeNeedsIwadSections() {
        WadFile noFlats = WadWriter.iwad().marker("S_START").marker("S_END").toWadFile("i.wad");
        WadFile pwad = WadWriter.pwad().add("A", new byte[1]).toWadFile("p.wad");
        assertThatThrownBy(() -> LumpDirectory.builder().add(noFlats).merge(pwad))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("Flats section not found in IWAD");
        WadFile noSprites = WadWriter.iwad().marker("F_START").marker("F_END").toWadFile("i.wad");
        assertThatThrownBy(() -> LumpDirectory.builder().add(noSprites).merge(pwad))
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("Sprites section not found in IWAD");
    }

    @Test
    void vanillaAppendKeepsPwadSections() {
        WadFile pwad = WadWriter.pwad().marker("F_START").add("NEWFLAT", new byte[1]).marker("F_END")
                .toWadFile("p.wad");
        LumpDirectory d = LumpDirectory.of(List.of(iwad(), pwad));
        assertThat(d.find("F_START")).isEqualTo(d.size() - 3);
        assertThat(d.size()).isEqualTo(iwad().lumpCount() + 3);
    }
}
