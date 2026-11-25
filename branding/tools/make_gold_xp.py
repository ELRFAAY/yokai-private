"""Recolors the animated XP icon (r_xp_0..10, blue-violet) to GOLD -> r_xpg_0..10 (same alpha). Run from the repo root: python3 -I branding/tools/make_gold_xp.py"""
import numpy as np
from PIL import Image
D = 'app/src/main/res/drawable-nodpi/'
for i in range(11):
    im = Image.open(D + f'r_xp_{i}.webp').convert('RGBA')
    a = np.asarray(im).astype(np.float32) / 255
    rgb, al = a[..., :3], a[..., 3]
    hsv = np.asarray(Image.fromarray((rgb * 255).astype(np.uint8)).convert('HSV')).astype(np.float32) / 255
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    h = np.full_like(h, 42 / 360.0)                      # gold hue
    s = np.clip(s * 2.6 + 0.18, 0, 0.95)                 # richer
    v = np.clip(v * 0.82 + 0.20 + 0.15 * (v ** 2), 0, 1)  # warmer lights, keep the dark centre readable
    out = np.dstack([h, s, v])
    rgb2 = np.asarray(Image.fromarray((out * 255).astype(np.uint8), 'HSV').convert('RGB')).astype(np.float32) / 255
    res = np.dstack([rgb2, al])
    Image.fromarray((res * 255).astype(np.uint8), 'RGBA').save(D + f'r_xpg_{i}.webp', quality=95, method=6)
print('ok')
