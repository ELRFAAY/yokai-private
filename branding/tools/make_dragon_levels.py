"""Cuts the 12 growth stages of the little dragon (owner's sheets of 2026-11-23, in branding/mascot-models/new-sheets-2026-11-23/):
stages 1-6 = levels-1-6-567658.jpg (3x2), stages 7-12 = picked cells of growth-A-567723.jpg and growth-B-567721.jpg (3x2, cells numbered 1-6
left to right, top to bottom). Picked for: dark green body, the hood spiral still visible, richer than the stage before.
Output: app/src/main/res/drawable-nodpi/r_lv_1.webp ... r_lv_12.webp (every stage scaled to the same height) + branding/mascot-models/growth-stages/*.png
Run from the repo root: python3 branding/tools/make_dragon_levels.py   (Pillow, numpy, scipy)
"""
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

D = 'branding/mascot-models/new-sheets-2026-11-23/'
PICK = [  # stage: (file, cell 1-6)
    ('levels-1-6-567658.jpg', 1), ('levels-1-6-567658.jpg', 2), ('levels-1-6-567658.jpg', 3),
    ('levels-1-6-567658.jpg', 4), ('levels-1-6-567658.jpg', 5), ('levels-1-6-567658.jpg', 6),
    ('growth-A-567723.jpg', 2), ('growth-A-567723.jpg', 3), ('growth-A-567723.jpg', 4),
    ('growth-B-567721.jpg', 3), ('growth-B-567721.jpg', 4), ('growth-B-567721.jpg', 5),
]
H = 320


def cell(file, n, cols=3, rows=2):
    im = np.array(Image.open(D + file).convert('RGB'))
    h, w, _ = im.shape
    white = (im > 225).all(axis=2)
    lab, _ = ndi.label(white)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fg = ~np.isin(lab, list(border))
    r, c = divmod(n - 1, cols)
    plab, k = ndi.label(ndi.binary_dilation(fg, iterations=5))
    cw, ch = w / cols, h / rows
    mine = np.zeros_like(fg)
    for i in range(1, k + 1):
        m = (plab == i) & fg
        if not m.any():
            continue
        ys, xs = np.nonzero(m)
        cr = min(int(np.median(ys) / ch), rows - 1)
        cc = min(int(np.median(xs) / cw), cols - 1)
        if cr == r and cc == c:
            mine |= m
    ys, xs = np.nonzero(mine)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    a = ndi.gaussian_filter(mine.astype(np.float32), 0.7)
    a = np.where(mine, 1.0, np.clip(a * 1.6, 0, 1) * ndi.binary_dilation(mine, iterations=1))
    a = np.where(~mine & (im.min(axis=2) > 190), 0.0, a)
    return Image.fromarray(np.dstack([im, (a * 255).astype(np.uint8)])[y0:y1, x0:x1], 'RGBA')


if __name__ == '__main__':
    for i, (f, n) in enumerate(PICK, 1):
        t = cell(f, n)
        t = t.resize((max(1, round(t.width * H / t.height)), H), Image.LANCZOS)
        t.save('branding/mascot-models/growth-stages/stage_%02d.png' % i)
        t.save('app/src/main/res/drawable-nodpi/r_lv_%d.webp' % i, 'WEBP', quality=88, method=6)
        print(i, f, n, t.size)
