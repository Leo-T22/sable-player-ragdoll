package dev.leo.sableplayerragdoll.compat.jade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.impl.ui.ArmorElement;
import snownee.jade.impl.ui.HealthElement;

public final class RagdollPartTooltipProvider implements IBlockComponentProvider {
    public static final RagdollPartTooltipProvider INSTANCE = new RagdollPartTooltipProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_part");

    private RagdollPartTooltipProvider() {
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
        if (!data.contains("BodyPart")) {
            return;
        }

        tooltip.clear();

        final String skinName = data.getString("SkinName");
        if (!skinName.isBlank() && !"Player".equals(skinName)) {
            tooltip.add(Component.literal(skinName));
        }
        tooltip.add(Component.literal(formatPartName(data.getString("BodyPart"))));

        appendStats(tooltip, data);
    }

    static void appendStats(final ITooltip tooltip, final CompoundTag data) {
        final List<IElement> stats = new ArrayList<>();
        if (data.contains("MaxHealth")) {
            final float max = data.getFloat("MaxHealth");
            if (max > 0) {
                final float health = data.getFloat("Health");
                stats.add(new HealthElement(max, health).tag(
                        ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_health")));
            }
        }
        if (data.contains("Armor")) {
            final float armor = data.getFloat("Armor");
            if (armor > 0) {
                stats.add(new ArmorElement(armor).tag(
                        ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_armor")));
            }
        }
        if (!stats.isEmpty()) {
            tooltip.add(stats);
        }
    }

    static String formatPartName(final String serializedName) {
        final StringBuilder sb = new StringBuilder();
        for (final String word : serializedName.split("_")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
