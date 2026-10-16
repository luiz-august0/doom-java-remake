// Offline replacement for Chocolate Doom's OPL layer (opl/opl.c + opl/opl_sdl.c) so that the ORIGINAL
// src/i_oplmusic.c, src/mus2mid.c and src/midifile.c can run without SDL audio or a wall clock.
//
// The timing logic (callback queue, OPL_Mix_Callback chunking, AdvanceTime, pause handling) is copied from opl_sdl.c;
// SDL mutexes are dropped, and a log of every register write that reaches the chip (time in us, register, value) is
// produced. OPL detection is skipped: OPL_Init() just pretends to find an OPL3.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "opl3.h"
#include "opl.h"
#include "opl_queue.h"

unsigned int opl_sample_rate = 48000;

static opl_callback_queue_t *callback_queue;
static uint64_t current_time;
static int opl_sdl_paused;
static uint64_t pause_offset;
static opl3_chip opl_chip;
static int register_num;
static int mixing_freq;
static int16_t *mix_buffer;

FILE *opl_reg_log;

// ---- timers are not emulated: the writes are swallowed as in opl_sdl.c WriteRegister()

static void WriteRegister(unsigned int reg_num, unsigned int value)
{
    switch (reg_num)
    {
        case OPL_REG_TIMER1:
        case OPL_REG_TIMER2:
        case OPL_REG_TIMER_CTRL:
            break;

        default:
            if (opl_reg_log)
            {
                fprintf(opl_reg_log, "%llu %u %u\n", (unsigned long long) current_time, reg_num, value);
            }
            OPL3_WriteRegBuffered(&opl_chip, reg_num, value);
            break;
    }
}

void OPL_WritePort(opl_port_t port, unsigned int value)
{
    if (port == OPL_REGISTER_PORT)
    {
        register_num = value;
    }
    else if (port == OPL_REGISTER_PORT_OPL3)
    {
        register_num = value | 0x100;
    }
    else if (port == OPL_DATA_PORT)
    {
        WriteRegister(register_num, value);
    }
}

unsigned int OPL_ReadPort(opl_port_t port)
{
    (void) port;
    return 0;
}

unsigned int OPL_ReadStatus(void)
{
    return 0;
}

// Copy of opl.c OPL_WriteRegister, minus the (time-less) delay reads.
void OPL_WriteRegister(int reg, int value)
{
    if (reg & 0x100)
    {
        OPL_WritePort(OPL_REGISTER_PORT_OPL3, reg);
    }
    else
    {
        OPL_WritePort(OPL_REGISTER_PORT, reg);
    }
    OPL_WritePort(OPL_DATA_PORT, value);
}

opl_init_result_t OPL_Detect(void)
{
    return OPL_INIT_OPL3;
}

// Copy of opl.c OPL_InitRegisters.
void OPL_InitRegisters(int opl3)
{
    int r;

    for (r = OPL_REGS_LEVEL; r <= OPL_REGS_LEVEL + OPL_NUM_OPERATORS; ++r)
    {
        OPL_WriteRegister(r, 0x3f);
    }
    for (r = OPL_REGS_ATTACK; r <= OPL_REGS_WAVEFORM + OPL_NUM_OPERATORS; ++r)
    {
        OPL_WriteRegister(r, 0x00);
    }
    for (r = 1; r < OPL_REGS_LEVEL; ++r)
    {
        OPL_WriteRegister(r, 0x00);
    }
    OPL_WriteRegister(OPL_REG_TIMER_CTRL, 0x60);
    OPL_WriteRegister(OPL_REG_TIMER_CTRL, 0x80);
    OPL_WriteRegister(OPL_REG_WAVEFORM_ENABLE, 0x20);

    if (opl3)
    {
        OPL_WriteRegister(OPL_REG_NEW, 0x01);
        for (r = OPL_REGS_LEVEL; r <= OPL_REGS_LEVEL + OPL_NUM_OPERATORS; ++r)
        {
            OPL_WriteRegister(r | 0x100, 0x3f);
        }
        for (r = OPL_REGS_ATTACK; r <= OPL_REGS_WAVEFORM + OPL_NUM_OPERATORS; ++r)
        {
            OPL_WriteRegister(r | 0x100, 0x00);
        }
        for (r = 1; r < OPL_REGS_LEVEL; ++r)
        {
            OPL_WriteRegister(r | 0x100, 0x00);
        }
    }

    OPL_WriteRegister(OPL_REG_FM_MODE, 0x40);

    if (opl3)
    {
        OPL_WriteRegister(OPL_REG_NEW, 0x01);
    }
}

opl_init_result_t OPL_Init(unsigned int port_base)
{
    (void) port_base;
    mixing_freq = opl_sample_rate;
    opl_sdl_paused = 0;
    pause_offset = 0;
    callback_queue = OPL_Queue_Create();
    current_time = 0;
    mix_buffer = malloc(mixing_freq * 4);
    OPL3_Reset(&opl_chip, mixing_freq);
    return OPL_INIT_OPL3;
}

void OPL_Shutdown(void)
{
}

void OPL_SetSampleRate(unsigned int rate)
{
    opl_sample_rate = rate;
}

void OPL_SetCallback(uint64_t us, opl_callback_t callback, void *data)
{
    OPL_Queue_Push(callback_queue, callback, data, current_time - pause_offset + us);
}

void OPL_ClearCallbacks(void)
{
    OPL_Queue_Clear(callback_queue);
}

void OPL_Lock(void)
{
}

void OPL_Unlock(void)
{
}

void OPL_Delay(uint64_t us)
{
    (void) us;
}

void OPL_SetPaused(int paused)
{
    opl_sdl_paused = paused;
}

void OPL_AdjustCallbacks(float factor)
{
    OPL_Queue_AdjustCallbacks(callback_queue, current_time, factor);
}

// ---- copied from opl_sdl.c

static void AdvanceTime(unsigned int nsamples)
{
    opl_callback_t callback;
    void *callback_data;
    uint64_t us;

    us = ((uint64_t) nsamples * OPL_SECOND) / mixing_freq;
    current_time += us;

    if (opl_sdl_paused)
    {
        pause_offset += us;
    }

    while (!OPL_Queue_IsEmpty(callback_queue)
        && current_time >= OPL_Queue_Peek(callback_queue) + pause_offset)
    {
        if (!OPL_Queue_Pop(callback_queue, &callback, &callback_data))
        {
            break;
        }
        callback(callback_data);
    }
}

static void FillBuffer(int16_t *buffer, unsigned int nsamples)
{
    assert(nsamples < (unsigned) mixing_freq);
    OPL3_GenerateStream(&opl_chip, mix_buffer, nsamples);
    // SDL_MixAudioFormat into a silent buffer at full volume is a plain copy (the buffer starts at zero).
    memcpy(buffer, mix_buffer, nsamples * 4);
}

// Fills `buffer_samples` stereo frames, exactly like OPL_Mix_Callback().
void harness_mix(int16_t *buffer, unsigned int buffer_samples)
{
    unsigned int filled = 0;

    while (filled < buffer_samples)
    {
        uint64_t next_callback_time;
        uint64_t nsamples;

        if (opl_sdl_paused || OPL_Queue_IsEmpty(callback_queue))
        {
            nsamples = buffer_samples - filled;
        }
        else
        {
            next_callback_time = OPL_Queue_Peek(callback_queue) + pause_offset;

            nsamples = (next_callback_time - current_time) * mixing_freq;
            nsamples = (nsamples + OPL_SECOND - 1) / OPL_SECOND;

            if (nsamples > buffer_samples - filled)
            {
                nsamples = buffer_samples - filled;
            }
        }

        FillBuffer(buffer + filled * 2, nsamples);
        filled += nsamples;

        AdvanceTime(nsamples);
    }
}
