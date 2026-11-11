---
name: zomboid-otto-tech
description: OTTO the robot train conductor: voice pipeline, foul-mouthed personality rule, reaction hooks (boarding, zombies, shots, stop button)
metadata:
  type: project
---

Split from [[zomboid-trains-tech]] 2026-10-28.
- OTTO voice: tools/trains/otto_voice.py (Piper joe voice; style D since 12:30 = B made clearer and louder at his ask, attenuation 48) writes sounds/otto/*.ogg + OttoLines.java; OttoSay payload, client OttoVoice. Model: tools/trains/otto.py paints atlas (0,256) and writes models/train/otto.bin + OttoModel (paint.py calls it); OTTO_PREVIEW=dir renders previews. Feature faces need a sub rect, e.g. nz=('eye',(0,0,1,1)), or they show a patch.
- OTTO's personality (Josiah 2026-10-27): crude and foul-mouthed, swearing and insults OK, NO slurs at real groups. DOORS_OPEN group at each stop, "Get on the fucking train." half the time. He may send more lines: add to LINES in otto_voice.py and re-run.
- Reactions (2026-10-27): Otto.watch (each TrainRun tick) boards/alights/jumped, zombiesAbout>=4; Guns.fire calls Trains.shot(level, player, eye, stop) per pellet (keep that hook if editing Guns), Otto.shot checks HIM point and cab/windscreen panes in car-0 frame.
- Stop button lines: STOP_REQUESTED, HALTED, DOORS_HELD (Otto.stopRequested/halted/doorsHeld). Close-doors button (2026-10-28, TrainCar.CLOSE_BUTTONS 0.34 over each stop button, atlas tile (0,6), StopRequest.close): DOORS_SHUT, TrainRun.closeDoors.
- Re-running otto_voice.py re-encodes every clip and reorders sounds.json: keep only the new clips (git checkout the rest) and splice new keys into sounds.json after the last otto.* key.
- Josiah 2026-10-29 08:35: OTTO was annoying. Now he says ONLY "What's up, fuckers?" (GREET) when someone boards; nothing on call buttons or anywhere else. Config trains.ottoOnlyGreets (default true) gates Otto.say/tell; false brings all lines back.
