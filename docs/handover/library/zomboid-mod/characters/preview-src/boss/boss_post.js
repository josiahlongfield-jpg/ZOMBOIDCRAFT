// ---------------------------------------------------------------- the boss stands BOSS_K times the hunters' size
const BOSS_K = 1.4;
{
  const pose0 = pose;
  pose = function (w, motion, dyn) {
    const r = pose0(w, motion, dyn), g = [w.x, 0, w.z], K = BOSS_K;
    r.parts = r.parts.map(([n, f]) => [n, { o: add(g, mul(sub(f.o, g), K)), R: f.R.map(row => row.map(x => x * K)) }]);
    for (const ch of dyn.chunks) for (let k = 0; k < ch.v.length; k += VS) for (let i = 0; i < 3; i++) ch.v[k + i] = g[i] + (ch.v[k + i] - g[i]) * K;
    return r;
  };
}
