#!/usr/bin/env bash
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*deobf*.jar' 2>/dev/null)
CP=$(echo $MC_JARS | tr ' ' ':')
echo "===== RenderPipelines GUI_OPAQUE_TEXTURED_BACKGROUND / GUI_TEXTURED build"
javap -cp "$CP" -p -c net.minecraft.client.renderer.RenderPipelines 2>&1 | grep -E "ldc|invoke|putstatic" | grep -B30 -E "putstatic.*(GUI_OPAQUE_TEXTURED_BACKGROUND|GUI_TEXTURED:)" | grep -vE "^--$" | head -80
echo "===== who uses GUI_OPAQUE_TEXTURED_BACKGROUND"
for c in $(for j in $MC_JARS; do unzip -Z1 "$j"; done | grep -E '^net/minecraft/client/gui/render/.*\.class$' | sed 's/\.class$//; s#/#.#g' | sort -u); do
  out=$(javap -cp "$CP" -p -c "$c" 2>/dev/null | grep -nE "GUI_OPAQUE_TEXTURED_BACKGROUND|blurBeforeThisStratum|isBlur|BLUR" )
  [ -n "$out" ] && { echo "-- $c"; echo "$out" | head -20; }
done
echo "===== GuiRenderer methods"
javap -cp "$CP" -p net.minecraft.client.gui.render.GuiRenderer 2>&1 | head -80
