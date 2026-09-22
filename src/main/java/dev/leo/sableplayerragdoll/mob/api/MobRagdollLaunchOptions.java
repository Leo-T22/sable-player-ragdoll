package dev.leo.sableplayerragdoll.mob.api;

import dev.leo.sableplayerragdoll.api.RagdollWailingOptions;
import org.jetbrains.annotations.Nullable;

public record MobRagdollLaunchOptions(int durationTicks, int corpseDurationTicks, boolean fallApartOnDeath,
                                      @Nullable RagdollWailingOptions wailing) {
    public static final int DEFAULT_DURATION_TICKS = 80;
    public static final int DEFAULT_CORPSE_DURATION_TICKS = 100;

    public MobRagdollLaunchOptions(int durationTicks) {
        this(durationTicks, DEFAULT_CORPSE_DURATION_TICKS, false, null);
    }

    public MobRagdollLaunchOptions(int durationTicks, int corpseDurationTicks, boolean fallApartOnDeath) {
        this(durationTicks, corpseDurationTicks, fallApartOnDeath, null);
    }

    public static MobRagdollLaunchOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int durationTicks = DEFAULT_DURATION_TICKS;
        private int corpseDurationTicks = DEFAULT_CORPSE_DURATION_TICKS;
        private boolean fallApartOnDeath;
        private RagdollWailingOptions wailing;

        private Builder() {
        }

        public Builder durationTicks(int durationTicks) {
            this.durationTicks = Math.max(1, durationTicks);
            return this;
        }

        public Builder corpseDurationTicks(int corpseDurationTicks) {
            this.corpseDurationTicks = Math.max(1, corpseDurationTicks);
            return this;
        }

        public Builder fallApartOnDeath(boolean fallApartOnDeath) {
            this.fallApartOnDeath = fallApartOnDeath;
            return this;
        }

        public Builder wailing(@Nullable RagdollWailingOptions wailing) {
            this.wailing = wailing;
            return this;
        }

        public MobRagdollLaunchOptions build() {
            return new MobRagdollLaunchOptions(
                    this.durationTicks, this.corpseDurationTicks, this.fallApartOnDeath, this.wailing);
        }
    }
}
