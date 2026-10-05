"""Shared pieces: Praat pitch/intensity, the prototype of plan step 6 (segmentation + tone templates), reporting."""
import re
from pathlib import Path

import numpy as np
import parselmouth

ROOT = Path(__file__).resolve().parents[2]
# Converted audio, alignments and caches; under the git-ignored build directory.
WORK = ROOT / "build" / "sentence_feasibility"

HOP = 0.01

def analyse(path_or_sound):
    """f0 (NaN unvoiced) and intensity (dB) on a common 10 ms grid."""
    snd = path_or_sound if isinstance(path_or_sound, parselmouth.Sound) else parselmouth.Sound(str(path_or_sound))
    n = int(snd.duration / HOP)
    t = np.arange(n) * HOP
    pitch = snd.to_pitch_ac(time_step=HOP, pitch_floor=60, pitch_ceiling=500)
    f0 = np.array([pitch.get_value_at_time(x) for x in t])
    inten = snd.to_intensity(minimum_pitch=100, time_step=HOP)
    db = np.array([inten.get_value(x) for x in t])
    db[np.isnan(db)] = np.nanmin(db) if np.any(~np.isnan(db)) else 0
    return f0, db

def levels(f0, lo, hi):
    return 1 + 4 * np.log(f0 / lo) / np.log(hi / lo)

def runs(mask):
    out, i, n = [], 0, len(mask)
    while i < n:
        if mask[i]:
            j = i
            while j < n and mask[j]: j += 1
            out.append([i, j]); i = j
        else: i += 1
    return out

def segment(f0, db, n):
    """Plan step 6: split the voiced region at unvoiced gaps and energy dips, forcing n segments."""
    segs = [r for r in runs(~np.isnan(f0)) if r[1] - r[0] >= 5]
    if not segs: return None
    while len(segs) > n:  # merge across the shortest gap
        k = min(range(len(segs) - 1), key=lambda i: segs[i + 1][0] - segs[i][1])
        segs[k:k + 2] = [[segs[k][0], segs[k + 1][1]]]
    while len(segs) < n:  # split the longest run at its deepest interior energy dip
        k = max(range(len(segs)), key=lambda i: segs[i][1] - segs[i][0])
        a, b = segs[k]
        if b - a < 10: return None
        m = max(3, (b - a) // 5)
        cut = a + m + int(np.argmin(db[a + m:b - m]))
        segs[k:k + 1] = [[a, cut], [cut, b]]
    return segs

def shape(lv, points=8, skip=0.15):
    """Voiced levels of one syllable resampled to a fixed number of points; onset transition skipped."""
    v = lv[~np.isnan(lv)]
    if len(v) < 3: return None
    v = v[int(len(v) * skip):]
    if len(v) < 2: return None
    return np.interp(np.linspace(0, len(v) - 1, points), np.arange(len(v)), v)

def tpl(*pts, points=8):
    return np.interp(np.linspace(0, len(pts) - 1, points), np.arange(len(pts)), pts)

TEMPLATES = {1: [tpl(5, 5)], 2: [tpl(3, 3, 5), tpl(2.5, 4.5)], 3: [tpl(2, 1), tpl(2, 1, 1, 3), tpl(1.5, 1.5)],
             4: [tpl(5, 1), tpl(5, 4, 2)], 0: [tpl(3, 2.5)]}

def classify(lv, allow_neutral=True, height_w=0.5):
    s = shape(lv)
    if s is None: return 3  # no usable pitch: creak, treat as low
    best, bt = 1e9, None
    for tone, ts in TEMPLATES.items():
        if tone == 0 and not allow_neutral: continue
        for t in ts:
            dh = s.mean() - t.mean()
            d = np.sqrt(np.mean((s - t - dh) ** 2) + (height_w * dh) ** 2)
            if d < best: best, bt = d, tone
    return bt

def parse_name(name):
    return [(m.group(1), int(m.group(2)) % 5) for m in re.finditer(r'([a-zü]+)([0-5])', name)]

def confusion(pairs, labels=(1, 2, 3, 4, 0)):
    idx = {l: i for i, l in enumerate(labels)}
    m = np.zeros((len(labels), len(labels)), int)
    for t, p in pairs: m[idx[t], idx[p]] += 1
    lines = ['true\\pred ' + ' '.join(f'{l:>5}' for l in labels) + '   recall']
    for l in labels:
        row = m[idx[l]]
        lines.append(f'   T{l}     ' + ' '.join(f'{x:>5}' for x in row) + f'   {row[idx[l]] / max(1, row.sum()):.0%}  (n={row.sum()})')
    lines.append(f'accuracy {np.trace(m) / max(1, m.sum()):.1%} over {m.sum()} syllables')
    return '\n'.join(lines)
