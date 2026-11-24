"""Cuts the owner's egg evolution sheet (EGG-569149.jpg, 4x2) into r_egg_1..8.webp (transparent, shared scale).
Background = white connected to the cell border; the soft glow around cracks fades out by distance to white (no halo disc).
Frames: 448x384 canvas, bottom line y=374 like the standard dragon, one common scale for all 8 frames.
Run from the repo root: python3 -I branding/tools/make_egg_frames.py   (Pillow, numpy, scipy)"""
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

SRC = 'branding/mascot-models/new-sheets-2026-11-24/egg/EGG-569149.jpg'
RES = 'app/src/main/res/drawable-nodpi/'
im = np.asarray(Image.open(SRC).convert('RGB')).astype(np.float32)
H, W, _ = im.shape
cw, ch = W // 4, H // 2
EGG_H, CW, CH, BASE = 330, 448, 384, 374
cells = []
for i in range(8):
    r, c = divmod(i, 4)
    cell = im[r * ch:(r + 1) * ch, c * cw:(c + 1) * cw]
    mn = cell.min(axis=2)
    bgish = mn >= 205
    lab, n = ndi.label(bgish)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    alpha = np.where(bg, np.clip((245 - mn) / 40.0, 0, 1), 1.0)
    alpha = np.where(bg & (mn >= 245), 0, alpha)
    # un-blend from white so the glow keeps its colour, not a grey fringe
    a = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.where(bg[..., None], np.clip((cell - 255 * (1 - a)) / a, 0, 255), cell)
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    cells.append(rgba)
# common scale: from the first (plain) egg's height
a0 = cells[0][..., 3] > 200
ys0 = np.where(a0.any(axis=1))[0]
# the sparkles/smoke of frame 1 are also opaque: use the solid egg of frame 2 as the reference
a1 = cells[1][..., 3] > 250
ys1 = np.where(a1.any(axis=1))[0]
s = EGG_H / float(ys1.max() - ys1.min())
for i, rgba in enumerate(cells):
    al = rgba[..., 3] > 40
    ys, xs = np.where(al)
    # anchor: the egg's own bottom (frame 8 = half shell sits on the same line)
    big = rgba[..., 3] > 250
    by = np.where(big.any(axis=1))[0].max()
    bx = np.where(big.any(axis=0))[0]
    cx = (bx.min() + bx.max()) / 2.0
    img = Image.fromarray(rgba, 'RGBA')
    img = img.resize((int(img.width * s), int(img.height * s)), Image.LANCZOS)
    canvas = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
    ox = int(CW / 2 - cx * s)
    oy = int(BASE - by * s)
    canvas.alpha_composite(img, (max(ox, 0), max(oy, 0))) if ox >= 0 and oy >= 0 else canvas.paste(img, (ox, oy), img)
    canvas.save(RES + 'r_egg_%d.webp' % (i + 1), quality=92, method=6)
    print(i + 1, canvas.size)
