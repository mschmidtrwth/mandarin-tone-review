"""Tone recognition on the aligned graded-reader chapters; see README.md for what E1 to E5 measure."""
import os, numpy as np, collections
from tonelib import *
from data import load
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.model_selection import GroupKFold

chapters = load()
print('\nchapters', len(chapters), 'books', len({c['book'] for c in chapters}))

# ---------- E1: plan's template classifier on aligner-given syllable boundaries
pairs = []; perbook = collections.defaultdict(list)
for c in chapters:
    lv = levels(c['f0'], c['lo'], c['hi'])
    for ph in c['phrases']:
        for s in ph:
            if s['amb']: continue
            p = classify(lv[s['a']:s['b']]); pairs.append((s['tone'], p)); perbook[c['book']].append(s['tone'] == p)
print('\n===== E1: isolated-tone templates, syllable boundaries GIVEN (forced aligner)')
print(confusion(pairs))
print('per book:', ' '.join(f'{np.mean(v):.0%}' for v in perbook.values()))
maj = collections.Counter(t for t, _ in pairs); print('label distribution', dict(maj), f'-> always guessing T4 scores {maj[4] / len(pairs):.0%}')

# ---------- E2/E3: plan's segmentation (voiced runs + energy dips, forced count), then classify
bins = [(1, 1), (2, 2), (3, 4), (5, 7), (8, 12), (13, 99)]
loc = collections.defaultdict(list); e2e = collections.defaultdict(list); natural = collections.defaultdict(list)
pairs3 = []
for c in chapters:
    lv = levels(c['f0'], c['lo'], c['hi'])
    for ph in c['phrases']:
        n = len(ph); a0, b0 = max(0, ph[0]['a'] - 5), ph[-1]['b']
        f0 = c['f0'][a0:b0]; db = c['db'][a0:b0]
        b = next(k for k in bins if k[0] <= n <= k[1])
        natural[b].append(len([r for r in runs(~np.isnan(f0)) if r[1] - r[0] >= 5]) == n)
        segs = segment(f0, db, n)
        for i, s in enumerate(ph):
            if segs is None: loc[b].append(False); e2e[b].append(False); continue
            x, y = segs[i][0] + a0, segs[i][1] + a0
            ov = max(0, min(y, s['b']) - max(x, s['a'])) / max(1, y - x)   # share of the segment inside the right syllable
            loc[b].append(ov >= 0.6)
            p = classify(lv[x:y]); e2e[b].append(p == s['tone']); pairs3.append((s['tone'], p))
print('\n===== E2/E3: segmentation as planned (syllable count known), by phrase length')
print('syllables/phrase  phrases  voiced-runs==count  syllables located  tone right end-to-end')
for b in bins:
    if natural[b]: print(f'   {b[0]:>2}-{b[1]:<2}          {len(natural[b]):>5}      {np.mean(natural[b]):>6.0%}            {np.mean(loc[b]):>6.0%}             {np.mean(e2e[b]):>6.0%}')
al = [x for v in loc.values() for x in v]; ae = [x for v in e2e.values() for x in v]
print(f'   all                              located {np.mean(al):.0%}   end-to-end {np.mean(ae):.0%}')

# ---------- E4: learned classifier with context, boundaries given, tested on books it never saw
def feat(lvseg):
    s = shape(lvseg, points=6, skip=0.0)
    return s if s is not None else np.full(6, np.nan)
X, Xc, y, g, phid = [], [], [], [], []
pid = 0
for c in chapters:
    lv = levels(c['f0'], c['lo'], c['hi'])
    for ph in c['phrases']:
        pid += 1
        fs = [feat(lv[s['a']:s['b']]) for s in ph]
        allv = np.concatenate([lv[s['a']:s['b']] for s in ph]); pm = np.nanmean(allv) if np.any(~np.isnan(allv)) else np.nan
        for i, s in enumerate(ph):
            if s['amb']: continue
            seg = lv[s['a']:s['b']]
            own = list(fs[i]) + [(s['b'] - s['a']) * HOP, np.mean(~np.isnan(seg)), c['db'][s['a']:s['b']].max() - np.percentile(c['db'], 90)]
            prev = list(fs[i - 1][-3:]) if i > 0 else [np.nan] * 3
            nxt = list(fs[i + 1][:3]) if i + 1 < len(ph) else [np.nan] * 3
            ctx = prev + nxt + [i, len(ph) - 1 - i, pm, np.nanmean(seg) - pm if np.any(~np.isnan(seg)) else np.nan]
            X.append(own); Xc.append(own + ctx); y.append(s['tone']); g.append(c['book']); phid.append(pid)
X, Xc, y, g, phid = np.array(X), np.array(Xc), np.array(y), np.array(g), np.array(phid)
print(f'\n===== E4: small learned classifier, boundaries given, leave-books-out ({len(y)} syllables)')
for name, F in (('contour only', X), ('contour + neighbours + phrase position', Xc)):
    pred = np.zeros_like(y); proba = np.zeros((len(y), 5))
    for tr, te in GroupKFold(n_splits=6).split(F, y, g):
        m = HistGradientBoostingClassifier(max_iter=300, learning_rate=0.08).fit(F[tr], y[tr])
        pred[te] = m.predict(F[te]); proba[te] = m.predict_proba(F[te])
    print(f'--- {name}'); print(confusion(list(zip(y, pred))))
    print('per book:', ' '.join(f'{np.mean(pred[g == b] == y[g == b]):.0%}' for b in sorted(set(g))))
# verification view (context model): the app knows the expected tone and only flags confident mismatches
pe = proba[np.arange(len(y)), y]
print('\n===== E5: "is the expected tone plausible?" (context model). False alarm = native syllable flagged;')
print('      catch rate = flagged when a different tone is expected than the one spoken (stand-in for a learner error)')
for thr in (0.05, 0.1, 0.2, 0.33):
    fa = np.mean(pe < thr)
    catch = np.mean([(proba[:, t][y != t] < thr).mean() for t in range(5)])
    perphrase = np.mean([np.any(pe[phid == p] < thr) for p in np.unique(phid)])
    print(f'  flag if p(expected) < {thr:<4}: false alarms {fa:5.1%} of syllables, {perphrase:4.0%} of phrases get >=1;  wrong-tone catch rate {catch:4.0%}')
