package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class RagdollBlockInteractClient {
   private RagdollBlockInteractClient() {
   }

   public static void init() {
      NeoForge.EVENT_BUS.addListener(RagdollBlockInteractClient::onRightClickBlock);
      NeoForge.EVENT_BUS.addListener(RagdollBlockInteractClient::onLeftClickBlock);
   }

   private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
      if (event.getLevel().isClientSide() && (isRagdollPart(event) || isLocalPlayerRagdolled())) {
         event.setUseItem(TriState.FALSE);
      }
   }

   private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
      if (event.getLevel().isClientSide() && (isRagdollPart(event) || isLocalPlayerRagdolled())) {
         event.setCanceled(true);
      }
   }

   private static boolean isRagdollPart(PlayerInteractEvent event) {
      return event.getLevel().getBlockEntity(event.getPos()) instanceof RagdollPartBlockEntity
         || event.getLevel().getBlockEntity(event.getPos()) instanceof MobRagdollPartBlockEntity;
   }

   private static boolean isLocalPlayerRagdolled() {
      var player = Minecraft.getInstance().player;
      return player != null && player.getVehicle() instanceof RagdollSeatEntity;
   }
}
