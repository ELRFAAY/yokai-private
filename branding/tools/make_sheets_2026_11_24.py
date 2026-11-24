"""Cuts the owner's sheets of 2026-11-24 (branding/mascot-models/new-sheets-2026-11-24/) into drawables.
  AV1  stage portraits 4x3   -> r_av_1 ... r_av_12   (round-avatar heads of the 12 growth stages)
  AV2  costume portraits 4x3 -> r_avc_<name>          (alchemist ... knight)
  AV3  costume portraits 3x3 -> r_avc_<name>          (magician ... steampunk)
  IC1  icons 4x2             -> r_ic_<name>           (dragon head, crowned head, egg, book, hourglass, flame, trophy, calendar)
  HAMMER 2 cells             -> r_hammer_up, r_hammer_raised
Method: every cell is cut on the sheet's fixed grid. The "core" = everything inside the dark outline (outline mask, closed, holes filled), kept
fully opaque (so white hats, white eyes and the calendar page stay solid). Outside the core, light pixels are turned into real transparency by
un-blending them from white (alpha from how far they are from white, colour recovered), so soft glows / halo rings / sparkles / glass keep
their tint on any background and the jpeg noise (< 12 levels from white) disappears.
Portraits are re-centred horizontally on the core and put on a square canvas (the app cuts a circle).
Run from the repo root: python3 branding/tools/make_sheets_2026_11_24.py   (Pillow, numpy, scipy)
"""
import os

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

D = 'branding/mascot-models/new-sheets-2026-11-24/'
OUT = 'app/src/main/res/drawable-nodpi/'
CUTS = 'branding/mascot-models/new-sheets-2026-11-24/cuts/'

STAGES = ['AV1-stage-portraits-568231.jpg', 4, 3, ['r_av_%d' % i for i in range(1, 13)]]
COSTUMES_A = ['AV2-costume-portraits-1-12-568235.jpg', 4, 3,
              ['r_avc_' + n for n in 'alchemist astronaut bonsai builder chef detective doctor executive explorer firefighter guard knight'.split()]]
COSTUMES_B = ['AV3-costume-portraits-13-21-568236.jpg', 3, 3,
              ['r_avc_' + n for n in 'magician opera painter pharaoh pilot pirate rockstar scribe steampunk'.split()]]
ICONS = ['IC1-icons-568233.jpg', 4, 2,
         ['r_ic_dragon', 'r_ic_dragon_crown', 'r_ic_egg', 'r_ic_book', 'r_ic_hourglass', 'r_ic_flame', 'r_ic_trophy', 'r_ic_calendar']]
HAMMER = ['HAMMER-568234.jpg', 2, 1, ['r_hammer_up', 'r_hammer_raised']]


def to_rgba(rgb, clear_gaps=0):
    """rgb: float array HxWx3 (0-255) on white. Returns RGBA uint8 with the core opaque and the rest un-blended from white.
    clear_gaps = min area (px) of a closed PURE-WHITE gap inside the outline (between an arm and the body, inside a tail coil) that is turned
    transparent too (0 = keep every enclosed white area, right for hats, calendar pages, eye whites)."""
    mn = rgb.min(axis=2)
    dark = mn < 110
    closed = ndi.binary_closing(dark, iterations=3)
    core = ndi.binary_fill_holes(closed)
    # tiny dark specks that are not part of anything big are not a core
    lab, n = ndi.label(core)
    if n > 1:
        sizes = ndi.sum(core, lab, range(1, n + 1))
        keep = [i + 1 for i, s in enumerate(sizes) if s >= 0.0006 * core.size]
        core = np.isin(lab, keep)
    if clear_gaps:
        white = (mn >= 246) & ((rgb.max(axis=2) - mn) < 8)
        wl, wn = ndi.label(white)
        if wn:
            sizes = ndi.sum(white, wl, range(1, wn + 1))
            big = [i + 1 for i, sz in enumerate(sizes) if sz >= clear_gaps]
            gap = ndi.binary_dilation(np.isin(wl, big), iterations=1)
            core = core & ~gap
    # alpha ~ distance from white (reaches 1 at min channel 185 (a glow keeps a light, luminous colour on dark backgrounds)); the colour is recovered with the SAME ratio so a glow keeps ONE hue along its
    # whole fade (a gate, not an offset, removes the jpeg noise: pixels < 8 levels from white stay clear, 8-18 fade in)
    a_raw = np.clip((255.0 - mn) / (255.0 - 185.0), 0, 1)
    gate = np.clip((255.0 - mn - 8.0) / 10.0, 0, 1)
    a = a_raw * gate
    af = np.maximum(a_raw, 1e-3)[..., None]
    colour = np.clip(255.0 - (255.0 - rgb) / af, 0, 255)
    alpha = np.where(core, 1.0, a)
    out_rgb = np.where(core[..., None], rgb, colour)
    return np.dstack([out_rgb, alpha * 255]).astype(np.uint8), core


def cut_grid(spec):
    f, cols, rows, names = spec
    im = np.asarray(Image.open(D + f).convert('RGB')).astype(np.float32)
    h, w, _ = im.shape
    cw, ch = w / cols, h / rows
    res = {}
    for i, name in enumerate(names):
        r, c = divmod(i, cols)
        x0, x1 = round(c * cw), round((c + 1) * cw)
        y0, y1 = round(r * ch), round((r + 1) * ch)
        # a hair inside the grid so the grey cell borders of some sheets never get in
        cell = im[y0 + 2:y1 - 2, x0 + 2:x1 - 2]
        res[name] = to_rgba(cell)
    return res


def portrait(rgba, core, size=384):
    """square canvas, core centred horizontally, same scale for the whole sheet (scale = cell side -> size)."""
    h, w, _ = rgba.shape
    side = max(h, w)
    ys, xs = np.nonzero(core)
    cx = (xs.min() + xs.max()) / 2.0
    canvas = np.zeros((side, side, 4), np.uint8)
    ox = int(round(side / 2.0 - cx))
    ox = max(min(ox, side - w), 0) if w <= side else 0
    canvas[0:h, ox:ox + w] = rgba
    return Image.fromarray(canvas, 'RGBA').resize((size, size), Image.LANCZOS)


def trim_pad(rgba, core, side, margin=0.1):
    ys, xs = np.nonzero(rgba[..., 3] > 8)
    im = Image.fromarray(rgba, 'RGBA').crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    k = side * (1 - 2 * margin) / max(im.size)
    im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
    canvas = Image.new('RGBA', (side, side), (0, 0, 0, 0))
    canvas.alpha_composite(im, ((side - im.width) // 2, (side - im.height) // 2))
    return canvas


def save(img, name):
    os.makedirs(CUTS, exist_ok=True)
    img.save(CUTS + name + '.png')
    img.save(OUT + name + '.webp', 'WEBP', quality=90, method=6)
    print(name, img.size)


if __name__ == '__main__':
    for spec in (STAGES, COSTUMES_A, COSTUMES_B):
        for name, (rgba, core) in cut_grid(spec).items():
            save(portrait(rgba, core), name)
    for name, (rgba, core) in cut_grid(ICONS).items():
        save(trim_pad(rgba, core, 256), name)
    # the hammer: both poses at the SAME scale (the upright one is the reference), 512 px canvas
    hm = cut_grid(HAMMER)
    boxes = {}
    for name, (rgba, core) in hm.items():
        ys, xs = np.nonzero(rgba[..., 3] > 8)
        boxes[name] = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    big = max(max(b[2] - b[0], b[3] - b[1]) for b in boxes.values())
    k = 480.0 / big
    for name, (rgba, core) in hm.items():
        b = boxes[name]
        im = Image.fromarray(rgba, 'RGBA').crop(b)
        im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
        canvas = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
        canvas.alpha_composite(im, ((512 - im.width) // 2, (512 - im.height) // 2))
        save(canvas, name)
