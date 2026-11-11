---
name: zomboid-ship-tech
description: How the massive cargo ships at PORT places are built and previewed (BigShip.java, vessel_ kit, ship bunker, rain Photon mock-up)
metadata:
  type: project
  modified: 2026-10-26T02:48:37.289Z
---

(2026-10-26, landscape thread) Commits 1f6f72d (ships) and 343e835 (rain mock-up) on branch claude/project-thread-d61kbn, CI green. Merged into playtest ae07dd2 (2026-10-26 03:05, CI green), jar sent.

- src/main/java/com/zomboidcraft/world/county/BigShip.java builds the ocean-going ships; Ships.java keeps only the small boats (tug, barge, trawler), which still use vanilla blocks.
- Frame: bow +x, keel line z=0, y from the quay top; Ships.SEA=-4, BED=-12. container(len, beam, Bunker.State) is big when beam >= 36 (deck 14, keel -16, 8 house levels). bulk(len, beam, tanker) has deck 12.
- PortSite.container(): beam 40-42, length 250-290, bow east or west; 60% carry a bunker (SEALED 45%, LOOTED 25%, BREACHED 30% of those). The feeder at anchor is container(...) with null. industrial(): bulk carrier or tanker (45%).
- Bunker: on the tank top of the aft hold, frame p.turned(dx, tank, dz, 3) so the blast door faces aft; reached from the engine room through vessel_false_door (flush, bulkhead-textured). Marks "bunker <state>" at the false door and "bunker door". /zc bunker searches the 3 nearest downtowns AND 3 nearest ports, nearest first.
- Block kit: tools/city/vessel.py (161 vessel_ blocks) writes blocks.json; registered in photo_textures.py. rotate "axis" is x/z only, so vertical risers use vessel_mast.
- Preview: scratch pv.sh NAME VARIANT SEED [W D] (SitePreview with -Dlevel=20). View coords = place coords for x/z; y = 20 + ship y.
- view.html &photon=1&rain=1 is the rainy Photon look (overcast, wet ground, grey fog, streaks; exposure capped at 1.25). Use it for exterior previews from now on, since his game rains permanently. The served copy in furn/www/view.html is a COPY: re-copy after editing.
- Sheets sent: previews/cargo-ships.jpg and previews/cargo-ships-photon-rain.jpg.
- The rest of PortSite and Harbour still use some vanilla blocks (sheds, jetties, crane colour option white_concrete). That is a follow-up sweep.
- Next in wave one: civil-defence shelter, forest hatch.

Related: [[zomboid-bunkers-direction]], [[zomboid-garage-bunker-tech]], [[zomboid-bunker-kit-tech]], [[zomboid-world-tech]].
