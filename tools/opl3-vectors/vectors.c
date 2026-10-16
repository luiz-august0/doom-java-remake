// Reference vector generator for the Java port of Nuked OPL3 (doom.sound.opl.Opl3).
//
// It links the ORIGINAL Chocolate Doom opl/opl3.c, drives it through register sequences (2-op, 4-op, rhythm,
// OPL2/OPL3 mode, envelopes, fuzz) and writes, for each scenario, the operations performed plus the expected output as
// FNV-1a 64 hashes of every block of 128 stereo frames. The Java test replays the operations and must match bit for bit.
//
// Output format (text, gzip it):
//   # opl3-vectors v1
//   vector <name> <samplerate>
//   w <reg> <val>     OPL3_WriteRegBuffered
//   W <reg> <val>     OPL3_WriteReg
//   s <frames>        OPL3_GenerateStream (output-rate frames); followed by ceil(frames/128) lines "h <hex>"
//   n <frames>        OPL3_Generate (native 49716 Hz frames); followed by ceil(frames/128) lines "h <hex>"
//   end
//
// Build/run: see build.sh.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include "opl3.h"

#define BLOCK 128

static FILE *out;
static opl3_chip chip;
static uint32_t rng_state;

static uint32_t rnd(void)
{
    rng_state ^= rng_state << 13;
    rng_state ^= rng_state >> 17;
    rng_state ^= rng_state << 5;
    return rng_state;
}

static void begin(const char *name, int rate, uint32_t seed)
{
    fprintf(out, "vector %s %d\n", name, rate);
    OPL3_Reset(&chip, rate);
    rng_state = seed ? seed : 1;
}

static void end(void)
{
    fprintf(out, "end\n");
}

static void w(int reg, int val)
{
    fprintf(out, "w %d %d\n", reg, val & 0xff);
    OPL3_WriteRegBuffered(&chip, reg, val & 0xff);
}

static void wd(int reg, int val)
{
    fprintf(out, "W %d %d\n", reg, val & 0xff);
    OPL3_WriteReg(&chip, reg, val & 0xff);
}

static uint64_t fnv(const int16_t *p, int n)
{
    uint64_t h = 0xcbf29ce484222325ULL;
    int i;
    for (i = 0; i < n; i++)
    {
        uint16_t v = (uint16_t) p[i];
        h ^= v & 0xff;
        h *= 0x100000001b3ULL;
        h ^= v >> 8;
        h *= 0x100000001b3ULL;
    }
    return h;
}

static int16_t *buf;

static void emit_hashes(int frames)
{
    int f;
    for (f = 0; f < frames; f += BLOCK)
    {
        int n = frames - f < BLOCK ? frames - f : BLOCK;
        fprintf(out, "h %016llx\n", (unsigned long long) fnv(buf + f * 2, n * 2));
    }
}

static void gen_stream(int frames)
{
    fprintf(out, "s %d\n", frames);
    OPL3_GenerateStream(&chip, buf, frames);
    emit_hashes(frames);
}

static void gen_native(int frames)
{
    int i;
    fprintf(out, "n %d\n", frames);
    for (i = 0; i < frames; i++)
    {
        OPL3_Generate(&chip, buf + i * 2);
    }
    emit_hashes(frames);
}

// Register offset of the modulator operator of channel (0..8) within one array; the carrier is +3.
static const int mod_off[9] = { 0x00, 0x01, 0x02, 0x08, 0x09, 0x0a, 0x10, 0x11, 0x12 };

// channel 0..17 (>= 9 selects the second array).
static void setup_channel(int ch, int m20, int m40, int m60, int m80, int mwf,
                          int c20, int c40, int c60, int c80, int cwf, int c0val)
{
    int hi = ch >= 9 ? 0x100 : 0;
    int base = mod_off[ch % 9] | hi;
    wd(0x20 + base, m20);
    wd(0x40 + base, m40);
    wd(0x60 + base, m60);
    wd(0x80 + base, m80);
    wd(0xe0 + base, mwf);
    wd(0x23 + base, c20);
    wd(0x43 + base, c40);
    wd(0x63 + base, c60);
    wd(0x83 + base, c80);
    wd(0xe3 + base, cwf);
    wd(0xc0 + (ch % 9) + hi, c0val);
}

static void key_on(int ch, int fnum, int block)
{
    int hi = ch >= 9 ? 0x100 : 0;
    w(0xa0 + (ch % 9) + hi, fnum & 0xff);
    w(0xb0 + (ch % 9) + hi, 0x20 | (block << 2) | ((fnum >> 8) & 3));
}

static void key_off(int ch, int fnum, int block)
{
    int hi = ch >= 9 ? 0x100 : 0;
    w(0xa0 + (ch % 9) + hi, fnum & 0xff);
    w(0xb0 + (ch % 9) + hi, (block << 2) | ((fnum >> 8) & 3));
}

static void scenario_basic(int rate)
{
    char name[64];
    snprintf(name, sizeof name, "basic-2op-%d", rate);
    begin(name, rate, 1);
    w(0x01, 0x20);
    // Classic AdLib test instrument: modulator mult 1, carrier mult 1, FM, feedback 3.
    setup_channel(0, 0x01, 0x10, 0xf0, 0x77, 0, 0x01, 0x00, 0xf0, 0x77, 0, 0x06);
    key_on(0, 0x241, 4);
    gen_stream(rate / 2);
    key_off(0, 0x241, 4);
    gen_stream(rate / 4);
    // Additive connection, other pitch.
    setup_channel(0, 0x21, 0x0a, 0xa4, 0x35, 0, 0x22, 0x00, 0xc2, 0x15, 0, 0x0f);
    key_on(0, 0x181, 3);
    gen_stream(rate / 4);
    key_off(0, 0x181, 3);
    gen_stream(rate / 8);
    end();
}

static void scenario_opl2_waveforms(void)
{
    int ch;
    begin("opl2-nine-channels", 48000, 2);
    w(0x01, 0x20);
    w(0x08, 0x00);
    w(0xbd, 0xc0);
    for (ch = 0; ch < 9; ch++)
    {
        int wf = ch & 3;
        setup_channel(ch,
                      0x20 | (ch & 15) | ((ch & 1) ? 0x80 : 0) | ((ch & 2) ? 0x40 : 0), (ch * 5) & 0x3f | ((ch & 3) << 6),
                      0xa0 | (ch & 7), 0x40 | ((ch * 3) & 15), wf,
                      0x21 + (ch & 3) * 0x40, (ch * 2) & 0x1f | ((ch & 3) << 6), 0xb0 | (ch & 3), 0x30 | ((ch * 5) & 15),
                      (wf + 1) & 3, ((ch & 7) << 1) | (ch & 1));
        key_on(ch, 0x100 + ch * 37, ch % 8);
    }
    gen_stream(6000);
    gen_native(2000);
    for (ch = 0; ch < 9; ch += 2)
    {
        key_off(ch, 0x100 + ch * 37, ch % 8);
    }
    gen_stream(6000);
    w(0x08, 0x40);
    for (ch = 0; ch < 9; ch++)
    {
        key_on(ch, 0x80 + ch * 91, (ch + 3) % 8);
    }
    gen_stream(6000);
    end();
}

static void scenario_opl3_waveforms(int rate)
{
    int ch;
    char name[64];
    snprintf(name, sizeof name, "opl3-eighteen-channels-%d", rate);
    begin(name, rate, 3);
    w(0x01, 0x20);
    w(0x105, 0x01);
    w(0xbd, 0x80);
    for (ch = 0; ch < 18; ch++)
    {
        int wf = ch & 7;
        int pan = 0x10 << (ch % 3 == 2 ? 1 : 0);
        if (ch % 3 == 0)
        {
            pan = 0x30;
        }
        setup_channel(ch,
                      0x21 + (ch & 7), (ch * 3) & 0x3f, 0xe0 | (ch & 7), 0x30 | (ch & 7), wf,
                      0x01 | ((ch & 1) << 6), (ch * 7) & 0x1f, 0xd0 | (ch & 3), 0x20 | (ch & 7), (wf + 3) & 7,
                      pan | ((ch & 7) << 1) | ((ch >> 1) & 1));
        key_on(ch, 0x120 + ch * 29, 2 + ch % 5);
    }
    gen_stream(rate / 4);
    for (ch = 0; ch < 18; ch += 3)
    {
        key_off(ch, 0x120 + ch * 29, 2 + ch % 5);
    }
    gen_stream(rate / 8);
    end();
}

static void scenario_four_op(void)
{
    int pair, alg;
    begin("opl3-four-op", 48000, 4);
    w(0x01, 0x20);
    w(0x105, 0x01);
    w(0x104, 0x3f);
    for (alg = 0; alg < 4; alg++)
    {
        for (pair = 0; pair < 6; pair++)
        {
            int ch = pair < 3 ? pair : pair + 6;
            int c0 = ((alg & 1) ? 0x01 : 0x00) | 0x30 | (3 << 1);
            int c1 = ((alg & 2) ? 0x01 : 0x00) | 0x30 | (2 << 1);
            setup_channel(ch, 0x21, 0x14, 0xf2, 0x53, 0, 0x01, 0x00, 0xf1, 0x45, 0, c0);
            setup_channel(ch + 3, 0x22, 0x10 + pair, 0xe3, 0x34, pair & 3, 0x02, 0x08, 0xd2, 0x35, 1, c1);
            key_on(ch, 0x200 + pair * 40, 3 + (pair & 1));
        }
        gen_stream(4000);
        for (pair = 0; pair < 6; pair++)
        {
            int ch = pair < 3 ? pair : pair + 6;
            key_off(ch, 0x200 + pair * 40, 3 + (pair & 1));
        }
        gen_stream(2000);
    }
    // Switch back to 2-op on some pairs while others stay 4-op.
    w(0x104, 0x15);
    for (pair = 0; pair < 6; pair++)
    {
        int ch = pair < 3 ? pair : pair + 6;
        key_on(ch, 0x180 + pair * 17, 4);
    }
    gen_stream(5000);
    end();
}

static void scenario_rhythm(int opl3)
{
    int step;
    char name[64];
    snprintf(name, sizeof name, "rhythm-%s", opl3 ? "opl3" : "opl2");
    begin(name, 48000, opl3 ? 6 : 5);
    w(0x01, 0x20);
    if (opl3)
    {
        w(0x105, 0x01);
    }
    // Channels 6, 7, 8 as drum voices.
    setup_channel(6, 0x01, 0x08, 0xf8, 0x47, 0, 0x01, 0x00, 0xf8, 0x66, 0, 0x08);
    setup_channel(7, 0x01, 0x0c, 0xf0, 0x77, 0, 0x01, 0x00, 0xf5, 0x76, 0, 0x00);
    setup_channel(8, 0x04, 0x00, 0xf6, 0x66, 0, 0x01, 0x02, 0xf7, 0x77, 0, 0x04);
    key_off(6, 0x120, 2);
    key_off(7, 0x150, 2);
    key_off(8, 0x1c0, 2);
    w(0xbd, 0x20 | 0x80 | 0x40);
    gen_stream(500);
    for (step = 0; step < 32; step++)
    {
        int bits = (step * 5 + (step >> 2)) & 0x1f;
        w(0xbd, 0xc0 | 0x20 | bits);
        gen_stream(1500);
        w(0xbd, 0xc0 | 0x20);
        gen_stream(300);
    }
    // Leave rhythm mode again: channels 6..8 become 2-op melodic.
    w(0xbd, 0x00);
    key_on(6, 0x200, 4);
    key_on(7, 0x240, 4);
    key_on(8, 0x280, 4);
    gen_stream(4000);
    end();
}

static void scenario_envelopes(void)
{
    int ch, round;
    begin("envelopes", 48000, 7);
    w(0x01, 0x20);
    for (round = 0; round < 4; round++)
    {
        for (ch = 0; ch < 9; ch++)
        {
            int ar = (ch * 2 + round * 3) & 15;
            int dr = (ch * 5 + round) & 15;
            int sl = (ch + round * 4) & 15;
            int rr = (15 - ch + round) & 15;
            int egt = (ch + round) & 1 ? 0x20 : 0x00;
            int ksr = (ch >> 1) & 1 ? 0x10 : 0x00;
            int ksl = (ch & 3) << 6;
            setup_channel(ch, 0x01 | egt | ksr, 0x00 | ksl, (ar << 4) | dr, (sl << 4) | rr, 0,
                          0x01 | egt | ksr, (ch * 2) | ksl, (ar << 4) | dr, (sl << 4) | rr, 0, 0x01);
            key_on(ch, 0x1a0 + ch * 23, (ch + round) % 8);
        }
        gen_stream(9000);
        for (ch = 0; ch < 9; ch++)
        {
            key_off(ch, 0x1a0 + ch * 23, (ch + round) % 8);
        }
        gen_stream(9000);
    }
    end();
}

static void scenario_writebuf(void)
{
    int i;
    begin("write-queue-overflow", 48000, 8);
    // More buffered writes than the 1024-entry queue holds, with no generation in between.
    w(0x01, 0x20);
    for (i = 0; i < 3000; i++)
    {
        int ch = i % 9;
        w(0x20 + mod_off[ch], 0x01 + (i & 15));
        w(0x23 + mod_off[ch], 0x01);
        w(0x43 + mod_off[ch] + 3, (i * 7) & 0x3f);
        w(0x60 + mod_off[ch], 0xf0 | (i & 15));
        w(0x80 + mod_off[ch], 0x77);
        w(0xa0 + ch, (i * 13) & 0xff);
        w(0xb0 + ch, 0x20 | ((i >> 3) & 0x1f));
    }
    gen_stream(3000);
    gen_native(1500);
    end();
}

static int valid_reg(int idx)
{
    static const int bases[] = {
        0x20, 0x40, 0x60, 0x80, 0xe0
    };
    int hi = (rnd() & 1) ? 0x100 : 0;
    int pick = rnd() % 20;
    if (pick < 10)
    {
        // slot register: base + operator offset (some invalid offsets 6, 7, 14, 15 on purpose)
        int b = bases[rnd() % 5];
        int off = (rnd() % 3) * 8 + (rnd() % 8);
        return hi | (b + off);
    }
    if (pick < 13)
    {
        return hi | (0xa0 + rnd() % 10);
    }
    if (pick < 16)
    {
        return hi | (0xb0 + rnd() % 10);
    }
    if (pick < 18)
    {
        return hi | (0xc0 + rnd() % 10);
    }
    if (pick == 18)
    {
        static const int misc[] = { 0x01, 0x08, 0xbd, 0x104, 0x105, 0x02, 0x103 };
        return misc[rnd() % 7];
    }
    (void) idx;
    return hi | (0xbd);
}

static void scenario_fuzz(int n, int rate, int opl3)
{
    int i;
    char name[64];
    snprintf(name, sizeof name, "fuzz-%d-%d-%s", n, rate, opl3 ? "opl3" : "opl2");
    begin(name, rate, 0x9e3779b9u * (n + 1));
    w(0x01, 0x20);
    if (opl3)
    {
        w(0x105, 0x01);
    }
    for (i = 0; i < 1500; i++)
    {
        int count = 1 + rnd() % 6;
        int j;
        for (j = 0; j < count; j++)
        {
            int reg = valid_reg(i);
            int val = rnd() & 0xff;
            if ((reg & 0xff) == 0x05 && (reg & 0x100))
            {
                // keep the OPL2/OPL3 mode as chosen by the scenario most of the time
                val = (rnd() % 16 == 0) ? val : (opl3 ? 1 : 0);
            }
            if (rnd() % 3 == 0)
            {
                wd(reg, val);
            }
            else
            {
                w(reg, val);
            }
        }
        if (rnd() % 4 == 0)
        {
            gen_native(1 + rnd() % 200);
        }
        else
        {
            gen_stream(1 + rnd() % 400);
        }
    }
    end();
}

int main(int argc, char **argv)
{
    if (argc < 2)
    {
        fprintf(stderr, "usage: %s <output.txt>\n", argv[0]);
        return 1;
    }
    out = fopen(argv[1], "w");
    if (!out)
    {
        perror("fopen");
        return 1;
    }
    buf = malloc(sizeof(int16_t) * 2 * 400000);
    fprintf(out, "# opl3-vectors v1\n");

    scenario_basic(48000);
    scenario_basic(44100);
    scenario_basic(49716);
    scenario_opl2_waveforms();
    scenario_opl3_waveforms(48000);
    scenario_opl3_waveforms(44100);
    scenario_four_op();
    scenario_rhythm(0);
    scenario_rhythm(1);
    scenario_envelopes();
    scenario_writebuf();
    scenario_fuzz(0, 48000, 0);
    scenario_fuzz(1, 48000, 1);
    scenario_fuzz(2, 44100, 1);
    scenario_fuzz(3, 44100, 0);

    fclose(out);
    free(buf);
    return 0;
}
