package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.api.DespawnCondition;
import dev.leo.sableplayerragdoll.api.RagdollSession;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class RagdollSessionManager {
   public static final String RAGDOLL_USER_TAG = "sable_player_ragdoll";
   private static final String START_TICK_KEY = "startTick";
   private static final String PLAYER_ID_KEY = "playerId";
   private static final String NON_PLAYER_KEY = "nonPlayer";
   private static final String EXPIRING_KEY = "expiring";
   private static final String DESPAWN_MODE_KEY = "despawnMode";
   private static final String DESPAWN_TICKS_KEY = "despawnTicks";
   private static final String DESPAWN_SPEED_KEY = "despawnSpeed";
   private static final int MIN_TICKS_BEFORE_SPEED_RELEASE = 8;
   private static final int NON_PLAYER_DURATION_SCALE = 3;
   private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
   private static final ConcurrentHashMap<UUID, List<DespawnCondition>> CUSTOM_DESPAWN_CONDITIONS = new ConcurrentHashMap<>();

   private RagdollSessionManager() {
   }

   public static boolean isMarkedRagdoll(ServerSubLevel subLevel) {
      CompoundTag tag = subLevel.getUserDataTag();
      return tag != null && tag.getBoolean(RAGDOLL_USER_TAG);
   }

   public static void registerRagdoll(ServerSubLevel subLevel, long startTick, @Nullable UUID playerId, boolean nonPlayer) {
      CompoundTag tag = subLevel.getUserDataTag();
      if (tag == null) {
         tag = new CompoundTag();
      }

      tag.putBoolean(RAGDOLL_USER_TAG, true);
      tag.putLong(START_TICK_KEY, startTick);
      if (playerId != null) {
         tag.putUUID(PLAYER_ID_KEY, playerId);
      }

      tag.putBoolean(NON_PLAYER_KEY, nonPlayer);
      tag.remove(EXPIRING_KEY);
      subLevel.setUserDataTag(tag);
      ACTIVE.add(subLevel.getUniqueId());
   }

   public static void unregister(ServerSubLevel subLevel) {
      ACTIVE.remove(subLevel.getUniqueId());
      CUSTOM_DESPAWN_CONDITIONS.remove(subLevel.getUniqueId());
   }

   public static void setCustomDespawnConditions(ServerSubLevel subLevel, List<DespawnCondition> conditions) {
      if (conditions.isEmpty()) {
         CUSTOM_DESPAWN_CONDITIONS.remove(subLevel.getUniqueId());
      } else {
         CUSTOM_DESPAWN_CONDITIONS.put(subLevel.getUniqueId(), List.copyOf(conditions));
      }
   }

   public static void setPlayerlessDespawnRule(ServerSubLevel subLevel, PlayerlessDespawnRule rule) {
      CompoundTag tag = subLevel.getUserDataTag();
      if (tag == null) {
         tag = new CompoundTag();
      }

      tag.putString(DESPAWN_MODE_KEY, rule.mode().name());
      tag.putInt(DESPAWN_TICKS_KEY, rule.ticks());
      tag.putDouble(DESPAWN_SPEED_KEY, rule.speedMetersPerSecond());
      subLevel.setUserDataTag(tag);
   }

   public static @Nullable ServerSubLevel activeRagdollForPlayer(ServerLevel level, UUID playerId) {
      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (!(container instanceof ServerSubLevelContainer serverContainer)) {
         return null;
      }

      for (UUID id : new ArrayList<>(ACTIVE)) {
         SubLevel subLevel = serverContainer.getSubLevel(id);
         if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved() && playerId.equals(getPlayerId(serverSubLevel))) {
            return serverSubLevel;
         }
      }

      return null;
   }

   public static boolean canManualDismount(ServerLevel level, ServerSubLevel subLevel) {
      CompoundTag tag = subLevel.getUserDataTag();
      return tag != null && level.getGameTime() - tag.getLong(START_TICK_KEY) >= (long) RagdollSettings.ragdollDurationTicks();
   }

   public static void tickActiveRagdolls(ServerLevel level) {
      if (!ACTIVE.isEmpty()) {
         SubLevelContainer container = SubLevelContainer.getContainer(level);
         if (container instanceof ServerSubLevelContainer serverContainer) {
            SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
            if (physicsSystem != null) {
               for (UUID id : new ArrayList<>(ACTIVE)) {
                  SubLevel subLevel = serverContainer.getSubLevel(id);
                  if (subLevel instanceof ServerSubLevel serverSubLevel) {
                     if (serverSubLevel.isRemoved() || !isMarkedRagdoll(serverSubLevel)) {
                        ACTIVE.remove(id);
                     } else if (shouldExpire(level, physicsSystem, serverSubLevel)) {
                        RagdollExpireHelper.expire(physicsSystem, level, serverSubLevel, reasonFor(serverSubLevel, level, physicsSystem));
                     }
                  }
               }
            }
         }
      }
   }

   static boolean shouldExpire(ServerLevel level, SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      if (isExpiring(subLevel)) {
         return false;
      }
      CompoundTag tag = subLevel.getUserDataTag();
      if (tag == null) {
         return false;
      }
      long elapsed = level.getGameTime() - tag.getLong(START_TICK_KEY);
      List<DespawnCondition> customConditions = CUSTOM_DESPAWN_CONDITIONS.get(subLevel.getUniqueId());
      if (customConditions != null) {
         return customConditionsShouldExpire(level, physicsSystem, subLevel, elapsed, customConditions);
      }
      Boolean customExpired = customPlayerlessExpiration(tag, elapsed, physicsSystem, subLevel);
      if (customExpired != null) {
         return customExpired;
      }
      if (elapsed >= (long) scaledRagdollDurationTicks(tag)) {
         return true;
      }
      if (elapsed >= (long) scaledSafetyLifetimeTicks(tag)) {
         return true;
      }
      return elapsed >= (long) scaledMinTicksBeforeSpeedRelease(tag)
            && sampleSpeedMetersPerSecond(physicsSystem, subLevel) <= RagdollSettings.releaseSpeedThreshold();
   }

   private static boolean customConditionsShouldExpire(
      ServerLevel level,
      SubLevelPhysicsSystem physicsSystem,
      ServerSubLevel subLevel,
      long elapsed,
      List<DespawnCondition> conditions
   ) {
      UUID playerId = getPlayerId(subLevel);
      ServerPlayer player = playerId == null ? null : level.getServer().getPlayerList().getPlayer(playerId);
      if (player == null) {
         return true;
      }

      RagdollSession session = new ManagedRagdollSession(player, subLevel, elapsed, physicsSystem);
      for (DespawnCondition condition : conditions) {
         if (condition.shouldDespawn(session)) {
            return true;
         }
      }
      return false;
   }

   private static @Nullable Boolean customPlayerlessExpiration(CompoundTag tag, long elapsed, SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      if (!tag.getBoolean(NON_PLAYER_KEY) || !tag.contains(DESPAWN_MODE_KEY)) {
         return null;
      }

      PlayerlessDespawnRule.Mode mode = despawnMode(tag.getString(DESPAWN_MODE_KEY));
      return switch (mode) {
         case DEFAULT -> null;
         case NEVER -> false;
         case AFTER_TICKS -> elapsed >= (long) tag.getInt(DESPAWN_TICKS_KEY);
         case BELOW_SPEED -> sampleSpeedMetersPerSecond(physicsSystem, subLevel) <= tag.getDouble(DESPAWN_SPEED_KEY);
      };
   }

   private static PlayerlessDespawnRule.Mode despawnMode(String modeName) {
      try {
         return PlayerlessDespawnRule.Mode.valueOf(modeName);
      } catch (IllegalArgumentException ignored) {
         return PlayerlessDespawnRule.Mode.DEFAULT;
      }
   }

   private static String reasonFor(ServerSubLevel subLevel, ServerLevel level, SubLevelPhysicsSystem physicsSystem) {
      CompoundTag tag = subLevel.getUserDataTag();
      if (tag == null) {
         return "expired";
      }
      long elapsed = level.getGameTime() - tag.getLong(START_TICK_KEY);
      if (elapsed >= (long) scaledSafetyLifetimeTicks(tag)) {
         return "safety timeout";
      }
      return elapsed >= (long) scaledRagdollDurationTicks(tag) ? "ride duration" : "slowed down";
   }

   private static int scaledRagdollDurationTicks(CompoundTag tag) {
      return RagdollSettings.ragdollDurationTicks() * durationScale(tag);
   }

   private static int scaledSafetyLifetimeTicks(CompoundTag tag) {
      return RagdollSettings.step1BodyLifetimeTicks() * durationScale(tag);
   }

   private static int scaledMinTicksBeforeSpeedRelease(CompoundTag tag) {
      return MIN_TICKS_BEFORE_SPEED_RELEASE * durationScale(tag);
   }

   private static int durationScale(CompoundTag tag) {
      return tag.getBoolean(NON_PLAYER_KEY) ? NON_PLAYER_DURATION_SCALE : 1;
   }

   static void markExpiring(ServerSubLevel subLevel) {
      CompoundTag tag = subLevel.getUserDataTag();
      if (tag == null) {
         tag = new CompoundTag();
      }
      tag.putBoolean(EXPIRING_KEY, true);
      subLevel.setUserDataTag(tag);
   }

   public static boolean isExpiring(ServerSubLevel subLevel) {
      CompoundTag tag = subLevel.getUserDataTag();
      return tag != null && tag.getBoolean(EXPIRING_KEY);
   }

   @Nullable
   static UUID getPlayerId(ServerSubLevel subLevel) {
      CompoundTag tag = subLevel.getUserDataTag();
      return tag != null && tag.hasUUID(PLAYER_ID_KEY) ? tag.getUUID(PLAYER_ID_KEY) : null;
   }

   private static double sampleSpeedMetersPerSecond(SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
      return handle != null && handle.isValid() ? handle.getLinearVelocity().length() : 0.0;
   }

   private record ManagedRagdollSession(ServerPlayer player, ServerSubLevel subLevel, long elapsedTicks, SubLevelPhysicsSystem physicsSystem) implements RagdollSession {

      @Override
      public Vec3 currentVelocity() {
         RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
         if (handle == null || !handle.isValid()) {
            return Vec3.ZERO;
         }

         org.joml.Vector3d velocity = handle.getLinearVelocity(new org.joml.Vector3d());
         return new Vec3(velocity.x / 20.0, velocity.y / 20.0, velocity.z / 20.0);
      }

      @Override
      public void release() {
         if (!subLevel.isRemoved()) {
            RagdollExpireHelper.expireImmediate(physicsSystem, player.serverLevel(), subLevel, "api custom release");
         }
      }
   }
}
