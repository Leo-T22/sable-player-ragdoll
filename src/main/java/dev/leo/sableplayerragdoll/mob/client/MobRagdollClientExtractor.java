package dev.leo.sableplayerragdoll.mob.client;

import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel.PartRole;
import dev.leo.sableplayerragdoll.mob.model.RenderedModelExtractor;
import dev.leo.sableplayerragdoll.mob.network.MobRagdollSpawnPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MobRagdollClientExtractor {
    private MobRagdollClientExtractor() {
    }

    public static void extractAndSend(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof LivingEntity livingEntity) {
            extractAndSend(livingEntity);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void extractAndSend(LivingEntity livingEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderer<? super LivingEntity> renderer =
                (EntityRenderer<? super LivingEntity>) minecraft.getEntityRenderDispatcher().getRenderer(livingEntity);
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            return;
        }

        EntityModel<?> model = livingRenderer.getModel();
        ResourceLocation texture = livingRenderer.getTextureLocation(livingEntity);

        float bodyYaw = livingEntity.yBodyRot;
        float limbSwing = livingEntity.walkAnimation.position(0.0F);
        float limbSwingAmount = livingEntity.walkAnimation.speed(0.0F);
        float headYaw = livingEntity.getYHeadRot() - bodyYaw;
        float headPitch = livingEntity.getXRot();
        EntityModel rawModel = model;
        rawModel.young = livingEntity.isBaby();
        rawModel.prepareMobModel(livingEntity, limbSwing, limbSwingAmount, 0.0F);
        rawModel.setupAnim(livingEntity, limbSwing, limbSwingAmount, (float) livingEntity.tickCount, headYaw, headPitch);

        ExtractedMobModel extracted = RenderedModelExtractor.extract(model);

        Map<String, ExtractedMobModel.ExtractedPart> animatedByName = RenderedModelExtractor.extractAnimated(model)
                .parts().stream()
                .collect(Collectors.toMap(ExtractedMobModel.ExtractedPart::name, p -> p));

        CompoundTag variantData = renderVariantData(livingEntity);
        boolean baby = livingEntity.isBaby();
        float modelScale = getModelScale(livingEntity, livingRenderer, model, extracted);
        Map<String, ExtractedMobModel.ExtractedPart> staticByName = extracted.parts().stream()
                .collect(Collectors.toMap(ExtractedMobModel.ExtractedPart::name, p -> p, (first, second) -> first));
        List<MobRagdollSpawnPacket.Part> parts = extracted.parts().stream()
                .filter(part -> !part.attachment())
                .filter(part -> !isMergedSameRoleChild(part, staticByName))
                .map(part -> toPacketPart(
                        part,
                        animatedByName.get(part.name()),
                        texture,
                        keepPartNames(extracted, part),
                        variantData,
                        modelScale,
                        baby))
                .toList();
        if (!parts.isEmpty()) {
            PacketDistributor.sendToServer(new MobRagdollSpawnPacket(
                    livingEntity.getId(),
                    livingEntity.getType().builtInRegistryHolder().key().location().toString(),
                    bodyYaw,
                    parts
            ));
        }
    }

    static CompoundTag renderVariantData(LivingEntity livingEntity) {
        CompoundTag tag = livingEntity.saveWithoutId(new CompoundTag());
        stripAgeData(tag);
        return tag;
    }

    private static void stripAgeData(CompoundTag tag) {
        tag.remove("Age");
        tag.remove("ForcedAge");
        tag.remove("InLove");
    }

    private static MobRagdollSpawnPacket.Part toPacketPart(
            ExtractedMobModel.ExtractedPart part,
            ExtractedMobModel.ExtractedPart animatedPart,
            ResourceLocation texture,
            List<String> keepPartNames,
        CompoundTag variantData,
        float modelScale,
        boolean baby) {
        MobPartRole role = toBlockRole(part.role());
        float sizeScale = modelScale;

        ExtractedMobModel.ExtractedPart pos = animatedPart != null ? animatedPart : part;
        float x = boxCenterX(pos) / 16.0F;
        float y = (24.0F - boxCenterY(pos)) / 16.0F;
        float z = boxCenterZ(pos) / 16.0F;
        float pivotX = pos.pose().x() / 16.0F;
        float pivotY = (24.0F - pos.pose().y()) / 16.0F;
        float pivotZ = pos.pose().z() / 16.0F;

        org.joml.Quaternionf initialRot = new org.joml.Quaternionf(
                part.pose().qx(), part.pose().qy(), part.pose().qz(), part.pose().qw());
        org.joml.Quaternionf animatedRot = new org.joml.Quaternionf(
                pos.pose().qx(), pos.pose().qy(), pos.pose().qz(), pos.pose().qw());
        float rotQx = animatedRot.x();
        float rotQy = animatedRot.y();
        float rotQz = animatedRot.z();
        float rotQw = animatedRot.w();

        float[] localSize = localOrientedSize(part, initialRot);
        float sizeX = localSize[0] * sizeScale;
        float sizeY = localSize[1] * sizeScale;
        float sizeZ = localSize[2] * sizeScale;
        return new MobRagdollSpawnPacket.Part(
                role,
                part.name(),
                keepPartNames,
                texture.toString(),
                part.parentName(),
                variantData,
                baby,
                sizeScale,
                x * modelScale,
                y * modelScale,
                z * modelScale,
                pivotX * modelScale,
                pivotY * modelScale,
                pivotZ * modelScale,
                rotQx,
                rotQy,
                rotQz,
                rotQw,
                initialRot.x(),
                initialRot.y(),
                initialRot.z(),
                initialRot.w(),
                sizeX,
                sizeY,
                sizeZ,
                part.quads().stream()
                        .map(quad -> new MobRagdollSpawnPacket.Quad(
                                quad.vertices().stream()
                                        .map(vertex -> new MobRagdollSpawnPacket.Vertex(vertex.x(), vertex.y(), vertex.z(), vertex.u(), vertex.v()))
                                        .toList(),
                                quad.normalX(),
                                quad.normalY(),
                                quad.normalZ()
                        ))
                        .toList()
        );
    }

    static List<String> keepPartNames(ExtractedMobModel extracted, ExtractedMobModel.ExtractedPart part) {
        List<String> names = new ArrayList<>();
        names.add(part.name());
        boolean changed;
        do {
            changed = false;
            for (ExtractedMobModel.ExtractedPart candidate : extracted.parts()) {
                if (names.contains(candidate.parentName())
                        && (candidate.attachment() || candidate.role() == part.role())
                        && !names.contains(candidate.name())) {
                    names.add(candidate.name());
                    changed = true;
                }
            }
        } while (changed);
        return List.copyOf(names);
    }

    private static boolean isMergedSameRoleChild(ExtractedMobModel.ExtractedPart part, Map<String, ExtractedMobModel.ExtractedPart> partsByName) {
        if (part.parentName() == null) {
            return false;
        }
        ExtractedMobModel.ExtractedPart parent = partsByName.get(part.parentName());
        return parent != null && parent.role() == part.role();
    }

    private static float getModelScale(LivingEntity livingEntity, LivingEntityRenderer<?, ?> renderer, EntityModel<?> model, ExtractedMobModel extracted) {
        float rendererScale = probeRendererScale(renderer, livingEntity);
        boolean ageableModelHandlesBabyScale = model instanceof AgeableListModel<?> && model.young;
        boolean rendererHandlesBabyScale = livingEntity.isBaby() && rendererScaleDiffersFromBase(rendererScale, renderer, livingEntity);
        if (livingEntity.isBaby() && !ageableModelHandlesBabyScale && !rendererHandlesBabyScale) {
            float boundsScale = getBoundingBoxScale(livingEntity, extracted);
            if (Float.isFinite(boundsScale) && boundsScale > 0.05F) {
                return rendererScale * boundsScale;
            }
        }
        return rendererScale * livingEntity.getScale();
    }

    private static final java.lang.reflect.Method SCALE_METHOD = resolveScaleMethod();

    private static java.lang.reflect.Method resolveScaleMethod() {
        try {
            java.lang.reflect.Method method = LivingEntityRenderer.class.getDeclaredMethod(
                    "scale", LivingEntity.class, PoseStack.class, float.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static float probeRendererScale(LivingEntityRenderer<?, ?> renderer, LivingEntity entity) {
        if (SCALE_METHOD == null) {
            return getRendererScale(renderer);
        }
        try {
            PoseStack poseStack = new PoseStack();
            SCALE_METHOD.invoke(renderer, entity, poseStack, 1.0F);
            org.joml.Vector3f scale = poseStack.last().pose().getScale(new org.joml.Vector3f());
            float average = (Math.abs(scale.x) + Math.abs(scale.y) + Math.abs(scale.z)) / 3.0F;
            return Float.isFinite(average) && average > 1.0E-4F ? average : 1.0F;
        } catch (Throwable error) {
            return getRendererScale(renderer);
        }
    }

    private static boolean rendererScaleDiffersFromBase(float rendererScale, LivingEntityRenderer<?, ?> renderer, LivingEntity entity) {
        float baseScale = getRendererScale(renderer) * entity.getScale();
        return Float.isFinite(rendererScale)
                && Float.isFinite(baseScale)
                && Math.abs(rendererScale - baseScale) > 1.0E-3F;
    }

    private static float getBoundingBoxScale(LivingEntity livingEntity, ExtractedMobModel extracted) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (ExtractedMobModel.ExtractedPart part : extracted.parts()) {
            if (part.attachment()) {
                continue;
            }
            for (ExtractedMobModel.Box box : part.boxes()) {
                minX = Math.min(minX, box.x());
                minY = Math.min(minY, box.y());
                minZ = Math.min(minZ, box.z());
                maxX = Math.max(maxX, box.x() + box.width());
                maxY = Math.max(maxY, box.y() + box.height());
                maxZ = Math.max(maxZ, box.z() + box.depth());
            }
        }
        if (!Float.isFinite(minX)) {
            return livingEntity.getScale();
        }
        float modelWidth = Math.max(maxX - minX, maxZ - minZ) / 16.0F;
        float modelHeight = (maxY - minY) / 16.0F;
        float widthScale = modelWidth > 0.0F ? livingEntity.getBbWidth() / modelWidth : Float.NaN;
        float heightScale = modelHeight > 0.0F ? livingEntity.getBbHeight() / modelHeight : Float.NaN;
        if (Float.isFinite(widthScale) && Float.isFinite(heightScale)) {
            return Math.max(widthScale, heightScale);
        }
        return Float.isFinite(heightScale) ? heightScale : widthScale;
    }

    private static float getRendererScale(LivingEntityRenderer<?, ?> renderer) {
        for (Class<?> c = renderer.getClass(); c != Object.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field field = c.getDeclaredField("SCALE");
                field.setAccessible(true);
                return field.getFloat(renderer);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        }
        return 1.0F;
    }

    private static MobPartRole toBlockRole(PartRole role) {
        return switch (role) {
            case HEAD -> MobPartRole.HEAD;
            case TORSO -> MobPartRole.TORSO;
            case ARM -> MobPartRole.ARM;
            case LEG -> MobPartRole.LEG;
            case WING -> MobPartRole.WING;
            case TAIL -> MobPartRole.TAIL;
            default -> MobPartRole.OTHER;
        };
    }

    private static float boxCenterX(ExtractedMobModel.ExtractedPart part) {
        if (part.boxes().isEmpty()) return 0.0F;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (ExtractedMobModel.Box box : part.boxes()) {
            min = Math.min(min, box.x());
            max = Math.max(max, box.x() + box.width());
        }
        return (min + max) * 0.5F;
    }

    private static float boxCenterY(ExtractedMobModel.ExtractedPart part) {
        if (part.boxes().isEmpty()) return 0.0F;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (ExtractedMobModel.Box box : part.boxes()) {
            min = Math.min(min, box.y());
            max = Math.max(max, box.y() + box.height());
        }
        return (min + max) * 0.5F;
    }

    private static float boxCenterZ(ExtractedMobModel.ExtractedPart part) {
        if (part.boxes().isEmpty()) return 0.0F;
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (ExtractedMobModel.Box box : part.boxes()) {
            min = Math.min(min, box.z());
            max = Math.max(max, box.z() + box.depth());
        }
        return (min + max) * 0.5F;
    }

    private static float[] localOrientedSize(ExtractedMobModel.ExtractedPart part, org.joml.Quaternionf rotation) {
        if (part.quads().isEmpty()) {
            return new float[] {8.0F, 8.0F, 8.0F};
        }

        org.joml.Quaternionf inverse = new org.joml.Quaternionf(rotation);
        if (Float.isFinite(inverse.x) && Float.isFinite(inverse.y) && Float.isFinite(inverse.z) && Float.isFinite(inverse.w)
                && inverse.lengthSquared() > 1.0E-6F) {
            inverse.normalize().invert();
        } else {
            inverse.identity();
        }

        float originX = part.pose().x();
        float originY = part.pose().y();
        float originZ = part.pose().z();
        float min = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        org.joml.Vector3f local = new org.joml.Vector3f();
        for (ExtractedMobModel.TexturedQuad quad : part.quads()) {
            for (ExtractedMobModel.Vertex vertex : quad.vertices()) {
                local.set(vertex.x() - originX, vertex.y() - originY, vertex.z() - originZ);
                inverse.transform(local);
                min = Math.min(min, local.x());
                minY = Math.min(minY, local.y());
                minZ = Math.min(minZ, local.z());
                maxX = Math.max(maxX, local.x());
                maxY = Math.max(maxY, local.y());
                maxZ = Math.max(maxZ, local.z());
            }
        }
        if (!Float.isFinite(min)) {
            return new float[] {8.0F, 8.0F, 8.0F};
        }
        return new float[] {
                Math.max(1.0F, maxX - min),
                Math.max(1.0F, maxY - minY),
                Math.max(1.0F, maxZ - minZ)
        };
    }
}
