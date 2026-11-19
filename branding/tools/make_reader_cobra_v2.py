"""Cuts the reader-cobra poses out of branding/mascot-models/14-reader-cobra-peek-sleepy-559280.jpg
into transparent lossless WebPs (drawable-nodpi). Usage: python make_reader_cobra_v2.py <sheet.jpg> <res dir>
Poses are found automatically (connected components on the white background); see POSES for the names."""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

SHEET, RES = sys.argv[1], sys.argv[2]

# (box x0, y0, x1, y1 found by segmentation, name, trim_left_px)  trim_left removes the curved hood edge on
# the peek poses so the flat cut of the neck sits exactly on the screen edge.
POSES = [
    ((20, 27, 275, 330), 'r_cobra_peek_normal', 24),
    ((305, 25, 572, 330), 'r_cobra_peek_look', 24),
    ((595, 25, 864, 331), 'r_cobra_peek_laugh', 24),
    ((882, 27, 1180, 330), 'r_cobra_peek_point', 24),
    ((41, 359, 300, 635), 'r_cobra_yawn', 0),
    ((449, 365, 720, 635), 'r_cobra_rub', 0),
    ((849, 365, 1143, 635), 'r_cobra_bored', 0),
    ((37, 647, 281, 881), 'r_cobra_nod1', 0),
    ((289, 647, 535, 881), 'r_cobra_nod2', 0),
]

src = Image.open(SHEET).convert('RGB')
for (x0, y0, x1, y1), name, trim in POSES:
    im = src.crop((x0 - 6, y0 - 6, x1 + 6, y1 + 6))
    pad = Image.new('RGB', (im.width + 12, im.height + 12), (255, 255, 255)); pad.paste(im, (6, 6)); im = pad
    a = np.asarray(im).astype(np.int16)
    minc = a.min(axis=2)
    lab, _ = ndi.label(minc >= 205)
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    bg = np.isin(lab, list(border))
    alpha = np.where(bg, 0, 255).astype(np.uint8)
    ring = ndi.binary_dilation(bg, iterations=1) & ~bg
    soft = np.clip((255 - minc) * 255 // 110, 0, 255).astype(np.uint8)
    alpha = np.where(ring, np.minimum(alpha, soft), alpha).astype(np.uint8)
    # keep the biggest piece plus anything within 12 px of it (motion lines etc.), drop stray dots
    lab2, n2 = ndi.label(alpha > 0)
    sizes = ndi.sum(alpha > 0, lab2, range(1, n2 + 1))
    big = lab2 == (int(np.argmax(sizes)) + 1)
    near = ndi.binary_dilation(big, iterations=12)
    keep = np.isin(lab2, [i + 1 for i in range(n2) if sizes[i] > 25 and near[lab2 == i + 1].any()])
    alpha = np.where(keep, alpha, 0).astype(np.uint8)
    rgba = np.dstack([a.astype(np.uint8), alpha])
    ys, xs = np.nonzero(alpha)
    rgba = rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    out = Image.fromarray(rgba, 'RGBA')
    if trim:
        out = out.crop((trim, 0, out.width, out.height))
    out = out.resize((int(out.width * 1.4), int(out.height * 1.4)), Image.LANCZOS)
    out.save(f'{RES}/drawable-nodpi/{name}.webp', 'WEBP', lossless=True, quality=100, method=6)
    print(name, out.size)
