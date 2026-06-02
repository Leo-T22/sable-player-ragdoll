package dev.leo.sableplayerragdoll.neoforge.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;

// Fired on the NeoForge game event bus after the player has been released from a ragdoll.
// exitVelocity is the velocity inherited by the player at the moment of release.
public class RagdollEndEvent extends Event {

   private final ServerPlayer player;
   private final Vec3 exitVelocity;
   private final Reason reason;

   public RagdollEndEvent(ServerPlayer player, Vec3 exitVelocity, Reason reason) {
      this.player = player;
      this.exitVelocity = exitVelocity;
      this.reason = reason;
   }

   public ServerPlayer player() {
      return player;
   }

   public Vec3 exitVelocity() {
      return exitVelocity;
   }

   public Reason reason() {
      return reason;
   }

   public enum Reason {
      EXPIRED,
      RELEASED,
      PLAYER_DEATH,
      PLAYER_LOGOUT
   }
}
