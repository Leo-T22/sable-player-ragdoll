package dev.leo.sableplayerragdoll.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;


final class CuriosRenderHelper {

    private static final Map<String, Set<BodyPart>> SLOT_BODY_PARTS = Map.of(
        "head",     Set.of(BodyPart.HEAD),
        "necklace", Set.of(BodyPart.TORSO),
        "back",     Set.of(BodyPart.TORSO),
        "belt",     Set.of(BodyPart.TORSO),
        "charm",    Set.of(BodyPart.TORSO),
        "curio",    Set.of(BodyPart.TORSO),
        "ring",     Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM),
        "hands",    Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM),
        "feet",     Set.of(BodyPart.RIGHT_LEG)
    );

    private CuriosRenderHelper() {}

    private static boolean slotBelongsToPart(String slotId, BodyPart bodyPart) {
        Set<BodyPart> parts = SLOT_BODY_PARTS.get(slotId);
        // Unknown slots default to TORSO so they at least appear somewhere.
        return parts == null ? bodyPart == BodyPart.TORSO : parts.contains(bodyPart);
    }

    @SuppressWarnings("unchecked")
    static void render(
        BodyPart bodyPart,
        LivingEntity entity,
        RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> parent,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        float partialTick
    ) {
        var handler = entity.getCapability(CuriosCapability.INVENTORY);
        if (handler == null) return;

        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            String slotId = entry.getKey();
            if (!slotBelongsToPart(slotId, bodyPart)) continue;

            ICurioStacksHandler stacksHandler = entry.getValue();
            var stacks = stacksHandler.getStacks();
            var renders = stacksHandler.getRenders();

            for (int i = 0; i < stacks.getSlots(); i++) {
                if (!renders.get(i)) continue; // rendering disabled for this slot index

                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                SlotContext slotContext = new SlotContext(slotId, entity, i, false, true);

                CuriosRendererRegistry.getRenderer(stack.getItem()).ifPresent(renderer -> {
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        ICurioRenderer raw = renderer;
                        raw.render(
                            stack,
                            slotContext,
                            poseStack,
                            parent,
                            buffer,
                            packedLight,
                            partialTick,
                            0.0f, 0.0f, 0.0f, 0.0f, 0.0f
                        );
                    } catch (Exception e) {
                        // Swallow rendering errors for individual curio items to avoid crashing.
                    }
                });
            }
        }
    }
}
