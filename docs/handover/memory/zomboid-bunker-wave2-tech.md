---
name: zomboid-bunker-wave2-tech
description: Bunker wave 2 kinds (bank vault, ghost station, old mine adit, then grain silo, missing floor, harbour fort, container tunnel, offshore ship): where each lives and how it was built
metadata:
  type: project
  modified: 2026-11-06T02:10:00.000Z
---

LANDSCAPE thread, branch claude/project-thread-d61kbn. Plan doc https://claude.ai/artifact/DxX1cErgwr3AvxeE9HLS6k has each kind's way in + clue. Harbour fort built 2026-11-06 (8c03b3810): see [[zomboid-harbour-fort-tech]]. Wave 2 is complete.

- refs/bunker has 18 folders, each with sources.md.
- Reloading benches (a4e84b2f): Shops GUNS, hunter garages, Cellar/police/Sheriff armouries.
- Wave 2 BANK VAULT (4dd4b769; army+benches+vault in playtest 15a28c73 23:3x): Bank.bunker (Civic.bank rolls 35%; 50/30/20 sealed/looted/breached); Bank.panicRoom: bk_deposit_blank x3 cols + bk_deposit_door (new door block; subset.py now handles doors) in vault back row, bunk_hatch below, core Kind.VAULT turned 2 at PANIC_FLOOR -14, shaft at core (0,-4). Bunkers.KINDS has "vault" (TOWN candidates); hints in PaperMaps/CountyCommand. Preview sheet previews/bunker-bank-vault.jpg. Mesh view coords: x,z = world (dump+origin), y = dump index.
- Wave 2 GHOST STATION (b6cce576, playtest 61c90db1; previews/bunker-ghost-station.jpg): GhostSite (HIDEOUT variant ghost_station via County.ghostStation) on a long straight subway TUNNEL run; platform, head house, deep stair, Bunker SUBWAY; gh_infill walls. mesh.py needs --near LOCAL x,y,z --radius 250.
- Wave 2 OLD MINE ADIT (48181841a, playtest 9566e6e50 2026-11-06; sheet previews/bunker-mine-adit.jpg): County.planHideout adds mines for roll<=0.14 via mineFits (mountains depth>=60, slope, cover, 120 from places, 60 from roads); variant mine_adit, MINE_REACH 80. MineSite: cutting, spoil, rails, boards, sign, old sets, NEW set behind portal (clue), propped fall, antechamber, Bunker FOREST .inMine(). Preview /tmp/claude-0/mn/dump.sh.
- Wave 2 SILO (24a547b3b, playtest b33796c1c, sheet previews/bunker-silo.jpg): SiloBunker.dig from FarmSite.barnComplex (15% via own SplittableRandom(p.seed^0x5110), first old STAVE/BRICK silo), sl_padlock (tools/city/silo.py) beside bunk_door_steel at silo foot, dirt_path with y1 "air" flanks (stops Kit.overgrow), hatch at silo centre, HatchSite.antechamber (now static), Kind FOREST, mark "silo"; Bunkers FARM candidates. SitePreview farm crop 220 200 seed 19 has one (use -Dlevel=20).
- Wave 2 MISSING FLOOR (f3809fc6c, playtest 7efc57a22 2026-11-06; previews/bunker-missing-floor.jpg): city/Strongpoint via Tower.strongpointFloor/strongpoint, rolled in DowntownSite.missingFloor (6%, own random); xf_ kit (tools/city/strongpoint.py); plates+slits on window row, bricked stair door (bashed if breached), lift doors plated so the lift skips it; marks 'floor'. Preview: /tmp/claude-0/xf/TowerFloor.
- Wave 2 CONTAINER TUNNEL (adff0fb74, playtest 4f3961a82; previews/bunker-container-tunnel.jpg): ContainerTunnel.dig from PortSite.container() tunnel() (35%, own random p.seed^0xC07A1, block nearest shed); box doors on the gap between columns, tn_cable out to sy_manhole, sealed = as_chain+sl_padlock inside; hatch at box far end, tunnel 2 wide to antechamber (its own ladder removed), Kind SHIP core; mark 'container'. Preview: SitePreview port container 340 300 seed 4.
- Wave 2 OFFSHORE SHIP (1b844e242, playtest 461aeb364; previews/bunker-offshore-ship.jpg): OffshoreSite hideout via County.offshoreShip (plan 'ship' anchorage in deep open sea, usually 5700,800); BigShip.container at anchor named(OffshoreSite.name), list as 2-block bands (|z|<8 unmoved), forward holds flooded past BigShip.dryEnd, crew walkers 'construction'; mark 'offshore'. Test: /tmp/claude-0/os/Find.java.

Related: [[zomboid-bunker-review-tech]], [[zomboid-bunkers-direction]], [[zomboid-bunker-kit-tech]].
