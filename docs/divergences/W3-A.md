# Divergences recorded by W3-A (monster AI, `doom.game.ai`)

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| `A_BrainAwake` target list | `braintargets[32]` is a fixed array; a map with more than 32 `MT_BOSSTARGET` things overflows it into other memory | growable array | no defined behaviour beyond the original's limit | p_enemy.c:A_BrainAwake |
| `A_BrainSpit` division | `((targ->y - mo->y) / newmobj->momy) / newmobj->state->tics` crashes (SIGFPE) when `momy` or the state's tics is 0 | `DoomFatalException("A_BrainSpit: division by zero ...")` | a fatal error instead of a host crash | p_enemy.c:A_BrainSpit |
| Boss-death dummy line | `line_t junk` on the stack with only `tag` set | one zero-filled `Line` per session with only `tag` set | the floor/door code reads only the tag for the types used | p_enemy.c:A_BossDeath, A_KeenDie |

## Notes (not divergences of ours)

- Game versions older than 1.9 (`exe_doom_1_2`, `exe_doom_1_5`) are not supported by `GameVersion`, so the conditionals
  `gameversion < exe_doom_1_5`, `<= exe_doom_1_2`, `> exe_doom_1_2` of `P_CheckMeleeRange`, `A_SargAttack` and `A_Chase`
  are resolved to their 1.9 outcome. `A_BossDeath`/`CheckBossEnd` keep the `< exe_ultimate` / `>= exe_ultimate` rule.
- **Oracle trace artefact (dangling `tracer`)**: the Arch-vile keeps `tracer` pointing at its `MT_FIRE` after the fire is
  freed. In the oracle, the freed zone block can be reused by a mobj spawned later (e.g. a bullet puff), so the trace prints
  the thinker index of that unrelated mobj, while we print `-2` (not a live thinker). The synthetic demos `f2m29a`
  (tic 1036) and `f2m29b` (tic 1201) differ only in this field of the Arch-vile (`a` hash); the simulation is otherwise
  identical. Not reproducible without emulating the zone allocator.
