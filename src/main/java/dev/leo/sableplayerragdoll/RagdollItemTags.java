package dev.leo.sableplayerragdoll;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class RagdollItemTags {
   private static final String TEST_MARKER_KEY = "sable_player_ragdoll_ragdoll_on_hit";
   private static final String TEST_MARKER_CRIT_KEY = "sable_player_ragdoll_critical_only";
   public static final TagKey<Item> RAGDOLL_ON_HIT = TagKey.create(
      Registries.ITEM,
      ResourceLocation.fromNamespaceAndPath(SablePlayerRagdoll.MOD_ID, "ragdoll_on_hit")
   );
   public static final TagKey<Item> RAGDOLL_ON_CRITICAL_HIT = TagKey.create(
      Registries.ITEM,
      ResourceLocation.fromNamespaceAndPath(SablePlayerRagdoll.MOD_ID, "ragdoll_on_critical_hit")
   );

   private RagdollItemTags() {
   }

   public static boolean canRagdollOnHit(ItemStack stack) {
      return stack.is(RAGDOLL_ON_HIT)
         || stack.is(RAGDOLL_ON_CRITICAL_HIT)
         || stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).matchedBy(testMarker());
   }

   public static boolean requiresCriticalHit(ItemStack stack) {
      return stack.is(RAGDOLL_ON_CRITICAL_HIT)
         || stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).matchedBy(critMarker());
   }

   public static void markTestItem(ItemStack stack) {
      CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TEST_MARKER_KEY, true));
   }

   public static void markTestItemCritOnly(ItemStack stack) {
      CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
         tag.putBoolean(TEST_MARKER_KEY, true);
         tag.putBoolean(TEST_MARKER_CRIT_KEY, true);
      });
   }

   private static CompoundTag testMarker() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean(TEST_MARKER_KEY, true);
      return tag;
   }

   private static CompoundTag critMarker() {
      CompoundTag tag = new CompoundTag();
      tag.putBoolean(TEST_MARKER_CRIT_KEY, true);
      return tag;
   }
}
