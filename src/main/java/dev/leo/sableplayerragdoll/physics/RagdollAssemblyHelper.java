package dev.leo.sableplayerragdoll.physics;

import com.mojang.authlib.GameProfile;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.block.RagdollBlocks;
import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollAssemblyHelper {
   private static final double NECK_TORSO_Y = 0.83;
   private static final double NECK_HEAD_Y = 0.23;
   private static final double SHOULDER_Y = 0.7;
   private static final double ARM_SHOULDER_Y = 0.12;
   private static final double HIP_TORSO_Y = 0.2;
   private static final double HIP_LEG_Y = 0.8;
   private static final double HALF_TURN = Math.PI;
   private static final double LEFT_ARM_ROLL = -Math.PI;
   private static final double RIGHT_ARM_ROLL = Math.PI;
   private static final double NECK_ANGULAR_STIFFNESS = 80.0;
   private static final double NECK_ANGULAR_DAMPING = 6.0;
   private static final double LIMB_ANGULAR_STIFFNESS = 35.0;
   private static final double LIMB_ANGULAR_DAMPING = 4.5;
   private static final PartSpawn[] PARTS = new PartSpawn[]{
      new PartSpawn("head", BodyPart.HEAD, 0.0, 1.7125, 0.0, 0.0),
      new PartSpawn("torso", BodyPart.TORSO, 0.0, 1.05, 0.0, 0.0),
      new PartSpawn("left_arm", BodyPart.LEFT_ARM, -0.36, 1.46, HALF_TURN, LEFT_ARM_ROLL),
      new PartSpawn("right_arm", BodyPart.RIGHT_ARM, 0.36, 1.46, 0.0, RIGHT_ARM_ROLL),
      new PartSpawn("left_leg", BodyPart.LEFT_LEG, -0.16, 0.5125, 0.0, 0.0),
      new PartSpawn("right_leg", BodyPart.RIGHT_LEG, 0.16, 0.5125, 0.0, 0.0)
   };
   private static final List<PhysicsConstraintHandle> ACTIVE_CONSTRAINTS = new ArrayList<>();
   private static final Map<UUID, List<UUID>> DOLL_PARTS_BY_HEAD = new ConcurrentHashMap<>();
   private static final Map<UUID, BodyPart> BODY_PART_BY_SUBLEVEL = new ConcurrentHashMap<>();
   private static final Map<UUID, UUID> HEAD_BY_PART = new ConcurrentHashMap<>();
   private static final Set<UUID> ELYTRA_HEADS = ConcurrentHashMap.newKeySet();

   private RagdollAssemblyHelper() {
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 forward) {
      return spawn(level, player, baseCenter, right, new Vec3(0.0, 1.0, 0.0), forward, false);
   }

   public static @Nullable Doll spawn(ServerLevel level, GameProfile profile, Vec3 baseCenter, Vec3 right, Vec3 forward) {
      return spawn(level, profile, null, baseCenter, right, new Vec3(0.0, 1.0, 0.0), forward, false);
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 up, Vec3 forward) {
      return spawn(level, player, baseCenter, right, up, forward, false);
   }

   public static @Nullable Doll spawn(ServerLevel level, GameProfile profile, Vec3 baseCenter, Vec3 right, Vec3 up, Vec3 forward) {
      return spawn(level, profile, null, baseCenter, right, up, forward, false);
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 up, Vec3 forward, boolean suppressLegContacts) {
      return spawn(level, player.getGameProfile(), player, baseCenter, right, up, forward, suppressLegContacts);
   }

   private static @Nullable Doll spawn(
      ServerLevel level,
      GameProfile profile,
      @Nullable Player equipmentSource,
      Vec3 baseCenter,
      Vec3 right,
      Vec3 up,
      Vec3 forward,
      boolean suppressLegContacts
   ) {
      for (PartSpawn part : PARTS) {
         Vec3 center = baseCenter.add(right.scale(part.rightOffset())).add(up.scale(part.upOffset()));
         int partY = BlockPos.containing(center).getY();
         if (partY < level.getMinBuildHeight() || partY >= level.getMaxBuildHeight()) {
            SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] ragdoll spawn aborted: part {} at Y={} outside world bounds [{}, {})", part.name(), partY, level.getMinBuildHeight(), level.getMaxBuildHeight());
            return null;
         }
      }
      pruneInactiveConstraints();
      Map<BodyPart, SpawnedPart> spawnedParts = new EnumMap<>(BodyPart.class);
      Quaterniond orientation = orientationFromBasis(right, up, forward);

      // Pass 1: assemble all parts at safe positions
      for (int i = 0; i < PARTS.length; i++) {
         PartSpawn part = PARTS[i];
         Vec3 desiredCenter = baseCenter.add(right.scale(part.rightOffset())).add(up.scale(part.upOffset()));
         BlockPos desiredPos = BlockPos.containing(desiredCenter);
         BlockPos safePos = new BlockPos(desiredPos.getX(), level.getMaxBuildHeight() - 1 - i, desiredPos.getZ());
         ServerSubLevel subLevel = assemblePart(level, safePos, part, profile, equipmentSource);
         if (subLevel != null) {
            spawnedParts.put(part.bodyPart(), new SpawnedPart(subLevel, desiredCenter, subLevel.getPlot().getCenterBlock(), part.rightOffset()));
         }
      }

      SpawnedPart head = spawnedParts.get(BodyPart.HEAD);
      if (head == null) {
         removeParts(level, spawnedParts.values().stream().map(SpawnedPart::subLevel).toList());
         return null;
      }

      // Pass 2: move all assembled parts to final positions
      for (PartSpawn part : PARTS) {
         SpawnedPart spawnedPart = spawnedParts.get(part.bodyPart());
         if (spawnedPart != null) {
            movePartTo(level, spawnedPart.subLevel(), spawnedPart.worldCenter(), partOrientation(orientation, part));
         }
      }

      // Pass 3: attach constraints — all parts are now at final positions
      int constraints = attachSpawnedParts(level, spawnedParts, suppressLegContacts);
      List<ServerSubLevel> subLevels = spawnedParts.values().stream().map(SpawnedPart::subLevel).toList();
      UUID headId = head.subLevel().getUniqueId();
      DOLL_PARTS_BY_HEAD.put(headId, subLevels.stream().map(ServerSubLevel::getUniqueId).toList());
      if (suppressLegContacts) {
         ELYTRA_HEADS.add(headId);
      }

      spawnedParts.forEach((bodyPart, spawnedPart) -> {
         UUID partId = spawnedPart.subLevel().getUniqueId();
         BODY_PART_BY_SUBLEVEL.put(partId, bodyPart);
         HEAD_BY_PART.put(partId, headId);
      });
      return new Doll(head.subLevel(), subLevels, constraints);
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right) {
      Vec3 forward = new Vec3(right.z, 0.0, -right.x);
      return spawn(level, player, baseCenter, right, forward);
   }

   public static List<UUID> consumeLinkedParts(UUID headId) {
      List<UUID> partIds = DOLL_PARTS_BY_HEAD.remove(headId);
      ELYTRA_HEADS.remove(headId);
      List<UUID> linkedParts = partIds == null ? List.of(headId) : partIds;
      linkedParts.forEach(BODY_PART_BY_SUBLEVEL::remove);
      linkedParts.forEach(HEAD_BY_PART::remove);
      return linkedParts;
   }

   public static List<UUID> linkedParts(UUID headId) {
      List<UUID> partIds = DOLL_PARTS_BY_HEAD.get(headId);
      return partIds == null ? List.of(headId) : partIds;
   }

   public static @Nullable UUID linkedTorso(UUID headId) {
      for (UUID partId : linkedParts(headId)) {
         if (BODY_PART_BY_SUBLEVEL.get(partId) == BodyPart.TORSO) {
            return partId;
         }
      }

      return null;
   }

   public static @Nullable UUID linkedHead(UUID partId) {
      return HEAD_BY_PART.get(partId);
   }

   public static boolean isRagdollPart(UUID subLevelId) {
      return HEAD_BY_PART.containsKey(subLevelId);
   }

   public static boolean isElytraRagdollPart(UUID subLevelId) {
      UUID headId = HEAD_BY_PART.get(subLevelId);
      return headId != null && ELYTRA_HEADS.contains(headId);
   }

   public static double launchVelocityScale(UUID subLevelId) {
      BodyPart bodyPart = BODY_PART_BY_SUBLEVEL.get(subLevelId);
      if (bodyPart == null) {
         return 1.0;
      }

      return switch (bodyPart) {
         case TORSO -> 0.34;
         case HEAD -> 0.22;
         case LEFT_ARM, RIGHT_ARM -> 0.14;
         case LEFT_LEG, RIGHT_LEG -> 0.08;
      };
   }

   private static ServerSubLevel assemblePart(ServerLevel level, BlockPos pos, PartSpawn part, GameProfile profile, @Nullable Player equipmentSource) {
      BlockState previous = level.getBlockState(pos);
      level.setBlock(pos, RagdollBlocks.ragdollPartDefaultState().setValue(RagdollPartBlock.BODY_PART, part.bodyPart()), 3);
      BlockEntity blockEntity = level.getBlockEntity(pos);
      if (blockEntity instanceof RagdollPartBlockEntity ragdollPart) {
         if (equipmentSource != null) {
            ragdollPart.configure(part.bodyPart(), equipmentSource);
         } else {
            ragdollPart.configure(part.bodyPart(), profile);
         }
      }

      Set<BlockPos> blocks = Set.of(pos);

      try {
         ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, pos, blocks, BoundingBox3i.from(blocks));
         if (subLevel != null && !subLevel.isRemoved()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return subLevel;
         }
      } catch (Throwable error) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] failed to spawn ragdoll part {} at {}: {}", part.name(), pos, error.toString());
      }

      level.setBlock(pos, previous, 3);
      return null;
   }

   private static int attachSpawnedParts(ServerLevel level, Map<BodyPart, SpawnedPart> parts, boolean suppressLegContacts) {
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] ragdoll constraints skipped: no physics system");
         return 0;
      }

      SpawnedPart torso = parts.get(BodyPart.TORSO);
      if (torso == null) {
         return 0;
      }

      int constraints = 0;
      constraints += attach(
         physicsSystem,
         torso,
         parts.get(BodyPart.HEAD),
         plotAnchor(torso, 0.5, NECK_TORSO_Y, 0.5),
         partPlotAnchor(parts.get(BodyPart.HEAD), 0.5, NECK_HEAD_Y, 0.5),
         NECK_ANGULAR_STIFFNESS,
         NECK_ANGULAR_DAMPING,
         "neck"
      );
      constraints += attachSideLimb(physicsSystem, torso, parts.get(BodyPart.LEFT_ARM), SHOULDER_Y, ARM_SHOULDER_Y, true, "left shoulder");
      constraints += attachSideLimb(physicsSystem, torso, parts.get(BodyPart.RIGHT_ARM), SHOULDER_Y, ARM_SHOULDER_Y, true, "right shoulder");
      constraints += attachSideLimb(physicsSystem, torso, parts.get(BodyPart.LEFT_LEG), HIP_TORSO_Y, HIP_LEG_Y, false, "left hip");
      constraints += attachSideLimb(physicsSystem, torso, parts.get(BodyPart.RIGHT_LEG), HIP_TORSO_Y, HIP_LEG_Y, false, "right hip");
      suppressInternalContacts(physicsSystem, parts);
      return constraints;
   }

   private static int attachSideLimb(SubLevelPhysicsSystem physicsSystem, SpawnedPart torso, SpawnedPart limb, double torsoY, double limbY, boolean anchorAtLimbCenter, String name) {
      if (limb == null) {
         return 0;
      }

      double torsoScale = anchorAtLimbCenter ? 1.0 : 0.5;
      double limbScale = anchorAtLimbCenter ? 0.0 : 0.5;
      double sideOffset = limb.sideOffset() - torso.sideOffset();
      double torsoX = 0.5 + sideOffset * torsoScale;
      double limbX = 0.5 - sideOffset * limbScale;
      return attach(physicsSystem, torso, limb, plotAnchor(torso, torsoX, torsoY, 0.5), plotAnchor(limb, limbX, limbY, 0.5), LIMB_ANGULAR_STIFFNESS, LIMB_ANGULAR_DAMPING, name);
   }

   private static int attach(
      SubLevelPhysicsSystem physicsSystem,
      SpawnedPart first,
      SpawnedPart second,
      Vector3d firstAnchor,
      Vector3d secondAnchor,
      double angularStiffness,
      double angularDamping,
      String name
   ) {
      if (first == null || second == null) {
         return 0;
      }

      try {
         GenericConstraintConfiguration config = new GenericConstraintConfiguration(
            firstAnchor,
            secondAnchor,
            new Quaterniond(),
            new Quaterniond(),
            Set.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z)
         );
         PhysicsConstraintHandle handle = physicsSystem.getPipeline().addConstraint(first.subLevel(), second.subLevel(), config);
         handle.setContactsEnabled(false);
         ACTIVE_CONSTRAINTS.add(handle);

         for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            handle.setMotor(axis, 0.0, angularStiffness, angularDamping, false, 0.0);
         }

         return 1;
      } catch (Throwable error) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] failed to attach ragdoll constraint {}: {}", name, error.toString());
         return 0;
      }
   }

   private static void suppressInternalContacts(SubLevelPhysicsSystem physicsSystem, Map<BodyPart, SpawnedPart> parts) {
      BodyPart[] limbs = new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG};
      SpawnedPart head = parts.get(BodyPart.HEAD);
      for (BodyPart limb : limbs) {
         suppressContacts(physicsSystem, head, parts.get(limb));
      }

      for (int firstIndex = 0; firstIndex < limbs.length; firstIndex++) {
         for (int secondIndex = firstIndex + 1; secondIndex < limbs.length; secondIndex++) {
            suppressContacts(physicsSystem, parts.get(limbs[firstIndex]), parts.get(limbs[secondIndex]));
         }
      }
   }

   private static void suppressContacts(SubLevelPhysicsSystem physicsSystem, SpawnedPart first, SpawnedPart second) {
      if (first == null || second == null) {
         return;
      }

      try {
         GenericConstraintConfiguration config = new GenericConstraintConfiguration(
            plotAnchor(first, 0.5, 0.5, 0.5),
            plotAnchor(second, 0.5, 0.5, 0.5),
            new Quaterniond(),
            new Quaterniond(),
            Set.of()
         );
         PhysicsConstraintHandle handle = physicsSystem.getPipeline().addConstraint(first.subLevel(), second.subLevel(), config);
         if (handle != null) {
            handle.setContactsEnabled(false);
            ACTIVE_CONSTRAINTS.add(handle);
         }
      } catch (Throwable error) {
         SablePlayerRagdoll.LOGGER.debug("[sable_player_ragdoll] failed to suppress ragdoll limb contact: {}", error.toString());
      }
   }

   private static Vector3d partPlotAnchor(SpawnedPart part, double x, double y, double z) {
      return part == null ? new Vector3d() : plotAnchor(part, x, y, z);
   }

   private static Vector3d plotAnchor(SpawnedPart part, double x, double y, double z) {
      return new Vector3d(part.plotPos().getX() + x, part.plotPos().getY() + y, part.plotPos().getZ() + z);
   }

   private static void movePartTo(ServerLevel level, ServerSubLevel subLevel, Vec3 desiredCenter, Quaterniond orientation) {
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) {
         return;
      }

      Vec3 currentCenter = subLevel.logicalPose().transformPosition(Vec3.atCenterOf(subLevel.getPlot().getCenterBlock()));
      Vec3 delta = desiredCenter.subtract(currentCenter);
      Vector3d position = new Vector3d(subLevel.logicalPose().position()).add(delta.x, delta.y, delta.z);
      subLevel.logicalPose().position().set(position);
      subLevel.logicalPose().orientation().set(orientation);
      physicsSystem.getPipeline().teleport(subLevel, subLevel.logicalPose().position(), subLevel.logicalPose().orientation());
      subLevel.updateLastPose();
   }

   private static Quaterniond orientationFromBasis(Vec3 right, Vec3 up, Vec3 forward) {
      Vec3 r = normalizeOr(right, new Vec3(1.0, 0.0, 0.0));
      Vec3 u = normalizeOr(up, new Vec3(0.0, 1.0, 0.0));
      Vec3 f = normalizeOr(forward, new Vec3(0.0, 0.0, 1.0));
      Matrix3d basis = new Matrix3d();
      basis.setColumn(0, new Vector3d(r.x, r.y, r.z));
      basis.setColumn(1, new Vector3d(u.x, u.y, u.z));
      basis.setColumn(2, new Vector3d(f.x, f.y, f.z));
      return basis.getNormalizedRotation(new Quaterniond());
   }

   private static Vec3 normalizeOr(Vec3 vector, Vec3 fallback) {
      return vector.lengthSqr() < 1.0E-6 ? fallback : vector.normalize();
   }

   private static Quaterniond partOrientation(Quaterniond baseOrientation, PartSpawn part) {
      return new Quaterniond(baseOrientation).rotateY(part.yawOffset()).rotateZ(part.rollOffset());
   }

   private static void removeParts(ServerLevel level, List<ServerSubLevel> subLevels) {
      SubLevelContainer container = SubLevelContainer.getContainer(level);
      if (!(container instanceof ServerSubLevelContainer serverContainer)) {
         return;
      }

      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      for (ServerSubLevel subLevel : subLevels) {
         SubLevel current = serverContainer.getSubLevel(subLevel.getUniqueId());
         if (!(current instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            continue;
         }
         try {
            if (physicsSystem != null) {
               physicsSystem.getPipeline().wakeUp(serverSubLevel);
            }
            serverContainer.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
         } catch (Throwable e) {
            SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] removeParts fallback markRemoved for {}: {}", serverSubLevel.getUniqueId(), e.toString());
            serverSubLevel.markRemoved();
         }
      }
   }

   private static void pruneInactiveConstraints() {
      for (Iterator<PhysicsConstraintHandle> iterator = ACTIVE_CONSTRAINTS.iterator(); iterator.hasNext();) {
         PhysicsConstraintHandle handle = iterator.next();
         if (handle == null || !handle.isValid()) {
            iterator.remove();
         }
      }
   }

   public record Doll(ServerSubLevel headSubLevel, List<ServerSubLevel> allSubLevels, int constraints) {
   }

   public record PartSpawn(String name, BodyPart bodyPart, double rightOffset, double upOffset, double yawOffset, double rollOffset) {
   }

   private record SpawnedPart(ServerSubLevel subLevel, Vec3 worldCenter, BlockPos plotPos, double sideOffset) {
   }
}
