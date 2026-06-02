package dev.leo.sableplayerragdoll.neoforge.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class RagdollCameraHelper {
   private static final int MAX_CAMERA_RETRY_TICKS = 40;
   private static int pendingCameraTicks;
   private static boolean suppressLocalPlayerRender;

   private RagdollCameraHelper() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onClientTick);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onRenderPlayer);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onRenderHand);
   }

   public static void requestUnlockedContraptionCamera() {
      suppressLocalPlayerRender = true;
      pendingCameraTicks = MAX_CAMERA_RETRY_TICKS;
      tryActivateUnlockedContraptionCamera();
   }

   private static void onClientTick(Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (suppressLocalPlayerRender && (minecraft.player == null || !minecraft.player.isPassenger())) {
         suppressLocalPlayerRender = false;
      }
      if (pendingCameraTicks > 0) {
         pendingCameraTicks--;
         if (tryActivateUnlockedContraptionCamera()) {
            pendingCameraTicks = 0;
         }
      }
   }

   private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (suppressLocalPlayerRender && minecraft.player != null && event.getEntity() == minecraft.player) {
         event.setCanceled(true);
      }
   }

   private static void onRenderHand(RenderHandEvent event) {
      if (suppressLocalPlayerRender) {
         event.setCanceled(true);
      }
   }

   private static boolean tryActivateUnlockedContraptionCamera() {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || minecraft.level == null || !player.isPassenger()) return false;
      if (Sable.HELPER.getVehicleSubLevel(player) == null) return false;
      minecraft.options.setCameraType(SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED);
      return true;
   }

   public static void resetFromContraptionCamera() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.options != null) {
         CameraType cameraType = minecraft.options.getCameraType();
         if (cameraType == SableCameraTypes.SUB_LEVEL_VIEW || cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
         }
         pendingCameraTicks = 0;
         suppressLocalPlayerRender = false;
      }
   }
}
