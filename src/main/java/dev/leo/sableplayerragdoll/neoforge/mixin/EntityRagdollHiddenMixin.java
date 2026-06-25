package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityRagdollHiddenMixin {
    private static final EntityDimensions RAGDOLL_SOURCE_DIMENSIONS = EntityDimensions.fixed(0.01F, 0.01F).withEyeHeight(0.0F);
    private static final double RAGDOLL_SOURCE_BB_HALF_SIZE = 0.0005;

    private boolean isHiddenRagdollSource() {
        return isHiddenRagdollSource((Entity) (Object) this);
    }

    private static boolean isHiddenRagdollSource(Entity self) {
        if (self.level().isClientSide()) {
            if (self instanceof Player && self.isInvisible() && self.getVehicle() instanceof RagdollSeatEntity) {
                return true;
            }
            return false;
        }
        if (self instanceof ServerPlayer serverPlayer && RagdollSessionManager.isPlayerCurrentlyRagdolled(serverPlayer)) {
            return true;
        }
        return MobRagdollAssembly.isConverted(self.getUUID());
    }

    private static boolean ragdollPipeActive() {
        return RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.get() || MobRagdollAssembly.RAGDOLL_PIPE_ACTIVE.get();
    }

    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void hideFromPicking(CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void hideFromDirectAttacks(CallbackInfoReturnable<Boolean> cir) {
        if (!ragdollPipeActive() && isHiddenRagdollSource()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void blockDirectInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!ragdollPipeActive() && isHiddenRagdollSource()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
    private void hideFromCollisionPicking(CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void hideFromCollisionPartners(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource() || isHiddenRagdollSource(other)) cir.setReturnValue(false);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void hideFromPushes(CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void ignorePush(Entity entity, CallbackInfo ci) {
        if (isHiddenRagdollSource()) ci.cancel();
    }

    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void hideFromProjectiles(CallbackInfoReturnable<Boolean> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(false);
    }

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void shrinkDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (isHiddenRagdollSource()) cir.setReturnValue(RAGDOLL_SOURCE_DIMENSIONS);
    }

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void shrinkBoundingBox(CallbackInfoReturnable<AABB> cir) {
        if (isHiddenRagdollSource()) {
            Entity self = (Entity) (Object) this;
            double half = RAGDOLL_SOURCE_BB_HALF_SIZE;
            cir.setReturnValue(new AABB(
                self.getX() - half, self.getY(), self.getZ() - half,
                self.getX() + half, self.getY() + half * 2.0, self.getZ() + half
            ));
        }
    }
}
