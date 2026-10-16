#!/usr/bin/env python3
"""Small helpers for the oracle scripts (no third-party dependencies).

  oracle-util.py demos <wad>                      list DEMOn lumps: version, map, players, tic count
  oracle-util.py demo-tics <wad> <lump>           print the tic count of one demo lump (or .lmp file)
  oracle-util.py png <wad> <frame.raw> <out.png> [palette|frame.scene]
                                                  convert a raw 320x200 indexed frame to PNG (PLAYPAL)
  oracle-util.py pick-tics <trace.gz> [count]     choose frame-dump tics from a trace (see pick_tics)
  oracle-util.py check-trace <trace.gz> <tics>    verify line count and tic numbering of a trace
"""
import gzip
import struct
import sys
import zlib


def read_wad(path):
    with open(path, "rb") as f:
        data = f.read()
    ident, numlumps, diroff = struct.unpack_from("<4sii", data, 0)
    if ident not in (b"IWAD", b"PWAD"):
        raise SystemExit(f"{path}: not a WAD file")
    lumps = []
    for i in range(numlumps):
        pos, size, name = struct.unpack_from("<ii8s", data, diroff + 16 * i)
        lumps.append((name.rstrip(b"\0").decode("ascii", "replace").upper(), pos, size))
    return data, lumps


def lump(data, lumps, name):
    found = None
    for n, pos, size in lumps:  # the last lump with that name wins, as in W_GetNumForName
        if n == name.upper():
            found = data[pos:pos + size]
    if found is None:
        raise SystemExit(f"lump {name} not found")
    return found


def demo_info(demo):
    """Header fields and tic count of a demo (v1.4+ format: 13-byte header)."""
    version = demo[0]
    if version < 104:  # pre-1.4: 7-byte header, always 4 player slots
        skill, episode, gamemap = demo[0], demo[1], demo[2]
        ingame = list(demo[3:7])
        header = 7
        info = dict(version=0, skill=skill, episode=episode, map=gamemap)
    else:
        skill, episode, gamemap, dm, respawn, fast, nomonsters, console = demo[1:9]
        ingame = list(demo[9:13])
        header = 13
        info = dict(version=version, skill=skill, episode=episode, map=gamemap, deathmatch=dm,
                    respawn=respawn, fast=fast, nomonsters=nomonsters, consoleplayer=console)
    players = sum(1 for p in ingame if p)
    body = demo[header:]
    end = body.find(b"\x80")
    # Ticcmds are 4 bytes per player; the end marker is the first 0x80 at a command boundary.
    tics = 0
    pos = 0
    while pos < len(body) and body[pos] != 0x80:
        pos += 4 * players
        tics += 1
    info.update(players=players, ingame=ingame, tics=tics, size=len(demo),
                trailing=len(body) - pos - 1)
    del end
    return info


def cmd_demos(wad):
    data, lumps = read_wad(wad)
    for n, pos, size in lumps:
        if n.startswith("DEMO"):
            info = demo_info(data[pos:pos + size])
            print(n, " ".join(f"{k}={v}" for k, v in info.items()))


def load_demo(wad, name):
    if name.lower().endswith(".lmp"):
        with open(name, "rb") as f:
            return f.read()
    data, lumps = read_wad(wad)
    return lump(data, lumps, name)


def cmd_demo_tics(wad, name):
    print(demo_info(load_demo(wad, name))["tics"])


def write_png(path, width, height, rgb_rows):
    def chunk(kind, payload):
        c = struct.pack(">I", len(payload)) + kind + payload
        return c + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    raw = b"".join(b"\0" + row for row in rgb_rows)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def cmd_png(wad, raw_path, out, palette=0):
    """palette: a PLAYPAL index, or the path of the frame's .scene sidecar (its `palette` line)."""
    if str(palette).endswith(".scene"):
        with open(palette) as f:
            palette = next(int(l.split()[1]) for l in f if l.startswith("palette "))
    data, lumps = read_wad(wad)
    playpal = lump(data, lumps, "PLAYPAL")
    pal = playpal[768 * int(palette):768 * (int(palette) + 1)]
    with open(raw_path, "rb") as f:
        frame = f.read()
    if len(frame) != 320 * 200:
        raise SystemExit(f"{raw_path}: expected 64000 bytes, got {len(frame)}")
    rows = []
    for y in range(200):
        row = bytearray()
        for x in range(320):
            i = frame[y * 320 + x] * 3
            row += pal[i:i + 3]
        rows.append(bytes(row))
    write_png(out, 320, 200, rows)


def read_trace(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or line.startswith(" "):
                continue
            yield dict(kv.split("=", 1) for kv in line.rstrip("\n").split(";"))


# Player field positions (0-based) in p<N>, see docs/contracts.md section 6.4.
P_DAMAGECOUNT = 40
P_WEAPON_STATE = 46
P_FLASH_STATE = 50
P_ATTACKDOWN = 54


def pick_tics(path, count=12):
    """Pick about `count` tics: tic 0, evenly spaced tics, and 'interesting' tics where the
    console player fires (muzzle flash), takes damage, or many objects move."""
    lines = list(read_trace(path))
    if not lines:
        return []
    last = int(lines[-1]["t"])
    chosen = {0}
    even = max(1, count - 5)
    for i in range(1, even):
        chosen.add(last * i // even)
    firing, hurt, busy = [], [], []
    prev_hash = None
    for d in lines:
        t = int(d["t"])
        if d.get("gs") != "0" or t < 2:
            continue
        p = next((d[k] for k in ("p0", "p1", "p2", "p3") if k in d), None)
        if p is None:
            continue
        f = p.split(",")
        if f[P_FLASH_STATE] != "-1" and f[P_ATTACKDOWN] == "1":
            firing.append(t)
        if int(f[P_DAMAGECOUNT]) > 0:
            hurt.append(t)
        if prev_hash is not None and d["a"] != prev_hash:
            busy.append(t)
        prev_hash = d["a"]
    for group in (firing, hurt):
        if group:
            chosen.add(group[len(group) // 3])
            chosen.add(group[(2 * len(group)) // 3])
    if busy:
        chosen.add(busy[len(busy) // 2])
    return sorted(t for t in chosen if t <= last)


def cmd_pick_tics(path, count=12):
    print(",".join(str(t) for t in pick_tics(path, int(count))))


def cmd_check_trace(path, expected):
    n = 0
    for d in read_trace(path):
        if int(d["t"]) != n:
            raise SystemExit(f"{path}: line {n} has t={d['t']}")
        n += 1
    status = "ok" if n == int(expected) else "MISMATCH"
    print(f"{path}: {n} tic lines, expected {expected}: {status}")
    if n != int(expected):
        sys.exit(1)


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    cmd, args = argv[1], argv[2:]
    {"demos": cmd_demos, "demo-tics": cmd_demo_tics, "png": cmd_png,
     "pick-tics": cmd_pick_tics, "check-trace": cmd_check_trace}[cmd](*args)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
