package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.neoforge.config.RagdollClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class RagdollSableCameraBypass {
   private RagdollSableCameraBypass() {
   }
   private static boolean isRagdollCameraPlayer(Entity entity) {
      Minecraft minecraft = Minecraft.getInstance();
      return entity != null && entity == minecraft.player && entity.getVehicle() instanceof RagdollSeatEntity;
   }
   public static boolean suppressSubLevelCameraRotation(Entity entity) {
      if (!isRagdollCameraPlayer(entity)) {
         return false;
      }
      if (RagdollClientConfig.useFirstPersonCamera()) {
         return !RagdollCameraHelper.isFirstPersonAligned();
      }
      return true;
   }
   public static boolean detachSubLevelCameraPosition(Entity entity) {
      return isRagdollCameraPlayer(entity) && !RagdollClientConfig.useFirstPersonCamera();
   }
}
