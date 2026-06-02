package dev.leo.sableplayerragdoll.neoforge.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

// Fired on the NeoForge game event bus before a ragdoll is assembled.
// Cancel to prevent the launch entirely. Modify velocity to redirect the force.
public class RagdollStartEvent extends Event implements ICancellableEvent {

   private final ServerPlayer player;
   private Vec3 velocity;

   public RagdollStartEvent(ServerPlayer player, Vec3 velocity) {
      this.player = player;
      this.velocity = velocity;
   }

   public ServerPlayer player() {
      return player;
   }

   public Vec3 velocity() {
      return velocity;
   }

   public void setVelocity(Vec3 velocity) {
      this.velocity = velocity;
   }
}
