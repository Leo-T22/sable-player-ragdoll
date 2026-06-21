package dev.leo.sableplayerragdoll.neoforge.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;

public final class RagdollClientConfig {
   private static final Builder BUILDER = new Builder();

   public static final BooleanValue USE_FIRST_PERSON_CAMERA = BUILDER.translation("sable_player_ragdoll.configuration.use_first_person_camera")
      .comment("When ragdolled, switch to the normal first-person camera instead of the unlocked Sable contraption camera.")
      .define("useFirstPersonCamera", false);

   public static final DoubleValue SUB_LEVEL_CAMERA_DISTANCE = BUILDER.translation("sable_player_ragdoll.configuration.sub_level_camera_distance")
      .comment(
         "Base distance (blocks) for the unlocked Sable ragdoll camera before scroll zoom. Sable multiplies this by",
         "~1.75 and clamps it against terrain, so the effective pull-back is larger. Vanilla default is 4.0; lower it",
         "to bring the camera closer to the ragdoll. Ignored when useFirstPersonCamera is on."
      )
      .defineInRange("subLevelCameraDistance", 4.0, 0.5, 16.0);

   public static final BooleanValue SHOW_CONTROLS_HINT = BUILDER.translation("sable_player_ragdoll.configuration.show_controls_hint")
      .comment("Show the on-screen controls hint popup when entering ragdoll mode.")
      .define("showControlsHint", true);

   public static final ModConfigSpec SPEC = BUILDER.build();

   private RagdollClientConfig() {
   }

   public static void register(ModContainer container) {
      container.registerConfig(Type.CLIENT, SPEC);
   }

   public static boolean useFirstPersonCamera() {
      return USE_FIRST_PERSON_CAMERA.get();
   }

   public static double subLevelCameraDistance() {
      return SUB_LEVEL_CAMERA_DISTANCE.get();
   }

   public static boolean showControlsHint() {
      return SHOW_CONTROLS_HINT.get();
   }
}
