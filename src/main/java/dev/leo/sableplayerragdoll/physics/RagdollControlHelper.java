package dev.leo.sableplayerragdoll.physics;

import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class RagdollControlHelper {
   private static final double CONTROL_TORQUE = 9.0;
   private static final double SWIM_FORWARD_ACCELERATION = 0.0;
   private static final double SWIM_UP_ACCELERATION = 0.0;
   private static final double MAX_CONTROL_ANGULAR_SPEED = 8.0;
   private static final double LEVITATION_ACCELERATION = 52.2;
   private static final double LEVITATION_FRONT_OFFSET = 0.35;
   private static final double MAX_LEVITATION_SPEED = 3.0;
   private static final int INPUT_TIMEOUT_TICKS = 10;
   private static final Map<UUID, ControlInput> INPUTS = new ConcurrentHashMap<>();

   private RagdollControlHelper() {
   }

   public static void updateInput(ServerPlayer player, float strafe, float forward) {
      INPUTS.put(player.getUUID(), new ControlInput(clampAxis(strafe), clampAxis(forward), player.serverLevel().getGameTime()));
   }

   public static void clearInput(UUID playerId) {
      INPUTS.remove(playerId);
   }

   public static void apply(ServerSubLevel torsoSubLevel, RigidBodyHandle handle, double timeStep) {
      ServerLevel level = torsoSubLevel.getLevel();
      ServerPlayer player = controllingPlayer(level, torsoSubLevel);
      if (player == null) {
         return;
      }

      applyLevitation(player, torsoSubLevel, handle, timeStep);

      ControlInput input = INPUTS.get(player.getUUID());
      if (input == null || level.getGameTime() - input.gameTime() > (long)INPUT_TIMEOUT_TICKS) {
         return;
      }

      double inputStrength = Math.max(Math.abs(input.strafe()), Math.abs(input.forward()));
      if (inputStrength < 1.0E-3 || handle.getAngularVelocity(new Vector3d()).length() > MAX_CONTROL_ANGULAR_SPEED) {
         return;
      }

      Vector3d localTorque = new Vector3d(
         input.forward() * CONTROL_TORQUE * timeStep,
         -input.strafe() * CONTROL_TORQUE * timeStep,
         0.0
      );
      Vector3d localImpulse = new Vector3d(
         0.0,
         Math.max(0.0F, input.forward()) * SWIM_UP_ACCELERATION * timeStep,
         -input.forward() * SWIM_FORWARD_ACCELERATION * timeStep
      );
      ForceTotal forceTotal = new ForceTotal();
      forceTotal.applyLinearAndAngularImpulse(localImpulse, localTorque);
      handle.applyForcesAndReset(forceTotal);
   }

   private static void applyLevitation(ServerPlayer player, ServerSubLevel torsoSubLevel, RigidBodyHandle handle, double timeStep) {
      MobEffectInstance levitation = player.getEffect(MobEffects.LEVITATION);
      if (levitation == null || handle.getLinearVelocity(new Vector3d()).y() >= MAX_LEVITATION_SPEED) {
         return;
      }

      double amplifierScale = Math.max(1, levitation.getAmplifier() + 1);
      Vector3d worldImpulse = new Vector3d(0.0, LEVITATION_ACCELERATION * amplifierScale * timeStep, 0.0);
      Vector3d localImpulse = torsoSubLevel.logicalPose().transformNormalInverse(worldImpulse, new Vector3d());
      Vector3d localLiftPoint = new Vector3d(
         torsoSubLevel.getPlot().getCenterBlock().getX() + 0.5,
         torsoSubLevel.getPlot().getCenterBlock().getY() + 0.5,
         torsoSubLevel.getPlot().getCenterBlock().getZ() + 0.5 + LEVITATION_FRONT_OFFSET
      );
      ForceTotal forceTotal = new ForceTotal();
      forceTotal.applyImpulseAtPoint(torsoSubLevel, localLiftPoint, localImpulse);
      handle.applyForcesAndReset(forceTotal);
   }

   @Nullable
   private static ServerPlayer controllingPlayer(ServerLevel level, ServerSubLevel torsoSubLevel) {
      UUID headId = RagdollAssemblyHelper.linkedHead(torsoSubLevel.getUniqueId());
      if (headId == null) {
         return null;
      }

      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (!(container instanceof ServerSubLevelContainer serverContainer)) {
         return null;
      }

      SubLevel subLevel = serverContainer.getSubLevel(headId);
      if (!(subLevel instanceof ServerSubLevel headSubLevel) || headSubLevel.isRemoved()) {
         return null;
      }

      UUID playerId = RagdollSessionManager.getPlayerId(headSubLevel);
      return playerId == null || !(level.getEntity(playerId) instanceof ServerPlayer player) ? null : player;
   }

   private static float clampAxis(float value) {
      if (value > 1.0F) {
         return 1.0F;
      }

      return value < -1.0F ? -1.0F : value;
   }

   private static record ControlInput(float strafe, float forward, long gameTime) {
   }
}
