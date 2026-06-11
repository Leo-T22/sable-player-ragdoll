package dev.leo.sableplayerragdoll;

import net.minecraft.sounds.SoundEvent;

public final class RagdollSoundEvents {
   private static SoundEvent ragdollImpact;
   private static SoundEvent ragdollSmallImpact;

   private RagdollSoundEvents() {
   }

   public static SoundEvent ragdollImpact() {
      return ragdollImpact;
   }

   public static SoundEvent ragdollSmallImpact() {
      return ragdollSmallImpact;
   }

   public static void bindRagdollImpact(SoundEvent soundEvent) {
      ragdollImpact = soundEvent;
   }

   public static void bindRagdollSmallImpact(SoundEvent soundEvent) {
      ragdollSmallImpact = soundEvent;
   }
}
