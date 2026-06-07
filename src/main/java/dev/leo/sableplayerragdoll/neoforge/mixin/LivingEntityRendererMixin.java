package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.neoforge.client.RagdollPartBlockEntityRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
   @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
   private void onGetModel(CallbackInfoReturnable<M> cir) {
      if (RagdollPartBlockEntityRenderer.currentModel() != null) {
         cir.setReturnValue((M) RagdollPartBlockEntityRenderer.currentModel());
      }
   }
}
