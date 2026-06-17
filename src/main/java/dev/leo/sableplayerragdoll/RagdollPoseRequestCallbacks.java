package dev.leo.sableplayerragdoll;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;

public final class RagdollPoseRequestCallbacks {
   private static BiConsumer<ServerPlayer, Long> onRequestPose = (player, requestId) -> {};

   private RagdollPoseRequestCallbacks() {
   }

   public static void setOnRequestPose(BiConsumer<ServerPlayer, Long> handler) {
      onRequestPose = handler != null ? handler : (player, requestId) -> {};
   }

   public static void notifyRequestPose(ServerPlayer player, long requestId) {
      onRequestPose.accept(player, requestId);
   }
}
