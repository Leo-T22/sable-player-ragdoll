package dev.leo.sableplayerragdoll.neoforge.client;

import java.util.List;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.neoforge.config.RagdollClientConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.common.NeoForge;

public final class RagdollControlsHint {
   private static final int LEFT_COLOR = 0xFF5555;
   private static final int RIGHT_COLOR = 0x5599FF;

   private static boolean wasRagdolling;

   private RagdollControlsHint() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollControlsHint::onClientTick);
   }

   private static void onClientTick(Post event) {
      LocalPlayer player = Minecraft.getInstance().player;
      boolean ragdolling = player != null && player.getVehicle() instanceof RagdollSeatEntity;
      if (ragdolling && !wasRagdolling) {
         if (RagdollClientConfig.showControlsHint()) {
            RagdollControlsHud.show(buildLines());
         }
      } else if (!ragdolling && wasRagdolling) {
         RagdollControlsHud.hide();
      }
      wasRagdolling = ragdolling;
      RagdollControlsHud.tick();
   }

   private static List<Component> buildLines() {
      Minecraft minecraft = Minecraft.getInstance();
      return List.of(
         Component.translatable("sable_player_ragdoll.ragdoll.controls.line1"),
         Component.translatable(
            "sable_player_ragdoll.ragdoll.controls.line2",
            keyLabel(minecraft.options.keyAttack, LEFT_COLOR),
            keyLabel(minecraft.options.keyUse, RIGHT_COLOR),
            keyLabel(minecraft.options.keySprint, LEFT_COLOR),
            keyLabel(minecraft.options.keyJump, RIGHT_COLOR)
         )
      );
   }

   private static Component keyLabel(KeyMapping key, int rgb) {
      return key.getTranslatedKeyMessage().copy().withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
   }
}
