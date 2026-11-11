---
name: zomboid-injuries
description: ZomboidCraft body-part injury system (Zomboid-lite), chosen and shipped 2026-10-25; design and where it lives
metadata:
  type: project
  modified: 2026-10-25T12:43:30.067Z
---

Josiah asked for area-specific damage shown as a glowing red outline on the HUD figure that moves with it. He picked "Zomboid-lite" depth on 2026-10-25. It shipped in playtest d35f27f.

- Six parts (head, torso, both arms, both legs). Each part has one wound (scratch/burn/laceration/bite) and/or a fracture. survival/Body.java is a synced attachment "body"; survival/Injuries.java handles damage → parts, bleeding, healing and attribute effects.
- Falls break legs (chance (dmg-3)/8 per leg). Zombies hit mostly the arms. Fire burns (15%). Blasts and arrows cut.
- A bare broken leg means -25% speed and no sprinting; splinted it is -12%. Hurt arms lower attack damage and add rifle sway.
- Bandages bind the worst open wound. The splint item (2 sticks + bandage) splints a fracture (heals 3x faster). Fractures take about 1 hour splinted and 3 hours bare.
- Test commands: `/zc injure <part> <scratch|cut|bite|burn|break>`, `/zc heal`, `/zc stats`.
- Glow: InventoryScreenMixin captures the HUD figure's render state, and client/hud/InjuryGlowLayer draws emissive shells on the posed parts.

**Why:** Josiah wants Zomboid-style medical play that reads at a glance on the figure.
**How to apply:** extend this system (e.g. infection per bite, stitches, painkillers) rather than adding a parallel one. See [[zomboid-hud-direction]].

**Lesson (2026-10-25):** `@Inject(method="extractRenderState")` without a descriptor matched InventoryScreen's void render overload, and the game crashed at launch. CI's server smoke test never applies client mixins, so green CI does not prove a client mixin works. Always pin the full descriptor from probe output when a name may be overloaded.
