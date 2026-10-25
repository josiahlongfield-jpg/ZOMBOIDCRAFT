#!/usr/bin/env bash
# Prints Minecraft / Fabric API class names and signatures to help fix compile errors.
MC_JARS=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-*.jar' 2>/dev/null)
FAPI_JARS=$(find ~/.gradle/caches/modules-2 -path '*net.fabricmc.fabric-api*' -name '*.jar' ! -name '*sources*' 2>/dev/null)
CP=$(echo $MC_JARS $FAPI_JARS | tr ' ' ':')
echo "MC jars: $MC_JARS"
classes() { for j in $MC_JARS $FAPI_JARS; do unzip -Z1 "$j"; done | grep -E "$1" | sort -u; }
sig() { echo "----- $1"; javap -cp "$CP" -protected "$1" 2>&1 | grep -E "$2"; }


echo "===== bench2"
javap -cp "$CP" -p net.minecraft.client.renderer.entity.state.ItemEntityRenderState 2>&1
javap -cp "$CP" -c -p net.minecraft.world.entity.item.ItemEntity 2>&1 | sed -n '/public static float getSpin(float, float)/,/^$/p'
javap -cp "$CP" -c -p net.minecraft.client.renderer.entity.ItemEntityRenderer 2>&1 | sed -n '/public void submit(net.minecraft.client.renderer.entity.state.ItemEntityRenderState/,/^$/p' | grep -E "invoke|ldc|getfield|fload|fmul|fadd|fdiv|fneg"
javap -cp "$CP" -c -p net.minecraft.client.gui.render.pip.GuiEntityRenderer 2>&1 | sed -n '/protected void renderToTexture(net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState/,/^$/p;/protected float getTranslateY/,/^$/p' | grep -E "invoke|ldc|getfield|fload|fmul|fadd|fdiv|iload|i2f|freturn"
javap -cp "$CP" -c -p net.minecraft.client.gui.render.pip.PictureInPictureRenderer 2>&1 | sed -n '/public void prepare(/,/^$/p' | grep -E "invoke|ldc|getfield|fmul|i2f|iload|fload"
javap -cp "$CP" -c -p net.minecraft.client.renderer.item.ItemStackRenderState 2>&1 | sed -n '/public net.minecraft.world.phys.AABB getModelBoundingBox()/,/^$/p;/public void visitExtents(/,/^$/p' | grep -E "invoke|ldc|getfield"
javap -cp "$CP" -c -p 'net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState' 2>&1 | sed -n '/void visitExtents(/,/^$/p;/void applyTransform(/,/^$/p' | grep -E "invoke|ldc|getfield"
javap -cp "$CP" -p net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState 2>&1 | head -20
javap -cp "$CP" -c -p net.minecraft.client.gui.GuiGraphicsExtractor 2>&1 | sed -n '/public void entity(/,/^$/p' | grep -E "invoke|new"
javap -cp "$CP" -c -p net.minecraft.client.gui.screens.inventory.InventoryScreen 2>&1 | sed -n '/public static void extractEntityInInventory(/,/^$/p' | grep -E "invoke|ldc|getstatic"
exit 0

echo "===== bench1"
classes 'gui/render/pip/|gui/render/state/pip/|SpecialGuiElement|gui/render/state/GuiItemRenderState|state/ItemClusterRenderState|state/ItemEntityRenderState|state/ItemDisplayEntityRenderState|state/DisplayEntityRenderState'
for c in $(classes 'gui/render/pip/[A-Za-z]*.class|gui/render/state/pip/[A-Za-z]*.class|SpecialGuiElement' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do sig "$c" 'public|protected'; done
sig net.minecraft.client.gui.GuiGraphicsExtractor 'submit|entity|Entity|pip|Picture|item\(|scissor|enableScissor|nextStratum|guiRenderState'
sig net.minecraft.client.renderer.entity.state.ItemClusterRenderState 'public'
sig net.minecraft.client.renderer.entity.ItemEntityRenderer 'public|static'
sig net.minecraft.client.Minecraft 'getItemModelResolver|getEntityRenderDispatcher|gameRenderer|itemModelResolver'
sig net.minecraft.client.renderer.MultiBufferSource 'public|static'
sig net.minecraft.client.renderer.SubmitNodeCollector 'submitItem|submitModel'
classes 'client/renderer/feature/FeatureRenderDispatcher|client/renderer/SubmitNodeStorage|client/renderer/SubmitNodeCollection'
sig net.minecraft.client.renderer.feature.FeatureRenderDispatcher 'public'
sig net.minecraft.client.renderer.SubmitNodeStorage 'public'
sig net.minecraft.client.renderer.GameRenderer 'getFeatureRenderDispatcher|getSubmitNodeStorage|featureRender|submitNode|getLighting|lighting'
sig net.minecraft.client.renderer.rendertype.RenderTypes 'itemEntityTranslucentCull|translucent|entityTranslucent'
exit 0

echo "===== render2"
echo "$MC_JARS" | tr ' ' '\n'
classes 'client/(menu|screenhandler)/|gui/screens/MenuScreens'
for c in $(classes 'client/(menu|screenhandler)/' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do sig "$c" '.'; done
sig 'net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback$RegistrationHelper' '.'
for j in $MC_JARS; do echo "-- $j"; javap -cp "$j" -p net.minecraft.client.gui.screens.MenuScreens 2>&1 | grep -E "register|ScreenConstructor"; done
javap -cp "$CP" -c -p 'net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState' 2>&1 | sed -n '/void submit(/,/^$/p' | grep -E "invoke|ldc" | head -12
sig com.mojang.math.Axis 'YP|rotationDegrees'
sig net.minecraft.client.renderer.texture.OverlayTexture 'NO_OVERLAY'
sig net.minecraft.client.model.geom.ModelPart 'translateAndRotate'
sig net.minecraft.client.gui.screens.inventory.AbstractContainerScreen 'protected|public'
find ~/.gradle/caches -name '*.accesswidener' -o -name '*.classtweaker' 2>/dev/null | head
for j in $FAPI_JARS; do unzip -p "$j" '*.accesswidener' '*.classtweaker' 2>/dev/null | grep -iE "MenuScreens|ScreenConstructor"; done | sort -u
exit 0

echo "===== render1"
classes 'rendering/v1/.*(Layer|RenderState|Feature)|renderer/entity/player/|renderer/entity/state/(Avatar|Player|HumanoidRender|LivingEntityRender|EntityRender)|client/model/player/|model/(PlayerModel|HumanoidModel)|renderer/item/(ItemModelResolver|ItemStackRenderState)|layers/(ItemInHandLayer|CustomHeadLayer|CapeLayer|PlayerItemInHandLayer)|state/v1|renderstate'
for c in $(classes 'rendering/v1/.*(LayerRegistration|RenderState)|renderstate|state/v1' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do sig "$c" '.'; done
for c in $(classes 'renderer/entity/player/[A-Za-z]*Renderer.class|state/(AvatarRenderState|PlayerRenderState).class|model/player/PlayerModel.class|model/PlayerModel.class' | sed 's/\.class$//; s#/#.#g'); do sig "$c" 'public|protected'; done
sig net.minecraft.client.renderer.entity.state.EntityRenderState 'public'
sig net.minecraft.client.renderer.entity.state.HumanoidRenderState 'public'
sig net.minecraft.client.model.HumanoidModel 'public'
sig net.minecraft.client.renderer.item.ItemModelResolver 'public'
sig net.minecraft.client.renderer.item.ItemStackRenderState 'public'
sig net.minecraft.client.renderer.entity.layers.CustomHeadLayer '.'
javap -cp "$CP" -c -p net.minecraft.client.renderer.entity.layers.CustomHeadLayer 2>&1 | sed -n '/void submit(/,/^$/p' | grep -E "invoke|ldc|getfield" | head -40
sig net.minecraft.client.renderer.entity.EntityRendererProvider\$Context 'public'
sig net.minecraft.world.item.ItemDisplayContext 'public static final'


echo "===== nearby1"
classes 'screenhandler/v1|menu/v1|world/inventory/(AbstractCraftingMenu|RecipeBookMenu|CraftingMenu|ResultSlot|ArmorSlot|TransientCraftingContainer|CraftingContainer|ResultContainer|ContainerData|MenuType|ChestMenu)|ContainerUser|ContainerOpenersCounter|entity/item/ItemEntity.class|block/entity/(ChestBlockEntity|BarrelBlockEntity|RandomizableContainerBlockEntity|BaseContainerBlockEntity)|RandomizableContainer.class'
for c in $(classes 'screenhandler/v1|menu/v1' | grep -v '\$' | sed 's/\.class$//; s#/#.#g'); do sig "$c" '.'; done
sig net.minecraft.world.inventory.AbstractCraftingMenu '.'
sig net.minecraft.world.inventory.RecipeBookMenu '.'
sig net.minecraft.world.inventory.InventoryMenu '.'
javap -cp "$CP" -p net.minecraft.world.inventory.InventoryMenu 2>&1
javap -cp "$CP" -c -p net.minecraft.world.inventory.InventoryMenu 2>&1 | sed -n '/public net.minecraft.world.inventory.InventoryMenu(/,/^$/p' | grep -E "invoke|new|getstatic|bipush|iconst" | head -60
javap -cp "$CP" -c -p net.minecraft.world.inventory.InventoryMenu 2>&1 | sed -n '/void slotsChanged/,/^$/p;/void removed/,/^$/p' | grep -E "invoke|getstatic|getfield" | head -30
sig net.minecraft.world.inventory.CraftingMenu 'static|public'
sig net.minecraft.world.inventory.ResultSlot 'public'
sig net.minecraft.world.inventory.ArmorSlot '.'
javap -cp "$CP" -p net.minecraft.world.inventory.ArmorSlot 2>&1 | head -5
sig net.minecraft.world.inventory.TransientCraftingContainer 'public'
sig net.minecraft.world.inventory.MenuType 'public'
sig net.minecraft.world.Container 'startOpen|stopOpen|stillValid|getEntities|ContainerUser'
sig net.minecraft.world.entity.ContainerUser '.'
sig net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity 'public|protected'
sig net.minecraft.world.RandomizableContainer 'public|default|static'
sig net.minecraft.world.level.block.entity.BaseContainerBlockEntity 'public|protected'
sig net.minecraft.world.level.block.entity.ChestBlockEntity 'public|protected'
sig net.minecraft.world.level.block.entity.BarrelBlockEntity 'public|protected'
sig net.minecraft.world.level.block.ChestBlock 'static|getContainer'
sig net.minecraft.world.entity.item.ItemEntity 'public'
sig net.minecraft.server.level.ServerPlayer 'openMenu|closeContainer|containerMenu|initMenu|doCloseContainer'
sig net.minecraft.world.entity.player.Player 'containerMenu|inventoryMenu|closeContainer|canInteractWith|isWithinBlockInteractionRange|blockInteractionRange'
sig net.minecraft.world.level.Level 'getEntitiesOfClass|getBlockEntity'
sig net.minecraft.client.gui.screens.MenuScreens 'public|static'
sig net.minecraft.core.BlockPos 'STREAM_CODEC'
sig net.minecraft.network.chat.ComponentSerialization 'STREAM_CODEC'
sig net.minecraft.network.codec.ByteBufCodecs 'list|collection|VAR_INT|STRING_UTF8'
sig net.minecraft.world.item.ItemStack 'STREAM_CODEC'
sig net.minecraft.world.inventory.AbstractContainerMenu 'public|protected'
sig net.minecraft.world.inventory.Slot 'public'
sig net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking 'canSend|send'
grep -E "error:|warning: \[removal" build.log | head -20
exit 0

echo "===== carry1"
classes 'client/item/v1/|attachment/v1/AttachmentRegistry|attachment/v1/AttachmentRegistry\$Builder|world/ContainerHelper|entity/v1/ServerPlayerEvents|entity/event/v1/ServerPlayerEvents|item/ItemStackTemplate'
sig net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback '.'
sig 'net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry$Builder' '.'
sig net.minecraft.world.ContainerHelper 'public'
sig net.minecraft.world.item.Item 'Hover|Tooltip|onDestroyed|inventoryTick|canFitInside|overrideStackedOnOther|overrideOtherStackedOnMe'
sig net.minecraft.world.entity.player.Player ' drop\(|hasInfiniteMaterials|isCreative|getInventory'
sig net.minecraft.world.Container 'public|default'
sig net.minecraft.world.SimpleContainer 'public'
sig net.minecraft.world.item.ItemStackTemplate 'public'
sig net.minecraft.world.item.ItemStack 'OPTIONAL_CODEC|CODEC|isSameItemSameComponents|copyWithCount|getMaxStackSize|isStackable|limitSize|split'
sig net.minecraft.core.NonNullList 'public static'
javap -cp "$CP" -c -p net.minecraft.world.inventory.InventoryMenu 2>&1 | sed -n '/quickMoveStack(net.minecraft.world.entity.player.Player, int)/,/^$/p' | grep -E "bipush|sipush|iconst|if_|invoke" | head -60
javap -cp "$CP" -c -p net.minecraft.world.entity.player.Inventory 2>&1 | sed -n '/public boolean add(int, net.minecraft.world.item.ItemStack)/,/^$/p' | grep -E "invoke|ireturn" | head -30
javap -cp "$CP" -c -p net.minecraft.world.entity.player.Inventory 2>&1 | sed -n '/public void dropAll()/,/^$/p' | grep -E "invoke" | head -20
exit 0

echo "===== inventory2"
echo "## InventoryScreen.init / constructor bytecode"
javap -cp "$CP" -c -p net.minecraft.client.gui.screens.inventory.InventoryScreen 2>&1 | sed -n '/void init()/,/^$/p' | head -60
echo "## Minecraft: methods mentioning Screen"
javap -cp "$CP" -p net.minecraft.client.Minecraft 2>&1 | grep -i "screen"
echo "## Window"
javap -cp "$CP" -p com.mojang.blaze3d.platform.Window 2>&1 | grep -iE "scale|width|height"
echo "## Options gui scale"
javap -cp "$CP" -p net.minecraft.client.Options 2>&1 | grep -iE "guiScale|unicode"
javap -cp "$CP" -p net.minecraft.client.Minecraft 2>&1 | grep -iE "resize|getWindow"
echo "## Gui setScreen"
javap -cp "$CP" -p net.minecraft.client.gui.Gui 2>&1 | grep -iE "screen"
echo "## Minecraft: call sites creating InventoryScreen"
javap -cp "$CP" -c -p net.minecraft.client.Minecraft 2>&1 | grep -n -B3 -A12 "class net/minecraft/client/gui/screens/inventory/InventoryScreen" | head -80
exit 0

echo "===== inventory1"
classes 'gui/screens/inventory/(InventoryScreen|AbstractContainerScreen|AbstractRecipeBookScreen|EffectsInInventory|CreativeModeInventoryScreen|ContainerScreen)\.class|world/inventory/(InventoryMenu|AbstractContainerMenu|Slot|ChestMenu|MenuType)\.class|gui/screens/MenuScreens|renderer/RenderPipelines|resources/(Identifier|ResourceLocation)\.class|screenhandler|menu/v1|screen/v1/Screen|item/component/(ItemContainerContents|BundleContents)\.class|client/gui/render/state|GuiItemRenderState|client/renderer/state/'
sig net.minecraft.client.gui.screens.inventory.InventoryScreen '.'
sig net.minecraft.client.gui.screens.inventory.AbstractContainerScreen '.'
sig net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen '.'
sig net.minecraft.client.gui.screens.Screen 'protected|public'
sig net.minecraft.client.gui.GuiGraphicsExtractor '.'
sig net.minecraft.client.gui.screens.MenuScreens 'public'
sig net.minecraft.world.inventory.InventoryMenu 'public'
sig net.minecraft.world.inventory.AbstractContainerMenu 'public|protected'
sig net.minecraft.world.inventory.Slot 'public'
sig net.minecraft.world.inventory.MenuType 'public'
sig net.minecraft.client.renderer.RenderPipelines 'GUI'
sig net.minecraft.world.entity.player.Inventory 'public'
sig net.minecraft.world.item.component.ItemContainerContents 'public'
sig net.minecraft.client.Minecraft 'setScreen|getWindow|gameRenderer'
sig net.minecraft.client.gui.components.AbstractWidget 'public'
sig net.minecraft.client.gui.components.Renderable '.'
classes 'fabric/api/(screenhandler|menu|client/screen)'
sig net.fabricmc.fabric.api.client.screen.v1.ScreenEvents '.'
exit 0



echo "===== arms"
sig net.minecraft.client.renderer.ItemInHandRenderer '.'
javap -cp "$CP" -c -p net.minecraft.client.renderer.ItemInHandRenderer 2>&1 | grep -A70 'void renderPlayerArm(' | head -90
javap -cp "$CP" -c -p net.minecraft.client.renderer.ItemInHandRenderer 2>&1 | grep -A60 'void renderHandsWithItems(' | grep -E 'invoke|getfield|Method|Field|fload|ldc' | head -60
javap -cp "$CP" -c -p net.minecraft.client.renderer.ItemInHandRenderer 2>&1 | grep -A40 'void renderArmWithItem(' | head -45
javap -cp "$CP" -c -p net.minecraft.client.renderer.entity.player.AvatarRenderer 2>&1 | grep -A50 'private void renderHand(' | head -60
sig net.minecraft.client.renderer.GameRenderer 'bob|Bob|renderItemInHand|ItemInHand'
javap -cp "$CP" -c -p net.minecraft.client.renderer.GameRenderer 2>&1 | grep -B2 -A40 'void renderItemInHand(' | grep -E 'invoke|Method|getfield' | head -40
sig net.minecraft.client.renderer.SubmitNodeCollector '.'
sig net.minecraft.client.renderer.OrderedSubmitNodeCollector 'submitModelPart|submitModel'
sig net.minecraft.world.entity.player.PlayerSkin '.'
sig net.minecraft.core.ClientAsset\$Texture '.'
sig net.minecraft.client.renderer.entity.EntityRenderDispatcher 'Player|Avatar|getRenderer'
sig net.minecraft.client.player.LocalPlayer 'Bob|bob'
sig net.minecraft.client.player.AbstractClientPlayer 'Skin|skin'
sig net.minecraft.core.particles.DustParticleOptions 'public'
sig net.minecraft.world.level.block.LightBlock 'LEVEL|public static'
sig com.mojang.blaze3d.platform.Window 'GuiScale|getWidth|getHeight'
sig net.minecraft.client.multiplayer.ClientLevel 'setBlock|players\(|addParticle'
classes 'rendertype/RenderTypes|renderer/RenderType\.class|rendertype/RenderType\.class'
sig net.minecraft.client.renderer.rendertype.RenderTypes 'entityTranslucent|entityCutout|entitySolid'
sig net.minecraft.client.renderer.RenderType 'entityTranslucent|entityCutout'
sig net.minecraft.client.model.geom.ModelPart 'resetPose|render|visible|xRot|public float'
sig net.minecraft.client.renderer.texture.OverlayTexture 'NO_OVERLAY'
sig net.minecraft.world.entity.HumanoidArm '.'

echo "===== crosshair"
sig net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement '.'
sig net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements 'CROSSHAIR'
sig net.minecraft.client.gui.Gui 'Crosshair|crosshair'
sig org.joml.Matrix3x2fStack 'push|pop'
sig org.joml.Matrix3x2f 'translate\(float, float\)|scale\(float, float\)'
sig net.minecraft.world.food.FoodProperties 'public'
sig net.minecraft.world.item.component.Consumable 'public'
sig net.minecraft.world.item.component.Consumables 'public'
classes 'item/component/Consumable|consume_effects|ItemUseAnimation'

echo "===== volume"
classes 'client/sounds/SoundEngine|client/sounds/SoundManager|client/sounds/ChannelAccess|options/SoundOptionsScreen|options/OptionsSubScreen|components/AbstractSliderButton|screen/v1/ScreenEvents|screen/v1/Screens|client/OptionInstance|components/OptionsList'
sig net.minecraft.client.sounds.SoundEngine '.'
javap -cp "$CP" -c -p net.minecraft.client.sounds.SoundEngine 2>&1 | grep -B2 -A14 'float calculateVolume' | head -60
sig net.minecraft.client.sounds.SoundManager 'public'
sig net.minecraft.client.gui.screens.options.SoundOptionsScreen '.'
sig net.minecraft.client.gui.screens.options.OptionsSubScreen '.'
sig net.minecraft.client.gui.components.AbstractSliderButton '.'
sig net.minecraft.client.gui.components.Button 'builder|public static'
sig 'net.minecraft.client.gui.components.Button$Builder' '.'
sig net.minecraft.client.gui.screens.Screen 'protected|addRenderableWidget|onClose|init|render\(|public'
sig net.minecraft.client.gui.layouts.HeaderAndFooterLayout '.'
sig net.minecraft.client.gui.components.OptionsList '.'
sig net.fabricmc.fabric.api.client.screen.v1.ScreenEvents '.'
sig net.fabricmc.fabric.api.client.screen.v1.Screens '.'
sig net.minecraft.client.resources.sounds.SoundInstance 'getIdentifier|getLocation|getVolume|getSource'

echo "===== sound"
sig net.minecraft.client.resources.sounds.AbstractSoundInstance '.'
sig net.minecraft.client.resources.sounds.SoundInstance '.'
sig net.minecraft.client.resources.sounds.Sound 'public'

echo "===== scope"
classes 'RenderPipelines|gui/Gui\.class|hud/VanillaHudElements|hud/HudElementRegistry'
sig net.minecraft.client.renderer.RenderPipelines 'GUI'
for j in $MC_JARS; do unzip -Z1 "$j" | grep -E 'RenderPipelines' | sed "s|^|$j: |"; done | head
javap -cp "$CP" -c -p net.minecraft.client.gui.Gui 2>&1 | grep -A40 -i 'Spyglass' | grep -E 'invoke|getstatic|Method|Field' | head -40
sig net.minecraft.client.gui.Gui 'Spyglass|scope|Vignette'
sig net.minecraft.world.entity.player.Player 'isScoping'

echo "===== pose"
classes 'model/.*HumanoidModel|model/.*PlayerModel|model/.*AnimationUtils|entity/player/.*Renderer|entity/.*AvatarRenderer|state/AvatarRenderState|state/PlayerRenderState|state/HumanoidRenderState|state/ArmedEntityRenderState|state/LivingEntityRenderState|layers/ItemInHandLayer|layers/PlayerItemInHandLayer|rendering/v1/RenderStateDataKey|rendering/v1/FabricRenderState|rendering/v1/.*RenderState'
for c in net.minecraft.client.model.HumanoidModel net.minecraft.client.model.HumanoidModel\$ArmPose net.minecraft.client.model.player.PlayerModel net.minecraft.client.model.PlayerModel net.minecraft.client.model.AnimationUtils net.minecraft.client.model.geom.ModelPart; do sig "$c" '.'; done
for c in net.minecraft.client.renderer.entity.player.AvatarRenderer net.minecraft.client.renderer.entity.player.PlayerRenderer net.minecraft.client.renderer.entity.HumanoidMobRenderer net.minecraft.client.renderer.entity.state.AvatarRenderState net.minecraft.client.renderer.entity.state.PlayerRenderState net.minecraft.client.renderer.entity.state.HumanoidRenderState net.minecraft.client.renderer.entity.state.ArmedEntityRenderState net.minecraft.client.renderer.entity.layers.ItemInHandLayer net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer; do sig "$c" '.'; done
sig net.minecraft.client.renderer.entity.state.LivingEntityRenderState 'public|protected'
sig net.minecraft.client.renderer.entity.state.EntityRenderState 'public|protected'
sig net.minecraft.world.entity.LivingEntity 'isUsingItem|getUseItem|getTicksUsingItem|getUsedItemHand|getMainArm|swinging|attackAnim'
for j in $FAPI_JARS; do unzip -Z1 "$j" | grep -E 'rendering/v1/.*(RenderState|ArmPose|Avatar|Humanoid)' | sed "s|^|$j: |"; done | head -40

echo "===== attach"
sig net.minecraft.world.item.Item 'overrideOtherStackedOnMe|overrideStackedOnOther|appendHoverText|getName'
sig net.minecraft.world.item.ItemStack 'CODEC|STREAM_CODEC|listMatches|hashStackList|hashItemAndComponents|isSameItemSameComponents|matches\(|copyWithCount|EMPTY'
sig net.minecraft.world.inventory.ClickAction '.'
sig net.minecraft.world.entity.SlotAccess '.'
sig net.minecraft.world.inventory.Slot 'getItem|set\(|mayPlace|allowModification|safeInsert|safeTake'
sig net.minecraft.world.item.component.CustomModelData '.'
sig net.minecraft.world.item.component.ItemContainerContents 'equals|hashCode|CODEC|STREAM_CODEC'
sig net.minecraft.network.codec.ByteBufCodecs 'map\(|STRING_UTF8|idMapper|list'
sig net.minecraft.util.StringRepresentable 'fromEnum|EnumCodec|getSerializedName'
sig net.minecraft.sounds.SoundEvents 'BUNDLE_INSERT|BUNDLE_REMOVE|ARMOR_EQUIP_GENERIC|CROSSBOW_LOADING_END|ITEM_PICKUP'
sig net.minecraft.world.item.BundleItem 'override|playInsert|playRemove'
classes 'renderer/item/CompositeModel|renderer/item/SelectItemModel|properties/select/CustomModelDataProperty|renderer/item/ConditionalItemModel|properties/conditional/HasComponent|properties/select/ComponentContents'
sig net.minecraft.client.renderer.item.properties.select.CustomModelDataProperty '.'
sig net.minecraft.client.renderer.item.CompositeModel '.'

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
