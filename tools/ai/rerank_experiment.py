#!/usr/bin/env python3
"""
Off-device experiment: re-rank the matcher's top-30 leads with a pretrained image embedding.

Input: the PUZZLE_DUMP_DIR export of DatasetReplayTest / ImageBankBenchmarkTest (leads.tsv + piece BMPs), and the box
images it points to. For every lead, the piece is turned the way the matcher says, its body is cropped and compared
(cosine similarity) with the box at that lead. The final score mixes the AI similarity with the matcher's colour
distance (both z-scored over the 30 leads): score = sim - lam * dist. lam = inf is the current matcher.

    python tools/ai/rerank_experiment.py C:/dev/data/dump --remap /tmp=C:/dev/data
"""
import argparse, math, os, time
from collections import defaultdict

import numpy as np
import torch
import torchvision
from PIL import Image

try:
    import torch_directml
    DEVICE = torch_directml.device()
except ImportError:
    DEVICE = torch.device("cpu")

MEAN = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
STD = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)


def model(name):
    if name == "mobilenet_v3":
        m = torchvision.models.mobilenet_v3_large(weights="DEFAULT"); m.classifier = torch.nn.Identity()
    elif name == "resnet50":
        m = torchvision.models.resnet50(weights="DEFAULT"); m.fc = torch.nn.Identity()
    elif name == "dinov2_s":
        m = torch.hub.load("facebookresearch/dinov2", "dinov2_vits14", verbose=False)
    else:
        raise ValueError(name)
    m.eval()
    try:
        return m.to(DEVICE), DEVICE
    except Exception:
        return m, torch.device("cpu")


def tensor(img, size=224):
    img = img.convert("RGB").resize((size, size), Image.BILINEAR)
    t = torch.from_numpy(np.asarray(img, dtype=np.float32) / 255.0).permute(2, 0, 1)
    return (t - MEAN) / STD


@torch.no_grad()
def embed(m, dev, imgs, bs=64):
    out = []
    for i in range(0, len(imgs), bs):
        x = torch.stack([tensor(im) for im in imgs[i:i + bs]]).to(dev)
        f = m(x).float().cpu()
        out.append(torch.nn.functional.normalize(f, dim=1))
    return torch.cat(out) if out else torch.empty(0)


def body(piece, rot, masked):
    """Piece turned clockwise by rot, then its body (central square, scale from the mask area)."""
    p = piece.rotate(-rot, resample=Image.BILINEAR, expand=True)
    if not masked:
        return p
    a = np.asarray(p.convert("L")) > 8
    ys, xs = np.nonzero(a)
    if len(xs) < 50:
        return p
    side = math.sqrt(a.sum()) * 0.9
    cx, cy = xs.mean(), ys.mean()
    return p.crop((int(cx - side / 2), int(cy - side / 2), int(cx + side / 2), int(cy + side / 2)))


def patch(box, cols, rows, col, row):
    cw, ch = box.width / cols, box.height / rows
    side = math.sqrt(cw * ch) * 0.9
    x, y = col * cw, row * ch
    return box.crop((int(x - side / 2), int(y - side / 2), int(x + side / 2), int(y + side / 2)))


def z(v):
    v = np.asarray(v, dtype=np.float64)
    return (v - v.mean()) / (v.std() + 1e-9)


def load(dump, remap):
    rows = []
    for suite in sorted(os.listdir(dump)):
        f = os.path.join(dump, suite, "leads.tsv")
        if not os.path.exists(f):
            continue
        for line in open(f, encoding="utf-8"):
            s, box, cols, rows_, piece, tc, tr, trot, kind, leads = line.rstrip("\n").split("\t")
            for a, b in remap:
                box = box.replace(a, b, 1)
            L = [tuple(float(x) for x in l.split(":")) for l in leads.split("|")]
            rows.append(dict(suite=suite, box=box, cols=int(cols), rows=int(rows_), piece=os.path.join(dump, suite, piece),
                             tc=int(tc), tr=int(tr), kind=kind, leads=L))
    return rows


def evaluate(rows, sims, lams):
    res = defaultdict(lambda: defaultdict(lambda: [0, 0, 0, 0]))   # suite -> lam -> [n, exact, top4, upper]
    for r, sim in zip(rows, sims):
        L = r["leads"]; dist = [l[3] for l in L]
        def near(l, tol): return abs(l[0] - (r["tc"] + .5)) <= tol and abs(l[1] - (r["tr"] + .5)) <= tol
        upper = any(near(l, 1.01) for l in L)
        for lam in lams:
            score = -z(dist) if lam == math.inf else z(sim) - lam * z(dist)
            order = np.argsort(-score)
            ranked = [L[i] for i in order]
            for key in (r["suite"], "ALL"):
                acc = res[key][lam]
                acc[0] += 1; acc[1] += near(ranked[0], 0.51); acc[2] += any(near(l, 1.01) for l in ranked[:4]); acc[3] += upper
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dump"); ap.add_argument("--remap", action="append", default=[])
    ap.add_argument("--models", default="mobilenet_v3,resnet50,dinov2_s")
    a = ap.parse_args()
    remap = [tuple(x.split("=", 1)) for x in a.remap]
    rows = load(a.dump, remap)
    print(f"{len(rows)} pieces, device {DEVICE}")
    boxes = {}
    lams = [0.0, 0.25, 0.5, 1.0, 2.0, math.inf]
    for name in a.models.split(","):
        t0 = time.time()
        m, dev = model(name)
        sims = []
        for r in rows:
            if r["box"] not in boxes:
                boxes[r["box"]] = Image.open(r["box"]).convert("RGB")
            box = boxes[r["box"]]
            piece = Image.open(r["piece"]).convert("RGB")
            masked = r["suite"] == "bank"
            rots = sorted({int(l[2]) for l in r["leads"]})
            pe = dict(zip(rots, embed(m, dev, [body(piece, rot, masked) for rot in rots])))
            be = embed(m, dev, [patch(box, r["cols"], r["rows"], l[0], l[1]) for l in r["leads"]])
            sims.append([float(pe[int(l[2])] @ be[i]) for i, l in enumerate(r["leads"])])
        res = evaluate(rows, sims, lams)
        print(f"\n== {name}  ({time.time() - t0:.0f} s)")
        for suite, by in res.items():
            for lam in lams:
                n, ex, t4, up = by[lam]
                tag = "current matcher" if lam == math.inf else ("AI only" if lam == 0 else f"AI + {lam} x colour")
                print(f"{suite:8s} {tag:18s} exact {100*ex/n:5.1f}%  top4 {100*t4/n:5.1f}%  (truth in top30: {100*up/n:5.1f}%)")


if __name__ == "__main__":
    main()
