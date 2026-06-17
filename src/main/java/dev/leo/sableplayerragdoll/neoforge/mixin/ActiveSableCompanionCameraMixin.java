package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollSableCameraBypass;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class ActiveSableCompanionCameraMixin {
   @Inject(method = "getTrackingOrVehicleSubLevel", at = @At("HEAD"), cancellable = true)
   private void sablePlayerRagdoll$ignoreRagdollSeatCameraSubLevel(Entity entity, CallbackInfoReturnable<SubLevel> cir) {
      if (RagdollSableCameraBypass.detachSubLevelCameraPosition(entity)) {
         cir.setReturnValue(null);
      }
   }
}
