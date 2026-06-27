package dev.leo.sableplayerragdoll.neoforge;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.RagdollItemTags;
import dev.leo.sableplayerragdoll.DeletingStick;
import dev.leo.sableplayerragdoll.RagdollGrabCallbacks;
import dev.leo.sableplayerragdoll.RagdollPoseRequestCallbacks;
import dev.leo.sableplayerragdoll.RagdollSeatCallbacks;
import dev.leo.sableplayerragdoll.api.RagdollAsyncPoseRequests;
import dev.leo.sableplayerragdoll.RagdollSoundEvents;
import dev.leo.sableplayerragdoll.api.PlayerlessDespawnRule;
import dev.leo.sableplayerragdoll.SablePlayerRagdollBootstrap;
import dev.leo.sableplayerragdoll.api.PlayerlessRagdollSession;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollInteractEvent;
import dev.leo.sableplayerragdoll.api.RagdollWailingOptions;
import dev.leo.sableplayerragdoll.block.RagdollBlocks;
import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntities;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly;
import dev.leo.sableplayerragdoll.mob.MobRagdollBlacklistSavedData;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollLaunchOptions;
import dev.leo.sableplayerragdoll.mob.MobRagdollBlocks;
import dev.leo.sableplayerragdoll.mob.block.MobRagdollPartBlock;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.mob.network.MobRagdollNetworking;
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
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.effect.MobEffects;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

@Mod("sable_player_ragdoll")
public final class SablePlayerRagdollNeoForge {
   public SablePlayerRagdollNeoForge(IEventBus modBus, ModContainer modContainer) {
      RagdollBlockRegistration.register(modBus);
      modBus.addListener(RagdollConfig::onLoad);
      modBus.addListener(RagdollConfig::onReload);
      RagdollConfig.register(modContainer);
      modBus.addListener(RagdollNetworking::register);
      MobRagdollBlocks.register(modBus);
      modBus.addListener(MobRagdollNetworking::register);
      modBus.addListener(SablePlayerRagdollNeoForge::onCommonSetup);
      modBus.addListener(SablePlayerRagdollNeoForge::registerAttributes);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onLevelTick);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onServerTick);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onEntityMount);
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onEntityJoinLevel);
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
      NeoForge.EVENT_BUS.addListener(SablePlayerRagdollNeoForge::onProjectileImpact);
   }

   private static void onLevelTick(Post event) {
      if (event.getLevel() instanceof ServerLevel serverLevel) {
         RagdollSessionManager.tickActiveRagdolls(serverLevel);
         MobRagdollAssembly.tickActiveRagdolls(serverLevel);
         SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(serverLevel);
         if (physicsSystem != null) {
            RagdollDeferredSync.flushRemovals(physicsSystem);
         }
      }
   }

   private static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
      RagdollAsyncPoseRequests.tick(uuid -> event.getServer().getPlayerList().getPlayer(uuid));
   }

   @SuppressWarnings("unchecked")
   private static void onCommonSetup(FMLCommonSetupEvent event) {
      event.enqueueWork(() -> {
         RagdollBlocks.bindSeat((Block) RagdollBlockRegistration.RAGDOLL_SEAT.get());
         RagdollBlocks.bindPart((Block) RagdollBlockRegistration.RAGDOLL_PART.get());
         RagdollPartBlockEntities.bindRagdollPart((BlockEntityType<RagdollPartBlockEntity>) RagdollBlockRegistration.RAGDOLL_PART_BLOCK_ENTITY.get());
         RagdollSeatEntities.bindRagdollSeat((EntityType<RagdollSeatEntity>) RagdollBlockRegistration.RAGDOLL_SEAT_ENTITY.get());
         RagdollSeatEntities.bindRagdollDoll((EntityType<RagdollDollEntity>) RagdollBlockRegistration.RAGDOLL_DOLL_ENTITY.get());
         RagdollSoundEvents.bindRagdollImpact(RagdollBlockRegistration.RAGDOLL_IMPACT_SOUND.get());
         RagdollSoundEvents.bindRagdollSmallImpact(RagdollBlockRegistration.RAGDOLL_SMALL_IMPACT_SOUND.get());
         RagdollSeatCallbacks.setOnReleased(RagdollNetworking::notifyReleased);
         RagdollPoseRequestCallbacks.setOnRequestPose(RagdollNetworking::notifyRequestPose);
         RagdollGrabCallbacks.setOnGrabbed(RagdollNetworking::notifyGrabStarted);
         RagdollGrabCallbacks.setOnReleased(RagdollNetworking::notifyGrabEnded);
         SablePlayerRagdollBootstrap.init();
      });
   }

   private static void onEntityMount(EntityMountEvent event) {
      if (!(event.getLevel() instanceof ServerLevel level)) return;
      if (!event.isDismounting() && event.getEntityBeingMounted() instanceof LivingEntity mounted
         && MobRagdollAssembly.isPendingOrConverted(mounted.getUUID())) {
         event.setCanceled(true);
         return;
      }
      if (!event.isDismounting() || !(event.getEntityMounting() instanceof ServerPlayer player)) return;
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

   private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
      if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity living) {
         MobRagdollAssembly.hideLoadedRagdollSource(level, living);
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
      if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof MobRagdollPartBlock) {
         event.setCanceled(true);
         if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
               && event.getLevel() instanceof ServerLevel mobLevel) {
            if (event.getEntity() instanceof ServerPlayer attacker && DeletingStick.is(attacker.getMainHandItem())) {
               SubLevel subLevel = Sable.HELPER.getContaining(mobLevel, event.getPos());
               if (subLevel != null) DeletingStick.deleteMob(mobLevel, subLevel.getUniqueId(), attacker);
            } else if (mobLevel.getBlockEntity(event.getPos()) instanceof MobRagdollPartBlockEntity mobPart) {
               MobRagdollAssembly.attackPart(mobLevel, mobPart, event.getEntity());
            }
         }
         return;
      }
      if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof RagdollPartBlock) {
         event.setCanceled(true);
         if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
               && event.getLevel() instanceof ServerLevel level
               && event.getEntity() instanceof ServerPlayer attacker) {
            SubLevel subLevel = Sable.HELPER.getContaining(level, event.getPos());
            if (subLevel != null) {
               if (DeletingStick.is(attacker.getMainHandItem())) {
                  DeletingStick.delete(level, subLevel.getUniqueId(), attacker);
               } else {
                  UUID rootId = RagdollAssemblyHelper.linkedRoot(subLevel.getUniqueId());
                  if (rootId != null) pipeAttack(level, rootId, attacker);
               }
            }
         }
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
      if (event.getEntity() instanceof ServerPlayer mobClicker
            && DeletingStick.is(mobClicker.getItemInHand(event.getHand()))
            && level.getBlockState(event.getPos()).getBlock() instanceof MobRagdollPartBlock) {
         SubLevel sub = Sable.HELPER.getContaining(level, event.getPos());
         if (sub != null) DeletingStick.dismemberMob(level, sub.getUniqueId(), mobClicker);
         event.setCancellationResult(InteractionResult.SUCCESS);
         event.setCanceled(true);
         return;
      }
      BlockPos target = event.getPos().relative(event.getFace());
      boolean targetIsRagdoll = isInRagdollPlot(level, target);
      boolean posIsRagdoll = isInRagdollPlot(level, event.getPos());
      if (!targetIsRagdoll && !posIsRagdoll) return;
      if (event.getEntity() instanceof ServerPlayer player) {
         BlockPos ragdollPos = targetIsRagdoll ? target : event.getPos();
         SubLevel subLevel = Sable.HELPER.getContaining(level, ragdollPos);
         if (subLevel != null) {
            UUID rootId = RagdollAssemblyHelper.linkedRoot(subLevel.getUniqueId());
            if (rootId != null) {
               if (DeletingStick.is(player.getItemInHand(event.getHand()))) {
                  DeletingStick.dismember(level, subLevel.getUniqueId(), player);
                  event.setCancellationResult(InteractionResult.SUCCESS);
                  event.setCanceled(true);
                  return;
               }
               RagdollInteractEvent interactEvent = new RagdollInteractEvent(player, rootId, subLevel.getUniqueId(), ragdollPos, level);
               if (NeoForge.EVENT_BUS.post(interactEvent).isCanceled()) {
                  event.setCancellationResult(InteractionResult.SUCCESS);
                  event.setCanceled(true);
                  return;
               }
               InteractionResult piped = pipeInteract(level, rootId, player, event.getHand());
               event.setCancellationResult(piped != null ? piped : InteractionResult.FAIL);
               event.setCanceled(true);
               return;
            }
         }
      }
      event.setCancellationResult(InteractionResult.FAIL);
      event.setCanceled(true);
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
      if (!(event.getEntity() instanceof ServerPlayer attacker)) {
         return;
      }
      ItemStack weapon = attacker.getMainHandItem();
      if (!RagdollItemTags.canRagdollOnHit(weapon)) {
         return;
      }
      if (RagdollItemTags.requiresCriticalHit(weapon) && !isCriticalHit(attacker)) {
         return;
      }

      if (event.getTarget() instanceof ServerPlayer target) {
         RagdollRegistry.triggerWeaponHit(attacker, target);
      } else if (event.getTarget() instanceof LivingEntity mob && attacker.level() instanceof ServerLevel level) {
         Vec3 look = attacker.getLookAngle();
         Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
         Vec3 direction = horizontal.lengthSqr() > 1.0E-6 ? horizontal.normalize() : new Vec3(0.0, 0.0, 1.0);
         Vec3 linear = direction.scale(6.0).add(0.0, 8.0, 0.0);
         MobRagdollAssembly.requestLaunch(level, mob, linear, Vec3.ZERO,
            MobRagdollLaunchOptions.builder().durationTicks(200).build());
      }
   }

   @javax.annotation.Nullable
   private static InteractionResult pipeInteract(ServerLevel level, UUID rootId, ServerPlayer clickingPlayer, InteractionHand hand) {
      SubLevel rootSubLevel = SubLevelContainer.getContainer(level).getSubLevel(rootId);
      if (!(rootSubLevel instanceof ServerSubLevel serverRoot)) return null;
      UUID ragdollPlayerId = RagdollSessionManager.getPlayerId(serverRoot);
      if (ragdollPlayerId == null || ragdollPlayerId.equals(clickingPlayer.getUUID())) return null;
      Entity ragdolledEntity = level.getEntity(ragdollPlayerId);
      if (!(ragdolledEntity instanceof ServerPlayer ragdolledPlayer) || !ragdolledPlayer.isAlive()) return null;
      RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.set(true);
      try {
         return ragdolledPlayer.interact(clickingPlayer, hand);
      } finally {
         RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.set(false);
      }
   }

   private static void pipeAttack(ServerLevel level, UUID rootId, ServerPlayer attacker) {
      SubLevel rootSubLevel = SubLevelContainer.getContainer(level).getSubLevel(rootId);
      if (!(rootSubLevel instanceof ServerSubLevel serverRoot)) return;
      UUID ragdollPlayerId = RagdollSessionManager.getPlayerId(serverRoot);
      // A player riding their own ragdoll can have its parts directly under their crosshair — without this guard,
      // their own clicks pipe through as a self-attack (damage + hurt sound) on themselves.
      if (ragdollPlayerId == null || ragdollPlayerId.equals(attacker.getUUID())) return;
      Entity ragdolledEntity = level.getEntity(ragdollPlayerId);
      if (!(ragdolledEntity instanceof ServerPlayer ragdolledPlayer) || !ragdolledPlayer.isAlive()) return;
      RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.set(true);
      try {
         attacker.attack(ragdolledPlayer);
      } finally {
         RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.set(false);
      }
   }

   private static void onProjectileImpact(ProjectileImpactEvent event) {
      if (!(event.getProjectile().level() instanceof ServerLevel level)) return;
      if (!(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
      BlockPos hitPos = hit.getBlockPos();
      Block hitBlock = level.getBlockState(hitPos).getBlock();
      if (hitBlock instanceof MobRagdollPartBlock) {
         if (pipeMobProjectile(level, hitPos, event.getProjectile())) {
            event.setCanceled(true);
         }
         return;
      }
      if (!(hitBlock instanceof RagdollPartBlock)) return;

      SubLevel subLevel = Sable.HELPER.getContaining(level, hitPos);
      if (subLevel == null) return;
      UUID rootId = RagdollAssemblyHelper.linkedRoot(subLevel.getUniqueId());
      if (rootId == null) return;

      if (pipeProjectile(level, rootId, event.getProjectile())) {
         event.setCanceled(true);
      }
   }

   private static boolean pipeMobProjectile(ServerLevel level, BlockPos hitPos, Projectile projectile) {
      if (!(level.getBlockEntity(hitPos) instanceof MobRagdollPartBlockEntity part)) return false;
      UUID sourceId = part.sourceEntityId();
      if (sourceId == null) return false;
      Entity owner = projectile.getOwner();
      if (owner != null && sourceId.equals(owner.getUUID())) return false;
      Entity sourceEntity = level.getEntity(sourceId);
      if (!(sourceEntity instanceof LivingEntity living) || !living.isAlive()) return false;

      DamageSource source = projectileDamageSource(level, projectile);
      float damage = projectileDamageAmount(projectile);

      MobRagdollAssembly.RAGDOLL_PIPE_ACTIVE.set(true);
      try {
         boolean hurt = living.hurt(source, damage);
         if (hurt) {
            MobRagdollAssembly.applyKnockup(sourceId);
         }
         return hurt;
      } finally {
         MobRagdollAssembly.RAGDOLL_PIPE_ACTIVE.set(false);
      }
   }

   private static boolean pipeProjectile(ServerLevel level, UUID rootId, Projectile projectile) {
      SubLevel rootSubLevel = SubLevelContainer.getContainer(level).getSubLevel(rootId);
      if (!(rootSubLevel instanceof ServerSubLevel serverRoot)) return false;
      UUID ragdollPlayerId = RagdollSessionManager.getPlayerId(serverRoot);
      if (ragdollPlayerId == null) return false;
      Entity owner = projectile.getOwner();
      if (owner != null && ragdollPlayerId.equals(owner.getUUID())) return false;
      Entity ragdolledEntity = level.getEntity(ragdollPlayerId);
      if (!(ragdolledEntity instanceof ServerPlayer ragdolledPlayer) || !ragdolledPlayer.isAlive()) return false;

      DamageSource source = projectileDamageSource(level, projectile);
      float damage = projectileDamageAmount(projectile);

      RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.set(true);
      try {
         return ragdolledPlayer.hurt(source, damage);
      } finally {
         RagdollSessionManager.RAGDOLL_PIPE_ACTIVE.set(false);
      }
   }

   private static DamageSource projectileDamageSource(ServerLevel level, Projectile projectile) {
      Entity owner = projectile.getOwner();
      if (projectile instanceof ThrownTrident trident) return level.damageSources().trident(trident, owner);
      if (projectile instanceof AbstractArrow arrow) return level.damageSources().arrow(arrow, owner);
      if (projectile instanceof WitherSkull skull) return level.damageSources().witherSkull(skull, owner);
      if (projectile instanceof Fireball fireball) return level.damageSources().fireball(fireball, owner);
      LivingEntity livingOwner = owner instanceof LivingEntity le ? le : null;
      return level.damageSources().mobProjectile(projectile, livingOwner);
   }

   private static float projectileDamageAmount(Projectile projectile) {
      // AbstractArrow.getBaseDamage() is already velocity-scaled and covers ThrownTrident (8.0)
      if (projectile instanceof AbstractArrow arrow) return (float) arrow.getBaseDamage();
      if (projectile instanceof WitherSkull) return 8.0F;
      if (projectile instanceof LargeFireball) return 6.0F;
      if (projectile instanceof Fireball) return 5.0F; // SmallFireball, DragonFireball, etc.
      if (projectile instanceof ShulkerBullet) return 4.0F;
      return 0.0F; // snowball, egg, wind charge, etc. — no direct damage in vanilla
   }

   private static boolean isCriticalHit(ServerPlayer attacker) {
      return attacker.fallDistance > 0.0F
         && !attacker.onGround()
         && !attacker.onClimbable()
         && !attacker.isInWater()
         && !attacker.hasEffect(MobEffects.BLINDNESS)
         && !attacker.isPassenger()
         && !attacker.isSprinting();
   }

   private static boolean isRagdolled(PlayerInteractEvent event) {
      return event.getEntity() instanceof ServerPlayer player && isRagdolled(player.serverLevel(), player);
   }

   private static boolean isRagdolled(ServerLevel level, ServerPlayer player) {
      ServerSubLevel ragdoll = RagdollSessionManager.activeRagdollForPlayer(level, player.getUUID());
      return ragdoll != null; // root IS the torso
   }

   private static void onRegisterCommands(RegisterCommandsEvent event) {
      event.getDispatcher().register(RagdollCommand.build());
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
            .then(Commands.literal("mobless")
               .then(Commands.argument("entity", ResourceLocationArgument.id())
                  .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                  .executes(context -> spawnMoblessRagdoll(
                     context.getSource(),
                     ResourceLocationArgument.getId(context, "entity"),
                     context.getSource().getPosition()
                  ))
                  .then(Commands.argument("pos", Vec3Argument.vec3())
                     .executes(context -> spawnMoblessRagdoll(
                        context.getSource(),
                        ResourceLocationArgument.getId(context, "entity"),
                        Vec3Argument.getVec3(context, "pos")
                     )))))
            .then(Commands.literal("wailing")
               .then(Commands.literal("start")
                  .executes(context -> startWailing(context.getSource().getPlayerOrException(), 100, 15.0, 10))
                  .then(Commands.argument("duration_ticks", IntegerArgumentType.integer(1))
                     .executes(context -> startWailing(
                        context.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(context, "duration_ticks"),
                        15.0,
                        10
                     ))
                     .then(Commands.argument("stiffness", DoubleArgumentType.doubleArg(0.0))
                        .executes(context -> startWailing(
                           context.getSource().getPlayerOrException(),
                           IntegerArgumentType.getInteger(context, "duration_ticks"),
                           DoubleArgumentType.getDouble(context, "stiffness"),
                           10
                        ))
                        .then(Commands.argument("interval_ticks", IntegerArgumentType.integer(1))
                           .executes(context -> startWailing(
                              context.getSource().getPlayerOrException(),
                              IntegerArgumentType.getInteger(context, "duration_ticks"),
                              DoubleArgumentType.getDouble(context, "stiffness"),
                              IntegerArgumentType.getInteger(context, "interval_ticks")
                           ))
                           .then(Commands.argument("targets", EntityArgument.players())
                              .executes(context -> startWailing(
                                 context.getSource(),
                                 EntityArgument.getPlayers(context, "targets"),
                                 IntegerArgumentType.getInteger(context, "duration_ticks"),
                                 DoubleArgumentType.getDouble(context, "stiffness"),
                                 IntegerArgumentType.getInteger(context, "interval_ticks")
                              ))
                           )
                        )
                     )
                  )
               )
               .then(Commands.literal("stop")
                  .executes(context -> stopWailing(context.getSource().getPlayerOrException()))
                  .then(Commands.argument("targets", EntityArgument.players())
                     .executes(context -> stopWailing(context.getSource(), EntityArgument.getPlayers(context, "targets"))))
               )
            )
            .then(Commands.literal("mob_blacklist")
               .then(Commands.literal("list")
                  .executes(context -> listMobRagdollBlacklist(context.getSource())))
               .then(Commands.literal("clear")
                  .executes(context -> clearMobRagdollBlacklist(context.getSource())))
               .then(Commands.literal("add")
                  .then(Commands.literal("entity")
                     .then(Commands.argument("entity", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                        .executes(context -> addMobRagdollBlacklistEntity(
                           context.getSource(),
                           ResourceLocationArgument.getId(context, "entity")
                        ))))
                  .then(Commands.literal("mod")
                     .then(Commands.argument("namespace", StringArgumentType.word())
                        .suggests(SablePlayerRagdollNeoForge::suggestEntityNamespaces)
                        .executes(context -> addMobRagdollBlacklistNamespace(
                           context.getSource(),
                           StringArgumentType.getString(context, "namespace")
                        )))))
               .then(Commands.literal("remove")
                  .then(Commands.literal("entity")
                     .then(Commands.argument("entity", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                        .executes(context -> removeMobRagdollBlacklistEntity(
                           context.getSource(),
                           ResourceLocationArgument.getId(context, "entity")
                        ))))
                  .then(Commands.literal("mod")
                     .then(Commands.argument("namespace", StringArgumentType.word())
                        .suggests(SablePlayerRagdollNeoForge::suggestEntityNamespaces)
                        .executes(context -> removeMobRagdollBlacklistNamespace(
                           context.getSource(),
                           StringArgumentType.getString(context, "namespace")
                        ))))))
            .then(Commands.literal("ragdolling_stick")
               .executes(context -> giveRagdollingStick(context.getSource())))
            .then(Commands.literal("deleting_stick")
               .executes(context -> giveDeletingStick(context.getSource())))
      );
   }

   private static int addMobRagdollBlacklistEntity(CommandSourceStack source, ResourceLocation entityId) {
      MobRagdollBlacklistSavedData data = MobRagdollBlacklistSavedData.get(source.getLevel());
      if (!data.addEntity(entityId)) {
         source.sendFailure(Component.literal(entityId + " is already in the mob ragdoll blacklist."));
         return 0;
      }
      source.sendSuccess(() -> Component.literal("Blacklisted mob ragdoll entity " + entityId + "."), true);
      return 1;
   }

   private static int removeMobRagdollBlacklistEntity(CommandSourceStack source, ResourceLocation entityId) {
      MobRagdollBlacklistSavedData data = MobRagdollBlacklistSavedData.get(source.getLevel());
      if (!data.removeEntity(entityId)) {
         source.sendFailure(Component.literal(entityId + " was not in the mob ragdoll blacklist."));
         return 0;
      }
      source.sendSuccess(() -> Component.literal("Removed mob ragdoll entity blacklist entry " + entityId + "."), true);
      return 1;
   }

   private static int addMobRagdollBlacklistNamespace(CommandSourceStack source, String namespace) {
      String normalized = MobRagdollBlacklistSavedData.normalizeNamespace(namespace);
      if (!MobRagdollBlacklistSavedData.isValidNamespace(normalized)) {
         source.sendFailure(Component.literal(namespace + " is not a valid mod id namespace."));
         return 0;
      }

      MobRagdollBlacklistSavedData data = MobRagdollBlacklistSavedData.get(source.getLevel());
      if (!data.addNamespace(normalized)) {
         source.sendFailure(Component.literal(normalized + " is already in the mob ragdoll mod blacklist."));
         return 0;
      }
      source.sendSuccess(() -> Component.literal("Blacklisted mob ragdolls from mod namespace " + normalized + "."), true);
      return 1;
   }

   private static int removeMobRagdollBlacklistNamespace(CommandSourceStack source, String namespace) {
      String normalized = MobRagdollBlacklistSavedData.normalizeNamespace(namespace);
      if (!MobRagdollBlacklistSavedData.isValidNamespace(normalized)) {
         source.sendFailure(Component.literal(namespace + " is not a valid mod id namespace."));
         return 0;
      }

      MobRagdollBlacklistSavedData data = MobRagdollBlacklistSavedData.get(source.getLevel());
      if (!data.removeNamespace(normalized)) {
         source.sendFailure(Component.literal(normalized + " was not in the mob ragdoll mod blacklist."));
         return 0;
      }
      source.sendSuccess(() -> Component.literal("Removed mob ragdoll mod blacklist entry " + normalized + "."), true);
      return 1;
   }

   private static int clearMobRagdollBlacklist(CommandSourceStack source) {
      MobRagdollBlacklistSavedData data = MobRagdollBlacklistSavedData.get(source.getLevel());
      if (!data.clear()) {
         source.sendFailure(Component.literal("The mob ragdoll blacklist is already empty."));
         return 0;
      }
      source.sendSuccess(() -> Component.literal("Cleared the mob ragdoll blacklist."), true);
      return 1;
   }

   private static int listMobRagdollBlacklist(CommandSourceStack source) {
      MobRagdollBlacklistSavedData data = MobRagdollBlacklistSavedData.get(source.getLevel());
      String entities = formatBlacklistEntries(data.entities());
      String namespaces = formatBlacklistEntries(data.namespaces());
      source.sendSuccess(() -> Component.literal("Mob ragdoll blacklist: entities " + entities + "; mods " + namespaces + "."), false);
      return data.entities().size() + data.namespaces().size();
   }

   private static String formatBlacklistEntries(Collection<String> values) {
      if (values.isEmpty()) {
         return "none";
      }
      return String.join(", ", values.stream().sorted().toList());
   }

   private static CompletableFuture<Suggestions> suggestEntityNamespaces(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      return SharedSuggestionProvider.suggest(
         BuiltInRegistries.ENTITY_TYPE.keySet().stream()
            .map(ResourceLocation::getNamespace)
            .distinct()
            .sorted(),
         builder
      );
   }

   private static int startWailing(ServerPlayer player, int durationTicks, double stiffness, int intervalTicks) {
      return startWailing(
         player.createCommandSourceStack(),
         List.of(player),
         RagdollWailingOptions.builder()
            .durationTicks(durationTicks)
            .stiffness(stiffness)
            .intervalTicks(intervalTicks)
            .build()
      );
   }

   private static int startWailing(
      CommandSourceStack source,
      Collection<ServerPlayer> players,
      int durationTicks,
      double stiffness,
      int intervalTicks
   ) {
      return startWailing(
         source,
         players,
         RagdollWailingOptions.builder()
            .durationTicks(durationTicks)
            .stiffness(stiffness)
            .intervalTicks(intervalTicks)
            .build()
      );
   }

   private static int startWailing(CommandSourceStack source, Collection<ServerPlayer> players, RagdollWailingOptions options) {
      int applied = 0;
      for (ServerPlayer player : players) {
         var session = RagdollAPI.activeSession(player);
         if (session == null) {
            continue;
         }

         session.applyWailing(options);
         applied++;
      }

      int result = applied;
      if (result == 0) {
         source.sendFailure(Component.literal("No target players are currently ragdolled."));
      } else {
         source.sendSuccess(() -> Component.literal("Started wailing on " + result + " ragdoll(s)."), true);
      }
      return result;
   }

   private static int stopWailing(ServerPlayer player) {
      return stopWailing(player.createCommandSourceStack(), List.of(player));
   }

   private static int stopWailing(CommandSourceStack source, Collection<ServerPlayer> players) {
      int stopped = 0;
      for (ServerPlayer player : players) {
         var session = RagdollAPI.activeSession(player);
         if (session == null) {
            continue;
         }

         session.stopWailing();
         stopped++;
      }

      int result = stopped;
      if (result == 0) {
         source.sendFailure(Component.literal("No target players are currently ragdolled."));
      } else {
         source.sendSuccess(() -> Component.literal("Stopped wailing on " + result + " ragdoll(s)."), true);
      }
      return result;
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
      SubLevel torsoSubLevel = SubLevelContainer.getContainer(level).getSubLevel(session.id());
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

   private static int spawnMoblessRagdoll(CommandSourceStack source, ResourceLocation entityId, Vec3 pos) {
      EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
      if (type == null) {
         source.sendFailure(Component.literal("Unknown entity type: " + entityId));
         return 0;
      }
      UUID id = RagdollAPI.spawnMobless(source.getLevel(), type, pos);
      if (id == null) {
         source.sendFailure(Component.literal("Could not ragdoll " + entityId + " (not a living entity, or not whitelisted)."));
         return 0;
      }
      source.sendSuccess(() -> Component.literal("Spawning mobless ragdoll " + RagdollRegistry.shortId(id) + " from " + entityId
         + " (needs a player nearby to capture the model)"), true);
      return 1;
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

   private static int giveRagdollingStick(CommandSourceStack source) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      ItemStack stack = new ItemStack(Items.STICK);
      RagdollItemTags.markTestItem(stack);
      stack.set(DataComponents.CUSTOM_NAME, Component.literal("Ragdolling Stick"));

      if (!player.getInventory().add(stack)) {
         player.drop(stack, false);
      }
      source.sendSuccess(() -> Component.literal("Gave ragdolling stick"), true);
      return 1;
   }

   private static int giveDeletingStick(CommandSourceStack source) throws CommandSyntaxException {
      ServerPlayer player = source.getPlayerOrException();
      ItemStack stack = DeletingStick.create();
      if (!player.getInventory().add(stack)) {
         player.drop(stack, false);
      }
      source.sendSuccess(() -> Component.literal("Gave deleting stick (left-click deletes, right-click dismembers)"), true);
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
      MobRagdollAssembly.resetRuntimeState();
   }
}
