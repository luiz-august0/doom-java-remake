package doom.wad.map;

import doom.wad.LumpDirectory;
import doom.wad.LumpReader;
import doom.wad.WadFormatException;
import java.util.Locale;

/**
 * All lumps of one map, decoded. {@code reject} is the raw REJECT lump (the loader pads it like Chocolate Doom) and
 * {@code blockmap} the signed 16-bit values of the BLOCKMAP lump.
 */
public record MapData(String name, MapThing[] things, MapLinedef[] linedefs, MapSidedef[] sidedefs,
                      MapVertex[] vertexes, MapSeg[] segs, MapSubsector[] subsectors, MapNode[] nodes,
                      MapSector[] sectors, byte[] reject, int[] blockmap) {

    /**
     * Reads the ten lumps that follow the marker {@code mapName} (as {@code P_SetupLevel} does with
     * {@code lumpnum + ML_*}). Unlike DOOM, which would read whatever lump follows, the lump names are verified.
     *
     * @throws WadFormatException when the map is missing or a lump is not where it should be
     */
    public static MapData load(LumpDirectory lumps, String mapName) {
        int marker = lumps.find(mapName);
        if (marker < 0) {
            throw new WadFormatException("W_GetNumForName: " + mapName.toUpperCase(Locale.ROOT) + " not found!");
        }
        String name = lumps.name(marker);
        LumpReader[] r = new LumpReader[MapLump.values().length];
        for (MapLump l : MapLump.values()) {
            int n = marker + l.offsetFromMarker();
            if (n >= lumps.size() || !lumps.name(n).equals(l.name())) {
                throw new WadFormatException(lumps.wadOf(marker).fileName(), name, -1,
                        "map lump " + l + " is missing (found "
                                + (n < lumps.size() ? lumps.name(n) : "end of directory") + " in its place)");
            }
            r[l.ordinal()] = LumpReader.of(lumps, n, name + " " + l);
        }
        return new MapData(name,
                MapLumps.things(r[MapLump.THINGS.ordinal()]),
                MapLumps.linedefs(r[MapLump.LINEDEFS.ordinal()]),
                MapLumps.sidedefs(r[MapLump.SIDEDEFS.ordinal()]),
                MapLumps.vertexes(r[MapLump.VERTEXES.ordinal()]),
                MapLumps.segs(r[MapLump.SEGS.ordinal()]),
                MapLumps.subsectors(r[MapLump.SSECTORS.ordinal()]),
                MapLumps.nodes(r[MapLump.NODES.ordinal()]),
                MapLumps.sectors(r[MapLump.SECTORS.ordinal()]),
                r[MapLump.REJECT.ordinal()].bytes(),
                MapLumps.blockmap(r[MapLump.BLOCKMAP.ordinal()]));
    }
}
