package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;

public final class RagdollSeatEntityRenderer extends EntityRenderer<RagdollSeatEntity> {
   public RagdollSeatEntityRenderer(Context context) {
      super(context);
   }

   @Override
   public boolean shouldRender(RagdollSeatEntity entity, Frustum frustum, double camX, double camY, double camZ) {
      return false;
   }

   @Override
   public ResourceLocation getTextureLocation(RagdollSeatEntity entity) {
      return null;
   }
}
