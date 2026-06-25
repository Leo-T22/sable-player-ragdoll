package dev.leo.sableplayerragdoll.api;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public record RagdollEquipmentSnapshot(
   Map<EquipmentSlot, ItemStack> vanillaItems,
   Map<String, List<ItemStack>> curioItems,
   Map<String, List<ItemStack>> curioCosmeticItems,
   Map<String, List<Boolean>> curioRenderOptions,
   Map<String, List<ItemStack>> accessoriesItems,
   Map<String, List<ItemStack>> accessoriesCosmeticItems,
   Map<String, List<Boolean>> accessoriesRenderOptions
) {
   public RagdollEquipmentSnapshot(
      Map<EquipmentSlot, ItemStack> vanillaItems,
      Map<String, List<ItemStack>> curioItems,
      Map<String, List<ItemStack>> accessoriesItems
   ) {
      this(vanillaItems, curioItems, Map.of(), Map.of(), accessoriesItems, Map.of(), Map.of());
   }

   public RagdollEquipmentSnapshot {
      vanillaItems = copyVanilla(vanillaItems);
      curioItems = copySlotMap(curioItems);
      curioCosmeticItems = copySlotMap(curioCosmeticItems);
      curioRenderOptions = copyBooleanSlotMap(curioRenderOptions);
      accessoriesItems = copySlotMap(accessoriesItems);
      accessoriesCosmeticItems = copySlotMap(accessoriesCosmeticItems);
      accessoriesRenderOptions = copyBooleanSlotMap(accessoriesRenderOptions);
   }

   public static RagdollEquipmentSnapshot empty() {
      return new RagdollEquipmentSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
   }

   public RagdollEquipmentSnapshot merge(RagdollEquipmentSnapshot other) {
      if (other == null) return this;

      EnumMap<EquipmentSlot, ItemStack> vanilla = new EnumMap<>(EquipmentSlot.class);
      vanilla.putAll(this.vanillaItems);
      vanilla.putAll(other.vanillaItems);

      Map<String, List<ItemStack>> curios = new LinkedHashMap<>();
      curios.putAll(this.curioItems);
      curios.putAll(other.curioItems);

      Map<String, List<ItemStack>> curioCosmetics = new LinkedHashMap<>();
      curioCosmetics.putAll(this.curioCosmeticItems);
      curioCosmetics.putAll(other.curioCosmeticItems);

      Map<String, List<Boolean>> curioRenderOptions = new LinkedHashMap<>();
      curioRenderOptions.putAll(this.curioRenderOptions);
      curioRenderOptions.putAll(other.curioRenderOptions);

      Map<String, List<ItemStack>> accessories = new LinkedHashMap<>();
      accessories.putAll(this.accessoriesItems);
      accessories.putAll(other.accessoriesItems);

      Map<String, List<ItemStack>> accessoryCosmetics = new LinkedHashMap<>();
      accessoryCosmetics.putAll(this.accessoriesCosmeticItems);
      accessoryCosmetics.putAll(other.accessoriesCosmeticItems);

      Map<String, List<Boolean>> accessoryRenderOptions = new LinkedHashMap<>();
      accessoryRenderOptions.putAll(this.accessoriesRenderOptions);
      accessoryRenderOptions.putAll(other.accessoriesRenderOptions);

      return new RagdollEquipmentSnapshot(vanilla, curios, curioCosmetics, curioRenderOptions, accessories, accessoryCosmetics, accessoryRenderOptions);
   }

   public boolean isEmpty() {
      return vanillaItems.isEmpty()
         && curioItems.isEmpty()
         && curioCosmeticItems.isEmpty()
         && curioRenderOptions.isEmpty()
         && accessoriesItems.isEmpty()
         && accessoriesCosmeticItems.isEmpty()
         && accessoriesRenderOptions.isEmpty();
   }

   public RagdollEquipmentSnapshot filteredByAvailableItems(List<ItemStack> availableItems) {
      List<ItemStack> available = new ArrayList<>();
      if (availableItems != null) {
         for (ItemStack stack : availableItems) {
            if (!stack.isEmpty()) available.add(stack.copy());
         }
      }

      EnumMap<EquipmentSlot, ItemStack> vanilla = new EnumMap<>(EquipmentSlot.class);
      for (Map.Entry<EquipmentSlot, ItemStack> entry : vanillaItems.entrySet()) {
         ItemStack stack = entry.getValue();
         vanilla.put(entry.getKey(), !stack.isEmpty() && consumeExact(available, stack) ? stack : ItemStack.EMPTY);
      }

      Map<String, List<ItemStack>> curios = filterSlotMapLoose(curioItems, available);
      Map<String, List<ItemStack>> curioCosmetics = filterSlotMapLoose(curioCosmeticItems, available);
      Map<String, List<ItemStack>> accessories = filterSlotMapLoose(accessoriesItems, available);
      Map<String, List<ItemStack>> accessoryCosmetics = filterSlotMapLoose(accessoriesCosmeticItems, available);
      return new RagdollEquipmentSnapshot(vanilla, curios, curioCosmetics, curioRenderOptions, accessories, accessoryCosmetics, accessoriesRenderOptions);
   }

   private static Map<EquipmentSlot, ItemStack> copyVanilla(Map<EquipmentSlot, ItemStack> source) {
      EnumMap<EquipmentSlot, ItemStack> copy = new EnumMap<>(EquipmentSlot.class);
      if (source != null) {
         source.forEach((slot, stack) -> {
            if (slot != null && stack != null) copy.put(slot, stack.copy());
         });
      }
      return Map.copyOf(copy);
   }

   private static Map<String, List<ItemStack>> copySlotMap(Map<String, List<ItemStack>> source) {
      Map<String, List<ItemStack>> copy = new LinkedHashMap<>();
      if (source != null) {
         source.forEach((slot, stacks) -> {
            if (slot != null && stacks != null) {
               copy.put(slot, stacks.stream().map(ItemStack::copy).toList());
            }
         });
      }
      return Map.copyOf(copy);
   }

   private static Map<String, List<Boolean>> copyBooleanSlotMap(Map<String, List<Boolean>> source) {
      Map<String, List<Boolean>> copy = new LinkedHashMap<>();
      if (source != null) {
         source.forEach((slot, values) -> {
            if (slot != null && values != null) {
               copy.put(slot, List.copyOf(values));
            }
         });
      }
      return Map.copyOf(copy);
   }

   private static Map<String, List<ItemStack>> filterSlotMapLoose(Map<String, List<ItemStack>> slotMap, List<ItemStack> available) {
      Map<String, List<ItemStack>> result = new LinkedHashMap<>();
      for (Map.Entry<String, List<ItemStack>> entry : slotMap.entrySet()) {
         List<ItemStack> filtered = new ArrayList<>(entry.getValue().size());
         for (ItemStack stack : entry.getValue()) {
            filtered.add(!stack.isEmpty() && consumeLoose(available, stack) ? stack : ItemStack.EMPTY);
         }
         result.put(entry.getKey(), filtered);
      }
      return result;
   }

   private static boolean consumeExact(List<ItemStack> available, ItemStack target) {
      for (int i = 0; i < available.size(); i++) {
         ItemStack item = available.get(i);
         if (ItemStack.isSameItemSameComponents(item, target)) {
            consumeOne(available, i, item);
            return true;
         }
      }
      return false;
   }

   private static boolean consumeLoose(List<ItemStack> available, ItemStack target) {
      for (int i = 0; i < available.size(); i++) {
         ItemStack item = available.get(i);
         if (ItemStack.isSameItem(item, target)) {
            consumeOne(available, i, item);
            return true;
         }
      }
      return false;
   }

   private static void consumeOne(List<ItemStack> available, int index, ItemStack stack) {
      if (stack.getCount() > 1) {
         stack.shrink(1);
      } else {
         available.remove(index);
      }
   }
}
