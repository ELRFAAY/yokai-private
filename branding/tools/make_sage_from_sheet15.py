"""Cuts the wise-old-snake poses out of branding/mascot-models/15-wise-snake-full-body-moods-559299.jpg into the six
384x384 transparent WebPs the home mascot uses (r_sage_<mood>.webp).
Usage: python make_sage_from_sheet15.py <sheet.jpg> <res dir>
Poses: 0 happy, 1 blink, 2 hurt, 3 sad, 4 scared, 5 small floating scared (used for dizzy); the Arabic labels under the poses are excluded by the boxes."""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

SHEET, RES = sys.argv[1], sys.argv[2]
BOXES = [(57, 24, 377, 395), (456, 24, 780, 395), (850, 23, 1141, 398), (151, 464, 439, 838), (492, 460, 780, 858), (805, 501, 985, 811)]
MOODS = {'happy': 0, 'blink': 1, 'hurt': 2, 'sad': 3, 'scared': 4, 'dizzy': 5}
SCALE = 0.95
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
