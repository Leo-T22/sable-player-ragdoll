package dev.leo.sableplayerragdoll.neoforge.config;

import dev.leo.sableplayerragdoll.config.RagdollSettings;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.config.ModConfigEvent.Loading;
import net.neoforged.fml.event.config.ModConfigEvent.Reloading;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class RagdollConfig {
   private static final Builder BUILDER = new Builder();

   public static final BooleanValue ENABLED = BUILDER.comment("Master switch for the ragdoll system.").define("enabled", true);
   public static final DoubleValue MAX_VELOCITY_DELTA = BUILDER.comment("Ignore velocity spikes above this (m/s) to filter teleports and chunk loads.")
      .defineInRange("maxVelocityDelta", 120.0, 8.0, 256.0);
   public static final IntValue COOLDOWN_TICKS = BUILDER.comment("Ticks before the same player can be ragdolled again.")
      .defineInRange("cooldownTicks", 60, 0, 1200);
   public static final BooleanValue AFFECT_CREATIVE = BUILDER.comment("When true, creative-mode players can also be ragdolled.").define("affectCreative", true);
   public static final DoubleValue MAX_FLING_SPEED = BUILDER.comment("Clamp inherited linear speed on the ragdoll capsule (m/s).")
      .defineInRange("maxFlingSpeed", 128.0, 1.0, 256.0);
   public static final DoubleValue RAGDOLL_MAX_LAUNCH_SPEED = BUILDER.comment("Clamp total ragdoll launch speed (m/s).")
      .defineInRange("ragdollMaxLaunchSpeed", 128.0, 1.0, 256.0);
   public static final BooleanValue AUTO_SEAT_ON_TRIGGER = BUILDER.comment("Seat the player on the ragdoll automatically after launch.")
      .define("autoSeatOnTrigger", true);
   public static final IntValue RAGDOLL_DURATION_TICKS = BUILDER.comment("Ticks after launch before the ragdoll expires and the player is unseated.")
      .defineInRange("ragdollDurationTicks", 40, 5, 600);
   public static final IntValue STEP1_BODY_LIFETIME_TICKS = BUILDER.comment("Hard safety limit: force expiry if the ragdoll still exists after this many ticks.")
      .defineInRange("step1BodyLifetimeTicks", 200, 20, 2400);
   public static final DoubleValue RELEASE_SPEED_THRESHOLD = BUILDER.comment("Expire after touchdown only once the ragdoll slows below this speed (m/s).")
      .defineInRange("releaseSpeedThreshold", 0.1, 0.0, 32.0);
   public static final BooleanValue DEBUG_LOGGING = BUILDER.comment("Log ragdoll trigger and seating details to the server console.")
      .define("debugLogging", true);

   public static final ModConfigSpec SPEC = BUILDER.build();

   private RagdollConfig() {
   }

   public static void register(ModContainer container) {
      container.registerConfig(Type.SERVER, SPEC);
   }

   public static void onLoad(Loading event) {
      if (event.getConfig().getSpec() == SPEC) apply();
   }

   public static void onReload(Reloading event) {
      if (event.getConfig().getSpec() == SPEC) apply();
   }

   private static void apply() {
      RagdollSettings.setEnabled((Boolean) ENABLED.get());
      RagdollSettings.setMaxVelocityDelta((Double) MAX_VELOCITY_DELTA.get());
      RagdollSettings.setCooldownTicks((Integer) COOLDOWN_TICKS.get());
      RagdollSettings.setAffectCreative((Boolean) AFFECT_CREATIVE.get());
      RagdollSettings.setMaxFlingSpeed((Double) MAX_FLING_SPEED.get());
      RagdollSettings.setRagdollMaxLaunchSpeed((Double) RAGDOLL_MAX_LAUNCH_SPEED.get());
      RagdollSettings.setAutoSeatOnTrigger((Boolean) AUTO_SEAT_ON_TRIGGER.get());
      RagdollSettings.setRagdollDurationTicks((Integer) RAGDOLL_DURATION_TICKS.get());
      RagdollSettings.setStep1BodyLifetimeTicks((Integer) STEP1_BODY_LIFETIME_TICKS.get());
      RagdollSettings.setReleaseSpeedThreshold((Double) RELEASE_SPEED_THRESHOLD.get());
      RagdollSettings.setDebugLogging((Boolean) DEBUG_LOGGING.get());
   }

   static {
      BUILDER.comment("Ragdoll lifetime and seating.").push("ragdoll");
      BUILDER.pop();
      BUILDER.comment("Developer options.").push("debug");
      BUILDER.pop();
   }
}
