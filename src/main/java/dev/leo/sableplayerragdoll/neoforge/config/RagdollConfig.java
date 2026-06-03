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

   public static final BooleanValue ENABLED = BUILDER.translation("sable_player_ragdoll.configuration.enabled")
      .comment("Master switch for the ragdoll system.")
      .define("enabled", true);

   static {
      BUILDER.translation("sable_player_ragdoll.configuration.trigger").comment("Ragdoll trigger behavior.").push("trigger");
   }

   public static final IntValue COOLDOWN_TICKS = BUILDER.translation("sable_player_ragdoll.configuration.cooldown_ticks")
      .comment("Ticks before the same player can be ragdolled again.")
      .defineInRange("cooldownTicks", 60, 0, 1200);
   public static final BooleanValue AFFECT_CREATIVE = BUILDER.translation("sable_player_ragdoll.configuration.affect_creative")
      .comment("When true, creative-mode players can also be ragdolled.")
      .define("affectCreative", true);
   public static final BooleanValue AUTO_SEAT_ON_TRIGGER = BUILDER.translation("sable_player_ragdoll.configuration.auto_seat_on_trigger")
      .comment("Seat the player on the ragdoll automatically after launch.")
      .define("autoSeatOnTrigger", true);
   public static final BooleanValue ALLOW_MANUAL_TRIGGER = BUILDER.translation("sable_player_ragdoll.configuration.allow_manual_trigger")
      .comment("Allow players to trigger their own ragdoll with the client keybind.")
      .define("allowManualTrigger", true);

   static {
      BUILDER.pop();
      BUILDER.translation("sable_player_ragdoll.configuration.physics").comment("Ragdoll physics limits.").push("physics");
   }

   public static final DoubleValue MAX_VELOCITY_DELTA = BUILDER.translation("sable_player_ragdoll.configuration.max_velocity_delta")
      .comment("Ignore velocity spikes above this (m/s) to filter teleports and chunk loads.")
      .defineInRange("maxVelocityDelta", 120.0, 8.0, 256.0);
   public static final DoubleValue MAX_FLING_SPEED = BUILDER.translation("sable_player_ragdoll.configuration.max_fling_speed")
      .comment("Clamp inherited linear speed on the ragdoll capsule (m/s).")
      .defineInRange("maxFlingSpeed", 128.0, 1.0, 256.0);
   public static final DoubleValue RAGDOLL_MAX_LAUNCH_SPEED = BUILDER.translation("sable_player_ragdoll.configuration.ragdoll_max_launch_speed")
      .comment("Clamp total ragdoll launch speed (m/s).")
      .defineInRange("ragdollMaxLaunchSpeed", 128.0, 1.0, 256.0);

   static {
      BUILDER.pop();
      BUILDER.translation("sable_player_ragdoll.configuration.despawn").comment("When player ragdolls expire.").push("despawn");
   }

   public static final BooleanValue EXPIRE_AFTER_DURATION = BUILDER.translation("sable_player_ragdoll.configuration.expire_after_duration")
      .comment("Expire player ragdolls after the configured duration. If all despawn toggles are off, player ragdolls never expire automatically.")
      .define("expireAfterDuration", false);
   public static final IntValue RAGDOLL_DURATION_TICKS = BUILDER.translation("sable_player_ragdoll.configuration.player_ragdoll_duration")
      .comment("Ticks after launch before a player ragdoll expires and the player is unseated.")
      .defineInRange("ragdollDurationTicks", 40, 5, 600);
   public static final BooleanValue EXPIRE_WHEN_SLOW = BUILDER.translation("sable_player_ragdoll.configuration.expire_when_slow")
      .comment("Expire player ragdolls after they touch down and slow below the release speed threshold.")
      .define("expireWhenSlow", false);
   public static final DoubleValue RELEASE_SPEED_THRESHOLD = BUILDER.translation("sable_player_ragdoll.configuration.release_speed_threshold")
      .comment("Expire after touchdown only once the ragdoll slows below this speed (m/s).")
      .defineInRange("releaseSpeedThreshold", 0.1, 0.0, 32.0);
   public static final BooleanValue EXPIRE_AFTER_SAFETY_TIMEOUT = BUILDER.translation("sable_player_ragdoll.configuration.expire_after_safety_timeout")
      .comment("Force-expire player ragdolls after the safety timeout.")
      .define("expireAfterSafetyTimeout", false);
   public static final IntValue STEP1_BODY_LIFETIME_TICKS = BUILDER.translation("sable_player_ragdoll.configuration.safety_timeout")
      .comment("Hard safety limit: force expiry if the ragdoll still exists after this many ticks.")
      .defineInRange("step1BodyLifetimeTicks", 200, 20, 2400);

   static {
      BUILDER.pop();
      BUILDER.translation("sable_player_ragdoll.configuration.debug").comment("Developer options.").push("debug");
   }

   public static final BooleanValue DEBUG_LOGGING = BUILDER.translation("sable_player_ragdoll.configuration.debug_logging")
      .comment("Log ragdoll trigger and seating details to the server console.")
      .define("debugLogging", true);

   static {
      BUILDER.pop();
   }

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
      RagdollSettings.setAllowManualTrigger((Boolean) ALLOW_MANUAL_TRIGGER.get());
      RagdollSettings.setExpireAfterDuration((Boolean) EXPIRE_AFTER_DURATION.get());
      RagdollSettings.setRagdollDurationTicks((Integer) RAGDOLL_DURATION_TICKS.get());
      RagdollSettings.setExpireWhenSlow((Boolean) EXPIRE_WHEN_SLOW.get());
      RagdollSettings.setStep1BodyLifetimeTicks((Integer) STEP1_BODY_LIFETIME_TICKS.get());
      RagdollSettings.setExpireAfterSafetyTimeout((Boolean) EXPIRE_AFTER_SAFETY_TIMEOUT.get());
      RagdollSettings.setReleaseSpeedThreshold((Double) RELEASE_SPEED_THRESHOLD.get());
      RagdollSettings.setDebugLogging((Boolean) DEBUG_LOGGING.get());
   }
}
