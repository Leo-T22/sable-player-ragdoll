package dev.leo.sableplayerragdoll.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.leo.sableplayerragdoll.neoforge.network.RagdollTriggerPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class RagdollKeybinds {
   private static final KeyMapping RAGDOLL_KEY = new KeyMapping(
      "key.sable_player_ragdoll.ragdoll",
      InputConstants.Type.KEYSYM,
      GLFW.GLFW_KEY_H,
      "key.categories.sable_player_ragdoll"
   );

   private RagdollKeybinds() {
   }

   public static void init(IEventBus modBus) {
      modBus.addListener(RagdollKeybinds::registerKeyMappings);
      NeoForge.EVENT_BUS.addListener(RagdollKeybinds::onClientTick);
   }

   private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
      event.register(RAGDOLL_KEY);
   }

   private static void onClientTick(Post event) {
      while (RAGDOLL_KEY.consumeClick()) {
         if (Minecraft.getInstance().player != null) {
            PacketDistributor.sendToServer(
               new RagdollTriggerPacket(RagdollClientPoseCapture.capture(), Minecraft.getInstance().player.yBodyRot),
               new CustomPacketPayload[0]
            );
         }
      }
   }
}
