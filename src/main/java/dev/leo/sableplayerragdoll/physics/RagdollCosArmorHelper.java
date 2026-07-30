package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import lain.mods.cos.impl.ModObjects;
import lain.mods.cos.impl.inventory.InventoryCosArmor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;

// All uses of the hash map with use "main" as the only key
// because I would rather keep constancy than use cleaner code
// as that would be annoying for the other contributors.
final class RagdollCosArmorHelper {
   private RagdollCosArmorHelper() {
   }

   static void applyToPart(RagdollPartBlockEntity part, Player player) {
      CosArmorSnapshot snapshot = captureSnapshot(player, true);
      snapshot.items().forEach(part::setCosmeticArmorItems);
      snapshot.renderOptions().forEach(part::setCosmeticArmorRenderOptions);
   }

   static void applyFrom(ServerLevel level, UUID rootId, Player player) {
      CosArmorSnapshot snapshot = captureSnapshot(player, true);
      RagdollEquipmentHelper.applyToAllParts(level, rootId, be -> {
         snapshot.items().forEach(be::setCosmeticArmorItems);
         snapshot.renderOptions().forEach(be::setCosmeticArmorRenderOptions);
      });
   }

   static Map<String, List<ItemStack>> capture(Player player) {
      return captureSnapshot(player, false).items();
   }

   static CosArmorSnapshot captureSnapshot(Player player) {
      return captureSnapshot(player, false);
   }

   private static CosArmorSnapshot captureSnapshot(Player player, boolean includeEmptySlots) {
      var inv = ModObjects.invMan.getCosArmorInventory(player.getUUID());
      if (inv == null) return CosArmorSnapshot.empty();

      Map<String, List<ItemStack>> accessoriesItems = new LinkedHashMap<>();
      Map<String, List<Boolean>> accessoriesRenderOptions = new LinkedHashMap<>();

      List<ItemStack> items = items(inv, 4);
      List<Boolean> renderOptions = renderOptions(inv);
      if (includeEmptySlots || hasAnyStack(items)) {
         accessoriesItems.put("main", items);
         accessoriesRenderOptions.put("main", renderOptions);
      }

      return new CosArmorSnapshot(accessoriesItems, accessoriesRenderOptions);
   }

   private static List<ItemStack> items(InventoryCosArmor container, int size) {
      List<ItemStack> items = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
         items.add(container.getItem(i).copy());
      }
      return items;
   }

   private static List<Boolean> renderOptions(InventoryCosArmor container) {
      List<Boolean> options = new ArrayList<>(container.getSlots());
      for (int i = 0; i < container.getSlots(); i++) {
         options.add(container.isSkinArmor(i));
      }
      return options;
   }

   private static boolean hasAnyStack(List<ItemStack> items) {
      return items.stream().anyMatch(stack -> !stack.isEmpty());
   }

   static long cosmeticArmorSignature(Player player) {
      long hash = 1L;
      CosArmorSnapshot snapshot = captureSnapshot(player, true);
      for (Map.Entry<String, List<ItemStack>> entry : snapshot.items().entrySet()) {
         hash = 31L * hash + entry.getKey().hashCode();
         List<ItemStack> items = entry.getValue();
         for (int i = 0; i < items.size(); i++) {
            hash = 31L * hash + i;
            hash = 31L * hash + stackSignature(items.get(i));
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

   record CosArmorSnapshot(Map<String, List<ItemStack>> items, Map<String, List<Boolean>> renderOptions) {
      static CosArmorSnapshot empty() {
         return new CosArmorSnapshot(Map.of(), Map.of());
      }
   }
}
