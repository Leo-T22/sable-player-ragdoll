package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.RagdollSeatCallbacks;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class RagdollExpireHelper {
   private RagdollExpireHelper() {
   }

   public static void expire(SubLevelPhysicsSystem physicsSystem, ServerLevel level, ServerSubLevel subLevel, String reason) {
      if (!subLevel.isRemoved() && !RagdollSessionManager.isExpiring(subLevel)) {
         RagdollSessionManager.markExpiring(subLevel);
         unseatRider(level, subLevel);
         discardSeatEntities(level, subLevel);
         RagdollSessionManager.unregister(subLevel);
         RagdollRegistry.untrack(subLevel.getUniqueId());
         RagdollDeferredSync.queueRemoval(subLevel.getUniqueId(), level);
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] expiring ragdoll {} ({})", RagdollRegistry.shortId(subLevel.getUniqueId()), reason);
      }
   }

   public static void expireImmediate(SubLevelPhysicsSystem physicsSystem, ServerLevel level, ServerSubLevel subLevel, String reason) {
      if (!subLevel.isRemoved() && !RagdollSessionManager.isExpiring(subLevel)) {
         RagdollSessionManager.markExpiring(subLevel);
         unseatRider(level, subLevel);
         discardSeatEntities(level, subLevel);
         RagdollSessionManager.unregister(subLevel);
         RagdollRegistry.untrack(subLevel.getUniqueId());
         RagdollDeferredSync.cancel(subLevel.getUniqueId());
         RagdollRemovalHelper.removeRagdollSubLevel(physicsSystem, subLevel);
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] expiring ragdoll {} immediately ({})", RagdollRegistry.shortId(subLevel.getUniqueId()), reason);
      }
   }

   private static void unseatRider(ServerLevel level, ServerSubLevel subLevel) {
      UUID playerId = RagdollSessionManager.getPlayerId(subLevel);
      if (playerId != null) {
         Entity entity = level.getEntity(playerId);
         if (entity instanceof LivingEntity livingEntity) {
            Vec3 inheritedVelocity = sublevelVelocityAsBlocksPerTick(level, subLevel);
            if (livingEntity.isPassenger()) {
               livingEntity.stopRiding();
            }
            if (inheritedVelocity != null) {
               livingEntity.setDeltaMovement(inheritedVelocity);
            }
            RagdollSeatingHelper.restoreVisibility(livingEntity);
            if (livingEntity instanceof ServerPlayer player) {
               RagdollRegistry.suppressAfterRelease(player.getUUID(), level.getGameTime());
               RagdollSeatCallbacks.notifyReleased(player);
            }
         }
      }
   }

   @Nullable
   private static Vec3 sublevelVelocityAsBlocksPerTick(ServerLevel level, ServerSubLevel headSubLevel) {
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return null;
      ServerSubLevel source = torsoPart(level, headSubLevel);
      RigidBodyHandle handle = physicsSystem.getPhysicsHandle(source);
      if (handle == null || !handle.isValid()) return null;
      Vector3d vel = handle.getLinearVelocity(new Vector3d());
      return new Vec3(vel.x / 20.0, vel.y / 20.0, vel.z / 20.0);
   }

   private static ServerSubLevel torsoPart(ServerLevel level, ServerSubLevel headSubLevel) {
      UUID torsoId = RagdollAssemblyHelper.linkedTorso(headSubLevel.getUniqueId());
      if (torsoId == null) return headSubLevel;
      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (!(container instanceof ServerSubLevelContainer serverContainer)) return headSubLevel;
      SubLevel torso = serverContainer.getSubLevel(torsoId);
      return torso instanceof ServerSubLevel serverTorso && !serverTorso.isRemoved() ? serverTorso : headSubLevel;
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
