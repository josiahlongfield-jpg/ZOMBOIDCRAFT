#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }

echo "===== classes"
classes '/(EntityType|EntityTypes|Zombie|Husk|ZombieVillager|DeltaTracker|GuiGraphicsExtractor)\.class$'
classes '(ServerEntityEvents|ItemGroupEvents|ServerEntityLoad|EntityEvents|HudElement|ItemGroup|CreativeTab)[^$]*\.class$'

sig net.minecraft.world.entity.EntityType 'ZOMBIE|HUSK|spawn\('
sig net.minecraft.commands.CommandSourceStack 'ermission'
sig net.minecraft.world.entity.player.Player 'Message|Overlay|ActionBar'
sig net.minecraft.server.level.ServerPlayer 'Message|Overlay|ActionBar'
sig net.minecraft.client.gui.GuiGraphicsExtractor 'String|text|fill\(|guiWidth'
sig net.minecraft.world.entity.LivingEntity 'completeUsingItem|useItem;|hurtServer'
sig net.minecraft.world.entity.Mob 'goalSelector'
