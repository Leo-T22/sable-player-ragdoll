package dev.leo.sableplayerragdoll.config;

public final class RagdollSettings {
   private static boolean enabled = true;
   private static double minVelocityDelta = 15.0;
   private static double maxVelocityDelta = 120.0;
   private static double maxFlingSpeed = 128.0;
   private static double ragdollMaxLaunchSpeed = 128.0;
   private static boolean expireAfterDuration = false;
   private static int ragdollDurationTicks = 40;
   private static boolean expireAfterSafetyTimeout = false;
   private static int step1BodyLifetimeTicks = 200;
   private static boolean expireWhenSlow = false;
   private static double releaseSpeedThreshold = 0.1;
   private static int cooldownTicks = 60;
   private static boolean affectCreative = true;
   private static boolean autoSeatOnTrigger = true;
   private static boolean allowManualTrigger = true;
   private static boolean debugLogging = true;

   private RagdollSettings() {
   }

   public static boolean enabled() { return enabled; }
   public static void setEnabled(boolean v) { enabled = v; }

   public static double minVelocityDelta() { return minVelocityDelta; }
   public static void setMinVelocityDelta(double v) { minVelocityDelta = Math.max(0.1, v); }

   public static double maxVelocityDelta() { return maxVelocityDelta; }
   public static void setMaxVelocityDelta(double v) { maxVelocityDelta = Math.max(1.0, v); }

   public static double maxFlingSpeed() { return maxFlingSpeed; }
   public static void setMaxFlingSpeed(double v) { maxFlingSpeed = Math.max(0.5, v); }

   public static double ragdollMaxLaunchSpeed() { return ragdollMaxLaunchSpeed; }
   public static void setRagdollMaxLaunchSpeed(double v) { ragdollMaxLaunchSpeed = Math.max(0.5, v); }

   public static boolean expireAfterDuration() { return expireAfterDuration; }
   public static void setExpireAfterDuration(boolean v) { expireAfterDuration = v; }

   public static int ragdollDurationTicks() { return ragdollDurationTicks; }
   public static void setRagdollDurationTicks(int v) { ragdollDurationTicks = Math.max(1, v); }

   public static boolean expireAfterSafetyTimeout() { return expireAfterSafetyTimeout; }
   public static void setExpireAfterSafetyTimeout(boolean v) { expireAfterSafetyTimeout = v; }

   public static int step1BodyLifetimeTicks() { return step1BodyLifetimeTicks; }
   public static void setStep1BodyLifetimeTicks(int v) { step1BodyLifetimeTicks = Math.max(20, v); }

   public static boolean expireWhenSlow() { return expireWhenSlow; }
   public static void setExpireWhenSlow(boolean v) { expireWhenSlow = v; }

   public static double releaseSpeedThreshold() { return releaseSpeedThreshold; }
   public static void setReleaseSpeedThreshold(double v) { releaseSpeedThreshold = Math.max(0.0, v); }

   public static int cooldownTicks() { return cooldownTicks; }
   public static void setCooldownTicks(int v) { cooldownTicks = Math.max(0, v); }

   public static boolean affectCreative() { return affectCreative; }
   public static void setAffectCreative(boolean v) { affectCreative = v; }

   public static boolean autoSeatOnTrigger() { return autoSeatOnTrigger; }
   public static void setAutoSeatOnTrigger(boolean v) { autoSeatOnTrigger = v; }

   public static boolean allowManualTrigger() { return allowManualTrigger; }
   public static void setAllowManualTrigger(boolean v) { allowManualTrigger = v; }

   public static boolean debugLogging() { return debugLogging; }
   public static void setDebugLogging(boolean v) { debugLogging = v; }
}
