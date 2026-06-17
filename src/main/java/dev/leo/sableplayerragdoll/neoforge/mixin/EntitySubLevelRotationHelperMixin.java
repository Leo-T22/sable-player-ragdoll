package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollSableCameraBypass;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntitySubLevelRotationHelper.class, remap = false)
public abstract class EntitySubLevelRotationHelperMixin {
   @Inject(method = "getSubLevelInheritedOrientation", at = @At("HEAD"), cancellable = true)
   private static void sablePlayerRagdoll$ignoreRagdollSeatCameraRotation(
      Entity cameraEntity,
      Function<SubLevel, Pose3dc> poseProvider,
      EntitySubLevelRotationHelper.Type type,
      CallbackInfoReturnable<Quaterniond> cir
   ) {
      if (type == EntitySubLevelRotationHelper.Type.CAMERA && RagdollSableCameraBypass.suppressSubLevelCameraRotation(cameraEntity)) {
         cir.setReturnValue(null);
      }
   }
}
