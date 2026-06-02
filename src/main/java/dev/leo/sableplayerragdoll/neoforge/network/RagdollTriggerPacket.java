package dev.leo.sableplayerragdoll.neoforge.network;

import dev.leo.sableplayerragdoll.physics.RagdollRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RagdollTriggerPacket() implements CustomPacketPayload {
   public static final Type<RagdollTriggerPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "trigger_ragdoll")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollTriggerPacket> STREAM_CODEC = StreamCodec.unit(new RagdollTriggerPacket());

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollTriggerPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            RagdollRegistry.triggerManual(player);
         }
      });
   }
}
