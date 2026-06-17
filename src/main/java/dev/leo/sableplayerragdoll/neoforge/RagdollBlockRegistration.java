package dev.leo.sableplayerragdoll.neoforge;

import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.block.RagdollSeatBlock;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;

public final class RagdollBlockRegistration {
   public static final Blocks BLOCKS = DeferredRegister.createBlocks("sable_player_ragdoll");
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, "sable_player_ragdoll");
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "sable_player_ragdoll");
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, "sable_player_ragdoll");

   public static final DeferredBlock<RagdollSeatBlock> RAGDOLL_SEAT = BLOCKS.register(
      "ragdoll_seat", () -> new RagdollSeatBlock(Properties.of().mapColor(MapColor.NONE).strength(2.0F).noOcclusion().noLootTable().noTerrainParticles())
   );
   public static final DeferredBlock<RagdollPartBlock> RAGDOLL_PART = BLOCKS.register(
      "ragdoll_part", () -> new RagdollPartBlock(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).sound(SoundType.EMPTY).noOcclusion().noLootTable().noTerrainParticles())
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RagdollPartBlockEntity>> RAGDOLL_PART_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
      "ragdoll_part", () -> BlockEntityType.Builder.of(RagdollPartBlockEntity::new, RAGDOLL_PART.get()).build(null)
   );
   public static final DeferredHolder<EntityType<?>, EntityType<RagdollSeatEntity>> RAGDOLL_SEAT_ENTITY = ENTITY_TYPES.register(
      "ragdoll_seat",
      () -> Builder.<RagdollSeatEntity>of(RagdollSeatEntity::new, MobCategory.MISC)
            .sized(0.25F, 0.35F)
            .passengerAttachments(0.1F)
            .setShouldReceiveVelocityUpdates(false)
            .build("sable_player_ragdoll:ragdoll_seat")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<RagdollDollEntity>> RAGDOLL_DOLL_ENTITY = ENTITY_TYPES.register(
      "ragdoll_doll",
      () -> Builder.<RagdollDollEntity>of(RagdollDollEntity::new, MobCategory.MISC)
            .sized(0.6F, 1.8F)
            .clientTrackingRange(10)
            .updateInterval(3)
            .build("sable_player_ragdoll:ragdoll_doll")
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> RAGDOLL_IMPACT_SOUND = SOUND_EVENTS.register(
      "ragdoll_impact",
      () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_impact"))
   );
   public static final DeferredHolder<SoundEvent, SoundEvent> RAGDOLL_SMALL_IMPACT_SOUND = SOUND_EVENTS.register(
      "ragdoll_small_impact",
      () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_small_impact"))
   );

   private RagdollBlockRegistration() {
   }

   public static void register(IEventBus modBus) {
      BLOCKS.register(modBus);
      ENTITY_TYPES.register(modBus);
      BLOCK_ENTITY_TYPES.register(modBus);
      SOUND_EVENTS.register(modBus);
   }
}
