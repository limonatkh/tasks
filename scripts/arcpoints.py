# Finds the arc on the Today header: «نور» (orange body), one station dot (the pink maghrib), and a point
# on the plain line between. Prints "NX NY SX SY LX LY".
import sys, zlib, struct
def png(path):
    d = open(path, 'rb').read(); i = 8; w = h = 0; idat = b''
    while i < len(d):
        n = struct.unpack('>I', d[i:i+4])[0]; t = d[i+4:i+8]; c = d[i+8:i+8+n]
        if t == b'IHDR': w, h, bd, ct = struct.unpack('>IIBB', c[:10])
        if t == b'IDAT': idat += c
        i += 12 + n
    raw = zlib.decompress(idat); bpp = 4 if ct == 6 else 3; st = w * bpp; rows = []; prev = bytearray(st); p = 0
    for y in range(h):
        f = raw[p]; p += 1; line = bytearray(raw[p:p+st]); p += st
        for x in range(st):
            a = line[x-bpp] if x >= bpp else 0; b = prev[x]; c = prev[x-bpp] if x >= bpp else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line); prev = line
    return w, h, bpp, rows
w, h, bpp, rows = png(sys.argv[1])
def px(x, y): r = rows[y]; return r[x*bpp], r[x*bpp+1], r[x*bpp+2]
def find(test, y0, y1):
    pts = [(x, y) for y in range(y0, y1, 2) for x in range(0, w, 2) if test(*px(x, y))]
    if not pts: return None
    return sum(p[0] for p in pts) // len(pts), sum(p[1] for p in pts) // len(pts)
top, bot = int(h * 0.11), int(h * 0.26)
nour = find(lambda r, g, b: r > 230 and 120 < g < 160 and b < 90, top, bot)            # lantern orange #FF8A3D
pink = find(lambda r, g, b: 180 < r < 210 and 70 < g < 100 and 105 < b < 135, top, bot)  # maghrib #C0587A
if not nour: nour = (w // 2, int(h * 0.17))
if not pink: pink = (int(w * 0.35), int(h * 0.16))
line = ((nour[0] + pink[0]) // 2, (nour[1] + pink[1]) // 2 - 6)
print(nour[0], nour[1], pink[0], pink[1], line[0], line[1])
