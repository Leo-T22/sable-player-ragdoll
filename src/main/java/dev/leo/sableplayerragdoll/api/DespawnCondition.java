package dev.leo.sableplayerragdoll.api;

@FunctionalInterface
public interface DespawnCondition {

   boolean shouldDespawn(RagdollSession session);

   static DespawnCondition afterTicks(int ticks) {
      return session -> session.elapsedTicks() >= ticks;
   }

   static DespawnCondition belowSpeed(double metersPerSecond) {
      return session -> session.currentVelocity().length() * 20.0 <= metersPerSecond;
   }

   static DespawnCondition belowSpeedAfterTicks(double metersPerSecond, int minTicks) {
      return session -> session.elapsedTicks() >= minTicks
            && session.currentVelocity().length() * 20.0 <= metersPerSecond;
   }

   static DespawnCondition never() {
      return session -> false;
   }

   static DespawnCondition all(DespawnCondition... conditions) {
      return session -> {
         for (DespawnCondition c : conditions) {
            if (!c.shouldDespawn(session)) return false;
         }
         return true;
      };
   }

   static DespawnCondition any(DespawnCondition... conditions) {
      return session -> {
         for (DespawnCondition c : conditions) {
            if (c.shouldDespawn(session)) return true;
         }
         return false;
      };
   }
}
