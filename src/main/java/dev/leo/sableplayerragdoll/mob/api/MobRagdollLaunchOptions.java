package dev.leo.sableplayerragdoll.mob.api;

public record MobRagdollLaunchOptions(boolean autoSeat, int durationTicks) {
    public static final int DEFAULT_DURATION_TICKS = 80;

    public static MobRagdollLaunchOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean autoSeat = true;
        private int durationTicks = DEFAULT_DURATION_TICKS;

        private Builder() {
        }

        public Builder autoSeat(boolean autoSeat) {
            this.autoSeat = autoSeat;
            return this;
        }

        public Builder durationTicks(int durationTicks) {
            this.durationTicks = Math.max(1, durationTicks);
            return this;
        }

        public MobRagdollLaunchOptions build() {
            return new MobRagdollLaunchOptions(this.autoSeat, this.durationTicks);
        }
    }
}
