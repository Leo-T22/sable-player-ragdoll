package dev.leo.sableplayerragdoll.neoforge.client;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
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
      if (event.getLevel().isClientSide() && isRagdollPart(event)) {
         event.setUseItem(TriState.FALSE);
      }
   }

   private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
      if (event.getLevel().isClientSide() && isRagdollPart(event)) {
         event.setCanceled(true);
      }
   }

   private static boolean isRagdollPart(PlayerInteractEvent event) {
      return event.getLevel().getBlockEntity(event.getPos()) instanceof RagdollPartBlockEntity;
   }
}
