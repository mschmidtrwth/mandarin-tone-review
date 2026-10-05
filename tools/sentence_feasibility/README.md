# Sentence feasibility check for tone recognition

One-off experiment from October 2026: does the tone recognition of plan step 6 (energy-dip
segmentation plus tone templates) hold up on whole sentences? It does not, so step 6 is limited to
single words and tone pairs. These scripts are kept to rerun the numbers and as a starting point for
sentence support. They are prototypes in Python on Praat's pitch tracker, not part of the app.

## Scripts

| File | What it does |
|---|---|
| `tonelib.py` | Shared code: Praat pitch and intensity on a 10 ms grid, tone levels, the step 6 prototype (`segment`, `classify`), confusion matrix. |
| `eval_words.py` | Runs the step 6 prototype on the 71 bundled word samples. |
| `chapters.txt` | The graded-reader chapters used, relative to `samples/graded_readers`. |
| `align.py` | Transcribes each chapter with Whisper large-v3, converts the text to pinyin and force-aligns it with torchaudio's MMS aligner. Writes per-syllable times and tones. |
| `data.py` | Loads the aligned chapters: pitch track, speaker range (5th to 95th percentile of the chapter), phrases split at pauses over 250 ms, and label fixes (third-tone sandhi, common neutral-tone words). |
| `eval_sentences.py` | Experiments E1 to E5 below on the aligned chapters. |

Converted audio, alignments and caches go to `build/sentence_feasibility`, which git ignores.

## Running

`eval_words.py` needs only `numpy` and `praat-parselmouth`. The rest needs a CUDA GPU and

    pip install torch torchaudio transformers accelerate pypinyin praat-parselmouth numpy scipy scikit-learn soundfile

then, from this directory:

    python eval_words.py
    python align.py            # about 45 s per chapter, skips chapters already done
    python eval_sentences.py

## Results

Per-syllable accuracy. Words: 71 samples, one speaker. Sentences: 19 chapters from 15 books,
about 12,000 syllables of native narration.

| Test | Accuracy |
|---|---|
| Words, step 6 prototype | 81 % |
| E1: step 6 templates on sentences, syllable boundaries from the aligner | 44 % |
| E2: step 6 segmentation on sentences, syllable count known: syllables located | 87 % overall, 96 % for 2 syllables, 66 % for 8 to 12 |
| E3: step 6 end to end on sentences | 40 % |
| E4: gradient-boosted trees on the syllable's contour, aligner boundaries, tested on unseen books | 71 % |
| E4: same plus neighbouring syllables and position in the phrase | 76 % |

E5 treats the E4 context model as a checker that only flags a syllable when the expected tone is
improbable. Flagging below 5 % probability still flags 6 % of native syllables (17 % of phrases get
at least one flag) while catching 73 % of wrong tones.

Always answering tone 4 scores 29 % on the sentences. E4's results vary by under a point between runs.

## Caveats

- Tone labels are automatic: Whisper's transcript, pypinyin's readings, and the fixes in `data.py`.
  Neutral tones in particular are under-labelled, which lowers every sentence figure somewhat.
- Syllable boundaries come from a CTC aligner and are approximate.
- All audio is native narration. Nothing here says how learner speech behaves.
- `samples/graded_readers/martian.txt` holds the text of "The Country of the Blind", with a character
  missing at most line wraps. It is not used: Whisper's transcript matched it apart from those gaps.
- The `samples/pronunciation_zh_*` words are by many speakers with no per-speaker range, so they are
  left out.
