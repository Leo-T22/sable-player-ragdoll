package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.neoforge.network.RagdollTorsoGrabPacket;
import dev.ryanhcode.sable.Sable;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;

public final class RagdollTorsoGrabClient {
   private static final double MAX_GRAB_RANGE = 5.0;
   @Nullable
   private static BlockPos activePos;

   private RagdollTorsoGrabClient() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollTorsoGrabClient::onClientTick);
   }

   private static void onClientTick(Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || minecraft.level == null || player.isSpectator()) { stopGrab(); return; }
      if (player.isPassenger()) { stopGrab(); return; }
      if (!minecraft.options.keyUse.isDown()) { stopGrab(); return; }
      if (activePos != null) return;

      BlockPos torsoPos = targetedTorso(minecraft);
      if (torsoPos == null) return;

      float desiredRange = desiredRange(player, torsoPos);
      activePos = torsoPos;
      PacketDistributor.sendToServer(new RagdollTorsoGrabPacket(torsoPos, desiredRange, false), new CustomPacketPayload[0]);
      player.swing(InteractionHand.MAIN_HAND);
   }

   private static void stopGrab() {
      if (activePos != null) {
         PacketDistributor.sendToServer(new RagdollTorsoGrabPacket(activePos, -1.0F, true), new CustomPacketPayload[0]);
         activePos = null;
      }
   }

   @Nullable
   private static BlockPos targetedTorso(Minecraft minecraft) {
      if (!(minecraft.hitResult instanceof BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS || minecraft.level == null) return null;
      BlockPos pos = blockHit.getBlockPos();
      BlockEntity blockEntity = minecraft.level.getBlockEntity(pos);
      return blockEntity instanceof RagdollPartBlockEntity ragdollPart && ragdollPart.canBeGrabbedAsTorso() ? pos : null;
   }

   private static float desiredRange(LocalPlayer player, BlockPos torsoPos) {
      if (player.level().getBlockEntity(torsoPos) instanceof RagdollPartBlockEntity ragdollPart) {
         Vector3d projected = Sable.HELPER.projectOutOfSubLevel(player.level(), ragdollPart.torsoGrabCenter());
         Vec3 eyePosition = player.getEyePosition();
         double maxRange = Math.min(MAX_GRAB_RANGE, player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue());
         return (float) Math.min(projected.distance(eyePosition.x, eyePosition.y, eyePosition.z), maxRange);
      }
      return 2.0F;
   }
}
