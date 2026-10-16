import struct, sys, os
# Generates the PWADs and demos of testdata/flow; the traces come from the oracle:
#   tools/oracle/run.sh freedoom1 exd<a|b|c>.lmp -file <exit|secret>.wad -trace <name>.trace.gz
# usage: make-flow-testdata.py <freedoom1.wad> <output dir>
S = sys.argv[2].rstrip('/') + '/'
iwad = open(sys.argv[1], 'rb').read()
n, ofs = struct.unpack_from('<ii', iwad, 4)
d = []
for i in range(n):
    p, sz, nm = struct.unpack_from('<ii8s', iwad, ofs + 16 * i)
    d.append((nm.rstrip(b'\0').decode(), p, sz))
idx = [i for i, e in enumerate(d) if e[0] == 'E1M1'][0]
lumps = {d[idx + k][0]: iwad[d[idx + k][1]:d[idx + k][1] + d[idx + k][2]] for k in range(1, 11)}
order = ['THINGS', 'LINEDEFS', 'SIDEDEFS', 'VERTEXES', 'SEGS', 'SSECTORS', 'NODES', 'SECTORS', 'REJECT', 'BLOCKMAP']
verts = [struct.unpack_from('<hh', lumps['VERTEXES'], 4 * i) for i in range(len(lumps['VERTEXES']) // 4)]
lines = [list(struct.unpack_from('<hhhhhhh', lumps['LINEDEFS'], 14 * i)) for i in range(len(lumps['LINEDEFS']) // 14)]
import math
things = [struct.unpack_from('<hhhhh', lumps['THINGS'], 10 * i) for i in range(len(lumps['THINGS']) // 10)]
st = [t for t in things if t[3] == 1][0]
sx, sy, ang = st[0], st[1], st[2]
dx, dy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
print('start', sx, sy, ang, file=sys.stderr)
best = None
for i, l in enumerate(lines):
    if not (l[2] & 4):
        continue
    (x1, y1), (x2, y2) = verts[l[0]], verts[l[1]]
    ex, ey = x2 - x1, y2 - y1
    den = dx * ey - dy * ex
    if abs(den) < 1e-9:
        continue
    t = ((x1 - sx) * ey - (y1 - sy) * ex) / den
    u = ((x1 - sx) * dy - (y1 - sy) * dx) / den
    if t > 40 and 0 <= u <= 1 and (best is None or t < best[1]):
        best = (i, t)
print('line', best, file=sys.stderr)
li = best[0]


def pwad(special, name):
    ls = [l[:] for l in lines]
    ls[li][3] = special
    lump = b''.join(struct.pack('<hhhhhhh', *l) for l in ls)
    parts = [('E1M1', b'')] + [(k, lumps[k] if k != 'LINEDEFS' else lump) for k in order]
    data = b''
    ents = []
    pos = 12
    for nm, b in parts:
        ents.append((pos, len(b), nm))
        data += b
        pos += len(b)
    dirb = b''.join(struct.pack('<ii8s', p, s, nm.encode()) for p, s, nm in ents)
    open(S + name, 'wb').write(b'PWAD' + struct.pack('<ii', len(parts), pos) + data + dirb)


pwad(52, 'exit.wad')
pwad(124, 'secret.wad')


def demo(name, players, tics, press):
    h = bytes([109, 2, 1, 1, 0, 0, 0, 1, 0] + [1 if i < players else 0 for i in range(4)])
    b = b''
    for t in range(tics):
        fwd = 50 if t < 140 else 0
        btn = 2 if press(t) else 0
        for p in range(players):
            b += struct.pack('<bbBB', fwd, 0, 0, btn)
    open(S + name, 'wb').write(h + b + bytes([0x80]))


demo('exda.lmp', 1, 1000, lambda t: t >= 140 and t % 2 == 0)
demo('exdb.lmp', 1, 1000, lambda t: t in (400, 600, 700, 800, 900))
demo('exdc.lmp', 2, 1000, lambda t: t >= 140 and t % 3 == 0)
