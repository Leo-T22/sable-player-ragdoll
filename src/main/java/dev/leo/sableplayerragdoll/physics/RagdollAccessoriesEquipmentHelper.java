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
      AccessorySnapshot snapshot = captureSnapshot(player, true);
      snapshot.items().forEach(part::setAccessoriesItems);
      snapshot.cosmeticItems().forEach(part::setAccessoriesCosmeticItems);
      snapshot.renderOptions().forEach(part::setAccessoriesRenderOptions);
   }

   static void applyFrom(ServerLevel level, UUID rootId, Player player) {
      AccessorySnapshot snapshot = captureSnapshot(player, true);
      RagdollEquipmentHelper.applyToAllParts(level, rootId, be -> {
         snapshot.items().forEach(be::setAccessoriesItems);
         snapshot.cosmeticItems().forEach(be::setAccessoriesCosmeticItems);
         snapshot.renderOptions().forEach(be::setAccessoriesRenderOptions);
      });
   }

   static Map<String, List<ItemStack>> capture(Player player) {
      return captureSnapshot(player, false).items();
   }

   static AccessorySnapshot captureSnapshot(Player player) {
      return captureSnapshot(player, false);
   }

   private static AccessorySnapshot captureSnapshot(Player player, boolean includeEmptySlots) {
      AccessoriesCapability cap = AccessoriesCapability.get(player);
      if (cap == null) return AccessorySnapshot.empty();

      Map<String, List<ItemStack>> accessoriesItems = new LinkedHashMap<>();
      Map<String, List<ItemStack>> accessoriesCosmeticItems = new LinkedHashMap<>();
      Map<String, List<Boolean>> accessoriesRenderOptions = new LinkedHashMap<>();
      for (Map.Entry<String, ? extends AccessoriesContainer> entry : cap.getContainers().entrySet()) {
         String slotName = entry.getKey();
         AccessoriesContainer container = entry.getValue();
         List<ItemStack> items = items(container.getAccessories(), container.getSize());
         List<ItemStack> cosmeticItems = items(container.getCosmeticAccessories(), container.getSize());
         List<Boolean> renderOptions = renderOptions(container);
         if (includeEmptySlots || hasAnyStack(items) || hasAnyStack(cosmeticItems)) {
            accessoriesItems.put(slotName, items);
            accessoriesCosmeticItems.put(slotName, cosmeticItems);
            accessoriesRenderOptions.put(slotName, renderOptions);
         }
      }
      return new AccessorySnapshot(accessoriesItems, accessoriesCosmeticItems, accessoriesRenderOptions);
   }

   private static List<ItemStack> items(net.minecraft.world.Container container, int size) {
      List<ItemStack> items = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
         items.add(container.getItem(i).copy());
      }
      return items;
   }

   private static List<Boolean> renderOptions(AccessoriesContainer container) {
      List<Boolean> options = new ArrayList<>(container.getSize());
      for (int i = 0; i < container.getSize(); i++) {
         options.add(container.shouldRender(i));
      }
      return options;
   }

   private static boolean hasAnyStack(List<ItemStack> items) {
      return items.stream().anyMatch(stack -> !stack.isEmpty());
   }

   static long accessoriesSignature(Player player) {
      long hash = 1L;
      AccessorySnapshot snapshot = captureSnapshot(player, true);
      for (Map.Entry<String, List<ItemStack>> entry : snapshot.items().entrySet()) {
         hash = 31L * hash + entry.getKey().hashCode();
         List<ItemStack> items = entry.getValue();
         for (int i = 0; i < items.size(); i++) {
            hash = 31L * hash + i;
            hash = 31L * hash + stackSignature(items.get(i));
            hash = 31L * hash + stackSignature(snapshot.cosmeticItems().getOrDefault(entry.getKey(), List.of()).size() > i ? snapshot.cosmeticItems().get(entry.getKey()).get(i) : ItemStack.EMPTY);
            hash = 31L * hash + Boolean.hashCode(snapshot.renderOptions().getOrDefault(entry.getKey(), List.of()).size() > i && snapshot.renderOptions().get(entry.getKey()).get(i));
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

   record AccessorySnapshot(Map<String, List<ItemStack>> items, Map<String, List<ItemStack>> cosmeticItems, Map<String, List<Boolean>> renderOptions) {
      static AccessorySnapshot empty() {
         return new AccessorySnapshot(Map.of(), Map.of(), Map.of());
      }
   }
}
