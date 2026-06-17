package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollCameraHelper;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class RagdollFirstPersonEyeMixin {
   @Shadow
   private Vec3 position;

   @Shadow
   protected abstract void setPosition(double x, double y, double z);
   @Inject(method = "setup", at = @At("TAIL"))
   private void sablePlayerRagdoll$anchorRagdollEye(
      BlockGetter area, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci
   ) {
      Vec3 target = RagdollCameraHelper.firstPersonHeadAnchor(this.position, partialTick);
      if (target != this.position) {
         this.setPosition(target.x, target.y, target.z);
      }
   }
}
