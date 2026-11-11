# Fighter and attack-helicopter HUD symbology notes

Written from general knowledge of public material (flight manuals, simulator docs, press photos), not traced from the photos in these folders. Use it as a layout guide next to the photos in f22/, su27/, apache/ and symbology/. The in-game HUDs should use made-up names and markings, as the project's naming rule requires.

## Shared basics (any modern fighter HUD)
- **Colour**: one colour, monochrome green (about #3CFF5A) on a transparent combiner glass. Thin lines and a slight glow. Brightness and night dim settings.
- **Field of view**: roughly 25-30 degrees wide and 20 high, centred a little above the cockpit's centre line. Symbols outside that box are clipped.
- **Boresight / gun cross ("W" or small cross)**: fixed at the aircraft's nose line. It shows where the nose points.
- **Flight path marker (velocity vector)**: a circle with two wings and a tail. It shows where the aircraft is actually going, so it drifts below or beside the boresight with angle of attack and sideslip. It is the main thing pilots fly by.
- **Pitch ladder**: horizontal rungs every 5 degrees, numbered. Climb rungs are solid; dive rungs are dashed and angled toward the horizon. The horizon line is long and solid. The ladder rotates with roll and stays centred on the flight path marker.
- **Heading tape**: a scale across the top (or bottom) in degrees with a centre caret, labelled every 10 degrees (e.g. "27" = 270).
- **Airspeed**: left side, a box or tape in knots (calibrated airspeed). Mach number below it (e.g. "0.92").
- **Altitude**: right side, a box or tape in feet. A small "R" marks radar altitude when low. A vertical speed readout sits next to it.
- **G meter**: top left, current G (e.g. "4.2") and peak G since reset.
- **Angle of attack**: a small bracket or number near the airspeed (alpha in degrees).
- **Bank scale**: a small arc with ticks at 10, 20, 30 and 45 degrees and a pointer, at the bottom of the HUD.
- **Master mode**: text such as NAV, A/A (air to air), A/G (air to ground), with the selected weapon and count (e.g. "SRM 4", "GUN 480").
- **Target symbols**: a target box (square) on a locked target, a missile seeker circle that grows or shrinks for in-range cues, range readout and closure rate, and "SHOOT" cue when in range. Gun mode shows a lead-computing reticle (funnel or pipper) instead.
- **Warnings**: flashing text such as PULL UP, LOW FUEL, BINGO, and a breakaway "X" when about to hit the ground.

## F-22 style (US, 5th generation)
- Wide-angle HUD, fairly clean. Large flight path marker, minimal ladder.
- Airspeed and altitude in boxes (not tapes); heading tape at top.
- Six colour flat-panel displays below the HUD. The middle one is the big tactical situation display (top-down map with own ship in the centre, threats and friendlies as shapes). Smaller panels show engines, fuel, stores (weapons bay picture) and the checklist.
- An up-front control panel sits just under the HUD with a keypad and two small displays.

## Su-27 style (Soviet / Russian, 4th generation)
- Smaller HUD (ILS-31 type) with Russian-style symbology: the horizon is a horizontal line with a centre "aircraft" symbol and, in some modes, a bar that moves for pitch rather than a full ladder.
- Speed in km/h and altitude in metres, often as numbers left and right. Cyrillic mode letters.
- Old-school analogue cockpit below it: round gauges for attitude (ADI), horizontal situation (HSI), airspeed, altimeter, plus a small radar/IRST scope (round, green) on the right.
- Missile launch zone shown as a vertical scale with min and max range marks and a moving target range marker.

## AH-64 Apache style (attack helicopter)
- No HUD on glass. The pilot wears the IHADSS helmet sight: a small monocle in front of the right eye, showing symbology over the night-vision FLIR picture.
- Symbology over the view: heading tape at top, airspeed (knots) left, radar altitude (feet) right with a vertical bar, torque percentage top left, a hover position box and velocity vector line, cueing dots that point to targets, and weapon status text at the bottom (e.g. "GUN", "RKT", "MSL" with counts).
- The gun (30 mm chain gun under the nose) slaves to where the pilot looks, so the reticle follows the head.
- TADS/FLIR view (gunner): black-and-white thermal picture (white hot), a centre crosshair, zoom levels (wide/medium/narrow), laser range readout and a "LASE" or "LST" mode indicator.
- Cockpit: two crew in tandem (gunner front, pilot rear), each with two square multi-function displays (MFDs) and a keyboard.

## Game-feel suggestions
- Draw the HUD in screen space on a fixed "combiner" rectangle, fade it out when the player looks far to the side (jets), or attach it to the view (Apache helmet).
- Keep the flight path marker, ladder, speed, altitude, heading and weapon text as the minimum set; add G and alpha for detail.
- Levelling systems: horizon line plus bank scale cover "levelling"; a "level-off" cue can flash when vertical speed is near zero.
