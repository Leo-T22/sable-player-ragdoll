package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.api.DespawnCondition;
import dev.leo.sableplayerragdoll.api.RagdollSession;
import dev.leo.sableplayerragdoll.RagdollSoundEvents;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class RagdollSessionManager {
   public static final ThreadLocal<Boolean> RAGDOLL_PIPE_ACTIVE = ThreadLocal.withInitial(() -> false);

   public static final String RAGDOLL_USER_TAG = "sable_player_ragdoll";
   private static final String START_TICK_KEY = "startTick";
   private static final String PLAYER_ID_KEY = "playerId";
   private static final String NON_PLAYER_KEY = "nonPlayer";
   private static final String EXPIRING_KEY = "expiring";
   private static final String END_REASON_KEY = "endReason";
   private static final String DESPAWN_MODE_KEY = "despawnMode";
   private static final String DESPAWN_TICKS_KEY = "despawnTicks";
   private static final String DESPAWN_SPEED_KEY = "despawnSpeed";
   private static final String GRAB_DISABLED_KEY = "grabDisabled";
   private static final int MIN_TICKS_BEFORE_SPEED_RELEASE = 8;
   private static final int NON_PLAYER_DURATION_SCALE = 3;
   private static final ConcurrentHashMap<UUID, List<DespawnCondition>> CUSTOM_DESPAWN_CONDITIONS = new ConcurrentHashMap<>();
   private static final Set<UUID> DISMOUNT_LOCKED = ConcurrentHashMap.newKeySet();
   private static final ConcurrentHashMap<UUID, Vector3d> LAST_VELOCITIES = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<UUID, Long> NEXT_IMPACT_DAMAGE_TICKS = new ConcurrentHashMap<>();

   private RagdollSessionManager() {
   }

   public static boolean isMarkedRagdoll(ServerSubLevel subLevel) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      return tag != null && tag.getBoolean(RAGDOLL_USER_TAG);
   }

   public static void registerRagdoll(ServerSubLevel subLevel, long startTick, @Nullable UUID playerId, boolean nonPlayer) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) {
         tag = new CompoundTag();
      }

      tag.putBoolean(RAGDOLL_USER_TAG, true);
      tag.putLong(START_TICK_KEY, startTick);
      if (playerId != null) {
         tag.putUUID(PLAYER_ID_KEY, playerId);
      } else {
         tag.remove(PLAYER_ID_KEY);
      }

      tag.putBoolean(NON_PLAYER_KEY, nonPlayer);
      tag.remove(EXPIRING_KEY);
      RagdollBlockOwnership.session(subLevel, tag);
   }

   public static void resetState() {
      CUSTOM_DESPAWN_CONDITIONS.clear();
      DISMOUNT_LOCKED.clear();
      LAST_VELOCITIES.clear();
      NEXT_IMPACT_DAMAGE_TICKS.clear();
      RAGDOLL_PIPE_ACTIVE.remove();
   }

   public static void unregister(ServerSubLevel subLevel) {
      CUSTOM_DESPAWN_CONDITIONS.remove(RagdollBlockOwnership.sessionId(subLevel));
      DISMOUNT_LOCKED.remove(RagdollBlockOwnership.sessionId(subLevel));
      LAST_VELOCITIES.remove(RagdollBlockOwnership.sessionId(subLevel));
      NEXT_IMPACT_DAMAGE_TICKS.remove(RagdollBlockOwnership.sessionId(subLevel));
      RagdollMotorEffects.clear(RagdollBlockOwnership.sessionId(subLevel));
      RagdollAccessoriesLiveSync.clear(RagdollBlockOwnership.sessionId(subLevel));
   }

   public static void setCustomDespawnConditions(ServerSubLevel subLevel, List<DespawnCondition> conditions) {
      if (conditions.isEmpty()) {
         CUSTOM_DESPAWN_CONDITIONS.remove(RagdollBlockOwnership.sessionId(subLevel));
      } else {
         CUSTOM_DESPAWN_CONDITIONS.put(RagdollBlockOwnership.sessionId(subLevel), List.copyOf(conditions));
      }
   }

   static void detachPlayer(ServerSubLevel subLevel, PlayerlessDespawnRule rule, long currentGameTime) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) return;
      tag.remove(PLAYER_ID_KEY);
      tag.putBoolean(NON_PLAYER_KEY, true);
      tag.putLong(START_TICK_KEY, currentGameTime);
      tag.remove(EXPIRING_KEY);
      RagdollBlockOwnership.session(subLevel, tag);
      setPlayerlessDespawnRule(subLevel, rule);
      CUSTOM_DESPAWN_CONDITIONS.remove(RagdollBlockOwnership.sessionId(subLevel));
      DISMOUNT_LOCKED.remove(RagdollBlockOwnership.sessionId(subLevel));
   }

   public static void setPlayerlessDespawnRule(ServerSubLevel subLevel, PlayerlessDespawnRule rule) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) {
         tag = new CompoundTag();
      }

      tag.putString(DESPAWN_MODE_KEY, rule.mode().name());
      tag.putInt(DESPAWN_TICKS_KEY, rule.ticks());
      tag.putDouble(DESPAWN_SPEED_KEY, rule.speedMetersPerSecond());
      RagdollBlockOwnership.session(subLevel, tag);
   }

   private static ServerSubLevel sourceRoot(net.minecraft.world.entity.Entity vehicle, ServerLevel level, UUID owner) {
      return vehicle instanceof dev.leo.sableplayerragdoll.entity.RagdollSeatEntity seat
            ? seat.ragdollRoot(level, owner) : RagdollBlockOwnership.root(level, owner);
   }

   public static @Nullable ServerSubLevel activeRagdollForPlayer(ServerLevel level, UUID playerId) {
      var entity = level.getEntity(playerId);
      if (entity == null || entity.getVehicle() == null) return null;
      var tag = entity.getVehicle().getPersistentData();
      if (!tag.hasUUID(RagdollBlockLifetime.SOURCE_SESSION)) return null;
      UUID owner = tag.getUUID(RagdollBlockLifetime.SOURCE_SESSION);
      var root = sourceRoot(entity.getVehicle(), level, owner);
      if (root == null) return null;
      for (var be : RagdollBlockOwnership.blocks(root)) {
         var identity = ((RagdollOwnedBlock) be).ragdollIdentity();
         if (owner.equals(identity.owner()) && playerId.equals(identity.source())) return root;
      }
      return null;
   }

   static void setGrabDisabled(ServerSubLevel subLevel, boolean disabled) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) {
         if (!disabled) return;
         tag = new CompoundTag();
      }
      if (disabled) {
         tag.putBoolean(GRAB_DISABLED_KEY, true);
      } else {
         tag.remove(GRAB_DISABLED_KEY);
      }
      RagdollBlockOwnership.session(subLevel, tag);
   }

   static boolean isGrabDisabled(ServerSubLevel subLevel) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      return tag != null && tag.getBoolean(GRAB_DISABLED_KEY);
   }

   public static void setDismountLocked(ServerSubLevel subLevel, boolean locked) {
      if (locked) {
         DISMOUNT_LOCKED.add(RagdollBlockOwnership.sessionId(subLevel));
      } else {
         DISMOUNT_LOCKED.remove(RagdollBlockOwnership.sessionId(subLevel));
      }
   }

   public static boolean canManualDismount(ServerLevel level, ServerSubLevel subLevel) {
      if (DISMOUNT_LOCKED.contains(RagdollBlockOwnership.sessionId(subLevel))) return false;
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) return false;
      long elapsed = level.getGameTime() - tag.getLong(START_TICK_KEY);
      if (elapsed < (long) RagdollSettings.minDismountTicks()) return false;
      return !RagdollSettings.expireAfterDuration()
            || elapsed >= (long) scaledRagdollDurationTicks(tag);
   }

   public static void tickRoot(ServerLevel level, ServerSubLevel root) {
      var physics = SubLevelPhysicsSystem.get(level);
      var container = SubLevelContainer.getContainer(level);
      if (physics == null || !(container instanceof ServerSubLevelContainer serverContainer)
            || root.isRemoved() || !isMarkedRagdoll(root) || isExpiring(root)) return;
      if (shouldExpire(level, physics, root)) {
         RagdollExpireHelper.expire(level, root, reasonFor(root, level, physics));
      } else {
         RagdollMotorEffects.tick(level, root);
         applyImpactDamage(level, physics, serverContainer, root);
         pollAccessories(level, root);
      }
   }

   private static void pollAccessories(ServerLevel level, ServerSubLevel head) {
      UUID playerId = getPlayerId(head);
      if (playerId == null) return;
      ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
      if (player == null) return;
      RagdollAccessoriesLiveSync.poll(level, head.getUniqueId(), player);
   }

   static boolean shouldExpire(ServerLevel level, SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      if (isExpiring(subLevel)) {
         return false;
      }
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) {
         return false;
      }
      long elapsed = level.getGameTime() - tag.getLong(START_TICK_KEY);
      List<DespawnCondition> customConditions = CUSTOM_DESPAWN_CONDITIONS.get(RagdollBlockOwnership.sessionId(subLevel));
      if (customConditions != null) {
         return customConditionsShouldExpire(level, physicsSystem, subLevel, elapsed, customConditions);
      }
      Boolean customExpired = customPlayerlessExpiration(tag, elapsed, physicsSystem, subLevel);
      if (customExpired != null) {
         return customExpired;
      }
      if (RagdollSettings.expireAfterDuration() && elapsed >= (long) scaledRagdollDurationTicks(tag)) {
         return true;
      }
      if (RagdollSettings.expireAfterSafetyTimeout() && elapsed >= (long) scaledSafetyLifetimeTicks(tag)) {
         return true;
      }
      return RagdollSettings.expireWhenSlow()
            && elapsed >= (long) scaledMinTicksBeforeSpeedRelease(tag)
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
      if (!tag.getBoolean(NON_PLAYER_KEY)) return null;
      if (!tag.contains(DESPAWN_MODE_KEY)) return false;

      PlayerlessDespawnRule.Mode mode = despawnMode(tag.getString(DESPAWN_MODE_KEY));
      return switch (mode) {
         case DEFAULT, NEVER -> false;
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
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) {
         return "expired";
      }
      long elapsed = level.getGameTime() - tag.getLong(START_TICK_KEY);
      if (RagdollSettings.expireAfterSafetyTimeout() && elapsed >= (long) scaledSafetyLifetimeTicks(tag)) {
         return "safety timeout";
      }
      return RagdollSettings.expireAfterDuration() && elapsed >= (long) scaledRagdollDurationTicks(tag) ? "ride duration" : "slowed down";
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
      markExpiring(subLevel, "expired");
   }

   static void markExpiring(ServerSubLevel subLevel, String reason) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      if (tag == null) {
         tag = new CompoundTag();
      }
      tag.putBoolean(EXPIRING_KEY, true);
      tag.putString(END_REASON_KEY, reason);
      RagdollBlockOwnership.session(subLevel, tag);
   }

   public static boolean isExpiring(ServerSubLevel subLevel) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      return tag != null && tag.getBoolean(EXPIRING_KEY);
   }

   static @Nullable String getEndReason(ServerSubLevel subLevel) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      return tag != null && tag.contains(END_REASON_KEY) ? tag.getString(END_REASON_KEY) : null;
   }

   @Nullable
   public static UUID getPlayerId(ServerSubLevel subLevel) {
      CompoundTag tag = RagdollBlockOwnership.session(subLevel);
      return tag != null && tag.hasUUID(PLAYER_ID_KEY) ? tag.getUUID(PLAYER_ID_KEY) : null;
   }

   public static boolean isPlayerCurrentlyRagdolled(ServerPlayer player) {
      ServerSubLevel ragdoll = activeRagdollForPlayer(player.serverLevel(), player.getUUID());
      return ragdoll != null;
   }

   private static double sampleSpeedMetersPerSecond(SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
      return handle != null && handle.isValid() ? handle.getLinearVelocity().length() : 0.0;
   }

   private static @Nullable Vector3d sampleVelocityMetersPerSecond(SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      RigidBodyHandle handle = physicsSystem.getPhysicsHandle(subLevel);
      return handle != null && handle.isValid() ? handle.getLinearVelocity(new Vector3d()) : null;
   }

   private static void applyImpactDamage(ServerLevel level, SubLevelPhysicsSystem physicsSystem, ServerSubLevelContainer serverContainer, ServerSubLevel rootSubLevel) {
      if (!RagdollSettings.impactDamageEnabled()) return;
      UUID playerId = getPlayerId(rootSubLevel);
      if (playerId == null) return;
      ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
      if (player == null || player.isDeadOrDying() || player.isSpectator()) return;

      ImpactSample impact = sampleLargestLinkedVelocityDelta(level, physicsSystem, serverContainer, rootSubLevel);
      double delta = impact.delta();
      double feedbackThreshold = Math.min(RagdollSettings.impactFeedbackThreshold(), RagdollSettings.impactDamageThreshold());
      if (delta <= feedbackThreshold) return;

      long gameTime = level.getGameTime();
      UUID rootSubLevelId = RagdollBlockOwnership.sessionId(rootSubLevel);
      if (gameTime < NEXT_IMPACT_DAMAGE_TICKS.getOrDefault(rootSubLevelId, Long.MIN_VALUE)) return;

      NEXT_IMPACT_DAMAGE_TICKS.put(rootSubLevelId, gameTime + (long) RagdollSettings.impactDamageCooldownTicks());
      Vec3 position = impact.position();
      spawnImpactParticles(level, position, delta, feedbackThreshold);
      double damageThreshold = RagdollSettings.impactDamageThreshold();
      boolean damageImpact = delta > damageThreshold;
      net.minecraft.sounds.SoundEvent soundEvent = damageImpact
         ? RagdollSoundEvents.ragdollImpact()
         : RagdollSoundEvents.ragdollSmallImpact();
      if (soundEvent != null) {
         float volume = damageImpact ? 0.25F : 1.7F;
         float pitch = 0.9F + level.random.nextFloat() * 0.2F;
         level.playSound(null, position.x, position.y, position.z, soundEvent, SoundSource.PLAYERS, volume, pitch);
      }

      if (damageImpact) {
         float damage = (float) Math.min(RagdollSettings.impactDamageMax(), (delta - damageThreshold) * RagdollSettings.impactDamageMultiplier());
         if (damage > 0.0F) {
            player.hurt(player.damageSources().flyIntoWall(), damage);
         }
      }
   }

   private static void spawnImpactParticles(ServerLevel level, Vec3 position, double delta, double threshold) {
      double maxDelta = threshold + RagdollSettings.impactDamageMax() / Math.max(0.001, RagdollSettings.impactDamageMultiplier());
      double strength = Math.clamp((delta - threshold) / Math.max(0.001, maxDelta - threshold), 0.0, 1.0);
      int count = 4 + (int) Math.round(strength * 10.0);
      double spread = 0.36 + strength * 0.24;
      double speed = 0.06;
      level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, position.x, position.y, position.z, count, spread, spread * 0.5, spread, speed);
   }

   private static ImpactSample sampleLargestLinkedVelocityDelta(ServerLevel level, SubLevelPhysicsSystem physicsSystem, ServerSubLevelContainer serverContainer, ServerSubLevel rootSubLevel) {
      double largestDelta = 0.0;
      Vec3 impactPosition = worldImpactPosition(level, rootSubLevel);
      for (UUID partId : RagdollAssemblyHelper.linkedParts(level, RagdollBlockOwnership.sessionId(rootSubLevel))) {
         SubLevel part = serverContainer.getSubLevel(partId);
         if (!(part instanceof ServerSubLevel partSubLevel) || partSubLevel.isRemoved()) {
            LAST_VELOCITIES.remove(partId);
            continue;
         }

         Vector3d velocity = sampleVelocityMetersPerSecond(physicsSystem, partSubLevel);
         if (velocity == null) {
            continue;
         }

         Vector3d previous = LAST_VELOCITIES.put(partId, new Vector3d(velocity));
         if (previous != null) {
            double delta = previous.sub(velocity).length();
            if (delta > largestDelta) {
               largestDelta = delta;
               impactPosition = worldImpactPosition(level, partSubLevel);
            }
         }
      }
      return new ImpactSample(largestDelta, impactPosition);
   }

   private static Vec3 worldImpactPosition(ServerLevel level, ServerSubLevel subLevel) {
      if (subLevel.getPlot() == null) {
         org.joml.Vector3dc position = subLevel.logicalPose().position();
         return new Vec3(position.x(), position.y(), position.z());
      }

      return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(subLevel.getPlot().getCenterBlock()));
   }

   private record ImpactSample(double delta, Vec3 position) {
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
      public boolean isDismountLocked() {
         return !RagdollSessionManager.canManualDismount(player.serverLevel(), subLevel);
      }

      @Override
      public void setDismountLocked(boolean locked) {
         RagdollSessionManager.setDismountLocked(subLevel, locked);
      }

      @Override
      public void applyWailing(dev.leo.sableplayerragdoll.api.RagdollWailingOptions options) {
         dev.leo.sableplayerragdoll.api.RagdollWailingOptions resolved =
            options == null ? dev.leo.sableplayerragdoll.api.RagdollWailingOptions.defaults() : options;
         RagdollMotorEffects.applyWailing(player.serverLevel(), subLevel, resolved.stiffness(), resolved.durationTicks(), resolved.intervalTicks(), resolved.startDelayTicks());
      }

      @Override
      public void stopWailing() {
         RagdollMotorEffects.stopWailing(subLevel);
      }

      @Override
      public void release() {
         if (!subLevel.isRemoved()) {
            RagdollExpireHelper.expire(player.serverLevel(), subLevel, "api custom release");
         }
      }
   }
}
