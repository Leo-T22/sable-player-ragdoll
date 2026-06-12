package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.api.RagdollEquipmentScope;
import dev.leo.sableplayerragdoll.api.RagdollEquipmentSnapshot;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

final class RagdollEquipmentHelper {
   private RagdollEquipmentHelper() {
   }

   static void applyExtraEquipment(RagdollPartBlockEntity part, Player player) {
      if (ModList.get().isLoaded("curios")) {
         RagdollCuriosEquipmentHelper.applyToPart(part, player);
      }
      if (ModList.get().isLoaded("accessories")) {
         RagdollAccessoriesEquipmentHelper.applyToPart(part, player);
      }
   }

   static void applyFrom(ServerLevel level, UUID headId, Player player) {
      applySnapshot(level, headId, capture(player, RagdollEquipmentScope.ALL));
   }

   static void applyExtraFrom(ServerLevel level, UUID headId, Player player) {
      applySnapshot(level, headId, capture(player, RagdollEquipmentScope.OPTIONAL_MODS));
   }

   static RagdollEquipmentSnapshot capture(Player player, RagdollEquipmentScope scope) {
      RagdollEquipmentScope resolved = scope == null ? RagdollEquipmentScope.ALL : scope;
      Map<EquipmentSlot, ItemStack> vanillaItems = Map.of();
      Map<String, List<ItemStack>> curioItems = Map.of();
      Map<String, List<ItemStack>> accessoriesItems = Map.of();

      if (resolved == RagdollEquipmentScope.ALL || resolved == RagdollEquipmentScope.VANILLA) {
         EnumMap<EquipmentSlot, ItemStack> vanilla = new EnumMap<>(EquipmentSlot.class);
         for (EquipmentSlot slot : EquipmentSlot.values()) {
            vanilla.put(slot, player.getItemBySlot(slot).copy());
         }
         vanillaItems = vanilla;
      }

      if (resolved == RagdollEquipmentScope.ALL || resolved == RagdollEquipmentScope.OPTIONAL_MODS) {
         if (ModList.get().isLoaded("curios")) {
            curioItems = RagdollCuriosEquipmentHelper.capture(player);
         }
         if (ModList.get().isLoaded("accessories")) {
            accessoriesItems = RagdollAccessoriesEquipmentHelper.capture(player);
         }
      }

      return new RagdollEquipmentSnapshot(vanillaItems, curioItems, accessoriesItems);
   }

   static void applySnapshot(ServerLevel level, UUID headId, RagdollEquipmentSnapshot snapshot) {
      if (snapshot == null || snapshot.isEmpty()) return;

      applyToAllParts(level, headId, be -> {
         snapshot.vanillaItems().forEach(be::setItemForSlot);
         snapshot.curioItems().forEach(be::setCurioItems);
         snapshot.accessoriesItems().forEach(be::setAccessoriesItems);
      });
      sendPartUpdates(level, headId);
   }

   static void syncAccessoriesAndSend(ServerLevel level, UUID headId, Player player) {
      if (!ModList.get().isLoaded("accessories")) return;
      RagdollAccessoriesEquipmentHelper.applyFrom(level, headId, player);
      sendPartUpdates(level, headId);
   }

   static void applyToAllParts(ServerLevel level, UUID headId, Consumer<RagdollPartBlockEntity> action) {
      var container = SubLevelContainer.getContainer(level);
      for (UUID partId : RagdollAssemblyHelper.linkedParts(headId)) {
         SubLevel subLevel = container.getSubLevel(partId);
         if (subLevel == null || subLevel.getPlot() == null) continue;
         BlockPos center = subLevel.getPlot().getCenterBlock();
         if (subLevel.getLevel().getBlockEntity(center) instanceof RagdollPartBlockEntity part) {
            action.accept(part);
         }
      }
   }

   private static void sendPartUpdates(ServerLevel level, UUID headId) {
      var container = SubLevelContainer.getContainer(level);
      for (UUID partId : RagdollAssemblyHelper.linkedParts(headId)) {
         SubLevel subLevel = container.getSubLevel(partId);
         if (subLevel == null || subLevel.getPlot() == null) continue;
         BlockPos center = subLevel.getPlot().getCenterBlock();
         Level partLevel = subLevel.getLevel();
         partLevel.sendBlockUpdated(center, partLevel.getBlockState(center), partLevel.getBlockState(center), 3);
      }
   }
}
