#!/usr/bin/env python3
"""
Builds the real-photo benchmark used by DatasetReplayTest from the public Puzzle-Map dataset
(https://huggingface.co/datasets/pablo-moreira/puzzle-map, CC-BY-4.0): every puzzle whose piece photos carry a
row, column and quarter-turn (120_avengers, 100_dc, five 6_patos boxes). One folder per puzzle.

macOS only (uses `sips` to crop and convert to BMP, which Android unit tests can read without ImageIO).

    python3 tools/dataset/prepare_puzzle_map.py /tmp/puzzle-map
    PUZZLE_DATASET_DIR=/tmp/puzzle-map ./gradlew testDebugUnitTest --tests '*DatasetReplayTest' -i
"""
import json, os, subprocess, sys
from concurrent.futures import ThreadPoolExecutor

BASE = "https://huggingface.co/datasets/pablo-moreira/puzzle-map/resolve/main"


def get(url, path):
    if not os.path.exists(path):
        subprocess.run(["curl", "-sfL", "-o", path, url], check=True)   # curl: system certificates, unlike python.org builds
    return path


def size(path):
    out = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", path], capture_output=True, text=True).stdout
    return map(int, out.split()[-3::2])


def main(out):
    raw = os.path.join(out, "raw"); os.makedirs(raw, exist_ok=True)
    ann = json.load(open(get(f"{BASE}/pieces/annotations.json", os.path.join(raw, "pieces.json"))))
    grids = json.load(open(get(f"{BASE}/puzzles/annotations.json", os.path.join(raw, "puzzles.json"))))
    by_puzzle = {}
    for f, v in ann.items():
        for p in v["pieces"]:
            name = (p.get("puzzle") or {}).get("name")
            if name in grids and p.get("valid"):
                by_puzzle.setdefault(name, []).append((f, p))
    with ThreadPoolExecutor(12) as ex:
        list(ex.map(lambda r: get(f"{BASE}/pieces/{r[0]}", os.path.join(raw, r[0])), [r for rs in by_puzzle.values() for r in rs]))

    for puzzle, rows in sorted(by_puzzle.items()):
        dst = os.path.join(out, puzzle.rsplit(".", 1)[0]); os.makedirs(dst, exist_ok=True)
        box = get(f"{BASE}/puzzles/{puzzle}", os.path.join(raw, puzzle))
        subprocess.run(["sips", "-Z", "2000", "-s", "format", "bmp", box, "--out", os.path.join(dst, "box.bmp")], capture_output=True)
        g = grids[puzzle]
        open(os.path.join(dst, "grid.txt"), "w").write(f"{g['columns']} {g['rows']}\n")
        lines = []
        for f, p in rows:
            x0, y0, x1, y1 = map(int, p["bbox"]); s = p["sides"]; pz = p["puzzle"]
            w, h = size(os.path.join(raw, f))
            m = int(0.08 * max(x1 - x0, y1 - y0))
            X0, Y0, X1, Y1 = max(0, x0 - m), max(0, y0 - m), min(w, x1 + m), min(h, y1 + m)
            name = f.rsplit(".", 1)[0] + ".bmp"
            subprocess.run(["sips", "-s", "format", "bmp", "--cropToHeightWidth", str(Y1 - Y0), str(X1 - X0),
                            "--cropOffset", str(Y0), str(X0), os.path.join(raw, f), "--out", os.path.join(dst, name)],
                           capture_output=True)
            lines.append("\t".join(map(str, [name, pz["column"] - 1, pz["row"] - 1, pz["angle"], p["rotation"],
                                             x0 - X0, y0 - Y0, x1 - X0, y1 - Y0, s["top"], s["right"], s["bottom"], s["left"]])))
        open(os.path.join(dst, "pieces.tsv"), "w").write("\n".join(lines) + "\n")
        print(f"{puzzle}: {len(lines)} photos, grid {g['columns']}x{g['rows']}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/puzzle-map")
