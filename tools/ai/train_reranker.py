#!/usr/bin/env python3
"""
Trains a small contrastive model to re-rank the matcher's leads: one shared encoder (MobileNetV3-small) maps the
piece body and the box square under a lead to unit vectors; matching pairs must be closer than the other cells of
the same image (hard negatives) and of the other images in the batch (InfoNCE).

Pairs are drawn on the fly from free images (fetch_training_images.py): the piece is cut with the classic outline
of PiecePhotos, lit, white-balanced, blurred and grained like a phone photo, then cropped the way the benchmark
dumps crop it (masked piece: centred square from the mask area; real photo: annotated box minus the knobs).

    python tools/ai/train_reranker.py C:/dev/data/train-images C:/dev/data/reranker.pt --steps 20000
    python tools/ai/train_reranker.py C:/dev/data/train-images C:/dev/data/reranker.pt --export reranker.onnx
"""
import argparse, io, math, os, random, time

import numpy as np
import torch
import torchvision
from PIL import Image, ImageFilter

SIZE = 96
WIDE = os.environ.get("PUZZLE_WIDE") == "1"   # set by --wide (env: DataLoader workers re-import this module)  # --wide: camera variations measured on the owner's real photos (up to x2 per channel, saturation)
MEAN = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
STD = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
TABLES = [(120, 86, 60), (190, 160, 120), (60, 40, 30), (235, 235, 230), (128, 128, 128), (40, 90, 50), (50, 50, 55)]


def gpu():
    """The discrete Radeon through DirectML (not the CPU's integrated GPU, which drives the screen), else the CPU."""
    try:
        import torch_directml
    except ImportError:
        return torch.device("cpu")
    names = [torch_directml.device_name(i) for i in range(torch_directml.device_count())]
    return torch_directml.device(next((i for i, n in enumerate(names) if "RX" in n), torch_directml.default_device()))


def encoder(arch="mnv3s", dim=128):
    """Pretrained ImageNet backbone, classifier replaced by a linear embedding head."""
    if arch == "mnv3s":
        m = torchvision.models.mobilenet_v3_small(weights="DEFAULT"); m.classifier = torch.nn.Linear(576, dim)
    elif arch == "mnv3l":
        m = torchvision.models.mobilenet_v3_large(weights="DEFAULT"); m.classifier = torch.nn.Linear(960, dim)
    elif arch == "effb0":
        m = torchvision.models.efficientnet_b0(weights="DEFAULT"); m.classifier = torch.nn.Linear(1280, dim)
    elif arch == "resnet18":
        m = torchvision.models.resnet18(weights="DEFAULT"); m.fc = torch.nn.Linear(512, dim)
    else:
        raise ValueError(arch)
    return m


def tensor(img, size=SIZE):
    img = img.convert("RGB").resize((size, size), Image.BILINEAR)
    t = torch.from_numpy(np.asarray(img, dtype=np.float32) / 255.0).permute(2, 0, 1)
    return (t - MEAN) / STD


def piece_mask(h, w, cw, ch, sides):
    """PiecePhotos.inPiece on a pixel grid centred on the cell (sides: top, right, bottom, left; 0 flat, 1 tab, -1 blank)."""
    v, u = np.mgrid[0:h, 0:w].astype(np.float32)
    u = (u - w / 2) / cw; v = (v - h / 2) / ch
    m = (np.abs(u) <= .5) & (np.abs(v) <= .5)
    r, off, b = .17, .62, .38
    for (kx, ky), s in zip(((0, -1), (1, 0), (0, 1), (-1, 0)), sides):
        if s == 1:
            m |= np.hypot(u - kx * off, v - ky * off) < r
        elif s == -1:
            m &= ~(np.hypot(u - kx * b, v - ky * b) < r)
    return m


def box_patch(box, cw, ch, cx, cy):
    """Same square as rerank_experiment.patch: 0.9 x the cell, centred on it."""
    side = math.sqrt(cw * ch) * 0.9
    return box.crop((int(cx - side / 2), int(cy - side / 2), int(cx + side / 2), int(cy + side / 2)))


def photo_body(rnd, arr, cw, ch, cx, cy, col, row, cols, rows):
    """Fake phone photo of the piece at (cx,cy), cropped like the dumps crop it."""
    side = lambda edge: 0 if edge else rnd.choice((1, -1))
    sides = (side(row == 0), side(col == cols - 1), side(row == rows - 1), side(col == 0))
    hw, hh = int(cw * 0.9), int(ch * 0.9)
    x0, y0 = int(cx) - hw, int(cy) - hh
    H, W = arr.shape[:2]
    ys = np.clip(np.arange(y0, y0 + 2 * hh), 0, H - 1); xs = np.clip(np.arange(x0, x0 + 2 * hw), 0, W - 1)
    src = arr[ys][:, xs].astype(np.float32)
    m = piece_mask(2 * hh, 2 * hw, cw, ch, sides)
    masked = rnd.random() < 0.5                       # bank dump (black outside) or real photo (table around)
    table = np.zeros(3, np.float32) if masked else np.array(rnd.choice(TABLES), np.float32) * rnd.uniform(.8, 1.2)
    # Camera: white balance, exposure, side light, gamma; then the table where there is no piece.
    if WIDE:
        gains = np.array([rnd.uniform(.6, 1.5) for _ in range(3)], np.float32) * rnd.uniform(.6, 1.8)
    else:
        gains = np.array([rnd.uniform(.82, 1.18) for _ in range(3)], np.float32) * rnd.uniform(.75, 1.2)
    a = rnd.uniform(0, 2 * math.pi); g = rnd.uniform(0, .6)
    v, u = np.mgrid[0:2 * hh, 0:2 * hw].astype(np.float32)
    light = 1 + g * (math.cos(a) * (u / (2 * hw) - .5) + math.sin(a) * (v / (2 * hh) - .5))
    img = np.clip(255 * ((src * gains * light[..., None]) / 255).clip(0, 1) ** rnd.uniform(.8, 1.25), 0, 255)
    if WIDE:                                           # saturation and contrast of the phone's processing
        grey = img.mean(2, keepdims=True)
        img = np.clip(grey + (img - grey) * rnd.uniform(.5, 1.5), 0, 255)
        img = np.clip((img - 128) * rnd.uniform(.7, 1.4) + 128, 0, 255)
    img = np.where(m[..., None], img, table)
    # Rotation residue of the outline reading, resampled twice like the real pipeline.
    deg = rnd.uniform(-7, 7)
    pim = Image.fromarray(img.astype(np.uint8)).rotate(deg, resample=Image.BILINEAR, fillcolor=tuple(int(t) for t in table))
    mim = Image.fromarray(m.astype(np.uint8) * 255).rotate(deg)
    if masked:
        mm = np.asarray(mim) > 0
        yy, xx = np.nonzero(mm)
        s = math.sqrt(mm.sum()) * 0.9
        ccx, ccy = xx.mean(), yy.mean()
    else:
        yy, xx = np.nonzero(np.asarray(mim) > 0)
        bx0, bx1, by0, by1 = xx.min(), xx.max(), yy.min(), yy.max()
        ix, iy = (bx1 - bx0) * .16, (by1 - by0) * .16
        ccx, ccy = (bx0 + bx1) / 2 + rnd.uniform(-.05, .05) * cw, (by0 + by1) / 2 + rnd.uniform(-.05, .05) * ch
        s = max(bx1 - bx0 - 2 * ix, by1 - by0 - 2 * iy)
    crop = pim.crop((int(ccx - s / 2), int(ccy - s / 2), int(ccx + s / 2), int(ccy + s / 2)))
    # Phone optics: lens blur, scale, sensor grain, JPEG.
    crop = crop.resize((int(rnd.uniform(40, 160)),) * 2, Image.BILINEAR).filter(ImageFilter.GaussianBlur(rnd.uniform(0, 1.2)))
    n = np.asarray(crop, np.float32) + np.random.default_rng(rnd.getrandbits(32)).normal(0, rnd.uniform(1, 6), (crop.height, crop.width, 1))
    crop = Image.fromarray(np.clip(n, 0, 255).astype(np.uint8))
    buf = io.BytesIO(); crop.save(buf, "JPEG", quality=rnd.randint(55, 95))
    return Image.open(buf)


class Pairs(torch.utils.data.IterableDataset):
    """Endless batches: per image, a window of neighbouring cells (they look alike, like the top leads do)."""

    def __init__(self, files, images_per_batch, cells_per_image, seed):
        self.files, self.ipb, self.cpi, self.seed = files, images_per_batch, cells_per_image, seed

    def __iter__(self):
        info = torch.utils.data.get_worker_info()
        rnd = random.Random(self.seed + (info.id if info else 0))
        while True:
            P, B = [], []
            for f in rnd.sample(self.files, self.ipb):
                box = Image.open(f).convert("RGB")
                box.thumbnail((1600, 1600))
                arr = np.asarray(box)
                cell = rnd.uniform(28, 80)                       # 300 to 2000 pieces
                cols, rows = max(4, round(box.width / cell)), max(4, round(box.height / cell))
                cw, ch = box.width / cols, box.height / rows
                win = rnd.randint(4, 8)
                c0, r0 = rnd.randint(0, max(0, cols - win)), rnd.randint(0, max(0, rows - win))
                cells = rnd.sample([(c, r) for c in range(c0, min(cols, c0 + win)) for r in range(r0, min(rows, r0 + win))], self.cpi)
                for c, r in cells:
                    cx, cy = (c + .5) * cw, (r + .5) * ch
                    P.append(tensor(photo_body(rnd, arr, cw, ch, cx, cy, c, r, cols, rows)))
                    B.append(tensor(box_patch(box, cw, ch, cx, cy)))
            yield torch.stack(P), torch.stack(B)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("images"); ap.add_argument("out")
    ap.add_argument("--steps", type=int, default=20000); ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--images-per-batch", type=int, default=16); ap.add_argument("--cells", type=int, default=16)
    ap.add_argument("--workers", type=int, default=10); ap.add_argument("--samples", help="write a few pairs here and stop")
    ap.add_argument("--export", help="write the trained model (out) as ONNX here and stop")
    ap.add_argument("--wide", action="store_true", help="wider camera variations (white balance, exposure, saturation, contrast)")
    ap.add_argument("--exclude", help="comma-separated image name prefixes kept out of training (held-out boxes)")
    ap.add_argument("--arch", default="mnv3s", help="mnv3s, mnv3l, effb0, resnet18")
    ap.add_argument("--max-step-s", type=float, default=0.5,
                    help="stop if steps 5-20 average longer: long GPU commands trip the Windows watchdog (TDR) and froze the PC")
    a = ap.parse_args()
    global WIDE
    if a.wide:
        os.environ["PUZZLE_WIDE"] = "1"; WIDE = True
    if a.export:
        m = encoder(a.arch); m.load_state_dict(torch.load(a.out, map_location="cpu")); m.eval()
        torch.onnx.export(m, torch.zeros(1, 3, SIZE, SIZE), a.export, input_names=["image"], output_names=["embedding"],
                          dynamic_axes={"image": {0: "n"}, "embedding": {0: "n"}}, opset_version=17)
        print(a.export, os.path.getsize(a.export) // 1024, "KB"); return
    skip = tuple(a.exclude.split(",")) if a.exclude else ()
    files = sorted(os.path.join(a.images, f) for f in os.listdir(a.images) if f.endswith(".jpg") and not f.startswith(skip))
    if a.samples:
        os.makedirs(a.samples, exist_ok=True)
        p, b = next(iter(Pairs(files, 2, 8, 0)))
        unnorm = lambda t: Image.fromarray(((t * STD + MEAN).clamp(0, 1) * 255).byte().permute(1, 2, 0).numpy())
        sheet = Image.new("RGB", (SIZE * 16, SIZE * 2))
        for i in range(16):
            sheet.paste(unnorm(p[i]), (i * SIZE, 0)); sheet.paste(unnorm(b[i]), (i * SIZE, SIZE))
        sheet.save(os.path.join(a.samples, "pairs.png")); return
    dev = gpu()
    model = encoder(a.arch).to(dev)
    scale = torch.nn.Parameter(torch.tensor(math.log(1 / 0.07), device=dev))
    opt = torch.optim.AdamW(list(model.parameters()) + [scale], lr=a.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, a.lr, total_steps=a.steps, pct_start=0.05)
    loader = torch.utils.data.DataLoader(Pairs(files, a.images_per_batch, a.cells, 1), batch_size=None,
                                         num_workers=a.workers, persistent_workers=True, prefetch_factor=4)
    t0, avg = time.time(), None
    for step, (p, b) in enumerate(loader):
        if step == a.steps:
            break
        model.train()
        f = torch.nn.functional.normalize(model(torch.cat([p, b]).to(dev)), dim=1)
        fp, fb = f[:len(p)], f[len(p):]
        logits = fp @ fb.T * scale.exp().clamp(max=100)
        target = torch.arange(len(p), device=dev)
        loss = (torch.nn.functional.cross_entropy(logits, target) + torch.nn.functional.cross_entropy(logits.T, target)) / 2
        opt.zero_grad(); loss.backward(); opt.step(); sched.step()
        avg = float(loss) if avg is None else 0.98 * avg + 0.02 * float(loss)   # float() waits for the GPU
        if step == 5:
            t5 = time.time()
        if step == 20 and (time.time() - t5) / 15 > a.max_step_s:
            raise SystemExit(f"{(time.time() - t5) / 15:.2f} s per step > {a.max_step_s}: use smaller batches")
        if step % 200 == 0 or step == a.steps - 1:
            print(f"step {step:6d}  loss {avg:.3f}  T {1 / scale.exp().item():.3f}  {time.time() - t0:.0f} s", flush=True)
            torch.save(model.state_dict(), a.out)
    torch.save(model.state_dict(), a.out)


if __name__ == "__main__":
    main()
