"""Cuts the owner's second batch of 2026-11-21 sheets (branding/mascot-models/new-sheets-2026-11-21b/) into sprites.
S1 wise dragon (back, 3/4, side, talking, squint, pointing), S2 small dragon (back, fists x2, side, tongue, cheer),
S3 small dragon (top/back views for flying, curled up, front), S4 small dragon emotions (shock, dizzy x2, headache, scared, crying).
Same scale and canvas rules as make_small_dragon_new_sheets.py. Run from the repo root.
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import make_small_dragon_new_sheets as m

m.BASE = 'branding/mascot-models/new-sheets-2026-11-21b/'
m.CUT = 'branding/mascot-models/cutouts-new-b/'
FILES = {
    'S1': 'S1-wise-back-side-talk-squint-point-3x2-565053.jpg',
    'S2': 'S2-small-back-fists-side-tongue-cheer-3x2-565054.jpg',
    'S3': 'S3-small-topdown-flying-curled-front-3x2-565055.jpg',
    'S4': 'S4-small-emotions-shock-dizzy-headache-scared-cry-3x2-565056.jpg',
}
MERGE = {'S1': 12, 'S2': 12, 'S3': 12, 'S4': 22}  # S4: the dizzy stars float above the head, merge them in


def cut(key):
    pics, k = m.load(FILES[key], rows=2, merge_dil=MERGE[key])
    assert len(pics) == 6, (key, len(pics))
    os.makedirs(m.CUT, exist_ok=True)
    for i, p in enumerate(pics):
        p.save(m.CUT + '%s_%d.png' % (key, i))
    return pics, k


def run():
    p4, k4 = cut('S4')   # 0 shock, 1 dizzy, 2 dizzy shake, 3 headache, 4 scared, 5 crying
    p2, k2 = cut('S2')   # 0 back, 1 fist, 2 fist, 3 side, 4 tongue, 5 cheer
    p3, k3 = cut('S3')   # 0-3 top/back views, 4 curled up, 5 front arms out
    p1, k1 = cut('S1')   # wise: 0 back, 1 3/4, 2 side, 3 talking, 4 squint, 5 pointing
    m.save(m.place(p4[1], k4), 'r_mascot_dizzy')
    m.save(m.place(p4[3], k4), 'r_mascot_hurt')
    m.save(m.place(p4[4], k4), 'r_mascot_scared')
    m.save(m.place(p4[5], k4), 'r_mascot_sad')
    m.save(m.place(p4[0], k4), 'r_mascot_shock')
    m.save(m.place(p3[2], k3, align_hood=True), 'r_mascot_back')
    m.save(m.place(p2[5], k2), 'r_mascot_cheer')
    m.save(m.place(p2[4], k2), 'r_mascot_tongue')
    m.save(m.place(p2[1], k2), 'r_mascot_fist1')
    m.save(m.place(p2[2], k2), 'r_mascot_fist2')
    # S1's wise poses are only kept as cut-outs: every wise dragon sprite comes from p5 so his proportions never mix


def run_p5():
    """The wise dragon's main sheet p5 (4x3): calm, eyes shut, hit (stick dropped), crying, scared, dizzy, laughing,
    finger up (advice), angry, thinking, asleep, annoyed. Scaled so his hat is about as wide as the small dragon's hood."""
    import numpy as np
    from PIL import Image
    m.BASE = 'branding/mascot-models/new-sheets-2026-11-21/'
    pics, k = m.load('p5-wise-main-sheet-564640.jpg', rows=3, merge_dil=12)
    assert len(pics) == 12, len(pics)
    os.makedirs(m.CUT, exist_ok=True)
    for i, p in enumerate(pics):
        p.save(m.CUT + 'p5_%02d.png' % i)

    def hat_w(img):
        a = np.array(img.getchannel('A')) > 128
        xs = np.where(a[: int(a.shape[0] * 0.22)].any(axis=0))[0]
        return xs.max() - xs.min()

    k = 240.0 / hat_w(pics[0])  # his hat as wide as the small dragon's hood (about 250 px on the 384 px canvas)
    names = {0: 'r_sage_happy', 1: 'r_sage_blink', 2: 'r_sage_hurt', 3: 'r_sage_sad', 4: 'r_sage_scared', 5: 'r_sage_dizzy',
             6: 'r_sage2_laugh', 7: 'r_sage2_point', 8: 'r_sage2_angry', 9: 'r_sage2_think'}
    for i, name in names.items():
        m.save(m.place(pics[i], k), name)


if __name__ == '__main__':
    run()
    run_p5()
