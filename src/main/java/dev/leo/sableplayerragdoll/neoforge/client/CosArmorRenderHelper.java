package dev.leo.sableplayerragdoll.neoforge.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import dev.leo.sableplayerragdoll.neoforge.mixin.HumanoidArmorLayerAccessor;
import dev.leo.sableplayerragdoll.neoforge.mixin.LivingEntityRendererAccessor;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotTypeReference;
import io.wispforest.accessories.menu.ArmorSlotTypes;
import lain.mods.cos.impl.InventoryManager;
import lain.mods.cos.impl.ModConfigs;
import lain.mods.cos.impl.ModObjects;
import lain.mods.cos.impl.client.InventoryManagerClient;
import lain.mods.cos.impl.client.PlayerRenderHandler;
import lain.mods.cos.impl.inventory.InventoryCosArmor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class CosArmorRenderHelper {

    private CosArmorRenderHelper() {}

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
        var inv = ModObjects.invMan.getCosArmorInventoryClient(entity.getUUID());
        if (inv == null) return null;

        if (inv.isSkinArmor(slot.getIndex())) return ItemStack.EMPTY;

        ItemStack cosmetic = inv.getItem(slot.getIndex());
        return cosmetic.isEmpty() ? null : cosmetic;
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

    private static <T extends RenderLayer<?, ?>> T getLayer(EntityRenderer<? extends Player> playerRenderer) {
        var accessor = (LivingEntityRendererAccessor)playerRenderer;
        for (var layer : accessor.getLayers()) {
            if (layer.getClass().isInstance(layer)) {
                return (T)layer.getClass().cast(layer);
            }
        }
        return null;
    }

    private static void renderSlot(
            int slot,
            BodyPart bodyPart,
            RagdollPartBlockEntity blockEntity,
            LivingEntity entity,
            RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> parent,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick,
            EntityRenderer<? extends Player> playerRenderer) {

        var equipmentSlot = switch (slot) {
            case 0 -> EquipmentSlot.FEET;
            case 1 -> EquipmentSlot.LEGS;
            case 2 -> EquipmentSlot.CHEST;
            case 3 -> EquipmentSlot.HEAD;
            default -> null;
        };

        if (equipmentSlot == null) return;

        var invCosArmor = blockEntity.getCosmeticArmorItems().get("main");

        if (blockEntity.getCosmeticArmorsRenderOptions().get("main").get(slot))
            return;

        HumanoidArmorLayer<?,?,?> layer = getLayer(playerRenderer);

        ItemStack cosStack = invCosArmor.get(slot);
        if (cosStack.isEmpty())
            return;

        ItemStack originalStack = entity.getItemBySlot(equipmentSlot);
        if (ItemStack.isSameItemSameComponents(originalStack, cosStack))
            return;

        try {
            ((Player)entity).getInventory().armor.set(slot, cosStack);
            if (layer instanceof HumanoidArmorLayerAccessor accessor) {
                accessor.renderPlayerArmor(poseStack, buffer, entity, equipmentSlot, packedLight, accessor.accessPlayerModel());
            }
        } finally {
            ((Player)entity).getInventory().armor.set(slot, originalStack);
        }
    }

    static void renderFromStored(
        BodyPart bodyPart,
        RagdollPartBlockEntity blockEntity,
        LivingEntity entity,
        RenderLayerParent<RagdollDollEntity, PlayerModel<RagdollDollEntity>> parent,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        float partialTick,
        EntityRenderer<? extends Player> playerRenderer
    ) {
        if (!ModConfigs.getCosArmorStackRendering())
            return;

        if (PlayerRenderHandler.Disabled)
            return;

        if (!(playerRenderer instanceof LivingEntityRendererAccessor))
            return;

        // I know this code is bad, I just dont know a better way to do it.
        switch (bodyPart) {
            case HEAD ->  {
                renderSlot(3, bodyPart, blockEntity, entity, parent, poseStack, buffer, packedLight, partialTick, playerRenderer);
            }
            case TORSO, LEFT_ARM, RIGHT_ARM ->  {
                renderSlot(2, bodyPart, blockEntity, entity, parent, poseStack, buffer, packedLight, partialTick, playerRenderer);
            }
            case LEFT_LEG, RIGHT_LEG -> {
                renderSlot(1, bodyPart, blockEntity, entity, parent, poseStack, buffer, packedLight, partialTick, playerRenderer);
                renderSlot(0, bodyPart, blockEntity, entity, parent, poseStack, buffer, packedLight, partialTick, playerRenderer);
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
}
