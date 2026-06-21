package dev.leo.sableplayerragdoll.mob.api;

public record MobRagdollLaunchOptions(int durationTicks) {
    public static final int DEFAULT_DURATION_TICKS = 80;

    public static MobRagdollLaunchOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int durationTicks = DEFAULT_DURATION_TICKS;

        private Builder() {
        }

        public Builder durationTicks(int durationTicks) {
            this.durationTicks = Math.max(1, durationTicks);
            return this;
        }

        public MobRagdollLaunchOptions build() {
            return new MobRagdollLaunchOptions(this.durationTicks);
        }
    }
}
