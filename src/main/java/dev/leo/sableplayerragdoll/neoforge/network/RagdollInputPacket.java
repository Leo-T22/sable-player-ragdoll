package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.physics.RagdollControlHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollInputPacket(float strafe, float forward) implements CustomPacketPayload {
   public static final Type<RagdollInputPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_input")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollInputPacket> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.FLOAT, RagdollInputPacket::strafe,
      ByteBufCodecs.FLOAT, RagdollInputPacket::forward,
      RagdollInputPacket::new
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollInputPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            RagdollControlHelper.updateInput(player, packet.strafe(), packet.forward());
         }
      });
   }
}
