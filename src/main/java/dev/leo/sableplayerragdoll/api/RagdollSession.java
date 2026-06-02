package dev.leo.sableplayerragdoll.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public interface RagdollSession {

   ServerPlayer player();

   // Current linear velocity of the torso body, in blocks/tick.
   Vec3 currentVelocity();

   long elapsedTicks();

   // Triggers an immediate clean release. No-op if the session is already ending.
   void release();
}
