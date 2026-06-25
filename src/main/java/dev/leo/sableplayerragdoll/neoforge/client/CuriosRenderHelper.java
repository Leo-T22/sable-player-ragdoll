package dev.leo.sableplayerragdoll.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import java.util.LinkedHashSet;
import java.util.List;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void renderFromStored(
        BodyPart bodyPart,
        RagdollPartBlockEntity blockEntity,
        LivingEntity entity,
        RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> parent,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        float partialTick
    ) {
        for (String slotId : storedSlotIds(blockEntity)) {
            if (!slotBelongsToPart(slotId, bodyPart)) continue;

            List<ItemStack> stacks = blockEntity.getCurioItems().getOrDefault(slotId, List.of());
            List<ItemStack> cosmetics = blockEntity.getCurioCosmeticItems().getOrDefault(slotId, List.of());
            int slots = Math.max(stacks.size(), cosmetics.size());
            for (int i = 0; i < slots; i++) {
                if (!storedShouldRender(blockEntity, slotId, i)) continue;
                ItemStack stack = storedEffectiveStack(cosmetics, i, storedStack(stacks, i));
                if (stack.isEmpty()) continue;

                SlotContext slotContext = new SlotContext(slotId, entity, i, false, true);

                CuriosRendererRegistry.getRenderer(stack.getItem()).ifPresent(renderer -> {
                    try {
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
                        // Swallow rendering errors for individual curio items.
                    }
                });
            }
        }
    }

    private static Set<String> storedSlotIds(RagdollPartBlockEntity blockEntity) {
        Set<String> slotIds = new LinkedHashSet<>();
        slotIds.addAll(blockEntity.getCurioItems().keySet());
        slotIds.addAll(blockEntity.getCurioCosmeticItems().keySet());
        return slotIds;
    }

    private static ItemStack storedEffectiveStack(List<ItemStack> cosmetics, int index, ItemStack actual) {
        if (index < cosmetics.size()) {
            ItemStack cosmetic = cosmetics.get(index);
            if (!cosmetic.isEmpty()) return cosmetic;
        }
        return actual;
    }

    private static ItemStack storedStack(List<ItemStack> stacks, int index) {
        return index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
    }

    private static boolean storedShouldRender(RagdollPartBlockEntity blockEntity, String slotId, int index) {
        List<Boolean> options = blockEntity.getCurioRenderOptions().get(slotId);
        return options == null || index >= options.size() || Boolean.TRUE.equals(options.get(index));
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
            var cosmetics = stacksHandler.getCosmeticStacks();
            var renders = stacksHandler.getRenders();

            for (int i = 0; i < stacks.getSlots(); i++) {
                if (!renders.get(i)) continue; // rendering disabled for this slot index

                ItemStack cosmetic = i < cosmetics.getSlots() ? cosmetics.getStackInSlot(i) : ItemStack.EMPTY;
                ItemStack stack = !cosmetic.isEmpty() ? cosmetic : stacks.getStackInSlot(i);
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
