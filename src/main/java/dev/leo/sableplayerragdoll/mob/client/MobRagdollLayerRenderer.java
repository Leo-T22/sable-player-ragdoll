package dev.leo.sableplayerragdoll.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.leo.sableplayerragdoll.mob.block.MobPartRole;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import dev.leo.sableplayerragdoll.mob.client.ModelPartMask.SavedPart;
import dev.leo.sableplayerragdoll.mob.client.MobRagdollModelParts.NamedModelPart;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class MobRagdollLayerRenderer {
    private MobRagdollLayerRenderer() {
    }

    static boolean renderHeldItemLayer(
            RenderLayer<?, ?> layer,
            EntityModel<?> parentModel,
            MobRagdollPartBlockEntity blockEntity,
            LivingEntity livingEntity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        ItemInHandRenderer itemRenderer = itemInHandRenderer(layer);
        if (itemRenderer == null) {
            return false;
        }
        if (!(parentModel instanceof ArmedModel armedModel)) {
            return true;
        }

        HumanoidArm arm = armForPart(blockEntity);
        if (arm == null) {
            return true;
        }

        ItemStack stack = arm == livingEntity.getMainArm() ? livingEntity.getMainHandItem() : livingEntity.getOffhandItem();
        if (stack.isEmpty()) {
            return true;
        }

        poseStack.pushPose();
        if (parentModel.young) {
            poseStack.translate(0.0F, 0.75F, 0.0F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }
        armedModel.translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        boolean left = arm == HumanoidArm.LEFT;
        poseStack.translate((float) (left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        itemRenderer.renderItem(
                livingEntity,
                stack,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                left,
                poseStack,
                bufferSource,
                packedLight);
        poseStack.popPose();
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean renderDirectLayerModels(
            RenderLayer<?, ?> layer,
            EntityModel parentModel,
            Set<String> keepNames,
            boolean young,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        if (isEnergySwirlLayer(layer) || isTintedLayer(layer) || isEntityStateLayer(layer)) {
            return false;
        }
        List<LayerModel> layerModels = collectLayerModels(layer);
        if (layerModels.isEmpty()) {
            return false;
        }

        boolean rendered = false;
        for (LayerModel layerModel : layerModels) {
            if (layerModel.parts().stream().noneMatch(part -> MobRagdollModelParts.matchesKeepNames(part.names(), keepNames))) {
                continue;
            }

            List<SavedPart> saved = new ArrayList<>();
            try {
                parentModel.copyPropertiesTo(layerModel.model());
                setModelYoung(layerModel.model(), young);
                for (NamedModelPart modelPart : layerModel.parts()) {
                    if (!modelPart.hasCubes()) {
                        continue;
                    }
                    ModelPart part = modelPart.part();
                    saved.add(ModelPartMask.save(part));
                    if (!MobRagdollModelParts.matchesKeepNames(modelPart.names(), keepNames)) {
                        ModelPartMask.hideCubes(part);
                    } else {
                        ModelPartMask.resetToInitialPose(part);
                    }
                }

                VertexConsumer vertices = bufferSource.getBuffer(RenderType.entityCutoutNoCull(layerModel.texture()));
                layerModel.model().renderToBuffer(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
                rendered = true;
            } catch (Throwable ignored) {
                return false;
            } finally {
                for (SavedPart partPose : saved) {
                    partPose.restore();
                }
                setModelYoung(layerModel.model(), false);
            }
        }
        return rendered;
    }

    private static HumanoidArm armForPart(MobRagdollPartBlockEntity blockEntity) {
        if (blockEntity.role() != MobPartRole.ARM) {
            return null;
        }
        String name = blockEntity.partName().toLowerCase(Locale.ROOT);
        if (name.contains("left")) {
            return HumanoidArm.LEFT;
        }
        if (name.contains("right")) {
            return HumanoidArm.RIGHT;
        }
        return null;
    }

    private static ItemInHandRenderer itemInHandRenderer(RenderLayer<?, ?> layer) {
        for (Class<?> type = layer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!ItemInHandRenderer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(layer);
                    if (value instanceof ItemInHandRenderer renderer) {
                        return renderer;
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isEnergySwirlLayer(RenderLayer<?, ?> layer) {
        for (Class<?> type = layer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            if ("EnergySwirlLayer".equals(type.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTintedLayer(RenderLayer<?, ?> layer) {
        String name = layer.getClass().getSimpleName();
        return name.contains("Fur") || name.contains("Decor");
    }

    private static boolean isEntityStateLayer(RenderLayer<?, ?> layer) {
        String name = layer.getClass().getSimpleName();
        return name.contains("Saddle") || name.contains("Armor") || name.contains("Collar");
    }

    private static List<LayerModel> collectLayerModels(RenderLayer<?, ?> layer) {
        List<EntityModel<?>> models = new ArrayList<>();
        List<ResourceLocation> textures = new ArrayList<>();
        for (Class<?> type = layer.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    if (EntityModel.class.isAssignableFrom(field.getType())) {
                        Object value = field.get(layer);
                        if (value instanceof EntityModel<?> model) {
                            models.add(model);
                        }
                    } else if (ResourceLocation.class.isAssignableFrom(field.getType())) {
                        Object value = field.get(layer);
                        if (value instanceof ResourceLocation texture) {
                            textures.add(texture);
                        }
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        if (models.isEmpty() || textures.size() != 1) {
            return List.of();
        }

        ResourceLocation texture = textures.getFirst();
        List<LayerModel> layerModels = new ArrayList<>();
        for (EntityModel<?> model : models) {
            layerModels.add(new LayerModel(model, texture, MobRagdollModelParts.collectNamedParts(model)));
        }
        return List.copyOf(layerModels);
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

    private record LayerModel(EntityModel<?> model, ResourceLocation texture, List<NamedModelPart> parts) {
    }
}
