package dev.leo.sableplayerragdoll.mob.client;

import dev.leo.sableplayerragdoll.SablePlayerRagdoll;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.fml.ModList;

public final class EmfVanillaModelCompat {
    private static final Class<?> STATEFUL_PART = optionalClass(
            "traben.entity_model_features.models.parts.EMFModelPartWithState");
    private static final Field CURRENT_VARIANT = optionalField("currentModelVariant");
    private static final Field START_RENDER = optionalDeclaredField("startOfRenderRunnable");
    private static final Method SET_VARIANT = optionalMethod("setVariantStateTo", int.class);
    private static final boolean AVAILABLE = STATEFUL_PART != null
            && CURRENT_VARIANT != null
            && START_RENDER != null
            && SET_VARIANT != null;
    private static final Map<Class<?>, List<Field>> MODEL_PART_FIELDS = new ConcurrentHashMap<>();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private EmfVanillaModelCompat() {
    }

    public static Session enter(EntityModel<?> model) {
        if (!AVAILABLE) {
            warnUnavailable(null);
            return Session.inactive();
        }

        Map<ModelPart, Integer> variants = new IdentityHashMap<>();
        Map<ModelPart, Object> renderCallbacks = new IdentityHashMap<>();
        try {
            for (ModelPart part : fieldParts(model)) {
                if (STATEFUL_PART.isInstance(part)) {
                    variants.put(part, CURRENT_VARIANT.getInt(part));
                }
            }
            if (variants.isEmpty()) {
                return Session.inactive();
            }
            for (ModelPart part : variants.keySet()) {
                SET_VARIANT.invoke(part, 0);
            }
            for (ModelPart part : fieldParts(model)) {
                if (STATEFUL_PART.isInstance(part)) {
                    Object callback = START_RENDER.get(part);
                    if (callback != null) {
                        renderCallbacks.put(part, callback);
                        START_RENDER.set(part, null);
                    }
                }
            }
            return new Session(variants, renderCallbacks);
        } catch (Throwable error) {
            restore(variants, renderCallbacks);
            warnUnavailable(error);
            return Session.inactive();
        }
    }

    private static void warnUnavailable(Throwable error) {
        if (!isEmfLoaded() || !WARNED.compareAndSet(false, true)) {
            return;
        }
        if (error == null) {
            SablePlayerRagdoll.LOGGER.warn(
                    "EMF is installed, but its preserved vanilla-model API could not be resolved; "
                            + "ragdolls will use the normal model path.");
        } else {
            SablePlayerRagdoll.LOGGER.warn(
                    "Failed to enter EMF's preserved vanilla model; ragdolls will use the normal model path: {}",
                    error.toString());
        }
    }

    private static boolean isEmfLoaded() {
        try {
            return ModList.get().isLoaded("entity_model_features");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void restore(Map<ModelPart, Integer> variants, Map<ModelPart, Object> renderCallbacks) {
        if (SET_VARIANT == null) {
            return;
        }
        variants.forEach((part, variant) -> {
            try {
                SET_VARIANT.invoke(part, variant);
            } catch (Throwable ignored) {
            }
        });
        if (START_RENDER != null) {
            renderCallbacks.forEach((part, callback) -> {
                try {
                    START_RENDER.set(part, callback);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private static List<ModelPart> fieldParts(EntityModel<?> model) {
        List<ModelPart> parts = new ArrayList<>();
        Map<ModelPart, Boolean> seen = new IdentityHashMap<>();
        for (Field field : MODEL_PART_FIELDS.computeIfAbsent(model.getClass(), EmfVanillaModelCompat::modelPartFields)) {
            try {
                Object value = field.get(model);
                if (value instanceof ModelPart part && seen.put(part, Boolean.TRUE) == null) {
                    parts.add(part);
                }
            } catch (Throwable ignored) {
            }
        }
        return parts;
    }

    private static List<Field> modelPartFields(Class<?> modelClass) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = modelClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (ModelPart.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return List.copyOf(fields);
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field optionalField(String name) {
        if (STATEFUL_PART == null) {
            return null;
        }
        try {
            Field field = STATEFUL_PART.getField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field optionalDeclaredField(String name) {
        if (STATEFUL_PART == null) {
            return null;
        }
        try {
            Field field = STATEFUL_PART.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method optionalMethod(String name, Class<?>... parameters) {
        if (STATEFUL_PART == null) {
            return null;
        }
        try {
            Method method = STATEFUL_PART.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class Session implements AutoCloseable {
        private final Map<ModelPart, Integer> variants;
        private final Map<ModelPart, Object> renderCallbacks;
        private final boolean active;
        private boolean closed;

        private Session(Map<ModelPart, Integer> variants, Map<ModelPart, Object> renderCallbacks) {
            this(variants, renderCallbacks, true);
        }

        private Session(
                Map<ModelPart, Integer> variants,
                Map<ModelPart, Object> renderCallbacks,
                boolean active
        ) {
            this.variants = variants;
            this.renderCallbacks = renderCallbacks;
            this.active = active;
        }

        private static Session inactive() {
            return new Session(Map.of(), Map.of(), false);
        }

        public boolean active() {
            return this.active;
        }

        @Override
        public void close() {
            if (this.active && !this.closed) {
                this.closed = true;
                restore(this.variants, this.renderCallbacks);
            }
        }
    }
}
