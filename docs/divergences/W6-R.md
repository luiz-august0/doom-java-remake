# W6-R divergences

| area | original behaviour | ours | reason | reference |
|---|---|---|---|---|
| Loose files on the command line | `M_AddLooseFiles` (Windows drag and drop): only when every argument is an absolute Windows path (drive or UNC); the IWAD is chosen by file name (`D_IsIWADName`) | `LooseFiles`: the arguments before the first parameter, on every OS; a `.wad` is the IWAD when its header says `IWAD`; `.wad` → `-merge`, `.deh`/`.bex` → `-deh`, `.lmp` → `-playdemo`; the parameters are appended, so explicit ones win | The file association of the Windows installer and dropping a file on a launcher pass relative or Unix paths too; a renamed IWAD is still found | m_argv.c:M_AddLooseFiles |
