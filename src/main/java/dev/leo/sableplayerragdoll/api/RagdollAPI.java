package dev.leo.sableplayerragdoll.api;

import com.mojang.authlib.GameProfile;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollEndEvent;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollLaunchOptions;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollSession;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import dev.leo.sableplayerragdoll.physics.RagdollExpireHelper;
import dev.leo.sableplayerragdoll.physics.RagdollMotorEffects;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class RagdollAPI {
   private static final GameProfile DEFAULT_DUMMY_PROFILE = new GameProfile(
      UUID.nameUUIDFromBytes("sable_player_ragdoll:dummy".getBytes(StandardCharsets.UTF_8)),
      "Dummy"
   );

   private RagdollAPI() {
   }

   @Nullable
   public static RagdollSession launch(ServerPlayer player, Vec3 linearVelocityMetersPerSecond) {
      return launch(player, linearVelocityMetersPerSecond, RagdollLaunchOptions.defaults());
   }

   @Nullable
   public static RagdollSession launch(ServerPlayer player, Vec3 linearVelocityMetersPerSecond, List<DespawnCondition> conditions) {
      return launch(player, linearVelocityMetersPerSecond, RagdollLaunchOptions.builder().despawnConditions(conditions).build());
   }

   @Nullable
   public static RagdollSession launch(ServerPlayer player, Vec3 linearVelocityMetersPerSecond, RagdollLaunchOptions options) {
      return launch(player, linearVelocityMetersPerSecond, options, null);
   }
   @Nullable
   public static RagdollSession launch(ServerPlayer player, Vec3 linearVelocityMetersPerSecond, RagdollLaunchOptions options, @Nullable RagdollPoseSnapshot initialPose) {
      Vector3d linear = new Vector3d(linearVelocityMetersPerSecond.x, linearVelocityMetersPerSecond.y, linearVelocityMetersPerSecond.z);
      Vector3d angular = new Vector3d();
      RagdollLaunchOptions resolvedOptions = options == null ? RagdollLaunchOptions.defaults() : options;
      if (isRagdolled(player)) {
         return null;
      }
      if (initialPose != null) {
         return finishLaunch(player, resolvedOptions, linear, angular, initialPose);
      }
      RagdollAsyncPoseRequests.requestThenLaunch(player, linear, angular, resolvedOptions);
      return null;
   }

   @Nullable
   static RagdollSession finishLaunch(ServerPlayer player, RagdollLaunchOptions resolvedOptions, Vector3d linear, Vector3d angular, RagdollPoseSnapshot pose) {
      ServerLevel level = player.serverLevel();
      ServerSubLevel body = RagdollRegistry.launch(
         level,
         player,
         linear,
         angular,
         player.isFallFlying(),
         resolvedOptions.autoSeat(),
         resolvedOptions.limbs(),
         pose
      );
      if (body == null) return null;
      RagdollSessionManager.setCustomDespawnConditions(body, resolvedOptions.despawnConditions());
      if (resolvedOptions.lockDismount()) RagdollSessionManager.setDismountLocked(body, true);
      if (resolvedOptions.wailing() != null) {
         RagdollWailingOptions w = resolvedOptions.wailing();
         RagdollMotorEffects.applyWailing(level, body, w.stiffness(), w.durationTicks(), w.intervalTicks(), w.startDelayTicks());
      }
      return new ActiveRagdollSession(player, body, level.getGameTime(), resolvedOptions.despawnConditions());
   }

   @Nullable
   public static PlayerlessRagdollSession spawnPlayerless(ServerLevel level, Vec3 position, double headingDegrees) {
      return spawnPlayerless(level, position, headingDegrees, Vec3.ZERO);
   }

   @Nullable
   public static PlayerlessRagdollSession spawnPlayerless(ServerLevel level, Vec3 position, double headingDegrees, Vec3 linearVelocityMetersPerSecond) {
      return spawnPlayerless(level, position, headingDegrees, DEFAULT_DUMMY_PROFILE, linearVelocityMetersPerSecond, PlayerlessDespawnRule.defaultRule());
   }

   @Nullable
   public static PlayerlessRagdollSession spawnPlayerless(
      ServerLevel level,
      Vec3 position,
      double headingDegrees,
      Vec3 linearVelocityMetersPerSecond,
      PlayerlessDespawnRule despawnRule
   ) {
      return spawnPlayerless(level, position, headingDegrees, DEFAULT_DUMMY_PROFILE, linearVelocityMetersPerSecond, despawnRule);
   }

   @Nullable
   public static PlayerlessRagdollSession spawnPlayerless(ServerLevel level, Vec3 position, double headingDegrees, GameProfile profile, Vec3 linearVelocityMetersPerSecond) {
      return spawnPlayerless(level, position, headingDegrees, profile, linearVelocityMetersPerSecond, PlayerlessDespawnRule.defaultRule());
   }

   @Nullable
   public static PlayerlessRagdollSession spawnPlayerless(
      ServerLevel level,
      Vec3 position,
      double headingDegrees,
      GameProfile profile,
      Vec3 linearVelocityMetersPerSecond,
      PlayerlessDespawnRule despawnRule
   ) {
      return spawnPlayerless(level, position, headingDegrees, profile, linearVelocityMetersPerSecond, despawnRule, RagdollLimbOptions.defaults());
   }

   @Nullable
   public static PlayerlessRagdollSession spawnPlayerless(
      ServerLevel level,
      Vec3 position,
      double headingDegrees,
      GameProfile profile,
      Vec3 linearVelocityMetersPerSecond,
      PlayerlessDespawnRule despawnRule,
      RagdollLimbOptions limbs
   ) {
      Vec3 heading = Vec3.directionFromRotation(0.0F, (float) headingDegrees);
      Vector3d linear = new Vector3d(linearVelocityMetersPerSecond.x, linearVelocityMetersPerSecond.y, linearVelocityMetersPerSecond.z);
      RagdollLimbOptions resolvedLimbs = limbs == null ? RagdollLimbOptions.defaults() : limbs;
      ServerSubLevel body = RagdollRegistry.spawnPlayerless(level, position, heading, profile, linear, new Vector3d(), despawnRule, resolvedLimbs);
      if (body == null) return null;
      return new ActivePlayerlessRagdollSession(level, body, level.getGameTime());
   }

   @Nullable
   public static PlayerlessRagdollSession detachActive(ServerPlayer player, PlayerlessDespawnRule rule) {
      ServerLevel level = player.serverLevel();
      PlayerlessDespawnRule resolved = rule != null ? rule : PlayerlessDespawnRule.never();
      ServerSubLevel body = RagdollRegistry.detachActiveToPlayerless(level, player.getUUID(), resolved);
      if (body == null) return null;
      return new ActivePlayerlessRagdollSession(level, body, level.getGameTime());
   }

   public static boolean remove(ServerLevel level, UUID subLevelId) {
      return RagdollRegistry.removeById(level, subLevelId);
   }

   public static boolean remove(ServerLevel level, UUID subLevelId, boolean smokePuff) {
      return RagdollRegistry.removeById(level, subLevelId, smokePuff);
   }

   @Nullable
   public static UUID dismember(ServerLevel level, UUID rootId, BodyPart limb) {
      return RagdollRegistry.dismember(level, rootId, limb);
   }

   @Nullable
   public static UUID dismember(ServerLevel level, UUID partSubLevelId) {
      return RagdollRegistry.dismemberPart(level, partSubLevelId);
   }

   @Nullable
   public static RagdollSession activeSession(ServerPlayer player) {
      ServerSubLevel body = RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID());
      if (body == null) return null;
      return new ActiveRagdollSession(player, body, -1L, List.of());
   }

   public static boolean isRagdolled(ServerPlayer player) {
      return RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID()) != null;
   }

   public static boolean isRagdollSubLevel(UUID subLevelId) {
      return RagdollAssemblyHelper.isRagdollPart(subLevelId);
   }

   public static boolean isRagdollSubLevel(SubLevel subLevel) {
      return RagdollAssemblyHelper.isRagdollPart(subLevel.getUniqueId());
   }

   public static void setGrabDisabled(ServerLevel level, UUID subLevelId, boolean disabled) {
      RagdollRegistry.setGrabDisabled(level, subLevelId, disabled);
   }

   public static void setCorpse(ServerLevel level, UUID rootId, boolean corpse) {
      RagdollRegistry.setCorpse(level, rootId, corpse);
   }

   @Nullable
   public static UUID torsoSubLevelId(UUID rootId) {
      return rootId;
   }

   public static void copyEquipmentFrom(ServerLevel level, UUID rootId, Player player) {
      applyEquipmentSnapshot(level, rootId, captureEquipment(player, RagdollEquipmentScope.ALL));
   }

   public static void copyExtraEquipmentFrom(ServerLevel level, UUID rootId, Player player) {
      applyEquipmentSnapshot(level, rootId, captureEquipment(player, RagdollEquipmentScope.OPTIONAL_MODS));
   }

   public static RagdollEquipmentSnapshot captureEquipment(Player player, RagdollEquipmentScope scope) {
      return RagdollRegistry.captureEquipment(player, scope);
   }

   public static void applyEquipmentSnapshot(ServerLevel level, UUID rootId, RagdollEquipmentSnapshot snapshot) {
      RagdollRegistry.applyEquipmentSnapshot(level, rootId, snapshot);
   }
   @Nullable
   public static MobRagdollSession launchMob(ServerLevel level, LivingEntity mob, Vec3 linearVelocity) {
      return launchMob(level, mob, linearVelocity, Vec3.ZERO, MobRagdollLaunchOptions.defaults());
   }

   @Nullable
   public static MobRagdollSession launchMob(ServerLevel level, LivingEntity mob, Vec3 linearVelocity, Vec3 angularVelocity, MobRagdollLaunchOptions options) {
      if (!MobRagdollAssembly.requestLaunch(level, mob, linearVelocity, angularVelocity, options)) {
         return null;
      }
      return new ActiveMobRagdollSession(level, mob);
   }

   public static boolean isMobRagdolled(LivingEntity mob) {
      return MobRagdollAssembly.isPendingOrConverted(mob.getUUID());
   }

   public static void releaseMob(LivingEntity mob) {
      if (mob.level() instanceof ServerLevel serverLevel) {
         MobRagdollAssembly.despawn(serverLevel, mob);
      }
   }

   @Nullable
   public static UUID spawnMobless(ServerLevel level, EntityType<?> type, Vec3 position) {
      return spawnMobless(level, type, position, Vec3.ZERO, MobRagdollAssembly.DEFAULT_MOBLESS_DURATION_TICKS);
   }

   @Nullable
   public static UUID spawnMobless(ServerLevel level, EntityType<?> type, Vec3 position, Vec3 linearVelocity, int durationTicks) {
      return MobRagdollAssembly.spawnMobless(level, type, position, linearVelocity, durationTicks);
   }

   public static boolean removeMobRagdoll(ServerLevel level, UUID subLevelId) {
      return MobRagdollAssembly.removeBySubLevel(level, subLevelId, false);
   }

   public static boolean removeMobRagdoll(ServerLevel level, UUID subLevelId, boolean smokePuff) {
      return MobRagdollAssembly.removeBySubLevel(level, subLevelId, smokePuff);
   }

   @Nullable
   public static UUID dismemberMob(ServerLevel level, UUID partSubLevelId) {
      return MobRagdollAssembly.dismemberBySubLevel(level, partSubLevelId);
   }

   private record ActiveMobRagdollSession(ServerLevel level, LivingEntity entity) implements MobRagdollSession {
      @Override
      public Vec3 currentVelocity() {
         return MobRagdollAssembly.currentVelocity(entity.getUUID());
      }

      @Override
      public long elapsedTicks() {
         return MobRagdollAssembly.elapsedTicks(level, entity.getUUID());
      }

      @Override
      public void release() {
         MobRagdollAssembly.despawn(level, entity, MobRagdollEndEvent.Reason.RELEASED);
      }
   }

   private record ActiveRagdollSession(ServerPlayer player, ServerSubLevel subLevel, long startGameTime, List<DespawnCondition> customConditions)
         implements RagdollSession {

      @Override
      public Vec3 currentVelocity() {
         SubLevelPhysicsSystem sys = SubLevelPhysicsSystem.get(player.serverLevel());
         if (sys == null) return Vec3.ZERO;
         var handle = sys.getPhysicsHandle(subLevel);
         if (handle == null || !handle.isValid()) return Vec3.ZERO;
         var vel = handle.getLinearVelocity(new org.joml.Vector3d());
         return new Vec3(vel.x / 20.0, vel.y / 20.0, vel.z / 20.0);
      }

      @Override
      public long elapsedTicks() {
         if (startGameTime < 0) return -1;
         return player.serverLevel().getGameTime() - startGameTime;
      }

      @Override
      public boolean isDismountLocked() {
         return !RagdollSessionManager.canManualDismount(player.serverLevel(), subLevel);
      }

      @Override
      public void setDismountLocked(boolean locked) {
         RagdollSessionManager.setDismountLocked(subLevel, locked);
      }

      @Override
      public void applyWailing(RagdollWailingOptions options) {
         RagdollWailingOptions resolved = options == null ? RagdollWailingOptions.defaults() : options;
         RagdollMotorEffects.applyWailing(player.serverLevel(), subLevel, resolved.stiffness(), resolved.durationTicks(), resolved.intervalTicks(), resolved.startDelayTicks());
      }

      @Override
      public void stopWailing() {
         RagdollMotorEffects.stopWailing(subLevel);
      }

      @Override
      public void release() {
         ServerLevel level = player.serverLevel();
         SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
         if (physicsSystem != null && !subLevel.isRemoved()) {
            RagdollExpireHelper.expireImmediate(physicsSystem, level, subLevel, "api release");
         }
      }
   }

   private record ActivePlayerlessRagdollSession(ServerLevel level, ServerSubLevel subLevel, long startGameTime) implements PlayerlessRagdollSession {

      @Override
      public UUID id() {
         return subLevel.getUniqueId();
      }

      @Override
      public Vec3 currentVelocity() {
         SubLevelPhysicsSystem sys = SubLevelPhysicsSystem.get(level);
         if (sys == null) return Vec3.ZERO;
         var handle = sys.getPhysicsHandle(subLevel);
         if (handle == null || !handle.isValid()) return Vec3.ZERO;
         var vel = handle.getLinearVelocity(new org.joml.Vector3d());
         return new Vec3(vel.x / 20.0, vel.y / 20.0, vel.z / 20.0);
      }

      @Override
      public long elapsedTicks() {
         return level.getGameTime() - startGameTime;
      }

      @Override
      public void applyWailing(RagdollWailingOptions options) {
         RagdollWailingOptions resolved = options == null ? RagdollWailingOptions.defaults() : options;
         RagdollMotorEffects.applyWailing(level, subLevel, resolved.stiffness(), resolved.durationTicks(), resolved.intervalTicks(), resolved.startDelayTicks());
      }

      @Override
      public void stopWailing() {
         RagdollMotorEffects.stopWailing(subLevel);
      }

      @Override
      public void release() {
         SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
         if (physicsSystem != null && !subLevel.isRemoved()) {
            RagdollExpireHelper.expireImmediate(physicsSystem, level, subLevel, "api playerless release");
         }
      }
   }
}
