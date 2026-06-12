package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.menu.ArmorSlotTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class RagdollAccessoriesEquipmentHelper {
   private RagdollAccessoriesEquipmentHelper() {
   }

   static void applyToPart(RagdollPartBlockEntity part, Player player) {
      capture(player, true).forEach(part::setAccessoriesItems);
   }

   static void applyFrom(ServerLevel level, UUID headId, Player player) {
      Map<String, List<ItemStack>> items = capture(player, true);
      RagdollEquipmentHelper.applyToAllParts(level, headId, be -> items.forEach(be::setAccessoriesItems));
   }

   static Map<String, List<ItemStack>> capture(Player player) {
      return capture(player, false);
   }

   private static Map<String, List<ItemStack>> capture(Player player, boolean includeEmptySlots) {
      AccessoriesCapability cap = AccessoriesCapability.get(player);
      if (cap == null) return Map.of();

      Map<String, List<ItemStack>> accessoriesItems = new LinkedHashMap<>();
      for (Map.Entry<String, ? extends AccessoriesContainer> entry : cap.getContainers().entrySet()) {
         String slotName = entry.getKey();
         AccessoriesContainer container = entry.getValue();
         List<ItemStack> items = effectiveItems(slotName, container);
         if (includeEmptySlots || items.stream().anyMatch(stack -> !stack.isEmpty())) {
            accessoriesItems.put(slotName, items);
         }
      }
      return accessoriesItems;
   }

   private static List<ItemStack> effectiveItems(String slotName, AccessoriesContainer container) {
      if (ArmorSlotTypes.isArmorType(slotName)) {
         return List.of(container.getCosmeticAccessories().getItem(0).copy());
      }

      List<ItemStack> items = new ArrayList<>(container.getSize());
      for (int i = 0; i < container.getSize(); i++) {
         ItemStack actual = container.getAccessories().getItem(i);
         ItemStack cosmetic = container.getCosmeticAccessories().getItem(i);
         ItemStack effective = !cosmetic.isEmpty() ? cosmetic : actual;
         items.add(effective.copy());
      }
      return items;
   }

   static long accessoriesSignature(Player player) {
      long hash = 1L;
      for (Map.Entry<String, List<ItemStack>> entry : capture(player, true).entrySet()) {
         hash = 31L * hash + entry.getKey().hashCode();
         List<ItemStack> items = entry.getValue();
         for (int i = 0; i < items.size(); i++) {
            hash = 31L * hash + i;
            hash = 31L * hash + stackSignature(items.get(i));
         }
      }
      return hash;
   }

   private static long stackSignature(ItemStack stack) {
      if (stack.isEmpty()) return 0L;
      long hash = System.identityHashCode(stack.getItem());
      hash = 31L * hash + stack.getCount();
      hash = 31L * hash + stack.getComponents().hashCode();
      return hash;
   }
}
