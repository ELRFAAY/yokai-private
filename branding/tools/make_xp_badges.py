"""Cuts the 16 XP diamonds + the coin from branding/third-party/grand-gin-rummy-xp-sheet-570628.jpg (third-party art, (c) GameDuell, used
privately at the owner's decision) and recolors them: r_xpb_1..12 (tier by dragon level, GOLD = level 12) and r_coin_gold.
Run from the repo root: python3 -I branding/tools/make_xp_badges.py"""
import numpy as np
from PIL import Image
from scipy import ndimage as ndi
SRC = 'branding/third-party/grand-gin-rummy-xp-sheet-570628.jpg'
RES = 'app/src/main/res/drawable-nodpi/'
im = np.asarray(Image.open(SRC).convert('RGB')).astype(np.float32)
bgc = im[5, 5]
mask = (np.abs(im - bgc).sum(axis=2) > 40)
mask[:, :560] = False
mask = ndi.binary_closing(mask, iterations=3)
lab, n = ndi.label(mask)
boxes = [(o[0].start, o[1].start, o[0].stop, o[1].stop) for o in ndi.find_objects(lab) if (o[0].stop-o[0].start) > 100 and (o[1].stop-o[1].start) > 100]
boxes.sort(key=lambda b: (round(b[0] / 100), b[1]))
coins, dias = boxes[:4], boxes[4:]
assert len(coins) == 4 and len(dias) == 16
# tier colours: grey, bronze, green, teal, blue, indigo, purple, magenta, red, orange, amber, GOLD
COLORS = [(150,160,172),(176,122,74),(67,160,71),(0,150,136),(47,128,237),(83,82,200),(142,68,173),(214,60,150),(229,57,53),(245,124,0),(255,167,38),(255,196,20)]
ORDER = [0,1,2,3,4,5,6,8,9,10,11,7]   # level 12 = the most ornate one (round plate)

def cut(b):
    y0, x0, y1, x1 = b
    pad = 6
    crop = im[max(y0-pad,0):y1+pad, max(x0-pad,0):x1+pad]
    d = np.abs(crop - bgc).sum(axis=2)
    lab2, _ = ndi.label(d < 30)
    border = set(np.unique(np.concatenate([lab2[0], lab2[-1], lab2[:, 0], lab2[:, -1]]))) - {0}
    bg = np.isin(lab2, list(border))
    alpha = np.where(bg, np.clip((d - 20) / 25.0, 0, 1), 1.0)
    return crop, alpha

def tint(crop, alpha, col):
    L = crop.mean(axis=2) / 255.0
    L = (L - 0.12) / 0.78
    L = np.clip(L, 0, 1)
    c = np.array(col, dtype=np.float32)
    t = L[..., None]
    low = c * (t / 0.55)
    high = c + (255 - c) * ((t - 0.55) / 0.45) * 0.85
    rgb = np.where(t < 0.55, low, high)
    rgba = np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8)
    return Image.fromarray(rgba, 'RGBA')

def finish(img, name):
    ys, xs = np.where(np.asarray(img)[..., 3] > 10)
    img = img.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    s = 180 / max(img.size)
    img = img.resize((max(1, int(img.width * s)), max(1, int(img.height * s))), Image.LANCZOS)
    out = Image.new('RGBA', (192, 192), (0, 0, 0, 0))
    out.alpha_composite(img, ((192 - img.width) // 2, (192 - img.height) // 2))
    out.save(RES + name, quality=94, method=6)

for lvl in range(12):
    crop, alpha = cut(dias[ORDER[lvl]])
    finish(tint(crop, alpha, COLORS[lvl]), 'r_xpb_%d.webp' % (lvl + 1))
crop, alpha = cut(coins[0])
finish(tint(crop, alpha, (255, 190, 20)), 'r_coin_gold.webp')
print('ok')
