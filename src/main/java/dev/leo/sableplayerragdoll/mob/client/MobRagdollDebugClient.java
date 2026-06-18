package dev.leo.sableplayerragdoll.mob.client;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel;
import dev.leo.sableplayerragdoll.mob.model.RenderedModelExtractor;
import dev.leo.sableplayerragdoll.mob.item.MobRagdollItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = SablePlayerRagdoll.MOD_ID, value = Dist.CLIENT)
public final class MobRagdollDebugClient {
    private static int lastSentEntityId = -1;
    private static long lastSentGameTime = Long.MIN_VALUE;
    private static final long SEND_COOLDOWN_TICKS = 10;

    private MobRagdollDebugClient() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getEntity().getItemInHand(InteractionHand.MAIN_HAND).getItem() != MobRagdollItems.MOB_RAGDOLL_DEBUG_STICK.get()) {
            return;
        }
        inspect(event.getTarget());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getEntity().getItemInHand(InteractionHand.MAIN_HAND).getItem() != MobRagdollItems.MOB_RAGDOLL_DEBUG_STICK.get()) {
            return;
        }
        inspect(event.getTarget());
        event.setCanceled(true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void inspect(Entity target) {
        if (!(target instanceof LivingEntity livingEntity)) {
            return;
        }

        long gameTime = livingEntity.level().getGameTime();
        if (livingEntity.getId() == lastSentEntityId && gameTime - lastSentGameTime < SEND_COOLDOWN_TICKS) {
            return;
        }
        lastSentEntityId = livingEntity.getId();
        lastSentGameTime = gameTime;

        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderer<? super LivingEntity> renderer = (EntityRenderer<? super LivingEntity>) minecraft.getEntityRenderDispatcher().getRenderer(livingEntity);
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            return;
        }

        EntityModel<?> model = livingRenderer.getModel();
        model.young = livingEntity.isBaby();
        EntityModel rawModel = model;
        float bodyYaw = livingEntity.yBodyRot;
        float limbSwing = livingEntity.walkAnimation.position(0.0F);
        float limbSwingAmount = livingEntity.walkAnimation.speed(0.0F);
        float headYaw = livingEntity.getYHeadRot() - bodyYaw;
        float headPitch = livingEntity.getXRot();
        rawModel.prepareMobModel(livingEntity, limbSwing, limbSwingAmount, 0.0F);
        rawModel.setupAnim(livingEntity, limbSwing, limbSwingAmount, (float) livingEntity.tickCount, headYaw, headPitch);

        ExtractedMobModel extracted = RenderedModelExtractor.extract(model);
        long bodies = extracted.parts().stream().filter(part -> !part.attachment()).count();
        long attachments = extracted.parts().size() - bodies;

        SablePlayerRagdoll.LOGGER.info(
                "[mob-ragdoll-debug] {} renderer={} model={} parts={} bodies={} attachments={}",
                livingEntity.getType().builtInRegistryHolder().key().location(),
                renderer.getClass().getName(),
                extracted.sourceName(),
                extracted.parts().size(),
                bodies,
                attachments
        );

        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("Extracted " + extracted.parts().size() + " model parts: " + bodies + " bodies, " + attachments + " attachments")
                            .withStyle(ChatFormatting.AQUA),
                    false
            );
        }

        MobRagdollClientExtractor.extractAndSend(livingEntity);
    }
}
