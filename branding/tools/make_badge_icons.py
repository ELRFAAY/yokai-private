"""Cuts BADGES-569614.jpg (4x4) into r_badge_*.webp (192x192, transparent). Run from repo root: python3 -I branding/tools/make_badge_icons.py"""
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
SRC = 'branding/mascot-models/new-sheets-2026-11-24/badges/BADGES-569614.jpg'
RES = 'app/src/main/res/drawable-nodpi/'
names = ['c1','c2','c3','c4','h1','h2','h3','h4','s1','s2','s3','s4','chest','chest_open','xp','lock']
im = Image.open(SRC).convert('RGB')
cw = im.width // 4
INSET = 10  # drop the thin grid lines between cells
for i, n in enumerate(names):
    r, c = divmod(i, 4)
    cell = np.asarray(im.crop((c*cw+INSET, r*cw+INSET, (c+1)*cw-INSET, (r+1)*cw-INSET))).astype(np.float32)
    mn = cell.min(axis=2)
    lab, k = ndi.label(mn >= 215)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    alpha = np.where(bg, np.clip((248 - mn) / 33.0, 0, 1), 1.0)
    rgba = np.dstack([cell, alpha * 255]).astype(np.uint8)
    img = Image.fromarray(rgba, 'RGBA')
    ys, xs = np.where(alpha > 0.1)
    img = img.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    s = 176 / max(img.size)
    img = img.resize((max(1, int(img.width*s)), max(1, int(img.height*s))), Image.LANCZOS)
    out = Image.new('RGBA', (192, 192), (0, 0, 0, 0))
    out.alpha_composite(img, ((192-img.width)//2, (192-img.height)//2))
    out.save(RES + 'r_badge_%s.webp' % n, quality=92, method=6)
print('ok')
