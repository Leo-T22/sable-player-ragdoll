package dev.leo.sableplayerragdoll.compat.jade;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public final class RagdollPartServerDataProvider implements IServerDataProvider<BlockAccessor> {
    public static final RagdollPartServerDataProvider INSTANCE = new RagdollPartServerDataProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_part_data");

    private RagdollPartServerDataProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(final CompoundTag data, final BlockAccessor accessor) {
        final BlockEntity be = resolveBlockEntity(accessor);
        if (!(be instanceof RagdollPartBlockEntity ragdoll)) {
            return;
        }
        final String name = ragdoll.skinProfile().getName();
        if (name != null) {
            data.putString("SkinName", name);
        }
        data.putString("BodyPart", ragdoll.bodyPart().getSerializedName());

        if (ragdoll.isCorpse()) {
            data.putBoolean("Corpse", true);
            return;
        }

        if (accessor.getLevel() instanceof ServerLevel serverLevel) {
            final ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(ragdoll.skinProfile().getId());
            if (player != null) {
                putLivingStats(data, player);
                return;
            }
        }

        data.putFloat("Health", 0f);
        data.putFloat("MaxHealth", ragdoll.maxHealth());
        int armor = 0;
        for (final EquipmentSlot slot : ARMOR_SLOTS) {
            final ItemStack stack = ragdoll.itemBySlot(slot);
            if (stack.getItem() instanceof ArmorItem armorItem) {
                armor += armorItem.getDefense();
            }
        }
        if (armor > 0) {
            data.putInt("Armor", armor);
        }
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    static void putLivingStats(final CompoundTag data, final LivingEntity living) {
        data.putFloat("Health", living.getHealth());
        data.putFloat("MaxHealth", living.getMaxHealth());
        final int armor = living.getArmorValue();
        if (armor > 0) {
            data.putInt("Armor", armor);
        }
    }

    static BlockEntity resolveBlockEntity(final BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (be != null) {
            return be;
        }
        final Level level = accessor.getLevel();
        final BlockPos pos = accessor.getPosition();
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        final LevelPlot plot = container.getPlot(new ChunkPos(pos));
        if (plot == null) {
            return null;
        }
        return plot.getEmbeddedLevelAccessor().getBlockEntity(pos);
    }
}
