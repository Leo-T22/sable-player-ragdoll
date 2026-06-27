package dev.leo.sableplayerragdoll.physics;

import com.mojang.authlib.GameProfile;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.api.RagdollStartEvent;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.leo.sableplayerragdoll.RagdollKeybindExample;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentScope;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentSnapshot;
import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.api.RagdollPoseSnapshot;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class RagdollRegistry {
   private static final double BLOCKS_PER_TICK_TO_METERS_PER_SECOND = 20.0;
   private static final Set<UUID> RAGDOLL_BODY_IDS = new HashSet<>();
   private static final Map<UUID, Long> PLAYER_COOLDOWNS = new HashMap<>();
   private static final Map<UUID, Long> LAUNCH_RESERVATIONS = new HashMap<>();
   private static final long LAUNCH_RESERVATION_TICKS = 10L;
   private static boolean loggedFirstTick;

   private RagdollRegistry() {
   }

   // Called by RagdollAPI and detection layer to create and launch a ragdoll.
   @Nullable
   public static ServerSubLevel launch(ServerLevel level, ServerPlayer player, Vector3d linear, Vector3d angular, boolean elytraPose, boolean autoSeat) {
      return launch(level, player, linear, angular, elytraPose, autoSeat, RagdollLimbOptions.defaults());
   }

   public static ServerSubLevel launch(ServerLevel level, ServerPlayer player, Vector3d linear, Vector3d angular, boolean elytraPose, boolean autoSeat, RagdollLimbOptions limbs) {
      return launch(level, player, linear, angular, elytraPose, autoSeat, limbs, new RagdollPoseSnapshot(RagdollLimbOptions.defaults(), player.yBodyRot));
   }

   public static ServerSubLevel launch(
      ServerLevel level,
      ServerPlayer player,
      Vector3d linear,
      Vector3d angular,
      boolean elytraPose,
      boolean autoSeat,
      RagdollLimbOptions motorLimbs,
      RagdollPoseSnapshot initialPose
   ) {
      if (!RagdollSettings.enabled()) return null;
      if (player.isSpectator()) return null;
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return null;

      UUID playerId = player.getUUID();
      long gameTime = level.getGameTime();
      if (isLaunchSuppressed(level, playerId, gameTime)) {
         if (RagdollSettings.debugLogging()) {
            SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] launch ignored for {} (already ragdolled or launch already in flight)",
               player.getGameProfile().getName());
         }
         return null;
      }

      RagdollStartEvent event = new RagdollStartEvent(player, new Vec3(linear.x, linear.y, linear.z));
      if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
         return null;
      }
      linear = new Vector3d(event.velocity().x, event.velocity().y, event.velocity().z);

      boolean ragdollPose = elytraPose && player.isFallFlying();
      RagdollLimbOptions poseLimbs = withPlayerModelPose(player, mergeInitialPose(initialPose.limbs(), motorLimbs));
      Vec3 launchDir = new Vec3(linear.x, linear.y, linear.z);
      ServerSubLevel ragdollBody = ragdollPose
         ? assembleElytraRagdollBody(level, player, launchDir, poseLimbs)
         : assembleRagdollBody(level, player, bodyForward(initialPose.bodyYawDegrees()), poseLimbs);
      if (ragdollBody == null) return null;

      BlockPos plotSeat = ragdollBody.getPlot().getCenterBlock();
      if (!ensureValidMass(ragdollBody, List.of(plotSeat))) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] ragdoll {} has no valid mass — dropping", shortId(ragdollBody.getUniqueId()));
         dropFailed(physicsSystem, ragdollBody);
         return null;
      }

      RAGDOLL_BODY_IDS.add(ragdollBody.getUniqueId());
      UUID seatPlayerId = autoSeat ? playerId : null;
      RagdollDeferredSync.queueLaunch(ragdollBody, linear, angular, seatPlayerId, true);
      PLAYER_COOLDOWNS.put(playerId, gameTime + (long) RagdollSettings.cooldownTicks());
      LAUNCH_RESERVATIONS.put(playerId, gameTime + LAUNCH_RESERVATION_TICKS);
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
      return spawnPlayerless(level, baseCenter, heading, profile, linear, angular, despawnRule, RagdollLimbOptions.defaults());
   }

   @Nullable
   public static ServerSubLevel spawnPlayerless(
      ServerLevel level,
      Vec3 baseCenter,
      Vec3 heading,
      GameProfile profile,
      Vector3d linear,
      Vector3d angular,
      PlayerlessDespawnRule despawnRule,
      RagdollLimbOptions limbs
   ) {
      if (!RagdollSettings.enabled()) return null;
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return null;

      Vec3 forward = normalizeOr(new Vec3(heading.x, 0.0, heading.z), new Vec3(0.0, 0.0, 1.0));
      Vec3 right = horizontalRight(forward);
      RagdollAssemblyHelper.Doll doll = RagdollAssemblyHelper.spawn(level, profile, baseCenter, right, forward, limbs);
      if (doll == null) return null;

      ServerSubLevel ragdollBody = doll.rootSubLevel();
      BlockPos plotSeat = ragdollBody.getPlot().getCenterBlock();
      if (!ensureValidMass(ragdollBody, List.of(plotSeat))) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] playerless ragdoll {} has no valid mass - dropping", shortId(ragdollBody.getUniqueId()));
         dropFailed(physicsSystem, ragdollBody);
         return null;
      }

      RAGDOLL_BODY_IDS.add(ragdollBody.getUniqueId());
      RagdollSavedData.get(level).saveRagdoll(ragdollBody.getUniqueId(), doll.partSubLevelIds(), limbs);
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

   @Nullable
   public static ServerSubLevel detachActiveToPlayerless(ServerLevel level, UUID playerId, PlayerlessDespawnRule rule) {
      ServerSubLevel body = RagdollSessionManager.activeRagdollForPlayer(level, playerId);
      if (body == null) return null;
      RagdollSessionManager.detachPlayer(body, rule, level.getGameTime());
      RagdollExpireHelper.unseatPlayerSilently(level, playerId);
      Map<BodyPart, UUID> partMap = RagdollAssemblyHelper.linkedPartsAsMap(body.getUniqueId());
      RagdollSavedData.get(level).saveRagdoll(body.getUniqueId(), partMap, RagdollLimbOptions.defaults());
      return body;
   }

   // Manual keybind trigger (sent from client). Uses player's current movement as launch velocity.
   public static boolean triggerManual(ServerPlayer player) {
      return triggerManual(player, new RagdollPoseSnapshot(RagdollLimbOptions.defaults(), player.yBodyRot));
   }

   public static boolean triggerManual(ServerPlayer player, RagdollPoseSnapshot initialPose) {
      if (!RagdollSettings.enabled()) return false;
      if (!RagdollSettings.allowManualTrigger()) {
         if (RagdollSettings.debugLogging()) {
            SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] manual ragdoll ignored for {} (manual trigger disabled)", player.getGameProfile().getName());
         }
         return false;
      }
      ServerLevel level = player.serverLevel();
      long gameTime = level.getGameTime();
      if (!canTarget(player, gameTime, true)) {
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] manual ragdoll ignored for {} (not a valid target)", player.getGameProfile().getName());
         return false;
      }
      boolean launched = RagdollKeybindExample.launch(player, initialPose) != null;
      if (launched) {
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] manual ragdoll triggered for {}",
            player.getGameProfile().getName());
      }
      return launched;
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
      Vector3d linear = clampRagdollLaunchVelocity(toMetersPerSecond(direction.normalize().scale(0.6)));
      linear.y += 10.0;
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

   public static void tryRestoreOnLoad(ServerLevel level, ServerSubLevel rootSubLevel) {
      UUID rootId = rootSubLevel.getUniqueId();
      if (hasLiveJoints(rootId)) {
         return;
      }

      RagdollSavedData savedData = RagdollSavedData.get(level);
      Map<BodyPart, UUID> savedParts = savedData.ragdoll(rootId);
      if (savedParts.isEmpty()) {
         return;
      }

      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (!(container instanceof ServerSubLevelContainer serverContainer)) {
         return;
      }

      Map<BodyPart, ServerSubLevel> loadedParts = new java.util.EnumMap<>(BodyPart.class);
      for (Map.Entry<BodyPart, UUID> entry : savedParts.entrySet()) {
         SubLevel partSubLevel = serverContainer.getSubLevel(entry.getValue());
         if (!(partSubLevel instanceof ServerSubLevel serverPart) || serverPart.isRemoved()) {
            return;
         }

         loadedParts.put(entry.getKey(), serverPart);
      }

      RagdollLimbOptions limbs = savedData.ragdollLimbs(rootId);
      RagdollAssemblyHelper.restoreConstraints(level, loadedParts, limbs);
      SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] restored playerless ragdoll {} ({} parts)",
         shortId(rootId), loadedParts.size());
   }

   private static boolean hasLiveJoints(UUID rootId) {
      Map<BodyPart, RagdollAssemblyHelper.RagdollJoint> joints = RagdollAssemblyHelper.joints(rootId);
      if (joints.isEmpty()) return false;
      for (RagdollAssemblyHelper.RagdollJoint joint : joints.values()) {
         if (joint.handle() == null || !joint.handle().isValid()) return false;
      }
      return true;
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
      LAUNCH_RESERVATIONS.remove(playerId);
   }
   private static boolean isLaunchSuppressed(ServerLevel level, UUID playerId, long gameTime) {
      if (RagdollSessionManager.activeRagdollForPlayer(level, playerId) != null) {
         return true;
      }
      Long reservedUntil = LAUNCH_RESERVATIONS.get(playerId);
      return reservedUntil != null && gameTime <= reservedUntil;
   }

   public static boolean removeById(ServerLevel level, UUID subLevelId) {
      return removeById(level, subLevelId, false);
   }

   public static boolean removeById(ServerLevel level, UUID subLevelId, boolean smokePuff) {
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return false;
      UUID rootId = RagdollAssemblyHelper.linkedRoot(subLevelId);
      UUID targetId = rootId != null ? rootId : subLevelId;
      SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(targetId);
      if (!(subLevel instanceof ServerSubLevel ssl) || ssl.isRemoved()) return false;
      tryRestoreOnLoad(level, ssl);
      if (smokePuff) emitRemovalPuff(level, ssl);
      RagdollExpireHelper.expireImmediate(physicsSystem, level, ssl, "api remove by id");
      return true;
   }

   public static void emitRemovalPuff(ServerLevel level, ServerSubLevel subLevel) {
      if (subLevel == null || subLevel.isRemoved()) return;
      Vec3 pos;
      if (subLevel.getPlot() == null) {
         Vector3dc p = subLevel.logicalPose().position();
         pos = new Vec3(p.x(), p.y(), p.z());
      } else {
         pos = Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(subLevel.getPlot().getCenterBlock()));
      }
      if (pos == null) return;
      level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 20, 0.45, 0.25, 0.45, 0.04);
      level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BREEZE_JUMP, SoundSource.BLOCKS, 0.8F, 1.15F);
   }

   @Nullable
   public static UUID dismember(ServerLevel level, UUID rootId, BodyPart limb) {
      UUID limbId = RagdollAssemblyHelper.dismember(rootId, limb);
      if (limbId != null) syncSavedAfterDismember(level, rootId);
      return limbId;
   }

   @Nullable
   public static UUID dismemberPart(ServerLevel level, UUID partSubLevelId) {
      UUID rootId = RagdollAssemblyHelper.linkedRoot(partSubLevelId);
      BodyPart limb = RagdollAssemblyHelper.bodyPartOf(partSubLevelId);
      if (rootId == null || limb == null) return null;
      return dismember(level, rootId, limb);
   }

   private static void syncSavedAfterDismember(ServerLevel level, UUID rootId) {
      RagdollSavedData saved = RagdollSavedData.get(level);
      if (!saved.ragdoll(rootId).isEmpty()) {
         saved.saveRagdoll(rootId, RagdollAssemblyHelper.linkedPartsAsMap(rootId), saved.ragdollLimbs(rootId));
      }
   }

   public static void setGrabDisabled(ServerLevel level, UUID subLevelId, boolean disabled) {
      SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(subLevelId);
      if (subLevel instanceof ServerSubLevel ssl) {
         RagdollSessionManager.setGrabDisabled(ssl, disabled);
      }
   }

   public static void setCorpse(ServerLevel level, UUID rootId, boolean corpse) {
      RagdollEquipmentHelper.applyToAllParts(level, rootId, part -> part.setCorpse(corpse));
      RagdollEquipmentHelper.sendPartUpdates(level, rootId);
   }

   public static boolean isGrabDisabledAt(ServerLevel level, BlockPos pos) {
      SubLevel part = Sable.HELPER.getContaining(level, pos);
      return part instanceof ServerSubLevel ssl && RagdollSessionManager.isGrabDisabled(ssl);
   }

   public static void applyEquipmentFrom(ServerLevel level, UUID rootId, net.minecraft.world.entity.player.Player player) {
      RagdollEquipmentHelper.applyFrom(level, rootId, player);
   }

   public static void applyExtraEquipmentFrom(ServerLevel level, UUID rootId, net.minecraft.world.entity.player.Player player) {
      RagdollEquipmentHelper.applyExtraFrom(level, rootId, player);
   }

   public static RagdollEquipmentSnapshot captureEquipment(net.minecraft.world.entity.player.Player player, RagdollEquipmentScope scope) {
      return RagdollEquipmentHelper.capture(player, scope);
   }

   public static void applyEquipmentSnapshot(ServerLevel level, UUID rootId, RagdollEquipmentSnapshot snapshot) {
      RagdollEquipmentHelper.applySnapshot(level, rootId, snapshot);
   }

   public static void resetState() {
      RAGDOLL_BODY_IDS.clear();
      PLAYER_COOLDOWNS.clear();
      RagdollAssemblyHelper.resetState();
   }

   private static @Nullable ServerSubLevel assembleRagdollBody(ServerLevel level, ServerPlayer player, Vec3 poseForward, RagdollLimbOptions limbs) {
      Vec3 forward = normalizeOr(new Vec3(poseForward.x, 0.0, poseForward.z), bodyForward(player));
      Vec3 right = horizontalRight(forward);
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      double pitchRadians = Math.toRadians(proneBodyPitchDegrees(player));
      if (Math.abs(pitchRadians) > 1.0E-4) {
         double cos = Math.cos(pitchRadians);
         double sin = Math.sin(pitchRadians);
         Vec3 tiltedUp = up.scale(cos).add(forward.scale(sin));
         Vec3 tiltedForward = forward.scale(cos).subtract(up.scale(sin));
         up = normalizeOr(tiltedUp, up);
         forward = normalizeOr(tiltedForward, forward);
      }
      Vec3 baseCenter = player.position();
      RagdollAssemblyHelper.Doll doll = RagdollAssemblyHelper.spawn(level, player, baseCenter, right, up, forward, false, limbs);
      if (doll == null) return null;
      SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] assembled ragdoll {} for {} ({} parts, {} constraints)",
         shortId(doll.rootSubLevel().getUniqueId()), player.getGameProfile().getName(), doll.allSubLevels().size(), doll.constraints());
      return doll.rootSubLevel();
   }

   private static double proneBodyPitchDegrees(ServerPlayer player) {
      if (player.isFallFlying()) return 0.0;
      float swimAmount = player.getSwimAmount(1.0F);
      if (swimAmount <= 0.0F) return 0.0;
      double target = player.isInWater() ? 90.0 + player.getXRot() : 90.0;
      return swimAmount * target;
   }

   private static @Nullable ServerSubLevel assembleElytraRagdollBody(ServerLevel level, ServerPlayer player, Vec3 movementDirection, RagdollLimbOptions limbs) {
      Vec3 up = normalizeOr(movementDirection, yawForward(player));
      Vec3 forward = projectedOntoPlane(new Vec3(0.0, -1.0, 0.0), up);
      if (forward.lengthSqr() < 1.0E-6) forward = projectedOntoPlane(yawForward(player), up);
      forward = normalizeOr(forward, new Vec3(0.0, 0.0, 1.0));
      Vec3 right = normalizeOr(up.cross(forward), new Vec3(1.0, 0.0, 0.0));
      forward = normalizeOr(right.cross(up), forward);
      Vec3 baseCenter = player.position();
      RagdollAssemblyHelper.Doll doll = RagdollAssemblyHelper.spawn(level, player, baseCenter, right, up, forward, true, limbs);
      if (doll == null) return null;
      SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] assembled elytra ragdoll {} for {} ({} parts, {} constraints)",
         shortId(doll.rootSubLevel().getUniqueId()), player.getGameProfile().getName(), doll.allSubLevels().size(), doll.constraints());
      return doll.rootSubLevel();
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
      String reject = canTargetReject(player, gameTime, manualTrigger);
      if (reject != null) {
         if (RagdollSettings.debugLogging()) {
            SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] canTarget({}) rejected: {}", player.getGameProfile().getName(), reject);
         }
         return false;
      }
      return true;
   }

   private static String canTargetReject(ServerPlayer player, long gameTime, boolean manualTrigger) {
      if (player.isDeadOrDying()) return "isDeadOrDying";
      if (player.isPassenger()) return "isPassenger(vehicle=" + (player.getVehicle() == null ? "null" : player.getVehicle().getType()) + ")";
      if (player.isSpectator()) return "isSpectator";
      if (!manualTrigger && player.isFallFlying()) return "isFallFlying";
      if (!manualTrigger && player.isCreative() && !RagdollSettings.affectCreative()) return "creative+!affectCreative";
      var existing = RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID());
      if (existing != null) {
         return "activeRagdollForPlayer!=null (subLevel=" + existing.getUniqueId()
            + ", storedPlayerId=" + RagdollSessionManager.getPlayerId(existing) + ")";
      }
      if (gameTime < PLAYER_COOLDOWNS.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
         return "cooldown (until=" + PLAYER_COOLDOWNS.get(player.getUUID()) + ", now=" + gameTime + ")";
      }
      return null;
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
      return bodyForward(player.yBodyRot);
   }

   private static Vec3 bodyForward(float bodyYaw) {
      return Vec3.directionFromRotation(0.0F, bodyYaw).normalize();
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

   private static RagdollLimbOptions withPlayerModelPose(ServerPlayer player, RagdollLimbOptions limbs) {
      RagdollLimbOptions.Builder builder = RagdollLimbOptions.builder();
      putPose(builder, BodyPart.HEAD, limbs.get(BodyPart.HEAD), player.getXRot(), Mth.wrapDegrees(player.yBodyRot - player.getYHeadRot()), 0.0);
      putPose(builder, BodyPart.TORSO, limbs.get(BodyPart.TORSO), 0.0, 0.0, 0.0);
      putPose(builder, BodyPart.LEFT_ARM, limbs.get(BodyPart.LEFT_ARM), 0.0, 0.0, 0.0);
      putPose(builder, BodyPart.RIGHT_ARM, limbs.get(BodyPart.RIGHT_ARM), 0.0, 0.0, 0.0);
      putPose(builder, BodyPart.LEFT_LEG, limbs.get(BodyPart.LEFT_LEG), 0.0, 0.0, 0.0);
      putPose(builder, BodyPart.RIGHT_LEG, limbs.get(BodyPart.RIGHT_LEG), 0.0, 0.0, 0.0);
      return builder.build();
   }

   private static RagdollLimbOptions mergeInitialPose(RagdollLimbOptions initialPose, RagdollLimbOptions motorLimbs) {
      RagdollLimbOptions.Builder builder = RagdollLimbOptions.builder();
      for (BodyPart part : BodyPart.values()) {
         RagdollLimbConfig initial = initialPose.get(part);
         RagdollLimbConfig motor = motorLimbs.get(part);
         if (initial == null && motor == null) continue;

         RagdollLimbConfig.Builder config = RagdollLimbConfig.builder();
         if (initial != null && initial.rightOffset().isPresent() && initial.upOffset().isPresent() && initial.forwardOffset().isPresent()) {
            config.offset(initial.rightOffset().getAsDouble(), initial.upOffset().getAsDouble(), initial.forwardOffset().getAsDouble());
         }
         if (initial != null && initial.initialPitchDegrees().isPresent() && initial.initialYawDegrees().isPresent() && initial.initialRollDegrees().isPresent()) {
            config.initialRotation(
               initial.initialPitchDegrees().getAsDouble(),
               initial.initialYawDegrees().getAsDouble(),
               initial.initialRollDegrees().getAsDouble()
            );
         }
         if (motor != null && motor.pitchDegrees().isPresent()) config.pitch(motor.pitchDegrees().getAsDouble());
         if (motor != null && motor.yawDegrees().isPresent()) config.yaw(motor.yawDegrees().getAsDouble());
         if (motor != null && motor.rollDegrees().isPresent()) config.roll(motor.rollDegrees().getAsDouble());
         if (motor != null && motor.angularStiffness().isPresent()) config.stiffness(motor.angularStiffness().getAsDouble());
         if (motor != null && motor.angularDamping().isPresent()) config.damping(motor.angularDamping().getAsDouble());
         builder.limb(part, config);
      }
      return builder.build();
   }

   private static void putPose(
      RagdollLimbOptions.Builder builder,
      BodyPart part,
      @Nullable RagdollLimbConfig existing,
      double pitch,
      double yaw,
      double roll
   ) {
      RagdollLimbConfig.Builder config = RagdollLimbConfig.builder();
      if (existing != null && existing.initialPitchDegrees().isPresent() && existing.initialYawDegrees().isPresent() && existing.initialRollDegrees().isPresent()) {
         config.initialRotation(
            existing.initialPitchDegrees().getAsDouble(),
            existing.initialYawDegrees().getAsDouble(),
            existing.initialRollDegrees().getAsDouble()
         );
      } else {
         config.initialRotation(pitch, yaw, roll);
      }
      if (existing != null && existing.pitchDegrees().isPresent()) config.pitch(existing.pitchDegrees().getAsDouble());
      if (existing != null && existing.yawDegrees().isPresent()) config.yaw(existing.yawDegrees().getAsDouble());
      if (existing != null && existing.rollDegrees().isPresent()) config.roll(existing.rollDegrees().getAsDouble());
      if (existing != null && existing.angularStiffness().isPresent()) config.stiffness(existing.angularStiffness().getAsDouble());
      if (existing != null && existing.angularDamping().isPresent()) config.damping(existing.angularDamping().getAsDouble());
      builder.limb(part, config);
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
