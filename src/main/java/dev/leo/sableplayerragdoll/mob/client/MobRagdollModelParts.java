package dev.leo.sableplayerragdoll.mob.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

final class MobRagdollModelParts {
    private static final Field MODEL_PART_CUBES = field(ModelPart.class, "cubes");
    private static final Field MODEL_PART_CHILDREN = field(ModelPart.class, "children");

    private MobRagdollModelParts() {
    }

    static List<NamedModelPart> collectNamedParts(EntityModel<?> model) {
        Map<ModelPart, Set<String>> names = new IdentityHashMap<>();
        for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    ModelPart part = (ModelPart) field.get(model);
                    if (part != null) {
                        collectChildren(part, names);
                        names.computeIfAbsent(part, ignored -> new HashSet<>()).add(toPartName(field.getName()));
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return toNamedParts(names);
    }

    static List<NamedModelPart> collectNamedParts(ModelPart root) {
        Map<ModelPart, Set<String>> names = new IdentityHashMap<>();
        collectChildren(root, names);
        return toNamedParts(names);
    }

    static boolean matchesKeepNames(Set<String> partNames, Set<String> keepNames) {
        if (!Collections.disjoint(partNames, keepNames)) return true;
        for (String partName : partNames) {
            for (String keepName : keepNames) {
                if (partName.contains(keepName)) return true;
            }
        }
        return false;
    }

    private static List<NamedModelPart> toNamedParts(Map<ModelPart, Set<String>> names) {
        List<NamedModelPart> parts = new ArrayList<>();
        for (Map.Entry<ModelPart, Set<String>> entry : names.entrySet()) {
            parts.add(new NamedModelPart(entry.getKey(), Set.copyOf(entry.getValue()), hasCubes(entry.getKey())));
        }
        return List.copyOf(parts);
    }

    @SuppressWarnings("unchecked")
    private static void collectChildren(ModelPart parent, Map<ModelPart, Set<String>> names) {
        try {
            Map<String, ModelPart> children = (Map<String, ModelPart>) MODEL_PART_CHILDREN.get(parent);
            for (Map.Entry<String, ModelPart> child : children.entrySet()) {
                Set<String> childNames = names.computeIfAbsent(child.getValue(), ignored -> new HashSet<>());
                childNames.clear();
                childNames.add(child.getKey());
                collectChildren(child.getValue(), names);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasCubes(ModelPart part) {
        try {
            return !((List<ModelPart.Cube>) MODEL_PART_CUBES.get(part)).isEmpty();
        } catch (IllegalAccessException ignored) {
            return false;
        }
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
        return result.toString().toLowerCase(java.util.Locale.ROOT);
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

    record NamedModelPart(ModelPart part, Set<String> names, boolean hasCubes) {
    }
}
