"""Whisper transcription + MMS forced alignment of the chapters in chapters.txt.

Writes <chapter>.wav and <chapter>.json (per syllable: hanzi, pinyin, tone, start, end, aligner score)
to build/sentence_feasibility/chapters. Chapters already done are skipped. Needs a GPU.
"""
import sys, os, re, json, subprocess, numpy as np, torch, torchaudio, soundfile as sf
from pypinyin import lazy_pinyin, Style
from transformers import pipeline

from tonelib import ROOT, WORK

READERS = ROOT / 'samples' / 'graded_readers'
OUT = WORK / 'chapters'; OUT.mkdir(parents=True, exist_ok=True)
CHAPTERS = [l.strip() for l in open(os.path.join(os.path.dirname(__file__), 'chapters.txt')) if l.strip()]
dev = 'cuda'

asr = pipeline('automatic-speech-recognition', model='openai/whisper-large-v3', torch_dtype=torch.float16, device=dev)
bundle = torchaudio.pipelines.MMS_FA
fa = bundle.get_model(with_star=True).to(dev)
DICT = bundle.get_dict(star='*')

def syllables(text):
    """[(hanzi, toneless pinyin, tone)] with sandhi applied; non-hanzi dropped, phrase breaks kept as None."""
    out = []
    for chunk in re.findall(r'[一-鿿]+|[^一-鿿]+', text):
        if not re.match(r'[一-鿿]', chunk):
            if re.search(r'[，。！？；：、,.!?…]', chunk): out.append(None)
            continue
        py = lazy_pinyin(chunk, style=Style.TONE3, neutral_tone_with_five=True, tone_sandhi=True, v_to_u=False)
        if len(py) != len(chunk): continue
        for h, p in zip(chunk, py):
            m = re.fullmatch(r'([a-z]+)([1-5])', p)
            if m: out.append((h, m.group(1).replace('v', 'u'), int(m.group(2)) % 5))
    return out

for ch in CHAPTERS:
    key = ch.replace('/', '__').replace(' ', '_').replace("'", '').replace(',', '')[:-4]
    wav = f'{OUT}/{key}.wav'; js = f'{OUT}/{key}.json'
    if os.path.exists(js): continue
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f'{READERS}/{ch}', '-ar', '16000', '-ac', '1', '-c:a', 'pcm_s16le', wav], check=True)
    audio, sr = sf.read(wav, dtype='float32')
    text = asr({'raw': audio, 'sampling_rate': sr}, chunk_length_s=30, batch_size=2,
               generate_kwargs={'language': 'zh', 'task': 'transcribe', 'max_new_tokens': 220, 'prompt_ids': asr.tokenizer.get_prompt_ids('以下是普通话的句子。', return_tensors='pt').to(dev)})['text']
    syl = syllables(text)
    real = [s for s in syl if s]
    tokens = [DICT['*']]; owner = [-1]
    for i, (_, p, _) in enumerate(real):
        for c in p: tokens.append(DICT[c]); owner.append(i)
    with torch.inference_mode():
        em = torch.cat([fa(torch.from_numpy(audio[a:a + 16000 * 60]).unsqueeze(0).to(dev))[0] for a in range(0, len(audio), 16000 * 60)], dim=1)
        ali, sc = torchaudio.functional.forced_align(em.cpu(), torch.tensor([tokens]))
    ali, sc = ali[0].numpy(), sc[0].exp().numpy()
    sec = len(audio) / sr / em.shape[1]
    # walk the frame alignment: each non-blank run is one token occurrence
    spans, k, prev = [], -1, 0
    for f, t in enumerate(ali):
        if t != 0 and t != prev: k += 1; spans.append([f, f + 1, [sc[f]]])
        elif t != 0: spans[-1][1] = f + 1; spans[-1][2].append(sc[f])
        prev = t
    assert len(spans) == len(tokens), (len(spans), len(tokens))
    res, brk, j = [], False, 0
    per = {}
    for (a, b, s), o in zip(spans, owner):
        if o < 0: continue
        per.setdefault(o, []).append((a, b, np.mean(s)))
    it = iter(range(len(real)))
    idx = 0
    for s in syl:
        if s is None: brk = True; continue
        p = per[idx]
        res.append(dict(h=s[0], py=s[1], tone=s[2], start=round(p[0][0] * sec, 3), end=round(p[-1][1] * sec, 3),
                        score=round(float(np.mean([x[2] for x in p])), 3), brk=brk))
        brk = False; idx += 1
    json.dump(dict(text=text, syl=res), open(js, 'w'), ensure_ascii=False)
    print(key, len(res), 'syllables', text[:60], flush=True)
