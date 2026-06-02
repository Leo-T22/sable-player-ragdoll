package dev.leo.sableplayerragdoll;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

public final class RagdollSeatCallbacks {
   private static Consumer<ServerPlayer> onAutoSeated = player -> {};
   private static Consumer<ServerPlayer> onReleased = player -> {};

   private RagdollSeatCallbacks() {
   }

   public static void setOnAutoSeated(Consumer<ServerPlayer> handler) {
      onAutoSeated = handler != null ? handler : player -> {};
   }

   public static void setOnReleased(Consumer<ServerPlayer> handler) {
      onReleased = handler != null ? handler : player -> {};
   }

   public static void notifyAutoSeated(ServerPlayer player) {
      onAutoSeated.accept(player);
   }

   public static void notifyReleased(ServerPlayer player) {
      onReleased.accept(player);
   }
}
