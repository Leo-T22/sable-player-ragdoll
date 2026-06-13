package dev.leo.sableplayerragdoll.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

public final class SableConstraintCompat {
   private static final Constructor<?> FREE_CTOR = resolve(
      new Class<?>[]{Vector3dc.class, Vector3dc.class, Quaterniondc.class},
      "dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration",
      "dev.ryanhcode.sable.api.physics.constraint.free.FreeConstraintConfiguration"
   );

   private static final Constructor<?> GENERIC_CTOR = resolve(
      new Class<?>[]{Vector3dc.class, Vector3dc.class, Quaterniondc.class, Quaterniondc.class, Set.class},
      "dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration",
      "dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration"
   );

   private static final Method ADD_CONSTRAINT = resolveAddConstraint();

   private SableConstraintCompat() {
   }

   public static PhysicsConstraintHandle addConstraint(PhysicsPipeline pipeline, Object body1, Object body2, PhysicsConstraintConfiguration<?> config) {
      try {
         return (PhysicsConstraintHandle) ADD_CONSTRAINT.invoke(pipeline, body1, body2, config);
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException("Failed to add Sable constraint", e);
      }
   }

   private static Method resolveAddConstraint() {
      for (Method method : PhysicsPipeline.class.getMethods()) {
         if (method.getName().equals("addConstraint") && method.getParameterCount() == 3) {
            method.setAccessible(true);
            return method;
         }
      }
      throw new IllegalStateException("No addConstraint(...) method found on Sable PhysicsPipeline");
   }

   public static PhysicsConstraintConfiguration<?> free(Vector3dc pos1, Vector3dc pos2, Quaterniondc orientation) {
      return newInstance(FREE_CTOR, pos1, pos2, orientation);
   }

   public static PhysicsConstraintConfiguration<?> generic(
      Vector3dc pos1,
      Vector3dc pos2,
      Quaterniondc orientation1,
      Quaterniondc orientation2,
      Set<ConstraintJointAxis> lockedAxes
   ) {
      return newInstance(GENERIC_CTOR, pos1, pos2, orientation1, orientation2, lockedAxes);
   }

   private static PhysicsConstraintConfiguration<?> newInstance(Constructor<?> ctor, Object... args) {
      try {
         return (PhysicsConstraintConfiguration<?>) ctor.newInstance(args);
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException("Failed to instantiate Sable constraint configuration " + ctor.getDeclaringClass().getName(), e);
      }
   }

   private static Constructor<?> resolve(Class<?>[] params, String... classNames) {
      for (String className : classNames) {
         try {
            return Class.forName(className).getConstructor(params);
         } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Try the next candidate package.
         }
      }
      throw new IllegalStateException("No compatible Sable constraint configuration class found (looked for " + String.join(", ", classNames) + ")");
   }
}
