package dev.leo.sableplayerragdoll.api;

public record RagdollPoseSnapshot(RagdollLimbOptions limbs, float bodyYawDegrees) {
   public RagdollPoseSnapshot {
      limbs = limbs == null ? RagdollLimbOptions.defaults() : limbs;
   }
}
