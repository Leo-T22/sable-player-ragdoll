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
   Map<String, List<ItemStack>> accessoriesItems
) {
   public RagdollEquipmentSnapshot {
      vanillaItems = copyVanilla(vanillaItems);
      curioItems = copySlotMap(curioItems);
      accessoriesItems = copySlotMap(accessoriesItems);
   }

   public static RagdollEquipmentSnapshot empty() {
      return new RagdollEquipmentSnapshot(Map.of(), Map.of(), Map.of());
   }

   public RagdollEquipmentSnapshot merge(RagdollEquipmentSnapshot other) {
      if (other == null) return this;

      EnumMap<EquipmentSlot, ItemStack> vanilla = new EnumMap<>(EquipmentSlot.class);
      vanilla.putAll(this.vanillaItems);
      vanilla.putAll(other.vanillaItems);

      Map<String, List<ItemStack>> curios = new LinkedHashMap<>();
      curios.putAll(this.curioItems);
      curios.putAll(other.curioItems);

      Map<String, List<ItemStack>> accessories = new LinkedHashMap<>();
      accessories.putAll(this.accessoriesItems);
      accessories.putAll(other.accessoriesItems);

      return new RagdollEquipmentSnapshot(vanilla, curios, accessories);
   }

   public boolean isEmpty() {
      return vanillaItems.isEmpty() && curioItems.isEmpty() && accessoriesItems.isEmpty();
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
      Map<String, List<ItemStack>> accessories = filterSlotMapLoose(accessoriesItems, available);
      return new RagdollEquipmentSnapshot(vanilla, curios, accessories);
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
