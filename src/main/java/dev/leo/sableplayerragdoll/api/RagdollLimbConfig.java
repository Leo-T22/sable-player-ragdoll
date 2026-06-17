package dev.leo.sableplayerragdoll.api;

import java.util.OptionalDouble;

/**
 * Optional per-limb overrides applied when a ragdoll is assembled.
 *
 * <p>Every field is optional: anything left unset falls back to the built-in default for that
 * limb, so callers can override just a rotation, just the joint stiffness, or any subset. Rotation
 * is expressed in degrees about the limb's local axes (pitch = X, yaw = Y, roll = Z).
 *
 * <p>Build instances with {@link #builder()} and attach them to limbs via {@link RagdollLimbOptions}.
 */
public final class RagdollLimbConfig {
   private final OptionalDouble pitchDegrees;
   private final OptionalDouble yawDegrees;
   private final OptionalDouble rollDegrees;
   private final OptionalDouble initialPitchDegrees;
   private final OptionalDouble initialYawDegrees;
   private final OptionalDouble initialRollDegrees;
   private final OptionalDouble rightOffset;
   private final OptionalDouble upOffset;
   private final OptionalDouble forwardOffset;
   private final OptionalDouble angularStiffness;
   private final OptionalDouble angularDamping;

   private RagdollLimbConfig(
      OptionalDouble pitchDegrees,
      OptionalDouble yawDegrees,
      OptionalDouble rollDegrees,
      OptionalDouble initialPitchDegrees,
      OptionalDouble initialYawDegrees,
      OptionalDouble initialRollDegrees,
      OptionalDouble rightOffset,
      OptionalDouble upOffset,
      OptionalDouble forwardOffset,
      OptionalDouble angularStiffness,
      OptionalDouble angularDamping
   ) {
      this.pitchDegrees = pitchDegrees;
      this.yawDegrees = yawDegrees;
      this.rollDegrees = rollDegrees;
      this.initialPitchDegrees = initialPitchDegrees;
      this.initialYawDegrees = initialYawDegrees;
      this.initialRollDegrees = initialRollDegrees;
      this.rightOffset = rightOffset;
      this.upOffset = upOffset;
      this.forwardOffset = forwardOffset;
      this.angularStiffness = angularStiffness;
      this.angularDamping = angularDamping;
   }

   public OptionalDouble pitchDegrees() {
      return this.pitchDegrees;
   }

   public OptionalDouble yawDegrees() {
      return this.yawDegrees;
   }

   public OptionalDouble rollDegrees() {
      return this.rollDegrees;
   }

   public OptionalDouble initialPitchDegrees() {
      return this.initialPitchDegrees;
   }

   public OptionalDouble initialYawDegrees() {
      return this.initialYawDegrees;
   }

   public OptionalDouble initialRollDegrees() {
      return this.initialRollDegrees;
   }

   public OptionalDouble rightOffset() {
      return this.rightOffset;
   }

   public OptionalDouble upOffset() {
      return this.upOffset;
   }

   public OptionalDouble forwardOffset() {
      return this.forwardOffset;
   }

   public OptionalDouble angularStiffness() {
      return this.angularStiffness;
   }

   public OptionalDouble angularDamping() {
      return this.angularDamping;
   }

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder {
      private OptionalDouble pitchDegrees = OptionalDouble.empty();
      private OptionalDouble yawDegrees = OptionalDouble.empty();
      private OptionalDouble rollDegrees = OptionalDouble.empty();
      private OptionalDouble initialPitchDegrees = OptionalDouble.empty();
      private OptionalDouble initialYawDegrees = OptionalDouble.empty();
      private OptionalDouble initialRollDegrees = OptionalDouble.empty();
      private OptionalDouble rightOffset = OptionalDouble.empty();
      private OptionalDouble upOffset = OptionalDouble.empty();
      private OptionalDouble forwardOffset = OptionalDouble.empty();
      private OptionalDouble angularStiffness = OptionalDouble.empty();
      private OptionalDouble angularDamping = OptionalDouble.empty();

      private Builder() {
      }

      public Builder rotation(double pitchDegrees, double yawDegrees, double rollDegrees) {
         this.pitchDegrees = OptionalDouble.of(pitchDegrees);
         this.yawDegrees = OptionalDouble.of(yawDegrees);
         this.rollDegrees = OptionalDouble.of(rollDegrees);
         return this;
      }

      public Builder initialRotation(double pitchDegrees, double yawDegrees, double rollDegrees) {
         this.initialPitchDegrees = OptionalDouble.of(pitchDegrees);
         this.initialYawDegrees = OptionalDouble.of(yawDegrees);
         this.initialRollDegrees = OptionalDouble.of(rollDegrees);
         return this;
      }

      public Builder pitch(double degrees) {
         this.pitchDegrees = OptionalDouble.of(degrees);
         return this;
      }

      public Builder yaw(double degrees) {
         this.yawDegrees = OptionalDouble.of(degrees);
         return this;
      }

      public Builder roll(double degrees) {
         this.rollDegrees = OptionalDouble.of(degrees);
         return this;
      }

      public Builder offset(double right, double up, double forward) {
         this.rightOffset = OptionalDouble.of(right);
         this.upOffset = OptionalDouble.of(up);
         this.forwardOffset = OptionalDouble.of(forward);
         return this;
      }

      public Builder stiffness(double angularStiffness) {
         this.angularStiffness = OptionalDouble.of(angularStiffness);
         return this;
      }

      public Builder damping(double angularDamping) {
         this.angularDamping = OptionalDouble.of(angularDamping);
         return this;
      }

      public RagdollLimbConfig build() {
         return new RagdollLimbConfig(
            this.pitchDegrees,
            this.yawDegrees,
            this.rollDegrees,
            this.initialPitchDegrees,
            this.initialYawDegrees,
            this.initialRollDegrees,
            this.rightOffset,
            this.upOffset,
            this.forwardOffset,
            this.angularStiffness,
            this.angularDamping
         );
      }
   }
}
