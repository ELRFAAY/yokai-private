"""Cuts the wise-old-snake poses out of branding/mascot-models/13-wise-master-and-poses-559281.jpg into the six
384x384 transparent WebPs the home mascot uses (r_sage_<mood>.webp).
Usage: python make_sage_from_sheet13.py <sheet.jpg> <res dir>
Pose index = position on the sheet, row by row (0..11)."""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

SHEET, RES = sys.argv[1], sys.argv[2]
BOXES = [(55, 27, 284, 265), (337, 27, 565, 265), (618, 27, 846, 265), (899, 27, 1143, 265),
         (60, 307, 280, 541), (338, 312, 569, 541), (631, 300, 850, 542), (923, 294, 1139, 542),
         (61, 600, 275, 868), (345, 597, 560, 868), (604, 601, 891, 868), (926, 581, 1142, 868)]
MOODS = {'happy': 11, 'blink': 7, 'hurt': 6, 'sad': 1, 'scared': 0, 'dizzy': 2}
SCALE = 1.3
src = Image.open(SHEET).convert('RGB')

def cut(box):
    x0, y0, x1, y1 = box
    im = src.crop((x0 - 8, y0 - 8, x1 + 8, y1 + 8))
    pad = Image.new('RGB', (im.width + 12, im.height + 12), (255, 255, 255)); pad.paste(im, (6, 6)); im = pad
    a = np.asarray(im).astype(np.int16)
    minc = a.min(axis=2)
    lab, _ = ndi.label(minc >= 205)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    alpha = np.where(bg, 0, 255).astype(np.uint8)
    ring = ndi.binary_dilation(bg, iterations=1) & ~bg
    soft = np.clip((255 - minc) * 255 // 110, 0, 255).astype(np.uint8)
    alpha = np.where(ring, np.minimum(alpha, soft), alpha).astype(np.uint8)
    lab2, n2 = ndi.label(alpha > 0)
    sizes = ndi.sum(alpha > 0, lab2, range(1, n2 + 1))
    alpha = np.where(lab2 == (int(np.argmax(sizes)) + 1), alpha, 0).astype(np.uint8)
    ys, xs = np.nonzero(alpha)
    rgba = np.dstack([a.astype(np.uint8), alpha])[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    return Image.fromarray(rgba, 'RGBA')

for mood, idx in MOODS.items():
    c = cut(BOXES[idx])
    c = c.resize((int(c.width * SCALE), int(c.height * SCALE)), Image.LANCZOS)
    canvas = Image.new('RGBA', (384, 384), (0, 0, 0, 0))
    canvas.paste(c, ((384 - c.width) // 2, (384 - c.height) // 2), c)
    canvas.save(f'{RES}/drawable-nodpi/r_sage_{mood}.webp', 'WEBP', lossless=True, quality=100, method=6)
    print(mood, idx, c.size)
