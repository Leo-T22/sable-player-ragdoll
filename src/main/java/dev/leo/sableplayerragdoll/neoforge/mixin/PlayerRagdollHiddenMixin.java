package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerRagdollHiddenMixin {

    private boolean isHiddenRagdollSource() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            // Client can't check session state; infer from the invisible+seated condition
            return self instanceof Player && self.isInvisible() && self.getVehicle() instanceof RagdollSeatEntity;
        }
        if (!(self instanceof ServerPlayer serverPlayer)) return false;
        return RagdollSessionManager.isPlayerCurrentlyRagdolled(serverPlayer);
    }

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void hideFromPicking(CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void hideFromPushes(CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void cancelPush(Entity other, CallbackInfo ci) {
        if (isHiddenRagdollSource()) ci.cancel();
    }

    private static final float RAGDOLL_ANCHOR_EYE_HEIGHT = 1.62F;

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void shrinkDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (isHiddenRagdollSource()) {
            cir.setReturnValue(EntityDimensions.fixed(0.01F, 0.01F).withEyeHeight(RAGDOLL_ANCHOR_EYE_HEIGHT));
        }
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void stopPushingNeighbors(CallbackInfo ci) {
        if (isHiddenRagdollSource()) ci.cancel();
    }

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void stopPushPair(Entity other, CallbackInfo ci) {
        if (isHiddenRagdollSource()) ci.cancel();
    }
}
