"""Cuts the small dragon's POINTING pose from the owner's sheet p4 (bottom middle cell, the dragon points to its right).
Same cutter and scale as make_small_dragon_new_sheets.py; nothing is drawn by hand.
Run from the repo root: python3 branding/tools/make_small_dragon_point.py
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import make_small_dragon_new_sheets as m

m.BASE = 'branding/mascot-models/new-sheets-2026-11-21/'
pics, k = m.load('p4-small-back-fly-land-sit-point-think-564636.jpg', rows=2)
assert len(pics) == 6, len(pics)
os.makedirs(m.CUT, exist_ok=True)
for i, p in enumerate(pics):
    p.save(m.CUT + 'p4_%02d.png' % i)
m.save(m.place(pics[4], k), 'r_mascot_point')
