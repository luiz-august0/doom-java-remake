/*
 * C cross-check for doom-math (FixedMul, FixedDiv, SlopeDiv, R_PointToAngle2, P_AproxDistance).
 *
 * Built by run.sh against the Chocolate Doom 3.1.1 sources:
 *   - FixedMul / FixedDiv (64-bit version) from src/m_fixed.c and SlopeDiv / tantoangle from src/tables.c are
 *     compiled and linked unchanged;
 *   - R_PointToAngle / R_PointToAngle2 (src/doom/r_main.c) and P_AproxDistance (src/doom/p_maputl.c) are extracted
 *     verbatim by run.sh into "extracted.c", which this file includes.
 *
 * Output (stdout) is the vector file replayed by doom.math.CrossCheckTest:
 *   - explicit edge-case vectors:  "<fn> <inputs...> <result>", all numbers signed 32-bit decimal;
 *   - digests of millions of pseudo-random inputs:
 *       "digest <fn> <distribution> <seed> <count> <fnv1a64 hex>"
 *     The inputs come from SplitMix64 and the distributions defined below; the Java test regenerates exactly the
 *     same inputs and hashes its own results with FNV-1a 64 over the little-endian bytes of each int32 result.
 *
 * Every random draw is a separate statement so that the evaluation order is fixed (the Java side mirrors it).
 * Input construction uses unsigned arithmetic only, so it has no undefined behaviour.
 */

#include <inttypes.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "doomtype.h"
#include "m_fixed.h"
#include "tables.h"

/* Globals used by the extracted R_PointToAngle. */
fixed_t viewx;
fixed_t viewy;

#include "extracted.c"

#ifndef COMPILER_DESCRIPTION
#define COMPILER_DESCRIPTION "unknown"
#endif

/* ------------------------------------------------------------------------------------------------ PRNG */

static uint64_t rng_state;

static void seed_rng(uint64_t seed)
{
    rng_state = seed;
}

static uint64_t next64(void)
{
    uint64_t z = (rng_state += UINT64_C(0x9e3779b97f4a7c15));
    z = (z ^ (z >> 30)) * UINT64_C(0xbf58476d1ce4e5b9);
    z = (z ^ (z >> 27)) * UINT64_C(0x94d049bb133111eb);
    return z ^ (z >> 31);
}

static uint32_t u32(void)
{
    return (uint32_t) (next64() >> 32);
}

/* Two's-complement reinterpretation (well defined for every value with clang/gcc). */
static int32_t as_signed(uint32_t v)
{
    return (int32_t) v;
}

/* A random value with a random magnitude: a random 32-bit value shifted right (arithmetic) by 0..31. */
static uint32_t magnitude(void)
{
    uint32_t v = u32();
    uint32_t shift = u32() & 31;
    return (uint32_t) (as_signed(v) >> shift);
}

/* A small value in [-2, 2]. */
static uint32_t small_delta(void)
{
    return (uint32_t) ((int32_t) (u32() % 5) - 2);
}

/* ------------------------------------------------------------------------------------------------ digest */

static uint64_t fnv;

static void fnv_reset(void)
{
    fnv = UINT64_C(0xcbf29ce484222325);
}

static void fnv_int(int32_t value)
{
    uint32_t v = (uint32_t) value;
    int i;
    for (i = 0; i < 4; i++)
    {
        fnv ^= (v >> (8 * i)) & 0xff;
        fnv *= UINT64_C(0x100000001b3);
    }
}

/* ------------------------------------------------------------------------------------------------ functions */

static int32_t call_p2a(uint32_t x1, uint32_t y1, uint32_t x2, uint32_t y2)
{
    return (int32_t) R_PointToAngle2(as_signed(x1), as_signed(y1), as_signed(x2), as_signed(y2));
}

/* ------------------------------------------------------------------------------------------------ digests */

#define COUNT (1u << 21)

enum { D_UNIFORM, D_MAGNITUDE, D_MIXED, D_DIVEDGE, D_SMALLDEN };
static const char *dist2_names[] = {"uniform", "magnitude", "mixed", "divedge", "smallden"};

static void draw2(int dist, uint32_t *a, uint32_t *b)
{
    switch (dist)
    {
        case D_UNIFORM:
            *a = u32();
            *b = u32();
            break;
        case D_MAGNITUDE:
            *a = magnitude();
            *b = magnitude();
            break;
        case D_MIXED:
            *a = u32();
            *b = magnitude();
            break;
        case D_DIVEDGE:
        {
            /* |a| >> 14 is |b| or within 2 of it: the FixedDiv saturation boundary. */
            uint32_t m;
            uint32_t delta;
            *b = magnitude();
            m = as_signed(*b) < 0 ? 0u - *b : *b;
            delta = small_delta();
            *a = (m << 14) + delta;
            if (u32() & 1)
            {
                *a = 0u - *a;
            }
            break;
        }
        case D_SMALLDEN:
            *a = magnitude();
            *b = u32() & 0x3ff;
            break;
    }
}

static void digest2(const char *fn, int dist, uint64_t seed)
{
    uint32_t i;
    uint32_t a;
    uint32_t b;
    int32_t r;

    seed_rng(seed);
    fnv_reset();
    for (i = 0; i < COUNT; i++)
    {
        draw2(dist, &a, &b);
        if (!strcmp(fn, "mul"))
        {
            r = FixedMul(as_signed(a), as_signed(b));
        }
        else if (!strcmp(fn, "div"))
        {
            if (a == 0x80000000u && b == 0)
            {
                r = 0x7eadbeef; /* FixedDiv(INT_MIN, 0) divides by zero; the Java side uses the same marker */
            }
            else
            {
                r = FixedDiv(as_signed(a), as_signed(b));
            }
        }
        else if (!strcmp(fn, "slope"))
        {
            r = SlopeDiv(a, b);
        }
        else
        {
            r = P_AproxDistance(as_signed(a), as_signed(b));
        }
        fnv_int(r);
    }
    printf("digest %s %s %" PRIu64 " %u %016" PRIx64 "\n", fn, dist2_names[dist], seed, COUNT, fnv);
}

enum { P_UNIFORM, P_MAGNITUDE, P_NEAR, P_DIAG, P_AXIS };
static const char *dist4_names[] = {"uniform", "magnitude", "near", "diag", "axis"};

static void digest_p2a(int dist, uint64_t seed)
{
    uint32_t i;

    seed_rng(seed);
    fnv_reset();
    for (i = 0; i < COUNT; i++)
    {
        uint32_t x1;
        uint32_t y1;
        uint32_t x2;
        uint32_t y2;

        if (dist == P_MAGNITUDE)
        {
            x1 = magnitude();
            y1 = magnitude();
            x2 = magnitude();
            y2 = magnitude();
        }
        else
        {
            x1 = u32();
            y1 = u32();
            if (dist == P_UNIFORM)
            {
                x2 = u32();
                y2 = u32();
            }
            else if (dist == P_NEAR)
            {
                uint32_t dx = (uint32_t) (as_signed(magnitude()) >> 8);
                uint32_t dy = (uint32_t) (as_signed(magnitude()) >> 8);
                x2 = x1 + dx;
                y2 = y1 + dy;
            }
            else if (dist == P_DIAG)
            {
                /* |dx| and |dy| equal or within 2: the octant boundaries x == y and x == -y. */
                uint32_t d = magnitude();
                uint32_t e1 = small_delta();
                uint32_t e2 = small_delta();
                uint32_t sx = u32() & 1;
                uint32_t sy = u32() & 1;
                uint32_t dx = (sx ? 0u - d : d) + e1;
                uint32_t dy = (sy ? 0u - d : d) + e2;
                x2 = x1 + dx;
                y2 = y1 + dy;
            }
            else
            {
                /* P_AXIS: one coordinate equal, or both within 2. */
                uint32_t k = u32() % 3;
                uint32_t dx;
                uint32_t dy;
                if (k == 0)
                {
                    dx = 0;
                    dy = magnitude();
                }
                else if (k == 1)
                {
                    dx = magnitude();
                    dy = 0;
                }
                else
                {
                    dx = small_delta();
                    dy = small_delta();
                }
                x2 = x1 + dx;
                y2 = y1 + dy;
            }
        }
        fnv_int(call_p2a(x1, y1, x2, y2));
    }
    printf("digest p2a %s %" PRIu64 " %u %016" PRIx64 "\n", dist4_names[dist], seed, COUNT, fnv);
}

/* ------------------------------------------------------------------------------------------------ explicit */

static const uint32_t edges[] = {
    0, 1, 0xffffffffu, 2, 0xfffffffeu, 7, 255, 511, 512, 513, 0xfffffe00u,
    0x3fff, 0x4000, 0x4001, 0xffffc000u, 0xffff, 0x10000, 0xffff0000u, 0x10001, 0x20000,
    0x00640000 /* 100.0 */, 0xff9c0000u /* -100.0 */, 0x12345678, 0xedcba988u,
    0x1fffffff, 0x20000000, 0x3fffffff, 0x40000000, 0xc0000000u,
    0x7ffffffe, 0x7fffffff, 0x80000000u, 0x80000001u,
};

#define NUM_EDGES (sizeof(edges) / sizeof(edges[0]))

static void explicit2(void)
{
    size_t i;
    size_t j;

    for (i = 0; i < NUM_EDGES; i++)
    {
        for (j = 0; j < NUM_EDGES; j++)
        {
            int32_t a = as_signed(edges[i]);
            int32_t b = as_signed(edges[j]);
            printf("mul %d %d %d\n", a, b, FixedMul(a, b));
            if (!(a == INT_MIN && b == 0))
            {
                printf("div %d %d %d\n", a, b, FixedDiv(a, b));
            }
            printf("slope %d %d %d\n", a, b, SlopeDiv(edges[i], edges[j]));
            printf("approx %d %d %d\n", a, b, P_AproxDistance(a, b));
        }
    }

    /* FixedDiv saturation boundary: |a| >> 14 == |b| +- 1. */
    {
        static const uint32_t divisors[] = {1, 2, 3, 0x100, 0x7fff, 0x8000, 0x10000, 0x1ffff, 0x20000};
        size_t k;
        for (k = 0; k < sizeof(divisors) / sizeof(divisors[0]); k++)
        {
            int sb;
            int sa;
            int d;
            for (sb = 0; sb < 2; sb++)
            {
                uint32_t b = sb ? 0u - divisors[k] : divisors[k];
                for (d = -1; d <= 1; d++)
                {
                    for (sa = 0; sa < 2; sa++)
                    {
                        uint32_t a = (divisors[k] << 14) + (uint32_t) d;
                        if (sa)
                        {
                            a = 0u - a;
                        }
                        if (!(a == 0x80000000u && b == 0))
                        {
                            printf("div %d %d %d\n", as_signed(a), as_signed(b), FixedDiv(as_signed(a), as_signed(b)));
                        }
                    }
                }
            }
        }
    }
}

static void explicit_p2a(void)
{
    static const uint32_t origins[][2] = {
        {0, 0}, {0x80000000u, 0x80000000u}, {0x7fffffff, 0x80000000u}, {0x03e80000, 0xfc180000u},
    };
    static const uint32_t coords[] = {
        0, 1, 0xffffffffu, 511, 512, 0x10000, 0xffff0000u, 0x7fffffff, 0x80000000u, 0x40000000, 0xc0000000u,
        0x12345678,
    };
    static const uint32_t diagonal[] = {1, 2, 511, 512, 513, 0x10000, 0x40000000, 0x7fffffff};
    size_t o;
    size_t i;
    size_t j;

    for (o = 0; o < sizeof(origins) / sizeof(origins[0]); o++)
    {
        for (i = 0; i < sizeof(coords) / sizeof(coords[0]); i++)
        {
            for (j = 0; j < sizeof(coords) / sizeof(coords[0]); j++)
            {
                uint32_t x1 = origins[o][0];
                uint32_t y1 = origins[o][1];
                printf("p2a %d %d %d %d %d\n", as_signed(x1), as_signed(y1), as_signed(coords[i]), as_signed(coords[j]),
                       call_p2a(x1, y1, coords[i], coords[j]));
            }
        }
    }

    /* Octant boundaries around the origin: |x| == |y| and off by one. */
    for (i = 0; i < sizeof(diagonal) / sizeof(diagonal[0]); i++)
    {
        uint32_t k = diagonal[i];
        uint32_t points[][2] = {
            {k, k}, {k, 0u - k}, {0u - k, k}, {0u - k, 0u - k},
            {k, k - 1}, {k - 1, k}, {0u - k, k - 1}, {k - 1, 0u - k},
            {0u - (k - 1), 0u - k}, {0u - k, 0u - (k - 1)}, {k, 0}, {0, 0u - k},
        };
        size_t p;
        for (p = 0; p < sizeof(points) / sizeof(points[0]); p++)
        {
            printf("p2a 0 0 %d %d %d\n", as_signed(points[p][0]), as_signed(points[p][1]),
                   call_p2a(0, 0, points[p][0], points[p][1]));
        }
    }
}

int main(void)
{
    printf("# doom-math C cross-check vectors. Generated by doom-math/src/test/c/run.sh; do not edit.\n");
    printf("# Reference: Chocolate Doom 3.1.1 m_fixed.c, tables.c, r_main.c, p_maputl.c\n");
    printf("# Compiler: %s\n", COMPILER_DESCRIPTION);

    explicit2();
    explicit_p2a();

    digest2("mul", D_UNIFORM, 1);
    digest2("mul", D_MAGNITUDE, 2);
    digest2("mul", D_MIXED, 3);
    digest2("div", D_UNIFORM, 4);
    digest2("div", D_MAGNITUDE, 5);
    digest2("div", D_MIXED, 6);
    digest2("div", D_DIVEDGE, 7);
    digest2("slope", D_UNIFORM, 8);
    digest2("slope", D_MAGNITUDE, 9);
    digest2("slope", D_SMALLDEN, 10);
    digest2("approx", D_UNIFORM, 11);
    digest2("approx", D_MAGNITUDE, 12);
    digest_p2a(P_UNIFORM, 13);
    digest_p2a(P_MAGNITUDE, 14);
    digest_p2a(P_NEAR, 15);
    digest_p2a(P_DIAG, 16);
    digest_p2a(P_AXIS, 17);
    return 0;
}
