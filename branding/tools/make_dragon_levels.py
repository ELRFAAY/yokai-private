"""Cuts the 12 growth stages of the little dragon (owner's sheets of 2026-11-23, in branding/mascot-models/new-sheets-2026-11-23/):
stages 1-6 = levels-1-6-567658.jpg (3x2), stages 7-12 = picked cells of growth-A-567723.jpg and growth-B-567721.jpg (3x2, cells numbered 1-6
left to right, top to bottom). Picked for: dark green body, the hood spiral still visible, richer than the stage before.
v2 (24 Nov 2026): the old cutter treated everything that is not white as solid, so the soft glows / halo rings became opaque cream discs and
the antler tips got a jagged white fringe. Now it uses the same method as make_sheets_2026_11_24.py (opaque core inside the outline, light pixels
outside it un-blended from white = real soft transparency, closed white gaps between arm and body / inside the tail coil cleared).
All 12 pictures share ONE canvas and ONE scale (the dragon's body, not its glow, is the same height in every stage, bottom-centred), so the
stages line up and the glow never shrinks the dragon.
Output: app/src/main/res/drawable-nodpi/r_lv_1.webp ... r_lv_12.webp + branding/mascot-models/growth-stages/*.png
Run from the repo root: python3 branding/tools/make_dragon_levels.py   (Pillow, numpy, scipy)
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

sys.path.insert(0, os.path.dirname(__file__))
from make_sheets_2026_11_24 import to_rgba  # noqa: E402

D = 'branding/mascot-models/new-sheets-2026-11-23/'
PICK = [  # stage: (file, cell 1-6)
    ('levels-1-6-567658.jpg', 1), ('levels-1-6-567658.jpg', 2), ('levels-1-6-567658.jpg', 3),
    ('levels-1-6-567658.jpg', 4), ('levels-1-6-567658.jpg', 5), ('levels-1-6-567658.jpg', 6),
    ('growth-A-567723.jpg', 2), ('growth-A-567723.jpg', 3), ('growth-A-567723.jpg', 4),
    ('growth-B-567721.jpg', 3), ('growth-B-567721.jpg', 4), ('growth-B-567721.jpg', 5),
]
CORE_H = 300  # px: the body (outline silhouette) of every stage is scaled to this height
GAP_MIN = 200  # px: closed white gaps at least this big become transparent
_cache = {}


def sheet(file):
    if file not in _cache:
        rgb = np.asarray(Image.open(D + file).convert('RGB')).astype(np.float32)
        rgba, core = to_rgba(rgb, clear_gaps=GAP_MIN)
        _cache[file] = (rgba, core)
    return _cache[file]


def stage(file, n, cols=3, rows=2):
    """returns (RGBA array of this stage only, its core mask), both cropped to the alpha box."""
    rgba, core = sheet(file)
    h, w = core.shape
    cw, ch = w / cols, h / rows
    r, c = divmod(n - 1, cols)
    vis = rgba[..., 3] > 8
    lab, k = ndi.label(ndi.binary_dilation(vis, iterations=4))
    keep = np.zeros_like(vis)
    for i in range(1, k + 1):
        m = lab == i
        ys, xs = np.nonzero(m & core)
        if len(ys) < 200:
            continue
        cr = min(int(np.median(ys) / ch), rows - 1)
        cc = min(int(np.median(xs) / cw), cols - 1)
        if cr == r and cc == c:
            keep |= m
    out = rgba.copy()
    out[..., 3] = np.where(keep, out[..., 3], 0)
    kc = core & keep
    ys, xs = np.nonzero(out[..., 3] > 8)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    return out[y0:y1, x0:x1], kc[y0:y1, x0:x1]


if __name__ == '__main__':
    os.makedirs('branding/mascot-models/growth-stages', exist_ok=True)
    cuts = [stage(f, n) for f, n in PICK]
    # one shared canvas: widest / tallest extents around the body, measured at the common body scale
    left = right = top = bottom = 0.0
    info = []
    for rgba, core in cuts:
        ys, xs = np.nonzero(core)
        cy0, cy1, cx = ys.min(), ys.max() + 1, (xs.min() + xs.max()) / 2.0
        s = CORE_H / float(cy1 - cy0)
        h, w = core.shape
        left = max(left, cx * s)
        right = max(right, (w - cx) * s)
        top = max(top, cy1 * s)  # everything above the body's bottom line
        bottom = max(bottom, (h - cy1) * s)
        info.append((s, cx, cy1))
    half = int(np.ceil(max(left, right)))
    W, H = 2 * half, int(np.ceil(top + bottom))
    base = int(np.ceil(top))
    print('canvas', W, H)
    for i, ((rgba, core), (s, cx, cy1)) in enumerate(zip(cuts, info), 1):
        im = Image.fromarray(rgba, 'RGBA')
        im = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)
        canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        canvas.alpha_composite(im, (int(round(half - cx * s)), int(round(base - cy1 * s))))
        canvas.save('branding/mascot-models/growth-stages/stage_%02d.png' % i)
        canvas.save('app/src/main/res/drawable-nodpi/r_lv_%d.webp' % i, 'WEBP', quality=90, method=6)
        print(i, PICK[i - 1], canvas.size)
