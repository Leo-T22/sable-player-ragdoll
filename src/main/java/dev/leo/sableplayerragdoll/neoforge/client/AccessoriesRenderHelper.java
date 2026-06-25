package dev.leo.sableplayerragdoll.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotTypeReference;
import io.wispforest.accessories.menu.ArmorSlotTypes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

final class AccessoriesRenderHelper {

    private static final Set<String> SPLIT_ARM_SLOTS = Set.of("hand", "ring");

    private static final Map<String, Set<BodyPart>> SLOT_BODY_PARTS = Map.ofEntries(
        Map.entry("hat",      Set.of(BodyPart.HEAD)),
        Map.entry("face",     Set.of(BodyPart.HEAD)),
        Map.entry("necklace", Set.of(BodyPart.TORSO)),
        Map.entry("cape",     Set.of(BodyPart.TORSO)),
        Map.entry("back",     Set.of(BodyPart.TORSO)),
        Map.entry("belt",     Set.of(BodyPart.TORSO)),
        Map.entry("charm",    Set.of(BodyPart.TORSO)),
        Map.entry("shoes",    Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        Map.entry("anklet",   Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        Map.entry("wrist",    Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM))
    );

    //f irst match wins.
    private record KeywordRule(String keyword, Set<BodyPart> parts) {}
    private static final List<KeywordRule> SLOT_KEYWORDS = List.of(
        // Head
        new KeywordRule("head",    Set.of(BodyPart.HEAD)),
        new KeywordRule("hat",     Set.of(BodyPart.HEAD)),
        new KeywordRule("helmet",  Set.of(BodyPart.HEAD)),
        new KeywordRule("hair",    Set.of(BodyPart.HEAD)),
        new KeywordRule("mask",    Set.of(BodyPart.HEAD)),
        new KeywordRule("face",    Set.of(BodyPart.HEAD)),
        new KeywordRule("goggle",  Set.of(BodyPart.HEAD)),
        new KeywordRule("glass",   Set.of(BodyPart.HEAD)),
        // Arms
        new KeywordRule("hand",    Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        new KeywordRule("glove",   Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        new KeywordRule("gauntlet",Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        new KeywordRule("ring",    Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        new KeywordRule("bracelet",Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        new KeywordRule("wrist",   Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        new KeywordRule("bracer",  Set.of(BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM)),
        // Legs / feet
        new KeywordRule("boot",    Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("shoe",    Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("sock",    Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("feet",    Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("foot",    Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("anklet",  Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("ankle",   Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("leg",     Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        new KeywordRule("shin",    Set.of(BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG)),
        // Torso
        new KeywordRule("back",    Set.of(BodyPart.TORSO)),
        new KeywordRule("pack",    Set.of(BodyPart.TORSO)),
        new KeywordRule("bag",     Set.of(BodyPart.TORSO)),
        new KeywordRule("satchel", Set.of(BodyPart.TORSO)),
        new KeywordRule("pouch",   Set.of(BodyPart.TORSO)),
        new KeywordRule("tank",    Set.of(BodyPart.TORSO)),
        new KeywordRule("cape",    Set.of(BodyPart.TORSO)),
        new KeywordRule("cloak",   Set.of(BodyPart.TORSO)),
        new KeywordRule("wing",    Set.of(BodyPart.TORSO)),
        new KeywordRule("elytra",  Set.of(BodyPart.TORSO)),
        new KeywordRule("chest",   Set.of(BodyPart.TORSO)),
        new KeywordRule("body",    Set.of(BodyPart.TORSO)),
        new KeywordRule("torso",   Set.of(BodyPart.TORSO)),
        new KeywordRule("neck",    Set.of(BodyPart.TORSO)),
        new KeywordRule("collar",  Set.of(BodyPart.TORSO)),
        new KeywordRule("necklace",Set.of(BodyPart.TORSO)),
        new KeywordRule("pendant", Set.of(BodyPart.TORSO)),
        new KeywordRule("belt",    Set.of(BodyPart.TORSO)),
        new KeywordRule("waist",   Set.of(BodyPart.TORSO))
    );

    private AccessoriesRenderHelper() {}

    @Nullable
    static ItemStack storedCosmeticArmorOverride(RagdollPartBlockEntity blockEntity, EquipmentSlot slot) {
        SlotTypeReference reference = ArmorSlotTypes.getReferenceFromSlot(slot);
        if (reference == null) return null;
        String slotName = reference.slotName();
        if (!storedShouldRender(blockEntity, slotName, 0)) return ItemStack.EMPTY;
        List<ItemStack> cosmeticItems = blockEntity.getAccessoriesCosmeticItems().get(slotName);
        if (cosmeticItems == null || cosmeticItems.isEmpty()) return null;
        ItemStack item = cosmeticItems.get(0);
        return item.isEmpty() ? null : item;
    }

    @Nullable
    static ItemStack cosmeticArmorOverride(LivingEntity entity, EquipmentSlot slot) {
        AccessoriesCapability cap = AccessoriesCapability.get(entity);
        if (cap == null) return null;

        SlotTypeReference reference = ArmorSlotTypes.getReferenceFromSlot(slot);
        if (reference == null) return null;

        AccessoriesContainer container = cap.getContainer(reference);
        if (container == null) return null;

        if (!container.shouldRender(0)) return ItemStack.EMPTY;

        ItemStack cosmetic = container.getCosmeticAccessories().getItem(0);
        return cosmetic.isEmpty() ? null : cosmetic;
    }

    private static Set<BodyPart> resolveSlot(String slotName) {
        Set<BodyPart> exact = SLOT_BODY_PARTS.get(slotName);
        if (exact != null) return exact;
        String lower = slotName.toLowerCase();
        for (KeywordRule rule : SLOT_KEYWORDS) {
            if (lower.contains(rule.keyword())) return rule.parts();
        }
        return Set.of(BodyPart.TORSO); // unknown → torso fallback
    }

    private static boolean slotIndexBelongsToPart(String slotName, int index, BodyPart bodyPart) {
        if (SPLIT_ARM_SLOTS.contains(slotName)) {
            return bodyPart == ((index % 2 == 0) ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM);
        }
        return resolveSlot(slotName).contains(bodyPart);
    }

    @Nullable
    private static ModelPart oppositeLimb(BodyPart bodyPart, PlayerModel<?> model) {
        return switch (bodyPart) {
            case LEFT_LEG  -> model.rightLeg;
            case RIGHT_LEG -> model.leftLeg;
            case LEFT_ARM  -> model.rightArm;
            case RIGHT_ARM -> model.leftArm;
            default -> null;
        };
    }

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

        for (String slotName : storedSlotNames(blockEntity)) {
            if (ArmorSlotTypes.isArmorType(slotName)) continue;
            List<ItemStack> stacks = blockEntity.getAccessoriesItems().getOrDefault(slotName, List.of());
            List<ItemStack> cosmetics = blockEntity.getAccessoriesCosmeticItems().getOrDefault(slotName, List.of());
            int slots = Math.max(stacks.size(), cosmetics.size());
            for (int i = 0; i < slots; i++) {
                if (!slotIndexBelongsToPart(slotName, i, bodyPart)) continue;
                if (!storedShouldRender(blockEntity, slotName, i)) continue;
                ItemStack stack = storedEffectiveStack(cosmetics, i, storedStack(stacks, i));
                if (stack.isEmpty()) continue;
                AccessoryRenderer renderer = AccessoriesRendererRegistry.getRenderer(stack);
                if (renderer.isEmpty() || !renderer.shouldRender(true)) continue;

                float offLimbY = 0.0f;
                if (offLimb != null) {
                    offLimbY = offLimb.y;
                    offLimb.y += 10000.0f;
                }
                StoredSlotState slotState = mirrorStoredStack(entity, slotName, i, stack);
                SlotReference ref = slotState != null ? slotState.container().createReference(i) : SlotReference.of(entity, slotName, i);
                try {
                    renderer.render(stack, ref, poseStack, model, buffer, packedLight,
                        0.0f, 0.0f, partialTick, 0.0f, 0.0f, 0.0f);
                } catch (Exception e) {
                    // Swallow rendering errors for individual accessories.
                } finally {
                    if (slotState != null) slotState.restore();
                    if (offLimb != null) offLimb.y = offLimbY;
                }
            }
        }
    }

    private static Set<String> storedSlotNames(RagdollPartBlockEntity blockEntity) {
        Set<String> slotNames = new LinkedHashSet<>();
        slotNames.addAll(blockEntity.getAccessoriesItems().keySet());
        slotNames.addAll(blockEntity.getAccessoriesCosmeticItems().keySet());
        return slotNames;
    }

    @Nullable
    private static StoredSlotState mirrorStoredStack(LivingEntity entity, String slotName, int index, ItemStack stack) {
        AccessoriesCapability cap = AccessoriesCapability.get(entity);
        if (cap == null) return null;

        AccessoriesContainer container = cap.getContainers().get(slotName);
        if (container == null || index < 0 || index >= container.getSize()) return null;

        ItemStack previous = container.getAccessories().getItem(index).copy();
        container.getAccessories().setItem(index, stack.copy());
        return new StoredSlotState(container, index, previous);
    }

    private record StoredSlotState(AccessoriesContainer container, int index, ItemStack previous) {
        void restore() {
            container.getAccessories().setItem(index, previous);
        }
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

    private static boolean storedShouldRender(RagdollPartBlockEntity blockEntity, String slotName, int index) {
        List<Boolean> options = blockEntity.getAccessoriesRenderOptions().get(slotName);
        return options == null || index >= options.size() || Boolean.TRUE.equals(options.get(index));
    }

    static void render(
        BodyPart bodyPart,
        LivingEntity entity,
        RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> parent,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        float partialTick
    ) {
        AccessoriesCapability cap = AccessoriesCapability.get(entity);
        if (cap == null) return;

        PlayerModel<RagdollDollEntity> model = parent.getModel();
        ModelPart offLimb = oppositeLimb(bodyPart, model);

        for (Map.Entry<String, ? extends AccessoriesContainer> entry : cap.getContainers().entrySet()) {
            String slotName = entry.getKey();
            // Armor containers are handled through the vanilla armor layers
            // via the cosmetic equipment override.
            if (ArmorSlotTypes.isArmorType(slotName)) continue;

            AccessoriesContainer container = entry.getValue();

            for (int i = 0; i < container.getSize(); i++) {
                if (!slotIndexBelongsToPart(slotName, i, bodyPart)) continue;

                ItemStack stack = container.getAccessories().getItem(i);
                ItemStack cosmetic = container.getCosmeticAccessories().getItem(i);
                if (!cosmetic.isEmpty()) stack = cosmetic;

                if (stack.isEmpty()) continue;

                AccessoryRenderer renderer = AccessoriesRendererRegistry.getRenderer(stack);
                if (renderer.isEmpty() || !renderer.shouldRender(container.shouldRender(i))) continue;

                float offLimbY = 0.0f;
                if (offLimb != null) {
                    offLimbY = offLimb.y;
                    offLimb.y += 10000.0f;
                }

                SlotReference ref = container.createReference(i);
                try {
                    renderer.render(
                        stack, ref, poseStack, model, buffer, packedLight,
                        0.0f, 0.0f, partialTick, 0.0f, 0.0f, 0.0f
                    );
                } catch (Exception e) {
                    // Swallow rendering errors for individual accessories.
                } finally {
                    if (offLimb != null) {
                        offLimb.y = offLimbY;
                    }
                }
            }
        }
    }
}
