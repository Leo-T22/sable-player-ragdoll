package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityRagdollHiddenMixin {

    private boolean isHiddenRagdollSource() {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) {
            // Client can't check session state; infer from the invisible+seated condition
            return self.isInvisible() && self.getVehicle() instanceof RagdollSeatEntity;
        }
        if (!(self instanceof ServerPlayer serverPlayer)) return false;
        return RagdollSessionManager.isPlayerCurrentlyRagdolled(serverPlayer);
    }

    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void hideFromDirectAttacks(CallbackInfoReturnable<Boolean> cir) {
        if (!RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.get() && isHiddenRagdollSource()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void blockDirectInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.get() && isHiddenRagdollSource()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void hideFromCollisionPicking(CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void hideFromCollisionPartners(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void shrinkBoundingBox(CallbackInfoReturnable<AABB> cir) {
        if (isHiddenRagdollSource()) {
            Entity self = (Entity) (Object) this;
            double half = 0.0005;
            cir.setReturnValue(new AABB(
                self.getX() - half, self.getY(), self.getZ() - half,
                self.getX() + half, self.getY() + half * 2.0, self.getZ() + half
            ));
        }
    }
}
