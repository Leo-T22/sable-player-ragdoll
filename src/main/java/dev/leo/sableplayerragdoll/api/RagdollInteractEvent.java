package dev.leo.sableplayerragdoll.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public class RagdollInteractEvent extends Event implements ICancellableEvent {
   private final ServerPlayer player;
   private final UUID headId;
   private final UUID partId;
   private final BlockPos pos;
   private final ServerLevel level;

   public RagdollInteractEvent(ServerPlayer player, UUID headId, UUID partId, BlockPos pos, ServerLevel level) {
      this.player = player;
      this.headId = headId;
      this.partId = partId;
      this.pos = pos;
      this.level = level;
   }

   public ServerPlayer player() {
      return this.player;
   }

   public UUID headId() {
      return this.headId;
   }

   // The sublevel UUID of the specific body part that was right-clicked
   public UUID partId() {
      return this.partId;
   }

   public BlockPos pos() {
      return this.pos;
   }

   public ServerLevel level() {
      return this.level;
   }
}
