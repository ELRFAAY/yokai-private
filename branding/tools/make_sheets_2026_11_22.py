"""Cuts the owner's 2026-11-22 sheets (branding/mascot-models/new-sheets-2026-11-22/) into sprites.
WISE (hat width normalised per sheet, coil centre fixed, 640 px canvas, bottom aligned so frames of a sheet never jump):
  H1 5x3 stick hit (15), G 4x2 walk (8, 565706), I2-try3 4x2 wise drags the dazed small one (8).
SMALL (same scale rules as the home dragon, 384 px canvas): J 3x2 peek/notice/hit/squash/compress/bounce, J3b 3 shocked, E2 4x2 drop-in.
Run from the repo root: python3 branding/tools/make_sheets_2026_11_22.py
"""
import os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import make_small_dragon_new_sheets as m

m.BASE = 'branding/mascot-models/new-sheets-2026-11-22/'
m.CUT = 'branding/mascot-models/cutouts-2026-11-22/'
RES = m.RES
WCANVAS, WMARGIN, HAT = 640, 8, 240.0


def cut(name, rows, expect, merge_dil=12, min_area=8000):
    pics, k = m.load(name, rows=rows, merge_dil=merge_dil, min_area=min_area)
    assert len(pics) == expect, (name, len(pics), expect)
    return pics, k


def hat_w(img):
    a = np.array(img.getchannel('A')) > 128
    xs = np.where(a[: max(1, int(a.shape[0] * 0.22))].any(axis=0))[0]
    return xs.max() - xs.min()


def coil_x(img):
    a = np.array(img.getchannel('A')) > 128
    lo = a[int(a.shape[0] * 0.6):]
    xs = np.nonzero(lo.any(axis=0))[0]
    return (xs.min() + xs.max()) / 2.0


def place_wise(pic, k):
    w, h = max(1, round(pic.width * k)), max(1, round(pic.height * k))
    pic = pic.resize((w, h), Image.LANCZOS)
    assert w <= WCANVAS - 2 * WMARGIN and h <= WCANVAS - 2 * WMARGIN, (w, h)
    x = round(WCANVAS / 2 - coil_x(pic))
    x = max(WMARGIN, min(WCANVAS - WMARGIN - w, x))
    c = Image.new('RGBA', (WCANVAS, WCANVAS), (0, 0, 0, 0))
    c.alpha_composite(pic, (x, WCANVAS - WMARGIN - h))
    return c


def save(img, name):
    img.save(RES + name + '.webp', quality=92, method=6)
    print(name, img.size)


def run():
    os.makedirs(m.CUT, exist_ok=True)
    # H1: 15 frames (row1 neutral, hand on hip, cane up, cane behind head, cane horizontal; row2 cane on shoulder, swing streaks,
    # swing down mouth open, hold beard flying, cane across; row3 impact burst, beard flying, cane trembling, cane raised, neutral)
    h1, _ = cut('H1-wise-stick-hit-5x3-565684.jpg', 3, 15, merge_dil=6)
    k = HAT / hat_w(h1[0])
    for i, p in enumerate(h1):
        p.save(m.CUT + 'H1_%02d.png' % i)
        # r_h1_* webp no longer written: the H3 scene (make_h3_cells.py) replaced the H1 hit
    # G walk (565706): 8 frames, wise 3/4 front, frame 8 is a coiled rest pose (kept, not used in the loop)
    g, _ = cut('G-wise-walk-4x2-USE-565706.jpg', 2, 8, merge_dil=6)
    k = HAT / hat_w(g[0])
    for i, p in enumerate(g):
        p.save(m.CUT + 'G_%02d.png' % i)
        save(place_wise(p, k), 'r_g_%02d' % i)
    # I2-try3: wise (true profile facing RIGHT) drags the dazed small dragon, 8 frames
    i2, _ = cut('I2-try3-wise-profile-right-565703.jpg', 2, 8, merge_dil=6)
    k = HAT / hat_w(i2[0])
    for i, p in enumerate(i2):
        p.save(m.CUT + 'I2_%02d.png' % i)
        # r_i2_* webp no longer written: the wise one leaves alone with the G walk frames
    # J: small dragon 3x2: 0 peek, 1 notice, 2 shock with cane tip (skipped, J3b instead), 3 HIT squash, 4 compress, 5 bounce
    j, kj = cut('J-small-hit-3x2-565687.jpg', 2, 6, merge_dil=6)
    for i, p in enumerate(j):
        p.save(m.CUT + 'J_%02d.png' % i)
    for i in (0, 1, 3, 4, 5):
        save(m.place(j[i], kj), 'r_j_%d' % i)
    # J3b: shocked, no cane: still, shake left, shake right
    j3, k3 = cut('J3b-small-shocked-no-cane-565702.jpg', 1, 3, merge_dil=6)
    for i, p in enumerate(j3):
        p.save(m.CUT + 'J3b_%d.png' % i)
        save(m.place(p, k3), 'r_j3b_%d' % i)
    # E2: drop-in / landing: 0 falling arms up, 1 falling hood flapping, 2 squash + dust, 3 max squash, 4 bounce stretched dizzy,
    # 5 small bounce relieved, 6 settling shaking head, 7 sitting waving
    e, ke = cut('E2-small-dropin-landing-4x2-565700.jpg', 2, 8, merge_dil=12)
    for i, p in enumerate(e):
        p.save(m.CUT + 'E2_%d.png' % i)
        save(m.place(p, ke), 'r_e2_%d' % i)


if __name__ == '__main__':
    run()
