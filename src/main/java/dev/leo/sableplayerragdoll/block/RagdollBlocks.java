package dev.leo.sableplayerragdoll.block;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RagdollBlocks {
   private static Block seatBlock;
   private static Block partBlock;

   private RagdollBlocks() {
   }

   public static Block ragdollSeat() {
      return seatBlock;
   }

   public static Block ragdollPart() {
      return partBlock;
   }

   public static BlockState ragdollSeatDefaultState() {
      return seatBlock.defaultBlockState();
   }

   public static BlockState ragdollPartDefaultState() {
      return partBlock.defaultBlockState();
   }

   public static void bindSeat(Block block) {
      seatBlock = block;
   }

   public static void bindPart(Block block) {
      partBlock = block;
   }
}
