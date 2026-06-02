package dev.leo.sableplayerragdoll.block;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

public final class RagdollSeatBlock extends Block {
   private static final VoxelShape RIDER_COLLISION = Block.box(3.0, 4.0, 3.0, 13.0, 38.0, 13.0);

   public RagdollSeatBlock(Properties properties) {
      super(properties);
   }

   public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
      super.fallOn(level, state, pos, entity, fallDistance * 0.5F);
   }

   public void updateEntityAfterFallOn(BlockGetter reader, Entity entity) {
      if (entity.isSuppressingBounce()) {
         super.updateEntityAfterFallOn(reader, entity);
      } else {
         Vec3 motion = entity.getDeltaMovement();
         if (motion.y < 0.0) {
            double bounceScale = entity instanceof LivingEntity ? 1.0 : 0.8;
            entity.setDeltaMovement(motion.x, -motion.y * 0.66F * bounceScale, motion.z);
         }
      }
   }

   public PathType getBlockPathType(BlockState state, BlockGetter world, BlockPos pos, @Nullable Mob entity) {
      return PathType.RAIL;
   }

   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return Shapes.empty();
   }

   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return RIDER_COLLISION;
   }

   protected ItemInteractionResult useItemOn(
      ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
   ) {
      if (!player.isShiftKeyDown() && !(player instanceof FakePlayer)) {
         List<RagdollSeatEntity> seats = level.getEntitiesOfClass(RagdollSeatEntity.class, new AABB(pos));
         if (!seats.isEmpty()) {
            RagdollSeatEntity seatEntity = seats.getFirst();
            List<Entity> passengers = seatEntity.getPassengers();
            if (!passengers.isEmpty() && passengers.getFirst() instanceof Player) {
               return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            } else {
               if (!level.isClientSide) {
                  seatEntity.ejectPassengers();
                  player.startRiding(seatEntity);
               }

               return ItemInteractionResult.SUCCESS;
            }
         } else if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
         } else {
            sitDown(level, pos, player);
            return ItemInteractionResult.SUCCESS;
         }
      } else {
         return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
      }
   }

   public static boolean isSeatOccupied(Level level, BlockPos pos) {
      return !level.getEntitiesOfClass(RagdollSeatEntity.class, new AABB(pos)).isEmpty();
   }

   public static void sitDown(Level level, BlockPos pos, Entity entity) {
      if (!level.isClientSide) {
         RagdollSeatEntity seat = new RagdollSeatEntity(level);
         seat.setPlotAnchor(pos);
         level.addFreshEntity(seat);
         entity.startRiding(seat, true);
         if (entity instanceof TamableAnimal tamable) {
            tamable.setInSittingPose(true);
         }
      }
   }

   protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
      return false;
   }
}
