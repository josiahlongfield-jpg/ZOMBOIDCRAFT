#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }



echo "===== combat3"
sig net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper '.'
classes 'component/SwingAnimation|component/UseEffects|KeyMapping\$Category|keybinding/v1/KeyBindingHelper|state/HumanoidRenderState|state/ZombieRenderState|ai/goal/MeleeAttackGoal|ai/goal/ZombieAttackGoal'
sig net.minecraft.world.item.component.SwingAnimation '.'
sig net.minecraft.world.item.component.UseEffects '.'
sig 'net.minecraft.client.KeyMapping$Category' '.'
sig net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper '.'
sig net.minecraft.core.component.DataComponents 'USE|SWING|ATTACK|KINETIC|PIERC|MINIMUM'
sig net.minecraft.client.renderer.entity.state.HumanoidRenderState 'public'
sig net.minecraft.client.renderer.entity.state.ZombieRenderState 'public'
sig net.minecraft.client.player.LocalPlayer 'setXRot|turn|input|swing|isUsingItem'
sig net.minecraft.world.entity.Entity 'setXRot|setYRot|isVisuallySwimming|hasPose|turn\(|getRandom|distanceTo|hurtMarked'
sig net.minecraft.client.Options 'keyAttack|keyJump|keyUse|getCameraType|keyShift|keySprint'
sig net.minecraft.client.CameraType 'public'
sig net.minecraft.client.gui.GuiGraphicsExtractor 'public'
sig net.minecraft.world.entity.Mob 'isImmobile|aiStep|tick\('
sig net.minecraft.world.entity.LivingEntity 'isImmobile|swing|getSwimAmount|updateSwimAmount|isVisuallySwimming|addEffect|hasEffect|setPose'
sig net.minecraft.world.item.ItemStack 'hurtAndBreak|getDamageValue|getMaxDamage|isDamageableItem'
sig net.minecraft.client.multiplayer.ClientLevel 'playLocalSound|playSound'
sig net.minecraft.world.level.Level 'playLocalSound|playPlayerSound'
sig net.minecraft.client.renderer.entity.HumanoidMobRenderer 'public|protected'
sig net.minecraft.client.model.HumanoidModel 'setupAnim|public|protected'
sig net.minecraft.client.model.geom.ModelPart 'public'
sig net.minecraft.client.Minecraft 'options|player;|level;|gameMode|hitResult|getInstance|font;|screen;'
sig net.minecraft.world.entity.player.Player 'getAttackStrengthScale|isSecondaryUseActive|getAttackStrength'
sig com.mojang.blaze3d.platform.InputConstants 'KEY_R|KEY_V|KEY_B|Type'
sig net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry '.'
sig net.minecraft.world.entity.ai.goal.MeleeAttackGoal 'public|protected'
sig net.minecraft.world.effect.MobEffects 'SLOW|WEAK|MINING'
sig net.minecraft.server.level.ServerPlayer 'connection|hurtMarked|swing'
sig net.minecraft.world.entity.LivingEntity 'getUseItem|isUsingItem|getMainHandItem'
sig net.minecraft.client.player.AbstractClientPlayer 'public'
sig net.minecraft.client.renderer.entity.LivingEntityRenderer 'setupRotations|getBedOrientation|sleepDirection'
exit 0

echo "===== guns2"
classes 'item/properties/conditional/ConditionalItemModelProperties|item/properties/numeric/RangeSelectItemModelProperties|item/properties/select/SelectItemModelProperties|FabricItem\.class|component/CustomModelData\.class|HumanoidModel\$ArmPose|attachment/v1/AttachmentSyncPredicate|ClientTickEvents|PayloadTypeRegistry|ClientPlayNetworking|ServerPlayNetworking|LocalPlayer\.class|ItemModelGenerators|CustomModelDataProperty'
for c in $(for j in $MC_JARS; do unzip -Z1 "$j"; done | grep -E '^net/minecraft/client/.*\.class$' | sed 's/\.class$//;s|/|.|g' | grep -vE '\$[0-9]'); do :; done
echo "--- classes mentioning fov in method names"
for cls in net.minecraft.client.player.AbstractClientPlayer net.minecraft.client.player.LocalPlayer net.minecraft.client.renderer.GameRenderer net.minecraft.client.Camera net.minecraft.client.Options net.minecraft.world.entity.player.Player net.minecraft.client.renderer.ItemInHandRenderer; do
  echo "## $cls"; javap -cp "$CP" -p "$cls" 2>&1 | grep -iE 'fov|scop|zoom|ArmTransform|renderArmWithItem|renderHandsWithItems|renderItem\(|getFieldOfView|Bob' ; done
sig net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties '.'
sig net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties '.'
sig net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty '.'
sig net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty '.'
sig net.minecraft.client.renderer.item.properties.conditional.IsUsingItem '.'
sig net.minecraft.client.renderer.item.properties.numeric.UseDuration '.'
sig net.minecraft.client.renderer.item.properties.numeric.CustomModelDataProperty '.'
sig net.minecraft.util.ExtraCodecs\$LateBoundIdMapper 'public'
sig net.minecraft.world.item.component.CustomModelData '.'
sig net.fabricmc.fabric.api.item.v1.FabricItem '.'
sig 'net.minecraft.client.model.HumanoidModel$ArmPose' 'public static final|public'
sig net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate 'static'
sig net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry 'static|public'
sig net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking 'static'
sig net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking 'static'
sig net.minecraft.network.protocol.common.custom.CustomPacketPayload '.'
sig 'net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type' '.'
sig net.minecraft.network.codec.StreamCodec 'static'
sig net.minecraft.network.codec.ByteBufCodecs 'public static final'
sig net.minecraft.client.renderer.entity.player.AvatarRenderer 'ArmPose|getArmPose'
sig net.minecraft.client.renderer.entity.player.PlayerRenderer 'ArmPose|getArmPose'
classes 'renderer/entity/player/'
sig net.minecraft.world.entity.LivingEntity 'getTicksUsingItem|getUseItemRemainingTicks|releaseUsingItem|startUsingItem'
sig net.minecraft.world.entity.Entity 'getBoundingBox|getEyeY|getViewVector|getLookAngle|getEyePosition'
sig net.minecraft.server.level.ServerLevel 'sendParticles|playSound'
sig net.minecraft.world.level.Level 'playSound\(|playLocalSound|clip\('
sig net.minecraft.client.player.LocalPlayer 'public'
for j in $MC_JARS; do
  unzip -p "$j" assets/minecraft/models/item/crossbow.json 2>/dev/null
  unzip -p "$j" assets/minecraft/models/item/generated.json 2>/dev/null
  unzip -p "$j" assets/minecraft/models/item/spyglass_in_hand.json 2>/dev/null
done
exit 0

echo "===== signs"
for j in $MC_JARS; do unzip -l "$j" | grep -c "class" ; done
for j in $MC_JARS; do unzip -p "$j" net/minecraft/client/renderer/block/model/BlockElement\$Deserializer.class 2>/dev/null | strings | grep -i "emission\|shade" ; done
javap -cp "$CP" -p 'net.minecraft.client.renderer.block.model.BlockElement' 2>&1 | head -20

echo "===== weather4"
javap -cp "$CP" -p net.minecraft.world.entity.Entity 2>&1 | grep -i "hurt\|mark\|sync\|dirty\|impulse\|push"
javap -cp "$CP" -p net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket 2>&1 | head -20

echo "===== stage 3 rendering"
classes 'client/renderer/entity/(Zombie|AbstractZombie|HumanoidMob|LivingEntity|Mob)Renderer|client/renderer/entity/state/(Zombie|Humanoid|LivingEntity)RenderState|client/model/(Zombie|AbstractZombie|Humanoid)Model|client/model/geom/ModelLayers|EntityRendererRegistry|EntityRendererProvider|AnimationUtils' 
sig net.minecraft.client.renderer.entity.ZombieRenderer '.'
sig net.minecraft.client.renderer.entity.AbstractZombieRenderer '.'
sig net.minecraft.client.renderer.entity.HumanoidMobRenderer '.'
sig net.minecraft.client.renderer.entity.LivingEntityRenderer 'public|protected'
sig net.minecraft.client.renderer.entity.state.ZombieRenderState '.'
sig net.minecraft.client.renderer.entity.state.HumanoidRenderState '.'
sig net.minecraft.client.renderer.entity.state.LivingEntityRenderState '.'
sig net.minecraft.client.model.ZombieModel '.'
sig net.minecraft.client.model.AbstractZombieModel '.'
sig net.minecraft.client.model.HumanoidModel 'public|protected'
sig net.minecraft.client.model.AnimationUtils '.'
sig net.minecraft.client.model.geom.ModelLayers 'ZOMBIE|HUSK'
sig net.minecraft.client.model.geom.ModelPart 'public float|public void|public boolean'
sig net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry '.'
sig net.minecraft.client.renderer.entity.EntityRendererProvider\$Context 'public'
sig net.minecraft.client.renderer.entity.EntityRenderer 'public|protected'
echo "===== stage 3 model layers"
classes 'EntityModelLayerRegistry|ModelLayerRegistry|client/model/geom/(PartPose|ModelLayerLocation)|client/model/geom/builders/(LayerDefinition|MeshDefinition|PartDefinition|CubeListBuilder|CubeDeformation)'
sig net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry '.'
sig net.minecraft.client.model.geom.ModelLayerLocation 'public'
sig net.minecraft.client.model.geom.PartPose 'public static'
sig net.minecraft.client.model.geom.builders.LayerDefinition 'public static'
sig net.minecraft.client.model.geom.builders.PartDefinition 'public'
sig net.minecraft.client.model.geom.builders.CubeListBuilder 'texOffs|addBox|create'
sig net.minecraft.client.model.geom.ModelPart 'getChild'
echo "===== stage 3 AI"
sig net.minecraft.world.entity.ai.goal.GoalSelector 'public'
sig net.minecraft.world.entity.ai.goal.WrappedGoal 'public'
sig net.minecraft.world.entity.ai.goal.target.TargetGoal 'public|protected'
sig net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal 'public|protected'
sig net.minecraft.world.entity.Mob 'targetSelector|getSensing|hasLineOfSight|setTarget|getTarget'
sig net.minecraft.world.entity.ai.sensing.Sensing 'public'
sig net.minecraft.world.entity.LivingEntity 'getVoicePitch|yHeadRot|hasLineOfSight|getViewVector'
sig net.minecraft.world.entity.Entity 'getViewVector|getEyePosition|isDiscrete|isCrouching|isShiftKeyDown|getLookAngle'
sig net.minecraft.world.level.Level 'getMaxLocalRawBrightness|getRawBrightness|getBrightness'
sig net.minecraft.world.level.LevelReader 'getMaxLocalRawBrightness|getRawBrightness'
sig net.minecraft.world.level.BlockAndTintGetter 'getRawBrightness|getBrightness'
sig net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate '.'

echo "===== stage 3b combat"
classes 'networking/v1/(PayloadTypeRegistry|ServerPlayNetworking|ClientPlayNetworking)|custom/CustomPacketPayload|network/codec/StreamCodec\.class|world/entity/EntityDimensions|world/entity/Pose\.class|attachment/v1/AttachmentRegistry|ServerLivingEntityEvents|ClientTickEvents'
sig net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry '.'
sig net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking 'public static'
sig net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking 'public static'
sig net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking\$Context '.'
sig net.minecraft.network.protocol.common.custom.CustomPacketPayload '.'
sig net.minecraft.network.protocol.common.custom.CustomPacketPayload\$Type '.'
sig net.minecraft.network.codec.StreamCodec 'static'
sig net.minecraft.world.entity.EntityDimensions 'public'
sig net.minecraft.world.entity.Pose 'public static final'
sig net.minecraft.world.entity.Entity 'Dimensions|refreshDimensions|setPose|getPose|spawnAtLocation|push\(|setDeltaMovement|getDeltaMovement|hurtMarked|onGround|getXRot|getYRot|isPassenger|damageSources|getBbHeight|level\(\)'
sig net.minecraft.world.entity.LivingEntity 'knockback|hurtServer|getAttribute|getHealth|getMaxHealth|isDeadOrDying|swing|setSprinting|getJumpPower|jumpFromGround|travel|aiStep|isImmobile|serverAiStep|getFallFlyingTicks|causeFallDamage|setLastHurtByMob|getLastHurtByMob|hurtTime|invulnerableTime|playHurtSound'
sig net.minecraft.world.entity.Mob 'getNavigation|getMoveControl|getJumpControl|playAmbientSound|getAmbientSound|isNoAi|setNoAi|doHurtTarget|serverAiStep|goalSelector|setAggressive|isAggressive|getMaxHeadYRot'
sig net.minecraft.world.entity.player.Player 'setForcedPose|getForcedPose|getAbilities|updatePlayerPose|canPlayerFitWithinBlocksAndEntitiesWhen|isSwimming|hurtServer|getDefaultDimensions'
sig net.minecraft.world.entity.ai.attributes.Attributes 'public static final'
sig net.minecraft.world.entity.ai.goal.Goal 'public|protected'
sig net.minecraft.world.entity.ai.goal.Goal\$Flag '.'
sig net.minecraft.world.entity.ai.navigation.PathNavigation 'stop|moveTo|isDone'
sig net.minecraft.world.entity.monster.zombie.Zombie 'public|protected'
sig net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents '.'
sig net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents\$AllowDamage '.'
sig net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry 'public'
sig net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry\$Builder 'public'
sig net.fabricmc.fabric.api.attachment.v1.AttachmentTarget 'public'
sig net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents '.'
sig net.minecraft.client.KeyMapping 'public'
sig net.minecraft.client.renderer.entity.LivingEntityRenderer 'setupRotations|getFlipDegrees|isShaking|extractRenderState|scale'
sig net.minecraft.client.renderer.entity.state.LivingEntityRenderState 'public'
sig net.minecraft.client.renderer.entity.state.EntityRenderState 'public'
sig com.mojang.blaze3d.vertex.PoseStack 'public'
sig com.mojang.math.Axis 'public'
sig net.minecraft.world.damagesource.DamageSources 'mobAttack|playerAttack|fall|generic'
sig net.minecraft.world.entity.item.ItemEntity 'public ItemEntity'
sig net.minecraft.world.entity.EquipmentSlot 'public static final'
sig net.minecraft.world.entity.Entity 'getItemBySlot|setItemSlot'
sig net.minecraft.world.entity.LivingEntity 'getItemBySlot|setItemSlot|dropCustomDeathLoot|dropAllDeathLoot|dropFromLootTable'
sig net.minecraft.sounds.SoundEvents 'ZOMBIE_|PLAYER_ATTACK_KNOCKBACK|PLAYER_ATTACK_STRONG|GENERIC_SMALL_FALL|PLAYER_HURT|SKELETON_STEP|BONE_BLOCK'
sig net.minecraft.world.level.Level 'playSound\('
sig net.minecraft.server.level.ServerLevel 'sendParticles'
sig net.minecraft.core.particles.ParticleTypes 'BLOCK|DAMAGE|CRIT|SWEEP'

echo "===== stage addon layer"
sig net.minecraft.client.renderer.entity.layers.RenderLayer '.'
sig net.minecraft.client.renderer.entity.LivingEntityRenderer 'addLayer|layers|getModel'
sig net.minecraft.client.model.geom.ModelPart 'public'
sig net.minecraft.client.model.HumanoidModel 'public|protected'
sig net.minecraft.client.model.Model 'public'
sig net.minecraft.client.renderer.SubmitNodeCollector 'submitModel'
sig net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer 'public|protected|private'

echo "===== stage city blocks"
sig net.minecraft.world.level.block.state.BlockBehaviour\$Properties 'public'
sig net.minecraft.world.level.block.Block 'public static .*box|codec|getStateForPlacement|createBlockStateDefinition|protected .*getShape|getCollisionShape|getLightBlock|propagatesSkylightDown|skipRendering|getShadeBrightness|rotate|mirror'
sig net.minecraft.world.level.block.RotatedPillarBlock 'public'
sig net.minecraft.world.level.block.HorizontalDirectionalBlock 'public|protected'
sig net.minecraft.world.phys.shapes.Shapes 'public static'
sig net.minecraft.world.level.block.SoundType 'public static final'
sig net.minecraft.world.item.context.BlockPlaceContext 'getHorizontalDirection|getClickedFace|getNearestLookingDirection'
sig net.minecraft.client.renderer.ItemBlockRenderTypes 'public'
sig net.minecraft.client.renderer.chunk.ChunkSectionLayer '.'
sig net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap '.'
sig net.fabricmc.fabric.api.blockview.v2.FabricBlockView '.'
sig net.minecraft.world.item.BlockItem 'public BlockItem'
sig net.minecraft.world.item.Item\$Properties 'setId|useBlockDescriptionPrefix'
sig net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup '.'
sig net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab '.'
sig net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents '.'
sig net.minecraft.world.item.CreativeModeTab 'public'
sig net.minecraft.world.item.CreativeModeTab\$Builder 'public'
classes 'BlockRenderLayerMap|ChunkSectionLayer|ItemBlockRenderTypes|layers/EyesLayer|rendertype/RenderTypes|CreativeModeTabEvents|FabricCreativeModeTab|FabricItemGroup'
sig net.minecraft.client.renderer.entity.layers.EyesLayer '.'
sig net.minecraft.client.renderer.rendertype.RenderTypes 'eyes|Emissive|entityCutout|entityTranslucent'

echo "===== compile errors"
grep -E -A3 'error:' build.log | grep -v '^--$' | head -120 || true

echo "===== block render layers"
for j in $MC_JARS; do
  for m in glass black_stained_glass glass_pane_noside oak_leaves cobweb; do
    unzip -p "$j" "assets/minecraft/models/block/$m.json" 2>/dev/null && echo " <- $m in $j"
  done
done
javap -cp "$CP" -p net.minecraft.client.renderer.ItemBlockRenderTypes 2>&1 | head -30
classes 'ChunkSectionLayer|RenderLayerHelper|BlockRenderLayer'
javap -cp "$CP" -p net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper 2>&1 | head -20

echo "===== weather3"
javap -cp "$CP" -p -c net.minecraft.world.attribute.WeatherAttributes 2>&1 | grep -v "^ *[0-9]*: \(aload\|astore\|dup\|pop\|areturn\|return\|iload\|fload\|fstore\)" | head -150
javap -cp "$CP" -p 'net.minecraft.world.attribute.WeatherAttributes$WeatherAccess' 2>&1 | head -20
javap -cp "$CP" -p -c net.minecraft.client.renderer.LightmapRenderStateExtractor 2>&1 | grep -E "Field|Method|ldc|public|private" | head -80
javap -cp "$CP" -p -c net.minecraft.world.level.Level 2>&1 | grep -A30 "public int getSkyDarken\|public void updateSkyBrightness\|public float getSunAngle\|getRainLevel(float)" | grep -E "Field|Method|ldc|public|fconst" | head -60
javap -cp "$CP" -p net.minecraft.world.attribute.modifier.ColorModifier 'net.minecraft.world.attribute.modifier.ColorModifier$BlendToGray' net.minecraft.world.attribute.modifier.FloatModifier 'net.minecraft.world.attribute.modifier.AttributeModifier$OperationId' 2>&1 | head -80
for j in $MC_JARS; do
  if unzip -l "$j" data/minecraft/timeline/day.json >/dev/null 2>&1; then
    unzip -p "$j" data/minecraft/timeline/day.json > /tmp/day.json
    python3 -c "import json; d=json.load(open('/tmp/day.json')); [print(k, json.dumps(v)[:900]) for k,v in d['tracks'].items() if 'visual' in k or 'light' in k]"
    unzip -p "$j" data/minecraft/tags/timeline/in_overworld.json; echo " <- in_overworld tag"
    unzip -p "$j" data/minecraft/world_clock/overworld.json; echo " <- clock"
    break
  fi
done
exit 0

echo "===== weather2"
for c in net.minecraft.world.attribute.EnvironmentAttributeLayer 'net.minecraft.world.attribute.EnvironmentAttributeLayer$Constant' 'net.minecraft.world.attribute.EnvironmentAttributeLayer$TimeBased' 'net.minecraft.world.attribute.EnvironmentAttributeLayer$Positional' 'net.minecraft.world.attribute.EnvironmentAttributeSystem$Builder' net.minecraft.world.attribute.EnvironmentAttributeSystem net.minecraft.world.attribute.EnvironmentAttribute net.minecraft.world.attribute.modifier.AttributeModifier net.minecraft.world.level.saveddata.WeatherData net.minecraft.client.renderer.Lightmap net.minecraft.client.renderer.LightmapRenderStateExtractor net.minecraft.client.renderer.state.LightmapRenderState net.minecraft.world.entity.LightningBolt net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment net.minecraft.client.renderer.fog.environment.FogEnvironment net.minecraft.client.renderer.fog.FogData; do
  echo "----- $c"; javap -cp "$CP" -p "$c" 2>&1 | head -70
done
classes 'attribute/|WeatherAttributes|weather/'
for j in $MC_JARS; do
  unzip -p "$j" data/minecraft/timeline/day.json 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); [print(k, json.dumps(v)[:600]) for k,v in d['tracks'].items() if 'visual' in k or 'light' in k]" && echo " <- day visual tracks"
  unzip -p "$j" data/minecraft/tags/timeline/in_overworld.json 2>/dev/null && echo " <- in_overworld tag"
  unzip -p "$j" data/minecraft/world_clock/overworld.json 2>/dev/null && echo " <- clock"
  break
done
# where vanilla builds the attribute system and applies weather
javap -cp "$CP" -p -c net.minecraft.world.level.Level 2>/dev/null | grep -n -B2 -A40 "EnvironmentAttributeSystem addDefaultLayers\|static .*EnvironmentAttributeSystem\|addWeather\|WeatherAttributes" | head -120
javap -cp "$CP" -p -c net.minecraft.world.attribute.EnvironmentAttributeSystem 2>/dev/null | grep -n "invoke\|Method\|static" | head -80
exit 0

echo "===== weather"
classes 'attribute/Environment|EnvironmentAttribute|timeline/|Timeline|level/storage/WeatherData|WeatherData|client/renderer/fog/|FogRenderer|SkyRenderer|LightTexture|Lightmap|CloudRenderer|WeatherEffectRenderer|DimensionSpecialEffects|entity/LightningBolt|Hail|world/clock|WorldClock|GameRules$'
sig net.minecraft.world.level.Level 'Rain|Thunder|rain|thunder|Weather|weather|Brightness|DayTime|dayTime|TimeOfDay|SkyDarken|skyDarken|environmentAttributes|Attributes'
sig net.minecraft.client.multiplayer.ClientLevel 'Sky|sky|Cloud|cloud|Star|Flash|Rain|Weather|Darken|Attributes|effects'
sig net.minecraft.server.level.ServerLevel 'Weather|weather|Rain|Thunder|Lightning|lightning|setDayTime|tickTime|advanceWeather'
for c in $(classes 'attribute/EnvironmentAttributes\.class$|attribute/EnvironmentAttributeSystem\.class$|attribute/EnvironmentAttributeMap\.class$|attribute/EnvironmentAttributeReader\.class$|attribute/EnvironmentAttributeProbe\.class$|fog/FogRenderer\.class$|renderer/LightTexture\.class$|renderer/SkyRenderer\.class$|renderer/WeatherEffectRenderer\.class$|storage/WeatherData\.class$|gamerules/GameRules\.class$'); do
  sig "$(echo ${c%.class} | tr / .)" 'public|protected'
done
for j in $MC_JARS; do
  unzip -p "$j" data/minecraft/dimension_type/overworld.json 2>/dev/null && echo " <- overworld dimension type"
  unzip -Z1 "$j" | grep -E 'data/minecraft/(timeline|world_clock|environment)' | head -20
  for f in $(unzip -Z1 "$j" | grep -E 'data/minecraft/timeline/.*json' | head -4); do echo "--- $f"; unzip -p "$j" "$f" | head -80; done
  unzip -p "$j" data/minecraft/worldgen/biome/plains.json 2>/dev/null | head -60 && echo " <- plains biome"
done
