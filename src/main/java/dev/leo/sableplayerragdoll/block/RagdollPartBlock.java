package dev.leo.sableplayerragdoll.block;

import dev.leo.sableplayerragdoll.physics.RagdollBlockLifetime;
import dev.leo.sableplayerragdoll.physics.RagdollOwnedBlock;

import dev.leo.sableplayerragdoll.RagdollCollisionRules;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RagdollPartBlock extends Block implements EntityBlock, BlockSubLevelCollisionShape {
   public static final EnumProperty<BodyPart> BODY_PART = EnumProperty.create("body_part", BodyPart.class);
   private static final VoxelShape HEAD_OUTLINE_SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
   private static final VoxelShape TORSO_OUTLINE_SHAPE = Block.box(4.0, 2.0, 6.0, 12.0, 14.0, 10.0);
   private static final VoxelShape ARM_OUTLINE_SHAPE = Block.box(5.0, 2.0, 6.0, 9.0, 14.0, 10.0);
   private static final VoxelShape LEG_OUTLINE_SHAPE = Block.box(5.0, 2.0, 6.0, 9.0, 14.0, 10.0);

   private static final VoxelShape HEAD_COLLISION_SHAPE = Block.box(4.0, 6.0, 4.0, 12.0, 10.0, 12.0);
   private static final VoxelShape TORSO_COLLISION_SHAPE = Block.box(5.0, 3.0, 6.0, 11.0, 12.0, 10.0);
   private static final VoxelShape ARM_COLLISION_SHAPE = Block.box(6.0, 3.0, 6.0, 8.0, 13.0, 10.0);
   private static final VoxelShape LEG_COLLISION_SHAPE = Block.box(6.0, 3.0, 6.0, 8.0, 13.0, 10.0);

   public RagdollPartBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(this.defaultBlockState().setValue(BODY_PART, BodyPart.TORSO));
   }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (world, pos, blockState, be) -> {
            if (be instanceof RagdollOwnedBlock)
                RagdollBlockLifetime.tick(be);
        };
    }

   @Nullable
   @Override
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new RagdollPartBlockEntity(pos, state);
   }

   @Override
   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   @Override
   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return outlineShape(state);
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      if (RagdollCollisionRules.suppressLocalCollision()) {
         return Shapes.empty();
      }
      return collisionShape(state);
   }

   @Override
   public boolean hasDynamicShape() {
      return true;
   }

   @Override
   protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return Shapes.empty();
   }

   @Override
   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      super.createBlockStateDefinition(builder.add(BODY_PART));
   }

   @Override
   public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
      return collisionShape(state);
   }

   @Override
   public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
      return false;
   }

   @Override
   public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
      return false;
   }

   private static VoxelShape outlineShape(BlockState state) {
      return switch (state.getValue(BODY_PART)) {
         case HEAD -> HEAD_OUTLINE_SHAPE;
         case LEFT_ARM, RIGHT_ARM -> ARM_OUTLINE_SHAPE;
         case LEFT_LEG, RIGHT_LEG -> LEG_OUTLINE_SHAPE;
         case TORSO -> TORSO_OUTLINE_SHAPE;
      };
   }

   private static VoxelShape collisionShape(BlockState state) {
      return switch (state.getValue(BODY_PART)) {
         case HEAD -> HEAD_COLLISION_SHAPE;
         case LEFT_ARM, RIGHT_ARM -> ARM_COLLISION_SHAPE;
         case LEFT_LEG, RIGHT_LEG -> LEG_COLLISION_SHAPE;
         case TORSO -> TORSO_COLLISION_SHAPE;
      };
   }
}
