package dev.leo.sableplayerragdoll.physics;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.block.RagdollBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

public final class RagdollRemovalHelper {
   private RagdollRemovalHelper() {
   }

   public static void removeRagdollSubLevel(SubLevelPhysicsSystem physicsSystem, ServerSubLevel subLevel) {
      if (subLevel != null && !subLevel.isRemoved()) {
         ServerLevel level = physicsSystem.getLevel();
         RagdollRegistry.wakePhysicsBody(physicsSystem, subLevel);
         removeLinkedRagdollParts(physicsSystem, subLevel);
         clearRagdollBlocksInWorld(subLevel, level);
         SubLevelContainer container = SubLevelContainer.getContainer(level);
         if (container != null) {
            try {
               container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
               SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] removed ragdoll sublevel {}", RagdollRegistry.shortId(subLevel.getUniqueId()));
               return;
            } catch (Throwable var5) {
               SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] removeSubLevel failed for {}: {}", subLevel.getUniqueId(), var5.toString());
            }
         }
         subLevel.markRemoved();
         SablePlayerRagdoll.LOGGER.info("[sable_player_ragdoll] markRemoved fallback for ragdoll {}", RagdollRegistry.shortId(subLevel.getUniqueId()));
      }
   }

   private static void removeLinkedRagdollParts(SubLevelPhysicsSystem physicsSystem, ServerSubLevel rootSubLevel) {
      List<UUID> partIds = RagdollAssemblyHelper.consumeLinkedParts(rootSubLevel.getUniqueId());
      if (partIds.size() <= 1) return;

      ServerLevel level = physicsSystem.getLevel();
      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (container == null) return;

      for (UUID partId : partIds) {
         if (partId.equals(rootSubLevel.getUniqueId())) continue;
         if (container.getSubLevel(partId) instanceof ServerSubLevel partSubLevel && !partSubLevel.isRemoved()) {
            RagdollRegistry.wakePhysicsBody(physicsSystem, partSubLevel);
            clearRagdollBlocksInWorld(partSubLevel, level);
            try {
               container.removeSubLevel(partSubLevel, SubLevelRemovalReason.REMOVED);
            } catch (Throwable error) {
               SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] remove ragdoll part failed for {}: {}", partId, error.toString());
               partSubLevel.markRemoved();
            }
         }
      }
   }

   private static void clearRagdollBlocksInWorld(ServerSubLevel subLevel, ServerLevel level) {
      LevelPlot plot = subLevel.getPlot();
      if (plot != null) {
         clearWorldBlock(subLevel, level, plot.getCenterBlock());
      }
   }

   private static void clearWorldBlock(ServerSubLevel subLevel, ServerLevel level, BlockPos plotPos) {
      Vec3 worldCenter = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(plotPos));
      BlockPos worldPos = BlockPos.containing(worldCenter);
      BlockState worldState = level.getBlockState(worldPos);
      if (isRagdollBlock(worldState)) {
         level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), 3);
      }
   }

   private static boolean isRagdollBlock(BlockState state) {
      return state.is(RagdollBlocks.ragdollSeat()) || state.is(RagdollBlocks.ragdollPart()) || state.is(Blocks.GLASS);
   }
}
