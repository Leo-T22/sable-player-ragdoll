package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
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
    private static final double PLAYER_MASS_SCALE = 2.0;

    @Inject(method = "getMass", at = @At("HEAD"), cancellable = true)
    private static void sablePlayerRagdoll$mobRagdollMass(BlockGetter level, BlockPos pos, BlockState state, CallbackInfoReturnable<Double> cir) {
        Double volumeFraction = ragdollVolumeFraction(state);
        if (volumeFraction != null) {
            cir.setReturnValue(massFromVolume(volumeFraction * massScale(state)));
        }
    }

    @Inject(method = "getVolume", at = @At("HEAD"), cancellable = true)
    private static void sablePlayerRagdoll$ragdollVolume(BlockState state, CallbackInfoReturnable<Double> cir) {
        Double volumeFraction = ragdollVolumeFraction(state);
        if (volumeFraction != null) {
            cir.setReturnValue(volumeFraction);
        }
    }

    private static Double ragdollVolumeFraction(BlockState state) {
        if (state.getBlock() instanceof MobRagdollPartBlock) {
            return state.getValue(MobRagdollPartBlock.X_SIZE)
                    * state.getValue(MobRagdollPartBlock.Y_SIZE)
                    * state.getValue(MobRagdollPartBlock.Z_SIZE)
                    / 4096.0;
        }
        return state.getBlock() instanceof RagdollPartBlock
                ? playerPartVolumeFraction(state.getValue(RagdollPartBlock.BODY_PART))
                : null;
    }

    private static double massScale(BlockState state) {
        return state.getBlock() instanceof RagdollPartBlock ? PLAYER_MASS_SCALE : 1.0;
    }

    private static double massFromVolume(double volumeFraction) {
        return Math.max(0.01, RagdollSettings.mobMassDensity() * volumeFraction);
    }

    private static double playerPartVolumeFraction(BodyPart bodyPart) {
        return switch (bodyPart) {
            case HEAD -> (8.0 * 4.0 * 8.0) / 4096.0;
            case TORSO -> (6.0 * 9.0 * 4.0) / 4096.0;
            case LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG -> (2.0 * 10.0 * 4.0) / 4096.0;
        };
    }
}
