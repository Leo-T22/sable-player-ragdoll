package dev.leo.sableplayerragdoll.api;

import com.mojang.authlib.GameProfile;
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
      ServerLevel level = player.serverLevel();
      Vector3d linear = new Vector3d(linearVelocityMetersPerSecond.x, linearVelocityMetersPerSecond.y, linearVelocityMetersPerSecond.z);
      Vector3d angular = new Vector3d();
      RagdollLaunchOptions resolvedOptions = options == null ? RagdollLaunchOptions.defaults() : options;
      ServerSubLevel body = RagdollRegistry.launch(level, player, linear, angular, player.isFallFlying(), resolvedOptions.autoSeat(), resolvedOptions.limbs());
      if (body == null) return null;
      RagdollSessionManager.setCustomDespawnConditions(body, resolvedOptions.despawnConditions());
      if (resolvedOptions.lockDismount()) RagdollSessionManager.setDismountLocked(body, true);
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

   @Nullable
   public static UUID torsoSubLevelId(UUID headId) {
      return RagdollAssemblyHelper.linkedTorso(headId);
   }

   public static void copyEquipmentFrom(ServerLevel level, UUID headId, Player player) {
      applyEquipmentSnapshot(level, headId, captureEquipment(player, RagdollEquipmentScope.ALL));
   }

   public static void copyExtraEquipmentFrom(ServerLevel level, UUID headId, Player player) {
      applyEquipmentSnapshot(level, headId, captureEquipment(player, RagdollEquipmentScope.OPTIONAL_MODS));
   }

   public static RagdollEquipmentSnapshot captureEquipment(Player player, RagdollEquipmentScope scope) {
      return RagdollRegistry.captureEquipment(player, scope);
   }

   public static void applyEquipmentSnapshot(ServerLevel level, UUID headId, RagdollEquipmentSnapshot snapshot) {
      RagdollRegistry.applyEquipmentSnapshot(level, headId, snapshot);
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
