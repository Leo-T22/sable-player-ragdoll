package dev.leo.sableplayerragdoll.api;

import dev.leo.sableplayerragdoll.config.RagdollSettings;
import java.util.List;

public record RagdollLaunchOptions(boolean autoSeat, List<DespawnCondition> despawnConditions, RagdollLimbOptions limbs, boolean lockDismount, @org.jetbrains.annotations.Nullable RagdollWailingOptions wailing) {

   public static RagdollLaunchOptions defaults() {
      return builder().build();
   }

   public static Builder builder() {
      return new Builder();
   }

   public static final class Builder {
      private boolean autoSeat = RagdollSettings.autoSeatOnTrigger();
      private List<DespawnCondition> despawnConditions = List.of();
      private RagdollLimbOptions limbs = RagdollLimbOptions.defaults();
      private boolean lockDismount = false;
      @org.jetbrains.annotations.Nullable
      private RagdollWailingOptions wailing = null;

      private Builder() {
      }

      public Builder autoSeat(boolean autoSeat) {
         this.autoSeat = autoSeat;
         return this;
      }

      public Builder despawnConditions(List<DespawnCondition> despawnConditions) {
         this.despawnConditions = despawnConditions == null ? List.of() : List.copyOf(despawnConditions);
         return this;
      }

      public Builder limbs(RagdollLimbOptions limbs) {
         this.limbs = limbs == null ? RagdollLimbOptions.defaults() : limbs;
         return this;
      }

      public Builder lockDismount(boolean lockDismount) {
         this.lockDismount = lockDismount;
         return this;
      }

      public Builder wailing(@org.jetbrains.annotations.Nullable RagdollWailingOptions wailing) {
         this.wailing = wailing;
         return this;
      }

      public RagdollLaunchOptions build() {
         return new RagdollLaunchOptions(this.autoSeat, this.despawnConditions, this.limbs, this.lockDismount, this.wailing);
      }
   }
}
