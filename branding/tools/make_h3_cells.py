"""Cuts the owner's H3 sheet (both dragons in every cell) into 8 sprites with a FIXED GRID (not blob by blob).
Sheet: branding/mascot-models/new-sheets-2026-11-22/H3-contact-both-dragons-4x2-565719.jpg (1376x768, 4 columns x 2 rows).
 1 calm  2 wind-up  3 swing  4 IMPACT (cane tip ON the little dragon's head)  5 bump + stars  6 bounce-back dizzy  7 lecture  8 sad end
How:
 - every cell uses the SAME crop box (grid cell + PADX each side), so the framing is identical for all 8 frames;
   row 2 is moved up by its measured baseline difference so both rows stand on the same floor line;
 - the background is the white that is connected to the crop border (flood fill); white INSIDE the drawing (eyes, beard) stays;
   closed white holes between the coils of the lower body are cleared (they would show white on a dark page);
 - things that cross a grid line (the cane tip of cell 6 reaches into cell 7) are kept by connected pieces: every piece
   belongs to the cell that holds most of it, and pieces of the neighbour cell are erased from the crop;
 - every frame is scaled by the SAME factor (no per-frame scaling), so nothing pulses.
Output: app/src/main/res/drawable-nodpi/r_h3_0..7.webp (+ PNG cut-outs in branding/mascot-models/cutouts-2026-11-22/H3_*.png).
Run from the repo root: python3 branding/tools/make_h3_cells.py [--preview]   (Pillow, numpy, scipy)
"""
import os
import sys
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi

SRC = 'branding/mascot-models/new-sheets-2026-11-22/H3-contact-both-dragons-4x2-565719.jpg'
CUT = 'branding/mascot-models/cutouts-2026-11-22/'
RES = 'app/src/main/res/drawable-nodpi/'
COLS, ROWS = 4, 2
PADX = 24            # the box is wider than the grid cell: tails, stars and the cane tip touch the cell edge
BOX_H = 376
ROW_Y0 = (8, 392)    # box top of row 1 / row 2 (sheet px); row 2 is corrected by ROW_DY below
WHITE = 225          # all channels above this = page white
WISE_H_OUT = 436.0   # wise height (hat top -> floor) in the output sprites = the wise height of the G walk sprites, so the scene draws both at the same scale
MARGIN = 6
HOLE_ZONE = 120      # closed white holes this close to the floor line are coil holes and get cleared
HOLE_MIN = 60
# closed white gaps of the drawing that are NOT coil holes (found by looking at the dark preview): (cell index, x, y) in the cell box
# cell 1: the gap between the cane, the hand and the beard, and the little gap at the wise dragon's elbow
SEEDS = [(0, 226, 160), (0, 302, 218)]
SEED_MAX = 900


def load():
    im = np.array(Image.open(SRC).convert('RGB')).astype(np.int16)
    return im


def piece_owner(fg, cell_w, rows_split):
    """label the drawing in pieces (dilated a little so stars / motion lines stay with their dragon) and give every
    piece to the grid cell that holds most of its pixels."""
    H, W = fg.shape
    owner = np.full((H, W), -1, np.int16)
    lab, n = ndi.label(ndi.binary_dilation(fg, iterations=5))
    for i, sl in enumerate(ndi.find_objects(lab), 1):
        m = (lab == i) & fg
        if not m.any():
            continue
        ys, xs = np.nonzero(m)
        r = 0 if ys.mean() < rows_split else 1
        cnt = np.bincount(np.minimum(xs // cell_w, COLS - 1), minlength=COLS)
        c = int(cnt.argmax())
        owner[m] = r * COLS + c
    return owner


def cell_rgba(im, fg_owner, idx, dy):
    H, W, _ = im.shape
    r, c = divmod(idx, COLS)
    cw = W // COLS
    x0, x1 = c * cw - PADX, (c + 1) * cw + PADX
    y0 = ROW_Y0[r] + dy
    y1 = y0 + BOX_H
    pad = 40
    big = np.pad(im, ((pad, pad), (pad, pad), (0, 0)), constant_values=255)
    own = np.pad(fg_owner, ((pad, pad), (pad, pad)), constant_values=-1)
    rgb = big[y0 + pad:y1 + pad, x0 + pad:x1 + pad].astype(np.uint8)
    mine = own[y0 + pad:y1 + pad, x0 + pad:x1 + pad] == idx
    white = (rgb[..., 0] > WHITE) & (rgb[..., 1] > WHITE) & (rgb[..., 2] > WHITE)
    # keep only my pieces: everything else (the neighbour's pieces too) becomes page white
    keep = mine | (white & ndi.binary_dilation(mine, iterations=1))
    bg_candidate = white | ~mine
    lab, _ = ndi.label(bg_candidate)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    fg = ~bg
    return rgb, fg, (x0, y0)


def clear_coil_holes(rgb, fg, floor_y):
    white = (rgb[..., 0] > WHITE) & (rgb[..., 1] > WHITE) & (rgb[..., 2] > WHITE)
    holes = white & fg
    lab, n = ndi.label(holes)
    cleared = 0
    for i, sl in enumerate(ndi.find_objects(lab), 1):
        m = lab == i
        a = int(m.sum())
        cy = np.nonzero(m)[0].mean()
        if a >= HOLE_MIN and cy > floor_y - HOLE_ZONE:
            fg = fg & ~m
            cleared += 1
    return fg, cleared


def to_img(rgb, fg):
    al = Image.fromarray((fg * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    img = Image.fromarray(rgb).convert('RGBA')
    img.putalpha(al)
    return img


def wise_right_edge(img):
    """right edge of the wise dragon's coil (the rightmost drawn pixel of the bottom rows; the little dragon is always left of it)."""
    a = np.array(img.getchannel('A')) > 128
    fl = int(np.nonzero(a.any(axis=1))[0].max())
    xs = np.nonzero(a[fl - 52: fl - 8].any(axis=0))[0]
    return int(xs.max()), fl


def hat_top(img):
    """top of the wise dragon's straw hat (hat colour, right part of the cell, so the cane curl above it does not count)."""
    rgb = np.array(img.convert('RGB')).astype(int)
    a = np.array(img.getchannel('A')) > 128
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    hat = (r > 150) & (r - b > 45) & (r > g) & (g > 110) & a
    yy, _ = np.nonzero(hat[:, 200:])
    return int(yy.min())


def floor_line(fg):
    return int(np.nonzero(fg.any(axis=1))[0].max())


def run(preview=False):
    os.makedirs(CUT, exist_ok=True)
    im = load()
    H, W, _ = im.shape
    white = (im[..., 0] > WHITE) & (im[..., 1] > WHITE) & (im[..., 2] > WHITE)
    lab, _ = ndi.label(white)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fg_sheet = ~np.isin(lab, list(border))
    owner = piece_owner(fg_sheet, W // COLS, H // 2)
    # floor of every cell (lowest drawn pixel of the cell's own pieces)
    floors = []
    for idx in range(ROWS * COLS):
        ys = np.nonzero((owner == idx).any(axis=1))[0]
        floors.append(int(ys.max()))
    row_floor = [int(np.median(floors[:COLS])), int(np.median(floors[COLS:]))]
    dy2 = (row_floor[1] - ROW_Y0[1]) - (row_floor[0] - ROW_Y0[0])
    print('floor per cell', floors, 'row floors', row_floor, 'row-2 shift', dy2)
    cells = []
    for idx in range(ROWS * COLS):
        dy = dy2 if idx >= COLS else 0
        rgb, fg, org = cell_rgba(im, owner, idx, dy)
        floor_rel = row_floor[idx // COLS] - org[1]
        fg, cleared = clear_coil_holes(rgb, fg, floor_rel)
        for (ci, sx, sy) in SEEDS:
            if ci != idx:
                continue
            wh = (rgb[..., 0] > WHITE) & (rgb[..., 1] > WHITE) & (rgb[..., 2] > WHITE) & fg
            wl, _ = ndi.label(wh)
            near = wl[max(0, sy - 6):sy + 7, max(0, sx - 6):sx + 7]
            ids = [v for v in np.unique(near) if v]
            for v in ids:
                reg = wl == v
                if reg.sum() <= SEED_MAX:
                    fg = fg & ~reg
                    print('  cell', idx + 1, 'cleared a white gap of', int(reg.sum()), 'px at', (sx, sy))
        img = to_img(rgb, fg)
        cells.append(img)
        print('cell', idx + 1, 'box', org, 'size', img.size, 'coil holes cleared', cleared)
    # every frame gets the same wise height (row 2 of the sheet was drawn ~3% smaller) and the same wise position
    # (the wise slid up to 25 sheet px sideways between frames): scale + shift each cell about the wise's floor / right edge.
    info = [(wise_right_edge(c), hat_top(c)) for c in cells]
    heights = [fl - ht for ((_, fl), ht) in info]
    h_ref = float(np.median(heights))
    tx = float(np.median([e for ((e, _), _) in info]))
    fy = float(np.median([fl for ((_, fl), _) in info]))
    k = WISE_H_OUT / h_ref
    print('wise height per cell', heights, 'right edge', [e for ((e, _), _) in info], '-> out scale', round(k, 4))
    big = 80
    placed = []
    for i, img in enumerate(cells):
        (edge, fl), ht = info[i]
        s_i = k * h_ref / (fl - ht)
        w, h = round(img.width * s_i), round(img.height * s_i)
        sc = img.resize((w, h), Image.LANCZOS)
        cv = Image.new('RGBA', (round(img.width * k) + 2 * big, round(img.height * k) + 2 * big), (0, 0, 0, 0))
        ox = round(tx * k - edge * s_i) + big
        oy = round(fy * k - fl * s_i) + big
        cv.alpha_composite(sc, (ox, oy))
        placed.append(cv)
        img.save(CUT + 'H3_%d.png' % i)
    boxes = [c.getbbox() for c in placed]
    x0 = min(b[0] for b in boxes) - MARGIN
    y0 = min(b[1] for b in boxes) - MARGIN
    x1 = max(b[2] for b in boxes) + MARGIN
    y1 = max(b[3] for b in boxes) + MARGIN
    outs = []
    for i, cv in enumerate(placed):
        o = cv.crop((x0, y0, x1, y1))
        o.save(RES + 'r_h3_%d.webp' % i, quality=90, method=6)
        outs.append(o)
        print('r_h3_%d' % i, o.size)
    print('common canvas', outs[0].size, 'wise floor y', round(fy * k) + big - y0, 'wise right edge x', round(tx * k) + big - x0)
    if preview:
        w, h = outs[0].size
        for name, bgc in (('dark', (24, 24, 28, 255)), ('light', (236, 232, 222, 255))):
            sheet = Image.new('RGBA', (w * COLS, h * ROWS), bgc)
            for i, o in enumerate(outs):
                sheet.alpha_composite(o, ((i % COLS) * w, (i // COLS) * h))
            sheet.convert('RGB').save('/tmp/h3/preview_%s.png' % name)


if __name__ == '__main__':
    run('--preview' in sys.argv)
