package dev.leo.sableplayerragdoll.neoforge;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.RagdollItemTags;
import dev.leo.sableplayerragdoll.RagdollSeatCallbacks;
import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.leo.sableplayerragdoll.SablePlayerRagdollBootstrap;
import dev.leo.sableplayerragdoll.api.PlayerlessRagdollSession;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.block.RagdollBlocks;
import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntities;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntities;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.neoforge.config.RagdollConfig;
import dev.leo.sableplayerragdoll.neoforge.network.RagdollNetworking;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import dev.leo.sableplayerragdoll.physics.RagdollDeferredSync;
import dev.leo.sableplayerragdoll.physics.RagdollExpireHelper;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import java.util.Collection;
import java.util.UUID;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@Mod("sable_player_ragdoll")
public final class SablePlayerRagdollNeoForge {
   public SablePlayerRagdollNeoForge(IEventBus modBus, ModContainer modContainer) {
      RagdollBlockRegistration.register(modBus);
      modBus.addListener(RagdollConfig::onLoad);
      modBus.addListener(RagdollConfig::onReload);
      RagdollConfig.register(modContainer);
      modBus.addListener(RagdollNetworking::register);
      modBus.addListener(SablePlayerRagdollNeoForge::onCommonSetup);
      modBus.addListener(SablePlayerRagdollNeoForge::registerAttributes);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onLevelTick);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onEntityMount);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onBlockPlaced);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onBlockBreak);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onLeftClickBlock);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onRightClickBlock);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onRightClickItem);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onEntityInteract);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onEntityInteractSpecific);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onAttackEntity);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onEquipmentChange);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onPlayerDeath);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onPlayerLogout);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onRegisterCommands);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onServerStarted);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onServerStopped);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onAddReloadListeners);
   }

   private static void onLevelTick(Post event) {
      if (event.getLevel() instanceof ServerLevel serverLevel) {
         RagdollSessionManager.tickActiveRagdolls(serverLevel);
         SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(serverLevel);
         if (physicsSystem != null) {
            RagdollDeferredSync.flushRemovals(physicsSystem);
         }
      }
   }

   @SuppressWarnings("unchecked")
   private static void onCommonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> {
         RagdollBlocks.bindSeat((Block) RagdollBlockRegistration.RAGDOLL_SEAT.get());
         RagdollBlocks.bindPart((Block) RagdollBlockRegistration.RAGDOLL_PART.get());
         RagdollPartBlockEntities.bindRagdollPart((BlockEntityType<RagdollPartBlockEntity>) RagdollBlockRegistration.RAGDOLL_PART_BLOCK_ENTITY.get());
         RagdollSeatEntities.bindRagdollSeat((EntityType<RagdollSeatEntity>) RagdollBlockRegistration.RAGDOLL_SEAT_ENTITY.get());
         RagdollSeatEntities.bindRagdollDoll((EntityType<RagdollDollEntity>) RagdollBlockRegistration.RAGDOLL_DOLL_ENTITY.get());
         RagdollSeatCallbacks.setOnAutoSeated(RagdollNetworking::notifyAutoSeated);
         RagdollSeatCallbacks.setOnReleased(RagdollNetworking::notifyReleased);
         SablePlayerRagdollBootstrap.init();
      });
   }

   private static void onEntityMount(EntityMountEvent event) {
      if (!event.isDismounting() || !(event.getEntityMounting() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
      ServerSubLevel ragdoll = RagdollSessionManager.activeRagdollForPlayer(level, player.getUUID());
      if (ragdoll == null || RagdollSessionManager.isExpiring(ragdoll)) return;
      event.setCanceled(true);
      if (RagdollSessionManager.canManualDismount(level, ragdoll)) {
         SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
         if (physicsSystem != null) {
            RagdollExpireHelper.expire(physicsSystem, level, ragdoll, "manual dismount");
         }
      }
   }

   private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
      if (!(event.getLevel() instanceof ServerLevel level)) return;
      if (event.getEntity() instanceof ServerPlayer player && isRagdolled(level, player)) {
         event.setCanceled(true);
      }
   }

   private static boolean isInRagdollPlot(ServerLevel level, BlockPos pos) {
      SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
      return subLevel != null && RagdollAssemblyHelper.isRagdollPart(subLevel.getUniqueId());
   }

   private static void onBlockBreak(BlockEvent.BreakEvent event) {
      if (event.getState().getBlock() instanceof RagdollPartBlock) {
         event.setCanceled(true);
         return;
      }
      if (event.getPlayer() instanceof ServerPlayer player && isRagdolled(player.serverLevel(), player)) {
         event.setCanceled(true);
      }
   }

   private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
      if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof RagdollPartBlock) {
         event.setCanceled(true);
         return;
      }
      if (isRagdolled(event)) event.setCanceled(true);
   }

   private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (isRagdolled(event)) {
         event.setCancellationResult(InteractionResult.FAIL);
         event.setCanceled(true);
         return;
      }
      if (!(event.getLevel() instanceof ServerLevel level)) return;
      BlockPos target = event.getPos().relative(event.getFace());
      if (isInRagdollPlot(level, target) || isInRagdollPlot(level, event.getPos())) {
         event.setCancellationResult(InteractionResult.FAIL);
         event.setCanceled(true);
      }
   }

   private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
      if (isRagdolled(event)) {
         event.setCancellationResult(InteractionResult.FAIL);
         event.setCanceled(true);
         return;
      }
      if (!(event.getLevel() instanceof ServerLevel level)) return;
      ItemStack held = event.getEntity().getItemInHand(event.getHand());
      if (!(held.getItem() instanceof BucketItem) || held.getItem() == Items.BUCKET) return;
      HitResult hit = event.getEntity().pick(5.0, 0.0f, true);
      if (hit instanceof BlockHitResult blockHit) {
         BlockPos target = blockHit.getBlockPos().relative(blockHit.getDirection());
         if (isInRagdollPlot(level, target) || isInRagdollPlot(level, blockHit.getBlockPos())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
         }
      }
   }

   private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
      if (isRagdolled(event)) {
         event.setCancellationResult(InteractionResult.FAIL);
         event.setCanceled(true);
      }
   }

   private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
      if (isRagdolled(event)) {
         event.setCancellationResult(InteractionResult.FAIL);
         event.setCanceled(true);
      }
   }

   private static void onAttackEntity(AttackEntityEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer attacker) || !(event.getTarget() instanceof ServerPlayer target)) {
         return;
      }
      if (!RagdollItemTags.canRagdollOnHit(attacker.getMainHandItem())) {
         return;
      }

      RagdollRegistry.triggerWeaponHit(attacker, target);
   }

   private static boolean isRagdolled(PlayerInteractEvent event) {
      return event.getEntity() instanceof ServerPlayer player && isRagdolled(player.serverLevel(), player);
   }

   private static boolean isRagdolled(ServerLevel level, ServerPlayer player) {
      ServerSubLevel ragdoll = RagdollSessionManager.activeRagdollForPlayer(level, player.getUUID());
      return ragdoll != null && RagdollAssemblyHelper.linkedTorso(ragdoll.getUniqueId()) != null;
   }

   private static void onRegisterCommands(RegisterCommandsEvent event) {
      event.getDispatcher().register(
         Commands.literal("sable_player_ragdoll")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("dummy")
               .executes(context -> spawnDummyRagdoll(
                  context.getSource(),
                  context.getSource().getPosition(),
                  context.getSource().getRotation().y
               ))
               .then(despawnOptions(context -> spawnDummyRagdoll(
                  context.getSource(),
                  context.getSource().getPosition(),
                  context.getSource().getRotation().y,
                  context.rule()
               )))
               .then(Commands.literal("profile")
                  .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                     .executes(context -> spawnDummyRagdoll(
                        context.getSource(),
                        context.getSource().getPosition(),
                        context.getSource().getRotation().y,
                        firstProfile(GameProfileArgument.getGameProfiles(context, "profile"))
                     ))
                     .then(despawnOptions(context -> spawnDummyRagdoll(
                        context.getSource(),
                        context.getSource().getPosition(),
                        context.getSource().getRotation().y,
                        firstProfile(GameProfileArgument.getGameProfiles(context.context(), "profile")),
                        context.rule()
                     )))
                     .then(Commands.literal("elytra")
                        .executes(context -> spawnDummyRagdoll(
                           context.getSource(),
                           context.getSource().getPosition(),
                           context.getSource().getRotation().y,
                           firstProfile(GameProfileArgument.getGameProfiles(context, "profile")),
                           true
                        ))
                     )
                     .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("heading", DoubleArgumentType.doubleArg())
                           .executes(context -> spawnDummyRagdoll(
                              context.getSource(),
                              Vec3Argument.getVec3(context, "pos"),
                              DoubleArgumentType.getDouble(context, "heading"),
                              firstProfile(GameProfileArgument.getGameProfiles(context, "profile"))
                           ))
                           .then(despawnOptions(context -> spawnDummyRagdoll(
                              context.getSource(),
                              Vec3Argument.getVec3(context.context(), "pos"),
                              DoubleArgumentType.getDouble(context.context(), "heading"),
                              firstProfile(GameProfileArgument.getGameProfiles(context.context(), "profile")),
                              context.rule()
                           )))
                        )
                     )
                  )
               )
               .then(Commands.argument("pos", Vec3Argument.vec3())
                  .then(Commands.argument("heading", DoubleArgumentType.doubleArg())
                     .executes(context -> spawnDummyRagdoll(
                        context.getSource(),
                        Vec3Argument.getVec3(context, "pos"),
                        DoubleArgumentType.getDouble(context, "heading")
                     ))
                     .then(despawnOptions(context -> spawnDummyRagdoll(
                        context.getSource(),
                        Vec3Argument.getVec3(context.context(), "pos"),
                        DoubleArgumentType.getDouble(context.context(), "heading"),
                        context.rule()
                     )))
                  )
               )
            )
            .then(Commands.literal("test_stick")
               .executes(context -> giveRagdollTestStick(context.getSource())))
      );
   }

   private static int spawnDummyRagdoll(CommandSourceStack source, Vec3 position, double headingDegrees) {
      return spawnDummyRagdoll(source, position, headingDegrees, PlayerlessDespawnRule.defaultRule());
   }

   private static int spawnDummyRagdoll(CommandSourceStack source, Vec3 position, double headingDegrees, PlayerlessDespawnRule despawnRule) {
      PlayerlessRagdollSession session = RagdollAPI.spawnPlayerless(source.getLevel(), position, headingDegrees, Vec3.ZERO, despawnRule);
      return finishDummySpawn(source, session);
   }

   private static int spawnDummyRagdoll(CommandSourceStack source, Vec3 position, double headingDegrees, GameProfile profile) {
      return spawnDummyRagdoll(source, position, headingDegrees, profile, PlayerlessDespawnRule.defaultRule());
   }

   private static int spawnDummyRagdoll(CommandSourceStack source, Vec3 position, double headingDegrees, GameProfile profile, PlayerlessDespawnRule despawnRule) {
      GameProfile skinProfile = resolveSkinProfile(source, profile);
      PlayerlessRagdollSession session = RagdollAPI.spawnPlayerless(source.getLevel(), position, headingDegrees, skinProfile, Vec3.ZERO, despawnRule);
      return finishDummySpawn(source, session);
   }

   private static int spawnDummyRagdoll(CommandSourceStack source, Vec3 position, double headingDegrees, GameProfile profile, boolean withElytra) {
      GameProfile skinProfile = resolveSkinProfile(source, profile);
      PlayerlessRagdollSession session = RagdollAPI.spawnPlayerless(source.getLevel(), position, headingDegrees, skinProfile, Vec3.ZERO, PlayerlessDespawnRule.defaultRule());
      if (withElytra) equipTorso(source.getLevel(), session, new ItemStack(Items.ELYTRA));
      return finishDummySpawn(source, session);
   }

   private static void equipTorso(ServerLevel level, PlayerlessRagdollSession session, ItemStack stack) {
      if (session == null) return;
      UUID torsoId = RagdollAssemblyHelper.linkedTorso(session.id());
      if (torsoId == null) return;
      SubLevel torsoSubLevel = SubLevelContainer.getContainer(level).getSubLevel(torsoId);
      if (torsoSubLevel == null) return;
      if (torsoSubLevel.getLevel().getBlockEntity(torsoSubLevel.getPlot().getCenterBlock()) instanceof RagdollPartBlockEntity part) {
         part.setItemForSlot(EquipmentSlot.CHEST, stack);
      }
   }

   private static GameProfile firstProfile(Collection<GameProfile> profiles) {
      return profiles.iterator().next();
   }

   private static LiteralArgumentBuilder<CommandSourceStack> despawnOptions(DummyDespawnCommand command) {
      return Commands.literal("despawn")
         .then(Commands.literal("default")
            .executes(context -> command.run(new DummyDespawnContext(context, PlayerlessDespawnRule.defaultRule()))))
         .then(Commands.literal("never")
            .executes(context -> command.run(new DummyDespawnContext(context, PlayerlessDespawnRule.never()))))
         .then(Commands.literal("after_ticks")
            .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
               .executes(context -> command.run(new DummyDespawnContext(
                  context,
                  PlayerlessDespawnRule.afterTicks(IntegerArgumentType.getInteger(context, "ticks"))
               )))))
         .then(Commands.literal("below_speed")
            .then(Commands.argument("meters_per_second", DoubleArgumentType.doubleArg(0.0))
               .executes(context -> command.run(new DummyDespawnContext(
                  context,
                  PlayerlessDespawnRule.belowSpeed(DoubleArgumentType.getDouble(context, "meters_per_second"))
               )))));
   }

   private static GameProfile resolveSkinProfile(CommandSourceStack source, GameProfile profile) {
      if (!profile.getProperties().get("textures").isEmpty() || profile.getId() == null) {
         return profile;
      }

      try {
         ProfileResult result = source.getServer().getSessionService().fetchProfile(profile.getId(), true);
         if (result != null && !result.profile().getProperties().get("textures").isEmpty()) {
            return result.profile();
         }
      } catch (Throwable error) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] failed to resolve skin for {}: {}", profile.getName(), error.toString());
      }

      return profile;
   }

   private static int finishDummySpawn(CommandSourceStack source, PlayerlessRagdollSession session) {
      if (session == null) {
         source.sendFailure(Component.literal("Failed to spawn playerless ragdoll."));
         return 0;
      }

      String shortId = RagdollRegistry.shortId(session.id());
      source.sendSuccess(() -> Component.literal("Spawned playerless ragdoll " + shortId), true);
      return 1;
   }

   private static int giveRagdollTestStick(CommandSourceStack source) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      ItemStack stack = new ItemStack(Items.STICK);
      RagdollItemTags.markTestItem(stack);
      stack.set(DataComponents.CUSTOM_NAME, Component.literal("Ragdoll Test Stick"));

      if (!player.getInventory().add(stack)) {
         player.drop(stack, false);
      }
      source.sendSuccess(() -> Component.literal("Gave ragdoll test stick"), true);
      return 1;
   }

   @FunctionalInterface
   private interface DummyDespawnCommand {
      int run(DummyDespawnContext context) throws CommandSyntaxException;
   }

   private record DummyDespawnContext(CommandContext<CommandSourceStack> context, PlayerlessDespawnRule rule) {

      private CommandSourceStack getSource() {
         return this.context.getSource();
      }
   }

   @SuppressWarnings("unchecked")
   private static void registerAttributes(EntityAttributeCreationEvent event) {
      event.put((EntityType<RagdollDollEntity>) RagdollBlockRegistration.RAGDOLL_DOLL_ENTITY.get(), RagdollDollEntity.createAttributes().build());
   }

   private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      ServerLevel level = player.serverLevel();
      ServerSubLevel ragdoll = RagdollSessionManager.activeRagdollForPlayer(level, player.getUUID());
      if (ragdoll == null) return;
      for (UUID partId : RagdollAssemblyHelper.linkedParts(ragdoll.getUniqueId())) {
         SubLevel partSubLevel = SubLevelContainer.getContainer(level).getSubLevel(partId);
         if (partSubLevel == null) continue;
         BlockPos pos = partSubLevel.getPlot().getCenterBlock();
         if (partSubLevel.getLevel().getBlockEntity(pos) instanceof RagdollPartBlockEntity part) {
            part.setItemForSlot(event.getSlot(), event.getTo());
            part.getLevel().sendBlockUpdated(pos, part.getBlockState(), part.getBlockState(), 3);
         }
      }
   }

   private static void onPlayerDeath(LivingDeathEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      ServerLevel level = player.serverLevel();
      ServerSubLevel ragdoll = RagdollSessionManager.activeRagdollForPlayer(level, player.getUUID());
      if (ragdoll == null) return;
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return;
      RagdollExpireHelper.expireImmediate(physicsSystem, level, ragdoll, "player died");
   }

   private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player)) return;
      ServerLevel level = player.serverLevel();
      ServerSubLevel ragdoll = RagdollSessionManager.activeRagdollForPlayer(level, player.getUUID());
      if (ragdoll == null) return;
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) return;
      RagdollExpireHelper.expireImmediate(physicsSystem, level, ragdoll, "player disconnected", true);
   }

   private static void onServerStarted(ServerStartedEvent event) {
      RagdollConfig.applyBodyMasses();
   }

   private static void onAddReloadListeners(AddReloadListenerEvent event) {
      event.addListener(new SimplePreparableReloadListener<Object>() {
         @Override
         protected Object prepare(ResourceManager rm, ProfilerFiller p) {
            return null;
         }

         @Override
         protected void apply(Object result, ResourceManager rm, ProfilerFiller p) {
            RagdollConfig.applyBodyMasses();
         }
      });
   }

   private static void onServerStopped(ServerStoppedEvent event) {
      RagdollRegistry.resetState();
   }
}
