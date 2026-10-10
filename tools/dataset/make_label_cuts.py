"""cuts/<capture>.bmp + _mask.bmp (Kotlin dump) -> cuts/<capture>.png with alpha, for tools/dataset/label_pieces.html."""
import glob, os, sys
import numpy as np
from PIL import Image
for d in glob.glob(os.path.join(sys.argv[1], "*", "cuts")):
    for f in glob.glob(os.path.join(d, "*.bmp")):
        if f.endswith("_mask.bmp"):
            continue
        rgb = np.asarray(Image.open(f).convert("RGB")); m = np.asarray(Image.open(f[:-4] + "_mask.bmp").convert("L")) > 127
        Image.fromarray(np.dstack([rgb, (m * 255).astype(np.uint8)]), "RGBA").save(f[:-4] + ".png")
    print(d, len(glob.glob(os.path.join(d, "*.png"))), "png")
