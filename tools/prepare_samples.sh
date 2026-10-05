#!/usr/bin/env bash
# Converts samples/*.mp3 to 16 kHz mono 16-bit WAV with leading and trailing
# silence trimmed. The app bundles them as its reference library and the :pitch
# tests use them as fixtures.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/app/src/main/assets/samples"
mkdir -p "$out"

# Trim silence below -45 dB at both ends, keeping 50 ms of it as padding.
trim="silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05"

for mp3 in "$root"/samples/*.mp3; do
    name="$(basename "$mp3" .mp3)"
    ffmpeg -v error -y -i "$mp3" \
        -af "$trim,areverse,$trim,areverse" \
        -ar 16000 -ac 1 -c:a pcm_s16le "$out/$name.wav"
done

echo "wrote $(ls "$out"/*.wav | wc -l) files to $out"
