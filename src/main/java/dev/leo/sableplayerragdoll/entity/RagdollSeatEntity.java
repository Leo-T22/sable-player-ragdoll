package dev.leo.sableplayerragdoll.entity;

import dev.leo.sableplayerragdoll.block.RagdollSeatBlock;
import dev.leo.sableplayerragdoll.physics.RagdollAssemblyHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.Entity.MoveFunction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

public final class RagdollSeatEntity extends Entity implements IEntityWithComplexSpawn {
   private static final double RAGDOLL_HEAD_RIDER_OFFSET = -1.25;
   private static final String ANCHOR_X_KEY = "AnchorX";
   private static final String ANCHOR_Y_KEY = "AnchorY";
   private static final String ANCHOR_Z_KEY = "AnchorZ";
   private Optional<Vec3> plotAnchor = Optional.empty();

   public RagdollSeatEntity(EntityType<?> entityType, Level level) {
      super(entityType, level);
   }

   public RagdollSeatEntity(Level level) {
      this(RagdollSeatEntities.ragdollSeat(), level);
      this.noPhysics = true;
   }

   public void setPos(double x, double y, double z) {
      super.setPos(x, y, z);
      AABB bounds = this.getBoundingBox();
      Vec3 diff = new Vec3(x, y, z).subtract(bounds.getCenter());
      this.setBoundingBox(bounds.move(diff));
   }

   public void setPlotAnchor(BlockPos pos) {
      Vec3 anchor = new Vec3((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5);
      this.plotAnchor = Optional.of(anchor);
      this.setPos(anchor.x, anchor.y, anchor.z);
   }

   protected void positionRider(Entity passenger, MoveFunction callback) {
      if (this.hasPassenger(passenger)) {
         double heightOffset = this.getPassengerRidingPosition(passenger).y - passenger.getVehicleAttachmentPoint(this).y;
         callback.accept(passenger, this.getX(), 0.0625 + heightOffset + this.getRagdollHeadRiderOffset(), this.getZ());
      }
   }

   private double getRagdollHeadRiderOffset() {
      SubLevel subLevel = Sable.HELPER.getContaining(this);
      return subLevel != null && RagdollAssemblyHelper.linkedTorso(subLevel.getUniqueId()) != null ? RAGDOLL_HEAD_RIDER_OFFSET : 0.0;
   }

   public void onPassengerTurned(Entity passenger) {
      passenger.setYHeadRot(passenger.getYRot());
   }

   public void setDeltaMovement(Vec3 motion) {
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         this.plotAnchor.ifPresent(anchor -> {
            if (this.position().distanceToSqr(anchor) > 1.0E-6) {
               this.setPos(anchor.x, anchor.y, anchor.z);
            }
         });

         if (!this.isVehicle()) {
            if (!(this.level().getBlockState(this.blockPosition()).getBlock() instanceof RagdollSeatBlock)) {
               this.discard();
            }
         }
      }
   }

   protected boolean canRide(Entity entity) {
      return !(entity instanceof FakePlayer);
   }

   protected void removePassenger(Entity entity) {
      super.removePassenger(entity);
      if (entity instanceof TamableAnimal tamable) {
         tamable.setInSittingPose(false);
      }
   }

   public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
      return super.getDismountLocationForPassenger(passenger).add(0.0, 0.5, 0.0);
   }

   protected void defineSynchedData(Builder builder) {
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      if (tag.contains(ANCHOR_X_KEY) && tag.contains(ANCHOR_Y_KEY) && tag.contains(ANCHOR_Z_KEY)) {
         this.plotAnchor = Optional.of(new Vec3(tag.getDouble(ANCHOR_X_KEY), tag.getDouble(ANCHOR_Y_KEY), tag.getDouble(ANCHOR_Z_KEY)));
      }
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
      this.plotAnchor.ifPresent(anchor -> {
         tag.putDouble(ANCHOR_X_KEY, anchor.x);
         tag.putDouble(ANCHOR_Y_KEY, anchor.y);
         tag.putDouble(ANCHOR_Z_KEY, anchor.z);
      });
   }

   public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
   }

   public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
   }
}
