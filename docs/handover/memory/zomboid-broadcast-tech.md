---
name: zomboid-broadcast-tech
description: Grayhaven Broadcast Center tech (Broadcast/BroadcastHouse/BroadcastTower, tv_ kit, preview coords, checks); built 2026-10-28
metadata:
  type: project
---
WGHV Channel 6 "NewsCenter 6" broadcast center (+ WGHV-FM 98.1) on Grayhaven island, plan place "tower" at 3100,-2050 (Layout 2x2 lot, facing 2 = rotated 180, front on the subway street). Built 2026-10-28 by the LANDSCAPE thread, branch commit 8f1b3239, playtest regen 8354d9d9. No ref photos yet: redo when photos land ([[zomboid-photo-reference-rule]]).

- Broadcast.java (extends Grounds): plaza with logo pylon/flags, staff lot, service yard, guard post (razor wire, jersey barriers, nest). Constants for house AX0..BX1, tower TCX 34/TCZ 104, compound, transmitter, dish farm.
- BroadcastHouse.java: 3-storey brick station. Ground: lobby, green room, newsroom, control room (monitor wall, switcher), master control, tape library. 2nd: offices, weather center, edit suites. 3rd: engineering, break room, FM station, front holdout (EBS desk, cots, generator). Studio wing with lighting grid, set, cameras; dock with roll-up door. Stairs ST1/ST2 with roof huts; roof helipad (hs_pad_h) + heli 55%.
- BroadcastTower.java: lattice mast LT=200 + 30 antenna, BASE=3 (girts at y2 sealed the base, so legs start at y2 and girts above). Platforms at segments 2/5/8/12 + top; spine + tv_ladder (climbable) to the top. World top about y 298. Transmitter hut, ice bridge (tv_ice axis x), fenced dish farm (tv_dish facing south = correct tilt).
- Kit tools/city/broadcast.py (tv_, 256 blocks; registered after ballpark in photo_textures.py). tv_coax/tv_ice/tv_pipe are axis. tx_glass is rotate=axis (x/z), not facing. Mural plates need z0 15.3 (15.9 was invisible).
- Preview: `SitePreview DUMP landmark tower 133 133 SEED 8`; mesh site = (132-x, y+12, 132-z) because of the 180 lot. Checks clean on seeds 1-3 (walkcheck only furniture-top pockets, headroom 0, orientcheck 0, palcheck clean).
