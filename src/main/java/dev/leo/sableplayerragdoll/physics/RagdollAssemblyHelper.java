package dev.leo.sableplayerragdoll.physics;

import com.mojang.authlib.GameProfile;
import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import dev.leo.sableplayerragdoll.block.RagdollBlocks;
import dev.leo.sableplayerragdoll.block.RagdollPartBlock;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.api.RagdollLimbConfig;
import dev.leo.sableplayerragdoll.api.RagdollLimbOptions;
import dev.leo.sableplayerragdoll.block.entity.RagdollPartBlockEntity.BodyPart;
import dev.leo.sableplayerragdoll.config.RagdollSettings;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
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
import org.joml.Vector3dc;

public final class RagdollAssemblyHelper {
   private static final double NECK_TORSO_Y = 0.83;
   private static final double NECK_HEAD_Y = 0.23;
   private static final double SHOULDER_Y = 0.8;
   private static final double ARM_SHOULDER_Y = 0.8;
   private static final double HIP_TORSO_Y = 0.1;
   private static final double HIP_LEG_Y = 0.8;
   private static final double ARM_ROLL = 0.0;
   private static final double NECK_ANGULAR_STIFFNESS = 80.0;
   private static final double NECK_ANGULAR_DAMPING = 6.0;
   private static final double LIMB_ANGULAR_STIFFNESS = 5.0;
   private static final double LIMB_ANGULAR_DAMPING = 4.5;
   private static final PartSpawn[] PARTS = new PartSpawn[]{
      new PartSpawn("head", BodyPart.HEAD, 0.0, 1.7125, 0.0, 0.0),
      new PartSpawn("torso", BodyPart.TORSO, 0.0, 1.05, 0.0, 0.0),
      new PartSpawn("left_arm", BodyPart.LEFT_ARM, 0.36, 1.46, 0.0, ARM_ROLL),
      new PartSpawn("right_arm", BodyPart.RIGHT_ARM, -0.36, 1.46, 0.0, ARM_ROLL),
      new PartSpawn("left_leg", BodyPart.LEFT_LEG, 0.12, 0.5125, 0.0, 0.0),
      new PartSpawn("right_leg", BodyPart.RIGHT_LEG, -0.12, 0.5125, 0.0, 0.0)
   };
   private static final Map<BodyPart, PartSpawn> PART_BY_BODY = buildPartIndex();
   private static final List<PhysicsConstraintHandle> ACTIVE_CONSTRAINTS = new ArrayList<>();
   private static final Map<UUID, Map<BodyPart, RagdollJoint>> JOINTS_BY_ROOT = new ConcurrentHashMap<>();
   private static final Map<UUID, List<UUID>> DOLL_PARTS_BY_ROOT = new ConcurrentHashMap<>();
   private static final Map<UUID, BodyPart> BODY_PART_BY_SUBLEVEL = new ConcurrentHashMap<>();
   private static final Map<UUID, UUID> ROOT_BY_PART = new ConcurrentHashMap<>();
   private static final Set<UUID> ELYTRA_ROOTS = ConcurrentHashMap.newKeySet();

   private RagdollAssemblyHelper() {
   }

   private static Map<BodyPart, PartSpawn> buildPartIndex() {
      Map<BodyPart, PartSpawn> index = new EnumMap<>(BodyPart.class);
      for (PartSpawn part : PARTS) {
         index.put(part.bodyPart(), part);
      }
      return index;
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 forward) {
      return spawn(level, player, baseCenter, right, new Vec3(0.0, 1.0, 0.0), forward, false);
   }

   public static @Nullable Doll spawn(ServerLevel level, GameProfile profile, Vec3 baseCenter, Vec3 right, Vec3 forward) {
      return spawn(level, profile, null, baseCenter, right, new Vec3(0.0, 1.0, 0.0), forward, false, RagdollLimbOptions.defaults());
   }

   public static @Nullable Doll spawn(ServerLevel level, GameProfile profile, Vec3 baseCenter, Vec3 right, Vec3 forward, RagdollLimbOptions limbs) {
      return spawn(level, profile, null, baseCenter, right, new Vec3(0.0, 1.0, 0.0), forward, false, limbs);
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 forward, RagdollLimbOptions limbs) {
      return spawn(level, player.getGameProfile(), player, baseCenter, right, new Vec3(0.0, 1.0, 0.0), forward, false, limbs);
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 up, Vec3 forward) {
      return spawn(level, player, baseCenter, right, up, forward, false);
   }

   public static @Nullable Doll spawn(ServerLevel level, GameProfile profile, Vec3 baseCenter, Vec3 right, Vec3 up, Vec3 forward) {
      return spawn(level, profile, null, baseCenter, right, up, forward, false, RagdollLimbOptions.defaults());
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 up, Vec3 forward, boolean suppressLegContacts) {
      return spawn(level, player.getGameProfile(), player, baseCenter, right, up, forward, suppressLegContacts, RagdollLimbOptions.defaults());
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right, Vec3 up, Vec3 forward, boolean suppressLegContacts, RagdollLimbOptions limbs) {
      return spawn(level, player.getGameProfile(), player, baseCenter, right, up, forward, suppressLegContacts, limbs);
   }

   private static @Nullable Doll spawn(
      ServerLevel level,
      GameProfile profile,
      @Nullable Player equipmentSource,
      Vec3 baseCenter,
      Vec3 right,
      Vec3 up,
      Vec3 forward,
      boolean suppressLegContacts,
      RagdollLimbOptions limbs
   ) {
      pruneInactiveConstraints();
      Map<BodyPart, SpawnedPart> spawnedParts = new EnumMap<>(BodyPart.class);
      Quaterniond bodyOrientation = orientationFromBasis(right, up, forward);

      BlockPos basePos = BlockPos.containing(baseCenter);
      for (int i = 0; i < PARTS.length; i++) {
         PartSpawn part = PARTS[i];
         BlockPos safePos = new BlockPos(basePos.getX(), level.getMaxBuildHeight() - 1 - i, basePos.getZ());
         ServerSubLevel subLevel = assemblePart(level, safePos, part, profile, equipmentSource);
         if (subLevel != null) {
            spawnedParts.put(part.bodyPart(), new SpawnedPart(subLevel, baseCenter, subLevel.getPlot().getCenterBlock(), rightOffset(part, limbs.get(part.bodyPart()))));
         }
      }

      SpawnedPart torsoRoot = spawnedParts.get(BodyPart.TORSO);
      if (torsoRoot == null) {
         removeParts(level, spawnedParts.values().stream().map(SpawnedPart::subLevel).toList());
         return null;
      }

      PartSpawn torsoPart = PART_BY_BODY.get(BodyPart.TORSO);
      Quaterniond torsoOrientation = spawnOrientation(bodyOrientation, torsoPart, limbs.get(BodyPart.TORSO));
      Vec3 torsoCenter = baseCenter.add(up.scale(torsoPart.upOffset()));
      movePartTo(level, torsoRoot.subLevel(), torsoCenter, torsoOrientation);

      for (PartSpawn part : PARTS) {
         if (part.bodyPart() == BodyPart.TORSO) continue;
         SpawnedPart spawnedPart = spawnedParts.get(part.bodyPart());
         if (spawnedPart == null) continue;
         Quaterniond limbOrientation = spawnOrientation(bodyOrientation, part, limbs.get(part.bodyPart()));
         JointAnchor anchor = jointAnchor(part.bodyPart(), spawnedPart.sideOffset() - torsoRoot.sideOffset());
         Vector3d parentWorld = torsoOrientation.transform(new Vector3d(anchor.parentLocal()), new Vector3d());
         Vector3d childWorld = limbOrientation.transform(new Vector3d(anchor.childLocal()), new Vector3d());
         Vec3 limbCenter = torsoCenter.add(
            parentWorld.x - childWorld.x,
            parentWorld.y - childWorld.y,
            parentWorld.z - childWorld.z
         );
         movePartTo(level, spawnedPart.subLevel(), limbCenter, limbOrientation);
      }

      int constraints = attachSpawnedParts(level, spawnedParts, suppressLegContacts, limbs);
      List<ServerSubLevel> subLevels = spawnedParts.values().stream().map(SpawnedPart::subLevel).toList();
      UUID rootId = torsoRoot.subLevel().getUniqueId();
      DOLL_PARTS_BY_ROOT.put(rootId, subLevels.stream().map(ServerSubLevel::getUniqueId).toList());
      if (suppressLegContacts) {
         ELYTRA_ROOTS.add(rootId);
      }

      spawnedParts.forEach((bodyPart, spawnedPart) -> {
         UUID partId = spawnedPart.subLevel().getUniqueId();
         BODY_PART_BY_SUBLEVEL.put(partId, bodyPart);
         ROOT_BY_PART.put(partId, rootId);
      });
      Map<BodyPart, UUID> partSubLevelIds = new EnumMap<>(BodyPart.class);
      spawnedParts.forEach((bodyPart, spawnedPart) -> partSubLevelIds.put(bodyPart, spawnedPart.subLevel().getUniqueId()));
      return new Doll(torsoRoot.subLevel(), subLevels, partSubLevelIds, constraints);
   }

   public static @Nullable Doll spawn(ServerLevel level, ServerPlayer player, Vec3 baseCenter, Vec3 right) {
      Vec3 forward = new Vec3(right.z, 0.0, -right.x);
      return spawn(level, player, baseCenter, right, forward);
   }

   public static List<UUID> consumeLinkedParts(UUID rootId) {
      List<UUID> partIds = DOLL_PARTS_BY_ROOT.remove(rootId);
      ELYTRA_ROOTS.remove(rootId);
      List<UUID> linkedParts = partIds == null ? List.of(rootId) : partIds;
      linkedParts.forEach(BODY_PART_BY_SUBLEVEL::remove);
      linkedParts.forEach(ROOT_BY_PART::remove);
      JOINTS_BY_ROOT.remove(rootId);
      RagdollMotorEffects.clear(rootId);
      return linkedParts;
   }

   public static List<UUID> linkedParts(UUID rootId) {
      List<UUID> partIds = DOLL_PARTS_BY_ROOT.get(rootId);
      return partIds == null ? List.of(rootId) : partIds;
   }

   public static Map<BodyPart, UUID> linkedPartsAsMap(UUID rootId) {
      List<UUID> parts = linkedParts(rootId);
      Map<BodyPart, UUID> result = new EnumMap<>(BodyPart.class);
      for (UUID partId : parts) {
         BodyPart bodyPart = BODY_PART_BY_SUBLEVEL.get(partId);
         if (bodyPart != null) result.put(bodyPart, partId);
      }
      return result;
   }

   public static @Nullable UUID linkedHeadPart(UUID rootId) {
      for (UUID partId : linkedParts(rootId)) {
         if (BODY_PART_BY_SUBLEVEL.get(partId) == BodyPart.HEAD) {
            return partId;
         }
      }
      return null;
   }

   public static @Nullable UUID linkedRoot(UUID partId) {
      return ROOT_BY_PART.get(partId);
   }

   public static @Nullable BodyPart bodyPartOf(UUID subLevelId) {
      return BODY_PART_BY_SUBLEVEL.get(subLevelId);
   }

   public static @Nullable UUID dismember(UUID rootId, BodyPart limb) {
      if (limb == BodyPart.TORSO) return null;
      UUID limbId = linkedPartsAsMap(rootId).get(limb);
      Map<BodyPart, RagdollJoint> joints = JOINTS_BY_ROOT.get(rootId);
      RagdollJoint joint = joints == null ? null : joints.remove(limb);
      if (joint != null && joint.handle() != null) {
         if (joint.handle().isValid()) joint.handle().remove();
         ACTIVE_CONSTRAINTS.remove(joint.handle());
      }
      if (limbId != null) {
         BODY_PART_BY_SUBLEVEL.remove(limbId);
         ROOT_BY_PART.remove(limbId);
         List<UUID> parts = DOLL_PARTS_BY_ROOT.get(rootId);
         if (parts != null) {
            List<UUID> remaining = new ArrayList<>(parts);
            remaining.remove(limbId);
            DOLL_PARTS_BY_ROOT.put(rootId, remaining);
         }
      }
      return limbId;
   }

   public static boolean isRagdollPart(UUID subLevelId) {
      return ROOT_BY_PART.containsKey(subLevelId);
   }

   public static boolean isElytraRagdollPart(UUID subLevelId) {
      UUID rootId = ROOT_BY_PART.get(subLevelId);
      return rootId != null && ELYTRA_ROOTS.contains(rootId);
   }

   public static Map<BodyPart, RagdollJoint> joints(UUID rootId) {
      Map<BodyPart, RagdollJoint> joints = JOINTS_BY_ROOT.get(rootId);
      return joints == null ? Map.of() : Map.copyOf(joints);
   }

   public static @Nullable PhysicsConstraintHandle restoreConstraints(ServerLevel level, Map<BodyPart, ServerSubLevel> subLevels, RagdollLimbOptions limbs) {
      Map<BodyPart, SpawnedPart> parts = new EnumMap<>(BodyPart.class);
      for (PartSpawn part : PARTS) {
         ServerSubLevel subLevel = subLevels.get(part.bodyPart());
         if (subLevel != null) {
            parts.put(part.bodyPart(), new SpawnedPart(subLevel, Vec3.ZERO, subLevel.getPlot().getCenterBlock(), part.rightOffset()));
         }
      }

      ServerSubLevel torsoRoot = subLevels.get(BodyPart.TORSO);
      if (torsoRoot == null) {
         return null;
      }

      attachSpawnedParts(level, parts, false, limbs);
      UUID rootId = torsoRoot.getUniqueId();
      Map<BodyPart, RagdollJoint> joints = JOINTS_BY_ROOT.get(rootId);
      PhysicsConstraintHandle representative = joints == null || joints.isEmpty() ? null : joints.values().iterator().next().handle();

      List<ServerSubLevel> restoredSubLevels = parts.values().stream().map(SpawnedPart::subLevel).toList();
      DOLL_PARTS_BY_ROOT.put(rootId, restoredSubLevels.stream().map(ServerSubLevel::getUniqueId).toList());
      parts.forEach((bodyPart, spawnedPart) -> {
         UUID partId = spawnedPart.subLevel().getUniqueId();
         BODY_PART_BY_SUBLEVEL.put(partId, bodyPart);
         ROOT_BY_PART.put(partId, rootId);
      });
      return representative;
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
            RagdollEquipmentHelper.applyExtraEquipment(ragdollPart, equipmentSource);
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

   private static int attachSpawnedParts(ServerLevel level, Map<BodyPart, SpawnedPart> parts, boolean suppressLegContacts, RagdollLimbOptions limbs) {
      SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(level);
      if (physicsSystem == null) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] ragdoll constraints skipped: no physics system");
         return 0;
      }

      SpawnedPart torso = parts.get(BodyPart.TORSO);
      if (torso == null) {
         return 0;
      }

      RagdollLimbConfig headConfig = limbs.get(BodyPart.HEAD);
      UUID rootId = torso.subLevel().getUniqueId();
      SpawnedPart head = parts.get(BodyPart.HEAD);
      JointAnchor neck = jointAnchor(BodyPart.HEAD, 0.0);
      int constraints = 0;
      constraints += attach(
         rootId,
         BodyPart.HEAD,
         physicsSystem,
         torso,
         head,
         plotAnchorLocal(torso, neck.parentLocal()),
         head == null ? new Vector3d() : plotAnchorLocal(head, neck.childLocal()),
         stiffness(headConfig, NECK_ANGULAR_STIFFNESS),
         damping(headConfig, NECK_ANGULAR_DAMPING),
         limbRotationRadians(BodyPart.HEAD, headConfig),
         "neck"
      );
      constraints += attachSideLimb(rootId, physicsSystem, torso, BodyPart.LEFT_ARM, parts.get(BodyPart.LEFT_ARM), limbs.get(BodyPart.LEFT_ARM), "left shoulder");
      constraints += attachSideLimb(rootId, physicsSystem, torso, BodyPart.RIGHT_ARM, parts.get(BodyPart.RIGHT_ARM), limbs.get(BodyPart.RIGHT_ARM), "right shoulder");
      constraints += attachSideLimb(rootId, physicsSystem, torso, BodyPart.LEFT_LEG, parts.get(BodyPart.LEFT_LEG), limbs.get(BodyPart.LEFT_LEG), "left hip");
      constraints += attachSideLimb(rootId, physicsSystem, torso, BodyPart.RIGHT_LEG, parts.get(BodyPart.RIGHT_LEG), limbs.get(BodyPart.RIGHT_LEG), "right hip");
      return constraints;
   }

   private static int attachSideLimb(
      @Nullable UUID rootId,
      SubLevelPhysicsSystem physicsSystem,
      SpawnedPart torso,
      BodyPart bodyPart,
      SpawnedPart limb,
      @Nullable RagdollLimbConfig config,
      String name
   ) {
      if (limb == null) {
         return 0;
      }

      JointAnchor anchor = jointAnchor(bodyPart, limb.sideOffset() - torso.sideOffset());
      return attach(
         rootId,
         bodyPart,
         physicsSystem,
         torso,
         limb,
         plotAnchorLocal(torso, anchor.parentLocal()),
         plotAnchorLocal(limb, anchor.childLocal()),
         stiffness(config, LIMB_ANGULAR_STIFFNESS),
         damping(config, LIMB_ANGULAR_DAMPING),
         limbRotationRadians(bodyPart, config),
         name
      );
   }

   private static double stiffness(@Nullable RagdollLimbConfig config, double fallback) {
      return config != null && config.angularStiffness().isPresent() ? config.angularStiffness().getAsDouble() : fallback;
   }

   private static double damping(@Nullable RagdollLimbConfig config, double fallback) {
      return config != null && config.angularDamping().isPresent() ? config.angularDamping().getAsDouble() : fallback;
   }

   private static int attach(
      @Nullable UUID rootId,
      BodyPart bodyPart,
      SubLevelPhysicsSystem physicsSystem,
      SpawnedPart first,
      SpawnedPart second,
      Vector3d firstAnchor,
      Vector3d secondAnchor,
      double angularStiffness,
      double angularDamping,
      Vector3dc angularTarget,
      String name
   ) {
      if (first == null || second == null) {
         return 0;
      }

      try {
         PhysicsConstraintConfiguration<?> config = SableConstraintCompat.generic(
            firstAnchor,
            secondAnchor,
            new Quaterniond(),
            new Quaterniond(),
            Set.of(ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z)
         );
         PhysicsConstraintHandle handle = SableConstraintCompat.addConstraint(physicsSystem.getPipeline(), first.subLevel(), second.subLevel(), config);
         handle.setContactsEnabled(RagdollSettings.partSelfCollision());
         ACTIVE_CONSTRAINTS.add(handle);
         if (rootId != null) {
            JOINTS_BY_ROOT.computeIfAbsent(rootId, unused -> new EnumMap<>(BodyPart.class))
               .put(bodyPart, new RagdollJoint(handle, new Vector3d(angularTarget), angularStiffness, angularDamping));
         }

         // Angular motors hold the joint at its rest angle: pitch -> X, yaw -> Y, roll -> Z (radians).
         handle.setMotor(ConstraintJointAxis.ANGULAR_X, angularTarget.x(), angularStiffness, angularDamping, false, 0.0);
         handle.setMotor(ConstraintJointAxis.ANGULAR_Y, angularTarget.y(), angularStiffness, angularDamping, false, 0.0);
         handle.setMotor(ConstraintJointAxis.ANGULAR_Z, angularTarget.z(), angularStiffness, angularDamping, false, 0.0);

         return 1;
      } catch (Throwable error) {
         SablePlayerRagdoll.LOGGER.warn("[sable_player_ragdoll] failed to attach ragdoll constraint {}: {}", name, error.toString());
         return 0;
      }
   }

   private static JointAnchor jointAnchor(BodyPart part, double sideOffset) {
      return switch (part) {
         case HEAD -> new JointAnchor(centerLocal(0.5, NECK_TORSO_Y, 0.5), centerLocal(0.5, NECK_HEAD_Y, 0.5));
         case LEFT_ARM, RIGHT_ARM -> new JointAnchor(
            centerLocal(0.5 + sideOffset, SHOULDER_Y, 0.5),
            centerLocal(0.44, ARM_SHOULDER_Y, 0.5)
         );
         case LEFT_LEG, RIGHT_LEG -> new JointAnchor(
            centerLocal(0.5 + sideOffset * 0.5, HIP_TORSO_Y, 0.5),
            centerLocal(0.44 - sideOffset * 0.5, HIP_LEG_Y, 0.5)
         );
         case TORSO -> new JointAnchor(centerLocal(0.5, 0.5, 0.5), centerLocal(0.5, 0.5, 0.5));
      };
   }

   private static Vector3d centerLocal(double x, double y, double z) {
      return new Vector3d(x - 0.5, y - 0.5, z - 0.5);
   }

   private static Vector3d plotAnchorLocal(SpawnedPart part, Vector3dc local) {
      BlockPos plotPos = part.plotPos();
      return new Vector3d(plotPos.getX() + 0.5 + local.x(), plotPos.getY() + 0.5 + local.y(), plotPos.getZ() + 0.5 + local.z());
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

   private static Quaterniond spawnOrientation(Quaterniond baseOrientation, PartSpawn part, @Nullable RagdollLimbConfig config) {
      Vector3d r = initialRotationRadians(part, config);
      return new Quaterniond(baseOrientation).rotateY(r.y).rotateX(r.x).rotateZ(r.z);
   }

   // Resolves a limb's rest rotation (radians) as (x=pitch, y=yaw, z=roll), part defaults overridden
   // per-axis by the config. This is the motor target the ragdoll settles toward after spawning.
   private static Vector3d limbRotationRadians(PartSpawn part, @Nullable RagdollLimbConfig config) {
      double pitch = 0.0;
      double yaw = part.yawOffset();
      double roll = part.rollOffset();
      if (config != null) {
         if (config.pitchDegrees().isPresent()) pitch = Math.toRadians(config.pitchDegrees().getAsDouble());
         if (config.yawDegrees().isPresent()) yaw = Math.toRadians(config.yawDegrees().getAsDouble());
         if (config.rollDegrees().isPresent()) roll = Math.toRadians(config.rollDegrees().getAsDouble());
      }
      return new Vector3d(pitch, yaw, roll);
   }

   private static Vector3d initialRotationRadians(PartSpawn part, @Nullable RagdollLimbConfig config) {
      double pitch = 0.0;
      double yaw = part.yawOffset();
      double roll = part.rollOffset();
      if (config != null) {
         if (config.initialPitchDegrees().isPresent()) pitch = Math.toRadians(config.initialPitchDegrees().getAsDouble());
         if (config.initialYawDegrees().isPresent()) yaw = Math.toRadians(config.initialYawDegrees().getAsDouble());
         if (config.initialRollDegrees().isPresent()) roll = Math.toRadians(config.initialRollDegrees().getAsDouble());
      }
      return new Vector3d(pitch, yaw, roll);
   }

   private static Vector3d limbRotationRadians(BodyPart bodyPart, @Nullable RagdollLimbConfig config) {
      PartSpawn part = PART_BY_BODY.get(bodyPart);
      return part == null ? new Vector3d() : limbRotationRadians(part, config);
   }

   private static double rightOffset(PartSpawn part, @Nullable RagdollLimbConfig config) {
      return config != null && config.rightOffset().isPresent() ? config.rightOffset().getAsDouble() : part.rightOffset();
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

   public static void resetState() {
      ACTIVE_CONSTRAINTS.clear();
      JOINTS_BY_ROOT.clear();
      DOLL_PARTS_BY_ROOT.clear();
      BODY_PART_BY_SUBLEVEL.clear();
      ROOT_BY_PART.clear();
      ELYTRA_ROOTS.clear();
   }

   public record Doll(ServerSubLevel rootSubLevel, List<ServerSubLevel> allSubLevels, Map<BodyPart, UUID> partSubLevelIds, int constraints) {
   }

   public record RagdollJoint(PhysicsConstraintHandle handle, Vector3dc baseTarget, double baseStiffness, double baseDamping) {
   }

   public record PartSpawn(String name, BodyPart bodyPart, double rightOffset, double upOffset, double yawOffset, double rollOffset) {
   }

   private record JointAnchor(Vector3d parentLocal, Vector3d childLocal) {
   }

   private record SpawnedPart(ServerSubLevel subLevel, Vec3 worldCenter, BlockPos plotPos, double sideOffset) {
   }
}
