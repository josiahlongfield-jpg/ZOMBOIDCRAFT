---
name: zomboid-house-alarms-tech
description: How LANDSCAPE's Zomboid house alarms work (House.alarm placement, ha_ kit, world/gen/HouseAlarms ringing, power/battery, Electrical cut) and the yard arc/hum (2026-10-31)
metadata:
  type: project
  modified: 2026-10-31T10:04:31.014Z
---

Part of Josiah's approved LANDSCAPE plan [[zomboid-landscape-review-plan]]. Branch claude/project-thread-d61kbn. Video: /mnt/project-files/zomboid-mod/previews/house-alarms.mp4.

- **Placement:** world/county/House.alarm(height) runs after story(). Its dice are separate: SplittableRandom(interior.seed()*0x2545F4914F6CDD1D + 0xA1A2). It skips burnt houses (no interior), RUIN, TRAILER and CABIN.
  - Chance comes from House.setAlarmChance, which reads world.houseAlarms (sandbox "Alarm"). Values: never 0, extremely_rare .015, rare .04, sometimes .08 (default), often .15, very_often .25. These are MY GUESSES; check them against Zomboid's source on Josiah's PC.
  - The bell box goes at y=height-1 on the front wall, outside (z0-1, facing north). It tries offsets 2,-2,3,-3,1,-1,4,-4 from the door. If none fits, the house has no alarm.
  - The keypad goes INSIDE at z0+2, because walls are two layers: exterior at z0, home_wall_* at z0+1. It sits beside the door at y=2, facing south.
  - The mark is p.vehicle(centre, "!alarm:w;d;h;siren01;battery") and covers the garage too. Tools force placement with -Dalarms.rate=1.
- **ha_ kit** (tools/city/housealarm.py; the al_ prefix was taken), drawn from refs/alarm:
  - ha_bell_box: red gong bell.
  - ha_siren_box: white box with a blue lens.
  - ha_keypad: beige, with an LCD and ARM/RDY lamps.
  - All are decor with rotate=facing and the wall to the +z side as drawn.
- **Game side** (world/gen/HouseAlarms):
  - Chunk attachment zomboidcraft:house_alarms holds Alarm(box, siren, battery, until). until is 0 when armed, a game time while ringing, and -1 when spent.
  - Every 10 ticks it scans chunks within 2 of each player. An entity that is now inside the box AND was last seen outside it trips the alarm. WeakHashMap WAS_IN tracks this, so logins and zombies shut inside don't count. Zombies count only if zombieLore.houseAlarms is on.
  - Ringing lasts 1200 ticks. The bell loops every 80 ticks and the siren every 85. Noise.emitEngine 120 fires every 40 ticks. Then the alarm is spent.
  - An alarm needs power: PowerGrid.hasPower, or the battery. The battery lasts until offAt(district) + battery*2*world.alarmBatteryDays days; a value of -1 means no battery.
  - It registers AUDIO's 4 sound ids itself: world.alarm.house.bell/siren and block.alarm_panel.beep/disarm. AUDIO must not register them again.
  - Cut payload: zomboidcraft:house_alarm_cut. It needs Electrical 5 and a screwdriver in either hand. The cut takes 120 ticks, or 60 at level 8 or above, and gives +15 XP. The cut works whether or not the alarm is ringing.
  - Client: client/alarms/AlarmClient adds the green dot and the held-G option (greyed with the reason).
- **Sandbox:** SandboxOptions has "Alarm" mapped to world.houseAlarms and "AlarmDecayModifier" mapped to world.alarmBatteryDays (LANDSCAPE). The "AlarmDecay" choice duplicate is left unwired.
- **Yard effects** (world/gen/YardEffects):
  - On PowerGrid.OFF, if a player is within 256 blocks, pw_bushing* blocks spark for 70 ticks, then a bang (WorldEvents.EXPLOSION, smoke, Noise 96).
  - While a district is on, players within 24 blocks hear a hum (BEACON_AMBIENT at pitch 0.5) every 80 ticks.
  - Sounds are vanilla stand-ins. AUDIO could make substation ones.
