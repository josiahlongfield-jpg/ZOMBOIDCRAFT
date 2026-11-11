---
name: zomboid-foundry-tech
description: Foundry Row (coast and industry site 5, Calder Steel works at 1300,1950): files, frame, fr_ kit, layout constants, preview pipeline
metadata:
  type: project
  modified: 2026-10-30T19:50:37.392Z
---

Foundry Row = coast/industry group site 5 (last), built 2026-10-30 by the "Coast and industry" thread. Plan spot `foundry` (1300, 1950); Layout.fixedPlace makes a 6x5-block LANDMARK lot variant "foundry" (about x 1054..1506, z 1774..2146, Terrain.CITY); LandmarkSite dispatches `new Foundry(lot(0,0,w,d,0), rng, w, d)`.

Files (world/county): Foundry.java (Grounds subclass: ground, roads, rails, fence, lamps, story; helpers pipe/drum/pile/heap/platform/ladder/steelStair), FoundryFurnaces.java (two blast furnaces at FX {190,280}, FZ 120: stoves, dust catcher, skip, cast houses; No. 1 holdout with lit tap), FoundryWorks.java (coke battery, blowing engine + boilers, gas holder, ore yard + ore bridge, slag dump, open hearth, bar mill with fr_name_ picture, scrap, machine shop via Harbour.factory, water tower), FoundryRow.java (south strip z 317..372: alley 319..323, 10 SHOTGUN company houses x 4..93 turned to face south with back yards, Storefronts: Mercantile COMPANY, union hall VFW with soup line yard + food/local sprays, tavern TAVERN, employment REALTY; time office by the gate, 3-storey general office with fr_name_ parapet, wash house with fr_basket on fm_chain, car park).

Frame: x east, z south toward the street; FENCE 316, GATE 200, SPINE 294..301, rails SPUR 306 / HOT 147 / SLAG 99.

Kit: tools/city/foundry.py (fr_ prefix, 135 blocks), registered in photo_textures.py. Gotcha: texture painters and block-model builders share a module namespace, so a model helper with the same name as a painter (oven_door, basket) silently shadows it; model helpers are named *_model. `cp_timber` is a texture only, the block is `sy_timber`; `ext_black_metal` is a texture, use `hull_black`.

Preview: /tmp/claude-0/plan/fr/go.sh NAME CX CZ R (Y0=60 H=80) — real county dump meshed to scratchpad furn/www/m_fr_NAME. Gallery section "foundry" on artifact 2Zsu1YGwXQV4SRUiuATiCy.

Related: [[zomboid-landscape-queue]], [[zomboid-hollis-tech]], [[zomboid-shipyard-tech]].
