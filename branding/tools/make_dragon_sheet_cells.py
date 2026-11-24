"""Cuts the owner's 2026-11-23 SMALL dragon sheets (SA SB SC SC2 SD SD2 SE SF SG0 SG2 SG3) into PNG cut-outs.
Grid = the image divided equally into COLS x ROWS (the two rows of SA / SB / SE have an empty band in the middle, an equal split puts it between rows).
Background = only the white that is connected to the image border (flood fill), so white inside the drawing (eyes, teeth, galabiya) stays.
Pieces that cross a grid line go to the cell that holds most of them. NO scaling: every frame keeps the sheet's own scale, so the scale is the same for the whole sheet.
Skipped on purpose (INDEX.md): SG1 (inconsistent backs, redo pending), SG2 cell 8 (off-model).
Output: branding/mascot-models/cutouts-2026-11-23/<name>.png  (+ _preview_<sheet>.png on a dark page)
Run from the repo root: python3 branding/tools/make_dragon_sheet_cells.py [SA SB ...]   (Pillow, numpy, scipy)
"""
import os
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

SRC = 'branding/mascot-models/new-sheets-2026-11-23/'
OUT = 'branding/mascot-models/cutouts-2026-11-23/'
RES = 'app/src/main/res/drawable-nodpi/'
STD = 'app/src/main/res/drawable-nodpi/r_mascot_happy.webp'  # the standard little dragon: 384 px canvas, base line y=374
WHITE = 225
PAD = 24
# id: (file, cols, rows, name prefix, cells to skip)
SHEETS = {
    'SA': ('SA-greeting-4x2-567618.jpg', 4, 2, 'r_sa_', ()),
    'SB': ('SB-manga-finished-jump-4x2-567619.jpg', 4, 2, 'r_sb_', ()),
    'SC': ('SC-weekly-goal-spin-turntable-4x2-567620.jpg', 4, 2, 'r_sc_', ()),
    'SC2': ('SC2-after-spin-3x2-567621.jpg', 3, 2, 'r_sc2_', ()),
    'SD': ('SD-night-yawn-sleep-cap-4x2-567630.jpg', 4, 2, 'r_sd_', ()),
    'SD2': ('SD2-sleep-in-bed-with-cap-3x2-567631.jpg', 3, 2, 'r_sd2_', ()),
    'SE': ('SE-good-morning-tea-4x2-567632.jpg', 4, 2, 'r_se_', ()),
    'SF': ('SF-streak-3-7-30-4x3-567633.jpg', 4, 3, 'r_sf_', ()),
    'SG0': ('SG0-friday-anchor-567610.jpg', 1, 1, 'r_sg0', ()),
    'SG2': ('SG2-friday-poses-4x3-567608.jpg', 4, 3, 'r_sg2_', (7,)),
    'SG3': ('SG3-friday-faces-4x3-567609.jpg', 4, 3, 'r_sg3_', ()),
}


def coil(im):
    """(hood width, x centre of it): the widest row between 30 and 55 percent of the drawing's height. Hats, capes, arms held out
    and effects do not change it, so one scale fits the whole sheet."""
    a = np.array(im)[..., 3] > 10
    ys, xs = np.nonzero(a)
    top, h = ys.min(), ys.max() - ys.min()
    best = (0, 0.0)
    for y in range(top + int(h * 0.30), top + int(h * 0.55)):
        row = np.nonzero(a[y])[0]
        if len(row) and row.max() - row.min() > best[0]:
            best = (row.max() - row.min(), (row.max() + row.min()) / 2.0)
    return best


def normalise(key, frames):
    """Same scale for the whole sheet (hood width = the standard dragon's), frames registered by the hood centre and the
    floor line, one common canvas per sheet. Written as webp to res/drawable-nodpi/<name>.webp. Draw with k = half*2/384, bottom at +half."""
    if not frames:
        return
    std_w, _ = coil(Image.open(STD).convert('RGBA'))
    widths = sorted(coil(im)[0] for _, im in frames)
    s = std_w / float(widths[len(widths) // 2])
    scaled = []
    for name, im in frames:
        w, h = max(1, round(im.width * s)), max(1, round(im.height * s))
        sc = im.resize((w, h), Image.LANCZOS)
        _, cx = coil(sc)
        scaled.append((name, sc, cx))
    left = max(cx for _, _, cx in scaled)
    right = max(sc.width - cx for _, sc, cx in scaled)
    top = max(sc.height for _, sc, _ in scaled)
    W, H = int(np.ceil(left + right)), top
    for name, sc, cx in scaled:
        cv = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        cv.alpha_composite(sc, (int(round(left - cx)), H - sc.height))
        cv.save(RES + name + '.webp', 'WEBP', quality=88, method=6)
    print(key, 'scale %.3f canvas %dx%d' % (s, W, H))


def cut(key):
    f, cols, rows, prefix, skip = SHEETS[key]
    im = np.array(Image.open(SRC + f).convert('RGB'))
    H, W, _ = im.shape
    white = (im > WHITE).all(axis=2)
    lab, _ = ndi.label(white)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    fg = ~bg
    cw, ch = W / cols, H / rows
    owner = np.full((H, W), -1, np.int16)
    plab, n = ndi.label(ndi.binary_dilation(fg, iterations=5))
    for i in range(1, n + 1):
        m = (plab == i) & fg
        if not m.any():
            continue
        ys, xs = np.nonzero(m)
        cell = np.bincount((np.minimum((ys / ch).astype(int), rows - 1) * cols + np.minimum((xs / cw).astype(int), cols - 1)),
                           minlength=cols * rows).argmax()
        owner[m] = cell
    os.makedirs(OUT, exist_ok=True)
    tiles = []
    frames = []
    for idx in range(cols * rows):
        if idx in skip:
            continue
        r, c = divmod(idx, cols)
        x0, x1 = max(0, int(c * cw) - PAD), min(W, int((c + 1) * cw) + PAD)
        y0, y1 = max(0, int(r * ch) - PAD), min(H, int((r + 1) * ch) + PAD)
        mine = owner[y0:y1, x0:x1] == idx
        if not mine.any():
            print(key, idx, 'EMPTY')
            continue
        ys, xs = np.nonzero(mine)
        by0, by1, bx0, bx1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        alpha = ndi.gaussian_filter(mine.astype(np.float32), 0.7)
        alpha = np.where(mine, 1.0, np.clip(alpha * 1.6, 0, 1) * ndi.binary_dilation(mine, iterations=1))
        ring = ~mine
        alpha = np.where(ring & (im[y0:y1, x0:x1].min(axis=2) > 190), 0.0, alpha)  # no white fringe around the outline
        rgba = np.dstack([im[y0:y1, x0:x1], (alpha * 255).astype(np.uint8)])[by0:by1, bx0:bx1]
        name = prefix if (cols * rows == 1) else prefix + str(idx)
        Image.fromarray(rgba, 'RGBA').save(OUT + name + '.png')
        tiles.append(Image.fromarray(rgba, 'RGBA'))
        frames.append((name, Image.fromarray(rgba, 'RGBA')))
    # dark-page preview
    if tiles:
        th = max(t.height for t in tiles)
        tw = sum(t.width + 10 for t in tiles)
        prev = Image.new('RGBA', (min(tw, 3000), th + 20), (28, 28, 34, 255))
        x = 5
        for t in tiles:
            if x + t.width > prev.width:
                break
            prev.alpha_composite(t, (x, 10))
            x += t.width + 10
        prev.convert('RGB').save(OUT + '_preview_' + key + '.png')
    normalise(key, frames)
    print(key, 'done', len(tiles), 'frames')


if __name__ == '__main__':
    for k in (sys.argv[1:] or SHEETS):
        cut(k)
