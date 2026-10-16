// Reference harness for the Java music driver: runs Chocolate Doom's ORIGINAL i_oplmusic.c / mus2mid.c / midifile.c /
// opl3.c with the SDL/OPL layer replaced by opl_harness.c.
//
//   musicref play <wad> <lump> <rate> <block> <seconds> <musicvol0-127> <opl3 0|1> <loop 0|1> <reglog> <pcm|->
//       Plays a music lump for <seconds> of output, mixing <block> frames at a time. Writes "time_us reg value" lines
//       (every register write that reaches the chip) and optionally raw little-endian s16 stereo PCM.
//   musicref mus2mid <wad> <out.txt>
//       Converts every D_* lump with mus2mid (one forked process per lump, so static state cannot leak) and writes
//       "<name> <size> <fnv1a64 of the MIDI bytes>" or "<name> FAIL".
//   musicref mid <wad> <lump> <out.mid>
//       Writes the mus2mid output of one lump.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <ctype.h>
#include <unistd.h>
#include <sys/wait.h>

#include "doomtype.h"
#include "i_sound.h"
#include "memio.h"
#include "mus2mid.h"
#include "opl.h"

extern FILE *opl_reg_log;
void harness_mix(int16_t *buffer, unsigned int buffer_samples);

// ---- stubs for the rest of Chocolate Doom

int snd_samplerate = 48000;

typedef struct
{
    char name[9];
    uint8_t *data;
    int size;
} lump_t;

static lump_t *lumps;
static int numlumps;
static const char *genmidi_source;

static uint32_t rd32(const uint8_t *p)
{
    return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t) p[3] << 24);
}

static void load_wad(const char *path)
{
    FILE *f = fopen(path, "rb");
    long size;
    uint8_t *buf;
    int i, dirofs;

    if (!f)
    {
        perror(path);
        exit(1);
    }
    fseek(f, 0, SEEK_END);
    size = ftell(f);
    fseek(f, 0, SEEK_SET);
    buf = malloc(size);
    if (fread(buf, 1, size, f) != (size_t) size)
    {
        exit(1);
    }
    fclose(f);
    numlumps = rd32(buf + 4);
    dirofs = rd32(buf + 8);
    lumps = calloc(numlumps, sizeof(lump_t));
    for (i = 0; i < numlumps; i++)
    {
        const uint8_t *e = buf + dirofs + i * 16;
        int j;
        lumps[i].data = buf + rd32(e);
        lumps[i].size = rd32(e + 4);
        for (j = 0; j < 8 && e[8 + j]; j++)
        {
            lumps[i].name[j] = toupper(e[8 + j]);
        }
    }
    genmidi_source = path;
}

static lump_t *find_lump(const char *name)
{
    int i;
    for (i = numlumps - 1; i >= 0; i--)
    {
        if (strcasecmp(lumps[i].name, name) == 0)
        {
            return &lumps[i];
        }
    }
    return NULL;
}

void *W_CacheLumpName(const char *name, int tag)
{
    lump_t *l = find_lump(name);
    (void) tag;
    if (!l)
    {
        fprintf(stderr, "lump %s not found in %s\n", name, genmidi_source);
        exit(1);
    }
    return l->data;
}

void W_ReleaseLumpName(const char *name)
{
    (void) name;
}

FILE *M_fopen(const char *filename, const char *mode)
{
    return fopen(filename, mode);
}

int M_remove(const char *path)
{
    return remove(path);
}

boolean M_WriteFile(const char *name, const void *source, int length)
{
    FILE *f = fopen(name, "wb");
    if (!f)
    {
        return false;
    }
    fwrite(source, 1, length, f);
    fclose(f);
    return true;
}

char *M_TempFile(const char *s)
{
    char buf[256];
    snprintf(buf, sizeof buf, "%s/musicref-%d-%s", getenv("TMPDIR") ? getenv("TMPDIR") : "/tmp", (int) getpid(), s);
    return strdup(buf);
}

void *I_Realloc(void *ptr, size_t size)
{
    void *p = realloc(ptr, size);
    if (!p && size)
    {
        exit(1);
    }
    return p;
}

int M_snprintf(char *buf, size_t buf_len, const char *s, ...)
{
    (void) buf;
    (void) buf_len;
    (void) s;
    return 0;
}

boolean M_StringConcat(char *dest, const char *src, size_t dest_size)
{
    (void) dest;
    (void) src;
    (void) dest_size;
    return false;
}

const char *DEH_String(const char *s)
{
    return s;
}

void *Z_Malloc(int size, int tag, void *user)
{
    (void) tag;
    (void) user;
    return malloc(size);
}

void Z_Free(void *ptr)
{
    free(ptr);
}

// ---- helpers

static uint64_t fnv(const uint8_t *p, size_t n)
{
    uint64_t h = 0xcbf29ce484222325ULL;
    size_t i;
    for (i = 0; i < n; i++)
    {
        h ^= p[i];
        h *= 0x100000001b3ULL;
    }
    return h;
}

static int convert(lump_t *l, void **outbuf, size_t *outlen)
{
    MEMFILE *in = mem_fopen_read(l->data, l->size);
    MEMFILE *out = mem_fopen_write();
    int result = mus2mid(in, out);
    if (result == 0)
    {
        void *b;
        size_t n;
        mem_get_buf(out, &b, &n);
        *outbuf = malloc(n);
        memcpy(*outbuf, b, n);
        *outlen = n;
    }
    mem_fclose(in);
    mem_fclose(out);
    return result;
}

static int cmd_mus2mid(const char *wad, const char *outpath)
{
    int i;
    FILE *out = fopen(outpath, "w");
    load_wad(wad);
    for (i = 0; i < numlumps; i++)
    {
        pid_t pid;
        int status;
        if (strncmp(lumps[i].name, "D_", 2) != 0 || lumps[i].size == 0)
        {
            continue;
        }
        fflush(out);
        pid = fork();
        if (pid == 0)
        {
            void *b;
            size_t n;
            if (lumps[i].size > 4 && memcmp(lumps[i].data, "MThd", 4) == 0)
            {
                fprintf(out, "%s MIDI\n", lumps[i].name);
            }
            else if (convert(&lumps[i], &b, &n) != 0)
            {
                fprintf(out, "%s FAIL\n", lumps[i].name);
            }
            else
            {
                fprintf(out, "%s %zu %016llx\n", lumps[i].name, n, (unsigned long long) fnv(b, n));
            }
            fflush(out);
            _exit(0);
        }
        waitpid(pid, &status, 0);
    }
    fclose(out);
    return 0;
}

static int cmd_mid(const char *wad, const char *lump, const char *outpath)
{
    void *b;
    size_t n;
    lump_t *l;
    FILE *f;
    load_wad(wad);
    l = find_lump(lump);
    if (!l || convert(l, &b, &n) != 0)
    {
        fprintf(stderr, "conversion failed\n");
        return 1;
    }
    f = fopen(outpath, "wb");
    fwrite(b, 1, n, f);
    fclose(f);
    return 0;
}

static int cmd_play(char **argv)
{
    const char *wad = argv[0], *lumpname = argv[1];
    int rate = atoi(argv[2]);
    int block = atoi(argv[3]);
    double seconds = atof(argv[4]);
    int musicvol = atoi(argv[5]);
    int opl3 = atoi(argv[6]);
    int loop = atoi(argv[7]);
    const char *logpath = argv[8], *pcmpath = argv[9];
    long total, done = 0;
    int16_t *buf;
    FILE *pcm = NULL;
    lump_t *l;
    void *handle;

    load_wad(wad);
    l = find_lump(lumpname);
    if (!l)
    {
        fprintf(stderr, "lump %s not found\n", lumpname);
        return 1;
    }
    snd_samplerate = rate;
    if (opl3)
    {
        setenv("DMXOPTION", "-opl3", 1);
    }
    opl_reg_log = fopen(logpath, "w");
    if (strcmp(pcmpath, "-") != 0)
    {
        pcm = fopen(pcmpath, "wb");
    }
    if (!music_opl_module.Init || !music_opl_module.Init())
    {
        fprintf(stderr, "music init failed\n");
        return 1;
    }
    music_opl_module.SetMusicVolume(musicvol);
    handle = music_opl_module.RegisterSong(l->data, l->size);
    if (!handle)
    {
        fprintf(stderr, "register song failed\n");
        return 1;
    }
    music_opl_module.PlaySong(handle, loop);

    total = (long) (seconds * rate);
    buf = malloc(sizeof(int16_t) * 2 * block);
    while (done < total)
    {
        int n = total - done < block ? (int) (total - done) : block;
        harness_mix(buf, n);
        if (pcm)
        {
            fwrite(buf, 4, n, pcm);
        }
        done += n;
    }
    fclose(opl_reg_log);
    if (pcm)
    {
        fclose(pcm);
    }
    return 0;
}

int main(int argc, char **argv)
{
    if (argc >= 4 && strcmp(argv[1], "mus2mid") == 0)
    {
        return cmd_mus2mid(argv[2], argv[3]);
    }
    if (argc >= 5 && strcmp(argv[1], "mid") == 0)
    {
        return cmd_mid(argv[2], argv[3], argv[4]);
    }
    if (argc == 12 && strcmp(argv[1], "play") == 0)
    {
        return cmd_play(argv + 2);
    }
    fprintf(stderr, "usage: see the header of musicref.c\n");
    return 2;
}
