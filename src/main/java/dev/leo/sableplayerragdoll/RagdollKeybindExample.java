package dev.leo.sableplayerragdoll;

import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollLaunchOptions;
import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.api.RagdollSession;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class RagdollKeybindExample {

   private static final double TICKS_TO_METRES_PER_SECOND = 20.0;
   private static final double UPWARD_KICK = 5.0;

   private RagdollKeybindExample() {
   }

   @Nullable
   public static RagdollSession launch(ServerPlayer player) {
      if (player.isFallFlying()) {
         Vec3 velocity = player.getDeltaMovement().scale(TICKS_TO_METRES_PER_SECOND);
         return RagdollAPI.launch(player, velocity, RagdollLaunchOptions.builder().limbs(elytraPose()).build());
      }

      Vec3 horizontal = player.getKnownMovement().scale(TICKS_TO_METRES_PER_SECOND);
      double verticalVelocity = player.getKnownMovement().y * TICKS_TO_METRES_PER_SECOND;
      Vec3 velocity = new Vec3(horizontal.x, Math.max(verticalVelocity, UPWARD_KICK), horizontal.z);
      return RagdollAPI.launch(player, velocity, RagdollLaunchOptions.builder().limbs(onFootPose()).build());
   }

   private static RagdollLimbOptions onFootPose() {
      return RagdollLimbOptions.builder()
         .limb(BodyPart.LEFT_ARM, RagdollLimbConfig.builder().roll(20))
         .limb(BodyPart.RIGHT_ARM, RagdollLimbConfig.builder().roll(-20))
         .limb(BodyPart.RIGHT_LEG, RagdollLimbConfig.builder().roll(-20))
         .limb(BodyPart.LEFT_LEG, RagdollLimbConfig.builder().roll(20))
         .build();
   }

   private static RagdollLimbOptions elytraPose() {
      return RagdollLimbOptions.builder()
         .limb(BodyPart.LEFT_ARM, RagdollLimbConfig.builder().stiffness(60).damping(8))
         .limb(BodyPart.RIGHT_ARM, RagdollLimbConfig.builder().stiffness(60).damping(8))
         .build();
   }
}
