"""Finds a spot on the screen from a uiautomator dump: prints "x y" (or nothing)."""
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
mode = sys.argv[2]
nodes = []
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t, a, b, c, d = m.group(1), *map(int, m.groups()[1:])
    nodes.append((t, (a + c) // 2, (b + d) // 2))
if mode.startswith("text:"):
    want = mode[5:]
    for t, x, y in nodes:
        if want in t:
            print(x, y); break
elif mode == "chip-right":
    # A task chip in the periods column (right side in RTL), not a period title or time.
    for t, x, y in nodes:
        if x > 600 and 450 < y < 1900 and len(t) > 3 and not re.search(r"[0-9]|الفجر|الظهر|العصر|المغرب|العشاء", t):
            print(x, y, t, file=sys.stderr); print(x, y); break
elif mode == "after:" or mode.startswith("after:"):
    # The first line after a heading (an item of a list).
    want = mode[6:]; seen = False
    for t, x, y in nodes:
        if seen and t.strip():
            print(x, y); break
        if want in t: seen = True
elif mode.startswith("desc:"):
    # By accessibility label (Compose contentDescription), e.g. the done tick of a task.
    want = mode[5:]
    for m in re.finditer(r'<node [^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        d, a, b, c, e = m.group(1), *map(int, m.groups()[1:])
        if want in d and 150 < (b + e) // 2 < 2250:
            print((a + c) // 2, (b + e) // 2); break
