"""Cuts the owner's costume sheets (branding/mascot-models/costumes-2026-11-21/) into the transformation sprites.

Every sprite is scaled so the small dragon's HOOD has exactly the width of the normal home sprite (r_mascot_happy),
and sits on a canvas whose bottom margin (10 px) and horizontal centre match it, so a costume can be drawn with the
same pixel scale and bottom line as the normal dragon (taller hats just reach higher).
Duplicates between the sheets are skipped on purpose (one detective, one chef, one explorer).
Run from the repo root: python3 branding/tools/make_costumes.py   (needs Pillow, numpy, scipy)
"""
import os
import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage as ndi

BASE = 'branding/mascot-models/costumes-2026-11-21/'
OUT = 'branding/mascot-models/costumes-2026-11-21/cutouts/'
RES = 'app/src/main/res/drawable-nodpi/'

# sheet file -> names left to right; None = a duplicate of another costume, not used
SHEETS = [
    ('costumes-1-knight-sherlock-pirate-guard-chef-astronaut.jpg', ['knight', 'detective', 'pirate', 'guard', 'chef', 'astronaut'], 0),
    ('costumes-2-noir-pharaoh-doctor-firefighter-explorer-magician.jpg', [None, 'pharaoh', 'doctor', 'firefighter', 'explorer', 'magician'], 0),
    ('costumes-3-painter-chef-rock-executive-pilot-builder.jpg', ['painter', None, 'rockstar', 'executive', 'pilot', 'builder'], 222),
    ('costumes-4-alchemist-entomologist-opera-bonsai-steampunk-scribe.jpg', ['alchemist', None, 'opera', 'bonsai', 'steampunk', 'scribe'], 222),
]
CANVAS_W, BOTTOM_MARGIN = 384, 10


def green(a):
    return (a[:, :, 1] > a[:, :, 0] + 25) & (a[:, :, 1] > a[:, :, 2] + 25)


def hood_width(rgba):
    """Width of the green hood: the 75th percentile of the green row-extents in the upper-middle of the picture."""
    a = rgba[:, :, :3].astype(int)
    m = green(a) & (rgba[:, :, 3] > 128)
    ys = np.where(m.any(axis=1))[0]
    y0, y1 = ys.min(), ys.max() + 1
    h = y1 - y0
    ext = []
    for y in range(y0 + int(.12 * h), y0 + int(.50 * h)):
        xs = np.where(m[y])[0]
        if len(xs) > 5:
            ext.append(xs.max() - xs.min())
    return float(np.percentile(ext, 75))


def segment(path, label_band):
    im = Image.open(path).convert('RGB')
    a = np.array(im).astype(int)
    light = a.min(axis=2) > 178                      # white page + the pale ground shadows
    lab, _ = ndi.label(light)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fg = ~np.isin(lab, list(border))
    comp, n = ndi.label(fg)
    objs = ndi.find_objects(comp)
    big, small = [], []
    for i, sl in enumerate(objs, 1):
        ar = int((comp[sl] == i).sum())
        (big if ar >= 12000 else small).append((i, sl, ar))
    big.sort(key=lambda b: b[1][1].start)
    groups = {b[0]: [b[0]] for b in big}
    centers = {b[0]: ndi.center_of_mass(comp == b[0]) for b in big}
    for i, sl, ar in small:
        if ar < 60:
            continue
        ys, xs = sl
        if ys.stop <= label_band:                    # Arabic label above the characters
            continue
        # nearest big component within 45 px
        best, bd = None, 1e9
        mask = comp == i
        dil = ndi.binary_dilation(mask, iterations=45)
        for b in big:
            if (dil & (comp == b[0])).any():
                cy, cx = centers[b[0]]
                d = abs(cx - (xs.start + xs.stop) / 2)
                if d < bd:
                    best, bd = b[0], d
        if best is not None:
            groups[best].append(i)
    return im, a, comp, big, groups


def main():
    os.makedirs(OUT, exist_ok=True)
    ref = np.array(Image.open(RES + 'r_mascot_happy.webp').convert('RGBA'))
    ref_hood = hood_width(ref)
    print('reference hood width', ref_hood)
    for fname, names, band in SHEETS:
        im, a, comp, big, groups = segment(BASE + fname, band)
        assert len(big) == len(names), (fname, len(big))
        cuts = []
        for b, name in zip(big, names):
            m = np.isin(comp, groups[b[0]])
            ys, xs = np.where(m)
            x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
            rgba = np.dstack([a.astype(np.uint8), (m * 255).astype(np.uint8)])
            img = Image.fromarray(rgba).crop((x0, y0, x1, y1))
            al = img.getchannel('A').filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
            img.putalpha(al)
            img = img.crop(img.getbbox())
            cuts.append((name, img, ref_hood / hood_width(np.array(img))))
        # the characters of one sheet are drawn at one scale; hats/headdresses hide the hood in some of them and would
        # fool the measurement, so the sheet's median scale is used for all of them
        k = float(np.median([c[2] for c in cuts]))
        print(fname[:12], 'sheet scale %.3f' % k, ['%.2f' % c[2] for c in cuts])
        for name, img, _ in cuts:
            if name is None:
                continue
            img.save(OUT + name + '.png')
            w, h = round(img.width * k), round(img.height * k)
            img = img.resize((w, h), Image.LANCZOS)
            cw = max(CANVAS_W, w + 16)
            ch = h + BOTTOM_MARGIN + 4
            canvas = Image.new('RGBA', (cw, ch), (0, 0, 0, 0))
            canvas.alpha_composite(img, ((cw - w) // 2, ch - BOTTOM_MARGIN - h))
            canvas.save(RES + 'r_costume_' + name + '.webp', quality=90, method=6)
            print('  ', name, canvas.size)


if __name__ == '__main__':
    main()
