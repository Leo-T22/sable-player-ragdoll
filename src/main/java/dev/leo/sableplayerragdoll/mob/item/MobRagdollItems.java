package dev.leo.sableplayerragdoll.mob.item;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MobRagdollItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SablePlayerRagdoll.MOD_ID);

    public static final DeferredItem<MobRagdollDebugItem> MOB_RAGDOLL_DEBUG_STICK =
            ITEMS.register("mob_ragdoll_debug_stick", () -> new MobRagdollDebugItem(new Item.Properties().stacksTo(1)));

    private MobRagdollItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
