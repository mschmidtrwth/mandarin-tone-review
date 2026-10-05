#!/usr/bin/env bash
# Shows one of the bundled samples in the debug build on the connected phone,
# e.g. tools/show_sample.sh hao3
set -euo pipefail

name="${1:?usage: show_sample.sh <sample name, e.g. hao3>}"
root="$(cd "$(dirname "$0")/.." && pwd)"
wav="$root/app/src/main/assets/samples/$name.wav"
package=com.example.mandaring

adb shell run-as "$package" mkdir -p files/debug
adb exec-in run-as "$package" sh -c "cat > files/debug/$name.wav" < "$wav"
adb shell am start -S -W -n "$package/.MainActivity" --es debug_wav "$name.wav" > /dev/null
