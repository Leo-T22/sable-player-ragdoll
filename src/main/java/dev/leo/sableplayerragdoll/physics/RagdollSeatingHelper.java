package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.block.RagdollSeatBlock;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RagdollSeatingHelper {
   private static final Map<UUID, Boolean> PLAYER_PREVIOUS_INVISIBILITY = new ConcurrentHashMap<>();

   private RagdollSeatingHelper() {
   }

   public static void trySeatEntity(ServerLevel level, LivingEntity entity, ServerSubLevel ragdollSubLevel) {
      if (!isInvalidPassenger(entity) && ragdollSubLevel != null && !ragdollSubLevel.isRemoved()) {
         BlockPos plotSeatPos = ragdollSubLevel.getPlot().getCenterBlock();
         RagdollSeatBlock.sitDown(level, plotSeatPos, entity);
         if (!entity.isPassenger()) {
            SablePlayerRagdoll.LOGGER.warn(
               "[sable_player_ragdoll] sitDown did not mount {} on ragdoll {} at {}",
               targetName(entity), RagdollRegistry.shortId(ragdollSubLevel.getUniqueId()), plotSeatPos.toShortString()
            );
         } else {
            if (entity instanceof ServerPlayer player) {
               PLAYER_PREVIOUS_INVISIBILITY.putIfAbsent(player.getUUID(), player.isInvisible());
               player.setInvisible(true);
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
      if (entity instanceof ServerPlayer player) {
         Boolean wasInvisible = PLAYER_PREVIOUS_INVISIBILITY.remove(player.getUUID());
         if (wasInvisible != null) {
            player.setInvisible(wasInvisible);
         }
      }
   }

   // Safety net for ragdolls that end without going through RagdollExpireHelper's unseat paths.
   public static void sweepLeakedInvisibility(MinecraftServer server) {
      if (PLAYER_PREVIOUS_INVISIBILITY.isEmpty()) return;
      for (UUID playerId : new ArrayList<>(PLAYER_PREVIOUS_INVISIBILITY.keySet())) {
         ServerPlayer player = server.getPlayerList().getPlayer(playerId);
         if (player == null) {
            // Invisible is not serialized into player NBT, so nothing survives the disconnect.
            PLAYER_PREVIOUS_INVISIBILITY.remove(playerId);
            continue;
         }
         if (RagdollSessionManager.isPlayerCurrentlyRagdolled(player)) continue;
         restoreVisibility(player);
         SablePlayerRagdoll.LOGGER.warn(
            "[sable_player_ragdoll] restored leaked ragdoll invisibility for {} (ragdoll ended without unseating)",
            targetName(player)
         );
      }
   }

   public static void resetState() {
      PLAYER_PREVIOUS_INVISIBILITY.clear();
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
