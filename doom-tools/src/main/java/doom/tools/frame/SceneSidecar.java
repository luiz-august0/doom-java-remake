package doom.tools.frame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A parsed oracle scene sidecar ({@code frame-<T>.scene}, tools/oracle/FORMAT.md section 3): everything the 3D renderer
 * needs to reproduce the view window without the simulation.
 */
public final class SceneSidecar {

    public String iwad = "";
    public String demo = "";
    public int tic;
    public int requested;
    public int automap;
    public String rawName = "";
    public int viewX;
    public int viewY;
    public int viewZ;
    public int viewAngle;
    public int extraLight;
    public int fixedColormap;
    public int displayPlayer;
    public int consolePlayer;
    public int windowX;
    public int windowY;
    public int windowWidth;
    public int windowHeight;
    public int detailShift;
    public int screenBlocks;
    public int palette;
    public int fuzzPos;
    public int skyTexture;
    public int skyFlat;
    public final int[] powers = new int[6];
    /** Psprites: 0 = weapon, 1 = flash. */
    public final int[] pspriteSprite = {-1, -1};
    public final int[] pspriteFrame = {-1, -1};
    public final int[] pspriteSx = new int[2];
    public final int[] pspriteSy = new int[2];
    public int[] textureTranslation = new int[0];
    public int[] flatTranslation = new int[0];

    public int[] sectorFloor = new int[0];
    public int[] sectorCeiling = new int[0];
    public int[] sectorFloorPic = new int[0];
    public int[] sectorCeilingPic = new int[0];
    public int[] sectorLight = new int[0];

    public int[] sideTextureOffset = new int[0];
    public int[] sideRowOffset = new int[0];
    public int[] sideTop = new int[0];
    public int[] sideBottom = new int[0];
    public int[] sideMid = new int[0];

    public int[] thingSector = new int[0];
    public int[] thingX = new int[0];
    public int[] thingY = new int[0];
    public int[] thingZ = new int[0];
    public int[] thingAngle = new int[0];
    public int[] thingSprite = new int[0];
    public int[] thingFrame = new int[0];
    public int[] thingFlags = new int[0];
    public int[] thingPlayer = new int[0];

    /** Parses a sidecar file. */
    public static SceneSidecar read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        SceneSidecar s = new SceneSidecar();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(" ");
            switch (f[0]) {
                case "#doom-scene" -> {
                    for (String kv : f) {
                        if (kv.startsWith("iwad=")) {
                            s.iwad = kv.substring(5);
                        } else if (kv.startsWith("demo=")) {
                            s.demo = kv.substring(5);
                        }
                    }
                }
                case "tic" -> {
                    s.tic = Integer.parseInt(f[1]);
                    s.requested = Integer.parseInt(f[3]);
                }
                case "automap" -> s.automap = Integer.parseInt(f[1]);
                case "raw" -> s.rawName = f[1];
                case "view" -> {
                    s.viewX = Integer.parseInt(f[1]);
                    s.viewY = Integer.parseInt(f[2]);
                    s.viewZ = Integer.parseInt(f[3]);
                    s.viewAngle = Integer.parseInt(f[4]);
                    s.extraLight = Integer.parseInt(f[5]);
                    s.fixedColormap = Integer.parseInt(f[6]);
                    s.displayPlayer = Integer.parseInt(f[7]);
                    s.consolePlayer = Integer.parseInt(f[8]);
                }
                case "window" -> {
                    s.windowX = Integer.parseInt(f[1]);
                    s.windowY = Integer.parseInt(f[2]);
                    s.windowWidth = Integer.parseInt(f[3]);
                    s.windowHeight = Integer.parseInt(f[4]);
                    s.detailShift = Integer.parseInt(f[5]);
                    s.screenBlocks = Integer.parseInt(f[6]);
                }
                case "palette" -> s.palette = Integer.parseInt(f[1]);
                case "fuzzpos" -> s.fuzzPos = Integer.parseInt(f[1]);
                case "sky" -> {
                    s.skyTexture = Integer.parseInt(f[1]);
                    s.skyFlat = Integer.parseInt(f[2]);
                }
                case "powers" -> {
                    for (int p = 0; p < 6; p++) {
                        s.powers[p] = Integer.parseInt(f[1 + p]);
                    }
                }
                case "psprite" -> {
                    int w = f[1].equals("weapon") ? 0 : 1;
                    s.pspriteSprite[w] = Integer.parseInt(f[2]);
                    s.pspriteFrame[w] = Integer.parseInt(f[3]);
                    s.pspriteSx[w] = Integer.parseInt(f[4]);
                    s.pspriteSy[w] = Integer.parseInt(f[5]);
                }
                case "texturetranslation" -> s.textureTranslation = ints(f, 2, Integer.parseInt(f[1]));
                case "flattranslation" -> s.flatTranslation = ints(f, 2, Integer.parseInt(f[1]));
                case "sectors" -> {
                    int n = Integer.parseInt(f[1]);
                    s.sectorFloor = new int[n];
                    s.sectorCeiling = new int[n];
                    s.sectorFloorPic = new int[n];
                    s.sectorCeilingPic = new int[n];
                    s.sectorLight = new int[n];
                    for (int k = 0; k < n; k++) {
                        String[] g = lines.get(++i).split(" ");
                        int idx = Integer.parseInt(g[1]);
                        s.sectorFloor[idx] = Integer.parseInt(g[2]);
                        s.sectorCeiling[idx] = Integer.parseInt(g[3]);
                        s.sectorFloorPic[idx] = Integer.parseInt(g[4]);
                        s.sectorCeilingPic[idx] = Integer.parseInt(g[5]);
                        s.sectorLight[idx] = Integer.parseInt(g[6]);
                    }
                }
                case "sides" -> {
                    int n = Integer.parseInt(f[1]);
                    s.sideTextureOffset = new int[n];
                    s.sideRowOffset = new int[n];
                    s.sideTop = new int[n];
                    s.sideBottom = new int[n];
                    s.sideMid = new int[n];
                    for (int k = 0; k < n; k++) {
                        String[] g = lines.get(++i).split(" ");
                        int idx = Integer.parseInt(g[1]);
                        s.sideTextureOffset[idx] = Integer.parseInt(g[2]);
                        s.sideRowOffset[idx] = Integer.parseInt(g[3]);
                        s.sideTop[idx] = Integer.parseInt(g[4]);
                        s.sideBottom[idx] = Integer.parseInt(g[5]);
                        s.sideMid[idx] = Integer.parseInt(g[6]);
                    }
                }
                case "things" -> {
                    int n = Integer.parseInt(f[1]);
                    s.thingSector = new int[n];
                    s.thingX = new int[n];
                    s.thingY = new int[n];
                    s.thingZ = new int[n];
                    s.thingAngle = new int[n];
                    s.thingSprite = new int[n];
                    s.thingFrame = new int[n];
                    s.thingFlags = new int[n];
                    s.thingPlayer = new int[n];
                    for (int k = 0; k < n; k++) {
                        String[] g = lines.get(++i).split(" ");
                        s.thingSector[k] = Integer.parseInt(g[1]);
                        s.thingX[k] = Integer.parseInt(g[2]);
                        s.thingY[k] = Integer.parseInt(g[3]);
                        s.thingZ[k] = Integer.parseInt(g[4]);
                        s.thingAngle[k] = Integer.parseInt(g[5]);
                        s.thingSprite[k] = Integer.parseInt(g[6]);
                        s.thingFrame[k] = Integer.parseInt(g[7]);
                        s.thingFlags[k] = Integer.parseInt(g[8]);
                        s.thingPlayer[k] = g.length > 9 ? Integer.parseInt(g[9]) : -1;
                    }
                }
                default -> {
                }
            }
        }
        return s;
    }

    private static int[] ints(String[] f, int from, int n) {
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = Integer.parseInt(f[from + i]);
        }
        return a;
    }
}
