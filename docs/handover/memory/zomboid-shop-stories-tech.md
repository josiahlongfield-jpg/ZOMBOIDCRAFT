---
name: zomboid-shop-stories-tech
description: LANDSCAPE's shop building stories (Zomboid RB looted/trashed/gun store siege/staff last stand): code, hook, rate, preview (2026-11-06)
metadata:
  type: project
  modified: 2026-11-06T05:52:00.000Z
---

Commit b11e12ccc on claude/project-thread-d61kbn, CI green, playtest 2a6b82867 (2026-11-06 05:18).

- **world/city/ShopStories.java** (public): `tell(p, type, w, d)` is called at the end of Shops.fitOut. That covers Storefront fit trades, Walkup, PackTower and MallShop shops.
- **Dice:** its own SplittableRandom from a hash of p.world corners. Shops without a story build exactly as before.
- **Rate:** BASE 0.1 x setRate. StoryMarks registers it with SurvivorHouseChance, the same as houses.
- **Tales:**
  - LOOTED (RBShopLooted)
  - TRASHED (RBTrashed)
  - GUNSTORE_SIEGE: GUNS rolls it 70% of the time.
  - STAFF_LAST_STAND (RBCafe/RBPizzaWhirled): DINER and PIZZA roll it 60% of the time.
- **Floor box:** x 1..w-2, z 1..d-6. The doorway is kept clear. Bodies and walkers are StoryMarks marks at y 1, with a negative radius so they stay indoors.
- **Debug flags:** -Dshops.stories (rate) and -Dshops.tale=NAME (forces the tale).
- **Preview:**
  - Tool: /tmp/claude-0/ss/ShopPreview.java (`java -cp build/zcp ShopPreview OUT seed cut TYPES...`). Reloading_bench is shown as store_counter, because mesh.py has no model for it.
  - Sheet: previews/shop-stories.jpg.
- **Wave 2** (242c39e2d): Trades.story -> ShopStories.tellTrade(p, trade, left, right, back, door, pair). TAVERN gets BAR (RBBar) 70%, BARBER gets HAIR_SALON 70%, and other trades get looted or trashed. Preview: TradeShot in /tmp/claude-0/ss/com/.../county.
- **Schools** (aff9f0786): an overrun school (30%) rolls ShopStories.dice. Overrun classrooms put out bodies (teacher "office") and walkers, the gym shelter bodies and walkers. Public helpers: dice/body/walkersIndoors. All in playtest.
- **Tower floors** (ce6a2365c, playtest 22b5603c5): Floors.Fitter.story() at the start of aftermath's barricade section; clinic (MEDICAL/DENTAL) and office kinds; ShopStories.dice salted by f.y; needs 12 free cells. Test /tmp/claude-0/ss/TowerCount.java (rate 0.5 gives about half the towers a story floor).
- **Burnt** (52e2233f9, in playtest): BURNT weight 2 (trades: 20%); soots edge walls via the SOOTS set (wall + "_soot"); fittings to rubble_pile/fm_char_logs; scorch, fm_bones. Preview previews/shop-burnt.jpg. All of Zomboid's building stories are now done.

Related: [[zomboid-house-stories-tech]], [[zomboid-road-stories-tech]], [[zomboid-zone-stories-tech]].
