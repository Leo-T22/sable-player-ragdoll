package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.mob.block.MobRagdollPartBlock;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PhysicsBlockPropertyHelper.class, remap = false)
public abstract class PhysicsBlockPropertyHelperMixin {

    @Inject(method = "getMass", at = @At("HEAD"), cancellable = true)
    private static void sablePlayerRagdoll$ragdollMass(BlockGetter level, BlockPos pos, BlockState state, CallbackInfoReturnable<Double> cir) {
        if (isRagdollPart(state)) {
            cir.setReturnValue(0.2);
        }
    }

    @Inject(method = "getVolume", at = @At("HEAD"), cancellable = true)
    private static void sablePlayerRagdoll$ragdollVolume(BlockState state, CallbackInfoReturnable<Double> cir) {
        if (isRagdollPart(state)) {
            cir.setReturnValue(1.0);
        }
    }

    private static boolean isRagdollPart(BlockState state) {
        return state.getBlock() instanceof MobRagdollPartBlock || state.getBlock() instanceof RagdollPartBlock;
    }
}
