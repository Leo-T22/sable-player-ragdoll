package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

final class RagdollCuriosEquipmentHelper {
    private RagdollCuriosEquipmentHelper() {}

    static void applyToPart(RagdollPartBlockEntity part, Player player) {
        var handler = player.getCapability(CuriosCapability.INVENTORY);
        if (handler == null) return;
        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            var stacks = entry.getValue().getStacks();
            List<ItemStack> items = new ArrayList<>(stacks.getSlots());
            boolean hasItem = false;
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i).copy();
                items.add(stack);
                if (!stack.isEmpty()) hasItem = true;
            }
            if (hasItem) part.setCurioItems(entry.getKey(), items);
        }
    }

    static void applyFrom(ServerLevel level, UUID headId, Player player) {
        Map<String, List<ItemStack>> curioItems = capture(player);
        if (curioItems.isEmpty()) return;

        for (Map.Entry<String, List<ItemStack>> entry : curioItems.entrySet()) {
            String slotId = entry.getKey();
            List<ItemStack> items = entry.getValue();
            RagdollEquipmentHelper.applyToAllParts(level, headId, be -> be.setCurioItems(slotId, items));
        }
    }

    static Map<String, List<ItemStack>> capture(Player player) {
        var handler = player.getCapability(CuriosCapability.INVENTORY);
        if (handler == null) return Map.of();

        Map<String, List<ItemStack>> curioItems = new LinkedHashMap<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            var stacks = entry.getValue().getStacks();
            List<ItemStack> items = new ArrayList<>(stacks.getSlots());
            boolean hasItem = false;
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i).copy();
                items.add(stack);
                if (!stack.isEmpty()) hasItem = true;
            }
            if (hasItem) curioItems.put(entry.getKey(), items);
        }
        return curioItems;
    }
}
