package dev.leo.sableplayerragdoll.neoforge.client;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
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
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
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

    // Modification of PlayerRenderHandler (from CosmeticArmorReworked)
    // Original source code: https://github.com/zzik2/CosmeticArmorReworkedForked/blob/master/common/src/main/java/lain/mods/cos/impl/client/PlayerRenderHandler.java
    //
    // Modified to run using ragdolls instead of Players
    public enum RagdollRenderHandler {;
        private final LoadingCache<Object, Deque<Runnable>> cache = CacheBuilder.newBuilder().weakKeys()
                .build(new CacheLoader<Object, Deque<Runnable>>() {

                    @Override
                    public Deque<Runnable> load(Object key) throws Exception {
                        return new ArrayDeque<>();
                    }

                });

        private final LoadingCache<Object, ItemStack[]> cosArmorCache = CacheBuilder.newBuilder().weakKeys()
                .build(new CacheLoader<Object, ItemStack[]>() {

                    @Override
                    public ItemStack[] load(Object key) throws Exception {
                        return new ItemStack[4];
                    }

                });

        // todo: implement isSkinArmor(slot) into cosmeticArmorsRenderOptions
        // Added armor param because I dont feel like getting the armor straight from the entity
        public void handlePreRenderPlayer(RagdollPartBlockEntity ragdoll, ItemStack armor) {
            Deque<Runnable> queue = cache.getUnchecked(ragdoll);
            restoreItems(queue);

            if (PlayerRenderHandler.Disabled)
                return;

            InventoryCosArmor invCosArmor = ModObjects.invMan.getCosArmorInventoryClient(ragdoll.());

            if (ModConfigs.getCosArmorStackRendering()) {
                ItemStack[] cosArmor = cosArmorCache.getUnchecked(ragdoll);
                for (int i = 0; i < armor.size(); i++) {
                    if (invCosArmor.isSkinArmor(i)) {
                        cosArmor[i] = null;
                    } else {
                        ItemStack cosStack = invCosArmor.getStackInSlot(i);
                        cosArmor[i] = cosStack.isEmpty() ? null : cosStack.copy();
                    }
                }
            } else {
                ItemStack stack;
                for (int i = 0; i < armor.size(); i++) {
                    if (invCosArmor.isSkinArmor(i))
                        armor.set(i, ItemStack.EMPTY);
                    else if (!(stack = invCosArmor.getStackInSlot(i)).isEmpty())
                        armor.set(i, stack);
                }
            }
        }

        public void handlePostRenderPlayer(Player player) {
            restoreItems(cache.getUnchecked(player));
        }

        public void handlePreRenderPlayerCanceled(Player player) {
            restoreItems(cache.getUnchecked(player));
        }

        public ItemStack[] getCosArmorForStackRendering(Player player) {
            if (!ModConfigs.getCosArmorStackRendering() || PlayerRenderHandler.Disabled)
                return null;
            return cosArmorCache.getUnchecked(player);
        }

        private void restoreItems(Deque<Runnable> queue) {
            Runnable runnable;
            while ((runnable = queue.poll()) != null) {
                try {
                    runnable.run();
                } catch (Throwable e) {
                    ModObjects.logger.error("Failed in restoring client player items", e);
                }
            }
        }

    }
}
