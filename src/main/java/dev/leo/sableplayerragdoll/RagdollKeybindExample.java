package dev.leo.sableplayerragdoll;

import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollLaunchOptions;
import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.api.RagdollPoseSnapshot;
import dev.leo.sableplayerragdoll.api.RagdollSession;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class RagdollKeybindExample {

   private static final double TICKS_TO_METRES_PER_SECOND = 20.0;

   private RagdollKeybindExample() {
   }

   @Nullable
   public static RagdollSession launch(ServerPlayer player) {
      return launch(player, new RagdollPoseSnapshot(RagdollLimbOptions.defaults(), player.yBodyRot));
   }

   @Nullable
   public static RagdollSession launch(ServerPlayer player, RagdollPoseSnapshot initialPose) {
      Vec3 velocity = player.getKnownMovement().scale(TICKS_TO_METRES_PER_SECOND);
      return RagdollAPI.launch(player, velocity, RagdollLaunchOptions.builder().limbs(onFootPose()).build(), initialPose);
   }

   private static RagdollLimbOptions onFootPose() {
      return RagdollLimbOptions.builder()
         .limb(BodyPart.LEFT_ARM, RagdollLimbConfig.builder().roll(20).stiffness(5))
         .limb(BodyPart.RIGHT_ARM, RagdollLimbConfig.builder().roll(-20).stiffness(5))
         .limb(BodyPart.RIGHT_LEG, RagdollLimbConfig.builder().roll(-20).stiffness(5))
         .limb(BodyPart.LEFT_LEG, RagdollLimbConfig.builder().roll(20).stiffness(5))
         .build();
   }

}
