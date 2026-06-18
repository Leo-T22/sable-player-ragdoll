package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.RagdollGrabCallbacks;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import dev.leo.sableplayerragdoll.physics.RagdollSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollGrabPacket(BlockPos pos, boolean release) implements CustomPacketPayload {
   public static final Type<RagdollGrabPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "grab")
   );
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
         BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
         if (blockEntity instanceof RagdollPartBlockEntity ragdollPart) {
            if (packet.release()) {
               ragdollPart.stopGrab(player.getUUID());
               RagdollGrabCallbacks.notifyReleased(player);
            } else {
               if (!RagdollSettings.grabEnabled()) return;
               if (RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID()) != null) return;
               if (RagdollRegistry.isGrabDisabledAt(player.serverLevel(), packet.pos())) return;
               ragdollPart.startGrab(player.getUUID());
               RagdollGrabCallbacks.notifyGrabbed(player);
            }
         } else if (blockEntity instanceof MobRagdollPartBlockEntity mobRagdollPart) {
            if (packet.release()) {
               mobRagdollPart.stopGrab(player.getUUID());
               RagdollGrabCallbacks.notifyReleased(player);
            } else {
               if (!RagdollSettings.grabEnabled()) return;
               if (RagdollSessionManager.activeRagdollForPlayer(player.serverLevel(), player.getUUID()) != null) return;
               if (RagdollRegistry.isGrabDisabledAt(player.serverLevel(), packet.pos())) return;
               mobRagdollPart.startGrab(player.getUUID());
               RagdollGrabCallbacks.notifyGrabbed(player);
            }
         }
      });
   }
}
