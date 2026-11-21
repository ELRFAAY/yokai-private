"""Cuts the small dragon (full body) out of the owner's model sheets 16-19 and builds the home-screen mascot sprites.
Needs Pillow, numpy, scipy. Run from the repo root: python3 branding/tools/make_small_dragon_fullbody.py
"""
from PIL import Image, ImageFilter
import numpy as np, colorsys
from scipy import ndimage as ndi

def mx_dark(a):
    return a.max(axis=2) < 90

def _box(shape, k):
    m = np.zeros(shape[:2], bool); m[k[1]:k[3], k[0]:k[2]] = True; return m

def segment(path, merge_dil=12, min_area=20000, keep=()):
    # keep: boxes (x0,y0,x1,y1) in sheet pixels whose components are artwork, never labels (e.g. the impact star)
    im=Image.open(path).convert('RGB')
    a=np.array(im).astype(int)
    white=(a[:,:,0]>225)&(a[:,:,1]>225)&(a[:,:,2]>225)
    lab,_=ndi.label(white)
    border=set(np.unique(np.concatenate([lab[0,:],lab[-1,:],lab[:,0],lab[:,-1]])))-{0}
    bg=np.isin(lab,list(border)); fg=~bg
    # the impact star's white inside is open to the page, so it was cut away: close and fill its outline
    for k in keep:
        dk, nd = ndi.label(mx_dark(a) & _box(a.shape, k))
        star = np.zeros(dk.shape, bool)
        for j, sl in enumerate(ndi.find_objects(dk), 1):  # only the wide outline piece, not the stick tip / motion lines
            if sl[1].stop - sl[1].start > 100 and (dk[sl] == j).sum() > 300:
                star |= dk == j
        closed = ndi.binary_closing(np.pad(star, 12), structure=np.ones((3, 3)), iterations=7)[12:-12, 12:-12]
        fg = fg | (ndi.binary_fill_holes(closed) & _box(a.shape, k))
    # 1) remove the Arabic/English labels: dark, unsaturated, small components
    l1,n1=ndi.label(ndi.binary_dilation(fg,iterations=3))
    mx=a.max(axis=2); mn=a.min(axis=2); sat=(mx-mn)/np.maximum(mx,1)
    text=np.zeros_like(fg)
    for i,sl in enumerate(ndi.find_objects(l1),1):
        m=(l1==i)&fg
        area=m.sum()
        if area==0: continue
        ys,xs=sl; h=ys.stop-ys.start; w=xs.stop-xs.start
        if area<9000 and sat[m].mean()<0.25 and mx[m].mean()<110 and h<90:
            if any(xs.start<k[2] and xs.stop>k[0] and ys.start<k[3] and ys.stop>k[1] for k in keep):
                continue
            text|=m
    fg2=fg&~ndi.binary_dilation(text,iterations=2)
    # 2) merge each pose with its floating decorations
    l2,_=ndi.label(ndi.binary_dilation(fg2,iterations=merge_dil))
    blobs=[]
    for i,sl in enumerate(ndi.find_objects(l2),1):
        m=(l2==i)&fg2
        ar=int(m.sum())
        if ar>=min_area:
            ys,xs=sl; blobs.append(dict(i=i,box=(xs.start,ys.start,xs.stop,ys.stop),area=ar))
    return im,a,fg2,l2,blobs

def cutout(a,fg2,l2,blob):
    x0,y0,x1,y1=blob['box']
    m=fg2&(l2==blob['i'])
    rgba=np.dstack([a.astype(np.uint8),(m*255).astype(np.uint8)])
    img=Image.fromarray(rgba).crop((x0,y0,x1,y1))
    al=img.getchannel('A').filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    img.putalpha(al)
    return img.crop(img.getbbox())

def order(blobs,split=380):
    return sorted(blobs,key=lambda b:(b['box'][1]//split,b['box'][0]))

import os
BASE = 'branding/mascot-models/'
OUT = 'branding/mascot-models/cutouts/'
RES = 'app/src/main/res/drawable-nodpi/'
SHEETS = {
    '19-small-dragon-fullbody-moods-love-meh-bored-shock-559307.jpg': ['love', 'cool', 'meh', 'bored', 'disgust', 'shock', 'amazed'],
    '18-small-dragon-fullbody-moods-happy-blink-sad-scared-dizzy-559306.jpg': ['happy', 'blink', 'annoyed', 'sad', 'scared', 'dizzy'],
    '17-small-dragon-fullbody-wave-cheer-hourglass-stretch-559302.jpg': ['wave', 'cheer', 'hourglass', 'stretch'],
}
STICK = ('16-wise-dragon-stick-scene-559301.jpg',
         ['wise_walk', 'wise_raise', 'wise_hit', 'small_hit', 'small_carried', 'wise_walk_with_small'])
# home mascot mood -> cut-out file. BACK has no back view in the new sheets: the \"shocked\" pose is used (flying away fast).
# sheet 16: the black-outlined impact star under the wise dragon's stick looked like a label to the text filter
STICK_KEEP = [(1150, 270, 1360, 362)]
MOODS = {'happy': 'dragon_happy', 'blink': 'dragon_blink', 'hurt': 'stick_small_hit', 'sad': 'dragon_sad',
         'scared': 'dragon_scared', 'dizzy': 'dragon_dizzy', 'back': 'dragon_shock'}
CANVAS, SCALE, MARGIN = 384, 1.18, 8

def run():
    os.makedirs(OUT, exist_ok=True)
    jobs = [(f, n, 'dragon_') for f, n in SHEETS.items()] + [(STICK[0], STICK[1], 'stick_')]
    for f, names, prefix in jobs:
        im, a, fg2, l2, blobs = segment(BASE + f, keep=STICK_KEEP if prefix == 'stick_' else ())
        ob = order(blobs)
        assert len(ob) == len(names), (f, len(ob))
        for b, n in zip(ob, names):
            cutout(a, fg2, l2, b).save(OUT + prefix + n + '.png')
    for mood, src in MOODS.items():
        pic = Image.open(OUT + src + '.png')
        s = SCALE
        w, h = round(pic.width * s), round(pic.height * s)
        if w > CANVAS - 2 * MARGIN or h > CANVAS - 2 * MARGIN:
            k = min((CANVAS - 2 * MARGIN) / w, (CANVAS - 2 * MARGIN) / h)
            w, h = round(w * k), round(h * k)
        pic = pic.resize((w, h), Image.LANCZOS)
        canvas = Image.new('RGBA', (CANVAS, CANVAS), (0, 0, 0, 0))
        canvas.alpha_composite(pic, ((CANVAS - w) // 2, CANVAS - MARGIN - h))
        canvas.save(RES + 'r_mascot_' + mood + '.webp', quality=92, method=6)
        print(mood, '<-', src, (w, h))

if __name__ == '__main__':
    run()
