package dev.leo.sableplayerragdoll.api;

import com.mojang.authlib.GameProfile;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.physics.RagdollExpireHelper;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
      return launch(player, linearVelocityMetersPerSecond, List.of());
   }

   @Nullable
   public static RagdollSession launch(ServerPlayer player, Vec3 linearVelocityMetersPerSecond, List<DespawnCondition> conditions) {
      ServerLevel level = player.serverLevel();
      Vector3d linear = new Vector3d(linearVelocityMetersPerSecond.x, linearVelocityMetersPerSecond.y, linearVelocityMetersPerSecond.z);
      Vector3d angular = new Vector3d();
      ServerSubLevel body = RagdollRegistry.launch(level, player, linear, angular, player.isFallFlying(), RagdollSettings.autoSeatOnTrigger());
      if (body == null) return null;
      RagdollSessionManager.setCustomDespawnConditions(body, conditions);
      return new ActiveRagdollSession(player, body, level.getGameTime(), conditions);
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
      Vec3 heading = Vec3.directionFromRotation(0.0F, (float) headingDegrees);
      Vector3d linear = new Vector3d(linearVelocityMetersPerSecond.x, linearVelocityMetersPerSecond.y, linearVelocityMetersPerSecond.z);
      ServerSubLevel body = RagdollRegistry.spawnPlayerless(level, position, heading, profile, linear, new Vector3d(), despawnRule);
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
      public void release() {
         SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
         if (physicsSystem != null && !subLevel.isRemoved()) {
            RagdollExpireHelper.expireImmediate(physicsSystem, level, subLevel, "api playerless release");
         }
      }
   }
}
