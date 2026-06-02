package dev.leo.sableplayerragdoll.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class RagdollPartBlockEntityRenderer implements BlockEntityRenderer<RagdollPartBlockEntity>, RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> {
   private final PlayerModel<RagdollDollEntity> model;
   private final RagdollArmorLayer armorLayer;
   private final ElytraLayer<RagdollDollEntity, PlayerModel<RagdollDollEntity>> elytraLayer;
   private final ItemRenderer itemRenderer;
   private RagdollDollEntity renderEntity;
   private BodyPart currentArmorPart = BodyPart.TORSO;
   private ResourceLocation currentTexture = DefaultPlayerSkin.getDefaultTexture();

   public RagdollPartBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
      this.model = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
      this.armorLayer = new RagdollArmorLayer(
         this,
         new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
         new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
         context.getBlockRenderDispatcher().getBlockModelShaper().getModelManager()
      );
      this.elytraLayer = new ElytraLayer<>(this, context.getModelSet());
      this.itemRenderer = context.getItemRenderer();
   }

   @Override
   public void render(RagdollPartBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
      BodyPart bodyPart = blockEntity.bodyPart();
      this.showOnly(bodyPart);
      poseStack.pushPose();
      poseStack.translate(0.5F, bodyPart.renderYOffset(), 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      if (isArmPart(bodyPart)) poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
      float partScale = bodyPart.renderScale();
      poseStack.scale(-partScale, -partScale, partScale);
      this.centerVisiblePart(bodyPart);
      RagdollDollEntity entity = this.renderEntity(blockEntity);
      this.currentArmorPart = bodyPart;
      this.currentTexture = this.texture(blockEntity);
      VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(this.currentTexture));
      this.model.renderToBuffer(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
      this.armorLayer.render(poseStack, buffer, packedLight, entity, 0.0F, 0.0F, partialTick, 0.0F, 0.0F, 0.0F);
      this.renderElytra(bodyPart, entity, poseStack, buffer, packedLight, partialTick);
      this.renderHeldItem(blockEntity, entity, poseStack, buffer, packedLight);
      poseStack.popPose();
   }

   private static boolean isArmPart(BodyPart bodyPart) {
      return bodyPart == BodyPart.LEFT_ARM || bodyPart == BodyPart.RIGHT_ARM;
   }

   private void renderElytra(BodyPart bodyPart, RagdollDollEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
      if (entity != null && bodyPart == BodyPart.TORSO) {
         this.elytraLayer.render(poseStack, buffer, packedLight, entity, 0.0F, 0.0F, partialTick, 0.0F, 0.0F, 0.0F);
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

   private ResourceLocation texture(RagdollPartBlockEntity blockEntity) {
      if (Minecraft.getInstance().getSkinManager() == null) {
         return DefaultPlayerSkin.get(blockEntity.skinProfile()).texture();
      }
      return Minecraft.getInstance().getSkinManager().getInsecureSkin(blockEntity.skinProfile()).texture();
   }

   private RagdollDollEntity renderEntity(RagdollPartBlockEntity blockEntity) {
      Minecraft minecraft = Minecraft.getInstance();
      if (this.renderEntity == null && minecraft.level != null) {
         this.renderEntity = new RagdollDollEntity(minecraft.level);
      }
      if (this.renderEntity != null) {
         this.renderEntity.setItemSlot(EquipmentSlot.MAINHAND, blockEntity.itemBySlot(EquipmentSlot.MAINHAND));
         this.renderEntity.setItemSlot(EquipmentSlot.OFFHAND, blockEntity.itemBySlot(EquipmentSlot.OFFHAND));
         this.renderEntity.setItemSlot(EquipmentSlot.HEAD, blockEntity.itemBySlot(EquipmentSlot.HEAD));
         this.renderEntity.setItemSlot(EquipmentSlot.CHEST, blockEntity.itemBySlot(EquipmentSlot.CHEST));
         this.renderEntity.setItemSlot(EquipmentSlot.LEGS, blockEntity.itemBySlot(EquipmentSlot.LEGS));
         this.renderEntity.setItemSlot(EquipmentSlot.FEET, blockEntity.itemBySlot(EquipmentSlot.FEET));
      }
      return this.renderEntity;
   }

   private void renderHeldItem(RagdollPartBlockEntity blockEntity, RagdollDollEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      if (entity == null) return;
      BodyPart bodyPart = blockEntity.bodyPart();
      boolean left = bodyPart == BodyPart.LEFT_ARM;
      boolean right = bodyPart == BodyPart.RIGHT_ARM;
      if (!left && !right) return;
      ItemStack stack = left ? blockEntity.itemBySlot(EquipmentSlot.OFFHAND) : blockEntity.itemBySlot(EquipmentSlot.MAINHAND);
      if (stack.isEmpty()) return;
      HumanoidArm arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
      ItemDisplayContext ctx = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      poseStack.pushPose();
      this.model.translateToHand(arm, poseStack);
      poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
      this.itemRenderer.renderStatic(entity, stack, ctx, left, poseStack, buffer, entity.level(), packedLight, OverlayTexture.NO_OVERLAY, 0);
      poseStack.popPose();
   }

   private void showOnly(BodyPart bodyPart) {
      this.model.setAllVisible(false);
      this.resetPartPositions();
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

   private void centerVisiblePart(BodyPart bodyPart) {
      switch (bodyPart) {
         case HEAD -> { this.model.head.y = -4.0F; this.model.hat.y = -4.0F; }
         case LEFT_ARM -> { this.model.leftArm.x = -2.0F; this.model.leftArm.y = -4.0F; this.model.leftSleeve.x = -2.0F; this.model.leftSleeve.y = -4.0F; }
         case RIGHT_ARM -> { this.model.rightArm.x = 0.0F; this.model.rightArm.y = -4.0F; this.model.rightSleeve.x = 0.0F; this.model.rightSleeve.y = -4.0F; }
         case LEFT_LEG -> { this.model.leftLeg.x = 0.0F; this.model.leftLeg.y = -4.0F; this.model.leftPants.x = 0.0F; this.model.leftPants.y = -4.0F; }
         case RIGHT_LEG -> { this.model.rightLeg.x = 0.0F; this.model.rightLeg.y = -4.0F; this.model.rightPants.x = 0.0F; this.model.rightPants.y = -4.0F; }
         default -> { this.model.body.y = -4.0F; this.model.jacket.y = -4.0F; }
      }
   }

   private void resetPartPositions() {
      this.model.head.x = 0.0F; this.model.head.y = 0.0F;
      this.model.hat.x = 0.0F; this.model.hat.y = 0.0F;
      this.model.body.x = 0.0F; this.model.body.y = 0.0F;
      this.model.jacket.x = 0.0F; this.model.jacket.y = 0.0F;
      this.model.leftArm.x = 5.0F; this.model.leftArm.y = 2.0F;
      this.model.leftSleeve.x = 5.0F; this.model.leftSleeve.y = 2.0F;
      this.model.rightArm.x = -5.0F; this.model.rightArm.y = 2.0F;
      this.model.rightSleeve.x = -5.0F; this.model.rightSleeve.y = 2.0F;
      this.model.leftLeg.x = 1.9F; this.model.leftLeg.y = 12.0F;
      this.model.leftPants.x = 1.9F; this.model.leftPants.y = 12.0F;
      this.model.rightLeg.x = -1.9F; this.model.rightLeg.y = 12.0F;
      this.model.rightPants.x = -1.9F; this.model.rightPants.y = 12.0F;
   }

   private final class RagdollArmorLayer extends HumanoidArmorLayer<RagdollDollEntity, PlayerModel<RagdollDollEntity>, HumanoidModel<RagdollDollEntity>> {
      RagdollArmorLayer(RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> renderer, HumanoidModel<RagdollDollEntity> innerModel, HumanoidModel<RagdollDollEntity> outerModel, net.minecraft.client.resources.model.ModelManager modelManager) {
         super(renderer, innerModel, outerModel, modelManager);
      }

      @Override
      protected void setPartVisibility(HumanoidModel<RagdollDollEntity> model, EquipmentSlot slot) {
         model.setAllVisible(false);
         switch (RagdollPartBlockEntityRenderer.this.currentArmorPart) {
            case HEAD -> { model.head.visible = slot == EquipmentSlot.HEAD; model.hat.visible = slot == EquipmentSlot.HEAD; }
            case TORSO -> model.body.visible = slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS;
            case LEFT_ARM -> model.leftArm.visible = slot == EquipmentSlot.CHEST;
            case RIGHT_ARM -> model.rightArm.visible = slot == EquipmentSlot.CHEST;
            case LEFT_LEG -> model.leftLeg.visible = slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
            case RIGHT_LEG -> model.rightLeg.visible = slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
         }
      }
   }
}
