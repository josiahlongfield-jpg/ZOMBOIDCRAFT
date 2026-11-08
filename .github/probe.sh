#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
CP=$(echo $MC_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
echo "===== compile errors"
grep -E "error:" -A3 build.log | head -40
echo "===== GuiGraphicsExtractor blits"
javap -cp "$CP" -p net.minecraft.client.gui.GuiGraphicsExtractor 2>&1 | grep -iE "blit" | head -40
javap -cp "$CP" -p -c net.minecraft.client.gui.GuiGraphicsExtractor 2>&1 | awk '/(innerBlit|submitBlit|void blit\(com.mojang.renderpearl.api.textures.GpuTextureView)/{p=1} p{print} /return$/{if(p){print "-----"};p=0}' | head -200
echo "===== BlitRenderState"
for c in $(for j in $MC_JARS; do unzip -Z1 "$j"; done | grep -E 'BlitRenderState\.class$' | sed 's/\.class$//; s#/#.#g' | sort -u); do
  javap -cp "$CP" -p -c "$c" 2>&1 | head -120
done
