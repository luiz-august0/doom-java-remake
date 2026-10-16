package doom.sound.music;

import doom.math.annotation.VanillaRef;
import doom.wad.audio.GenMidi;

/**
 * The DMX OPL music driver: turns MIDI events into OPL register writes with the voice allocation, stealing, volume
 * mapping, frequency curve and pitch bend of DMX 1.9 as reproduced by Chocolate Doom's {@code i_oplmusic.c}. It owns no
 * clock; {@link OplMusicPlayer} schedules the events and owns the chip.
 *
 * <p>Voices: 9 in OPL2 mode (what DMX used in DOOM), 18 in OPL3 mode, where the second register array is used and the
 * stereo pan bits are driven by MIDI pan. MUS channel 15 (percussion) arrives here as MIDI channel 9 and is swapped
 * back to internal channel 15 exactly like the original, so internal channel numbers follow MUS numbering.
 *
 * <p>No allocation after construction; not thread safe.
 */
@VanillaRef("i_oplmusic.c")
public final class OplMusicDriver {

    private static final int NUM_VOICES_PER_ARRAY = 9;
    private static final int NUM_CHANNELS = 16;
    private static final int PERCUSSION_LOG_LEN = 16;

    private static final int REGS_TREMOLO = 0x20;
    private static final int REGS_LEVEL = 0x40;
    private static final int REGS_ATTACK = 0x60;
    private static final int REGS_SUSTAIN = 0x80;
    private static final int REGS_WAVEFORM = 0xE0;
    private static final int REGS_FREQ_1 = 0xA0;
    private static final int REGS_FREQ_2 = 0xB0;
    private static final int REGS_FEEDBACK = 0xC0;
    private static final int NUM_OPERATORS = 21;
    private static final int REG_WAVEFORM_ENABLE = 0x01;
    private static final int REG_TIMER_CTRL = 0x04;
    private static final int REG_FM_MODE = 0x08;
    private static final int REG_NEW = 0x105;

    private static final int CONTROLLER_VOLUME_MSB = 0x07;
    private static final int CONTROLLER_PAN = 0x0A;
    private static final int CONTROLLER_ALL_NOTES_OFF = 0x7B;

    private static final int[][] VOICE_OPERATORS = {
        {0x00, 0x01, 0x02, 0x08, 0x09, 0x0a, 0x10, 0x11, 0x12},
        {0x03, 0x04, 0x05, 0x0b, 0x0c, 0x0d, 0x13, 0x14, 0x15}
    };

    /** Data of a MIDI channel (internal numbering: 15 is percussion). */
    private static final class Channel {
        final int number;
        GenMidi.Instrument instrument;
        int volume;
        int volumeBase;
        int pan;
        int bend;

        Channel(int number) {
            this.number = number;
        }
    }

    private static final class Voice {
        int index;
        int op1;
        int op2;
        int array;
        GenMidi.Instrument currentInstr;
        int currentInstrVoice;
        Channel channel;
        int key;
        int note;
        int freq;
        int noteVolume;
        int carVolume;
        int modVolume;
        int regPan;
        int priority;
    }

    private final GenMidi genMidi;
    private final OplPort port;
    private final DmxVersion version;
    private final boolean opl3Mode;
    private final int numVoices;

    private final Voice[] voices = new Voice[NUM_VOICES_PER_ARRAY * 2];
    private final Voice[] voiceFreeList = new Voice[NUM_VOICES_PER_ARRAY * 2];
    private final Voice[] voiceAllocedList = new Voice[NUM_VOICES_PER_ARRAY * 2];
    private int voiceFreeNum;
    private int voiceAllocedNum;
    private final Voice[] updatedList = new Voice[NUM_VOICES_PER_ARRAY * 2];
    private final Voice[] notUpdatedList = new Voice[NUM_VOICES_PER_ARRAY * 2];

    private final Channel[] channels = new Channel[NUM_CHANNELS];

    private int startMusicVolume;
    private int currentMusicVolume;

    private final int[] lastPerc = new int[PERCUSSION_LOG_LEN];
    private int lastPercCount;

    /**
     * Creates the driver and, like {@code I_OPL_InitMusic}, resets every OPL register and clears the voice table.
     *
     * @param genMidi  instrument bank
     * @param port     register sink
     * @param opl3Mode {@code true} for 18 voices and stereo panning, {@code false} for 9 voices (DMX default)
     * @param version  DMX behaviour to emulate ({@link DmxVersion#DOOM_1_9} for vanilla Doom)
     */
    public OplMusicDriver(GenMidi genMidi, OplPort port, boolean opl3Mode, DmxVersion version) {
        this.genMidi = genMidi;
        this.port = port;
        this.opl3Mode = opl3Mode;
        this.version = version;
        this.numVoices = opl3Mode ? NUM_VOICES_PER_ARRAY * 2 : NUM_VOICES_PER_ARRAY;
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
        for (int i = 0; i < NUM_CHANNELS; i++) {
            channels[i] = new Channel(i);
        }
        initRegisters(opl3Mode);
        initVoices();
        for (int i = 0; i < NUM_CHANNELS; i++) {
            initChannel(channels[i]);
        }
    }

    // ---------------------------------------------------------------- registers

    private void writeRegister(int reg, int value) {
        port.writeRegister(reg, value);
    }

    /** {@code OPL_InitRegisters} from opl.c: the register initialisation DOOM performs at start-up. */
    private void initRegisters(boolean opl3) {
        for (int r = REGS_LEVEL; r <= REGS_LEVEL + NUM_OPERATORS; ++r) {
            writeRegister(r, 0x3f);
        }
        for (int r = REGS_ATTACK; r <= REGS_WAVEFORM + NUM_OPERATORS; ++r) {
            writeRegister(r, 0x00);
        }
        for (int r = 1; r < REGS_LEVEL; ++r) {
            writeRegister(r, 0x00);
        }
        writeRegister(REG_TIMER_CTRL, 0x60);
        writeRegister(REG_TIMER_CTRL, 0x80);
        writeRegister(REG_WAVEFORM_ENABLE, 0x20);

        if (opl3) {
            writeRegister(REG_NEW, 0x01);
            for (int r = REGS_LEVEL; r <= REGS_LEVEL + NUM_OPERATORS; ++r) {
                writeRegister(r | 0x100, 0x3f);
            }
            for (int r = REGS_ATTACK; r <= REGS_WAVEFORM + NUM_OPERATORS; ++r) {
                writeRegister(r | 0x100, 0x00);
            }
            for (int r = 1; r < REGS_LEVEL; ++r) {
                writeRegister(r | 0x100, 0x00);
            }
        }

        writeRegister(REG_FM_MODE, 0x40);

        if (opl3) {
            writeRegister(REG_NEW, 0x01);
        }
    }

    // ---------------------------------------------------------------- voices

    private void initVoices() {
        voiceFreeNum = numVoices;
        voiceAllocedNum = 0;
        for (int i = 0; i < numVoices; ++i) {
            Voice v = voices[i];
            v.index = i % NUM_VOICES_PER_ARRAY;
            v.op1 = VOICE_OPERATORS[0][i % NUM_VOICES_PER_ARRAY];
            v.op2 = VOICE_OPERATORS[1][i % NUM_VOICES_PER_ARRAY];
            v.array = (i / NUM_VOICES_PER_ARRAY) << 8;
            v.currentInstr = null;
            v.channel = null;
            voiceFreeList[i] = v;
        }
    }

    private Voice getFreeVoice() {
        if (voiceFreeNum == 0) {
            return null;
        }
        Voice result = voiceFreeList[0];
        voiceFreeNum--;
        for (int i = 0; i < voiceFreeNum; i++) {
            voiceFreeList[i] = voiceFreeList[i + 1];
        }
        voiceAllocedList[voiceAllocedNum++] = result;
        return result;
    }

    private void releaseVoice(int index) {
        // Doom 2 1.666 OPL crash emulation.
        if (index >= voiceAllocedNum) {
            voiceAllocedNum = 0;
            voiceFreeNum = 0;
            return;
        }

        Voice voice = voiceAllocedList[index];
        voiceKeyOff(voice);
        voice.channel = null;
        voice.note = 0;

        boolean doubleVoice = voice.currentInstrVoice != 0;

        voiceAllocedNum--;
        for (int i = index; i < voiceAllocedNum; i++) {
            voiceAllocedList[i] = voiceAllocedList[i + 1];
        }

        // Search to the end of the freelist (this is how Doom behaves).
        voiceFreeList[voiceFreeNum++] = voice;

        if (doubleVoice && version.ordinal() < DmxVersion.DOOM_1_9.ordinal()) {
            releaseVoice(index);
        }
    }

    private int loadOperatorData(int operator, GenMidi.Operator data, boolean maxLevel) {
        int level = data.scale();
        if (maxLevel) {
            level |= 0x3f;
        } else {
            level |= data.level();
        }
        writeRegister(REGS_LEVEL + operator, level);
        writeRegister(REGS_TREMOLO + operator, data.tremolo());
        writeRegister(REGS_ATTACK + operator, data.attack());
        writeRegister(REGS_SUSTAIN + operator, data.sustain());
        writeRegister(REGS_WAVEFORM + operator, data.waveform());
        return level;
    }

    private void setVoiceInstrument(Voice voice, GenMidi.Instrument instr, int instrVoice) {
        // Instrument already set for this channel?
        if (voice.currentInstr == instr && voice.currentInstrVoice == instrVoice) {
            return;
        }

        voice.currentInstr = instr;
        voice.currentInstrVoice = instrVoice;

        GenMidi.Voice data = instr.voice(instrVoice);

        // Are we using modulated feedback mode?
        boolean modulating = (data.feedback() & 0x01) == 0;

        // Doom loads the second operator first, then the first. The carrier is set to minimum volume until the voice
        // volume is set in setVoiceVolume. If we are not using modulating mode, we must set both to minimum volume.
        voice.carVolume = loadOperatorData(voice.op2 | voice.array, data.carrier(), true);
        voice.modVolume = loadOperatorData(voice.op1 | voice.array, data.modulator(), !modulating);

        // Set feedback register that controls the connection between the two operators. Turn on bits in the upper
        // nybble; this is for OPL3, where it turns on channel A/B.
        writeRegister((REGS_FEEDBACK + voice.index) | voice.array, data.feedback() | voice.regPan);

        // Calculate voice priority.
        voice.priority = 0x0f - (data.carrier().attack() >> 4) + 0x0f - (data.carrier().sustain() & 0x0f);
    }

    private void setVoiceVolume(Voice voice, int volume) {
        voice.noteVolume = volume;

        GenMidi.Voice oplVoice = voice.currentInstr.voice(voice.currentInstrVoice);

        // Multiply note volume and channel volume to get the actual volume.
        int midiVolume = 2 * (OplMusicTables.VOLUME_MAPPING[voice.channel.volume & 0x7F] + 1);
        int fullVolume = (OplMusicTables.VOLUME_MAPPING[voice.noteVolume & 0x7F] * midiVolume) >> 9;

        // The volume value to use in the register.
        int carVolume = 0x3f - fullVolume;

        // Update the volume register(s) if necessary.
        if (carVolume != (voice.carVolume & 0x3f)) {
            voice.carVolume = carVolume | (voice.carVolume & 0xc0);

            writeRegister((REGS_LEVEL + voice.op2) | voice.array, voice.carVolume);

            // If we are using non-modulated feedback mode, we must set the volume for both voices.
            if ((oplVoice.feedback() & 0x01) != 0 && oplVoice.modulator().level() != 0x3f) {
                int modVolume = oplVoice.modulator().level();
                if (modVolume < carVolume) {
                    modVolume = carVolume;
                }

                modVolume |= voice.modVolume & 0xc0;

                if (modVolume != voice.modVolume) {
                    voice.modVolume = modVolume;
                    writeRegister((REGS_LEVEL + voice.op1) | voice.array, modVolume | (oplVoice.modulator().scale() & 0xc0));
                }
            }
        }
    }

    private void setVoicePan(Voice voice, int pan) {
        voice.regPan = pan;
        GenMidi.Voice oplVoice = voice.currentInstr.voice(voice.currentInstrVoice);
        writeRegister((REGS_FEEDBACK + voice.index) | voice.array, oplVoice.feedback() | pan);
    }

    private void voiceKeyOff(Voice voice) {
        writeRegister((REGS_FREQ_2 + voice.index) | voice.array, voice.freq >> 8);
    }

    private int frequencyForVoice(Voice voice) {
        int note = voice.note;

        // Apply note offset, but not if the instrument is a fixed note instrument.
        GenMidi.Voice gmVoice = voice.currentInstr.voice(voice.currentInstrVoice);
        if (!voice.currentInstr.fixed()) {
            note += gmVoice.baseNoteOffset();
        }

        // Avoid possible overflow due to base note offset.
        while (note < 0) {
            note += 12;
        }
        while (note > 95) {
            note -= 12;
        }

        int freqIndex = 64 + 32 * note + voice.channel.bend;

        // If this is the second voice of a double voice instrument, the frequency index can be adjusted by the fine
        // tuning field.
        if (voice.currentInstrVoice != 0) {
            freqIndex += (voice.currentInstr.fineTuning() / 2) - 64;
        }

        if (freqIndex < 0) {
            freqIndex = 0;
        }

        // The first 7 notes use the start of the table, while consecutive notes loop around the latter part.
        if (freqIndex < 284) {
            return OplMusicTables.FREQUENCY_CURVE[freqIndex];
        }

        int subIndex = (freqIndex - 284) % (12 * 32);
        int octave = (freqIndex - 284) / (12 * 32);

        // Once the seventh octave is reached, things break down. We can only go up to octave 7 as a maximum anyway.
        if (octave >= 7) {
            octave = 7;
        }

        return OplMusicTables.FREQUENCY_CURVE[subIndex + 284] | (octave << 10);
    }

    private void updateVoiceFrequency(Voice voice) {
        int freq = frequencyForVoice(voice);
        if (voice.freq != freq) {
            writeRegister((REGS_FREQ_1 + voice.index) | voice.array, freq & 0xff);
            writeRegister((REGS_FREQ_2 + voice.index) | voice.array, (freq >> 8) | 0x20);
            voice.freq = freq;
        }
    }

    private void voiceKeyOn(Channel channel, GenMidi.Instrument instrument, int instrumentVoice, int note, int key,
                            int volume) {
        if (!opl3Mode && version == DmxVersion.DOOM1_1_666) {
            instrumentVoice = 0;
        }

        // Find a voice to use for this new note.
        Voice voice = getFreeVoice();
        if (voice == null) {
            return;
        }

        voice.channel = channel;
        voice.key = key;

        // Work out the note to use. This is normally the same as the key, unless it is a fixed pitch instrument.
        if (instrument.fixed()) {
            voice.note = instrument.fixedNote();
        } else {
            voice.note = note;
        }

        voice.regPan = channel.pan;

        // Program the voice with the instrument data, set the volume level, and write the frequency to turn the note on.
        setVoiceInstrument(voice, instrument, instrumentVoice);
        setVoiceVolume(voice, volume);
        voice.freq = 0;
        updateVoiceFrequency(voice);
    }

    // ---------------------------------------------------------------- voice stealing

    private void replaceExistingVoice() {
        int result = 0;
        for (int i = 0; i < voiceAllocedNum; i++) {
            if (voiceAllocedList[i].currentInstrVoice != 0
                    || channelOrder(voiceAllocedList[i].channel) >= channelOrder(voiceAllocedList[result].channel)) {
                result = i;
            }
        }
        releaseVoice(result);
    }

    private void replaceExistingVoiceDoom1() {
        int result = 0;
        for (int i = 0; i < voiceAllocedNum; i++) {
            if (channelOrder(voiceAllocedList[i].channel) > channelOrder(voiceAllocedList[result].channel)) {
                result = i;
            }
        }
        releaseVoice(result);
    }

    private void replaceExistingVoiceDoom2(Channel channel) {
        int result = 0;
        int priority = 0x8000;
        for (int i = 0; i < voiceAllocedNum - 3; i++) {
            if (voiceAllocedList[i].priority < priority
                    && channelOrder(voiceAllocedList[i].channel) >= channelOrder(channel)) {
                priority = voiceAllocedList[i].priority;
                result = i;
            }
        }
        releaseVoice(result);
    }

    /** Channel pointers are compared in the original; the channel table order is the channel number. */
    private static int channelOrder(Channel channel) {
        return channel == null ? -1 : channel.number;
    }

    // ---------------------------------------------------------------- MIDI events

    private Channel trackChannel(int midiChannel) {
        // MIDI uses channel 9 for percussion, MUS uses 15; DMX works on MUS data internally, so swap back.
        int n = midiChannel;
        if (n == 9) {
            n = 15;
        } else if (n == 15) {
            n = 9;
        }
        return channels[n & 15];
    }

    private void keyOffEvent(int midiChannel, int key) {
        Channel channel = trackChannel(midiChannel);
        // Turn off voices being used to play this key. If it is a double voice instrument there will be two.
        for (int i = 0; i < voiceAllocedNum; i++) {
            if (voiceAllocedList[i].channel == channel && voiceAllocedList[i].key == key) {
                releaseVoice(i);
                i--;
            }
        }
    }

    private void keyOnEvent(int midiChannel, int param1, int param2) {
        int note = param1;
        int key = param1;
        int volume = param2;

        // A volume of zero means key off.
        if (volume <= 0) {
            keyOffEvent(midiChannel, param1);
            return;
        }

        Channel channel = trackChannel(midiChannel);
        GenMidi.Instrument instrument;

        // Percussion channel is treated differently.
        if (midiChannel == 9) {
            if (key < 35 || key > 81) {
                return;
            }
            instrument = genMidi.percussion(key);
            lastPerc[lastPercCount] = key;
            lastPercCount = (lastPercCount + 1) % PERCUSSION_LOG_LEN;
            note = 60;
        } else {
            instrument = channel.instrument;
        }

        boolean doubleVoice = instrument.twoVoice();

        switch (version) {
            case DOOM1_1_666: {
                int voicenum = (doubleVoice ? 1 : 0) + 1;
                if (!opl3Mode) {
                    voicenum = 1;
                }
                while (voiceAllocedNum > numVoices - voicenum) {
                    replaceExistingVoiceDoom1();
                }
                if (doubleVoice) {
                    voiceKeyOn(channel, instrument, 1, note, key, volume);
                }
                voiceKeyOn(channel, instrument, 0, note, key, volume);
                break;
            }
            case DOOM2_1_666:
                if (voiceAllocedNum == numVoices) {
                    replaceExistingVoiceDoom2(channel);
                }
                if (voiceAllocedNum == numVoices - 1 && doubleVoice) {
                    replaceExistingVoiceDoom2(channel);
                }
                if (doubleVoice) {
                    voiceKeyOn(channel, instrument, 1, note, key, volume);
                }
                voiceKeyOn(channel, instrument, 0, note, key, volume);
                break;
            default:
                if (voiceFreeNum == 0) {
                    replaceExistingVoice();
                }
                voiceKeyOn(channel, instrument, 0, note, key, volume);
                if (doubleVoice) {
                    voiceKeyOn(channel, instrument, 1, note, key, volume);
                }
                break;
        }
    }

    private void programChangeEvent(int midiChannel, int program) {
        trackChannel(midiChannel).instrument = genMidi.melodic(program);
    }

    private void setChannelVolume(Channel channel, int volume, boolean clipStart) {
        channel.volumeBase = volume;

        if (volume > currentMusicVolume) {
            volume = currentMusicVolume;
        }
        if (clipStart && volume > startMusicVolume) {
            volume = startMusicVolume;
        }

        channel.volume = volume;

        // Update all voices that this channel is using.
        for (int i = 0; i < numVoices; ++i) {
            if (voices[i].channel == channel) {
                setVoiceVolume(voices[i], voices[i].noteVolume);
            }
        }
    }

    private void setChannelPan(Channel channel, int pan) {
        if (opl3Mode) {
            int regPan;
            if (pan >= 96) {
                regPan = 0x10;
            } else if (pan <= 48) {
                regPan = 0x20;
            } else {
                regPan = 0x30;
            }
            if (channel.pan != regPan) {
                channel.pan = regPan;
                for (int i = 0; i < numVoices; i++) {
                    if (voices[i].channel == channel) {
                        setVoicePan(voices[i], regPan);
                    }
                }
            }
        }
    }

    private void allNotesOff(Channel channel) {
        for (int i = 0; i < voiceAllocedNum; i++) {
            if (voiceAllocedList[i].channel == channel) {
                releaseVoice(i);
                i--;
            }
        }
    }

    private void controllerEvent(int midiChannel, int controller, int param) {
        Channel channel = trackChannel(midiChannel);
        switch (controller) {
            case CONTROLLER_VOLUME_MSB:
                setChannelVolume(channel, param, true);
                break;
            case CONTROLLER_PAN:
                setChannelPan(channel, param);
                break;
            case CONTROLLER_ALL_NOTES_OFF:
                allNotesOff(channel);
                break;
            default:
                break;
        }
    }

    private void pitchBendEvent(int midiChannel, int param2) {
        Channel channel = trackChannel(midiChannel);

        // Update the channel bend value. Only the MSB of the pitch bend value is considered: this is what Doom does.
        channel.bend = param2 - 64;

        // Update all voices for this channel.
        int updatedNum = 0;
        int notUpdatedNum = 0;
        for (int i = 0; i < voiceAllocedNum; ++i) {
            if (voiceAllocedList[i].channel == channel) {
                updateVoiceFrequency(voiceAllocedList[i]);
                updatedList[updatedNum++] = voiceAllocedList[i];
            } else {
                notUpdatedList[notUpdatedNum++] = voiceAllocedList[i];
            }
        }
        for (int i = 0; i < notUpdatedNum; i++) {
            voiceAllocedList[i] = notUpdatedList[i];
        }
        for (int i = 0; i < updatedNum; i++) {
            voiceAllocedList[i + notUpdatedNum] = updatedList[i];
        }
    }

    // ---------------------------------------------------------------- public interface

    /**
     * Processes one channel event of a {@link doom.wad.audio.MusicScore} ({@code ProcessEvent}).
     *
     * @param type        MIDI status nibble ({@code 0x80..0xE0})
     * @param midiChannel MIDI channel 0..15 (9 is percussion)
     */
    public void processEvent(int type, int midiChannel, int param1, int param2) {
        switch (type) {
            case 0x80:
                keyOffEvent(midiChannel, param1);
                break;
            case 0x90:
                keyOnEvent(midiChannel, param1, param2);
                break;
            case 0xB0:
                controllerEvent(midiChannel, param1, param2);
                break;
            case 0xC0:
                programChangeEvent(midiChannel, param1);
                break;
            case 0xE0:
                pitchBendEvent(midiChannel, param2);
                break;
            default:
                break;
        }
    }

    /**
     * {@code I_OPL_SetMusicVolume}: sets the music volume (0..127) and updates all channels. Channel 15
     * (percussion) takes the new volume directly, like the original.
     */
    public void setMusicVolume(int volume) {
        if (currentMusicVolume == volume) {
            return;
        }
        currentMusicVolume = volume;
        for (int i = 0; i < NUM_CHANNELS; ++i) {
            if (i == 15) {
                setChannelVolume(channels[i], volume, false);
            } else {
                setChannelVolume(channels[i], channels[i].volumeBase, false);
            }
        }
    }

    /** Current music volume 0..127. */
    public int musicVolume() {
        return currentMusicVolume;
    }

    /** {@code InitChannel} for every channel: the state at the start (or restart) of a song. */
    public void initChannels() {
        for (int i = 0; i < NUM_CHANNELS; ++i) {
            initChannel(channels[i]);
        }
    }

    private void initChannel(Channel channel) {
        channel.instrument = genMidi.instrument(0);
        channel.volume = currentMusicVolume;
        channel.volumeBase = 100;
        if (channel.volume > channel.volumeBase) {
            channel.volume = channel.volumeBase;
        }
        channel.pan = 0x30;
        channel.bend = 0;
    }

    /** Remembers the music volume as it was when the song (re)started ({@code start_music_volume}). */
    public void markSongStart() {
        startMusicVolume = currentMusicVolume;
    }

    /** {@code I_OPL_PauseSong} register part: key off every voice that plays a melodic instrument. */
    public void pauseVoices() {
        for (int i = 0; i < numVoices; ++i) {
            if (voices[i].channel != null && voices[i].currentInstr.index() < 128) {
                voiceKeyOff(voices[i]);
            }
        }
    }

    /** {@code I_OPL_StopSong} voice part: releases every voice of every channel. */
    public void allNotesOffAllChannels() {
        for (int i = 0; i < NUM_CHANNELS; ++i) {
            allNotesOff(channels[i]);
        }
    }

    /** Number of OPL voices in use: 9 or 18. */
    public int voiceCount() {
        return numVoices;
    }

    /** Number of voices currently playing a note. */
    public int activeVoices() {
        return voiceAllocedNum;
    }
}
