package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class ActiveSableCompanionBodyGuardMixin {

    @Inject(
        method = "getVelocity(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/companion/SubLevelAccess;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void sablePlayerRagdoll$guardRemovedSubLevel(
        Level level,
        SubLevelAccess subLevel,
        Vector3dc pos,
        Vector3d dest,
        CallbackInfoReturnable<Vector3d> cir
    ) {
        if (subLevel instanceof ServerSubLevel serverSubLevel && serverSubLevel.isRemoved()) {
            cir.setReturnValue(dest.zero());
        }
    }
}
