"""Cuts the owner's 2026-11-21 sheets (rB faces, rD poses, rA2 tail wag, rC2 wave, p3 bed) into sprites for the home mascot.
Every frame keeps the sheet's own SHORT, WIDE body (nothing is drawn by hand). Needs Pillow, numpy, scipy.
Run from the repo root: python3 branding/tools/make_small_dragon_new_sheets.py
"""
import os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from make_small_dragon_fullbody import segment, cutout

BASE = 'branding/mascot-models/new-sheets-2026-11-21/'
CUT = 'branding/mascot-models/cutouts-new/'
RES = 'app/src/main/res/drawable-nodpi/'
CANVAS, MARGIN, TARGET = 384, 8, 262  # TARGET: sqrt(median blob area) in canvas px, same for all small-dragon sheets


def rows_then_x(blobs, rows):
    ys = sorted(b['box'][1] for b in blobs)
    h = (max(b['box'][3] for b in blobs) - min(ys)) / rows
    top = min(ys)
    return sorted(blobs, key=lambda b: (int((b['box'][1] - top + h * 0.25) // h), b['box'][0]))


def load(name, rows=1, merge_dil=12, min_area=8000):
    im, a, fg2, l2, blobs = segment(BASE + name, merge_dil=merge_dil, min_area=min_area)
    ob = rows_then_x(blobs, rows)
    pics = [cutout(a, fg2, l2, b) for b in ob]
    k = TARGET / np.sqrt(np.median([b['area'] for b in blobs]))
    return pics, k


def hood_x(pic):
    al = np.array(pic.getchannel('A')) > 128
    top = al[: max(1, int(al.shape[0] * 0.35))]
    xs = np.nonzero(top.any(axis=0))[0]
    return (xs.min() + xs.max()) / 2.0


def place(pic, k, align_hood=True):
    w, h = max(1, round(pic.width * k)), max(1, round(pic.height * k))
    lim = CANVAS - 2 * MARGIN
    if w > lim or h > lim:
        s = min(lim / w, lim / h)
        w, h = round(w * s), round(h * s)
        k = k * s
    pic = pic.resize((w, h), Image.LANCZOS)
    cx = hood_x(pic) if align_hood else w / 2
    x = round(CANVAS / 2 - cx)
    x = max(MARGIN, min(CANVAS - MARGIN - w, x))
    canvas = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(pic, (x, CANVAS - MARGIN - h))
    return canvas


def save(img, name):
    img.save(RES + name + '.webp', quality=92, method=6)
    print(name, img.size)


def run():
    os.makedirs(CUT, exist_ok=True)
    # rB 4x3: row1 neutral, sleepy, eyes closed, mouth flat; row2 smile, small open, big open, "o"; row3 looks
    rb, k = load('rB-small-face-frames-4x3-564642.jpg', rows=3)
    assert len(rb) == 12, len(rb)
    for i, p in enumerate(rb):
        p.save(CUT + 'rB_%02d.png' % i)
    save(place(rb[0], k), 'r_mascot_happy')
    save(place(rb[2], k), 'r_mascot_blink')
    save(place(rb[5], k), 'r_mascot_talk1')
    save(place(rb[6], k), 'r_mascot_talk2')
    # rD 3x3: seal, hands on hips (where were you), sad, open arms, tired, yawn, stretch, startled, proud
    rd, k = load('rD-small-new-poses-3x3-564644.jpg', rows=3)
    assert len(rd) == 9, len(rd)
    for i, p in enumerate(rd):
        p.save(CUT + 'rD_%02d.png' % i)
    save(place(rd[2], k), 'r_mascot_sad')
    save(place(rd[7], k), 'r_mascot_scared')
    save(place(rd[7], k), 'r_mascot_back')
    save(place(rd[1], k), 'r_mascot_where')
    save(place(rd[3], k), 'r_mascot_open')
    save(place(rd[4], k), 'r_mascot_tired')
    save(place(rd[5], k), 'r_mascot_yawn')
    save(place(rd[6], k), 'r_mascot_stretch')
    save(place(rd[8], k), 'r_mascot_proud')
    # rA2 tail wag, 5 frames
    ra, k = load('rA2-small-tail-wag-redo-564648.jpg')
    assert len(ra) == 5, len(ra)
    for i, p in enumerate(ra):
        save(place(p, k), 'r_mascot_wag%d' % (i + 1))
    # rC2 wave, 6 frames (hands touch the neighbours, so no merge dilation)
    rc, k = load('rC2-small-wave-redo-564647.jpg', merge_dil=1, min_area=3000)
    assert len(rc) == 6, len(rc)
    for i, p in enumerate(rc):
        save(place(rc[i], k), 'r_mascot_wave%d' % (i + 1))
    # p3 bed, in sheet order: 0 yawn, 1 rub eye, 2 walk to bed, 3 lie, 4 pull blanket, 5 asleep + zz (wide art: own aspect, 480 px wide)
    p3, _ = load('p3-small-bedtime-sleep-564637.jpg', rows=2, merge_dil=6)
    assert len(p3) == 6, len(p3)
    for name, idx in (('yawn', 0), ('rub', 1), ('walk', 2), ('lie', 3), ('cover', 4), ('sleep', 5)):
        p = p3[idx]
        w = 480
        h = round(p.height * w / p.width)
        p = p.resize((w, h), Image.LANCZOS)
        p.save(CUT + 'p3_%s.png' % name)
        p.save(RES + 'r_bed_%s.webp' % name, quality=92, method=6)
        print('r_bed_' + name, p.size)


if __name__ == '__main__':
    run()
