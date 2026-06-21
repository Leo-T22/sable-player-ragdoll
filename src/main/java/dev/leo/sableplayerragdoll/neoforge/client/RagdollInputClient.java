package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.neoforge.network.RagdollArmInputPacket;
import dev.leo.sableplayerragdoll.neoforge.network.RagdollInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RagdollInputClient {
   private static float lastStrafe;
   private static float lastForward;
   private static int keepAliveTicks;
   private static boolean lastArmActive;

   private RagdollInputClient() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollInputClient::onClientTick);
   }

   private static void onClientTick(Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || !player.isPassenger()) {
         sendIfChanged(0.0F, 0.0F, true);
         sendArmIfChanged(false, false, false, false, Vec3.ZERO);
         return;
      }
      float strafe = axis(player.input.right, player.input.left);
      float forward = axis(player.input.down, player.input.up);
      sendIfChanged(strafe, forward, false);

      if (player.getVehicle() instanceof RagdollSeatEntity) {
         boolean leftReach = minecraft.options.keyAttack.isDown();
         boolean rightReach = minecraft.options.keyUse.isDown();
         boolean leftGrab = minecraft.options.keySprint.isDown();
         boolean rightGrab = minecraft.options.keyJump.isDown();
         org.joml.Vector3f camLook = minecraft.gameRenderer.getMainCamera().getLookVector();
         sendArmIfChanged(leftReach, rightReach, leftGrab, rightGrab, new Vec3(camLook.x(), camLook.y(), camLook.z()));
      } else {
         sendArmIfChanged(false, false, false, false, Vec3.ZERO);
      }
   }

   private static void sendIfChanged(float strafe, float forward, boolean forceZero) {
      boolean changed = strafe != lastStrafe || forward != lastForward;
      if (!changed && !forceZero && ++keepAliveTicks < 5) return;
      if (!changed && forceZero && lastStrafe == 0.0F && lastForward == 0.0F) return;
      keepAliveTicks = 0;
      lastStrafe = strafe;
      lastForward = forward;
      PacketDistributor.sendToServer(new RagdollInputPacket(strafe, forward), new CustomPacketPayload[0]);
   }

   private static void sendArmIfChanged(boolean leftReach, boolean rightReach, boolean leftGrab, boolean rightGrab, Vec3 look) {
      boolean active = leftReach || rightReach || leftGrab || rightGrab;
      if (!active && !lastArmActive) return;
      lastArmActive = active;
      PacketDistributor.sendToServer(
         new RagdollArmInputPacket(leftReach, rightReach, leftGrab, rightGrab, (float) look.x, (float) look.y, (float) look.z),
         new CustomPacketPayload[0]);
   }

   private static float axis(boolean negative, boolean positive) {
      if (positive == negative) return 0.0F;
      return positive ? 1.0F : -1.0F;
   }
}
