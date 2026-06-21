package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class RagdollControlHelper {
   private static final double CONTROL_TORQUE = 4.5;
   private static final double SWIM_FORWARD_ACCELERATION = 0.0;
   private static final double SWIM_UP_ACCELERATION = 0.0;
   private static final double MAX_CONTROL_ANGULAR_SPEED = 8.0;
   private static final double LEVITATION_ACCELERATION = 52.2;
   private static final double LEVITATION_FRONT_OFFSET = 0.35;
   private static final double MAX_LEVITATION_SPEED = 3.0;
   private static final double SLOW_FALLING_ACCELERATION = 34.0;
   private static final double MAX_SLOW_FALLING_DESCENT_SPEED = -1.7;
   private static final int INPUT_TIMEOUT_TICKS = 10;
   private static final double ARM_REACH_ACCELERATION = 18.0;
   private static final double MAX_ARM_REACH_ANGULAR_SPEED = 8.0;
   private static final double ARM_COUPLE_HALF_LENGTH = 0.3;
   private static final double HAND_LOCAL_DROP = 0.35;
   private static final double GRAB_STIFFNESS = 4000.0;
   private static final double GRAB_DAMPING = 150.0;
   private static final double GRAB_MAX_FORCE = 1200.0;
   private static final double GRAB_RADIUS = 0.15;
   private static final int GRAB_BREAK_TICKS = 4;
   private static final Map<UUID, ControlInput> INPUTS = new ConcurrentHashMap<>();
   private static final Map<UUID, ArmInput> ARM_INPUTS = new ConcurrentHashMap<>();
   // Active hand-grab constraints, keyed by arm sub-level id.
   private static final Map<UUID, ArmGrab> ARM_GRABS = new ConcurrentHashMap<>();
   private static final Map<UUID, Integer> ARM_GRAB_STRAIN = new ConcurrentHashMap<>();

   private RagdollControlHelper() {
   }

   public static void updateInput(ServerPlayer player, float strafe, float forward) {
      INPUTS.put(player.getUUID(), new ControlInput(clampAxis(strafe), clampAxis(forward), player.serverLevel().getGameTime()));
   }

   public static void updateArmInput(ServerPlayer player, boolean leftReach, boolean rightReach,
                                     boolean leftGrab, boolean rightGrab, float lookX, float lookY, float lookZ) {
      ARM_INPUTS.put(player.getUUID(),
         new ArmInput(leftReach, rightReach, leftGrab, rightGrab, new Vector3d(lookX, lookY, lookZ),
            player.serverLevel().getGameTime()));
   }

   public static void clearInput(UUID playerId) {
      INPUTS.remove(playerId);
      ARM_INPUTS.remove(playerId);
   }

   public static void applyArm(ServerSubLevel armSubLevel, RigidBodyHandle handle, double timeStep, BodyPart bodyPart) {
      if (bodyPart != BodyPart.LEFT_ARM && bodyPart != BodyPart.RIGHT_ARM) {
         return;
      }
      ServerLevel level = armSubLevel.getLevel();
      ServerPlayer player = controllingPlayer(level, armSubLevel);
      if (player == null) {
         return;
      }

      ArmInput input = ARM_INPUTS.get(player.getUUID());
      if (input == null || level.getGameTime() - input.gameTime() > (long) INPUT_TIMEOUT_TICKS) {
         return;
      }
      boolean active = bodyPart == BodyPart.LEFT_ARM ? input.leftReach() : input.rightReach();
      if (!active || input.look().lengthSquared() < 1.0E-6) {
         return;
      }
      if (handle.getAngularVelocity(new Vector3d()).length() > MAX_ARM_REACH_ANGULAR_SPEED) {
         return;
      }

      Vector3d worldImpulse = new Vector3d(input.look()).normalize().mul(ARM_REACH_ACCELERATION * timeStep);
      Vector3d localImpulse = armSubLevel.logicalPose().transformNormalInverse(worldImpulse, new Vector3d());

      BlockPos center = armSubLevel.getPlot().getCenterBlock();
      Vector3d handPoint = new Vector3d(center.getX() + 0.5, center.getY() + 0.5 - ARM_COUPLE_HALF_LENGTH, center.getZ() + 0.5);
      Vector3d shoulderPoint = new Vector3d(center.getX() + 0.5, center.getY() + 0.5 + ARM_COUPLE_HALF_LENGTH, center.getZ() + 0.5);

      ForceTotal forceTotal = new ForceTotal();
      forceTotal.applyImpulseAtPoint(armSubLevel, handPoint, localImpulse);
      forceTotal.applyImpulseAtPoint(armSubLevel, shoulderPoint, new Vector3d(localImpulse).negate());
      handle.applyForcesAndReset(forceTotal);
   }

   public static void applyArmGrab(ServerSubLevel armSubLevel, BodyPart bodyPart) {
      if (bodyPart != BodyPart.LEFT_ARM && bodyPart != BodyPart.RIGHT_ARM) {
         return;
      }
      UUID armId = armSubLevel.getUniqueId();
      ServerLevel level = armSubLevel.getLevel();
      ServerPlayer player = controllingPlayer(level, armSubLevel);
      ArmInput input = player == null ? null : ARM_INPUTS.get(player.getUUID());
      boolean active = input != null
         && level.getGameTime() - input.gameTime() <= (long) INPUT_TIMEOUT_TICKS
         && (bodyPart == BodyPart.LEFT_ARM ? input.leftGrab() : input.rightGrab());

      ArmGrab existing = ARM_GRABS.get(armId);
      if (!active) {
         if (existing != null) {
            existing.handle().remove();
            releaseGrab(armId);
         }
         return;
      }
      if (existing != null) {
         if (gripOverstrained(armSubLevel, existing, armId)) {
            existing.handle().remove();
            releaseGrab(armId);
         }
         return; // already holding on (or just slipped)
      }

      BlockPos center = armSubLevel.getPlot().getCenterBlock();
      Vec3 localHand = new Vec3(center.getX() + 0.5, center.getY() + 0.5 - HAND_LOCAL_DROP, center.getZ() + 0.5);
      Vec3 worldHand = armSubLevel.logicalPose().transformPosition(localHand);

      ServerSubLevel target = findGrabTarget(level, armSubLevel, worldHand);
      Object body1;
      Vector3d anchor1;
      Vec3 anchorPoint;
      if (target != null) {
         Vec3 targetLocal = target.logicalPose().transformPositionInverse(worldHand);
         body1 = target;
         anchor1 = new Vector3d(targetLocal.x, targetLocal.y, targetLocal.z);
         anchorPoint = targetLocal;
      } else if (handTouchingBlock(level, worldHand)) {
         body1 = null;
         anchor1 = new Vector3d(worldHand.x, worldHand.y, worldHand.z);
         anchorPoint = worldHand;
      } else {
         return;
      }

      ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
      if (container == null) {
         return;
      }
      SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
      Vector3d localAnchor = new Vector3d(localHand.x, localHand.y, localHand.z);
      PhysicsConstraintHandle handle = SableConstraintCompat.addConstraint(
         physicsSystem.getPipeline(), body1, armSubLevel,
         SableConstraintCompat.free(anchor1, localAnchor, new Quaterniond()));
      for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
         handle.setMotor(axis, 0.0, GRAB_STIFFNESS, GRAB_DAMPING, false, GRAB_MAX_FORCE);
      }
      ARM_GRABS.put(armId, new ArmGrab(handle, target, anchorPoint, localHand));
      ARM_GRAB_STRAIN.remove(armId);
   }

   private static boolean gripOverstrained(ServerSubLevel armSubLevel, ArmGrab grab, UUID armId) {
      if (!grab.handle().isValid() || (grab.target() != null && grab.target().isRemoved())) {
         ARM_GRAB_STRAIN.remove(armId);
         return true;
      }
      double breakDistance = RagdollSettings.grabBreakDistance();
      if (!RagdollSettings.grabBreakEnabled() || breakDistance <= 0.0) {
         ARM_GRAB_STRAIN.remove(armId);
         return false;
      }
      Vec3 handNow = armSubLevel.logicalPose().transformPosition(grab.armLocalHand());
      Vec3 anchorNow = grab.target() != null
         ? grab.target().logicalPose().transformPosition(grab.anchorPoint())
         : grab.anchorPoint();
      if (handNow.distanceTo(anchorNow) < breakDistance) {
         ARM_GRAB_STRAIN.remove(armId);
         return false;
      }
      int ticks = ARM_GRAB_STRAIN.merge(armId, 1, Integer::sum);
      return ticks >= GRAB_BREAK_TICKS;
   }

   private static void releaseGrab(UUID armId) {
      ARM_GRABS.remove(armId);
      ARM_GRAB_STRAIN.remove(armId);
   }

   private record ArmGrab(PhysicsConstraintHandle handle, @Nullable ServerSubLevel target,
                          Vec3 anchorPoint, Vec3 armLocalHand) {
   }

   /** Finds another sub-level touching the hand to grab, skipping the arm itself and its own ragdoll. */
   private static ServerSubLevel findGrabTarget(ServerLevel level, ServerSubLevel arm, Vec3 worldHand) {
      UUID armId = arm.getUniqueId();
      UUID armRoot = RagdollAssemblyHelper.linkedRoot(armId);
      BoundingBox3d bounds = new BoundingBox3d(
         AABB.ofSize(worldHand, GRAB_RADIUS * 2, GRAB_RADIUS * 2, GRAB_RADIUS * 2));
      for (SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, bounds)) {
         if (!(subLevel instanceof ServerSubLevel candidate) || candidate.isRemoved()) {
            continue;
         }
         if (candidate.getUniqueId().equals(armId)) {
            continue; // the grabbing arm itself
         }
         UUID candidateRoot = RagdollAssemblyHelper.linkedRoot(candidate.getUniqueId());
         if (armRoot != null && armRoot.equals(candidateRoot)) {
            continue; // another part of the same ragdoll
         }
         return candidate;
      }
      return null;
   }

   private static boolean handTouchingBlock(ServerLevel level, Vec3 handWorld) {
      return !level.noCollision(AABB.ofSize(handWorld, GRAB_RADIUS * 2, GRAB_RADIUS * 2, GRAB_RADIUS * 2));
   }

   public static void apply(ServerSubLevel torsoSubLevel, RigidBodyHandle handle, double timeStep) {
      ServerLevel level = torsoSubLevel.getLevel();
      ServerPlayer player = controllingPlayer(level, torsoSubLevel);
      if (player == null) {
         return;
      }

      applyLevitation(player, torsoSubLevel, handle, timeStep);
      applySlowFalling(player, torsoSubLevel, handle, timeStep);

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

   private static void applySlowFalling(ServerPlayer player, ServerSubLevel torsoSubLevel, RigidBodyHandle handle, double timeStep) {
      if (player.getEffect(MobEffects.SLOW_FALLING) == null || player.getEffect(MobEffects.LEVITATION) != null) {
         return;
      }

      double verticalSpeed = handle.getLinearVelocity(new Vector3d()).y();
      if (verticalSpeed >= MAX_SLOW_FALLING_DESCENT_SPEED) {
         return;
      }

      Vector3d worldImpulse = new Vector3d(0.0, SLOW_FALLING_ACCELERATION * timeStep, 0.0);
      Vector3d localImpulse = torsoSubLevel.logicalPose().transformNormalInverse(worldImpulse, new Vector3d());
      ForceTotal forceTotal = new ForceTotal();
      forceTotal.applyLinearAndAngularImpulse(localImpulse, new Vector3d());
      handle.applyForcesAndReset(forceTotal);
   }

   @Nullable
   private static ServerPlayer controllingPlayer(ServerLevel level, ServerSubLevel torsoSubLevel) {
      UUID rootId = RagdollAssemblyHelper.linkedRoot(torsoSubLevel.getUniqueId());
      if (rootId == null) {
         return null;
      }

      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (!(container instanceof ServerSubLevelContainer serverContainer)) {
         return null;
      }

      SubLevel subLevel = serverContainer.getSubLevel(rootId);
      if (!(subLevel instanceof ServerSubLevel rootSubLevel) || rootSubLevel.isRemoved()) {
         return null;
      }

      UUID playerId = RagdollSessionManager.getPlayerId(rootSubLevel);
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

   private static record ArmInput(boolean leftReach, boolean rightReach, boolean leftGrab, boolean rightGrab,
                                  Vector3d look, long gameTime) {
   }
}
