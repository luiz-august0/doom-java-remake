package doom.wad;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All lumps of the loaded WADs, concatenated in load order (IWAD first, then PWADs). Lookup by name returns the
 * <b>last</b> lump with that name, as {@code W_CheckNumForName} does (spec 05 §1).
 *
 * <p>By default PWADs are simply appended (vanilla {@code -file} semantics: a PWAD that brings its own
 * {@code S_START}/{@code F_START} sections replaces the whole sprite or flat list, as in DOOM.EXE). The
 * {@link Builder#merge(WadFile)} operation reproduces Chocolate Doom's {@code -merge} ({@code w_merge.c}), which folds
 * the PWAD's sprites and flats into the existing sections.
 */
public final class LumpDirectory {

    private final List<WadFile> wads;
    private final WadFile[] lumpWad;
    private final int[] lumpIndex;
    private final String[] names;
    /** Name to last lump number with that name. Lookup only; never iterated (no order dependence). */
    private final Map<String, Integer> lastByName;

    private LumpDirectory(List<WadFile> wads, List<Entry> entries) {
        this.wads = List.copyOf(wads);
        int total = entries.size();
        lumpWad = new WadFile[total];
        lumpIndex = new int[total];
        names = new String[total];
        lastByName = new HashMap<>(total * 2);
        for (int n = 0; n < total; n++) {
            Entry e = entries.get(n);
            lumpWad[n] = e.wad;
            lumpIndex[n] = e.index;
            names[n] = e.name;
            lastByName.put(e.name, n);
        }
    }

    /** Opens the given files in order (IWAD first) with {@code W_AddFile} semantics (see {@link WadFile#load}). */
    public static LumpDirectory open(List<Path> files) throws IOException {
        Builder b = builder();
        for (Path p : files) {
            b.add(WadFile.load(p));
        }
        return b.build();
    }

    /** Builds a directory over already opened WADs (IWAD first), vanilla semantics. */
    public static LumpDirectory of(List<WadFile> wads) {
        Builder b = builder();
        for (WadFile w : wads) {
            b.add(w);
        }
        return b.build();
    }

    /** A builder; operations apply in call order (Chocolate Doom runs every {@code -merge} before {@code -file}). */
    public static Builder builder() {
        return new Builder();
    }

    /** The WAD files in load order. */
    public List<WadFile> wads() {
        return wads;
    }

    /** Number of lumps ({@code numlumps}). */
    public int size() {
        return names.length;
    }

    /** {@code W_CheckNumForName}: number of the last lump with this name (case-insensitive, 8 characters), or -1. */
    public int find(String name) {
        Integer n = lastByName.get(LumpNames.key(name));
        return n == null ? -1 : n;
    }

    /** Number of the last lump with this name inside the given namespace, or -1. */
    public int find(String name, LumpNamespace namespace) {
        String key = LumpNames.key(name);
        for (int i = names.length - 1; i >= 0; i--) {
            if (names[i].equals(key) && namespace(i) == namespace) {
                return i;
            }
        }
        return -1;
    }

    /** {@code W_GetNumForName}: like {@link #find} but fails with the original message when absent. */
    public int get(String name) {
        int n = find(name);
        if (n < 0) {
            throw new WadFormatException("W_GetNumForName: " + LumpNames.key(name) + " not found!");
        }
        return n;
    }

    /** Upper-case name of lump {@code lump}. */
    public String name(int lump) {
        return names[lump];
    }

    /** Size in bytes of lump {@code lump}. */
    public int length(int lump) {
        return lumpWad[lump].lumpSize(lumpIndex[lump]);
    }

    /** Read-only little-endian view of the lump data, positioned at 0. No copy. */
    public ByteBuffer data(int lump) {
        return lumpWad[lump].lumpData(lumpIndex[lump]);
    }

    /** Copy of the lump data. */
    public byte[] bytes(int lump) {
        ByteBuffer b = data(lump);
        byte[] out = new byte[b.remaining()];
        b.get(out);
        return out;
    }

    /** The WAD file that contains lump {@code lump}. */
    public WadFile wadOf(int lump) {
        return lumpWad[lump];
    }

    /** Marker section of the lump inside its own file. */
    public LumpNamespace namespace(int lump) {
        return lumpWad[lump].namespace(lumpIndex[lump]);
    }

    /** {@code W_IsIWADLump}: true when the lump comes from the first loaded file. */
    public boolean isIwadLump(int lump) {
        return !wads.isEmpty() && lumpWad[lump] == wads.get(0);
    }

    /** Description of lump {@code lump}. */
    public Lump lump(int lump) {
        WadFile w = lumpWad[lump];
        int i = lumpIndex[lump];
        return new Lump(lump, names[lump], w.lumpSize(i), w.lumpOffset(i), w, i, w.namespace(i));
    }

    /** One lump of one file, with identity (Chocolate's {@code lumpinfo_t *}). */
    private static final class Entry {
        final WadFile wad;
        final int index;
        final String name;

        Entry(WadFile wad, int index) {
            this.wad = wad;
            this.index = index;
            this.name = wad.lumpName(index);
        }
    }

    /** Builds a {@link LumpDirectory} from {@code -file} additions and {@code -merge} merges. */
    public static final class Builder {

        private final List<WadFile> wads = new ArrayList<>();
        private List<Entry> entries = new ArrayList<>();

        private Builder() {
        }

        /** {@code W_AddFile}: appends every lump of the file. */
        public Builder add(WadFile wad) {
            wads.add(wad);
            for (int i = 0; i < wad.lumpCount(); i++) {
                entries.add(new Entry(wad, i));
            }
            return this;
        }

        /**
         * {@code W_MergeFile} (Chocolate Doom {@code -merge}, deutex semantics): the file's flats and sprites are
         * folded into the sections of the lumps loaded so far, its other lumps are appended.
         *
         * @throws WadFormatException when the lumps loaded so far have no flat or sprite section
         */
        public Builder merge(WadFile wad) {
            wads.add(wad);
            List<Entry> pwad = new ArrayList<>(wad.lumpCount());
            for (int i = 0; i < wad.lumpCount(); i++) {
                pwad.add(new Entry(wad, i));
            }
            entries = Merger.merge(entries, pwad, wad.fileName());
            return this;
        }

        public LumpDirectory build() {
            return new LumpDirectory(wads, entries);
        }
    }

    /** Port of Chocolate Doom's {@code w_merge.c} ({@code W_MergeFile}). */
    private static final class Merger {

        private final List<Entry> iwadFlats;
        private final List<Entry> iwadSprites;
        private final List<Entry> pwadFlats;
        private final List<Entry> pwadSprites;
        private final List<SpriteFrame> spriteFrames = new ArrayList<>();

        private Merger(List<Entry> iwad, List<Entry> pwad, String file) {
            iwadFlats = section(iwad, "F_START", "F_END", null, null);
            if (iwadFlats == null) {
                throw new WadFormatException(file, null, -1, "Flats section not found in IWAD");
            }
            iwadSprites = section(iwad, "S_START", "S_END", null, null);
            if (iwadSprites == null) {
                throw new WadFormatException(file, null, -1, "Sprites section not found in IWAD");
            }
            List<Entry> f = section(pwad, "F_START", "F_END", "FF_START", "FF_END");
            pwadFlats = f == null ? List.of() : f;
            List<Entry> s = section(pwad, "S_START", "S_END", "SS_START", "SS_END");
            pwadSprites = s == null ? List.of() : s;
        }

        static List<Entry> merge(List<Entry> iwad, List<Entry> pwad, String file) {
            Merger m = new Merger(iwad, pwad, file);
            m.generateSpriteList();
            return m.doMerge(iwad, pwad);
        }

        /** {@code FindInList}: first entry with that name, or -1. */
        private static int findInList(List<Entry> list, String name) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).name.equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        /** {@code SetupList}: the lumps strictly between the start and end markers, or null. */
        private static List<Entry> section(List<Entry> src, String start, String end, String start2, String end2) {
            int startLump = findInList(src, start);
            if (start2 != null && startLump < 0) {
                startLump = findInList(src, start2);
            }
            if (startLump >= 0) {
                int endLump = findInList(src, end);
                if (end2 != null && endLump < 0) {
                    endLump = findInList(src, end2);
                }
                if (endLump > startLump) {
                    return src.subList(startLump + 1, endLump);
                }
            }
            return null;
        }

        private static final class SpriteFrame {
            final String sprName;
            final char frame;
            final Entry[] angleLumps = new Entry[8];

            SpriteFrame(String sprName, char frame) {
                this.sprName = sprName;
                this.frame = frame;
            }
        }

        /** {@code ValidSpriteLumpName}. */
        private static boolean validSpriteLumpName(String name) {
            if (name.length() < 4) {
                return false;
            }
            if (name.length() < 6 || !isDigit(name.charAt(5))) {
                return false;
            }
            return name.length() < 7 || (name.length() == 8 && isDigit(name.charAt(7)));
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        /** {@code FindSpriteFrame}: finds or appends the frame record. */
        private SpriteFrame findSpriteFrame(String name, char frame) {
            String spr = name.substring(0, 4);
            for (SpriteFrame f : spriteFrames) {
                if (f.sprName.equals(spr) && f.frame == frame) {
                    return f;
                }
            }
            SpriteFrame f = new SpriteFrame(spr, frame);
            spriteFrames.add(f);
            return f;
        }

        private boolean frameUses(Entry lump, char frame, char angle) {
            SpriteFrame sprite = findSpriteFrame(lump.name, frame);
            int angleNum = angle - '0';
            if (angleNum == 0) {
                for (int i = 0; i < 8; i++) {
                    if (sprite.angleLumps[i] == lump) {
                        return true;
                    }
                }
                return false;
            }
            // DIVERGE: rotation 9 indexes past angle_lumps[8] in C (undefined); it never matches here.
            return angleNum <= 8 && sprite.angleLumps[angleNum - 1] == lump;
        }

        /** {@code SpriteLumpNeeded}. */
        private boolean spriteLumpNeeded(Entry lump) {
            String name = lump.name;
            if (!validSpriteLumpName(name)) {
                return true;
            }
            if (frameUses(lump, name.charAt(4), name.charAt(5))) {
                return true;
            }
            if (name.length() < 7) {
                return false;
            }
            return frameUses(lump, name.charAt(6), name.charAt(7));
        }

        private void install(Entry lump, char frame, char angle) {
            SpriteFrame sprite = findSpriteFrame(lump.name, frame);
            int angleNum = angle - '0';
            if (angleNum == 0) {
                for (int i = 0; i < 8; i++) {
                    sprite.angleLumps[i] = lump;
                }
            } else if (angleNum <= 8) {
                sprite.angleLumps[angleNum - 1] = lump;
            }
        }

        /** {@code AddSpriteLump}. */
        private void addSpriteLump(Entry lump) {
            String name = lump.name;
            if (!validSpriteLumpName(name)) {
                return;
            }
            install(lump, name.charAt(4), name.charAt(5));
            if (name.length() < 7) {
                return;
            }
            install(lump, name.charAt(6), name.charAt(7));
        }

        /** {@code GenerateSpriteList}. */
        private void generateSpriteList() {
            for (Entry e : iwadSprites) {
                addSpriteLump(e);
            }
            for (Entry e : pwadSprites) {
                addSpriteLump(e);
            }
        }

        private enum Section { NORMAL, FLATS, SPRITES }

        /** {@code DoMerge}. */
        private List<Entry> doMerge(List<Entry> iwad, List<Entry> pwad) {
            List<Entry> out = new ArrayList<>(iwad.size() + pwad.size());
            Section section = Section.NORMAL;
            for (Entry lump : iwad) {
                switch (section) {
                    case NORMAL -> {
                        if (lump.name.equals("F_START")) {
                            section = Section.FLATS;
                        } else if (lump.name.equals("S_START")) {
                            section = Section.SPRITES;
                        }
                        out.add(lump);
                    }
                    case FLATS -> {
                        if (lump.name.equals("F_END")) {
                            out.addAll(pwadFlats);
                            out.add(lump);
                            section = Section.NORMAL;
                        } else if (findInList(pwadFlats, lump.name) < 0) {
                            out.add(lump);
                        }
                    }
                    case SPRITES -> {
                        if (lump.name.equals("S_END")) {
                            for (Entry p : pwadSprites) {
                                if (spriteLumpNeeded(p)) {
                                    out.add(p);
                                }
                            }
                            out.add(lump);
                            section = Section.NORMAL;
                        } else if (spriteLumpNeeded(lump)) {
                            out.add(lump);
                        }
                    }
                }
            }
            section = Section.NORMAL;
            for (Entry lump : pwad) {
                switch (section) {
                    case NORMAL -> {
                        if (lump.name.equals("F_START") || lump.name.equals("FF_START")) {
                            section = Section.FLATS;
                        } else if (lump.name.equals("S_START") || lump.name.equals("SS_START")) {
                            section = Section.SPRITES;
                        } else {
                            out.add(lump);
                        }
                    }
                    case FLATS -> {
                        if (lump.name.equals("FF_END") || lump.name.equals("F_END")) {
                            section = Section.NORMAL;
                        }
                    }
                    case SPRITES -> {
                        if (lump.name.equals("SS_END") || lump.name.equals("S_END")) {
                            section = Section.NORMAL;
                        }
                    }
                }
            }
            return out;
        }
    }
}
