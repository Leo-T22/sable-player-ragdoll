package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.RagdollSeatCallbacks;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.api.RagdollEndEvent;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class RagdollExpireHelper {
   private RagdollExpireHelper() {
   }

   static void unseatPlayerSilently(ServerLevel level, UUID playerId) {
      Entity entity = level.getEntity(playerId);
      if (!(entity instanceof LivingEntity livingEntity)) return;
      if (livingEntity.isPassenger()) livingEntity.stopRiding();
      RagdollSeatingHelper.restoreVisibility(livingEntity);
   }

   public static void expire(SubLevelPhysicsSystem physicsSystem, ServerLevel level, ServerSubLevel subLevel, String reason) {
      if (!subLevel.isRemoved() && !RagdollSessionManager.isExpiring(subLevel)) {
         RagdollSessionManager.markExpiring(subLevel, reason);
         unseatRider(level, subLevel);
         discardSeatEntities(level, subLevel);
         RagdollSessionManager.unregister(subLevel);
         RagdollRegistry.untrack(subLevel.getUniqueId());
         RagdollDeferredSync.queueRemoval(subLevel.getUniqueId(), level);
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] expiring ragdoll {} ({})", RagdollRegistry.shortId(subLevel.getUniqueId()), reason);
      }
   }

   public static void expireImmediate(SubLevelPhysicsSystem physicsSystem, ServerLevel level, ServerSubLevel subLevel, String reason) {
      expireImmediate(physicsSystem, level, subLevel, reason, false);
   }

   public static void expireImmediate(SubLevelPhysicsSystem physicsSystem, ServerLevel level, ServerSubLevel subLevel, String reason, boolean placePlayerAtRagdoll) {
      if (!subLevel.isRemoved() && !RagdollSessionManager.isExpiring(subLevel)) {
         RagdollSessionManager.markExpiring(subLevel, reason);
         unseatRider(level, subLevel, placePlayerAtRagdoll);
         discardSeatEntities(level, subLevel);
         RagdollSessionManager.unregister(subLevel);
         RagdollRegistry.untrack(subLevel.getUniqueId());
         RagdollDeferredSync.cancel(subLevel.getUniqueId());
         RagdollRemovalHelper.removeRagdollSubLevel(physicsSystem, subLevel);
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] expiring ragdoll {} immediately ({})", RagdollRegistry.shortId(subLevel.getUniqueId()), reason);
      }
   }

   private static void unseatRider(ServerLevel level, ServerSubLevel subLevel) {
      unseatRider(level, subLevel, false);
   }

   private static void unseatRider(ServerLevel level, ServerSubLevel subLevel, boolean placePlayerAtRagdoll) {
      UUID playerId = RagdollSessionManager.getPlayerId(subLevel);
      if (playerId != null) {
         Entity entity = level.getEntity(playerId);
         if (entity instanceof LivingEntity livingEntity) {
            Vec3 releasePosition = placePlayerAtRagdoll ? releasePosition(level, subLevel) : null;
            Vec3 inheritedVelocity = sublevelVelocityAsBlocksPerTick(level, subLevel);
            Vec3 exitVelocity = inheritedVelocity == null ? Vec3.ZERO : inheritedVelocity;
            if (livingEntity.isPassenger()) {
               livingEntity.stopRiding();
            }
            if (releasePosition != null) {
               if (livingEntity instanceof ServerPlayer player) {
                  player.teleportTo(level, releasePosition.x, releasePosition.y, releasePosition.z, player.getYRot(), player.getXRot());
               } else {
                  livingEntity.teleportTo(releasePosition.x, releasePosition.y, releasePosition.z);
               }
            }
            if (exitVelocity != Vec3.ZERO) {
               livingEntity.setDeltaMovement(exitVelocity);
            }
            RagdollSeatingHelper.restoreVisibility(livingEntity);
            if (livingEntity instanceof ServerPlayer player) {
               RagdollRegistry.suppressAfterRelease(player.getUUID(), level.getGameTime());
               RagdollSeatCallbacks.notifyReleased(player);
               NeoForge.EVENT_BUS.post(new RagdollEndEvent(player, exitVelocity, endReason(subLevel)));
            }
         }
      }
   }

   private static RagdollEndEvent.Reason endReason(ServerSubLevel subLevel) {
      String reason = RagdollSessionManager.getEndReason(subLevel);
      if ("player died".equals(reason)) return RagdollEndEvent.Reason.PLAYER_DEATH;
      if ("player disconnected".equals(reason)) return RagdollEndEvent.Reason.PLAYER_LOGOUT;
      if (reason != null && reason.startsWith("api")) return RagdollEndEvent.Reason.RELEASED;
      return RagdollEndEvent.Reason.EXPIRED;
   }

   @Nullable
   private static Vec3 releasePosition(ServerLevel level, ServerSubLevel rootSubLevel) {
      ServerSubLevel source = torsoPart(level, rootSubLevel);
      if (source.getPlot() == null) return null;
      return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(source.getPlot().getCenterBlock())).add(0.0, 0.5, 0.0);
   }

   @Nullable
   private static Vec3 sublevelVelocityAsBlocksPerTick(ServerLevel level, ServerSubLevel rootSubLevel) {
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return null;
      ServerSubLevel source = torsoPart(level, rootSubLevel);
      RigidBodyHandle handle = physicsSystem.getPhysicsHandle(source);
      if (handle == null || !handle.isValid()) return null;
      Vector3d vel = handle.getLinearVelocity(new Vector3d());
      return new Vec3(vel.x / 20.0, vel.y / 20.0, vel.z / 20.0);
   }

   private static ServerSubLevel torsoPart(ServerLevel level, ServerSubLevel rootSubLevel) {
      return rootSubLevel;
   }

   private static void discardSeatEntities(ServerLevel level, ServerSubLevel subLevel) {
      AABB bounds = plotBounds(subLevel);
      if (bounds != null) {
         for (RagdollSeatEntity seat : level.getEntitiesOfClass(RagdollSeatEntity.class, bounds)) {
            seat.ejectPassengers();
            seat.discard();
         }
      }
   }

   @Nullable
   private static AABB plotBounds(ServerSubLevel subLevel) {
      ServerLevelPlot plot = subLevel.getPlot();
      if (plot == null) return null;
      BoundingBox3ic box = plot.getBoundingBox();
      return new AABB(
         (double) box.minX(), (double) box.minY(), (double) box.minZ(),
         (double) box.maxX() + 1.0, (double) box.maxY() + 1.0, (double) box.maxZ() + 1.0
      );
   }
}
