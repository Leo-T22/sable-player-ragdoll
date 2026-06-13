package dev.leo.sableplayerragdoll.neoforge;

import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.entity.RagdollDollEntity;
import dev.leo.sableplayerragdoll.entity.RagdollSeatEntity;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollCameraHelper;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollDollEntityRenderer;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollInputClient;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollKeybinds;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollPartBlockEntityRenderer;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollSeatEntityRenderer;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollGrabClient;
import dev.leo.sableplayerragdoll.neoforge.client.RagdollBlockInteractClient;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;

@Mod(value = "sable_player_ragdoll", dist = {Dist.CLIENT})
public final class SablePlayerRagdollNeoForgeClient {
   @SuppressWarnings("unchecked")
   public SablePlayerRagdollNeoForgeClient(ModContainer container, IEventBus modBus) {
      container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) ConfigurationScreen::new);
      RagdollCameraHelper.init();
      RagdollKeybinds.init(modBus);
      RagdollInputClient.init();
      RagdollGrabClient.init();
      RagdollBlockInteractClient.init();
      modBus.addListener(SablePlayerRagdollNeoForgeClient::registerEntityRenderers);
   }

   @SuppressWarnings("unchecked")
   private static void registerEntityRenderers(RegisterRenderers event) {
      event.registerEntityRenderer((EntityType<RagdollSeatEntity>) RagdollBlockRegistration.RAGDOLL_SEAT_ENTITY.get(), RagdollSeatEntityRenderer::new);
      event.registerEntityRenderer((EntityType<RagdollDollEntity>) RagdollBlockRegistration.RAGDOLL_DOLL_ENTITY.get(), RagdollDollEntityRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType<RagdollPartBlockEntity>) RagdollBlockRegistration.RAGDOLL_PART_BLOCK_ENTITY.get(), RagdollPartBlockEntityRenderer::new);
   }
}
