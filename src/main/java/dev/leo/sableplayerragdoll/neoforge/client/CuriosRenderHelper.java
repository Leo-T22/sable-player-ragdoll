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
import net.minecraft.client.model.geom.ModelPart;
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

    private static final Map<String, Set<BodyPart>> SLOT_BODY_PARTS = Map.ofEntries(
        Map.entry("head",     Set.of(BodyPart.HEAD)),
        Map.entry("necklace", Set.of(BodyPart.TORSO)),
        Map.entry("back",     Set.of(BodyPart.TORSO)),
        Map.entry("belt",     Set.of(BodyPart.TORSO)),
        Map.entry("charm",    Set.of(BodyPart.TORSO)),
        Map.entry("curio",    Set.of(BodyPart.TORSO)),
        Map.entry("ring",     Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        Map.entry("hands",    Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        Map.entry("feet",     Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG))
    );

    /**
     * Slots where items should be distributed across left/right arm by index parity.
     * Even index -> RIGHT_ARM, odd index -> LEFT_ARM.
     */
    private static final Set<String> SPLIT_ARM_SLOTS = Set.of("ring", "hands");

    private CuriosRenderHelper() {}

    /**
     * Checks whether a given slot+index combination belongs to a body part.
     * For split-arm slots like ring/hands, distributes by index parity
     * (even -> RIGHT_ARM, odd -> LEFT_ARM) so each arm block entity
     * only renders its share of items.
     */
    private static boolean slotBelongsToPart(String slotId, int index, BodyPart bodyPart) {
        Set<BodyPart> parts = SLOT_BODY_PARTS.get(slotId);
        if (parts == null) return bodyPart == BodyPart.TORSO;
        if (!SPLIT_ARM_SLOTS.contains(slotId)) return parts.contains(bodyPart);
        // Split-arm slots: distribute across arms by index parity
        if (bodyPart == BodyPart.LEFT_ARM || bodyPart == BodyPart.RIGHT_ARM) {
            BodyPart expectedArm = (index % 2 == 0) ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
            return bodyPart == expectedArm;
        }
        return false;
    }

    /**
     * Returns the opposite limb model part for a given body part.
     * Used to temporarily hide it during rendering so that Curio renderers
     * that read model parts for positioning don't double-render on the wrong side.
     */
    private static ModelPart oppositeLimb(BodyPart bodyPart, PlayerModel<?> model) {
        return switch (bodyPart) {
            case LEFT_LEG  -> model.rightLeg;
            case RIGHT_LEG -> model.leftLeg;
            case LEFT_ARM  -> model.rightArm;
            case RIGHT_ARM -> model.leftArm;
            default -> null;
        };
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
        PlayerModel<RagdollDollEntity> model = parent.getModel();
        ModelPart offLimb = oppositeLimb(bodyPart, model);

        for (String slotId : storedSlotIds(blockEntity)) {
            List<ItemStack> stacks = blockEntity.getCurioItems().getOrDefault(slotId, List.of());
            List<ItemStack> cosmetics = blockEntity.getCurioCosmeticItems().getOrDefault(slotId, List.of());
            int slots = Math.max(stacks.size(), cosmetics.size());
            for (int i = 0; i < slots; i++) {
                if (!slotBelongsToPart(slotId, i, bodyPart)) continue;
                if (!storedShouldRender(blockEntity, slotId, i)) continue;
                ItemStack stack = storedEffectiveStack(cosmetics, i, storedStack(stacks, i));
                if (stack.isEmpty()) continue;

                SlotContext slotContext = new SlotContext(slotId, entity, i, false, true);

                float offLimbY = 0.0f;
                if (offLimb != null) {
                    offLimbY = offLimb.y;
                    offLimb.y += 10000.0f;
                }

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

                if (offLimb != null) {
                    offLimb.y = offLimbY;
                }
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

        PlayerModel<RagdollDollEntity> model = parent.getModel();
        ModelPart offLimb = oppositeLimb(bodyPart, model);

        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            String slotId = entry.getKey();
            ICurioStacksHandler stacksHandler = entry.getValue();
            var stacks = stacksHandler.getStacks();
            var cosmetics = stacksHandler.getCosmeticStacks();
            var renders = stacksHandler.getRenders();

            for (int i = 0; i < stacks.getSlots(); i++) {
                if (!slotBelongsToPart(slotId, i, bodyPart)) continue;
                if (!renders.get(i)) continue;

                ItemStack cosmetic = i < cosmetics.getSlots() ? cosmetics.getStackInSlot(i) : ItemStack.EMPTY;
                ItemStack stack = !cosmetic.isEmpty() ? cosmetic : stacks.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                SlotContext slotContext = new SlotContext(slotId, entity, i, false, true);

                float offLimbY = 0.0f;
                if (offLimb != null) {
                    offLimbY = offLimb.y;
                    offLimb.y += 10000.0f;
                }

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

                if (offLimb != null) {
                    offLimb.y = offLimbY;
                }
            }
        }
    }
}
