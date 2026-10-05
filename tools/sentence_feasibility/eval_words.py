"""Prototype of plan step 6 on the bundled word samples: segmentation, tone templates, confusion matrix."""
import numpy as np
from tonelib import *

items = []
for wav in sorted((ROOT / 'app' / 'src' / 'main' / 'assets' / 'samples').glob('*.wav')):
    f0, db = analyse(wav)
    items.append((wav.stem, tuple(t for _, t in parse_name(wav.stem)), f0, db))

# All one speaker, so the range is pooled over the whole set.
lo, hi = np.percentile(np.concatenate([f0[~np.isnan(f0)] for _, _, f0, _ in items]), [5, 95])
print(f'{len(items)} clips, range {lo:.0f}-{hi:.0f} Hz')

pairs, wrong, right = [], [], 0
for name, truth, f0, db in items:
    lv = levels(f0, lo, hi)
    segs = segment(f0, db, len(truth))
    if segs is None:
        wrong.append(f'{name}:unsegmented'); continue
    pred = tuple(classify(lv[a:b], allow_neutral=(i > 0)) for i, (a, b) in enumerate(segs))
    pairs += list(zip(truth, pred)); right += truth == pred
    if truth != pred: wrong.append(f'{name}->{"".join(map(str, pred))}')
print(f'whole item right {right}/{len(items)}')
print(confusion(pairs))
print('wrong:', ' '.join(wrong))
