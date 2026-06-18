package dev.leo.sableplayerragdoll.mob.model;

import java.util.List;

public record ExtractedMobModel(String sourceName, List<ExtractedPart> parts) {
    public record ExtractedPart(
            String name,
            String parentName,
            PartRole role,
            boolean attachment,
            Pose pose,
            List<Box> boxes,
            List<TexturedQuad> quads
    ) {
    }

    public record Box(float x, float y, float z, float width, float height, float depth, int textureU, int textureV) {
    }

    public record TexturedQuad(List<Vertex> vertices, float normalX, float normalY, float normalZ) {
    }

    public record Vertex(float x, float y, float z, float u, float v) {
    }
    public record Pose(float x, float y, float z, float qx, float qy, float qz, float qw) {
        public static final Pose ZERO = new Pose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    }

    public enum PartRole {
        HEAD,
        TORSO,
        ARM,
        LEG,
        WING,
        TAIL,
        HORN,
        EAR,
        DECORATION,
        UNKNOWN
    }
}
