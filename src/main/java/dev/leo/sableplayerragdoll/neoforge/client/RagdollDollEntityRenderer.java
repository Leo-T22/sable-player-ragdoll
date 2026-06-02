package dev.leo.sableplayerragdoll.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class RagdollDollEntityRenderer extends LivingEntityRenderer<RagdollDollEntity, PlayerModel<RagdollDollEntity>> {
   public RagdollDollEntityRenderer(Context context) {
      super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
   }

   @Override
   public void render(RagdollDollEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      this.showOnly(entity.getBodyPart());
      this.model.crouching = false;
      super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
   }

   private void showOnly(RagdollDollEntity.BodyPart bodyPart) {
      this.model.setAllVisible(false);
      switch (bodyPart) {
         case HEAD -> { this.model.head.visible = true; this.model.hat.visible = true; }
         case LEFT_ARM -> { this.model.leftArm.visible = true; this.model.leftSleeve.visible = true; }
         case RIGHT_ARM -> { this.model.rightArm.visible = true; this.model.rightSleeve.visible = true; }
         case LEFT_LEG -> { this.model.leftLeg.visible = true; this.model.leftPants.visible = true; }
         case RIGHT_LEG -> { this.model.rightLeg.visible = true; this.model.rightPants.visible = true; }
         default -> { this.model.body.visible = true; this.model.jacket.visible = true; }
      }
   }

   @Override
   public ResourceLocation getTextureLocation(RagdollDollEntity entity) {
      if (Minecraft.getInstance().getSkinManager() == null) {
         return DefaultPlayerSkin.get(entity.getSkinProfile()).texture();
      }
      return Minecraft.getInstance().getSkinManager().getInsecureSkin(entity.getSkinProfile()).texture();
   }

   @Override
   protected void scale(RagdollDollEntity entity, PoseStack poseStack, float partialTickTime) {
      poseStack.scale(0.9375F, 0.9375F, 0.9375F);
   }
}
