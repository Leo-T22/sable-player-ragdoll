package dev.leo.sableplayerragdoll.physics;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.entity.entities_stick_sublevels.EntityStickExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class SubLevelEntityDetachHelper {
   private SubLevelEntityDetachHelper() {
   }

   public static List<Entity> detachTrackingEntities(ServerSubLevel subLevel, Predicate<Entity> skip) {
      List<Entity> detached = new ArrayList<>();
      if (subLevel == null || subLevel.isRemoved()) {
         return detached;
      }
      for (Entity entity : subLevel.getLevel().getEntities().getAll()) {
         if (skip != null && skip.test(entity)) {
            continue;
         }
         if (isAttachedToSubLevel(entity, subLevel)) {
            clearSubLevelTracking(entity);
            clearLeakedHiddenMobState(entity);
            detached.add(entity);
         }
      }
      return detached;
   }

   private static boolean isAttachedToSubLevel(Entity entity, ServerSubLevel subLevel) {
      if (Sable.HELPER.getTrackingOrVehicleSubLevel(entity) == subLevel) {
         return true;
      }
      if (entity instanceof EntityMovementExtension movement
            && subLevel.getUniqueId().equals(movement.sable$getLastTrackingSubLevelID())) {
         return true;
      }
      if (entity instanceof EntityStickExtension stick) {
         Vec3 plotPosition = stick.sable$getPlotPosition();
         if (plotPosition != null) {
            SubLevel containing = Sable.HELPER.getContaining(subLevel.getLevel(), plotPosition);
            return containing == subLevel;
         }
      }
      return false;
   }

   private static void clearSubLevelTracking(Entity entity) {
      if (entity instanceof EntityMovementExtension movement) {
         movement.sable$setTrackingSubLevel(null);
         movement.sable$setLastTrackingSubLevelID(null);
      }
      if (entity instanceof EntityStickExtension stick) {
         stick.sable$setPlotPosition(null);
      }
   }

   private static void clearLeakedHiddenMobState(Entity entity) {
      if (!(entity instanceof Mob mob)) {
         return;
      }
      if (mob.isInvisible() && mob.isNoAi() && entity.noPhysics) {
         mob.setNoAi(false);
         entity.setInvisible(false);
         entity.noPhysics = false;
         entity.refreshDimensions();
      }
   }

   public static void syncDetachedEntities(Collection<Entity> entities) {
      for (Entity entity : entities) {
         EntitySubLevelUtil.setOldPosNoMovement(entity);
         if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcastAndSend(entity, new ClientboundTeleportEntityPacket(entity));
         }
      }
   }
}
