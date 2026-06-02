package dev.leo.sableplayerragdoll.entity;

import net.minecraft.world.entity.EntityType;

public final class RagdollSeatEntities {
   private static EntityType<RagdollSeatEntity> ragdollSeat;
   private static EntityType<RagdollDollEntity> ragdollDoll;

   private RagdollSeatEntities() {
   }

   public static EntityType<RagdollSeatEntity> ragdollSeat() {
      return ragdollSeat;
   }

   public static EntityType<RagdollDollEntity> ragdollDoll() {
      return ragdollDoll;
   }

   public static void bindRagdollSeat(EntityType<RagdollSeatEntity> type) {
      ragdollSeat = type;
   }

   public static void bindRagdollDoll(EntityType<RagdollDollEntity> type) {
      ragdollDoll = type;
   }
}
