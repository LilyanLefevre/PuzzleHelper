#!/usr/bin/env python3
"""
Off-device experiment: locate Puzzle-Map pieces by masked normalised cross-correlation on pixels (every rotation, a few
scales), no colour descriptor. Compares with the matcher's exact-cell rate (56 %).

    python tools/ai/ncc_experiment.py /tmp/pm3 [--every 3] [--cell 24]
"""
import argparse, os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "dataset"))
from locate_piece import masked_ncc


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("root"); ap.add_argument("--every", type=int, default=1)
    ap.add_argument("--cell", type=float, default=24); ap.add_argument("--step", type=int, default=15)
    a = ap.parse_args()
    tot = [0, 0, 0]
    for d in sorted(os.listdir(a.root)):
        p = os.path.join(a.root, d)
        if not os.path.exists(os.path.join(p, "pieces.tsv")): continue
        cols, rows = map(int, open(os.path.join(p, "grid.txt")).read().split())
        box = Image.open(os.path.join(p, "box.bmp")).convert("RGB")
        f = a.cell * cols / box.width
        bw, bh = round(box.width * f), round(box.height * f)
        small = np.asarray(box.resize((bw, bh), Image.LANCZOS)); cw, ch = bw / cols, bh / rows
        n = ok = near = 0
        for i, line in enumerate(l for l in open(os.path.join(p, "pieces.tsv")) if l.strip()):
            if i % a.every: continue
            t = line.rstrip("\n").split("\t")
            col, row = int(t[1]), int(t[2]); x0, y0, x1, y1 = map(int, t[5:9])
            ix, iy = (x1 - x0) * .16, (y1 - y0) * .16
            ph = Image.open(os.path.join(p, t[0])).convert("RGB").crop((int(x0 + ix), int(y0 + iy), int(x1 - ix), int(y1 - iy)))
            best = (-9, 0, 0)
            for k in (0.85, 1.0, 1.15):
                s = k * np.sqrt(cw * ch) / np.sqrt(ph.width * ph.height)
                sz = (max(8, int(ph.width * s)), max(8, int(ph.height * s)))
                base = ph.resize(sz, Image.BILINEAR)
                for deg in range(0, 360, a.step):
                    im = base.rotate(deg, expand=True, resample=Image.BILINEAR)
                    # disc inside the body: robust to the piece's tilt
                    yy, xx = np.mgrid[0:im.height, 0:im.width]
                    m = ((xx - im.width / 2) ** 2 + (yy - im.height / 2) ** 2) < (min(sz) / 2) ** 2
                    r = masked_ncc(small, np.asarray(im), m)
                    j = np.unravel_index(np.argmax(r), r.shape)
                    if r[j] > best[0]: best = (r[j], (j[1] + im.width / 2) / cw, (j[0] + im.height / 2) / ch)
            n += 1; ok += abs(best[1] - (col + .5)) <= .51 and abs(best[2] - (row + .5)) <= .51
            near += abs(best[1] - (col + .5)) <= 1.01 and abs(best[2] - (row + .5)) <= 1.01
        print(f"{d:14s} n={n:4d} exact {100*ok/n:5.1f}%  +-1 {100*near/n:5.1f}%", flush=True)
        tot[0] += n; tot[1] += ok; tot[2] += near
    print(f"ALL            n={tot[0]:4d} exact {100*tot[1]/tot[0]:5.1f}%  +-1 {100*tot[2]/tot[0]:5.1f}%")


if __name__ == "__main__":
    main()
