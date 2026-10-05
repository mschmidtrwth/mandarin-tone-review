"""Loads the aligned chapters: pitch tracks, speaker range, pause-delimited phrases, corrected tone labels."""
import json, glob, os, re, numpy as np, parselmouth, pickle
from tonelib import *
D = str(WORK / 'chapters')
NEUTRAL2 = set('时候 喜欢 漂亮 东西 地方 朋友 告诉 觉得 事情 先生 明白 眼睛 耳朵 认识 意思 清楚 衣服 妈妈 爸爸 爷爷 奶奶 哥哥 姐姐 弟弟 妹妹 谢谢 月亮 窗户 头发 晚上 早上 这里 那里 哪里 外面 里面 前面 后面 上面 下面 力气 客气 便宜 舒服 聪明 麻烦 厉害 暖和 记得 认得 出来 进来 回来 起来 过来 下来 上来 出去 回去 进去 过去 名字 关系 消息 休息 故事 样子 知识 看看 想想 听听 说说 太太 小姐 怎么 什么 那么 这么 多么 我们 你们 他们 她们 人们 咱们 学生 先生 大夫 师傅 脑袋 困难 热闹 打算 商量 相信 希望'.split()) - {'学生', '希望', '相信', '困难', '过去'}

def track(wav):
    snd = parselmouth.Sound(wav)
    n = int(snd.duration / HOP)
    p = snd.to_pitch_ac(time_step=HOP, pitch_floor=60, pitch_ceiling=500)
    f0 = np.full(n, np.nan); idx = np.round(p.xs() / HOP).astype(int); fr = p.selected_array['frequency']
    ok = idx < n; f0[idx[ok]] = np.where(fr[ok] > 0, fr[ok], np.nan)
    it = snd.to_intensity(minimum_pitch=100, time_step=HOP)
    db = np.full(n, np.nan); idx = np.round(it.xs() / HOP).astype(int); ok = idx < n; db[idx[ok]] = it.values[0][ok]
    db[np.isnan(db)] = np.nanmin(db)
    return f0, db

def load():
    cache = D + '/all.pkl'
    if os.path.exists(cache): return pickle.load(open(cache, 'rb'))
    out = []
    for js in sorted(glob.glob(D + '/*.json')):
        key = os.path.basename(js)[:-5]; book = key.split('__')[0]
        syl = json.load(open(js))['syl']
        f0, db = track(js[:-5] + '.wav')
        v = f0[~np.isnan(f0)]; lo, hi = np.percentile(v, [5, 95])
        # lexical neutral tones pypinyin leaves as full tones
        for i in range(len(syl) - 1):
            if syl[i]['h'] + syl[i + 1]['h'] in NEUTRAL2 and not syl[i + 1]['brk']: syl[i + 1]['tone'] = 0
        # phrases: split at pauses
        phrases, cur = [], []
        for i, s in enumerate(syl):
            if cur and s['start'] - cur[-1]['end'] > 0.25: phrases.append(cur); cur = []
            cur.append(s)
        if cur: phrases.append(cur)
        for ph in phrases:
            for i, s in enumerate(ph):
                nxt = ph[i + 1]['start'] if i + 1 < len(ph) else s['end'] + 0.2
                s['a'] = int(round(s['start'] / HOP)); s['b'] = int(round(min(nxt, s['end'] + 0.25) / HOP))
                s['amb'] = False
            # third-tone sandhi within the phrase: every 3 followed by a 3 is spoken as 2
            i = 0
            while i < len(ph):
                j = i
                while j + 1 < len(ph) and ph[j]['tone'] == 3 and ph[j + 1]['tone'] == 3: j += 1
                if j > i:
                    for k in range(i, j): ph[k]['tone'] = 2
                    if j - i >= 2:
                        for k in range(i, j): ph[k]['amb'] = True  # 3-3-3: grouping decides 2-2-3 vs 3-2-3
                i = j + 1
        phrases = [ph for ph in phrases if np.mean([s['score'] < 0.3 for s in ph]) < 0.3]
        out.append(dict(key=key, book=book, f0=f0, db=db, lo=lo, hi=hi, phrases=phrases))
        print(key, f'{lo:.0f}-{hi:.0f} Hz median {np.median(v):.0f}', len(phrases), 'phrases', sum(map(len, phrases)), 'syll', flush=True)
    pickle.dump(out, open(cache, 'wb'))
    return out
