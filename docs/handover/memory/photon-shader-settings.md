---
name: photon-shader-settings
description: Josiah plays with Photon shaders on; the mod's fog uses vanilla fog distances, which Photon ignores; exact Photon fog values recommended
metadata:
  type: project
---

2026-10-25: Josiah reported that `/zc weather fog` did nothing. The likely cause is Photon, which draws its own fog and ignores vanilla fog-distance attributes. I asked him to test with shaders off (K). If that confirms it, the plan is a shader-independent fog (drifting fog particles around the player).

His Photon (on Iris, 26.3) was on defaults except Overworld Fog Intensity 2.00 and Colored Light Shafts On. Defaults he showed: Mie morning 0.0070, noon 0.0001, evening 0.0050, midnight 0.0050, rain 0.030, dawn/dusk 0.0020, altitude fade 7/7; Rayleigh normal 0.0005, rain 0.0005, falloff 30/30.

Values I recommended 2026-10-25 (keep intensity 2.00 as the master knob; drop to 1.5 if too thick): Mie morning 0.0120, noon 0.0030, evening 0.0080, midnight 0.0080, dawn/dusk 0.0060, rain 0.040, altitude fade start and half-life 20. Rayleigh normal 0.0003, rain 0.0002. Bloomy Fog on at 0.6. These were estimated, not tested; fine-tune from his screenshots.

Second round 2026-10-25 (he sent every Photon screen; fog visibly hazier after the first round): Colored Lights on, cloud shadows 1.0, sunlight 0.85, skylight 0.85, moonlight 0.6, auto exposure on with bias -0.3, saturation 0.80, contrast 1.10, aerial perspective boost 2, noctilucent clouds off, more cumulus coverage, aurora never, rain opacity 0.5, bloom 0.7. Shadows (2048, 8 chunks), GTAO, water and TAA+FXAA left as they were.

Light colours (Photon's RGB sliders max out at 1.00): sunlight 0.86/0.90/1.00, moonlight 0.68/0.77/1.00, blocklight 1.00/0.80/0.55 (cold daylight and moon, sodium-orange lamps). Exposure is under Post-Processing.

2026-10-25 (landscape thread): he said `/zc weather drizzle` doesn't stop the fog. One mod cause was fixed: a forced weather could still get a random fog bank. Most likely cause is Photon's own fog at intensity 2.00, which is on in every weather. Told him to check `/zc weather` (fog 0.10 or less means the fog is Photon's) and to lower Overworld Fog Intensity to 1.00.

Other general advice given: more cloud coverage, saturation about 0.8 with slightly more contrast and lower exposure, Purkinje on, rain puddles on, motion blur and strong depth of field off. Related: [[zomboid-mod-status]], [[josiah-world-art-direction]].

2026-10-25 (weapons thread): Josiah also runs Photonics (Iris raytracing add-on; his settings: Lighting Sharp, Max Lights 1000, Separate Handheld Rays on) and Distant Horizons (overdraw 16/16). Told him to leave them, and to drop Max Lights to ~250 first if FPS dips. To see the mod's LabPBR maps: Photon > Materials > Normal Mapping ON and Specular Mapping ON (on/off only; Texture Format stays labPBR, the default).

2026-10-25 (landscape thread) town FPS: 60 outside towns and 18 inside. Car detail levels raised it to 32; with shaders off it's 60 in town (probably the frame cap). So the rest is shader load from dense town geometry and lights. Iris's Max Shadow Distance slider doesn't show for Photon, because Photon sets its own. Suggested: Photon Shadows 8 → 6 chunks, Photon Colored Lights off, Photonics Max Lights 1000 → 250. CORRECTION (11:40): Colored Lights off turned his purple froglights amber (all light took the amber blocklight colour). Told him to turn it back ON; never suggest turning it off again. Lights past the Max Lights cap also fall back to amber, so raise it to ~400 if far lamps look amber. After those settings plus the cheaper far cars, he got 40-50 FPS in town (11:09). Photonics options are inside Photon's Shader Pack Settings, not a separate menu.

2026-10-26: Josiah has NO Mod Menu. Particle Rain settings open with the /particlerain chat command. A "hide vanilla rain" toggle is unconfirmed. Photon Rain Opacity is in Shader Pack Settings (he had 0.5; suggested 0.25).

LESSON 2026-10-26 (weather thread): Photon discards particle pixels below roughly 0.1 final alpha (texture x vertex alpha). Soft low-alpha mist drew as hard discs, then vanished entirely when thinned. Fix: particle textures with no faint pixels (solid body + rim of scattered specks that TAA smooths), and fade by size instead of alpha. The weapons thread found the same for beacon-beam glow (drops under ~half strength).
2026-10-26 11:4x: recommended the Squall mod (Fabric to 26.3: roof/ledge drips, wall runoff, splashes) plus optional Puddles & Floods; asked him to keep Particle Rain and pull one if they clash. Our own bolder roof-runoff is on hold while he tries Squall (our weather.drips may double up).

2026-11-01 (FPS thread): changing Photon's Profile resets its quality options (shadows, AO, clouds); his look values (fog, colours, exposure) survived in shaderpacks/photon_v1.4.zip.txt. Still upsetting to him (it happened after I suggested Medium as a test). Never suggest changing the Profile; test single settings instead. Restore sheet: /mnt/project-files/zomboid-mod/perf/photon-custom-settings.md (Profile High, then the custom list). His latest shadows: 1536 / 6 chunks.

His photon_v1.4.zip.txt 2026-11-01 05:13Z: Mie morning 0.0100, fog intensity 1.01, Photonics max lights 350, cumulus 1.50, blocklight I 1.25, bounced light 0.25, SUN_I=0.00 (sun off; asked if deliberate), shadowDistance 96.
Restored file: /mnt/project-files/zomboid-mod/perf/photon_v1.4.zip.txt (his file + COLORED_LIGHTS, AIR_FOG_COLORED_LIGHT_SHAFTS, shadowMapResolution=1536). Photon option keys/defaults: github sixthsurge/photon shaders/settings.glsl + shaders.properties (profile lines; defaults = High). Profiles Low/Medium/High turn COLORED_LIGHTS and light shafts OFF.
