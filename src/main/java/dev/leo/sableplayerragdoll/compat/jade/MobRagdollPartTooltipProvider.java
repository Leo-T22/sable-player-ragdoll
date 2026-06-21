package dev.leo.sableplayerragdoll.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class MobRagdollPartTooltipProvider implements IBlockComponentProvider {
    public static final MobRagdollPartTooltipProvider INSTANCE = new MobRagdollPartTooltipProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "mob_ragdoll_part");

    private MobRagdollPartTooltipProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public void appendTooltip(final ITooltip tooltip, final BlockAccessor accessor, final IPluginConfig config) {
        final CompoundTag data = accessor.getServerData();
        if (!data.contains("EntityType")) {
            return;
        }

        tooltip.clear();

        final ResourceLocation entityType = ResourceLocation.tryParse(data.getString("EntityType"));
        if (entityType != null) {
            Component name = Component.translatable("entity." + entityType.getNamespace() + "." + entityType.getPath());
            if (data.getBoolean("Baby")) {
                name = Component.literal("Baby ").append(name);
            }
            tooltip.add(name);
        }

        final String role = data.getString("Role");
        if (!role.isBlank() && !"other".equals(role)) {
            tooltip.add(Component.literal(RagdollPartTooltipProvider.formatPartName(role)));
        }

        RagdollPartTooltipProvider.appendStats(tooltip, data);
    }
}
