---
name: zomboid-ui-pages-tech
description: Kit screen Clothing page and skill books (SkillBooks read action), built by the UI thread 2026-10-29
metadata:
  type: project
---

- Clothing page (client/kit/ClothingPage.java), the 2nd kit tab, in playtest 2026-10-29 (28e22047). It reads clothing/ClothingSlot, Clothes.of and Clothes.totals (owned by the clothing thread). The rows use slot.menuSlot() and CharacterPage.choices. The Character page's old 4-slot clothing column became a totals summary with a link. The KitUiTest tab indexes use KitScreen.Page.X.ordinal(), so new tabs don't break it.
- Skill books (skills/SkillBooks + loot/items/SkillBookItem, registered in LootItems for ids skillbook_*):
  - Hold right-click for 15 s to read the volume for your level band (0-2, 3-4, 5-6, 7-8, 9-10), which gives x3/5/8/12/16 XP in Skills.addXp.
  - The books_read attachment is lost on death.
  - The Skills page shows which volume is needed and whether it has been read.
- The UI test workflow is marked "failure" when its log grep runs past head -200 (Broken pipe), even when the build succeeds. Gate merges on build.yml.
- Pending: the 12 roster bags (purse, briefcase, gym_bag, doctors_bag, plastic_bag, paper_bag, suitcase, lunchbox, cooler, mail_bag, tool_bag, ammo_pouch_belt) are mine. REFERENCES is collecting photos into refs/items/bags/. WORLD LOOT places them once I say they're in playtest.
- Kit figure (Josiah 2026-11-02, playtest ebe1b7286): Character/Clothing/Hair+beard figures stand still in idle facing out and turn by left-drag (client/kit/SpinFigure, InventoryScreenMixin @ModifyArg on the pose quaternion + zeroed walk anim). Health page figure stays front-on (part picking); companion screens and HUD figure still follow the mouse.
