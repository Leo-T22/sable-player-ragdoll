package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.block.RagdollSeatBlock;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class RagdollSeatingHelper {
   private static final ThreadLocal<ServerPlayer> UNSEATING = new ThreadLocal<>();

   private RagdollSeatingHelper() {
   }

   public static void trySeatEntity(ServerLevel level, LivingEntity entity, ServerSubLevel ragdollSubLevel) {
      if (!isInvalidPassenger(entity) && ragdollSubLevel != null && !ragdollSubLevel.isRemoved()) {
         BlockPos plotSeatPos = ragdollSubLevel.getPlot().getCenterBlock();
         RagdollSeatBlock.sitDown(level, plotSeatPos, entity);
         if (!(entity.getVehicle() instanceof RagdollSeatEntity seat)) {
            SablePlayerRagdoll.LOGGER.warn(
               "[sable_player_ragdoll] sitDown did not mount {} on ragdoll {} at {}",
               targetName(entity), RagdollRegistry.shortId(ragdollSubLevel.getUniqueId()), plotSeatPos.toShortString()
            );
         } else {
            entity.getVehicle().getPersistentData().putUUID(RagdollBlockLifetime.SOURCE_SESSION,
                  RagdollBlockOwnership.sessionId(ragdollSubLevel));
            if (entity instanceof ServerPlayer player) {
               seat.hideRider(player);
            }
            if (RagdollSettings.debugLogging()) {
               SablePlayerRagdoll.LOGGER.info(
                  "[sable_player_ragdoll] seated {} on ragdoll {} at plot {}",
                  targetName(entity), RagdollRegistry.shortId(ragdollSubLevel.getUniqueId()), plotSeatPos.toShortString()
               );
            }
         }
      }
   }

   public static void restoreVisibility(LivingEntity entity) {
      if (entity instanceof ServerPlayer player && player.getVehicle() instanceof RagdollSeatEntity seat) {
         seat.restoreRiderVisibility(player);
      }
   }

   public static void unseatOnLogout(ServerPlayer player) {
      if (player.getVehicle() instanceof RagdollSeatEntity) {
         UNSEATING.set(player);
         try { player.stopRiding(); }
         finally { UNSEATING.remove(); }
      }
      restoreVisibility(player);
   }

   public static boolean isUnseating(ServerPlayer player) {
      return UNSEATING.get() == player;
   }

   private static boolean isInvalidPassenger(LivingEntity entity) {
      return entity.isDeadOrDying() || entity instanceof ServerPlayer player && player.isSpectator();
   }

   private static String targetName(LivingEntity entity) {
      if (entity instanceof ServerPlayer player) {
         return player.getGameProfile().getName();
      }
      return entity.getType().getDescriptionId() + "#" + RagdollRegistry.shortId(entity.getUUID());
   }
}
