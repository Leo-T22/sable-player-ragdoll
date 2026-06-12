package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollGrabPacket(BlockPos pos, boolean release) implements CustomPacketPayload {
   public static final Type<RagdollGrabPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "grab")
   );
   private static final ResourceLocation GRAB_SLOWDOWN_ID = ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "grab_slowdown");
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollGrabPacket> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, RagdollGrabPacket::pos,
      ByteBufCodecs.BOOL, RagdollGrabPacket::release,
      RagdollGrabPacket::new
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollGrabPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) return;
         if (RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID()) != null) return;
         BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
         if (blockEntity instanceof RagdollPartBlockEntity ragdollPart) {
            if (packet.release()) {
               ragdollPart.stopGrab(player.getUUID());
               removeSlowdown(player);
               PacketDistributor.sendToAllPlayers(new RagdollGrabSyncPacket(player.getUUID(), false), new CustomPacketPayload[0]);
            } else {
               if (RagdollRegistry.isGrabDisabledAt(player.serverLevel(), packet.pos())) return;
               ragdollPart.startGrab(player.getUUID());
               applySlowdown(player);
               PacketDistributor.sendToAllPlayers(new RagdollGrabSyncPacket(player.getUUID(), true), new CustomPacketPayload[0]);
            }
         }
      });
   }

   private static void applySlowdown(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr == null) return;
      attr.removeModifier(GRAB_SLOWDOWN_ID);
      attr.addTransientModifier(new AttributeModifier(GRAB_SLOWDOWN_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
   }

   private static void removeSlowdown(ServerPlayer player) {
      AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (attr != null) attr.removeModifier(GRAB_SLOWDOWN_ID);
   }
}
