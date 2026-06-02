package dev.leo.sableplayerragdoll.api;

public record PlayerlessDespawnRule(Mode mode, int ticks, double speedMetersPerSecond) {

   public static PlayerlessDespawnRule defaultRule() {
      return new PlayerlessDespawnRule(Mode.DEFAULT, 0, 0.0);
   }

   public static PlayerlessDespawnRule never() {
      return new PlayerlessDespawnRule(Mode.NEVER, 0, 0.0);
   }

   public static PlayerlessDespawnRule afterTicks(int ticks) {
      return new PlayerlessDespawnRule(Mode.AFTER_TICKS, Math.max(0, ticks), 0.0);
   }

   public static PlayerlessDespawnRule belowSpeed(double metersPerSecond) {
      return new PlayerlessDespawnRule(Mode.BELOW_SPEED, 0, Math.max(0.0, metersPerSecond));
   }

   public enum Mode {
      DEFAULT,
      NEVER,
      AFTER_TICKS,
      BELOW_SPEED
   }
}
