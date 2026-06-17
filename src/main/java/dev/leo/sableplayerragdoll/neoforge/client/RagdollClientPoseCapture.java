package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class RagdollClientPoseCapture {

   private RagdollClientPoseCapture() {
   }

   public static RagdollLimbOptions capture() {
      Minecraft minecraft = Minecraft.getInstance();
      if (!(minecraft.player instanceof AbstractClientPlayer player)) {
         return RagdollLimbOptions.defaults();
      }

      EntityRenderer<? extends Player> renderer = minecraft.getEntityRenderDispatcher().getSkinMap().get(player.getSkin().model());
      if (!(renderer instanceof PlayerRenderer playerRenderer)) {
         return RagdollLimbOptions.defaults();
      }

      float partialTick = 1.0F;
      float limbSwing = player.walkAnimation.position(partialTick);
      float limbSwingAmount = Math.min(player.walkAnimation.speed(partialTick), 1.0F);

      PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();
      model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
      model.setupAnim(player, limbSwing, limbSwingAmount, (float) player.tickCount, player.getYHeadRot() - player.yBodyRot, player.getXRot());

      return RagdollLimbOptions.builder()
         .limb(BodyPart.HEAD, pose(model.head))
         .limb(BodyPart.TORSO, pose(model.body))
         .limb(BodyPart.LEFT_ARM, pose(model.leftArm))
         .limb(BodyPart.RIGHT_ARM, pose(model.rightArm))
         .limb(BodyPart.LEFT_LEG, pose(model.leftLeg))
         .limb(BodyPart.RIGHT_LEG, pose(model.rightLeg))
         .build();
   }

   private static RagdollLimbConfig pose(ModelPart part) {
      return RagdollLimbConfig.builder()
         .initialRotation(degrees(part.xRot), -degrees(part.yRot), degrees(part.zRot))
         .build();
   }

   private static double degrees(float radians) {
      return radians * Mth.RAD_TO_DEG;
   }
}
