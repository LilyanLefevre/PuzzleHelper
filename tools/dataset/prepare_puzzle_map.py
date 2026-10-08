#!/usr/bin/env python3
"""
Builds the real-photo benchmark used by DatasetReplayTest from the public Puzzle-Map dataset
(https://huggingface.co/datasets/pablo-moreira/puzzle-map, CC-BY-4.0): the "120_avengers" puzzle,
484 hand-held photos of its 120 pieces with their row, column, quarter-turn and side types.

macOS only (uses `sips` to crop and convert to BMP, which Android unit tests can read without ImageIO).

    python3 tools/dataset/prepare_puzzle_map.py /tmp/puzzle-map
    PUZZLE_DATASET_DIR=/tmp/puzzle-map ./gradlew testDebugUnitTest --tests '*DatasetReplayTest' -i
"""
import json, os, subprocess, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = "https://huggingface.co/datasets/pablo-moreira/puzzle-map/resolve/main"
PUZZLE = "120_avengers.png"


def get(url, path):
    if not os.path.exists(path):
        urllib.request.urlretrieve(url, path)
    return path


def main(out):
    os.makedirs(out, exist_ok=True)
    raw = os.path.join(out, "raw"); os.makedirs(raw, exist_ok=True)
    ann = json.load(open(get(f"{BASE}/pieces/annotations.json", os.path.join(raw, "pieces.json"))))
    grid = json.load(open(get(f"{BASE}/puzzles/annotations.json", os.path.join(raw, "puzzles.json"))))[PUZZLE]
    rows = [(f, p) for f, v in ann.items() for p in v["pieces"]
            if (p.get("puzzle") or {}).get("name") == PUZZLE and p.get("valid")]
    with ThreadPoolExecutor(12) as ex:
        list(ex.map(lambda r: get(f"{BASE}/pieces/{r[0]}", os.path.join(raw, r[0])), rows))
    box = get(f"{BASE}/puzzles/{PUZZLE}", os.path.join(raw, PUZZLE))
    subprocess.run(["sips", "-Z", "2000", "-s", "format", "bmp", box, "--out", os.path.join(out, "box.bmp")], capture_output=True)
    open(os.path.join(out, "grid.txt"), "w").write(f"{grid['columns']} {grid['rows']}\n")

    lines = []
    for f, p in rows:
        x0, y0, x1, y1 = map(int, p["bbox"]); s = p["sides"]; pz = p["puzzle"]
        w, h = map(int, subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", os.path.join(raw, f)],
                                       capture_output=True, text=True).stdout.split()[-3::2])
        m = int(0.08 * max(x1 - x0, y1 - y0))
        X0, Y0, X1, Y1 = max(0, x0 - m), max(0, y0 - m), min(w, x1 + m), min(h, y1 + m)
        name = f.rsplit(".", 1)[0] + ".bmp"
        subprocess.run(["sips", "-s", "format", "bmp", "--cropToHeightWidth", str(Y1 - Y0), str(X1 - X0),
                        "--cropOffset", str(Y0), str(X0), os.path.join(raw, f), "--out", os.path.join(out, name)],
                       capture_output=True)
        lines.append("\t".join(map(str, [name, pz["column"] - 1, pz["row"] - 1, pz["angle"], p["rotation"],
                                         x0 - X0, y0 - Y0, x1 - X0, y1 - Y0, s["top"], s["right"], s["bottom"], s["left"]])))
    open(os.path.join(out, "pieces.tsv"), "w").write("\n".join(lines) + "\n")
    print(f"{len(lines)} pieces ready in {out}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/puzzle-map")
