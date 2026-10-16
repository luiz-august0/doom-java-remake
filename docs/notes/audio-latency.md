# Audio output latency (M1 spike, ADR-012)

Criterion: event to sound at most about 50 ms, otherwise an OpenAL sink is added on that OS only.

## What was measured

`doom-it/src/test/java/doom/it/audio/LatencySpike.java` (a manual `main`, not a test). For each combination of device
buffer and mixer block size it starts the real audio thread on a `SourceDataLine` (48 kHz, 16-bit stereo), plays a
looping song (D_E1M1 through the OPL3 port, so the thread has realistic load) and starts a short sound effect 40 times at
random moments. It records the time from `AudioCommandQueue.startSfx` until `SourceDataLine.getLongFramePosition()`
reaches the first frame of the block that contains the sound. It also samples how many frames sit in the line buffer.

Machine: Apple Silicon Mac, macOS (Darwin 25.6), OpenJDK 25.0.1, default output device through Java Sound
(CoreAudio "DirectAudio" backend), no other audio load.

| device buffer (frames / ms) | block | queued frames avg / min | event to played, median / p90 / max (ms) | underruns |
|---|---|---|---|---|
| 512 / 10.7 | 256 | 486 / 0 | 14.2 / 18.9 / 20.1 | 0 |
| 1024 / 21.3 | 256 | 979 / 512 | 25.6 / 30.0 / 31.4 | 0 |
| 2048 / 42.7 | 256 | 1632 / 1024 | 39.2 / 44.0 / 49.3 | 0 |
| 4096 / 85.3 | 256 | 3430 / 2560 | 74.0 / 86.0 / 90.8 | 0 |
| 512 / 10.7 | 512 | 486 / 0 | 15.2 / 19.2 / 20.9 | 0 |
| 1024 / 21.3 | 512 | 998 / 512 | 25.2 / 28.3 / 30.1 | 0 |
| 2048 / 42.7 | 512 | 1741 / 1024 | 40.8 / 48.8 / 59.3 | 0 |
| 4096 / 85.3 | 512 | 3379 / 2560 | 73.5 / 89.6 / 93.6 | 0 |

The granted line buffer always equals the requested one, so Java Sound honours the size on this Mac.

Reading the table:

* The latency is dominated by how full the line buffer is: the blocking `write` keeps it nearly full, so
  event to sound is about one device buffer plus the wait for the next block (at most one block, 5 to 10 ms).
* A 512 frame buffer works but has no margin: the buffer is found empty at times (`min` 0), so a scheduling hiccup
  becomes an audible gap even though the underrun counter stayed at 0 in this run (the counter only sees a line that is
  completely empty when a write starts).
* 1024 frames with 256 or 512 frame blocks gives a stable queue (never below 512 frames) and about 25 to 30 ms.

## What is not included

Java Sound reports the position of frames consumed by its own driver layer. The operating system's output latency
(CoreAudio IO buffer, typically 5 to 12 ms on a built-in output, more on Bluetooth) is not visible to Java and is not
in the numbers. Adding about 10 to 15 ms for a built-in output gives roughly 35 to 45 ms end to end for the 1024 frame
configuration, within the 50 ms budget. Bluetooth headsets are outside the criterion with any backend.
Only macOS was measured; Windows and Linux still have to be checked when those machines are available (the same
`LatencySpike` runs unchanged).

## Decision

Keep Java Sound (`javax.sound.sampled`) as the sink; no OpenAL sink is needed on macOS.

* `AudioEngineConfig.defaults()`: 48 kHz, mixer block 512 frames, device buffer = 2 blocks (1024 frames, 21 ms).
* A "low latency" setting (block 256, device buffer 512 or 768) is possible on machines that keep up; the underrun counter
  (`AudioSink.underruns()`) is there to tell.
* Device buffers of 2048 frames and above exceed the budget once OS latency is added and should not be the default.
