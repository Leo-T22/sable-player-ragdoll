package dev.leo.sableplayerragdoll.mob.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel.Box;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel.ExtractedPart;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel.PartRole;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel.Pose;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel.TexturedQuad;
import dev.leo.sableplayerragdoll.mob.model.ExtractedMobModel.Vertex;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class RenderedModelExtractor {
    private static final Field MODEL_PART_CUBES = field(ModelPart.class, "cubes");
    private static final Field MODEL_PART_CHILDREN = field(ModelPart.class, "children");
    private static final Field AGEABLE_SCALE_HEAD = field(AgeableListModel.class, "scaleHead");
    private static final Field AGEABLE_BABY_Y_HEAD_OFFSET = field(AgeableListModel.class, "babyYHeadOffset");
    private static final Field AGEABLE_BABY_Z_HEAD_OFFSET = field(AgeableListModel.class, "babyZHeadOffset");
    private static final Field AGEABLE_BABY_HEAD_SCALE = field(AgeableListModel.class, "babyHeadScale");
    private static final Field AGEABLE_BABY_BODY_SCALE = field(AgeableListModel.class, "babyBodyScale");
    private static final Field AGEABLE_BODY_Y_OFFSET = field(AgeableListModel.class, "bodyYOffset");

    private RenderedModelExtractor() {
    }

    public static ExtractedMobModel extract(EntityModel<?> model) {
        return extract(model, false);
    }

    public static ExtractedMobModel extractAnimated(EntityModel<?> model) {
        return extract(model, true);
    }

    private static ExtractedMobModel extract(EntityModel<?> model, boolean animated) {
        Map<ModelPart, String> knownNames = new IdentityHashMap<>();
        Map<String, ExtractedPart> parts = new LinkedHashMap<>();
        Set<ModelPart> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ModelPart part : fieldParts(model)) {
            collectChildNames(part, knownNames);
        }

        AgeableTransforms ageableTransforms = AgeableTransforms.forModel(model);
        for (RootPart root : rootParts(model)) {
            knownNames.putIfAbsent(root.part(), root.name());
            PoseStack poseStack = ageableTransforms.basePose(root.part());
            collectPart(knownNames.getOrDefault(root.part(), root.name()), null, root.part(), knownNames, parts, visited, poseStack, animated);
        }
        for (RootPart root : declaredFieldParts(model)) {
            if (!visited.contains(root.part())) {
                knownNames.putIfAbsent(root.part(), root.name());
                PoseStack poseStack = ageableTransforms.basePose(root.part());
                collectPart(knownNames.getOrDefault(root.part(), root.name()), null, root.part(), knownNames, parts, visited, poseStack, animated);
            }
        }

        return new ExtractedMobModel(model.getClass().getSimpleName(), List.copyOf(parts.values()));
    }

    private static void collectPart(
            String name,
            String parentName,
            ModelPart part,
            Map<ModelPart, String> knownNames,
            Map<String, ExtractedPart> parts,
            Set<ModelPart> visited,
            PoseStack parentPose,
            boolean animated
    ) {
        if (!visited.add(part) || parts.containsKey(name) || !part.visible) {
            return;
        }

        List<TexturedQuad> quads = capturePartQuads(part, parentPose, animated);
        List<Box> boxes = boxes(quads);
        Map<String, ModelPart> children = children(part);
        boolean structuralContainer = boxes.isEmpty() && !children.isEmpty();
        String childParentName = parentName;
        if (!structuralContainer) {
            PartRole role = inferRole(name);
            Pose pose = transformedPose(parentPose, part, animated);
            boolean attachment = inferAttachment(name, role, boxes);
            String inferredParent = parentName != null ? parentName : inferParent(role);
            parts.put(name, new ExtractedPart(name, inferredParent, role, attachment, pose, boxes, quads));
            childParentName = name;
        }

        parentPose.pushPose();
        applyPose(parentPose, part, animated);
        for (Map.Entry<String, ModelPart> child : children.entrySet()) {
            String childName = knownNames.getOrDefault(child.getValue(), child.getKey());
            collectPart(childName, childParentName, child.getValue(), knownNames, parts, visited, parentPose, animated);
        }
        parentPose.popPose();
    }

    private static List<TexturedQuad> capturePartQuads(ModelPart part, PoseStack parentPose, boolean animated) {
        if (cubes(part).isEmpty()) {
            return List.of();
        }

        PoseStack poseStack = copyPoseStack(parentPose);
        Map<String, ModelPart> savedChildren = children(part);
        PartPose savedPose = PartPose.offsetAndRotation(part.x, part.y, part.z, part.xRot, part.yRot, part.zRot);
        try {
            MODEL_PART_CHILDREN.set(part, Map.of());
            if (!animated) {
                PartPose initial = part.getInitialPose();
                part.x = initial.x;
                part.y = initial.y;
                part.z = initial.z;
                part.xRot = initial.xRot;
                part.yRot = initial.yRot;
                part.zRot = initial.zRot;
            }
            CapturingVertexConsumer consumer = new CapturingVertexConsumer();
            part.render(poseStack, consumer, 0xF000F0, 0);
            return consumer.quads();
        } catch (IllegalAccessException ignored) {
            return List.of();
        } finally {
            try {
                MODEL_PART_CHILDREN.set(part, savedChildren);
            } catch (IllegalAccessException ignored) {
            }
            part.x = savedPose.x;
            part.y = savedPose.y;
            part.z = savedPose.z;
            part.xRot = savedPose.xRot;
            part.yRot = savedPose.yRot;
            part.zRot = savedPose.zRot;
        }
    }

    private static PoseStack copyPoseStack(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static Pose transformedPose(PoseStack parentPose, ModelPart part, boolean animated) {
        PoseStack poseStack = copyPoseStack(parentPose);
        applyPose(poseStack, part, animated);
        Matrix4f matrix = poseStack.last().pose();
        Vector3f origin = matrix.transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        org.joml.Quaternionf rot = matrix.getNormalizedRotation(new org.joml.Quaternionf());
        return new Pose(origin.x() * 16.0F, origin.y() * 16.0F, origin.z() * 16.0F, rot.x(), rot.y(), rot.z(), rot.w());
    }

    private static void applyPose(PoseStack poseStack, ModelPart part, boolean animated) {
        float x;
        float y;
        float z;
        float xRot;
        float yRot;
        float zRot;
        if (animated) {
            x = part.x;
            y = part.y;
            z = part.z;
            xRot = part.xRot;
            yRot = part.yRot;
            zRot = part.zRot;
        } else {
            PartPose pose = part.getInitialPose();
            x = pose.x;
            y = pose.y;
            z = pose.z;
            xRot = pose.xRot;
            yRot = pose.yRot;
            zRot = pose.zRot;
        }
        poseStack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        if (xRot != 0.0F || yRot != 0.0F || zRot != 0.0F) {
            poseStack.mulPose(new org.joml.Quaternionf().rotationZYX(zRot, yRot, xRot));
        }
    }

    private static List<Box> boxes(List<TexturedQuad> quads) {
        if (quads.isEmpty()) {
            return List.of();
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (TexturedQuad quad : quads) {
            for (Vertex vertex : quad.vertices()) {
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
                maxZ = Math.max(maxZ, vertex.z());
            }
        }
        if (!Float.isFinite(minX)) {
            return List.of();
        }
        return List.of(new Box(minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ, -1, -1));
    }

    private static List<ModelPart> fieldParts(EntityModel<?> model) {
        return declaredFieldParts(model).stream().map(RootPart::part).toList();
    }

    private static List<RootPart> rootParts(EntityModel<?> model) {
        List<RootPart> fields = declaredFieldParts(model);
        Set<ModelPart> children = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RootPart field : fields) {
            collectDescendants(field.part(), children);
        }
        List<RootPart> roots = new ArrayList<>();
        for (RootPart field : fields) {
            if (!children.contains(field.part())) {
                roots.add(field);
            }
        }
        return roots.isEmpty() ? fields : List.copyOf(roots);
    }

    private static List<RootPart> declaredFieldParts(EntityModel<?> model) {
        Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<RootPart> namedParts = new ArrayList<>();
        for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    ModelPart part = (ModelPart) field.get(model);
                    if (part != null && seen.add(part)) {
                        namedParts.add(new RootPart(part, toPartName(field.getName())));
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return List.copyOf(namedParts);
    }

    private static void collectDescendants(ModelPart parent, Set<ModelPart> descendants) {
        for (ModelPart child : children(parent).values()) {
            if (descendants.add(child)) {
                collectDescendants(child, descendants);
            }
        }
    }

    private static void collectChildNames(ModelPart parent, Map<ModelPart, String> knownNames) {
        for (Map.Entry<String, ModelPart> child : children(parent).entrySet()) {
            knownNames.put(child.getValue(), child.getKey());
            collectChildNames(child.getValue(), knownNames);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart part) {
        try {
            return (Map<String, ModelPart>) MODEL_PART_CHILDREN.get(part);
        } catch (IllegalAccessException ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart part) {
        try {
            return (List<ModelPart.Cube>) MODEL_PART_CUBES.get(part);
        } catch (IllegalAccessException ignored) {
            return List.of();
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

    private static PartRole inferRole(String partName) {
        String name = partName.toLowerCase(Locale.ROOT);
        if (containsAny(name, "head", "skull")) return PartRole.HEAD;
        if (containsAny(name, "body", "torso", "chest", "abdomen")) return PartRole.TORSO;
        if (containsAny(name, "arm", "hand")) return PartRole.ARM;
        if (containsAny(name, "leg", "foot", "hoof")) return PartRole.LEG;
        if (name.contains("wing")) return PartRole.WING;
        if (name.contains("tail")) return PartRole.TAIL;
        if (containsAny(name, "horn", "antler")) return PartRole.HORN;
        if (name.contains("ear")) return PartRole.EAR;
        if (containsAny(name, "beak", "nose", "snout", "mouth", "jaw", "hat", "rim", "red_thing", "comb", "jacket", "mane")) {
            return PartRole.DECORATION;
        }
        return PartRole.UNKNOWN;
    }

    private static boolean inferAttachment(String partName, PartRole role, List<Box> boxes) {
        String name = partName.toLowerCase(Locale.ROOT);
        if (containsAny(name, "beak", "nose", "snout", "mouth", "jaw", "hat", "rim", "red_thing", "comb", "jacket", "mane")) {
            return true;
        }
        if (role == PartRole.HORN || role == PartRole.EAR || role == PartRole.DECORATION) {
            return true;
        }
        if (boxes.size() == 1) {
            Box box = boxes.getFirst();
            float volume = Math.abs(box.width() * box.height() * box.depth());
            return volume > 0.0F && volume <= 12.0F;
        }
        return false;
    }

    private static String inferParent(PartRole role) {
        return switch (role) {
            case ARM, LEG, WING, TAIL -> "body";
            case HORN, EAR, DECORATION -> "head";
            default -> null;
        };
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String toPartName(String javaFieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < javaFieldName.length(); i++) {
            char c = javaFieldName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }

    private record RootPart(ModelPart part, String name) {
    }

    private enum AgeableGroup {
        HEAD,
        BODY,
        NONE
    }

    private record AgeableTransforms(
            Map<ModelPart, AgeableGroup> rootGroups,
            boolean scaleHead,
            float babyYHeadOffset,
            float babyZHeadOffset,
            float babyHeadScale,
            float babyBodyScale,
            float bodyYOffset
    ) {
        private static AgeableTransforms none() {
            return new AgeableTransforms(Map.of(), false, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F);
        }

        private static AgeableTransforms forModel(EntityModel<?> model) {
            if (!(model instanceof AgeableListModel<?> ageableModel) || !model.young) {
                return none();
            }

            try {
                Map<ModelPart, AgeableGroup> groups = new IdentityHashMap<>();
                for (ModelPart part : invokeModelParts(ageableModel, "headParts")) {
                    groups.put(part, AgeableGroup.HEAD);
                }
                for (ModelPart part : invokeModelParts(ageableModel, "bodyParts")) {
                    groups.put(part, AgeableGroup.BODY);
                }
                if (groups.isEmpty()) {
                    return none();
                }
                return new AgeableTransforms(
                        groups,
                        AGEABLE_SCALE_HEAD.getBoolean(ageableModel),
                        AGEABLE_BABY_Y_HEAD_OFFSET.getFloat(ageableModel),
                        AGEABLE_BABY_Z_HEAD_OFFSET.getFloat(ageableModel),
                        AGEABLE_BABY_HEAD_SCALE.getFloat(ageableModel),
                        AGEABLE_BABY_BODY_SCALE.getFloat(ageableModel),
                        AGEABLE_BODY_Y_OFFSET.getFloat(ageableModel)
                );
            } catch (ReflectiveOperationException ignored) {
                return none();
            }
        }

        private PoseStack basePose(ModelPart root) {
            PoseStack poseStack = new PoseStack();
            AgeableGroup group = this.rootGroups.getOrDefault(root, AgeableGroup.NONE);
            if (group == AgeableGroup.HEAD) {
                if (this.scaleHead) {
                    float scale = 1.5F / this.babyHeadScale;
                    poseStack.scale(scale, scale, scale);
                }
                poseStack.translate(0.0F, this.babyYHeadOffset / 16.0F, this.babyZHeadOffset / 16.0F);
            } else if (group == AgeableGroup.BODY) {
                float scale = 1.0F / this.babyBodyScale;
                poseStack.scale(scale, scale, scale);
                poseStack.translate(0.0F, this.bodyYOffset / 16.0F, 0.0F);
            }
            return poseStack;
        }

        private static Iterable<ModelPart> invokeModelParts(AgeableListModel<?> model, String methodName)
                throws ReflectiveOperationException {
            Method method = method(model.getClass(), methodName);
            Object result = method.invoke(model);
            if (!(result instanceof Iterable<?> iterable)) {
                return List.of();
            }
            Set<ModelPart> parts = Collections.newSetFromMap(new IdentityHashMap<>());
            List<ModelPart> ordered = new ArrayList<>();
            for (Object value : iterable) {
                if (value instanceof ModelPart part && parts.add(part)) {
                    ordered.add(part);
                }
            }
            return List.copyOf(ordered);
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

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>(4);
        private final List<TexturedQuad> quads = new ArrayList<>();
        private float u;
        private float v;
        private float normalX;
        private float normalY;
        private float normalZ;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.vertices.add(new Vertex(x * 16.0F, y * 16.0F, z * 16.0F, this.u, this.v));
            if (this.vertices.size() == 4) {
                this.quads.add(new TexturedQuad(List.copyOf(this.vertices), this.normalX, this.normalY, this.normalZ));
                this.vertices.clear();
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
            return this;
        }

        private List<TexturedQuad> quads() {
            return List.copyOf(this.quads);
        }
    }
}
