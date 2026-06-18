package dev.leo.sableplayerragdoll.mob.block;

import dev.leo.sableplayerragdoll.RagdollCollisionRules;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MobRagdollPartBlock extends Block implements EntityBlock, BlockSubLevelCollisionShape {
    public static final IntegerProperty X_SIZE = IntegerProperty.create("x_size", 1, 16);
    public static final IntegerProperty Y_SIZE = IntegerProperty.create("y_size", 1, 16);
    public static final IntegerProperty Z_SIZE = IntegerProperty.create("z_size", 1, 16);

    public MobRagdollPartBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(X_SIZE, 8)
                .setValue(Y_SIZE, 8)
                .setValue(Z_SIZE, 8));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobRagdollPartBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level.getBlockEntity(pos) instanceof MobRagdollPartBlockEntity blockEntity) {
            return blockEntity.renderAnchor() ? blockEntity.visualShape() : Shapes.empty();
        }
        return shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (RagdollCollisionRules.suppressLocalCollision()) {
            return Shapes.empty();
        }
        return shape(state);
    }

    @Override
    public boolean hasDynamicShape() {
        return true;
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return shape(state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            InteractionResult result = level.getBlockEntity(pos) instanceof MobRagdollPartBlockEntity part
                    ? MobRagdollAssembly.interactWithPart(serverLevel, part, player, hand)
                    : MobRagdollAssembly.interactWithPart(serverLevel, pos, player, hand);
            if (result.consumesAction()) {
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel) {
            return level.getBlockEntity(pos) instanceof MobRagdollPartBlockEntity part
                    ? MobRagdollAssembly.interactWithPart(serverLevel, part, player, InteractionHand.MAIN_HAND)
                    : MobRagdollAssembly.interactWithPart(serverLevel, pos, player, InteractionHand.MAIN_HAND);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            if (level instanceof ServerLevel serverLevel) {
                if (level.getBlockEntity(pos) instanceof MobRagdollPartBlockEntity part) {
                    MobRagdollAssembly.attackPart(serverLevel, part, player);
                } else {
                    MobRagdollAssembly.attackPart(serverLevel, pos, player);
                }
            }
            MobRagdollAssembly.applyKnockupForPart(pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(X_SIZE, Y_SIZE, Z_SIZE));
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }

    private static VoxelShape shape(BlockState state) {
        double xSize = state.getValue(X_SIZE);
        double ySize = state.getValue(Y_SIZE);
        double zSize = state.getValue(Z_SIZE);
        double minX = (16.0 - xSize) * 0.5;
        double minY = (16.0 - ySize) * 0.5;
        double minZ = (16.0 - zSize) * 0.5;
        return Block.box(minX, minY, minZ, minX + xSize, minY + ySize, minZ + zSize);
    }
}
