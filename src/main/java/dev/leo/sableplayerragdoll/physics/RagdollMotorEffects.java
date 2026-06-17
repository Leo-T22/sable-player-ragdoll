package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class RagdollMotorEffects {
   private static final String WAILING_KEY = "Wailing";
   private static final String START_TICK_KEY = "StartTick";
   private static final String END_TICK_KEY = "EndTick";
   private static final String STIFFNESS_KEY = "Stiffness";
   private static final String INTERVAL_TICKS_KEY = "IntervalTicks";
   private static final String SEED_KEY = "Seed";
   private static final double WAILING_DAMPING = 8.0;
   private static final Map<UUID, RuntimeWailing> RUNTIME_WAILING = new ConcurrentHashMap<>();

   private RagdollMotorEffects() {
   }

   public static void applyWailing(ServerLevel level, ServerSubLevel rootSubLevel, double stiffness, int durationTicks, int intervalTicks, int startDelayTicks) {
      long startTick = level.getGameTime() + Math.max(0, startDelayTicks);
      long endTick = startTick + Math.max(1, durationTicks);
      long seed = level.random.nextLong();
      CompoundTag tag = writableUserData(rootSubLevel);
      CompoundTag wailing = new CompoundTag();
      wailing.putLong(START_TICK_KEY, startTick);
      wailing.putLong(END_TICK_KEY, endTick);
      wailing.putDouble(STIFFNESS_KEY, Math.max(0.0, stiffness));
      wailing.putInt(INTERVAL_TICKS_KEY, Math.max(1, intervalTicks));
      wailing.putLong(SEED_KEY, seed);
      tag.put(WAILING_KEY, wailing);
      rootSubLevel.setUserDataTag(tag);
      RUNTIME_WAILING.put(rootSubLevel.getUniqueId(), new RuntimeWailing(RandomSource.create(seed), startTick));
   }

   public static void tick(ServerLevel level, ServerSubLevel rootSubLevel) {
      CompoundTag tag = rootSubLevel.getUserDataTag();
      if (tag == null || !tag.contains(WAILING_KEY)) {
         return;
      }

      CompoundTag wailing = tag.getCompound(WAILING_KEY);
      long gameTime = level.getGameTime();
      if (gameTime >= wailing.getLong(END_TICK_KEY)) {
         restoreBaseMotors(rootSubLevel.getUniqueId());
         tag.remove(WAILING_KEY);
         rootSubLevel.setUserDataTag(tag);
         RUNTIME_WAILING.remove(rootSubLevel.getUniqueId());
         return;
      }

      RuntimeWailing runtime = RUNTIME_WAILING.computeIfAbsent(
         rootSubLevel.getUniqueId(),
         unused -> new RuntimeWailing(RandomSource.create(wailing.getLong(SEED_KEY)), startTick(wailing, gameTime))
      );
      if (gameTime >= runtime.nextRetargetTick()) {
         retargetWailing(level, rootSubLevel, wailing, runtime);
      }
   }

   public static void clear(UUID rootId) {
      RUNTIME_WAILING.remove(rootId);
   }

   public static void stopWailing(ServerSubLevel rootSubLevel) {
      restoreBaseMotors(rootSubLevel.getUniqueId());
      CompoundTag tag = rootSubLevel.getUserDataTag();
      if (tag != null && tag.contains(WAILING_KEY)) {
         tag.remove(WAILING_KEY);
         rootSubLevel.setUserDataTag(tag);
      }
      RUNTIME_WAILING.remove(rootSubLevel.getUniqueId());
   }

   private static void retargetWailing(ServerLevel level, ServerSubLevel rootSubLevel, CompoundTag wailing, RuntimeWailing runtime) {
      Map<BodyPart, RagdollAssemblyHelper.RagdollJoint> joints = RagdollAssemblyHelper.joints(rootSubLevel.getUniqueId());
      double stiffness = wailing.getDouble(STIFFNESS_KEY);
      for (Map.Entry<BodyPart, RagdollAssemblyHelper.RagdollJoint> entry : joints.entrySet()) {
         if (entry.getKey() == BodyPart.TORSO) {
            continue;
         }

         RagdollAssemblyHelper.RagdollJoint joint = entry.getValue();
         PhysicsConstraintHandle handle = joint.handle();
         if (handle == null || !handle.isValid()) {
            continue;
         }

         Vector3d target = randomTarget(entry.getKey(), joint.baseTarget(), runtime.random());
         handle.setMotor(ConstraintJointAxis.ANGULAR_X, target.x, stiffness, WAILING_DAMPING, false, 0.0);
         handle.setMotor(ConstraintJointAxis.ANGULAR_Y, target.y, stiffness, WAILING_DAMPING, false, 0.0);
         handle.setMotor(ConstraintJointAxis.ANGULAR_Z, target.z, stiffness, WAILING_DAMPING, false, 0.0);
      }

      runtime.setNextRetargetTick(level.getGameTime() + Math.max(1, wailing.getInt(INTERVAL_TICKS_KEY)));
   }

   private static long startTick(CompoundTag wailing, long fallback) {
      return wailing.contains(START_TICK_KEY) ? wailing.getLong(START_TICK_KEY) : fallback;
   }

   private static Vector3d randomTarget(BodyPart bodyPart, Vector3dc baseTarget, RandomSource random) {
      double pitchRange;
      double yawRange;
      double rollRange;
      switch (bodyPart) {
         case HEAD -> {
            pitchRange = 18.0;
            yawRange = 25.0;
            rollRange = 16.0;
         }
         case LEFT_ARM, RIGHT_ARM -> {
            pitchRange = 95.0;
            yawRange = 35.0;
            rollRange = 80.0;
         }
         case LEFT_LEG, RIGHT_LEG -> {
            pitchRange = 55.0;
            yawRange = 20.0;
            rollRange = 45.0;
         }
         case TORSO -> {
            pitchRange = 0.0;
            yawRange = 0.0;
            rollRange = 0.0;
         }
         default -> {
            pitchRange = 35.0;
            yawRange = 25.0;
            rollRange = 35.0;
         }
      }

      return new Vector3d(baseTarget)
         .add(randomRadians(random, pitchRange), randomRadians(random, yawRange), randomRadians(random, rollRange));
   }

   private static double randomRadians(RandomSource random, double degrees) {
      return Math.toRadians((random.nextDouble() * 2.0 - 1.0) * degrees);
   }

   private static void restoreBaseMotors(UUID rootId) {
      for (RagdollAssemblyHelper.RagdollJoint joint : RagdollAssemblyHelper.joints(rootId).values()) {
         PhysicsConstraintHandle handle = joint.handle();
         if (handle == null || !handle.isValid()) {
            continue;
         }

         Vector3dc baseTarget = joint.baseTarget();
         handle.setMotor(ConstraintJointAxis.ANGULAR_X, baseTarget.x(), joint.baseStiffness(), joint.baseDamping(), false, 0.0);
         handle.setMotor(ConstraintJointAxis.ANGULAR_Y, baseTarget.y(), joint.baseStiffness(), joint.baseDamping(), false, 0.0);
         handle.setMotor(ConstraintJointAxis.ANGULAR_Z, baseTarget.z(), joint.baseStiffness(), joint.baseDamping(), false, 0.0);
      }
   }

   private static CompoundTag writableUserData(ServerSubLevel subLevel) {
      CompoundTag tag = subLevel.getUserDataTag();
      return tag == null ? new CompoundTag() : tag;
   }

   private static final class RuntimeWailing {
      private final RandomSource random;
      private long nextRetargetTick;

      private RuntimeWailing(RandomSource random, long nextRetargetTick) {
         this.random = random;
         this.nextRetargetTick = nextRetargetTick;
      }

      private RandomSource random() {
         return random;
      }

      private long nextRetargetTick() {
         return nextRetargetTick;
      }

      private void setNextRetargetTick(long value) {
         nextRetargetTick = value;
      }
   }
}
