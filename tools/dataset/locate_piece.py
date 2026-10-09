#!/usr/bin/env python3
"""
Finds where a photographed piece sits on the box image by brute force (masked normalised cross-correlation over
position x rotation x scale on the pixels). Slow, desktop only: it gives the ground truth of real captures, the app
never uses it.

    python tools/dataset/locate_piece.py box.jpg grid_cols grid_rows capture.jpg [capture2.jpg ...]

Prints the 3 best peaks per capture: col/row in grid units (cell centre = col+.5), clockwise turn to put the piece back, score.
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage
from scipy.signal import fftconvolve

BOX_W = 700
SCALES = (0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.15)


def crop_photo(path, ratio=0.5, side=420):
    im = Image.open(path).convert("RGB")
    s = int(min(im.size) * ratio)
    x0, y0 = (im.width - s) // 2, (im.height - s) // 2
    return im.crop((x0, y0, x0 + s, y0 + s)).resize((side, side), Image.BILINEAR)


def segment(im):
    a = np.asarray(im, np.float32)
    b = max(4, int(a.shape[0] * .05))
    ring = np.concatenate([a[:b].reshape(-1, 3), a[-b:].reshape(-1, 3), a[:, :b].reshape(-1, 3), a[:, -b:].reshape(-1, 3)])
    d = np.linalg.norm(a - np.median(ring, 0), axis=2)
    d = ndimage.gaussian_filter(d, 1.5)
    hist, edges = np.histogram(d, 64)
    p = hist / hist.sum(); w = np.cumsum(p); m = np.cumsum(p * edges[:-1]); mt = m[-1]
    t = edges[np.argmax((mt * w - m) ** 2 / (w * (1 - w) + 1e-9))]
    mask = ndimage.binary_opening(d > t, iterations=2)
    lab, n = ndimage.label(mask)
    c = lab[a.shape[0] // 2, a.shape[1] // 2] or (np.argmax(ndimage.sum(mask, lab, range(1, n + 1))) + 1)
    return ndimage.binary_fill_holes(lab == c)


def masked_ncc(img, tpl, mask):
    """Zero-normalised cross-correlation of tpl (h,w,3) under mask (h,w) at every position of img (H,W,3), mean over channels."""
    n = mask.sum(); m = mask.astype(np.float64)
    out = 0
    for ch in range(3):
        I = img[..., ch].astype(np.float64); T = tpl[..., ch].astype(np.float64)
        Tm = (T - (T * m).sum() / n) * m
        sT = np.sqrt((Tm ** 2).sum())
        s1 = fftconvolve(I, m[::-1, ::-1], "valid"); s2 = fftconvolve(I * I, m[::-1, ::-1], "valid")
        cross = fftconvolve(I, Tm[::-1, ::-1], "valid")
        var = np.maximum(s2 - s1 * s1 / n, 1e-6)
        out = out + cross / (sT * np.sqrt(var))
    return out / 3


def main():
    box = Image.open(sys.argv[1]).convert("RGB"); cols, rows = int(sys.argv[2]), int(sys.argv[3])
    bw = BOX_W; bh = round(box.height * bw / box.width)
    small = np.asarray(box.resize((bw, bh), Image.LANCZOS))
    cellw = bw / cols
    for path in sys.argv[4:]:
        ph = crop_photo(path); mask = segment(ph)
        ys, xs = np.nonzero(mask)
        ph = ph.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)); mask = mask[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
        area = mask.sum(); peaks = []
        for k in SCALES:
            f = k * cellw * 1.07 / np.sqrt(area)          # box px per photo px (piece area ~ 1.07^2 cells)
            sz = (max(8, int(ph.width * f)), max(8, int(ph.height * f)))
            for deg in range(0, 360, 15):
                t = ph.resize(sz, Image.BILINEAR).rotate(-deg, expand=True, resample=Image.BILINEAR)
                m = Image.fromarray(mask.astype(np.uint8) * 255).resize(sz, Image.BILINEAR).rotate(-deg, expand=True) 
                m = np.asarray(m) > 128
                if m.sum() < 30 or t.width >= bw or t.height >= bh:
                    continue
                r = masked_ncc(small, np.asarray(t), m)
                i = np.unravel_index(np.argmax(r), r.shape)
                cx, cy = i[1] + t.width / 2, i[0] + t.height / 2
                peaks.append((r[i], cx / cellw, cy / (bh / rows), deg, k))
        peaks.sort(reverse=True)
        print(path.split("/")[-1], "mask area", area)
        shown = []
        for s, c, r, deg, k in peaks:
            if all(abs(c - c2) > 1 or abs(r - r2) > 1 for _, c2, r2, _, _ in shown):
                shown.append((s, c, r, deg, k)); print(f"   score {s:.3f}  col {c:.1f} row {r:.1f}  turn {deg}  scale x{k}")
            if len(shown) == 3: break


if __name__ == "__main__":
    main()
