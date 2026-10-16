# Fix task F1 — remaining red integration tests

Read `docs/agent-guide.md` and `docs/contracts.md` first. Goal: `DOOM_IWAD_DIR=$DOOM_HOME/.iwads ./mvnw -o verify` fully green
(and plain `./mvnw -o verify` without IWADs green), with no regression in `DemoSyncIT -Dsync.synthetic=all`.

1. `doom.it.trace.ReferenceTracesIT.realTracesParseAndAreWellFormed` expects keys `a,s,l,d,x,m` on every line, but the
   title-loop traces (`testdata/traces/title-*.trace.gz`) have only `t,gs,r,lt,map,tot,p0` on lines before the first
   level. Make the check match contracts §6 for those lines (and document in contracts-request if §6 needs wording).
2. `FlowSyncIT` needs `testdata/flow/secret.wad` (and maybe other PWADs) produced by `testdata/flow/make-flow-testdata.py`
   from Freedoom data; generate it at test time (or commit generated PWADs if they only contain map lumps derived from
   Freedoom, which is BSD) so the IT runs without manual steps; it must pass against the oracle.
3. `TitleLoopSyncIT`: the full title loop diverges from the oracle on key `l` (line hash) at tic 13726 (doom1),
   26253 (freedoom1), 14429 (freedoom2). Find the root cause with the verbose traces (oracle: run the title loop with
   `-trace`/`-traceverbose -tracefrom/-traceto` and no demo, see `tools/oracle/README.md`), fix it in the right place
   (simulation: line flags/special/tag; or the trace writer), and make the IT pass for the complete loop.
You may edit the packages involved; keep changes minimal and explain each in the report (contracts §13).
