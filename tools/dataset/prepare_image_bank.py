#!/usr/bin/env python3
"""
Builds the image bank used by ImageBankBenchmarkTest: 8 public-domain paintings (Wikimedia Commons) and 16 photos
(Unsplash License, via picsum.photos), converted to BMP. The benchmark cuts each one into a 500-piece puzzle and
fakes phone photos of its pieces.

macOS only (uses `sips`).

    python3 tools/dataset/prepare_image_bank.py /tmp/image-bank
    PUZZLE_IMAGES_DIR=/tmp/image-bank ./gradlew testDebugUnitTest --tests '*ImageBankBenchmarkTest' -i
"""
import os, re, subprocess, sys

UA = "PuzzleItBenchmark/1.0 (https://github.com/LilyanLefevre/PuzzleHelper)"

# Public domain, Wikimedia Commons file names.
PAINTINGS = [
    "Tsunami_by_hokusai_19th_century.jpg",
    "Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg",
    "Mona_Lisa,_by_Leonardo_da_Vinci,_from_C2RMF_retouched.jpg",
    "The_Kiss_-_Gustav_Klimt_-_Google_Cultural_Institute.jpg",
    "Claude_Monet,_Impression,_soleil_levant.jpg",
    "Pieter_Bruegel_the_Elder_-_The_Tower_of_Babel_(Vienna)_-_Google_Art_Project_-_edited.jpg",
    "Claude_Monet_-_Water_Lilies_-_1906,_Ryerson.jpg",
    "Georges_Seurat_-_A_Sunday_on_La_Grande_Jatte_--_1884_-_Google_Art_Project.jpg",
]

# Unsplash License: (picsum id, author, original page).
PHOTOS = [
    ("164", "Linh Nguyen", "https://unsplash.com/photos/agkblvPff5U"),
    ("178", "Thanun Buranapong", "https://unsplash.com/photos/JbeBraLha7U"),
    ("166", "Romain Briaux", "https://unsplash.com/photos/yD3PXDV7Sjc"),
    ("172", "Aleksi Tappura", "https://unsplash.com/photos/TQeX8khR54I"),
    ("182", "Andrea Boldizsar", "https://unsplash.com/photos/BwgKUh9tN84"),
    ("127", "Marcin Czerwinski", "https://unsplash.com/photos/rf-0DQu5M6Y"),
    ("126", "Zugr", "https://unsplash.com/photos/asrWX-lU3RE"),
    ("167", "petradr", "https://unsplash.com/photos/WqK_xV_hbug"),
    ("187", "Andre Koch", "https://unsplash.com/photos/oSf8ePoG9NU"),
    ("185", "Tim de Groot", "https://unsplash.com/photos/M_eB1UjE0do"),
    ("115", "Christian Hebell", "https://unsplash.com/photos/A6S-q3D67Ss"),
    ("142", "Vadim Sherbakov", "https://unsplash.com/photos/KSyemQIWwP8"),
    ("121", "Radio Pink", "https://unsplash.com/photos/p-bkdO43shE"),
    ("114", "Brian Gonzalez", "https://unsplash.com/photos/llYg8Ni43fc"),
    ("175", "petradr", "https://unsplash.com/photos/8hgm6mKK04U"),
    ("196", "Dyaa Eldin Moustafa", "https://unsplash.com/photos/mR_HR8NZwg8"),
]


def fetch(url, path):
    if not os.path.exists(path):
        subprocess.run(["curl", "-sfL", "-A", UA, "-o", path, url], check=True)


def to_bmp(src, out, name):
    subprocess.run(["sips", "-Z", "1600", "-s", "format", "bmp", src, "--out", os.path.join(out, name + ".bmp")], capture_output=True)


def main(out):
    raw = os.path.join(out, "raw"); os.makedirs(raw, exist_ok=True)
    for f in PAINTINGS:
        src = os.path.join(raw, "w_" + f)
        fetch(f"https://commons.wikimedia.org/wiki/Special:FilePath/{f}?width=1600", src)
        to_bmp(src, out, re.sub(r"[^A-Za-z0-9_]", "_", ("w_" + f.rsplit(".", 1)[0])[:40]))   # the name seeds the benchmark draws
    for pid, _, _ in PHOTOS:
        src = os.path.join(raw, f"p_{pid}.jpg")
        fetch(f"https://picsum.photos/id/{pid}/1600/1067", src)
        to_bmp(src, out, f"p_{pid}")
    with open(os.path.join(out, "CREDITS.txt"), "w") as c:
        c.write("Paintings: public domain, Wikimedia Commons.\n" + "\n".join(PAINTINGS) + "\n\n")
        c.write("Photos: Unsplash License.\n" + "\n".join(f"p_{p}: {a} - {u}" for p, a, u in PHOTOS) + "\n")
    print(f"{len(PAINTINGS) + len(PHOTOS)} images ready in {out}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/tmp/image-bank")
