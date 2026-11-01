"""Original synthesized alarm loops. No third-party samples. Peak normalized to 0.65."""
import math
import struct
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
ROOT.mkdir(parents=True, exist_ok=True)
RATE = 22050
DURATION = 8

def sound(kind, t):
    if kind == 'pulse':
        phase = t % 0.5
        freq, length = 880, 0.18
    elif kind == 'alternating':
        phase = t % 0.4
        freq, length = (660 if int(t / 0.4) % 2 == 0 else 990), 0.28
    elif kind == 'rhythm':
        period = 0.5 if t < 4 else 0.25
        phase = t % period
        freq, length = 740, period * 0.45
    elif kind == 'morning':
        phase = t % 0.5
        freq, length = [523.25, 659.25, 783.99, 1046.5, 783.99, 659.25, 587.33, 659.25][int(t / 0.5) % 8], 0.42
    else:
        phase = t % 1
        freq, length = (784 if int(t) % 2 == 0 else 1046.5), 0.85
    if phase >= length:
        return 0
    envelope = min(1, phase / 0.012, (length - phase) / 0.035)
    harmonics = [(1, 1), (2.76, .3), (4.07, .12)] if kind == 'bell' else [(1, 1), (2, .16)]
    decay = math.exp(-phase * 4) if kind == 'bell' else 1
    return envelope * decay * sum(a * math.sin(2 * math.pi * freq * h * phase) for h, a in harmonics)

for kind in ['pulse', 'alternating', 'bell', 'rhythm', 'morning']:
    samples = [sound(kind, i / RATE) for i in range(RATE * DURATION)]
    scale = 32767 * .65 / max(abs(s) for s in samples)
    with wave.open(str(ROOT / f'{kind}.wav'), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(b''.join(struct.pack('<h', round(s * scale)) for s in samples))

