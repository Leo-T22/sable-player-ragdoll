package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollTorsoGrabPacket(BlockPos pos, float desiredRange, boolean release) implements CustomPacketPayload {
   public static final Type<RagdollTorsoGrabPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "torso_grab")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollTorsoGrabPacket> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, RagdollTorsoGrabPacket::pos,
      ByteBufCodecs.FLOAT, RagdollTorsoGrabPacket::desiredRange,
      ByteBufCodecs.BOOL, RagdollTorsoGrabPacket::release,
      RagdollTorsoGrabPacket::new
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollTorsoGrabPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) return;
         if (RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID()) != null) return;
         BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
         if (blockEntity instanceof RagdollPartBlockEntity ragdollPart && ragdollPart.canBeGrabbedAsTorso()) {
            if (packet.release()) {
               ragdollPart.stopTorsoGrab(player.getUUID());
            } else {
               ragdollPart.startTorsoGrab(player.getUUID(), packet.desiredRange());
            }
         }
      });
   }
}
