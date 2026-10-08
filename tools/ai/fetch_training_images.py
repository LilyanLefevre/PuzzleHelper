#!/usr/bin/env python3
"""
Downloads free images to train the re-ranker on: Unsplash-licensed photos (picsum.photos) and public-domain
paintings from Wikimedia Commons (Google Art Project). The images of the benchmark bank are left out, so the evaluation stays fair.

    python tools/ai/fetch_training_images.py C:/dev/data/train-images --photos 1000 --art 1000
"""
import argparse, json, os, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "dataset"))
from prepare_image_bank import PHOTOS  # noqa: E402

UA = "PuzzleItTraining/1.0 (https://github.com/LilyanLefevre/PuzzleHelper)"
BANK = {pid for pid, _, _ in PHOTOS}
# Paintings of the bank (or close variants): kept out of training.
BANK_TITLES = ("grande jatte", "water lil", "starry night", "kiss", "impression", "mona lisa", "babel", "wave")


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def save(url, path):
    if os.path.exists(path):
        return
    try:
        data = get(url)
        open(path, "wb").write(data)
    except Exception as e:
        print("skip", url, e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out"); ap.add_argument("--photos", type=int, default=1000); ap.add_argument("--art", type=int, default=1000)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    jobs, credits = [], []
    page = 1
    while len(jobs) < a.photos:
        items = json.loads(get(f"https://picsum.photos/v2/list?page={page}&limit=100"))
        if not items:
            break
        for it in items:
            if it["id"] not in BANK and len(jobs) < a.photos:
                jobs.append((f"https://picsum.photos/id/{it['id']}/1200/800", os.path.join(a.out, f"p_{it['id']}.jpg")))
                credits.append(f"p_{it['id']}: {it['author']} - {it['url']} (Unsplash License)")
        page += 1
    n_art, offset = 0, 0
    while n_art < a.art and offset is not None:
        q = ("https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6"
             "&gsrsearch=%22Google%20Art%20Project%22%20painting&gsrlimit=50"
             f"&gsroffset={offset}&prop=imageinfo&iiprop=url&iiurlwidth=1200")
        data = json.loads(get(q))
        offset = data.get("continue", {}).get("gsroffset")
        for it in data.get("query", {}).get("pages", {}).values():
            info, title = it.get("imageinfo", [{}])[0], it["title"]
            if "thumburl" in info and n_art < a.art and not any(t in title.lower() for t in BANK_TITLES):
                jobs.append((info["thumburl"], os.path.join(a.out, f"w_{it['pageid']}.jpg")))
                credits.append(f"w_{it['pageid']}: {title} - Wikimedia Commons (public domain)")
                n_art += 1
    with ThreadPoolExecutor(4) as ex:
        list(ex.map(lambda j: save(*j), jobs))
    open(os.path.join(a.out, "CREDITS.txt"), "w", encoding="utf-8").write("\n".join(credits) + "\n")
    print(len([f for f in os.listdir(a.out) if f.endswith(".jpg")]), "images in", a.out)


if __name__ == "__main__":
    main()
