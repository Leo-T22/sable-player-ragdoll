package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityRagdollDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void sablePlayerRagdoll$suppressRagdollSuffocation(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callbackInfo) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (source.is(DamageTypes.IN_WALL) && sablePlayerRagdoll$isHiddenRagdollSource(entity)) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void splrmob$freezeRagdolledMovement(Vec3 travelVector, CallbackInfo callbackInfo) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (MobRagdollAssembly.isConverted(entity.getUUID())) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void splrmob$hideRagdolledEntityFromPushChecks(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (sablePlayerRagdoll$isHiddenRagdollSource((Entity) (Object) this)) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void splrmob$stopRagdolledEntityPushingNeighbors(CallbackInfo callbackInfo) {
        if (sablePlayerRagdoll$isHiddenRagdollSource((Entity) (Object) this)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
    private void splrmob$stopHiddenRagdollPushPair(Entity entity, CallbackInfo callbackInfo) {
        if (sablePlayerRagdoll$isHiddenRagdollSource((Entity) (Object) this) || sablePlayerRagdoll$isHiddenRagdollSource(entity)) {
            callbackInfo.cancel();
        }
    }

    private static boolean sablePlayerRagdoll$isHiddenRagdollSource(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof ServerPlayer serverPlayer && RagdollSessionManager.isPlayerCurrentlyRagdolled(serverPlayer)) {
            return true;
        }
        return MobRagdollAssembly.isConverted(entity.getUUID());
    }
}
