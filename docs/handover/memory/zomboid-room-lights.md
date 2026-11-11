---
name: zomboid-room-lights
description: Room light switches and blue/red cube lamps (lx_ kit, LightSwitch, RoomLights post-pass), shipped 2026-10-31; rooms start dark
metadata:
  type: project
  modified: 2026-10-31T02:19:34.051Z
---

**The ask (Josiah, 2026-10-31 01:09).** Light switches and working lights in every room of houses, town facilities, shops, towers and hotels; open industrial areas are left out. Modern buildings use "the nice blue lights the police station uses", with some red. He answered on 2026-10-31:
- The blue light is "the blue cube light on either side of the entrance doors in the downtown police buildings".
- "Rooms start dark."

**Built (commit 98517464, playtest merge 21421103):**
- **lx_ kit** (tools/city/roomlights.py): lx_switch / lx_switch_on (wall plate, decor, CityBlocks flag SWITCH 64), lx_cube_blue(_lit) light 13 and lx_cube_red(_lit) light 10. The script rewrites blocks.json in place and writes data/zomboidcraft/lamps.txt: "off on" pairs of room fittings, picked from blocks.json by the LAMP regex. Add a new room-lamp name to that regex.
- **world/blocks/LightSwitch.java**: the "light_switch" payload. Flipping a switch flood-fills its room at switch height (walls are solids and doors; at most 2500 cells, range 40) and swaps every paired lamp in a column from -2 to +10. Client: client/lights/LightClient (green dot through Interactables, chained in CarClient).
- **world/county/RoomLights.java**: a post-pass in Builders after clearFrontage. It does three things:
  - darkens every lit room lamp under a roof;
  - puts a switch beside each door into a room (a room needs a ceiling within 12 and at most 2500 cells);
  - adds a ceiling light to any room that has none.
  Modern places are DOWNTOWN except old_city, plus the LANDMARKs mall, airport, mercy, arena, tower, civic, university and broadcast. In them every ceiling_light* becomes a cube lamp, red 15% of the time.
- The previews counted 774 switches in a downtown block, 83 in a town and 71 in a suburb. Video: /mnt/project-files/zomboid-mod/previews/room-lights.mp4.

**Not done.** The police HQ door lamps themselves (cv_lamp_globe_lit on posts in CityCivic) were left as they are. Downtown orientcheck reports 13 windows turned out of their wall; these were there before this change.
