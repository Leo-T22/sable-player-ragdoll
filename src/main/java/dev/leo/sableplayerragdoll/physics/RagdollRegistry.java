package dev.leo.sableplayerragdoll.physics;

import com.mojang.authlib.GameProfile;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.block.RagdollBlocks;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class RagdollRegistry {
   private static final double BLOCKS_PER_TICK_TO_METERS_PER_SECOND = 20.0;
   private static final double MANUAL_RAGDOLL_UPWARD_SPEED = 10.0;
   private static final Set<UUID> RAGDOLL_BODY_IDS = new HashSet<>();
   private static final Map<UUID, Long> PLAYER_COOLDOWNS = new HashMap<>();
   private static boolean loggedFirstTick;

   private RagdollRegistry() {
   }

   // Called by RagdollAPI and detection layer to create and launch a ragdoll.
   @Nullable
   public static ServerSubLevel launch(ServerLevel level, ServerPlayer player, Vector3d linear, Vector3d angular, boolean elytraPose, boolean autoSeat) {
      if (!RagdollSettings.enabled()) return null;
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return null;

      boolean ragdollPose = elytraPose && player.isFallFlying();
      Vec3 launchDir = new Vec3(linear.x, linear.y, linear.z);
      ServerSubLevel ragdollBody = ragdollPose
         ? assembleElytraRagdollBody(level, player, launchDir)
         : assembleRagdollBody(level, player, bodyForward(player));
      if (ragdollBody == null) return null;

      BlockPos plotSeat = ragdollBody.getPlot().getCenterBlock();
      if (!ensureValidMass(ragdollBody, List.of(plotSeat))) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] ragdoll {} has no valid mass — dropping", shortId(ragdollBody.getUniqueId()));
         dropFailed(physicsSystem, ragdollBody);
         return null;
      }

      RAGDOLL_BODY_IDS.add(ragdollBody.getUniqueId());
      UUID seatPlayerId = autoSeat ? player.getUUID() : null;
      RagdollDeferredSync.queueLaunch(ragdollBody, linear, angular, seatPlayerId, ragdollPose);
      PLAYER_COOLDOWNS.put(player.getUUID(), level.getGameTime() + (long) RagdollSettings.cooldownTicks());
      SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] queued ragdoll {} for {} (launch + sitDown next tick)",
         shortId(ragdollBody.getUniqueId()), player.getGameProfile().getName());
      return ragdollBody;
   }

   @Nullable
   public static ServerSubLevel spawnPlayerless(ServerLevel level, Vec3 baseCenter, Vec3 heading, GameProfile profile, Vector3d linear, Vector3d angular) {
      return spawnPlayerless(level, baseCenter, heading, profile, linear, angular, PlayerlessDespawnRule.defaultRule());
   }

   @Nullable
   public static ServerSubLevel spawnPlayerless(
      ServerLevel level,
      Vec3 baseCenter,
      Vec3 heading,
      GameProfile profile,
      Vector3d linear,
      Vector3d angular,
      PlayerlessDespawnRule despawnRule
   ) {
      if (!RagdollSettings.enabled()) return null;
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return null;

      Vec3 forward = normalizeOr(new Vec3(heading.x, 0.0, heading.z), new Vec3(0.0, 0.0, 1.0));
      Vec3 right = horizontalRight(forward);
      RagdollAssemblyHelper.Doll doll = RagdollAssemblyHelper.spawn(level, profile, baseCenter, right, forward);
      if (doll == null) return null;

      ServerSubLevel ragdollBody = doll.headSubLevel();
      BlockPos plotSeat = ragdollBody.getPlot().getCenterBlock();
      if (!ensureValidMass(ragdollBody, List.of(plotSeat))) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] playerless ragdoll {} has no valid mass - dropping", shortId(ragdollBody.getUniqueId()));
         dropFailed(physicsSystem, ragdollBody);
         return null;
      }

      RAGDOLL_BODY_IDS.add(ragdollBody.getUniqueId());
      RagdollDeferredSync.queuePlayerlessLaunch(ragdollBody, linear, angular, false, despawnRule);
      SablePlayerRagdoll.LOGGER.info(
         "[sable_player_ragdoll] queued playerless ragdoll {} at {} heading={} ({} parts, {} constraints)",
         shortId(ragdollBody.getUniqueId()),
         BlockPos.containing(baseCenter).toShortString(),
         fmtVec3dc(new Vector3d(forward.x, forward.y, forward.z)),
         doll.allSubLevels().size(),
         doll.constraints()
      );
      return ragdollBody;
   }

   // Manual keybind trigger (sent from client). Uses player's current movement as launch velocity.
   public static boolean triggerManual(ServerPlayer player) {
      if (!RagdollSettings.enabled()) return false;
      ServerLevel level = player.serverLevel();
      long gameTime = level.getGameTime();
      if (!canTarget(player, gameTime, true)) {
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] manual ragdoll ignored for {} (not a valid target)", player.getGameProfile().getName());
         return false;
      }

      boolean elytraPose = player.isFallFlying();
      Vector3d linear = elytraPose
         ? clampRagdollLaunchVelocity(toMetersPerSecond(player.getDeltaMovement()))
         : withManualKick(clampRagdollLaunchVelocity(toMetersPerSecond(new Vec3(player.getDeltaMovement().x, 0, player.getDeltaMovement().z))));
      Vector3d angular = new Vector3d();
      ServerSubLevel body = launch(level, player, linear, angular, elytraPose, RagdollSettings.autoSeatOnTrigger());
      if (body != null) {
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] manual ragdoll {} for {} launch={} m/s",
            shortId(body.getUniqueId()), player.getGameProfile().getName(), fmtVec3dc(linear));
      }
      return body != null;
   }

   public static boolean triggerWeaponHit(ServerPlayer attacker, ServerPlayer target) {
      if (!RagdollSettings.enabled()) return false;
      ServerLevel level = target.serverLevel();
      long gameTime = level.getGameTime();
      if (!canTarget(target, gameTime, true)) {
         if (RagdollSettings.debugLogging()) {
            SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] weapon ragdoll ignored for {} (not a valid target)", target.getGameProfile().getName());
         }
         return false;
      }

      Vec3 direction = target.position().subtract(attacker.position());
      Vec3 attackerForward = new Vec3(attacker.getLookAngle().x, 0.0, attacker.getLookAngle().z);
      direction = normalizeOr(new Vec3(direction.x, 0.0, direction.z), normalizeOr(attackerForward, new Vec3(0.0, 0.0, 1.0)));
      Vector3d linear = withManualKick(clampRagdollLaunchVelocity(toMetersPerSecond(direction.normalize().scale(0.6))));
      Vector3d angular = new Vector3d();
      ServerSubLevel body = launch(level, target, linear, angular, false, RagdollSettings.autoSeatOnTrigger());
      if (body != null && RagdollSettings.debugLogging()) {
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] weapon ragdoll {} for {} by {} launch={} m/s",
            shortId(body.getUniqueId()), target.getGameProfile().getName(), attacker.getGameProfile().getName(), fmtVec3dc(linear));
      }
      return body != null;
   }

   // Called from the Sable physics tick hook registered in SablePlayerRagdollBootstrap.
   public static void onPostPhysicsTick(SubLevelPhysicsSystem physicsSystem, double timeStep) {
      if (RagdollSettings.enabled()) {
         if (!loggedFirstTick) {
            loggedFirstTick = true;
            SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] ragdoll system active (debug={})", RagdollSettings.debugLogging());
         }
         RagdollDeferredSync.flush(physicsSystem);
      }
   }

   static void untrack(UUID subLevelId) {
      RAGDOLL_BODY_IDS.remove(subLevelId);
   }

   static void dropFailed(SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      if (subLevel != null && !subLevel.isRemoved()) {
         RagdollSessionManager.unregister(subLevel);
         untrack(subLevel.getUniqueId());
         RagdollDeferredSync.cancel(subLevel.getUniqueId());
         RagdollRemovalHelper.removeRagdollSubLevel(physicsSystem, subLevel);
      }
   }

   static void wakePhysicsBody(SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      if (subLevel != null && !subLevel.isRemoved()) {
         try {
            physicsSystem.getPipeline().wakeUp(subLevel);
         } catch (Throwable var3) {
            SablePlayerRagdoll.LOGGER.debug("wakeUp failed for {}: {}", subLevel.getUniqueId(), var3.toString());
         }
      }
   }

   // Clears ragdoll-side per-player state after release. Detection layer handles its own cleanup.
   static void suppressAfterRelease(UUID playerId, long gameTime) {
      RagdollControlHelper.clearInput(playerId);
      PLAYER_COOLDOWNS.put(playerId, gameTime + (long) RagdollSettings.cooldownTicks());
   }

   public static void resetState() {
      RAGDOLL_BODY_IDS.clear();
      PLAYER_COOLDOWNS.clear();
   }

   private static @Nullable ServerSubLevel assembleRagdollBody(ServerLevel level, ServerPlayer player, Vec3 poseForward) {
      Vec3 forward = normalizeOr(new Vec3(poseForward.x, 0.0, poseForward.z), bodyForward(player));
      Vec3 right = horizontalRight(forward);
      Vec3 baseCenter = Vec3.atCenterOf(BlockPos.containing(player.position()));
      RagdollAssemblyHelper.Doll doll = RagdollAssemblyHelper.spawn(level, player, baseCenter, right, forward);
      if (doll == null) return null;
      SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] assembled ragdoll {} for {} ({} parts, {} constraints)",
         shortId(doll.headSubLevel().getUniqueId()), player.getGameProfile().getName(), doll.allSubLevels().size(), doll.constraints());
      return doll.headSubLevel();
   }

   private static @Nullable ServerSubLevel assembleElytraRagdollBody(ServerLevel level, ServerPlayer player, Vec3 movementDirection) {
      Vec3 up = normalizeOr(movementDirection, yawForward(player));
      Vec3 forward = projectedOntoPlane(new Vec3(0.0, -1.0, 0.0), up);
      if (forward.lengthSqr() < 1.0E-6) forward = projectedOntoPlane(yawForward(player), up);
      forward = normalizeOr(forward, new Vec3(0.0, 0.0, 1.0));
      Vec3 right = normalizeOr(up.cross(forward), new Vec3(1.0, 0.0, 0.0));
      forward = normalizeOr(right.cross(up), forward);
      Vec3 baseCenter = Vec3.atCenterOf(BlockPos.containing(player.position()));
      RagdollAssemblyHelper.Doll doll = RagdollAssemblyHelper.spawn(level, player, baseCenter, right, up, forward, true);
      if (doll == null) return null;
      SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] assembled elytra ragdoll {} for {} ({} parts, {} constraints)",
         shortId(doll.headSubLevel().getUniqueId()), player.getGameProfile().getName(), doll.allSubLevels().size(), doll.constraints());
      return doll.headSubLevel();
   }

   private static boolean ensureValidMass(ServerSubLevel subLevel, List<BlockPos> plotPositions) {
      try {
         subLevel.getPlot().setBoundingBox(BoundingBox3i.from(plotPositions));
         subLevel.buildMassTracker();
         subLevel.updateMergedMassData(1.0F);
      } catch (Throwable var3) {
         SablePlayerRagdoll.LOGGER.warn("Mass rebuild threw for ragdoll {}", subLevel.getUniqueId(), var3);
         return false;
      }
      Vector3dc com = subLevel.getMassTracker().getCenterOfMass();
      return com != null && Double.isFinite(com.x()) && Double.isFinite(com.y()) && Double.isFinite(com.z());
   }

   private static boolean canTarget(ServerPlayer player, long gameTime, boolean manualTrigger) {
      if (player.isDeadOrDying() || player.isPassenger() || player.isSpectator()) return false;
      if (!manualTrigger && player.isFallFlying()) return false;
      if (!manualTrigger && player.isCreative() && !RagdollSettings.affectCreative()) return false;
      if (RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID()) != null) return false;
      return gameTime >= PLAYER_COOLDOWNS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
   }

   private static Vector3d withManualKick(Vector3d linear) {
      linear.y += MANUAL_RAGDOLL_UPWARD_SPEED;
      return linear;
   }

   private static Vector3d toMetersPerSecond(Vec3 blocksPerTick) {
      return new Vector3d(blocksPerTick.x, blocksPerTick.y, blocksPerTick.z).mul(BLOCKS_PER_TICK_TO_METERS_PER_SECOND);
   }

   private static Vector3d clampRagdollLaunchVelocity(Vector3d linear) {
      double max = RagdollSettings.ragdollMaxLaunchSpeed();
      double speed = linear.length();
      if (speed > max && speed > 1.0E-6) linear.mul(max / speed);
      return linear;
   }

   private static Vec3 bodyForward(ServerPlayer player) {
      return Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
   }

   private static Vec3 yawForward(ServerPlayer player) {
      return Vec3.directionFromRotation(0.0F, player.getYRot()).normalize();
   }

   private static Vec3 horizontalRight(Vec3 forward) {
      return normalizeOr(new Vec3(0.0, 1.0, 0.0).cross(forward), new Vec3(1.0, 0.0, 0.0));
   }

   private static Vec3 normalizeOr(Vec3 vector, Vec3 fallback) {
      return vector.lengthSqr() < 1.0E-6 ? fallback : vector.normalize();
   }

   private static Vec3 projectedOntoPlane(Vec3 vector, Vec3 normal) {
      return vector.subtract(normal.scale(vector.dot(normal)));
   }

   public static String shortId(UUID id) {
      return id == null ? "null" : id.toString().substring(0, 8);
   }

   public static String fmtVec3dc(Vector3dc vec) {
      return fmt(vec.x()) + "," + fmt(vec.y()) + "," + fmt(vec.z());
   }

   private static String fmt(double value) {
      return String.format("%.2f", value);
   }
}
