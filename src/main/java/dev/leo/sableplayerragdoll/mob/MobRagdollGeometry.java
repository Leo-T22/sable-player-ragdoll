package dev.leo.sableplayerragdoll.mob;

import dev.leo.sableplayerragdoll.mob.MobRagdollAssembly.PartSpawn;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;

final class MobRagdollGeometry {
    private static final float COLLISION_SIZE_SCALE = 0.85F;

    private MobRagdollGeometry() {
    }

    static Set<BlockPos> collisionBlocks(BlockPos anchor, PartSpawn part) {
        Set<BlockPos> blocks = new LinkedHashSet<>();
        int minX = minAxisBlockOffset(collisionPixels(part.xSize()));
        int maxX = maxAxisBlockOffset(collisionPixels(part.xSize()));
        int minY = minAxisBlockOffset(collisionPixels(part.ySize()));
        int maxY = maxAxisBlockOffset(collisionPixels(part.ySize()));
        int minZ = minAxisBlockOffset(collisionPixels(part.zSize()));
        int maxZ = maxAxisBlockOffset(collisionPixels(part.zSize()));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(anchor.offset(x, y, z));
                }
            }
        }
        return blocks;
    }

    static int minAxisBlockOffset(float pixels) {
        float size = Math.max(1.0F, pixels);
        return (int) Math.floor((8.0F - size * 0.5F) / 16.0F);
    }

    static int maxAxisBlockOffset(float pixels) {
        float size = Math.max(1.0F, pixels);
        return (int) Math.floor((8.0F + size * 0.5F - 0.0001F) / 16.0F);
    }

    static int slicePixels(float pixels, int blockOffset) {
        return clampPixels(sliceMax(pixels, blockOffset) - sliceMin(pixels, blockOffset));
    }

    static float collisionPixels(float visualPixels) {
        return Math.max(1.0F, visualPixels * COLLISION_SIZE_SCALE);
    }

    private static int clampPixels(float value) {
        return Math.max(1, Math.min(16, Math.round(value)));
    }

    private static float sliceMin(float pixels, int blockOffset) {
        float desiredMin = 8.0F - Math.max(1.0F, pixels) * 0.5F;
        return Math.max(0.0F, desiredMin - blockOffset * 16.0F);
    }

    private static float sliceMax(float pixels, int blockOffset) {
        float desiredMax = 8.0F + Math.max(1.0F, pixels) * 0.5F;
        return Math.min(16.0F, desiredMax - blockOffset * 16.0F);
    }
}
