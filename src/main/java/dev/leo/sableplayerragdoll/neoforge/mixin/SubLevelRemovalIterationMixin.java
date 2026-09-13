package dev.leo.sableplayerragdoll.neoforge.mixin;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import java.util.List;
import java.util.Iterator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//sable 2.0.3
@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class SubLevelRemovalIterationMixin {
    @Redirect(method = "processSubLevelRemovals", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<?> sablePlayerRagdoll$snapshotRemovals(List<?> subLevels) {
        return List.copyOf(subLevels).iterator();
    }
}
