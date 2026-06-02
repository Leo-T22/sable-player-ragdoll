package dev.leo.sableplayerragdoll.block;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RagdollPartBlock extends Block implements EntityBlock, BlockSubLevelCollisionShape {
   public static final EnumProperty<BodyPart> BODY_PART = EnumProperty.create("body_part", BodyPart.class);
   private static final VoxelShape HEAD_SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
   private static final VoxelShape TORSO_SHAPE = Block.box(4.0, 2.0, 6.0, 12.0, 14.0, 10.0);
   private static final VoxelShape ARM_SHAPE = Block.box(5.0, 2.0, 6.0, 9.0, 14.0, 10.0);
   private static final VoxelShape LEG_SHAPE = Block.box(5.0, 2.0, 6.0, 9.0, 14.0, 10.0);

   public RagdollPartBlock(Properties properties) {
      super(properties);
      this.registerDefaultState(this.defaultBlockState().setValue(BODY_PART, BodyPart.TORSO));
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
      return partShape(state);
   }

   @Override
   protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return partShape(state);
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
      return partShape(state);
   }

   private static VoxelShape partShape(BlockState state) {
      return switch (state.getValue(BODY_PART)) {
         case HEAD -> HEAD_SHAPE;
         case LEFT_ARM, RIGHT_ARM -> ARM_SHAPE;
         case LEFT_LEG, RIGHT_LEG -> LEG_SHAPE;
         case TORSO -> TORSO_SHAPE;
      };
   }
}
