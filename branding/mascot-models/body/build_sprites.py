import sys, numpy as np
from PIL import Image
B, RES, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SC = 0.91
def hood_geom(im):
    a = np.asarray(im.getchannel("A")) > 128
    top = np.where(a.any(axis=1))[0].min()
    rows = a[top + 12: top + 70]          # hood upper part: no hands there
    xs = np.where(rows.any(axis=0))[0]
    return top, (xs.min() + xs.max()) / 2.0
def compose(body, ghost_cut, head=None):
    W, H = 560, 560
    c = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    b = body.copy()
    btop, bcx = hood_geom(body)
    if head is not None:
        arr = np.asarray(b).copy(); arr[:ghost_cut, :, 3] = 0; b = Image.fromarray(arr)
    ox, oy = 60, 60
    c.alpha_composite(b, (ox, oy))
    if head is not None:
        h = head.resize((int(384 * SC), int(384 * SC)), Image.LANCZOS)
        htop, hcx = hood_geom(head)
        px = int(round(ox + bcx - hcx * SC)); py = int(round(oy + btop - htop * SC))
        tmp = Image.new("RGBA", (W, H), (0, 0, 0, 0)); tmp.paste(h, (px, py), h)
        c.alpha_composite(tmp)
    return c
tr = Image.open(B + "tailright.png").convert("RGBA")
tl = Image.open(B + "tailleft.png").convert("RGBA")
# tail-left pose: scale to the same hood width as tail-right, then align by hood in compose()
def hood_w(im):
    a = np.asarray(im.getchannel("A")) > 128
    top = np.where(a.any(axis=1))[0].min(); xs = np.where(a[top + 40: top + 110].any(axis=0))[0]
    return xs.max() - xs.min()
k = hood_w(tr) / hood_w(tl)
tl = tl.resize((int(tl.width * k), int(tl.height * k)), Image.LANCZOS)
print("tailleft scale", k)
sprites = {"happy": compose(tr, 0)}
for n in ["hurt", "scared", "dizzy", "sad", "blink"]:
    head = Image.open(f"{RES}/r_mascot_{n}.webp").convert("RGBA")
    sprites[n] = compose(tr, 236, head)
# union bbox -> one common square crop so the snake never jumps between sprites
bb = None
for s in sprites.values():
    b = s.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox()
    bb = b if bb is None else (min(bb[0], b[0]), min(bb[1], b[1]), max(bb[2], b[2]), max(bb[3], b[3]))
w, h = bb[2] - bb[0], bb[3] - bb[1]; side = max(w, h) + 8
x0 = bb[0] - (side - w) // 2; y0 = bb[1] - (side - h) // 2
print("union", bb, "side", side)
for n, s in sprites.items():
    s.crop((x0, y0, x0 + side, y0 + side)).resize((448, 448), Image.LANCZOS).save(f"{OUT}/{n}.png")
# contact sheet
names = list(sprites)
sheet = Image.new("RGB", (448 * 4, 448 * 2), (28, 28, 28))
for i, n in enumerate(names):
    im = Image.open(f"{OUT}/{n}.png"); bg = Image.new("RGBA", im.size, (28, 28, 28, 255)); bg.alpha_composite(im)
    sheet.paste(bg.convert("RGB"), ((i % 4) * 448, (i // 4) * 448))
sheet.save(f"{OUT}/contact.png")
