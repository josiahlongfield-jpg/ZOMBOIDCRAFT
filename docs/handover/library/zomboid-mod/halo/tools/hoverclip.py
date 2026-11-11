import sys, time, subprocess, numpy as np
sys.argv=['x']; exec(open('screens.py').read().replace('for f in (s1, s2, s3, s4, s5): f()\ns2(4)', ''))
_bg = background(); background = lambda: _bg.copy()
FPS = 30; path = [5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 6, 5]
def ease(t): return t * t * (3 - 2 * t)
frames = []
def hold(sel, s):
    w = [0.0] * 7; w[sel] = 1; frames.extend([(w, sel)] * int(s * FPS))
hold(path[0], 0.8)
for a, b in zip(path, path[1:]):
    n = int(0.28 * FPS)
    for k in range(1, n + 1):
        t = ease(k / n); w = [0.0] * 7; w[a] = 1 - t; w[b] = t; frames.append((w, b if t > 0.5 else a))
    hold(b, 0.7 if b in (5, 4, 0, 6) else 0.35)
out = SC + 'cs/out/cs2-hover.mp4'
p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', '1280x720', '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '26', '-preset', 'slow', out], stdin=subprocess.PIPE)
cache = {}; t0 = time.time()
for w, sel in frames:
    key_ = (tuple(round(x, 4) for x in w), sel)
    if key_ not in cache: cache[key_] = s2frame(list(w), sel).convert('RGB').resize((1280, 720), Image.LANCZOS).tobytes()
    p.stdin.write(cache[key_])
p.stdin.close(); p.wait(); print(len(frames), 'frames', time.time() - t0, 's', out)
