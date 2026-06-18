package dev.leo.sableplayerragdoll.mob.client;

import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.mob.client.ModelPartMask.SavedPart;
import dev.leo.sableplayerragdoll.mob.client.MobRagdollModelParts.NamedModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import com.mojang.logging.LogUtils;
import org.joml.Quaternionf;
import org.slf4j.Logger;

public final class MobRagdollPartBlockEntityRenderer implements BlockEntityRenderer<MobRagdollPartBlockEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float RAGDOLL_ANIMATION_TIME = 0.0F;
    private static final Set<ResourceLocation> UNLOADABLE_ENTITY_TYPES = ConcurrentHashMap.newKeySet();
    private static final Field MODEL_PART_CHILDREN = field(ModelPart.class, "children");
    private static final Field LIVING_RENDERER_LAYERS = field(LivingEntityRenderer.class, "layers");

    public MobRagdollPartBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            MobRagdollPartBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        if (!blockEntity.renderAnchor()) {
            return;
        }

        float x = blockEntity.xSize();
        float y = blockEntity.ySize();
        float z = blockEntity.zSize();

        if (renderReplay(blockEntity, partialTick, poseStack, bufferSource, packedLight)) {
            return;
        }

        if (!blockEntity.quads().isEmpty()) {
            MobRagdollQuadRenderer.render(blockEntity, poseStack, bufferSource, packedLight);
            return;
        }

        poseStack.pushPose();
        poseStack.translate((1.0F - x) * 0.5F, (1.0F - y) * 0.5F, (1.0F - z) * 0.5F);
        poseStack.scale(x, y, z);
        Minecraft.getInstance()
                .getBlockRenderer()
                .renderSingleBlock(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean renderReplay(
            MobRagdollPartBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        ResourceLocation entityTypeId = blockEntity.entityType();
        if (entityTypeId == null || blockEntity.partName().isBlank() || blockEntity.quads().isEmpty()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }

        LivingEntity livingEntity = liveSourceEntity(blockEntity, minecraft);
        if (livingEntity == null) {
            livingEntity = createFallbackEntity(blockEntity, minecraft, entityTypeId);
        }
        if (livingEntity == null) {
            return false;
        }

        EntityRenderer<? super LivingEntity> renderer = (EntityRenderer<? super LivingEntity>) minecraft.getEntityRenderDispatcher().getRenderer(livingEntity);
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            return false;
        }

        EntityModel model = livingRenderer.getModel();
        List<NamedModelPart> modelParts = MobRagdollModelParts.collectNamedParts(model);
        Set<String> keepNames = new HashSet<>(blockEntity.keepPartNames());
        keepNames.add(blockEntity.partName());

        Bounds bounds = bounds(blockEntity);
        float centerX = (bounds.minX() + bounds.maxX()) * 0.5F;
        float centerY = (bounds.minY() + bounds.maxY()) * 0.5F;
        float centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5F;

        boolean wasInvisible = livingEntity.isInvisible();
        if (wasInvisible) livingEntity.setInvisible(false);
        boolean young = blockEntity.baby();

        List<SavedPart> saved = new ArrayList<>();
        boolean pushed = false;
        try {
            setModelYoung(model, young);
            model.prepareMobModel(livingEntity, 0.0F, 0.0F, RAGDOLL_ANIMATION_TIME);
            setModelYoung(model, young);
            model.setupAnim(livingEntity, 0.0F, 0.0F, RAGDOLL_ANIMATION_TIME, 0.0F, 0.0F);
            setModelYoung(model, young);

            for (NamedModelPart modelPart : modelParts) {
                if (modelPart.hasCubes()) {
                    ModelPart part = modelPart.part();
                    if (!MobRagdollModelParts.matchesKeepNames(modelPart.names(), keepNames)) {
                        saved.add(ModelPartMask.save(part));
                        ModelPartMask.hideCubes(part);
                    } else {
                        saved.add(ModelPartMask.save(part));
                        ModelPartMask.resetToInitialPose(part);
                    }
                }
            }

            poseStack.pushPose();
            pushed = true;
            poseStack.translate(0.5F, 0.5F, 0.5F);

            float visualScale = visualFitScale(blockEntity);
            poseStack.scale(-visualScale, -visualScale, visualScale);
            poseStack.mulPose(renderCorrectionModelSpace(blockEntity));
            poseStack.translate(-centerX / 16.0F, -centerY / 16.0F, -centerZ / 16.0F);

            VertexConsumer vertices = bufferSource.getBuffer(RenderType.entityCutoutNoCull(blockEntity.texture()));
            int overlay = LivingEntityRenderer.getOverlayCoords(livingEntity, whiteOverlayProgress(livingRenderer, livingEntity, partialTick));
            model.renderToBuffer(poseStack, vertices, packedLight, overlay);
            for (RenderLayer layer : layers(livingRenderer)) {
                setLayerModelsYoung(layer, young);
                if (MobRagdollLayerRenderer.renderHeldItemLayer(layer, model, blockEntity, livingEntity, poseStack, bufferSource, packedLight)) {
                    continue;
                }
                if (MobRagdollLayerRenderer.renderDirectLayerModels(layer, model, keepNames, young, poseStack, bufferSource, packedLight)) {
                    continue;
                }
                List<NamedModelPart> layerParts = collectLayerModelParts(layer);
                if (!layerParts.isEmpty()) {
                    if (layerParts.stream().noneMatch(p -> MobRagdollModelParts.matchesKeepNames(p.names(), keepNames))) {
                        continue;
                    }
                    List<SavedPart> layerSaved = new ArrayList<>();
                    for (NamedModelPart modelPart : layerParts) {
                        if (modelPart.hasCubes()) {
                            ModelPart part = modelPart.part();
                            layerSaved.add(ModelPartMask.save(part));
                            if (!MobRagdollModelParts.matchesKeepNames(modelPart.names(), keepNames)) {
                                ModelPartMask.hideCubes(part);
                            } else {
                                ModelPartMask.resetToInitialPose(part);
                            }
                        }
                    }
                    try {
                        setLayerModelsYoung(layer, young);
                        layer.render(poseStack, bufferSource, packedLight, livingEntity, 0.0F, 0.0F, RAGDOLL_ANIMATION_TIME, RAGDOLL_ANIMATION_TIME, 0.0F, 0.0F);
                    } finally {
                        setLayerModelsYoung(layer, young);
                        for (SavedPart partPose : layerSaved) {
                            partPose.restore();
                        }
                    }
                } else {
                    if (isBlockRenderingLayer(layer)) {
                        MobPartRole role = blockEntity.role();
                        String layerName = layer.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                        if (layerName.contains("head")) {
                            if (role != MobPartRole.HEAD) continue;
                        } else if (layerName.contains("hand") || layerName.contains("arm")) {
                            if (role != MobPartRole.ARM) continue;
                        } else if (layerName.contains("leg")) {
                            if (role != MobPartRole.LEG) continue;
                        } else if (role != MobPartRole.TORSO) {
                            continue;
                        }
                        setLayerModelsYoung(layer, young);
                        layer.render(poseStack, bufferSource, packedLight, livingEntity, 0.0F, 0.0F, RAGDOLL_ANIMATION_TIME, RAGDOLL_ANIMATION_TIME, 0.0F, 0.0F);
                        setLayerModelsYoung(layer, young);
                    } else {
                        setLayerModelsYoung(layer, young);
                        layer.render(poseStack, bufferSource, packedLight, livingEntity, 0.0F, 0.0F, RAGDOLL_ANIMATION_TIME, RAGDOLL_ANIMATION_TIME, 0.0F, 0.0F);
                        setLayerModelsYoung(layer, young);
                    }
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (pushed) {
                poseStack.popPose();
            }
            for (SavedPart partPose : saved) {
                partPose.restore();
            }
            setModelYoung(model, false);
            for (RenderLayer layer : layers(livingRenderer)) {
                setLayerModelsYoung(layer, false);
            }
            if (wasInvisible) livingEntity.setInvisible(true);
        }
    }

    private static LivingEntity liveSourceEntity(MobRagdollPartBlockEntity blockEntity, Minecraft minecraft) {
        if (blockEntity.sourceEntityNetworkId() < 0 || minecraft.level == null) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(blockEntity.sourceEntityNetworkId());
        if (entity instanceof LivingEntity living && living.getType().builtInRegistryHolder().key().location().equals(blockEntity.entityType())) {
            return living;
        }
        return null;
    }

    private static LivingEntity createFallbackEntity(MobRagdollPartBlockEntity blockEntity, Minecraft minecraft, ResourceLocation entityTypeId) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
        Entity entity = entityType.create(minecraft.level);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return null;
        }
        CompoundTag variantData = blockEntity.variantData();
        if (variantData != null && !UNLOADABLE_ENTITY_TYPES.contains(entityTypeId)) {
            double x = livingEntity.getX();
            double y = livingEntity.getY();
            double z = livingEntity.getZ();
            float yRot = livingEntity.getYRot();
            float xRot = livingEntity.getXRot();
            try {
                livingEntity.load(sanitizedVariantData(variantData));
            } catch (Exception e) {
                UNLOADABLE_ENTITY_TYPES.add(entityTypeId);
                LOGGER.warn("Failed to apply ragdoll variant data for entity type {}; rendering with default appearance.", entityTypeId, e);
                entity = entityType.create(minecraft.level);
                if (!(entity instanceof LivingEntity reset)) {
                    return null;
                }
                livingEntity = reset;
            }
            livingEntity.setPos(x, y, z);
            livingEntity.setYRot(yRot);
            livingEntity.setXRot(xRot);
        }
        return livingEntity;
    }

    private static float whiteOverlayProgress(LivingEntityRenderer<?, ?> renderer, LivingEntity entity, float partialTick) {
        try {
            Method method = method(renderer.getClass(), "getWhiteOverlayProgress", LivingEntity.class, float.class);
            Object result = method.invoke(renderer, entity, partialTick);
            return result instanceof Float value ? value : 0.0F;
        } catch (ReflectiveOperationException ignored) {
            return 0.0F;
        }
    }

    private static Bounds bounds(MobRagdollPartBlockEntity blockEntity) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (MobRagdollPartBlockEntity.Quad quad : blockEntity.quads()) {
            for (MobRagdollPartBlockEntity.Vertex vertex : quad.vertices()) {
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
                maxZ = Math.max(maxZ, vertex.z());
            }
        }
        if (!Float.isFinite(minX)) {
            return new Bounds(-4.0F, -4.0F, -4.0F, 4.0F, 4.0F, 4.0F);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static float visualFitScale(MobRagdollPartBlockEntity blockEntity) {
        float scale = blockEntity.renderScale();
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            return 1.0F;
        }
        return Math.max(0.05F, Math.min(4.0F, scale));
    }

    private static Quaternionf renderCorrectionModelSpace(MobRagdollPartBlockEntity blockEntity) {
        Quaternionf initial = new Quaternionf(
                blockEntity.renderQx(),
                blockEntity.renderQy(),
                blockEntity.renderQz(),
                blockEntity.renderQw());
        return inverseOrIdentity(initial);
    }

    private static Quaternionf inverseOrIdentity(Quaternionf initial) {
        if (!Float.isFinite(initial.x) || !Float.isFinite(initial.y)
                || !Float.isFinite(initial.z) || !Float.isFinite(initial.w)
                || initial.lengthSquared() < 1.0E-6F) {
            return new Quaternionf();
        }
        return initial.normalize().invert();
    }

    @SuppressWarnings("unchecked")
    private static List<RenderLayer<?, ?>> layers(LivingEntityRenderer<?, ?> renderer) {
        try {
            return (List<RenderLayer<?, ?>>) LIVING_RENDERER_LAYERS.get(renderer);
        } catch (IllegalAccessException ignored) {
            return List.of();
        }
    }

    private static boolean isBlockRenderingLayer(RenderLayer<?, ?> layer) {
        for (Class<?> type = layer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (BlockRenderDispatcher.class.isAssignableFrom(field.getType())
                        || ItemRenderer.class.isAssignableFrom(field.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<NamedModelPart> collectLayerModelParts(RenderLayer<?, ?> layer) {
        List<NamedModelPart> parts = new ArrayList<>();
        for (Class<?> type = layer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())
                        && !EntityModel.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(layer);
                    if (value instanceof EntityModel<?> model) {
                        parts.addAll(MobRagdollModelParts.collectNamedParts(model));
                    } else if (value instanceof ModelPart modelPart) {
                        parts.addAll(MobRagdollModelParts.collectNamedParts(modelPart));
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return List.copyOf(parts);
    }

    private static CompoundTag sanitizedVariantData(CompoundTag variantData) {
        CompoundTag copy = variantData.copy();
        copy.remove("Age");
        copy.remove("ForcedAge");
        copy.remove("InLove");
        return copy;
    }

    private static void setLayerModelsYoung(RenderLayer<?, ?> layer, boolean young) {
        for (Class<?> type = layer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!EntityModel.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(layer);
                    if (value instanceof EntityModel<?> model) {
                        setModelYoung(model, young);
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    private static void setModelYoung(EntityModel<?> model, boolean young) {
        for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("young");
                if (field.getType() == boolean.class) {
                    field.setAccessible(true);
                    field.setBoolean(model, young);
                    return;
                }
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException ignored) {
                return;
            }
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException error) {
            throw new IllegalStateException("Missing field " + type.getName() + "." + name, error);
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new IllegalStateException("Missing method " + type.getName() + "." + name);
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }

}
