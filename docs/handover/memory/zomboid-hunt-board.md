---
name: zomboid-hunt-board
description: Hunt board page on the command radio (UI thread draws it; AUDIO owns entries, AIRCRAFT owns strike status); mock sent 2026-11-06, awaiting Josiah's sign-off
metadata:
  type: project
---

From the world-systems brief (W20/W21/W22, doc UxHyNZNfCqGbJFzxXd67VJ) after Josiah's marine/purge direction (2026-11-06).
- Mock: https://claude.ai/artifact/PgV8knaxyt8Mqg62gkRCrd (source in UI scratchpad huntboard/hunt-board.html). Kit style; radio faceplate (CMD 41.25, Overwatch/Reaper), purge credit total, tabs (districts, targets, objectives, escorts), list + detail with Overwatch caption, net log strip, Air support box, Track pins one line to the HUD. Opens from the G menu when carrying something that hears 41.25, or right-click the military radio.
- RULE: nothing about the purge on the Map (W22 no); places are words only.
- Open question to Josiah: districts show Open/Cleared only, or also progress.
- Data: AUDIO (plan 6PuJBUc9D8v8qWt3etpeoF) supplies entries (id, kind, title, where, status, credit, doneWhen, gives, caption+time, seen, optional progress) + change event + hearsCommandNet(player). AIRCRAFT supplies AirStrikes.status(base): ready, ticksLeft, bonus (max 2), lastResult (type, where as BlockPos -> UI names it from the nearest place, day, kills, friendlies), cycleTicks (comms cut already applied).
- Build nothing until Josiah signs off; preview clip before merge.
- 2026-11-06 12:20 AUDIO: Josiah approved its command net plan (all as recommended). Wave 1 on wh9qba 15649a626: client/gear/HuntBoardClient.board() JsonObject {total, hears, entries[{id, kind district|target|objective|escort, title, where, doneWhen, gives, caption, status open|done, credit, time, doneAt, seen}], log[{text,time}]}, version(), hears(), seen(id); server CommandNet.hears(player). No district progress. My board page still waits for Josiah's OK on the mock.
- 2026-11-06 20:07 AIRCRAFT: strikes in playtest 7f9e6db5a. AirStrikes.status(level, base) -> Status(ready, ticksLeft, cycleTicks, bonus, lastResult Last(type, where BlockPos, day, kills, friendlies)); base ids StrikeCalls.BASE (default "main").
