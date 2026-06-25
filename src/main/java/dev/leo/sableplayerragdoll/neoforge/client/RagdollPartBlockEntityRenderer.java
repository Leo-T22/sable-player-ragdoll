package dev.leo.sableplayerragdoll.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import net.minecraft.client.CameraType;
import dev.leo.sableplayerragdoll.neoforge.mixin.LivingEntityRendererAccessor;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.neoforged.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class RagdollPartBlockEntityRenderer implements BlockEntityRenderer<RagdollPartBlockEntity>, RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> {
   private static PlayerModel<RagdollDollEntity> activeModel = null;
   private static BodyPart activeBodyPart = null;

   public static PlayerModel<RagdollDollEntity> currentModel() {
      return activeModel;
   }

   public static BodyPart activeBodyPart() {
      return activeBodyPart;
   }

   private final PlayerModel<RagdollDollEntity> defaultModel;
   private final PlayerModel<RagdollDollEntity> slimModel;
   private PlayerModel<RagdollDollEntity> model;
   private final ModelPart defaultCloak;
   private final ModelPart slimCloak;
   private final ElytraLayer<RagdollDollEntity, PlayerModel<RagdollDollEntity>> elytraLayer;
   private RagdollDollEntity renderEntity;
   private ResourceLocation currentTexture = DefaultPlayerSkin.getDefaultTexture();
   private ResourceLocation currentCapeTexture = null;

   public RagdollPartBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
      ModelPart defaultRoot = context.bakeLayer(ModelLayers.PLAYER);
      ModelPart slimRoot = context.bakeLayer(ModelLayers.PLAYER_SLIM);
      this.defaultModel = new PlayerModel<>(defaultRoot, false);
      this.slimModel = new PlayerModel<>(slimRoot, true);
      this.defaultCloak = defaultRoot.getChild("cloak");
      this.slimCloak = slimRoot.getChild("cloak");
      this.model = this.defaultModel;
      this.elytraLayer = new RagdollElytraLayer(this, context.getModelSet());
   }

   @Override
   public void render(RagdollPartBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
      BodyPart bodyPart = blockEntity.bodyPart();
      if (bodyPart == BodyPart.HEAD
            && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON
            && Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.getVehicle() instanceof RagdollSeatEntity
            && Minecraft.getInstance().player.getUUID().equals(blockEntity.skinProfile().getId())) {
         return;
      }
      PlayerSkin skin = this.skin(blockEntity);
      this.model = skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.defaultModel;
      this.currentTexture = skin.texture();
      this.currentCapeTexture = skin.capeTexture();
      this.showOnly(bodyPart);
      this.renderEntity(blockEntity); // keep renderEntity up-to-date for elytra/cape
      LivingEntity entity = this.getRenderEntity(blockEntity);

      activeModel = this.model;
      activeBodyPart = bodyPart;
      try {
         poseStack.pushPose();
         this.positionPart(bodyPart, poseStack);
         this.renderLayers(blockEntity, bodyPart, entity, poseStack, buffer, packedLight, partialTick);
         poseStack.popPose();
      } finally {
         activeModel = null;
         activeBodyPart = null;
      }
   }

   private void positionPart(BodyPart bodyPart, PoseStack poseStack) {
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      float partScale = bodyPart.renderScale();
      poseStack.scale(-partScale, -partScale, partScale);
      this.centerVisiblePart(bodyPart, poseStack);
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private void renderLayers(RagdollPartBlockEntity blockEntity, BodyPart bodyPart, LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
      // Render the base model — use hurt overlay when the real player is in their hurt window
      int baseOverlay = !blockEntity.isCorpse() && entity.hurtTime > 0
          ? OverlayTexture.pack(0, 3)  // u=0 (full flash), v=3 (red hurt row)
          : OverlayTexture.NO_OVERLAY;
      VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(this.currentTexture));
      this.model.renderToBuffer(poseStack, vertices, packedLight, baseOverlay);

      // Render all vanilla layers from the standard PlayerRenderer
      Minecraft minecraft = Minecraft.getInstance();
      boolean slim = this.model == this.slimModel;
      var playerRenderer = minecraft.getEntityRenderDispatcher().getSkinMap().get(slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE);

      boolean accessoriesLoaded = ModList.get().isLoaded("accessories");

      if (playerRenderer instanceof LivingEntityRendererAccessor accessor) {
         boolean wasInvisible = entity.isInvisible();
         entity.setInvisible(false);

         // Only populate slots belonging to this body part leave the rest empty.
         ItemStack[] oldItems = new ItemStack[EquipmentSlot.values().length];
         for (EquipmentSlot slot : EquipmentSlot.values()) {
            oldItems[slot.ordinal()] = entity.getItemBySlot(slot);
            ItemStack candidate = blockEntity.itemBySlot(slot);
            if (accessoriesLoaded && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && blockEntity.hasAccessoriesItems()) {
               ItemStack cosmetic = AccessoriesRenderHelper.storedCosmeticArmorOverride(blockEntity, slot);
               if (cosmetic != null) candidate = cosmetic;
            }
            ItemStack toShow = slotsForPart(bodyPart).contains(slot) && !isArmorSlotBlockedForPart(bodyPart, slot, candidate)
               ? candidate : ItemStack.EMPTY;
            entity.setItemSlot(slot, toShow);
         }

         // Displace all model parts that don't belong to this block
         ModelPart currentPart = visiblePart(bodyPart);
         ModelPart[] allMainParts = {
            this.model.head, this.model.body,
            this.model.leftArm, this.model.rightArm,
            this.model.leftLeg, this.model.rightLeg
         };
         float[] savedPartY = new float[allMainParts.length];
         for (int j = 0; j < allMainParts.length; j++) {
            savedPartY[j] = allMainParts[j].y;
            if (allMainParts[j] != currentPart) allMainParts[j].y += 10000f;
         }

         try {
            for (var layer : accessor.getLayers()) {
               String layerClass = layer.getClass().getName();
               // Curios/Accessories handled separately; ElytraLayer handled explicitly below.
               if (layerClass.equals("top.theillusivec4.curios.client.render.CuriosLayer")
                     || layerClass.equals("io.wispforest.accessories.client.AccessoriesRenderLayer")
                     || layerClass.equals("net.minecraft.client.renderer.entity.layers.ElytraLayer")) {
                  continue;
               }
               // YDM Weapon Master
               if (bodyPart != BodyPart.TORSO
                     && (layerClass.equals("com.minecraftserverzone.weaponmaster.itemlayers.HumanoidItemLayer")
                        || layerClass.equals("com.minecraftserverzone.weaponmaster.itemlayers.HumanoidItemLayerLac"))) {
                  continue;
               }
               try {
                  ((net.minecraft.client.renderer.entity.layers.RenderLayer) layer).render(
                     poseStack, buffer, packedLight, entity, 0.0F, 0.0F, partialTick, entity.tickCount + partialTick, 0.0F, 0.0F
                  );
               } catch (ClassCastException e) {
               } catch (Exception e) {
                  // Swallow other per-layer errors
               }
            }
         } finally {
            for (int j = 0; j < allMainParts.length; j++) allMainParts[j].y = savedPartY[j];
            entity.setInvisible(wasInvisible);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
               entity.setItemSlot(slot, oldItems[slot.ordinal()]);
            }
         }
      }

      if (bodyPart == BodyPart.TORSO && this.renderEntity != null) {
         this.elytraLayer.render(poseStack, buffer, packedLight, this.renderEntity, 0.0F, 0.0F, partialTick, 0.0F, 0.0F, 0.0F);
         this.renderCape(blockEntity, entity, poseStack, buffer, packedLight);
      }
      
      if (ModList.get().isLoaded("accessories")) {
         if (blockEntity.hasAccessoriesItems()) {
            AccessoriesRenderHelper.renderFromStored(bodyPart, blockEntity, entity, this, poseStack, buffer, packedLight, partialTick);
         }
      }
      if (ModList.get().isLoaded("curios")) {
         if (blockEntity.hasCurioItems()) {
            CuriosRenderHelper.renderFromStored(bodyPart, blockEntity, entity, this, poseStack, buffer, packedLight, partialTick);
         }
      }
   }

   @Override
   public PlayerModel<RagdollDollEntity> getModel() {
      return this.model;
   }

   @Override
   public ResourceLocation getTextureLocation(RagdollDollEntity entity) {
      return this.currentTexture;
   }

   private PlayerSkin skin(RagdollPartBlockEntity blockEntity) {
      if (Minecraft.getInstance().getSkinManager() == null) {
         return DefaultPlayerSkin.get(blockEntity.skinProfile());
      }
      return Minecraft.getInstance().getSkinManager().getInsecureSkin(blockEntity.skinProfile());
   }

   private LivingEntity getRenderEntity(RagdollPartBlockEntity blockEntity) {
      Minecraft minecraft = Minecraft.getInstance();
      if (!blockEntity.isCorpse() && minecraft.level != null) {
         UUID uuid = blockEntity.skinProfile().getId();
         if (uuid != null) {
            Player player = minecraft.level.getPlayerByUUID(uuid);
            if (player != null) {
               return player;
            }
         }
      }
      return this.renderEntity(blockEntity);
   }

   private RagdollDollEntity renderEntity(RagdollPartBlockEntity blockEntity) {
      Minecraft minecraft = Minecraft.getInstance();
      if (this.renderEntity == null && minecraft.level != null) {
         this.renderEntity = new RagdollDollEntity(minecraft.level);
      }
      if (this.renderEntity != null) {
         this.renderEntity.setSkinProfile(blockEntity.skinProfile());
         this.renderEntity.setItemSlot(EquipmentSlot.MAINHAND, blockEntity.itemBySlot(EquipmentSlot.MAINHAND));
         this.renderEntity.setItemSlot(EquipmentSlot.OFFHAND, blockEntity.itemBySlot(EquipmentSlot.OFFHAND));
         this.renderEntity.setItemSlot(EquipmentSlot.HEAD, blockEntity.itemBySlot(EquipmentSlot.HEAD));
         this.renderEntity.setItemSlot(EquipmentSlot.CHEST, blockEntity.itemBySlot(EquipmentSlot.CHEST));
         this.renderEntity.setItemSlot(EquipmentSlot.LEGS, blockEntity.itemBySlot(EquipmentSlot.LEGS));
         this.renderEntity.setItemSlot(EquipmentSlot.FEET, blockEntity.itemBySlot(EquipmentSlot.FEET));
      }
      return this.renderEntity;
   }

   private void showOnly(BodyPart bodyPart) {
      this.model.setAllVisible(false);
      this.model.young = false;
      this.model.crouching = false;

      switch (bodyPart) {
         case HEAD -> { this.model.head.visible = true; this.model.hat.visible = true; }
         case LEFT_ARM -> { this.model.leftArm.visible = true; this.model.leftSleeve.visible = true; }
         case RIGHT_ARM -> { this.model.rightArm.visible = true; this.model.rightSleeve.visible = true; }
         case LEFT_LEG -> { this.model.leftLeg.visible = true; this.model.leftPants.visible = true; }
         case RIGHT_LEG -> { this.model.rightLeg.visible = true; this.model.rightPants.visible = true; }
         default -> { this.model.body.visible = true; this.model.jacket.visible = true; }
      }
   }

   private void centerVisiblePart(BodyPart bodyPart, PoseStack poseStack) {
      ModelPart part = this.visiblePart(bodyPart);
      poseStack.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
            // Per-part corrections, in pixels (1/16 block) like the pivot above.
      switch (bodyPart) {
         case HEAD -> nudge(poseStack, 0.0F, 4.0F, 0.0F);
         case TORSO -> nudge(poseStack, 0.0F, -6.5F, 0.0F);
         case LEFT_ARM -> nudge(poseStack, -2.0F, -4.0F, 0.0F);
         case RIGHT_ARM -> nudge(poseStack, 0.0F, -4.0F, 0.0F);
         case LEFT_LEG -> nudge(poseStack, -1.0F, -6.0F, 0.0F);
         case RIGHT_LEG -> nudge(poseStack, -1.0F, -6.0F, 0.0F);
      }
   }

   // Per-part offset in pixels (1/16 of a block).
   private static void nudge(PoseStack poseStack, float xPixels, float yPixels, float zPixels) {
      poseStack.translate(xPixels / 16.0F, yPixels / 16.0F, zPixels / 16.0F);
   }

   private ModelPart visiblePart(BodyPart bodyPart) {
      return switch (bodyPart) {
         case HEAD -> this.model.head;
         case LEFT_ARM -> this.model.leftArm;
         case RIGHT_ARM -> this.model.rightArm;
         case LEFT_LEG -> this.model.leftLeg;
         case RIGHT_LEG -> this.model.rightLeg;
         case TORSO -> this.model.body;
      };
   }

   private static Set<EquipmentSlot> slotsForPart(BodyPart bodyPart) {
      return switch (bodyPart) {
         case HEAD      -> EnumSet.of(EquipmentSlot.HEAD);
         case TORSO     -> EnumSet.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS);
         case RIGHT_ARM -> EnumSet.of(EquipmentSlot.MAINHAND, EquipmentSlot.CHEST);
         case LEFT_ARM  -> EnumSet.of(EquipmentSlot.OFFHAND,  EquipmentSlot.CHEST);
         case LEFT_LEG  -> EnumSet.of(EquipmentSlot.LEGS, EquipmentSlot.FEET);
         case RIGHT_LEG -> EnumSet.of(EquipmentSlot.LEGS, EquipmentSlot.FEET);
      };
   }

   private static boolean isArmorSlotBlockedForPart(BodyPart bodyPart, EquipmentSlot slot, ItemStack item) {
      if (slot != EquipmentSlot.CHEST) return false;
      if (bodyPart != BodyPart.LEFT_ARM && bodyPart != BodyPart.RIGHT_ARM) return false;
      return !(item.getItem() instanceof net.minecraft.world.item.ArmorItem);
   }

   private void renderCape(RagdollPartBlockEntity blockEntity, LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      if (this.currentCapeTexture == null) return;
      if (blockEntity.itemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) return;
      if (entity instanceof net.minecraft.client.player.AbstractClientPlayer acp && !acp.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.CAPE)) return;
      ModelPart cloak = this.model == this.slimModel ? this.slimCloak : this.defaultCloak;
      cloak.visible = true;
      poseStack.pushPose();
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      poseStack.translate(0.0F, 0.0F, -0.125F);
      poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
      VertexConsumer vertices = buffer.getBuffer(RenderType.entitySolid(this.currentCapeTexture));
      cloak.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
      poseStack.popPose();
   }

   private final class RagdollElytraLayer extends ElytraLayer<RagdollDollEntity, PlayerModel<RagdollDollEntity>> {
      RagdollElytraLayer(RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> renderer, net.minecraft.client.model.geom.EntityModelSet modelSet) {
         super(renderer, modelSet);
      }

      @Override
      public ResourceLocation getElytraTexture(ItemStack stack, RagdollDollEntity entity) {
         return currentCapeTexture != null ? currentCapeTexture : super.getElytraTexture(stack, entity);
      }
   }
}
