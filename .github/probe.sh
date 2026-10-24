#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }

echo "===== stage 2"
classes 'fabric/api/loot/v3/[^$]*(\$[A-Za-z]+)?\.class$'
sig net.fabricmc.fabric.api.loot.v3.LootTableEvents '.'
sig 'net.fabricmc.fabric.api.loot.v3.LootTableEvents$Modify' '.'
sig net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder 'pool|modify'
sig 'net.minecraft.world.item.Item$Properties' 'public'
classes '/ToolMaterial\.class$'
sig net.minecraft.world.item.ToolMaterial 'static|public net'
sig 'net.minecraft.world.level.block.state.BlockBehaviour$Properties' 'setId|of\(|strength|sound|noOcclusion|ofFullCopy|ofLegacyCopy|instabreak|pushReaction'
sig net.minecraft.world.level.block.Block 'getStateForPlacement|createBlockStateDefinition| box\(|registerDefaultState|popResource'
sig net.minecraft.world.level.block.state.BlockBehaviour 'getShape|getCollisionShape|useItemOn|useWithoutItem|attack\(|getDrops'
sig net.minecraft.world.level.Level 'destroyBlockProgress|levelEvent|removeBlock|destroyBlock'
sig net.minecraft.world.level.block.state.properties.BlockStateProperties 'HORIZONTAL_FACING'
sig net.minecraft.world.level.block.state.properties.IntegerProperty 'create'
sig net.minecraft.world.item.Item 'useOn|InteractionResult use\(|hurtEnemy|postHurtEnemy'
sig net.minecraft.world.item.BlockItem 'BlockItem\('
sig net.minecraft.world.item.context.UseOnContext 'public'
sig 'net.minecraft.world.level.storage.loot.LootPool' 'lootPool|builder|static'
sig 'net.minecraft.world.level.storage.loot.LootPool$Builder' 'public'
sig net.minecraft.world.level.storage.loot.entries.LootItem 'lootTableItem'
sig net.minecraft.world.level.storage.loot.BuiltInLootTables 'VILLAGE_PLAINS|SIMPLE_DUNGEON|ABANDONED_MINESHAFT|VILLAGE_TOOLSMITH'
sig net.minecraft.world.level.storage.loot.providers.number.UniformGenerator 'between'
sig net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition 'randomChance'
sig net.minecraft.world.level.storage.loot.functions.SetItemCountFunction 'setCount'
sig net.minecraft.sounds.SoundEvents 'WOOD_HIT|ZOMBIE_ATTACK_WOODEN_DOOR|ZOMBIE_BREAK_WOODEN_DOOR|ANVIL_PLACE|WOOD_PLACE'
sig net.minecraft.tags.ItemTags 'PLANKS'
sig net.minecraft.world.level.block.SoundType 'WOOD'
classes 'fabric/api/client/keymapping/[^$]*\.class$|fabric/api/client/keybinding/[^$]*\.class$'
sig net.minecraft.world.entity.LivingEntity 'swing\(|getMainHandItem'
sig net.minecraft.world.entity.ai.navigation.PathNavigation 'isDone|moveTo|stop\(|getPath'
sig net.minecraft.world.level.block.state.BlockState 'is\(|getBlock'
sig net.minecraft.world.level.block.Blocks 'GLASS_PANE;|GLASS;'
sig net.minecraft.tags.BlockTags 'DOORS|IMPERMEABLE|GLASS_PANES'

echo "===== compile errors"
grep -E -A3 'error:' build.log | grep -v '^--$' | head -120 || true
