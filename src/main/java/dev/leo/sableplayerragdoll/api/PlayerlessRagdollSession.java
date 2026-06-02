package dev.leo.sableplayerragdoll.api;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public interface PlayerlessRagdollSession {

   UUID id();

   // Current linear velocity of the root body, in blocks/tick.
   Vec3 currentVelocity();

   long elapsedTicks();

   // Triggers an immediate clean release. No-op if the session is already ending.
   void release();
}
