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
import net.minecraft.client.model.geom.ModelPart;
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
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class RagdollPartBlockEntityRenderer implements BlockEntityRenderer<RagdollPartBlockEntity>, RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> {
   private final PlayerModel<RagdollDollEntity> defaultModel;
   private final PlayerModel<RagdollDollEntity> slimModel;
   private PlayerModel<RagdollDollEntity> model;
   private final RagdollArmorLayer armorLayer;
   private final ElytraLayer<RagdollDollEntity, PlayerModel<RagdollDollEntity>> elytraLayer;
   private final ItemRenderer itemRenderer;
   private RagdollDollEntity renderEntity;
   private BodyPart currentArmorPart = BodyPart.TORSO;
   private ResourceLocation currentTexture = DefaultPlayerSkin.getDefaultTexture();

   public RagdollPartBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
      this.defaultModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
      this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
      this.model = this.defaultModel;
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
      PlayerSkin skin = this.skin(blockEntity);
      this.model = skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.defaultModel;
      this.currentArmorPart = bodyPart;
      this.currentTexture = skin.texture();
      this.showOnly(bodyPart);
      RagdollDollEntity entity = this.renderEntity(blockEntity);

      poseStack.pushPose();
      this.positionPart(bodyPart, poseStack);
      this.renderLayers(blockEntity, bodyPart, entity, poseStack, buffer, packedLight, partialTick);
      poseStack.popPose();
   }

   private void positionPart(BodyPart bodyPart, PoseStack poseStack) {
      poseStack.translate(0.5F, 0.5F, 0.5F);
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      float partScale = bodyPart.renderScale();
      poseStack.scale(-partScale, -partScale, partScale);
      this.centerVisiblePart(bodyPart, poseStack);
   }

   private void renderLayers(RagdollPartBlockEntity blockEntity, BodyPart bodyPart, RagdollDollEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
      VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(this.currentTexture));
      this.model.renderToBuffer(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
      this.armorLayer.render(poseStack, buffer, packedLight, entity, 0.0F, 0.0F, partialTick, 0.0F, 0.0F, 0.0F);
      this.renderElytra(bodyPart, entity, poseStack, buffer, packedLight, partialTick);
      this.renderHeldItem(blockEntity, entity, poseStack, buffer, packedLight);
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

   private PlayerSkin skin(RagdollPartBlockEntity blockEntity) {
      if (Minecraft.getInstance().getSkinManager() == null) {
         return DefaultPlayerSkin.get(blockEntity.skinProfile());
      }
      return Minecraft.getInstance().getSkinManager().getInsecureSkin(blockEntity.skinProfile());
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
