package doom.app;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

/**
 * The {@link System.LoggerFinder} of the application (ADR-018): every module logs through {@code System.Logger}; this
 * class writes the messages of the configured severity and above to standard error and, once {@link #configure} gave it
 * a file, to the log file. File output never blocks the caller: messages go through a bounded queue to a daemon
 * writer thread (they are dropped, counted, if the queue is full), so the main loop does no file I/O (ADR-020).
 *
 * <p>The finder is created by the JDK before the application knows its settings; {@code Main} calls
 * {@link #configure} as soon as the command line and configuration are read. Until then INFO and above go to the
 * console.
 */
public final class ConsoleLoggerFinder extends System.LoggerFinder {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int QUEUE_CAPACITY = 8192;

    private volatile int threshold = System.Logger.Level.INFO.getSeverity();
    private volatile boolean console = true;
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private volatile Thread writer;
    private volatile long dropped;

    /** Sets the minimum severity and opens the log file (appending). Safe to call more than once. */
    public synchronized void configure(System.Logger.Level level, Path logFile, boolean toConsole) {
        this.threshold = level.getSeverity();
        this.console = toConsole;
        if (logFile != null && writer == null) {
            try {
                Path parent = logFile.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                var out = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                Thread t = new Thread(() -> drain(out), "doom-log-writer");
                t.setDaemon(true);
                t.start();
                writer = t;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> flush(out), "doom-log-flush"));
            } catch (IOException e) {
                System.err.println("cannot open log file " + logFile + ": " + e.getMessage());
            }
        }
    }

    private void drain(java.io.BufferedWriter out) {
        try {
            while (true) {
                String line = queue.take();
                out.write(line);
                out.write('\n');
                if (queue.isEmpty()) {
                    out.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("log file write failed: " + e.getMessage());
        }
    }

    private void flush(java.io.BufferedWriter out) {
        try {
            String line;
            while ((line = queue.poll()) != null) {
                out.write(line);
                out.write('\n');
            }
            if (dropped > 0) {
                out.write("(" + dropped + " log messages dropped)\n");
            }
            out.flush();
        } catch (IOException ignored) {
            // nothing useful left to do at exit
        }
    }

    @Override
    public System.Logger getLogger(String name, Module module) {
        return new AppLogger(name);
    }

    private final class AppLogger implements System.Logger {

        private final String name;

        AppLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(Level level) {
            return level != Level.OFF && level.getSeverity() >= threshold;
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            if (!isLoggable(level)) {
                return;
            }
            StringBuilder sb = new StringBuilder(96);
            sb.append(LocalTime.now().format(TIME)).append(' ').append(level.getName()).append(' ').append(name)
                    .append(": ").append(msg);
            if (thrown != null) {
                StringWriter sw = new StringWriter();
                thrown.printStackTrace(new PrintWriter(sw));
                sb.append('\n').append(sw.toString().stripTrailing());
            }
            String line = sb.toString();
            if (console) {
                System.err.println(line);
            }
            if (writer != null && !queue.offer(line)) {
                dropped++;
            }
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... params) {
            if (isLoggable(level)) {
                log(level, bundle, params == null || params.length == 0 ? format : MessageFormat.format(format, params),
                        (Throwable) null);
            }
        }

        @Override
        public void log(Level level, Supplier<String> msgSupplier) {
            if (isLoggable(level)) {
                log(level, null, msgSupplier.get(), (Throwable) null);
            }
        }
    }
}
