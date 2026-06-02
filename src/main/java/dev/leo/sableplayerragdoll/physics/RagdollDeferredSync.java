package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class RagdollDeferredSync {
   private static final int REMOVAL_DELAY_TICKS = 1;
   private static final Map<UUID, PendingLaunch> PENDING_LAUNCHES = new ConcurrentHashMap<>();
   private static final Map<UUID, PendingRemoval> PENDING_REMOVALS = new ConcurrentHashMap<>();

   private RagdollDeferredSync() {
   }

   public static void queueLaunch(ServerSubLevel subLevel, org.joml.Vector3d linearVelocity, org.joml.Vector3d angularVelocity, UUID seatEntityId) {
      queueLaunch(subLevel, linearVelocity, angularVelocity, seatEntityId, false);
   }

   public static void queueLaunch(ServerSubLevel subLevel, org.joml.Vector3d linearVelocity, org.joml.Vector3d angularVelocity, UUID seatEntityId, boolean launchAllLinkedParts) {
      PENDING_LAUNCHES.put(subLevel.getUniqueId(), new PendingLaunch(linearVelocity, angularVelocity, seatEntityId, launchAllLinkedParts, false, PlayerlessDespawnRule.defaultRule(), subLevel.getLevel()));
   }

   public static void queuePlayerlessLaunch(ServerSubLevel subLevel, org.joml.Vector3d linearVelocity, org.joml.Vector3d angularVelocity, boolean launchAllLinkedParts) {
      queuePlayerlessLaunch(subLevel, linearVelocity, angularVelocity, launchAllLinkedParts, PlayerlessDespawnRule.defaultRule());
   }

   public static void queuePlayerlessLaunch(
      ServerSubLevel subLevel,
      org.joml.Vector3d linearVelocity,
      org.joml.Vector3d angularVelocity,
      boolean launchAllLinkedParts,
      PlayerlessDespawnRule despawnRule
   ) {
      PENDING_LAUNCHES.put(subLevel.getUniqueId(), new PendingLaunch(linearVelocity, angularVelocity, null, launchAllLinkedParts, true, despawnRule, subLevel.getLevel()));
   }

   public static void queueRemoval(UUID subLevelId, ServerLevel level) {
      PENDING_REMOVALS.putIfAbsent(subLevelId, new PendingRemoval(REMOVAL_DELAY_TICKS, level));
   }

   public static void cancel(UUID subLevelId) {
      PENDING_LAUNCHES.remove(subLevelId);
      PENDING_REMOVALS.remove(subLevelId);
   }

   public static void flush(SubLevelPhysicsSystem physicsSystem) {
      flushLaunches(physicsSystem);
      flushRemovals(physicsSystem);
   }

   private static void flushLaunches(SubLevelPhysicsSystem physicsSystem) {
      if (!PENDING_LAUNCHES.isEmpty()) {
         SubLevelContainer container = SubLevelContainer.getContainer(physicsSystem.getLevel());
         if (!(container instanceof ServerSubLevelContainer serverContainer)) {
            PENDING_LAUNCHES.clear();
            return;
         }
         Iterator<Entry<UUID, PendingLaunch>> iterator = PENDING_LAUNCHES.entrySet().iterator();
         while (iterator.hasNext()) {
            Entry<UUID, PendingLaunch> entry = iterator.next();
            if (entry.getValue().level() != physicsSystem.getLevel()) continue;
            if (serverContainer.getSubLevel(entry.getKey()) instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
               PendingLaunch launch = entry.getValue();
               try {
                  RagdollRegistry.wakePhysicsBody(physicsSystem, serverSubLevel);
                  physicsSystem.getPipeline().onStatsChanged(serverSubLevel);
                  RagdollRegistry.wakePhysicsBody(physicsSystem, serverSubLevel);
                  boolean nonPlayerPassenger = launch.nonPlayer();
                  if (launch.seatEntityId() != null) {
                     Entity seatEntity = physicsSystem.getLevel().getEntity(launch.seatEntityId());
                     nonPlayerPassenger = seatEntity != null && !(seatEntity instanceof ServerPlayer);
                     if (seatEntity instanceof LivingEntity livingEntity) {
                        RagdollSeatingHelper.trySeatEntity(physicsSystem.getLevel(), livingEntity, serverSubLevel);
                     }
                  }
                  int launchedParts = launchLinkedParts(physicsSystem, serverContainer, serverSubLevel, launch);
                  RagdollSessionManager.registerRagdoll(serverSubLevel, physicsSystem.getLevel().getGameTime(), launch.seatEntityId(), nonPlayerPassenger);
                  if (launch.nonPlayer()) {
                     RagdollSessionManager.setPlayerlessDespawnRule(serverSubLevel, launch.despawnRule());
                  }
                  SablePlayerRagdoll.LOGGER.info(
                     "[sable_player_ragdoll] ragdoll launched {} linear={} angular={} parts={}",
                     RagdollRegistry.shortId(serverSubLevel.getUniqueId()),
                     RagdollRegistry.fmtVec3dc(launch.linearVelocity()),
                     RagdollRegistry.fmtVec3dc(launch.angularVelocity()),
                     launchedParts
                  );
               } catch (Throwable var9) {
                  SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] launch failed for ragdoll {}: {}", entry.getKey(), var9.toString());
                  RagdollRegistry.dropFailed(physicsSystem, serverSubLevel);
               }
               iterator.remove();
               continue;
            }
            iterator.remove();
         }
      }
   }

   private static int launchLinkedParts(SubLevelPhysicsSystem physicsSystem, ServerSubLevel rootSubLevel, PendingLaunch launch) {
      SubLevelContainer container = SubLevelContainer.getContainer(physicsSystem.getLevel());
      if (container instanceof ServerSubLevelContainer serverContainer) {
         return launchLinkedParts(physicsSystem, serverContainer, rootSubLevel, launch);
      }
      physicsSystem.getPipeline().addLinearAndAngularVelocity(rootSubLevel, launch.linearVelocity(), launch.angularVelocity());
      return 1;
   }

   private static int launchLinkedParts(SubLevelPhysicsSystem physicsSystem, ServerSubLevelContainer serverContainer, ServerSubLevel rootSubLevel, PendingLaunch launch) {
      if (launch.launchAllLinkedParts()) {
         int launched = 0;
         for (UUID partId : RagdollAssemblyHelper.linkedParts(rootSubLevel.getUniqueId())) {
            if (serverContainer.getSubLevel(partId) instanceof ServerSubLevel partSubLevel && !partSubLevel.isRemoved()) {
               RagdollRegistry.wakePhysicsBody(physicsSystem, partSubLevel);
               physicsSystem.getPipeline().onStatsChanged(partSubLevel);
               RagdollRegistry.wakePhysicsBody(physicsSystem, partSubLevel);
               physicsSystem.getPipeline().addLinearAndAngularVelocity(partSubLevel, launch.linearVelocity(), launch.angularVelocity());
               launched++;
            }
         }
         if (launched > 0) return launched;
      }
      UUID torsoId = RagdollAssemblyHelper.linkedTorso(rootSubLevel.getUniqueId());
      if (torsoId != null && serverContainer.getSubLevel(torsoId) instanceof ServerSubLevel torsoSubLevel && !torsoSubLevel.isRemoved()) {
         RagdollRegistry.wakePhysicsBody(physicsSystem, torsoSubLevel);
         physicsSystem.getPipeline().onStatsChanged(torsoSubLevel);
         RagdollRegistry.wakePhysicsBody(physicsSystem, torsoSubLevel);
         physicsSystem.getPipeline().addLinearAndAngularVelocity(torsoSubLevel, launch.linearVelocity(), launch.angularVelocity());
         return 1;
      }
      physicsSystem.getPipeline().addLinearAndAngularVelocity(rootSubLevel, launch.linearVelocity(), launch.angularVelocity());
      return 1;
   }

   public static void flushRemovals(SubLevelPhysicsSystem physicsSystem) {
      if (!PENDING_REMOVALS.isEmpty()) {
         SubLevelContainer container = SubLevelContainer.getContainer(physicsSystem.getLevel());
         if (container instanceof ServerSubLevelContainer serverContainer) {
            Iterator<Entry<UUID, PendingRemoval>> iterator = PENDING_REMOVALS.entrySet().iterator();
            while (iterator.hasNext()) {
               Entry<UUID, PendingRemoval> entry = iterator.next();
               if (entry.getValue().level() != physicsSystem.getLevel()) continue;
               if (entry.getValue().ticksRemaining() > 1) {
                  PENDING_REMOVALS.put(entry.getKey(), new PendingRemoval(entry.getValue().ticksRemaining() - 1, entry.getValue().level()));
               } else {
                  SubLevel subLevel = serverContainer.getSubLevel(entry.getKey());
                  if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
                     RagdollRemovalHelper.removeRagdollSubLevel(physicsSystem, serverSubLevel);
                  }
                  iterator.remove();
               }
            }
         } else {
            PENDING_REMOVALS.clear();
         }
      }
   }

   private record PendingLaunch(
      org.joml.Vector3d linearVelocity,
      org.joml.Vector3d angularVelocity,
      UUID seatEntityId,
      boolean launchAllLinkedParts,
      boolean nonPlayer,
      PlayerlessDespawnRule despawnRule,
      ServerLevel level
   ) {
      private PendingLaunch(
         org.joml.Vector3d linearVelocity,
         org.joml.Vector3d angularVelocity,
         UUID seatEntityId,
         boolean launchAllLinkedParts,
         boolean nonPlayer,
         PlayerlessDespawnRule despawnRule,
         ServerLevel level
      ) {
         this.linearVelocity = new org.joml.Vector3d(linearVelocity);
         this.angularVelocity = new org.joml.Vector3d(angularVelocity);
         this.seatEntityId = seatEntityId;
         this.launchAllLinkedParts = launchAllLinkedParts;
         this.nonPlayer = nonPlayer;
         this.despawnRule = despawnRule;
         this.level = level;
      }
   }

   private record PendingRemoval(int ticksRemaining, ServerLevel level) {
   }
}
