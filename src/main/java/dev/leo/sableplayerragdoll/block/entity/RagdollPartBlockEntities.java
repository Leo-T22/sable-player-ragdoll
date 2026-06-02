package dev.leo.sableplayerragdoll.block.entity;

import net.minecraft.world.level.block.entity.BlockEntityType;

public final class RagdollPartBlockEntities {
   private static BlockEntityType<RagdollPartBlockEntity> ragdollPart;

   private RagdollPartBlockEntities() {
   }

   public static BlockEntityType<RagdollPartBlockEntity> ragdollPart() {
      return ragdollPart;
   }

   public static void bindRagdollPart(BlockEntityType<RagdollPartBlockEntity> type) {
      ragdollPart = type;
   }
}
