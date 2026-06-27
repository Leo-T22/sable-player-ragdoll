package dev.leo.sableplayerragdoll;

import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class DeletingStick {
   private static final String MARKER_KEY = "sable_player_ragdoll_deleting_stick";

   private DeletingStick() {
   }

   public static ItemStack create() {
      ItemStack stack = new ItemStack(Items.STICK);
      CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(MARKER_KEY, true));
      stack.set(DataComponents.CUSTOM_NAME, Component.literal("Deleting Stick"));
      return stack;
   }

   public static boolean is(ItemStack stack) {
      return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).matchedBy(marker());
   }

   // Left-click: delete the whole ragdoll (any attached part), or just the limb if it has been severed.
   public static void delete(ServerLevel level, UUID clickedSubLevelId, ServerPlayer player) {
      boolean removed = RagdollAPI.remove(level, clickedSubLevelId, true);
      player.displayClientMessage(Component.literal(
         removed ? "Deleted ragdoll " + RagdollRegistry.shortId(clickedSubLevelId) : "Nothing to delete here"), true);
   }

   // Right-click: sever the clicked limb. It stays in the world as a free body until deleted.
   public static void dismember(ServerLevel level, UUID clickedSubLevelId, ServerPlayer player) {
      UUID limbId = RagdollAPI.dismember(level, clickedSubLevelId);
      player.displayClientMessage(Component.literal(
         limbId != null ? "Dismembered limb " + RagdollRegistry.shortId(limbId) : "Can't dismember that part"), true);
   }

   // Same as delete/dismember, for mob ragdolls (a separate sub-level system from player ragdolls).
   public static void deleteMob(ServerLevel level, UUID clickedSubLevelId, ServerPlayer player) {
      boolean removed = RagdollAPI.removeMobRagdoll(level, clickedSubLevelId, true);
      player.displayClientMessage(Component.literal(
         removed ? "Deleted mob ragdoll " + RagdollRegistry.shortId(clickedSubLevelId) : "Nothing to delete here"), true);
   }

   public static void dismemberMob(ServerLevel level, UUID clickedSubLevelId, ServerPlayer player) {
      UUID limbId = RagdollAPI.dismemberMob(level, clickedSubLevelId);
      player.displayClientMessage(Component.literal(
         limbId != null ? "Dismembered mob limb " + RagdollRegistry.shortId(limbId) : "Can't dismember that part"), true);
   }

   private static CompoundTag marker() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean(MARKER_KEY, true);
      return tag;
   }
}
