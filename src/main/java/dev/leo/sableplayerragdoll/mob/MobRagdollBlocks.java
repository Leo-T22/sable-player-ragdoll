package dev.leo.sableplayerragdoll.mob;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.mob.block.MobRagdollPartBlock;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MobRagdollBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SablePlayerRagdoll.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, SablePlayerRagdoll.MOD_ID);

    public static final DeferredBlock<MobRagdollPartBlock> MOB_RAGDOLL_PART = BLOCKS.register(
            "mob_ragdoll_part",
            () -> new MobRagdollPartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(-1.0F, 0.0F)
                    .sound(SoundType.EMPTY)
                    .noOcclusion()
                    .noLootTable()
                    .noTerrainParticles())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MobRagdollPartBlockEntity>> MOB_RAGDOLL_PART_ENTITY = BLOCK_ENTITY_TYPES.register(
            "mob_ragdoll_part",
            () -> BlockEntityType.Builder.of(MobRagdollPartBlockEntity::new, MOB_RAGDOLL_PART.get()).build(null)
    );

    private MobRagdollBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
    }
}
