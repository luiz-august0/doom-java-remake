# Agent guide (common rules for every implementation agent)

Project: `doom-java`, a DOOM (1993) remake in Java 25. The simulation must be bit-identical to DOOM.EXE v1.9
(demos sync tic by tic against a patched Chocolate Doom "oracle"); the presentation is modern.

- You work in a git worktree (your current directory). Main tree: `DOOM_HOME=/Users/luizaugustomarques/Documents/projetos/doom-java`.
- **Read `docs/contracts.md` first** (ownership §2, coding rules §3, workflow §4, paths §5, trace format §6, oracle §7,
  report template §13). It overrides `docs/spec/*` where they differ.
- References: original C source `/Users/luizaugustomarques/Documents/projetos/DOOM/linuxdoom-1.10`; Chocolate Doom 3.1.1
  source `$DOOM_HOME/tools/oracle/work/chocolate-doom` (wins when they differ). IWADs: `$DOOM_HOME/.iwads/`
  (`doom1.wad` shareware 1.9, `freedoom1.wad`, `freedoom2.wad`, `freedm.wad`).
- Oracle: binary `$DOOM_HOME/tools/oracle/work/build/src/chocolate-doom`, runner `tools/oracle/run.sh`, formats in
  `tools/oracle/FORMAT.md`; reference traces in `testdata/traces/`; frames/sidecars/defs dumps in `$DOOM_HOME/tools/oracle/work/cache/`.
- Edit only what you own. Never edit `pom.xml`, `module-info.java`, `docs/contracts.md` or this guide: put requests in your report.
- Build: `./mvnw -o -q -pl <modules> -am verify` (never `install`). ITs: `DOOM_IWAD_DIR=$DOOM_HOME/.iwads ./mvnw -o -q -pl doom-it -am verify`.
- If plain `git` is refused in Bash, use `/usr/bin/git`.
- English everywhere. `@VanillaRef("file.c:Function")` on code reproducing an original rule. No mutable static state.
- **Commit work in progress after every meaningful step** (usage limits interrupt agents; uncommitted work is lost).
  End commit messages with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Be economical: inspect big files with `grep` / `sed -n` ranges instead of reading them whole.
- Finish with the contracts §13 report (the only thing the orchestrator sees).

## Continuing interrupted work
If your task says you continue a previous attempt: run `git merge <branch>` (given in your task) in your worktree,
then `git log --stat main..HEAD` to see what is done, read the existing code, run the build/tests, and continue.
Do not redo finished parts; fix them only if wrong.
