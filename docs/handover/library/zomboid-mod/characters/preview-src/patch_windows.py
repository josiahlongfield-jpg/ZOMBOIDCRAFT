# kits_v21.html -> kits_v22.html: zombies beating on doors and windows, and climbing in through a window (breakin.js)
import sys
src, dst = sys.argv[1], sys.argv[2]
s = open(src).read()
code = open(__file__.replace('patch_windows.py', 'breakin.js')).read()

def once(a, b):
    global s
    assert s.count(a) == 1, (s.count(a), a[:80])
    s = s.replace(a, b)

# the movement code, ahead of zombieMove (so the offline pose renderer has it too)
once('\nfunction zombieMove(fig, mode, t, v) {', '\n' + code + '\nfunction zombieMove(fig, mode, t, v) {')
once("  const hips = J.hips; hips.rotation.order = 'YXZ'; hips.position.set(0, BASE, 0);\n",
     "  const hips = J.hips; hips.rotation.order = 'YXZ'; hips.position.set(0, BASE, 0);\n"
     "  if (BRK_MODES[mode]) { if (!LEGS.off) return breakIn(J, mode, t, v); mode = 'attack'; }   // no legs: it claws at it from the floor\n")
# buttons
once('        <button data-v="down">Knocked down</button>\n',
     '        <button data-v="down">Knocked down</button>\n'
     '        <button data-v="bangdoor">Bangs on a door</button>\n'
     '        <button data-v="bangwin">Bangs on a window</button>\n'
     '        <button data-v="climb">Breaks in a window</button>\n')
once("  if (id === 'anim') state.anim = b.dataset.v; else state.close = b.dataset.v === 'close';",
     "  if (id === 'anim') { state.anim = b.dataset.v; if (BRK.yaw[state.anim] != null) { state.yaw = BRK.yaw[state.anim]; BRK.t0 = clock.getElapsedTime(); } }\n"
     "  else state.close = b.dataset.v === 'close';")
# the props on the stage, run every frame
once("const stage = new THREE.Group(); scene.add(stage);", "const stage = new THREE.Group(); scene.add(stage);\nvar BRKP = brkProps(); stage.add(BRKP.g);")
once("  while (stage.children.length) stage.remove(stage.children[0]);", "  for (const c of stage.children.slice()) if (!BRKP || c !== BRKP.g) stage.remove(c);   // the door and window stay")
once("  if (state.close) { tp.set(0, 30, 52); tl.set(0, 25, 0); } else { tp.set(0, 24, w < 600 ? 140 : 100); tl.set(0, 16, 0); }",
     "  if (state.close) { tp.set(0, 30, 52); tl.set(0, 25, 0); } else { tp.set(0, 24, w < 600 ? 140 : 100); tl.set(0, 16, 0); }\n"
     "  if (BRK_MODES[state.anim] && !state.close) {   // step back and up to take in the wall and the window, centred on where it goes\n"
     "    const c = state.anim === 'bangdoor' ? 2 : 5, sy = Math.sin(state.yaw), cy = Math.cos(state.yaw);\n"
     "    tp.set(c * sy, 30, w < 600 ? 175 : 130); tl.set(c * sy, 26, c * cy);\n  }")
once("  stepLimbs(Math.min(0.05, t - (frame.prevL ?? t))); frame.prevL = t;",
     "  stepLimbs(Math.min(0.05, t - (frame.prevL ?? t))); frame.prevL = t;\n  brkStep(BRKP, state.anim, mv.dt);")
# hitboxes and shots: the game's tables don't cover these poses yet
once("  const mode = state.anim, n = legsGone(), ph = v.phase || 0;\n  if (LEGS.off || n === 2) {",
     "  const mode = state.anim, n = legsGone(), ph = v.phase || 0;\n  if (BRK_MODES[mode]) return ['IDLE', false];\n  if (LEGS.off || n === 2) {")
once("  capsView.visible = HEADS.show && !d;", "  capsView.visible = HEADS.show && !d && !BRK_MODES[state.anim];")
once("    const text = !HEADS.show ? '' : d ? 'Dead: no hitboxes' :",
     "    const text = !HEADS.show ? '' : d ? 'Dead: no hitboxes' : BRK_MODES[state.anim] ? 'Hitboxes for banging and climbing come with the game build' :")
once("function shoot(e) {\n  if (HEADS.dead) return;",
     "function shoot(e) {\n  if (HEADS.dead) return;\n"
     "  if (BRK_MODES[state.anim]) { shotlog.textContent = 'Shooting is off while it bangs or climbs: the hitboxes for these poses come with the game build'; return; }")
once("const MOOD = { shamble: 'idle', idle: 'idle', crawler: 'idle', down: 'idle', alert: 'alert', chase: 'chase', attack: 'chase' };",
     "const MOOD = { shamble: 'idle', idle: 'idle', crawler: 'idle', down: 'idle', alert: 'alert', chase: 'chase', attack: 'chase', bangdoor: 'chase', bangwin: 'chase', climb: 'alert' };")
# the blurb
once("and it still keeps coming. Pick an outfit,",
     "and it still keeps coming. Bangs on a door and Bangs on a window: one blow a second like the game, arms in turn and "
     "every fourth both at once, leaning its weight in, the free hand pawing. Breaks in a window: it smashes the glass, "
     "rakes the teeth out of the frame, hauls itself onto the sill (the game's one-block sill and 0.69 by 1.75 opening), "
     "slides through on its belly, spills out head first onto the floor, lies there a moment and gets up. Pick an outfit,")
open(dst, 'w').write(s)
print('ok', len(s))
