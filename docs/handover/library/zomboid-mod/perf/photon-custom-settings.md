# Josiah's Photon custom settings (rebuilt from our notes)

Matches photon_v1.4.zip.txt in this folder: drop that file into your shaderpacks folder instead of setting these by hand. Settings not listed are Photon's defaults (the High profile). Open them from Video Settings, Shader Packs, Shader Pack Settings. Anything not listed was left at Photon's default.

## Lighting
- Sunlight: 0.00 (as in your file; 0.85 if you want the sun back), colour 0.86 / 0.90 / 1.00
- Skylight: 0.85
- Moonlight: 0.6, colour 0.68 / 0.77 / 1.00
- Blocklight: 1.25, colour 1.00 / 0.80 / 0.55
- Bounced light: 0.25
- Colored Lights: **On** (keep it on: off turns purple lights amber)
- Colored Light Shafts: On
- Cloud shadows: 1.0
- Ambient occlusion: GTAO

## Shadows
- Resolution: 1536 (your latest)
- Distance: 6 chunks (your latest)

## Fog / atmosphere
- Overworld Fog Intensity: 1.01
- Mie fog: morning 0.0100, noon 0.0030, evening 0.0080, midnight 0.0080, dawn/dusk 0.0060, rain 0.040
- Altitude fade: start 20, half-life 20
- Rayleigh: normal 0.0003, rain 0.0002
- Bloomy Fog: On, 0.6
- Aerial perspective boost: 2

## Sky
- Cumulus coverage: 1.50
- Noctilucent clouds: Off
- Aurora: Never
- Rain opacity: 0.25 (it was 0.5 earlier)

## Post-processing
- Auto exposure: On, bias -0.3
- Saturation: 0.80
- Contrast: 1.10
- Bloom: 0.7
- Purkinje: On
- Motion blur: Off
- Depth of field: Off (or weak)
- Anti-aliasing: TAA + FXAA

## Materials
- Normal Mapping: On
- Specular Mapping: On (Texture Format stays labPBR)
- Rain puddles: On

## Photonics (in the same menu)
- Lighting: Sharp (Photon's default)
- Max Lights: 350
- Separate Handheld Rays: On
