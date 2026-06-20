package dev.leo.sableplayerragdoll.api;

import dev.leo.sableplayerragdoll.RagdollPoseRequestCallbacks;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

public final class RagdollAsyncPoseRequests {
   private static final int TIMEOUT_TICKS = 10;
   private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
   private static final Map<Long, PendingLaunch> PENDING = new ConcurrentHashMap<>();
   private static final Set<UUID> PENDING_PLAYERS = ConcurrentHashMap.newKeySet();

   private RagdollAsyncPoseRequests() {
   }

   static void requestThenLaunch(ServerPlayer player, Vector3d linear, Vector3d angular, RagdollLaunchOptions options) {
      if (!PENDING_PLAYERS.add(player.getUUID())) {
         return;
      }
      long requestId = NEXT_REQUEST_ID.incrementAndGet();
      PENDING.put(requestId, new PendingLaunch(player.getUUID(), linear, angular, options, TIMEOUT_TICKS));
      RagdollPoseRequestCallbacks.notifyRequestPose(player, requestId);
   }

   public static void resolve(ServerPlayer player, long requestId, RagdollPoseSnapshot pose) {
      PendingLaunch pending = PENDING.get(requestId);
      if (pending == null || !pending.playerId.equals(player.getUUID())) return;
      PENDING.remove(requestId);
      PENDING_PLAYERS.remove(pending.playerId);
      RagdollAPI.finishLaunch(player, pending.options, pending.linear, pending.angular, pose);
   }

   public static void tick(java.util.function.Function<UUID, ServerPlayer> playerLookup) {
      if (PENDING.isEmpty()) return;
      var iterator = PENDING.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<Long, PendingLaunch> entry = iterator.next();
         PendingLaunch pending = entry.getValue();
         if (--pending.ticksRemaining > 0) continue;
         iterator.remove();
         PENDING_PLAYERS.remove(pending.playerId);
         ServerPlayer player = playerLookup.apply(pending.playerId);
         if (player != null) {
            RagdollAPI.finishLaunch(player, pending.options, pending.linear, pending.angular,
               new RagdollPoseSnapshot(RagdollLimbOptions.defaults(), player.yBodyRot));
         }
      }
   }

   private static final class PendingLaunch {
      final UUID playerId;
      final Vector3d linear;
      final Vector3d angular;
      final RagdollLaunchOptions options;
      int ticksRemaining;

      PendingLaunch(UUID playerId, Vector3d linear, Vector3d angular, RagdollLaunchOptions options, int ticksRemaining) {
         this.playerId = playerId;
         this.linear = linear;
         this.angular = angular;
         this.options = options;
         this.ticksRemaining = ticksRemaining;
      }
   }
}
