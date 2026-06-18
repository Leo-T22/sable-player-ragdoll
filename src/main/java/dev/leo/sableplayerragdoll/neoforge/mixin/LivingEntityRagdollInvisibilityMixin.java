package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.mob.MobRagdollSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityRagdollInvisibilityMixin {
   @Inject(method = "updateInvisibilityStatus", at = @At("TAIL"))
   private void splrmob$keepRagdollSourceInvisible(CallbackInfo ci) {
      LivingEntity self = (LivingEntity) (Object) this;
      if (!(self.level() instanceof ServerLevel level)) {
         return;
      }
      if (MobRagdollAssembly.isConverted(self.getUUID())
            || MobRagdollSavedData.get(level).getEntry(self.getUUID()) != null) {
         self.setInvisible(true);
      }
   }
}
