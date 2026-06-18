package dev.leo.sableplayerragdoll.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.leo.sableplayerragdoll.mob.block.entity.MobRagdollPartBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

final class MobRagdollQuadRenderer {
    private MobRagdollQuadRenderer() {
    }

    static void render(
            MobRagdollPartBlockEntity blockEntity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        Bounds bounds = bounds(blockEntity);
        float centerX = (bounds.minX() + bounds.maxX()) * 0.5F;
        float centerY = (bounds.minY() + bounds.maxY()) * 0.5F;
        float centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5F;
        VertexConsumer vertices = bufferSource.getBuffer(RenderType.entityCutoutNoCull(blockEntity.texture()));

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(renderCorrectionBlockSpace(blockEntity));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f matrix = poseStack.last().pose();
        for (MobRagdollPartBlockEntity.Quad quad : blockEntity.quads()) {
            for (MobRagdollPartBlockEntity.Vertex vertex : quad.vertices()) {
                float x = 0.5F - (vertex.x() - centerX) / 16.0F;
                float y = 0.5F - (vertex.y() - centerY) / 16.0F;
                float z = 0.5F + (vertex.z() - centerZ) / 16.0F;
                vertices.addVertex(matrix, x, y, z)
                        .setColor(-1)
                        .setUv(vertex.u(), vertex.v())
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(quad.normalX(), -quad.normalY(), quad.normalZ());
            }
        }
        poseStack.popPose();
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

    private static Quaternionf renderCorrectionBlockSpace(MobRagdollPartBlockEntity blockEntity) {
        Quaternionf initial = new Quaternionf(
                -blockEntity.renderQx(),
                -blockEntity.renderQy(),
                blockEntity.renderQz(),
                blockEntity.renderQw());
        if (!Float.isFinite(initial.x) || !Float.isFinite(initial.y)
                || !Float.isFinite(initial.z) || !Float.isFinite(initial.w)
                || initial.lengthSquared() < 1.0E-6F) {
            return new Quaternionf();
        }
        return initial.normalize().invert();
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    }
}
