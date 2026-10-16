package doom.sound.opl;

/**
 * Bit-exact Java port of Nuked OPL3 1.8 as shipped in Chocolate Doom ({@code opl/opl3.c}, Nuke.YKT, GPL/LGPL): the
 * chip, its register file, the buffered register writes ({@link #writeRegBuffered}) and the linear resampler to the
 * output rate ({@link #generateStream}).
 *
 * <p>The C code is reproduced operation by operation, including its integer widths: 8 and 16 bit fields are masked
 * where C would truncate them. C pointers to {@code Bit16s} values (slot output, feedback modulation and the shared
 * zero source) are kept as indices into one {@code short} array. Reference vectors generated from the C original live in
 * the test resources.
 *
 * <p>Not thread safe: one chip belongs to the audio thread. No allocation after construction.
 */
public final class Opl3 {

    /** Native sample rate the chip is resampled from. */
    public static final int NATIVE_RATE = 49716;

    private static final int RSM_FRAC = 10;
    private static final int WRITEBUF_SIZE = 1024;
    private static final int WRITEBUF_DELAY = 2;

    private static final int CH_2OP = 0;
    private static final int CH_4OP = 1;
    private static final int CH_4OP2 = 2;
    private static final int CH_DRUM = 3;

    private static final int EGK_NORM = 0x01;
    private static final int EGK_DRUM = 0x02;

    private static final int EG_ATTACK = 0;
    private static final int EG_DECAY = 1;
    private static final int EG_SUSTAIN = 2;
    private static final int EG_RELEASE = 3;

    private static final int[] MT = {1, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 20, 24, 24, 30, 30};
    private static final int[] KSLROM = {0, 32, 40, 45, 48, 51, 53, 55, 56, 58, 59, 60, 61, 62, 63, 64};
    private static final int[] KSLSHIFT = {8, 1, 2, 0};
    private static final int[][] EG_INCSTEP = {{0, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 1, 0}, {1, 1, 1, 0}};
    private static final int[] AD_SLOT = {
        0, 1, 2, 3, 4, 5, -1, -1, 6, 7, 8, 9, 10, 11, -1, -1,
        12, 13, 14, 15, 16, 17, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };
    private static final int[] CH_SLOT = {0, 1, 2, 6, 7, 8, 12, 13, 14, 18, 19, 20, 24, 25, 26, 30, 31, 32};

    /** Index of the always-zero modulation source in {@link #mem}. */
    private static final int ZERO = 0;
    private static final int OUT_BASE = 1;
    private static final int FB_BASE = 37;

    private final class Slot {
        final int num;
        Channel channel;
        int mod = ZERO;
        int prout;
        int egRout;
        int egOut;
        int egGen;
        int egKsl;
        boolean tremOn;
        int regVib;
        int regType;
        int regKsr;
        int regMult;
        int regKsl;
        int regTl;
        int regAr;
        int regDr;
        int regSl;
        int regRr;
        int regWf;
        int key;
        int pgReset;
        int pgPhase;
        int pgPhaseOut;

        Slot(int num) {
            this.num = num;
        }

        int out() {
            return OUT_BASE + num;
        }

        int fb() {
            return FB_BASE + num;
        }
    }

    private final class Channel {
        final int chNum;
        Slot slot0;
        Slot slot1;
        Channel pair;
        final int[] out = new int[4];
        int chtype = CH_2OP;
        int fNum;
        int block;
        int fb;
        int con;
        int alg;
        int ksv;
        int cha = 0xffff;
        int chb = 0xffff;

        Channel(int chNum) {
            this.chNum = chNum;
        }
    }

    private final Channel[] channel = new Channel[18];
    private final Slot[] slot = new Slot[36];
    /** Shared 16-bit cells: [0] zero source, [1+n] slot n output, [37+n] slot n feedback modulation. */
    private final short[] mem = new short[1 + 36 + 36];

    private int timer;
    private long egTimer;
    private int egTimerrem;
    private int egState;
    private int egAdd;
    private int newm;
    private int nts;
    private int rhy;
    private int vibpos;
    private int vibshift;
    private int tremolo;
    private int tremolopos;
    private int tremoloshift;
    private int noise;
    private final int[] mixbuff = new int[2];
    private int rmHhBit2;
    private int rmHhBit3;
    private int rmHhBit7;
    private int rmHhBit8;
    private int rmTcBit3;
    private int rmTcBit5;

    private int rateratio;
    private int samplecnt;
    private final short[] oldsamples = new short[2];
    private final short[] samples = new short[2];

    private long writebufSamplecnt;
    private int writebufCur;
    private int writebufLast;
    private long writebufLasttime;
    private final long[] wbTime = new long[WRITEBUF_SIZE];
    private final int[] wbReg = new int[WRITEBUF_SIZE];
    private final int[] wbData = new int[WRITEBUF_SIZE];

    /**
     * Creates a chip that resamples to {@code sampleRate} Hz.
     *
     * @see #reset(int)
     */
    public Opl3(int sampleRate) {
        for (int i = 0; i < 36; i++) {
            slot[i] = new Slot(i);
        }
        for (int i = 0; i < 18; i++) {
            channel[i] = new Channel(i);
        }
        reset(sampleRate);
    }

    /** {@code OPL3_Reset}: restores the power-on state and sets the output rate. */
    public void reset(int sampleRate) {
        java.util.Arrays.fill(mem, (short) 0);
        timer = 0;
        egTimer = 0;
        egTimerrem = 0;
        egState = 0;
        egAdd = 0;
        newm = 0;
        nts = 0;
        rhy = 0;
        vibpos = 0;
        tremolo = 0;
        tremolopos = 0;
        mixbuff[0] = 0;
        mixbuff[1] = 0;
        rmHhBit2 = 0;
        rmHhBit3 = 0;
        rmHhBit7 = 0;
        rmHhBit8 = 0;
        rmTcBit3 = 0;
        rmTcBit5 = 0;
        samplecnt = 0;
        oldsamples[0] = 0;
        oldsamples[1] = 0;
        samples[0] = 0;
        samples[1] = 0;
        writebufSamplecnt = 0;
        writebufCur = 0;
        writebufLast = 0;
        writebufLasttime = 0;
        java.util.Arrays.fill(wbTime, 0);
        java.util.Arrays.fill(wbReg, 0);
        java.util.Arrays.fill(wbData, 0);

        for (int slotnum = 0; slotnum < 36; slotnum++) {
            Slot s = slot[slotnum];
            s.channel = null;
            s.mod = ZERO;
            s.prout = 0;
            s.egRout = 0x1ff;
            s.egOut = 0x1ff;
            s.egGen = EG_RELEASE;
            s.egKsl = 0;
            s.tremOn = false;
            s.regVib = 0;
            s.regType = 0;
            s.regKsr = 0;
            s.regMult = 0;
            s.regKsl = 0;
            s.regTl = 0;
            s.regAr = 0;
            s.regDr = 0;
            s.regSl = 0;
            s.regRr = 0;
            s.regWf = 0;
            s.key = 0;
            s.pgReset = 0;
            s.pgPhase = 0;
            s.pgPhaseOut = 0;
        }
        for (int channum = 0; channum < 18; channum++) {
            Channel c = channel[channum];
            c.slot0 = slot[CH_SLOT[channum]];
            c.slot1 = slot[CH_SLOT[channum] + 3];
            slot[CH_SLOT[channum]].channel = c;
            slot[CH_SLOT[channum] + 3].channel = c;
            if ((channum % 9) < 3) {
                c.pair = channel[channum + 3];
            } else if ((channum % 9) < 6) {
                c.pair = channel[channum - 3];
            } else {
                c.pair = null;
            }
            c.out[0] = ZERO;
            c.out[1] = ZERO;
            c.out[2] = ZERO;
            c.out[3] = ZERO;
            c.chtype = CH_2OP;
            c.fNum = 0;
            c.block = 0;
            c.fb = 0;
            c.con = 0;
            c.alg = 0;
            c.ksv = 0;
            c.cha = 0xffff;
            c.chb = 0xffff;
            channelSetupAlg(c);
        }
        noise = 1;
        rateratio = (sampleRate << RSM_FRAC) / NATIVE_RATE;
        tremoloshift = 4;
        vibshift = 1;
    }

    // ---------------------------------------------------------------- envelope generator

    private static int envelopeCalcExp(int level) {
        if (level > 0x1fff) {
            level = 0x1fff;
        }
        return (Opl3Tables.EXP[level & 0xff] << 1) >> (level >> 8);
    }

    private static int envelopeSin(int wf, int phase, int envelope) {
        int out;
        int neg = 0;
        phase &= 0x3ff;
        switch (wf) {
            case 0:
                if ((phase & 0x200) != 0) {
                    neg = 0xffff;
                }
                if ((phase & 0x100) != 0) {
                    out = Opl3Tables.LOGSIN[(phase & 0xff) ^ 0xff];
                } else {
                    out = Opl3Tables.LOGSIN[phase & 0xff];
                }
                break;
            case 1:
                if ((phase & 0x200) != 0) {
                    out = 0x1000;
                } else if ((phase & 0x100) != 0) {
                    out = Opl3Tables.LOGSIN[(phase & 0xff) ^ 0xff];
                } else {
                    out = Opl3Tables.LOGSIN[phase & 0xff];
                }
                break;
            case 2:
                if ((phase & 0x100) != 0) {
                    out = Opl3Tables.LOGSIN[(phase & 0xff) ^ 0xff];
                } else {
                    out = Opl3Tables.LOGSIN[phase & 0xff];
                }
                break;
            case 3:
                if ((phase & 0x100) != 0) {
                    out = 0x1000;
                } else {
                    out = Opl3Tables.LOGSIN[phase & 0xff];
                }
                break;
            case 4:
                if ((phase & 0x300) == 0x100) {
                    neg = 0xffff;
                }
                if ((phase & 0x200) != 0) {
                    out = 0x1000;
                } else if ((phase & 0x80) != 0) {
                    out = Opl3Tables.LOGSIN[((phase ^ 0xff) << 1) & 0xff];
                } else {
                    out = Opl3Tables.LOGSIN[(phase << 1) & 0xff];
                }
                break;
            case 5:
                if ((phase & 0x200) != 0) {
                    out = 0x1000;
                } else if ((phase & 0x80) != 0) {
                    out = Opl3Tables.LOGSIN[((phase ^ 0xff) << 1) & 0xff];
                } else {
                    out = Opl3Tables.LOGSIN[(phase << 1) & 0xff];
                }
                break;
            case 6:
                if ((phase & 0x200) != 0) {
                    neg = 0xffff;
                }
                return (short) (envelopeCalcExp(envelope << 3) ^ neg);
            default:
                if ((phase & 0x200) != 0) {
                    neg = 0xffff;
                    phase = (phase & 0x1ff) ^ 0x1ff;
                }
                out = (phase << 3) & 0xffff;
                break;
        }
        return (short) (envelopeCalcExp(out + (envelope << 3)) ^ neg);
    }

    private void envelopeUpdateKsl(Slot s) {
        int ksl = (KSLROM[s.channel.fNum >> 6] << 2) - ((0x08 - s.channel.block) << 5);
        if (ksl < 0) {
            ksl = 0;
        }
        s.egKsl = ksl & 0xff;
    }

    private void envelopeCalc(Slot s) {
        int regRate = 0;
        int reset = 0;
        s.egOut = (short) (s.egRout + (s.regTl << 2) + (s.egKsl >> KSLSHIFT[s.regKsl]) + (s.tremOn ? tremolo : 0));
        if (s.key != 0 && s.egGen == EG_RELEASE) {
            reset = 1;
            regRate = s.regAr;
        } else {
            switch (s.egGen) {
                case EG_ATTACK:
                    regRate = s.regAr;
                    break;
                case EG_DECAY:
                    regRate = s.regDr;
                    break;
                case EG_SUSTAIN:
                    if (s.regType == 0) {
                        regRate = s.regRr;
                    }
                    break;
                case EG_RELEASE:
                    regRate = s.regRr;
                    break;
                default:
                    break;
            }
        }
        s.pgReset = reset;
        int ks = s.channel.ksv >> ((s.regKsr ^ 1) << 1);
        boolean nonzero = regRate != 0;
        int rate = (ks + (regRate << 2)) & 0xff;
        int rateHi = rate >> 2;
        int rateLo = rate & 0x03;
        if ((rateHi & 0x10) != 0) {
            rateHi = 0x0f;
        }
        int egShift = (rateHi + egAdd) & 0xff;
        int shift = 0;
        if (nonzero) {
            if (rateHi < 12) {
                if (egState != 0) {
                    switch (egShift) {
                        case 12:
                            shift = 1;
                            break;
                        case 13:
                            shift = (rateLo >> 1) & 0x01;
                            break;
                        case 14:
                            shift = rateLo & 0x01;
                            break;
                        default:
                            break;
                    }
                }
            } else {
                shift = ((rateHi & 0x03) + EG_INCSTEP[rateLo][timer & 0x03]) & 0xff;
                if ((shift & 0x04) != 0) {
                    shift = 0x03;
                }
                if (shift == 0) {
                    shift = egState;
                }
            }
        }
        int egRout = s.egRout;
        int egInc = 0;
        boolean egOff = false;
        // Instant attack
        if (reset != 0 && rateHi == 0x0f) {
            egRout = 0x00;
        }
        // Envelope off
        if ((s.egRout & 0x1f8) == 0x1f8) {
            egOff = true;
        }
        if (s.egGen != EG_ATTACK && reset == 0 && egOff) {
            egRout = 0x1ff;
        }
        switch (s.egGen) {
            case EG_ATTACK:
                if (s.egRout == 0) {
                    s.egGen = EG_DECAY;
                } else if (s.key != 0 && shift > 0 && rateHi != 0x0f) {
                    egInc = (short) (((~s.egRout) << shift) >> 4);
                }
                break;
            case EG_DECAY:
                if ((s.egRout >> 4) == s.regSl) {
                    s.egGen = EG_SUSTAIN;
                } else if (!egOff && reset == 0 && shift > 0) {
                    egInc = 1 << (shift - 1);
                }
                break;
            case EG_SUSTAIN:
            case EG_RELEASE:
                if (!egOff && reset == 0 && shift > 0) {
                    egInc = 1 << (shift - 1);
                }
                break;
            default:
                break;
        }
        s.egRout = (egRout + egInc) & 0x1ff;
        // Key off
        if (reset != 0) {
            s.egGen = EG_ATTACK;
        }
        if (s.key == 0) {
            s.egGen = EG_RELEASE;
        }
    }

    private static void envelopeKeyOn(Slot s, int type) {
        s.key |= type;
    }

    private static void envelopeKeyOff(Slot s, int type) {
        s.key &= ~type;
    }

    // ---------------------------------------------------------------- phase generator

    private void phaseGenerate(Slot s) {
        int fNum = s.channel.fNum;
        if (s.regVib != 0) {
            int range = (fNum >> 7) & 7;
            int vp = vibpos;
            if ((vp & 3) == 0) {
                range = 0;
            } else if ((vp & 1) != 0) {
                range >>= 1;
            }
            range >>= vibshift;
            if ((vp & 4) != 0) {
                range = -range;
            }
            fNum = (fNum + range) & 0xffff;
        }
        int basefreq = (fNum << s.channel.block) >>> 1;
        int phase = (s.pgPhase >>> 9) & 0xffff;
        if (s.pgReset != 0) {
            s.pgPhase = 0;
        }
        s.pgPhase += (basefreq * MT[s.regMult]) >>> 1;
        // Rhythm mode
        int noiseV = noise;
        s.pgPhaseOut = phase;
        if (s.num == 13) { // hh
            rmHhBit2 = (phase >> 2) & 1;
            rmHhBit3 = (phase >> 3) & 1;
            rmHhBit7 = (phase >> 7) & 1;
            rmHhBit8 = (phase >> 8) & 1;
        }
        if (s.num == 17 && (rhy & 0x20) != 0) { // tc
            rmTcBit3 = (phase >> 3) & 1;
            rmTcBit5 = (phase >> 5) & 1;
        }
        if ((rhy & 0x20) != 0) {
            int rmXor = (rmHhBit2 ^ rmHhBit7) | (rmHhBit3 ^ rmTcBit5) | (rmTcBit3 ^ rmTcBit5);
            switch (s.num) {
                case 13: // hh
                    s.pgPhaseOut = rmXor << 9;
                    if ((rmXor ^ (noiseV & 1)) != 0) {
                        s.pgPhaseOut |= 0xd0;
                    } else {
                        s.pgPhaseOut |= 0x34;
                    }
                    break;
                case 16: // sd
                    s.pgPhaseOut = (rmHhBit8 << 9) | ((rmHhBit8 ^ (noiseV & 1)) << 8);
                    break;
                case 17: // tc
                    s.pgPhaseOut = (rmXor << 9) | 0x80;
                    break;
                default:
                    break;
            }
        }
        int nBit = ((noiseV >>> 14) ^ noiseV) & 0x01;
        noise = (noiseV >>> 1) | (nBit << 22);
    }

    // ---------------------------------------------------------------- slot

    private void slotWrite20(Slot s, int data) {
        s.tremOn = ((data >> 7) & 0x01) != 0;
        s.regVib = (data >> 6) & 0x01;
        s.regType = (data >> 5) & 0x01;
        s.regKsr = (data >> 4) & 0x01;
        s.regMult = data & 0x0f;
    }

    private void slotWrite40(Slot s, int data) {
        s.regKsl = (data >> 6) & 0x03;
        s.regTl = data & 0x3f;
        envelopeUpdateKsl(s);
    }

    private void slotWrite60(Slot s, int data) {
        s.regAr = (data >> 4) & 0x0f;
        s.regDr = data & 0x0f;
    }

    private void slotWrite80(Slot s, int data) {
        s.regSl = (data >> 4) & 0x0f;
        if (s.regSl == 0x0f) {
            s.regSl = 0x1f;
        }
        s.regRr = data & 0x0f;
    }

    private void slotWriteE0(Slot s, int data) {
        s.regWf = data & 0x07;
        if (newm == 0x00) {
            s.regWf &= 0x03;
        }
    }

    private void slotGenerate(Slot s) {
        mem[OUT_BASE + s.num] = (short) envelopeSin(s.regWf, (s.pgPhaseOut + mem[s.mod]) & 0xffff, s.egOut & 0xffff);
    }

    private void slotCalcFb(Slot s) {
        if (s.channel.fb != 0x00) {
            mem[FB_BASE + s.num] = (short) ((s.prout + mem[OUT_BASE + s.num]) >> (0x09 - s.channel.fb));
        } else {
            mem[FB_BASE + s.num] = 0;
        }
        s.prout = mem[OUT_BASE + s.num];
    }

    // ---------------------------------------------------------------- channel

    private void channelUpdateRhythm(int data) {
        rhy = data & 0x3f;
        if ((rhy & 0x20) != 0) {
            Channel channel6 = channel[6];
            Channel channel7 = channel[7];
            Channel channel8 = channel[8];
            channel6.out[0] = channel6.slot1.out();
            channel6.out[1] = channel6.slot1.out();
            channel6.out[2] = ZERO;
            channel6.out[3] = ZERO;
            channel7.out[0] = channel7.slot0.out();
            channel7.out[1] = channel7.slot0.out();
            channel7.out[2] = channel7.slot1.out();
            channel7.out[3] = channel7.slot1.out();
            channel8.out[0] = channel8.slot0.out();
            channel8.out[1] = channel8.slot0.out();
            channel8.out[2] = channel8.slot1.out();
            channel8.out[3] = channel8.slot1.out();
            for (int chnum = 6; chnum < 9; chnum++) {
                channel[chnum].chtype = CH_DRUM;
            }
            channelSetupAlg(channel6);
            channelSetupAlg(channel7);
            channelSetupAlg(channel8);
            // hh
            if ((rhy & 0x01) != 0) {
                envelopeKeyOn(channel7.slot0, EGK_DRUM);
            } else {
                envelopeKeyOff(channel7.slot0, EGK_DRUM);
            }
            // tc
            if ((rhy & 0x02) != 0) {
                envelopeKeyOn(channel8.slot1, EGK_DRUM);
            } else {
                envelopeKeyOff(channel8.slot1, EGK_DRUM);
            }
            // tom
            if ((rhy & 0x04) != 0) {
                envelopeKeyOn(channel8.slot0, EGK_DRUM);
            } else {
                envelopeKeyOff(channel8.slot0, EGK_DRUM);
            }
            // sd
            if ((rhy & 0x08) != 0) {
                envelopeKeyOn(channel7.slot1, EGK_DRUM);
            } else {
                envelopeKeyOff(channel7.slot1, EGK_DRUM);
            }
            // bd
            if ((rhy & 0x10) != 0) {
                envelopeKeyOn(channel6.slot0, EGK_DRUM);
                envelopeKeyOn(channel6.slot1, EGK_DRUM);
            } else {
                envelopeKeyOff(channel6.slot0, EGK_DRUM);
                envelopeKeyOff(channel6.slot1, EGK_DRUM);
            }
        } else {
            for (int chnum = 6; chnum < 9; chnum++) {
                channel[chnum].chtype = CH_2OP;
                channelSetupAlg(channel[chnum]);
                envelopeKeyOff(channel[chnum].slot0, EGK_DRUM);
                envelopeKeyOff(channel[chnum].slot1, EGK_DRUM);
            }
        }
    }

    private void channelWriteA0(Channel c, int data) {
        if (newm != 0 && c.chtype == CH_4OP2) {
            return;
        }
        c.fNum = (c.fNum & 0x300) | data;
        c.ksv = ((c.block << 1) | ((c.fNum >> (0x09 - nts)) & 0x01)) & 0xff;
        envelopeUpdateKsl(c.slot0);
        envelopeUpdateKsl(c.slot1);
        if (newm != 0 && c.chtype == CH_4OP) {
            c.pair.fNum = c.fNum;
            c.pair.ksv = c.ksv;
            envelopeUpdateKsl(c.pair.slot0);
            envelopeUpdateKsl(c.pair.slot1);
        }
    }

    private void channelWriteB0(Channel c, int data) {
        if (newm != 0 && c.chtype == CH_4OP2) {
            return;
        }
        c.fNum = (c.fNum & 0xff) | ((data & 0x03) << 8);
        c.block = (data >> 2) & 0x07;
        c.ksv = ((c.block << 1) | ((c.fNum >> (0x09 - nts)) & 0x01)) & 0xff;
        envelopeUpdateKsl(c.slot0);
        envelopeUpdateKsl(c.slot1);
        if (newm != 0 && c.chtype == CH_4OP) {
            c.pair.fNum = c.fNum;
            c.pair.block = c.block;
            c.pair.ksv = c.ksv;
            envelopeUpdateKsl(c.pair.slot0);
            envelopeUpdateKsl(c.pair.slot1);
        }
    }

    private void channelSetupAlg(Channel c) {
        if (c.chtype == CH_DRUM) {
            if (c.chNum == 7 || c.chNum == 8) {
                c.slot0.mod = ZERO;
                c.slot1.mod = ZERO;
                return;
            }
            switch (c.alg & 0x01) {
                case 0x00:
                    c.slot0.mod = c.slot0.fb();
                    c.slot1.mod = c.slot0.out();
                    break;
                default:
                    c.slot0.mod = c.slot0.fb();
                    c.slot1.mod = ZERO;
                    break;
            }
            return;
        }
        if ((c.alg & 0x08) != 0) {
            return;
        }
        if ((c.alg & 0x04) != 0) {
            Channel p = c.pair;
            p.out[0] = ZERO;
            p.out[1] = ZERO;
            p.out[2] = ZERO;
            p.out[3] = ZERO;
            switch (c.alg & 0x03) {
                case 0x00:
                    p.slot0.mod = p.slot0.fb();
                    p.slot1.mod = p.slot0.out();
                    c.slot0.mod = p.slot1.out();
                    c.slot1.mod = c.slot0.out();
                    c.out[0] = c.slot1.out();
                    c.out[1] = ZERO;
                    c.out[2] = ZERO;
                    c.out[3] = ZERO;
                    break;
                case 0x01:
                    p.slot0.mod = p.slot0.fb();
                    p.slot1.mod = p.slot0.out();
                    c.slot0.mod = ZERO;
                    c.slot1.mod = c.slot0.out();
                    c.out[0] = p.slot1.out();
                    c.out[1] = c.slot1.out();
                    c.out[2] = ZERO;
                    c.out[3] = ZERO;
                    break;
                case 0x02:
                    p.slot0.mod = p.slot0.fb();
                    p.slot1.mod = ZERO;
                    c.slot0.mod = p.slot1.out();
                    c.slot1.mod = c.slot0.out();
                    c.out[0] = p.slot0.out();
                    c.out[1] = c.slot1.out();
                    c.out[2] = ZERO;
                    c.out[3] = ZERO;
                    break;
                default:
                    p.slot0.mod = p.slot0.fb();
                    p.slot1.mod = ZERO;
                    c.slot0.mod = p.slot1.out();
                    c.slot1.mod = ZERO;
                    c.out[0] = p.slot0.out();
                    c.out[1] = c.slot0.out();
                    c.out[2] = c.slot1.out();
                    c.out[3] = ZERO;
                    break;
            }
        } else {
            switch (c.alg & 0x01) {
                case 0x00:
                    c.slot0.mod = c.slot0.fb();
                    c.slot1.mod = c.slot0.out();
                    c.out[0] = c.slot1.out();
                    c.out[1] = ZERO;
                    c.out[2] = ZERO;
                    c.out[3] = ZERO;
                    break;
                default:
                    c.slot0.mod = c.slot0.fb();
                    c.slot1.mod = ZERO;
                    c.out[0] = c.slot0.out();
                    c.out[1] = c.slot1.out();
                    c.out[2] = ZERO;
                    c.out[3] = ZERO;
                    break;
            }
        }
    }

    private void channelWriteC0(Channel c, int data) {
        c.fb = (data & 0x0e) >> 1;
        c.con = data & 0x01;
        c.alg = c.con;
        if (newm != 0) {
            if (c.chtype == CH_4OP) {
                c.pair.alg = 0x04 | (c.con << 1) | c.pair.con;
                c.alg = 0x08;
                channelSetupAlg(c.pair);
            } else if (c.chtype == CH_4OP2) {
                c.alg = 0x04 | (c.pair.con << 1) | c.con;
                c.pair.alg = 0x08;
                channelSetupAlg(c);
            } else {
                channelSetupAlg(c);
            }
        } else {
            channelSetupAlg(c);
        }
        if (newm != 0) {
            c.cha = ((data >> 4) & 0x01) != 0 ? 0xffff : 0;
            c.chb = ((data >> 5) & 0x01) != 0 ? 0xffff : 0;
        } else {
            c.cha = 0xffff;
            c.chb = 0xffff;
        }
    }

    private void channelKeyOn(Channel c) {
        if (newm != 0) {
            if (c.chtype == CH_4OP) {
                envelopeKeyOn(c.slot0, EGK_NORM);
                envelopeKeyOn(c.slot1, EGK_NORM);
                envelopeKeyOn(c.pair.slot0, EGK_NORM);
                envelopeKeyOn(c.pair.slot1, EGK_NORM);
            } else if (c.chtype == CH_2OP || c.chtype == CH_DRUM) {
                envelopeKeyOn(c.slot0, EGK_NORM);
                envelopeKeyOn(c.slot1, EGK_NORM);
            }
        } else {
            envelopeKeyOn(c.slot0, EGK_NORM);
            envelopeKeyOn(c.slot1, EGK_NORM);
        }
    }

    private void channelKeyOff(Channel c) {
        if (newm != 0) {
            if (c.chtype == CH_4OP) {
                envelopeKeyOff(c.slot0, EGK_NORM);
                envelopeKeyOff(c.slot1, EGK_NORM);
                envelopeKeyOff(c.pair.slot0, EGK_NORM);
                envelopeKeyOff(c.pair.slot1, EGK_NORM);
            } else if (c.chtype == CH_2OP || c.chtype == CH_DRUM) {
                envelopeKeyOff(c.slot0, EGK_NORM);
                envelopeKeyOff(c.slot1, EGK_NORM);
            }
        } else {
            envelopeKeyOff(c.slot0, EGK_NORM);
            envelopeKeyOff(c.slot1, EGK_NORM);
        }
    }

    private void channelSet4Op(int data) {
        for (int bit = 0; bit < 6; bit++) {
            int chnum = bit;
            if (bit >= 3) {
                chnum += 9 - 3;
            }
            if (((data >> bit) & 0x01) != 0) {
                channel[chnum].chtype = CH_4OP;
                channel[chnum + 3].chtype = CH_4OP2;
            } else {
                channel[chnum].chtype = CH_2OP;
                channel[chnum + 3].chtype = CH_2OP;
            }
        }
    }

    private static short clipSample(int sample) {
        if (sample > 32767) {
            sample = 32767;
        } else if (sample < -32768) {
            sample = -32768;
        }
        return (short) sample;
    }

    private void runSlot(int ii) {
        Slot s = slot[ii];
        slotCalcFb(s);
        envelopeCalc(s);
        phaseGenerate(s);
        slotGenerate(s);
    }

    // ---------------------------------------------------------------- generation

    /**
     * {@code OPL3_Generate}: advances the chip by one native sample (49716 Hz) and stores left and right in
     * {@code buf[0]} and {@code buf[1]}.
     */
    public void generate(short[] buf) {
        buf[1] = clipSample(mixbuff[1]);

        for (int ii = 0; ii < 15; ii++) {
            runSlot(ii);
        }

        mixbuff[0] = 0;
        for (int ii = 0; ii < 18; ii++) {
            Channel c = channel[ii];
            short accm = 0;
            for (int jj = 0; jj < 4; jj++) {
                accm += mem[c.out[jj]];
            }
            mixbuff[0] += (short) (accm & c.cha);
        }

        for (int ii = 15; ii < 18; ii++) {
            runSlot(ii);
        }

        buf[0] = clipSample(mixbuff[0]);

        for (int ii = 18; ii < 33; ii++) {
            runSlot(ii);
        }

        mixbuff[1] = 0;
        for (int ii = 0; ii < 18; ii++) {
            Channel c = channel[ii];
            short accm = 0;
            for (int jj = 0; jj < 4; jj++) {
                accm += mem[c.out[jj]];
            }
            mixbuff[1] += (short) (accm & c.chb);
        }

        for (int ii = 33; ii < 36; ii++) {
            runSlot(ii);
        }

        if ((timer & 0x3f) == 0x3f) {
            tremolopos = (tremolopos + 1) % 210;
        }
        if (tremolopos < 105) {
            tremolo = tremolopos >> tremoloshift;
        } else {
            tremolo = (210 - tremolopos) >> tremoloshift;
        }

        if ((timer & 0x3ff) == 0x3ff) {
            vibpos = (vibpos + 1) & 7;
        }

        timer = (timer + 1) & 0xffff;

        egAdd = 0;
        if (egTimer != 0) {
            int shift = 0;
            while (shift < 36 && ((egTimer >>> shift) & 1) == 0) {
                shift++;
            }
            if (shift > 12) {
                egAdd = 0;
            } else {
                egAdd = shift + 1;
            }
        }

        if (egTimerrem != 0 || egState != 0) {
            if (egTimer == 0xfffffffffL) {
                egTimer = 0;
                egTimerrem = 1;
            } else {
                egTimer++;
                egTimerrem = 0;
            }
        }

        egState ^= 1;

        while (wbTime[writebufCur] <= writebufSamplecnt) {
            if ((wbReg[writebufCur] & 0x200) == 0) {
                break;
            }
            wbReg[writebufCur] &= 0x1ff;
            writeReg(wbReg[writebufCur], wbData[writebufCur]);
            writebufCur = (writebufCur + 1) % WRITEBUF_SIZE;
        }
        writebufSamplecnt++;
    }

    /** {@code OPL3_GenerateResampled}: one output-rate sample into {@code buf[0..1]}. */
    public void generateResampled(short[] buf) {
        while (samplecnt >= rateratio) {
            oldsamples[0] = samples[0];
            oldsamples[1] = samples[1];
            generate(samples);
            samplecnt -= rateratio;
        }
        buf[0] = (short) ((oldsamples[0] * (rateratio - samplecnt) + samples[0] * samplecnt) / rateratio);
        buf[1] = (short) ((oldsamples[1] * (rateratio - samplecnt) + samples[1] * samplecnt) / rateratio);
        samplecnt += 1 << RSM_FRAC;
    }

    /**
     * {@code OPL3_GenerateStream}: renders {@code numSamples} stereo frames at the output rate as interleaved
     * left/right values starting at {@code out[offset]}.
     */
    public void generateStream(short[] out, int offset, int numSamples) {
        short[] tmp = frame;
        for (int i = 0; i < numSamples; i++) {
            generateResampled(tmp);
            out[offset++] = tmp[0];
            out[offset++] = tmp[1];
        }
    }

    private final short[] frame = new short[2];

    // ---------------------------------------------------------------- registers

    /** {@code OPL3_WriteReg}: writes a register immediately. {@code reg} bit 8 selects the second register array. */
    public void writeReg(int reg, int v) {
        reg &= 0xffff;
        v &= 0xff;
        int high = (reg >> 8) & 0x01;
        int regm = reg & 0xff;
        switch (regm & 0xf0) {
            case 0x00:
                if (high != 0) {
                    switch (regm & 0x0f) {
                        case 0x04:
                            channelSet4Op(v);
                            break;
                        case 0x05:
                            newm = v & 0x01;
                            break;
                        default:
                            break;
                    }
                } else {
                    if ((regm & 0x0f) == 0x08) {
                        nts = (v >> 6) & 0x01;
                    }
                }
                break;
            case 0x20:
            case 0x30:
                if (AD_SLOT[regm & 0x1f] >= 0) {
                    slotWrite20(slot[18 * high + AD_SLOT[regm & 0x1f]], v);
                }
                break;
            case 0x40:
            case 0x50:
                if (AD_SLOT[regm & 0x1f] >= 0) {
                    slotWrite40(slot[18 * high + AD_SLOT[regm & 0x1f]], v);
                }
                break;
            case 0x60:
            case 0x70:
                if (AD_SLOT[regm & 0x1f] >= 0) {
                    slotWrite60(slot[18 * high + AD_SLOT[regm & 0x1f]], v);
                }
                break;
            case 0x80:
            case 0x90:
                if (AD_SLOT[regm & 0x1f] >= 0) {
                    slotWrite80(slot[18 * high + AD_SLOT[regm & 0x1f]], v);
                }
                break;
            case 0xe0:
            case 0xf0:
                if (AD_SLOT[regm & 0x1f] >= 0) {
                    slotWriteE0(slot[18 * high + AD_SLOT[regm & 0x1f]], v);
                }
                break;
            case 0xa0:
                if ((regm & 0x0f) < 9) {
                    channelWriteA0(channel[9 * high + (regm & 0x0f)], v);
                }
                break;
            case 0xb0:
                if (regm == 0xbd && high == 0) {
                    tremoloshift = (((v >> 7) ^ 1) << 1) + 2;
                    vibshift = ((v >> 6) & 0x01) ^ 1;
                    channelUpdateRhythm(v);
                } else if ((regm & 0x0f) < 9) {
                    Channel c = channel[9 * high + (regm & 0x0f)];
                    channelWriteB0(c, v);
                    if ((v & 0x20) != 0) {
                        channelKeyOn(c);
                    } else {
                        channelKeyOff(c);
                    }
                }
                break;
            case 0xc0:
                if ((regm & 0x0f) < 9) {
                    channelWriteC0(channel[9 * high + (regm & 0x0f)], v);
                }
                break;
            default:
                break;
        }
    }

    /**
     * {@code OPL3_WriteRegBuffered}: queues a register write so that it takes effect two native samples after the
     * previous one, as the real chip's bus does.
     */
    public void writeRegBuffered(int reg, int v) {
        reg &= 0xffff;
        v &= 0xff;
        if ((wbReg[writebufLast] & 0x200) != 0) {
            writeReg(wbReg[writebufLast] & 0x1ff, wbData[writebufLast]);
            writebufCur = (writebufLast + 1) % WRITEBUF_SIZE;
            writebufSamplecnt = wbTime[writebufLast];
        }

        wbReg[writebufLast] = reg | 0x200;
        wbData[writebufLast] = v;
        long time1 = writebufLasttime + WRITEBUF_DELAY;
        long time2 = writebufSamplecnt;

        if (time1 < time2) {
            time1 = time2;
        }

        wbTime[writebufLast] = time1;
        writebufLasttime = time1;
        writebufLast = (writebufLast + 1) % WRITEBUF_SIZE;
    }
}
