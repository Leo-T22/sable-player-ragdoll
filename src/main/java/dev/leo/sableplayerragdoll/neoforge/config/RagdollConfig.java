package dev.leo.sableplayerragdoll.neoforge.config;

import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.ryanhcode.sable.physics.config.block_properties.BlockStateConditionSet;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinition;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.config.ModConfigEvent.Loading;
import net.neoforged.fml.event.config.ModConfigEvent.Reloading;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
   public static final IntValue MIN_DISMOUNT_TICKS = BUILDER.translation("sable_player_ragdoll.configuration.min_dismount_ticks")
      .comment("Minimum ticks after ragdoll start before the player can manually exit. 60 = 3 seconds.")
      .defineInRange("minDismountTicks", 60, 0, 1200);

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
      BUILDER.translation("sable_player_ragdoll.configuration.impact_damage").comment("Damage from hard ragdoll impacts.").push("impact_damage");
   }

   public static final BooleanValue IMPACT_DAMAGE_ENABLED = BUILDER.translation("sable_player_ragdoll.configuration.impact_damage_enabled")
      .comment("Damage ragdolled players when their ragdoll takes a hard impact.")
      .define("impactDamageEnabled", true);
   public static final DoubleValue IMPACT_FEEDBACK_THRESHOLD = BUILDER.translation("sable_player_ragdoll.configuration.impact_feedback_threshold")
      .comment("Minimum speed lost in one tick before ragdoll impact sound and dust are played (m/s).")
      .defineInRange("impactFeedbackThreshold", 4.0, 0.0, 128.0);
   public static final DoubleValue IMPACT_DAMAGE_THRESHOLD = BUILDER.translation("sable_player_ragdoll.configuration.impact_damage_threshold")
      .comment("Minimum speed lost in one tick before ragdoll impact damage is applied (m/s).")
      .defineInRange("impactDamageThreshold", 12.0, 0.0, 128.0);
   public static final DoubleValue IMPACT_DAMAGE_MULTIPLIER = BUILDER.translation("sable_player_ragdoll.configuration.impact_damage_multiplier")
      .comment("Damage dealt per m/s of speed lost above the threshold.")
      .defineInRange("impactDamageMultiplier", 0.75, 0.0, 20.0);
   public static final DoubleValue IMPACT_DAMAGE_MAX = BUILDER.translation("sable_player_ragdoll.configuration.impact_damage_max")
      .comment("Maximum damage dealt by one ragdoll impact.")
      .defineInRange("impactDamageMax", 20.0, 0.0, 200.0);
   public static final IntValue IMPACT_DAMAGE_COOLDOWN_TICKS = BUILDER.translation("sable_player_ragdoll.configuration.impact_damage_cooldown_ticks")
      .comment("Minimum ticks between ragdoll impact damage events for the same ragdoll.")
      .defineInRange("impactDamageCooldownTicks", 10, 0, 200);

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
      BUILDER.translation("sable_player_ragdoll.configuration.body_mass").comment("Physics mass per body part (kg). Minimum 0.1.").push("body_mass");
   }

   public static final DoubleValue MASS_HEAD = BUILDER.translation("sable_player_ragdoll.configuration.mass_head")
      .comment("Mass of the head.")
      .defineInRange("massHead", 0.35, 0.1, 100.0);
   public static final DoubleValue MASS_TORSO = BUILDER.translation("sable_player_ragdoll.configuration.mass_torso")
      .comment("Mass of the torso.")
      .defineInRange("massTorso", 1.4, 0.1, 100.0);
   public static final DoubleValue MASS_ARM = BUILDER.translation("sable_player_ragdoll.configuration.mass_arm")
      .comment("Mass of each arm.")
      .defineInRange("massArm", 0.2, 0.1, 100.0);
   public static final DoubleValue MASS_LEG = BUILDER.translation("sable_player_ragdoll.configuration.mass_leg")
      .comment("Mass of each leg.")
      .defineInRange("massLeg", 0.45, 0.1, 100.0);

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
      RagdollSettings.setImpactDamageEnabled((Boolean) IMPACT_DAMAGE_ENABLED.get());
      RagdollSettings.setImpactFeedbackThreshold((Double) IMPACT_FEEDBACK_THRESHOLD.get());
      RagdollSettings.setImpactDamageThreshold((Double) IMPACT_DAMAGE_THRESHOLD.get());
      RagdollSettings.setImpactDamageMultiplier((Double) IMPACT_DAMAGE_MULTIPLIER.get());
      RagdollSettings.setImpactDamageMax((Double) IMPACT_DAMAGE_MAX.get());
      RagdollSettings.setImpactDamageCooldownTicks((Integer) IMPACT_DAMAGE_COOLDOWN_TICKS.get());
      RagdollSettings.setAutoSeatOnTrigger((Boolean) AUTO_SEAT_ON_TRIGGER.get());
      RagdollSettings.setAllowManualTrigger((Boolean) ALLOW_MANUAL_TRIGGER.get());
      RagdollSettings.setMinDismountTicks((Integer) MIN_DISMOUNT_TICKS.get());
      RagdollSettings.setExpireAfterDuration((Boolean) EXPIRE_AFTER_DURATION.get());
      RagdollSettings.setRagdollDurationTicks((Integer) RAGDOLL_DURATION_TICKS.get());
      RagdollSettings.setExpireWhenSlow((Boolean) EXPIRE_WHEN_SLOW.get());
      RagdollSettings.setStep1BodyLifetimeTicks((Integer) STEP1_BODY_LIFETIME_TICKS.get());
      RagdollSettings.setExpireAfterSafetyTimeout((Boolean) EXPIRE_AFTER_SAFETY_TIMEOUT.get());
      RagdollSettings.setReleaseSpeedThreshold((Double) RELEASE_SPEED_THRESHOLD.get());
      RagdollSettings.setDebugLogging((Boolean) DEBUG_LOGGING.get());
      RagdollSettings.setMassHead((Double) MASS_HEAD.get());
      RagdollSettings.setMassTorso((Double) MASS_TORSO.get());
      RagdollSettings.setMassArm((Double) MASS_ARM.get());
      RagdollSettings.setMassLeg((Double) MASS_LEG.get());
      applyBodyMasses();
   }

   public static void applyBodyMasses() {
      ResourceLocation massKey = ResourceLocation.fromNamespaceAndPath("sable", "mass");
      Map<BlockStateConditionSet, Map<ResourceLocation, Object>> overrides = new LinkedHashMap<>();
      overrides.put(conditionSet("body_part=head"),      Map.of(massKey, RagdollSettings.massHead()));
      overrides.put(conditionSet("body_part=left_arm"),  Map.of(massKey, RagdollSettings.massArm()));
      overrides.put(conditionSet("body_part=right_arm"), Map.of(massKey, RagdollSettings.massArm()));
      overrides.put(conditionSet("body_part=left_leg"),  Map.of(massKey, RagdollSettings.massLeg()));
      overrides.put(conditionSet("body_part=right_leg"), Map.of(massKey, RagdollSettings.massLeg()));
      PhysicsBlockPropertiesDefinition def = new PhysicsBlockPropertiesDefinition(
         new ExtraCodecs.TagOrElementLocation(ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_part"), false),
         2000,
         Map.of(massKey, RagdollSettings.massTorso()),
         Optional.of(overrides)
      );
      PhysicsBlockPropertiesDefinitionLoader.applyToBlocks(def);
   }

   private static BlockStateConditionSet conditionSet(String condition) {
      return BlockStateConditionSet.parse(condition).getOrThrow();
   }
}
