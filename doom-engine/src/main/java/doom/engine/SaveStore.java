package doom.engine;

import doom.ui.SaveSlots;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Save game files of one IWAD: {@code doomsav<N>.dsg} holds the snapshot bytes produced by the session, and
 * {@code doomsav<N>.txt} the description for the menus. Writing happens on a virtual thread (ADR-020).
 */
final class SaveStore implements SaveSlots {

    private static final System.Logger LOG = System.getLogger(SaveStore.class.getName());

    private final Path dir;
    private final List<CompletableFuture<Void>> pending = new CopyOnWriteArrayList<>();

    SaveStore(Path dir) {
        this.dir = dir;
    }

    Path file(int slot) {
        return dir.resolve("doomsav" + slot + ".dsg");
    }

    private Path descriptionFile(int slot) {
        return dir.resolve("doomsav" + slot + ".txt");
    }

    /** Writes a snapshot; the arrays are not touched afterwards by the caller. */
    void writeAsync(int slot, String description, byte[] data) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        pending.add(done);
        Thread.ofVirtual().name("save-writer").start(() -> {
            try {
                Files.createDirectories(dir);
                // a crash or a full disk in the middle must not destroy the previous save of the slot
                writeReplacing(file(slot), data);
                writeReplacing(descriptionFile(slot), description.getBytes(StandardCharsets.UTF_8));
                LOG.log(System.Logger.Level.INFO, "saved game to {0}", file(slot));
                done.complete(null);
            } catch (IOException | RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "cannot save game to " + file(slot), e);
                done.completeExceptionally(e);
            } finally {
                pending.remove(done);
            }
        });
    }

    private static void writeReplacing(Path target, byte[] bytes) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The snapshot of a slot, or {@code null} when there is none. */
    byte[] read(int slot) {
        try {
            Path f = file(slot);
            return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "cannot read " + file(slot), e);
            return null;
        }
    }

    @Override
    public String description(int slot) {
        try {
            Path f = descriptionFile(slot);
            if (!Files.isRegularFile(f) || !Files.isRegularFile(file(slot))) {
                return null;
            }
            String s = Files.readString(f, StandardCharsets.UTF_8);
            return s.length() > 24 ? s.substring(0, 24) : s;
        } catch (IOException e) {
            return null;
        }
    }

    /** Waits for the writes in progress. */
    void await() {
        for (CompletableFuture<Void> f : List.copyOf(pending)) {
            try {
                f.join();
            } catch (RuntimeException ignored) {
                // already logged
            }
        }
    }
}
