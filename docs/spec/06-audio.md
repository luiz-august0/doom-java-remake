# 06 — Audio

100% Java (ADR-012, ADR-021). Three pieces: **channel logic** (what to play, where and at what volume), **mixer** (sum of samples) and **music** (sequencer + OPL3 emulator).

```
simulation ──SimEvents──► SoundSystem (main thread) ──AudioCommandQueue──► audio thread
                           • picks channel                               ├─ SfxVoices (8..32)
                           • computes vol/sep every frame                ├─ MusicSequencer → Opl3 → resampler
                           • current music                               └─ sum → limiter → AudioSink (javax.sound)
```

## 1. Channel logic (original behaviour)

- **Channels:** 8 by default (configurable up to 32). Each channel stores the sound, the origin (actor or "no origin") and the priority.
- **Sound start:** if the origin is already playing a sound on a channel, it is **replaced** (exception: sounds with no origin). With no free channel, it replaces the one with the lowest priority, if the new priority is greater than or equal to it; otherwise the sound is discarded.
- **Attenuation:** from the approximate distance between the listener (the camera player) and the origin, with the original's cutoff distance and "near" distance. Beyond the cutoff, the sound does not play. **Exception:** map 8 (DOOM 1's boss maps, and also DOOM II's MAP08 — ADR-022) does not cut off by distance and have a minimum volume, as in the original.
- **Stereo separation:** by the angle between the listener's direction and the origin, with the same "spread" as the original (a sound straight ahead is centered).
- **Update:** every frame, channels with a moving origin recompute volume and separation. Channels whose sound has finished are freed.
- **Pitch variation:** existed in early versions of DOOM and is not in v1.9. Off by default (`sfxPitchVariation = false`).
- Effects and music volume: 0–15, as in the original menu.

This runs on the main thread and only **reads** the simulation state. Randomness, when there is any, uses the "misc" RNG.

## 2. Mixer

- Output format: 16-bit stereo PCM, 48 kHz (configurable: 44.1 kHz).
- DMX samples (unsigned 8-bit, normally 11025 Hz) converted to signed 16-bit at load time.
- **Resampling:** `nearest` (closer to the original "raw" sound) or linear (default). 32.32 fixed-point step per voice.
- Pan by a constant-power law from the 0–255 separation.
- Summing in `int`, *soft limiter* and conversion to `short`. No allocation per block: pre-allocated buffers.
- Block of 256–512 frames. The audio thread writes to the `SourceDataLine`, which blocks when the buffer is full. That blocking sets the pace of the loop.
- Music and effects have separate gains, applied in the mixer.

## 3. Music

### 3.1 Formats

- **MUS:** header (`MUS\x1A`, score length and offset, primary and secondary channels, instrument list) + events (release note, play note, *pitch bend*, system event, controller, end of measure, end) with a variable-size delay, in **140 Hz** ticks. MUS channel 15 is percussion → MIDI channel 9.
- **MIDI** (formats 0 and 1) for PWADs that bring `MThd`.
- Both become a single `MusicScore`: an ordered list of events with absolute time in ticks + the tick rate. The sequencer only knows this form.

### 3.2 Sequencer

- Runs **inside the audio thread**, paced by the sample clock: advances `samplesPerTick = outputRate / tickRate`. It does not use a wall clock, so there is no *drift* between music and mixer.
- *Loops* when the music asks for repetition, as in the original (level music repeats; so does the intermission's).
- Music changes coming from the simulation/UI arrive through the command queue.

### 3.3 OPL3 synthesis (default)

- **Emulator:** Java port of Nuked OPL3 (cycle-accurate, LGPL-2.1). Generates at the chip's native rate (~49,716 Hz), resampled to the output.
- **Instruments:** the IWAD's `GENMIDI` lump (128 melodic + 47 percussion, with 1 or 2 voices). That is why the music sounds like the DOS version with a Sound Blaster/AdLib card, with no external file.
- **Voice allocation:** 9 channels (OPL2 mode, as DMX used in DOOM) or 18 (OPL3 mode, optional). Voice-stealing policy, *pitch bend*, volume and pan according to the behaviour that Chocolate Doom reproduces from DMX (reference: its OPL music code, GPLv2).
- **Test:** reference vectors generated from Nuked OPL3 in C (sequences of register writes → expected samples). The Java port has to reproduce them **bit for bit**.

### 3.4 Alternative backends (post-v1)

`MusicSynth` allows plugging in a SoundFont through Gervill (`javax.sound.midi`) or the system MIDI. They are not part of v1.

## 4. Output (`AudioSink`)

- Default implementation in `doom-platform-javasound`: `AudioSystem.getSourceDataLine(format)`, buffer of ~2–4 blocks.
- *Underrun* → silence, *underrun* counter exposed in the debug overlay.
- Configurable output device (Java Sound mixer by name); the default is the system's.
- **Spike in M1:** measure the event → sound latency on the 3 OSes. Criterion: ≤ ~50 ms. If it fails on an OS, the OpenAL `AudioSink` comes in only on that OS (ADR-012).
