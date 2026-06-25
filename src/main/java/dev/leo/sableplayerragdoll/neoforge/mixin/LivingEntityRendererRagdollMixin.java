package dev.leo.sableplayerragdoll.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRagdollMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void splrmob$skipRagdollSourceRender(
            LivingEntity entity, float entityYaw, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            CallbackInfo ci
    ) {
        if (MobRagdollAssembly.isConverted(entity.getUUID())) {
            ci.cancel();
        }
    }
}
