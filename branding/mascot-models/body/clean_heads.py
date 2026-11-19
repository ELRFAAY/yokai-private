import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
SRC, OUT = sys.argv[1], sys.argv[2]
blink = Image.open(f"{SRC}/blink.webp").convert("RGBA")
# face regions to take from each mood (the hands are outside them); everything else comes from the hand-free blink head
REG = {
    "hurt":   [(102, 125, 292, 250), (166, 250, 244, 286)],
    "scared": [(102, 125, 280, 286)],
    "dizzy":  [(102, 125, 292, 286)],
    "sad":    [(102, 125, 284, 196), (168, 196, 250, 286)],
}
for n, rects in REG.items():
    mood = Image.open(f"{SRC}/{n}.webp").convert("RGBA")
    m = Image.new("L", (384, 384), 0); d = ImageDraw.Draw(m)
    for r in rects: d.rectangle(r, fill=255)
    m = m.filter(ImageFilter.GaussianBlur(3))
    a = (np.asarray(m).astype(float) / 255.0)[..., None]
    out = np.asarray(blink).astype(float) * (1 - a) + np.asarray(mood).astype(float) * a
    # the silhouette always stays the blink head's: no hands can appear
    out[..., 3] = np.asarray(blink)[..., 3]
    Image.fromarray(out.astype("uint8"), "RGBA").save(f"{OUT}/{n}.png")
sheet = Image.new("RGB", (4 * 384, 384), (60, 60, 60))
for i, n in enumerate(REG):
    im = Image.open(f"{OUT}/{n}.png"); bg = Image.new("RGBA", im.size, (60, 60, 60, 255)); bg.alpha_composite(im)
    sheet.paste(bg.convert("RGB"), (i * 384, 0))
sheet.save(f"{OUT}/contact_heads.png")
