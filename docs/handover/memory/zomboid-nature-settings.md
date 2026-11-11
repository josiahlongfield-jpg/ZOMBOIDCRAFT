---
name: zomboid-nature-settings
description: Zomboid sandbox Nature page settings (ZcConfig.Nature, Climate.java): what WEATHER built, who owns the rest
metadata:
  type: project
---
Josiah approved (2026-10-31 04:23) the WEATHER plan for the Nature group of the PZ review's settings section ([[zomboid-pz-review]]). Shipped to playtest 1add0b2d:
- New config section `nature` (ZcConfig.Nature), read through world/weather/Climate.java: climateCycle (normal/no_weather/endless_rain/endless_storm), fogCycle (normal/no_fog/endless_fog), rain (very_dry..very_rainy; normal = his chosen mix, never below moderate), temperature (very_cold..very_hot), maxFog and maxRainFx (client visual caps), snowOnGround (placeholder, no snow yet).
- Climate.airTemp(level,pos) gives °C for SURVIVAL's body temperature (an on/off setting per Josiah).
- Endless night stays, no new seasons; endless snow/blizzard cycles wait for snow.
- Owners of the rest: SURVIVAL (farming speed/abundance, compost, plant resilience, indoor crops, growing seasons by temperature only, fishing, nature's abundance, clay chances); Erosion: none anywhere for now (Josiah 2026-10-31, via LANDSCAPE), so no erosion settings. SURVIVAL told 2026-10-31.
Related: [[zomboid-weather-direction]], [[zomboid-match-zomboid-rule]].
(2026-10-31 05:02, from COMMANDS) Per-world Sandbox backbone (com.zomboidcraft.sandbox, playtest 67b3d201) wires nature.* to Zomboid ClimateCycle/FogCycle/Rain/Temperature/MaxFogIntensity/MaxRainFxIntensity (per player)/EnableSnowOnGround; values per world in zomboidcraft_sandbox.json. Still WEATHER's to wire via SandboxOptions.wires(): DayNightCycle, NightDarkness, NightLength (FireSpread is WEAPONS', ContainedFire).
