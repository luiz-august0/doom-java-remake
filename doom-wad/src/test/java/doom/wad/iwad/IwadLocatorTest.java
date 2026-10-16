package doom.wad.iwad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import doom.wad.WadFormatException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IwadLocatorTest {

    private static Path touch(Path p) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[] {1});
        return p;
    }

    @Test
    void searchOrderIsDoomWadDirThenPathThenCurrentThenAppData(@TempDir Path root) throws Exception {
        Path wadDir = root.resolve("waddir");
        Path pathA = root.resolve("a");
        Path pathB = root.resolve("b");
        Path cwd = root.resolve("cwd");
        Path app = root.resolve("app");
        Files.createDirectories(wadDir);
        touch(pathB.resolve("freedoom2.wad"));
        touch(cwd.resolve("doom2.wad"));
        touch(app.resolve("doom1.wad"));
        IwadLocator l = new IwadLocator(null, wadDir.toString(), pathA + File.pathSeparator + pathB, cwd, app);
        assertThat(l.searchPath()).containsExactly(wadDir.toString(), pathA.toString(), pathB.toString(),
                cwd.toString(), app.toString());
        // The first directory holding any IWAD wins, even if a later directory holds a preferred name.
        assertThat(l.locate()).contains(pathB.resolve("freedoom2.wad"));
    }

    @Test
    void namesAreTriedInOrderInsideADirectory(@TempDir Path root) throws Exception {
        touch(root.resolve("freedoom1.wad"));
        touch(root.resolve("doom1.wad"));
        touch(root.resolve("freedm.wad"));
        IwadLocator l = new IwadLocator(null, null, null, root, null);
        assertThat(l.locate()).contains(root.resolve("doom1.wad"));
    }

    @Test
    void fileNamesMatchCaseInsensitively(@TempDir Path root) throws Exception {
        Path upper = touch(root.resolve("DOOM2.WAD"));
        IwadLocator l = new IwadLocator(null, root.toString(), null, null, null);
        assertThat(l.locate()).hasValueSatisfying(p -> assertThat(p.getFileName().toString())
                .isEqualToIgnoringCase(upper.getFileName().toString()));
    }

    @Test
    void doomWadDirMayNameTheIwadDirectly(@TempDir Path root) throws Exception {
        Path iwad = touch(root.resolve("sub").resolve("freedm.wad"));
        IwadLocator l = new IwadLocator(null, iwad.toString(), null, null, null);
        assertThat(l.locate()).contains(iwad);
    }

    @Test
    void iwadArgumentIsAPathOrANameInTheSearchPath(@TempDir Path root) throws Exception {
        Path custom = touch(root.resolve("dir").resolve("custom.wad"));
        assertThat(new IwadLocator(custom.toString(), null, null, null, null).locate()).contains(custom);
        IwadLocator byName = new IwadLocator("custom.wad", custom.getParent().toString(), null, null, null);
        assertThat(byName.locate()).contains(custom);
        IwadLocator missing = new IwadLocator("missing.wad", root.toString(), null, null, null);
        assertThat(missing.locate()).isEmpty();
        assertThatThrownBy(missing::require)
                .isInstanceOf(WadFormatException.class)
                .hasMessage("IWAD file 'missing.wad' not found!");
        assertThatThrownBy(new IwadLocator(null, null, null, root.resolve("empty"), null)::require)
                .isInstanceOf(WadFormatException.class)
                .hasMessageContaining("No IWAD file was found");
    }

    @Test
    void bundledDirectoriesComeLastInTheSearchPath(@TempDir Path root) throws Exception {
        Path cwd = root.resolve("cwd");
        Path app = root.resolve("app");
        Path bundled = root.resolve("pkg").resolve("iwads");
        touch(bundled.resolve("doom1.wad"));
        IwadLocator l = new IwadLocator(null, null, null, cwd, app, java.util.List.of(bundled));
        assertThat(l.searchPath()).containsExactly(cwd.toString(), app.toString(), bundled.toString());
        assertThat(l.locate()).contains(bundled.resolve("doom1.wad"));
        // A WAD in an earlier directory (the current one) wins over the bundled doom1.wad.
        Path local = touch(cwd.resolve("freedm.wad"));
        assertThat(l.locate()).contains(local);
    }

    @Test
    void bundledDirectoriesFollowTheLauncherJarAndEnvironment(@TempDir Path root) {
        Path launcher = root.resolve("DoomJava").resolve("bin").resolve("DoomJava");
        Path jar = root.resolve("lib").resolve("doom-java.jar");
        var dirs = IwadLocator.bundledDirectories(launcher.toString(), jar, root.resolve("env").toString());
        assertThat(dirs).containsExactly(
                root.resolve("env").resolve("iwads"),
                root.resolve("env").resolve(".iwads"),
                root.resolve("DoomJava").resolve("bin").resolve("iwads"),
                root.resolve("DoomJava").resolve("iwads"),
                root.resolve("DoomJava").resolve("Resources").resolve("iwads"),
                root.resolve("lib").resolve("iwads"),
                root.resolve("iwads"),
                Path.of("/opt/doom-java/iwads"));
        assertThat(IwadLocator.bundledDirectories(null, null, null)).containsExactly(Path.of("/opt/doom-java/iwads"));
    }

    @Test
    void anIwadNextToTheLauncherIsFound(@TempDir Path root) throws Exception {
        Path launcher = root.resolve("App.app").resolve("Contents").resolve("MacOS").resolve("App");
        Path wad = touch(root.resolve("App.app").resolve("Contents").resolve("iwads").resolve("doom1.wad"));
        var dirs = IwadLocator.bundledDirectories(launcher.toString(), null, null);
        assertThat(new IwadLocator(null, null, null, null, null, dirs).locate()).contains(wad);
    }
}
