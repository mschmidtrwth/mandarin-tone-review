#!/usr/bin/env python3
"""Dumps Praat's F0 track for every test fixture as a reference for the :pitch tests.

Output is one line per sample: name;t0;dt;f0 f0 f0 ...
where t0 is the time of the first frame in seconds, dt the frame step,
and an f0 of 0 marks an unvoiced frame.
"""
from pathlib import Path

import parselmouth

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "pitch" / "src" / "test" / "resources"
SAMPLES = ROOT / "app" / "src" / "main" / "assets" / "samples"
TIME_STEP = 0.01
PITCH_FLOOR = 60
PITCH_CEILING = 500


def main():
    lines = []
    for wav in sorted(SAMPLES.glob("*.wav")):
        pitch = parselmouth.Sound(str(wav)).to_pitch_ac(
            time_step=TIME_STEP, pitch_floor=PITCH_FLOOR, pitch_ceiling=PITCH_CEILING
        )
        f0 = " ".join(f"{f:.2f}" for f in pitch.selected_array["frequency"])
        lines.append(f"{wav.stem};{pitch.xs()[0]:.6f};{TIME_STEP};{f0}")

    out = RESOURCES / "praat_f0.txt"
    out.write_text("\n".join(lines) + "\n")
    print(f"wrote {len(lines)} tracks to {out}")


if __name__ == "__main__":
    main()
