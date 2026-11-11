# Head bursts and the game's hitboxes (Josiah 27 Oct 06:39 and 07:27): shots land on the mod's own hit capsules
# (BodyCaps.java) with the game's gun numbers; big rifle and close shotgun headshots burst the head; a Hitboxes
# switch draws the capsules for the pose it is in. Run on the v16 kits.html (with patch_limbs applied):
#   python3 patch_heads.py KITS_HTML BODYCAPS_JAVA OUT_HTML
import json, re, sys
src, caps_java, out = sys.argv[1:4]
s = open(src).read()
j = open(caps_java).read()
caps, notes = {}, {}
for m in re.finditer(r"// (\w+): caught, right part, extra: ([\d.]+)%, ([\d.]+)%, ([\d.]+)%[^\n]*\n\s*private static final float\[\] T_(\w+) = \{([^}]*)\};", j):
    assert m.group(1) == m.group(5)
    caps[m.group(1)] = [float(v.strip().rstrip('f')) for v in m.group(6).split(',')]
    notes[m.group(1)] = [round(float(m.group(2))), round(float(m.group(3))), round(float(m.group(4)))]
assert len(caps) == 19 and all(len(v) == 13 * 8 for v in caps.values()), len(caps)
js = open('heads.js').read().replace('__CAPS__', json.dumps(caps, separators=(',', ':'))).replace('__NOTES__', json.dumps(notes))

def rep(a, b):
    global s
    assert s.count(a) == 1, a[:70]
    s = s.replace(a, b)

# the old mesh-based shot goes; the new one lives in heads.js
i = s.index("function shoot(e) {"); k = s.index("\ndocument.getElementById('gun')", i)
s = s[:i] + s[k + 1:]
rep("function frame() {", js + "function frame() {")
rep("  limbMove(fig, state.anim, t, { phase: (oi * 37 % 40) / 40 * 6, dt: Math.min(0.05, t - (frame.prev ?? t)) }); frame.prev = t;\n  applyFace();\n",
    "  const mv = { phase: (oi * 37 % 40) / 40 * 6, dt: Math.min(0.05, t - (frame.prev ?? t)) }; frame.prev = t;\n"
    "  if (!HEADS.dead) { limbMove(fig, state.anim, t, mv); applyFace(); }\n")
rep("  stepLimbs(Math.min(0.05, t - (frame.prevL ?? t))); frame.prevL = t;\n",
    "  stepLimbs(Math.min(0.05, t - (frame.prevL ?? t))); frame.prevL = t;\n  stepHeads(t, mv);\n")
rep("resetLimbs(); }", "resetLimbs(); resetHeads(); }")
rep('<span class="lab">Shoot it: pick a gun, click the zombie</span>', '<span class="lab">Shoot it: pick a gun, click the zombie (try the head)</span>')
rep('<button data-v="reset">Reset limbs</button>', '<button data-v="reset">Reset</button>')
rep('''      <div id="shotlog" style="font-size:12px;opacity:.85;margin-top:6px;min-height:1.3em" aria-live="polite"></div></div>''',
    '''      <div id="shotlog" style="font-size:12px;opacity:.85;margin-top:6px;min-height:1.3em" aria-live="polite"></div></div>
    <div class="group"><span class="lab">Hitboxes</span>
      <div class="seg" id="hitboxes">
        <button data-v="off" aria-pressed="true">Hidden</button>
        <button data-v="on">Shown</button>
      </div>
      <div id="hbnote" style="font-size:12px;opacity:.85;margin-top:6px;min-height:1.3em;max-width:46ch"></div></div>''')
rep("The numbers here are placeholders for the weapon thread to tune.",
    "Heads burst too: one hunting rifle headshot, or three or more pellets of buckshot in the head up close, blows the head apart (skull bits and the hat fly off along the shot, a torn neck is left) and it drops dead, tipping onto its side and vanishing in a puff the way every Minecraft mob dies; pistols and assault rifles kill with a headshot but don't burst it. Shots land exactly as in the game: on the mod's hitboxes for the pose it is in, with the game's gun damage, limb toughness and bursting numbers (all in /zc config). Switch Hitboxes to Shown to see them: red head, yellow body, blue arms, green legs.")
open(out, 'w').write(s)
open(out + '.check.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[-1])
print(len(s) // 1024, 'KB')
