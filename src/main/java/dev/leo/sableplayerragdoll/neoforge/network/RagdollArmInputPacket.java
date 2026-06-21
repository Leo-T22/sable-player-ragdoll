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

public record RagdollArmInputPacket(boolean leftReach, boolean rightReach, boolean leftGrab, boolean rightGrab,
                                    float lookX, float lookY, float lookZ) implements CustomPacketPayload {
   private static final int LEFT_REACH = 1;
   private static final int RIGHT_REACH = 1 << 1;
   private static final int LEFT_GRAB = 1 << 2;
   private static final int RIGHT_GRAB = 1 << 3;

   public static final Type<RagdollArmInputPacket> TYPE = new Type<>(
      ResourceLocation.fromNamespaceAndPath("sable_player_ragdoll", "ragdoll_arm_input")
   );
   public static final StreamCodec<RegistryFriendlyByteBuf, RagdollArmInputPacket> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.BYTE, RagdollArmInputPacket::flags,
      ByteBufCodecs.FLOAT, RagdollArmInputPacket::lookX,
      ByteBufCodecs.FLOAT, RagdollArmInputPacket::lookY,
      ByteBufCodecs.FLOAT, RagdollArmInputPacket::lookZ,
      RagdollArmInputPacket::fromFlags
   );

   private byte flags() {
      int flags = 0;
      if (leftReach) flags |= LEFT_REACH;
      if (rightReach) flags |= RIGHT_REACH;
      if (leftGrab) flags |= LEFT_GRAB;
      if (rightGrab) flags |= RIGHT_GRAB;
      return (byte) flags;
   }

   private static RagdollArmInputPacket fromFlags(byte flags, float lookX, float lookY, float lookZ) {
      return new RagdollArmInputPacket(
         (flags & LEFT_REACH) != 0,
         (flags & RIGHT_REACH) != 0,
         (flags & LEFT_GRAB) != 0,
         (flags & RIGHT_GRAB) != 0,
         lookX, lookY, lookZ);
   }

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(RagdollArmInputPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            RagdollControlHelper.updateArmInput(player, packet.leftReach(), packet.rightReach(),
               packet.leftGrab(), packet.rightGrab(), packet.lookX(), packet.lookY(), packet.lookZ());
         }
      });
   }
}
