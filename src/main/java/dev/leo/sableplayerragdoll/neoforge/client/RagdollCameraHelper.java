package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.neoforge.config.RagdollClientConfig;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollCameraHelper {
   private static final int MAX_CAMERA_RETRY_TICKS = 40;
   private static final double RAGDOLL_EYE_LOCAL_UP = 0.57;
   private static final double RAGDOLL_EYE_LOCAL_FORWARD = 0.25;
   // ShoulderSurfing crashes on unknown CameraType values in Perspective.of(), fall back to vanilla 3rd person cam.
   private static final boolean SHOULDER_SURFING = ModList.get().isLoaded("shouldersurfing");

   private static int pendingCameraTicks;
   private static boolean suppressLocalPlayerRender;
   private static boolean cameraSwitched;
   private static boolean ridingRagdollSeat;
   private static boolean firstPersonAligned;
   private static CameraType cameraTypeBeforeRagdoll;

   private RagdollCameraHelper() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onClientTick);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onRenderPlayer);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onRenderHand);
      NeoForge.EVENT_BUS.addListener(RagdollCameraHelper::onCalculateCameraDistance);
   }

   private static void onClientTick(Post event) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      boolean nowRidingRagdollSeat = player != null && player.getVehicle() instanceof RagdollSeatEntity;

      if (nowRidingRagdollSeat && !ridingRagdollSeat) {
         cameraTypeBeforeRagdoll = minecraft.options.getCameraType();
         suppressLocalPlayerRender = true;
         firstPersonAligned = false;
         cameraSwitched = false;
         pendingCameraTicks = MAX_CAMERA_RETRY_TICKS;
         player.setForcedPose(Pose.STANDING);
      } else if (!nowRidingRagdollSeat && ridingRagdollSeat) {
         if (player != null) {
            player.setForcedPose(null);
         }
         resetFromContraptionCamera();
      }
      ridingRagdollSeat = nowRidingRagdollSeat;

      if (pendingCameraTicks > 0) {
         pendingCameraTicks--;
         if (tryActivateUnlockedContraptionCamera()) {
            pendingCameraTicks = 0;
         } else if (pendingCameraTicks == 0) {
            SablePlayerRagdoll.LOGGER.debug(
               "[sable_player_ragdoll] ragdoll camera sub-level not ready within {} ticks; keeping original camera",
               MAX_CAMERA_RETRY_TICKS
            );
         }
      }
   }

   private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (event.getEntity().getVehicle() instanceof RagdollSeatEntity) {
         event.setCanceled(true);
         return;
      }
      if (suppressLocalPlayerRender && minecraft.player != null && event.getEntity() == minecraft.player) {
         event.setCanceled(true);
      }
   }

   private static void onRenderHand(RenderHandEvent event) {
      if (suppressLocalPlayerRender) {
         event.setCanceled(true);
      }
   }

   private static void onCalculateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.options.getCameraType() != SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) return;
      if (minecraft.player == null || event.getCamera().getEntity() != minecraft.player) return;
      if (!(minecraft.player.getVehicle() instanceof RagdollSeatEntity)) return;
      event.setDistance((float) RagdollClientConfig.subLevelCameraDistance());
   }

   private static boolean tryActivateUnlockedContraptionCamera() {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (player == null || minecraft.level == null || !player.isPassenger()) return false;
      SubLevel subLevel = Sable.HELPER.getVehicleSubLevel(player);
      if (subLevel == null) return false;
      CameraType targetCameraType;
      if (RagdollClientConfig.useFirstPersonCamera()) {
         targetCameraType = CameraType.FIRST_PERSON;
         alignFirstPersonRotationToPlayerView(player, subLevel);
      } else {
         targetCameraType = SHOULDER_SURFING ? CameraType.THIRD_PERSON_BACK : SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED;
      }
      if (minecraft.options.getCameraType() != targetCameraType) {
         minecraft.options.setCameraType(targetCameraType);
      }
      cameraSwitched = true;
      return true;
   }

   public static Vec3 firstPersonHeadAnchor(Vec3 fallback, float partialTick) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if (!RagdollClientConfig.useFirstPersonCamera()
         || player == null
         || !(player.getVehicle() instanceof RagdollSeatEntity)
         || minecraft.options.getCameraType() != CameraType.FIRST_PERSON
         || !(Sable.HELPER.getVehicleSubLevel(player) instanceof ClientSubLevel torso)) {
         return fallback;
      }
      BlockPos plot = torso.getPlot().getCenterBlock();
      Vec3 localEye = new Vec3(
         plot.getX() + 0.5,
         plot.getY() + 0.5 + RAGDOLL_EYE_LOCAL_UP,
         plot.getZ() + 0.5 + RAGDOLL_EYE_LOCAL_FORWARD
      );
      return torso.renderPose(partialTick).transformPosition(localEye);
   }

   private static void alignFirstPersonRotationToPlayerView(LocalPlayer player, SubLevel subLevel) {
      double yawRad = Math.toRadians(player.getYRot());
      double pitchRad = Math.toRadians(player.getXRot());
      Quaterniond worldRotation = new Quaterniond().rotationYXZ(Math.PI - yawRad, -pitchRad, 0.0);
      Quaterniond localRotation = new Quaterniond(subLevel.logicalPose().orientation()).invert().mul(worldRotation);

      Vector3d euler = new Vector3d();
      localRotation.getEulerAnglesYXZ(euler);
      float localYRot = (float) Math.toDegrees(Math.PI - euler.y);
      float localXRot = (float) Math.toDegrees(-euler.x);

      player.setYRot(localYRot);
      player.setXRot(localXRot);
      player.yRotO = localYRot;
      player.xRotO = localXRot;
      player.setYHeadRot(localYRot);
      player.yHeadRotO = localYRot;
      firstPersonAligned = true;
   }

   public static boolean isFirstPersonAligned() {
      return firstPersonAligned;
   }

   private static void resetFromContraptionCamera() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.options != null) {
         if (cameraSwitched) {
            minecraft.options.setCameraType(cameraTypeBeforeRagdoll != null ? cameraTypeBeforeRagdoll : CameraType.FIRST_PERSON);
            cameraSwitched = false;
         }
         cameraTypeBeforeRagdoll = null;
         pendingCameraTicks = 0;
         suppressLocalPlayerRender = false;
         firstPersonAligned = false;
      }
   }

}
