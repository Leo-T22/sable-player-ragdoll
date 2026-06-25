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
        CurioSnapshot snapshot = captureSnapshot(player, true);
        snapshot.items().forEach(part::setCurioItems);
        snapshot.cosmeticItems().forEach(part::setCurioCosmeticItems);
        snapshot.renderOptions().forEach(part::setCurioRenderOptions);
    }

    static void applyFrom(ServerLevel level, UUID rootId, Player player) {
        CurioSnapshot snapshot = captureSnapshot(player, true);
        if (snapshot.isEmpty()) return;

        RagdollEquipmentHelper.applyToAllParts(level, rootId, be -> {
            snapshot.items().forEach(be::setCurioItems);
            snapshot.cosmeticItems().forEach(be::setCurioCosmeticItems);
            snapshot.renderOptions().forEach(be::setCurioRenderOptions);
        });
    }

    static Map<String, List<ItemStack>> capture(Player player) {
        return captureSnapshot(player, false).items();
    }

    static CurioSnapshot captureSnapshot(Player player) {
        return captureSnapshot(player, false);
    }

    private static CurioSnapshot captureSnapshot(Player player, boolean includeEmptySlots) {
        var handler = player.getCapability(CuriosCapability.INVENTORY);
        if (handler == null) return CurioSnapshot.empty();

        Map<String, List<ItemStack>> curioItems = new LinkedHashMap<>();
        Map<String, List<ItemStack>> curioCosmeticItems = new LinkedHashMap<>();
        Map<String, List<Boolean>> curioRenderOptions = new LinkedHashMap<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            ICurioStacksHandler stacksHandler = entry.getValue();
            List<ItemStack> items = items(stacksHandler.getStacks());
            List<ItemStack> cosmeticItems = items(stacksHandler.getCosmeticStacks());
            List<Boolean> renderOptions = List.copyOf(stacksHandler.getRenders());
            if (includeEmptySlots || hasAnyStack(items) || hasAnyStack(cosmeticItems)) {
                curioItems.put(entry.getKey(), items);
                curioCosmeticItems.put(entry.getKey(), cosmeticItems);
                curioRenderOptions.put(entry.getKey(), renderOptions);
            }
        }
        return new CurioSnapshot(curioItems, curioCosmeticItems, curioRenderOptions);
    }

    private static List<ItemStack> items(top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler stacks) {
        List<ItemStack> items = new ArrayList<>(stacks.getSlots());
        for (int i = 0; i < stacks.getSlots(); i++) {
            items.add(stacks.getStackInSlot(i).copy());
        }
        return items;
    }

    private static boolean hasAnyStack(List<ItemStack> items) {
        return items.stream().anyMatch(stack -> !stack.isEmpty());
    }

    record CurioSnapshot(Map<String, List<ItemStack>> items, Map<String, List<ItemStack>> cosmeticItems, Map<String, List<Boolean>> renderOptions) {
        static CurioSnapshot empty() {
            return new CurioSnapshot(Map.of(), Map.of(), Map.of());
        }

        boolean isEmpty() {
            return items.isEmpty() && cosmeticItems.isEmpty() && renderOptions.isEmpty();
        }
    }
}
