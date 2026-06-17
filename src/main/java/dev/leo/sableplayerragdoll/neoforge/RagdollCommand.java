package dev.leo.sableplayerragdoll.neoforge;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.leo.sableplayerragdoll.api.DespawnCondition;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollLaunchOptions;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class RagdollCommand {

   private static final double BASE_SPEED_M_S = 15.0;

   private RagdollCommand() {
   }

   public static LiteralArgumentBuilder<CommandSourceStack> build() {
      return Commands.literal("ragdoll")
         .requires(source -> source.hasPermission(2))
         .then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("length", IntegerArgumentType.integer(0))
               .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0.0))
                  .then(Commands.argument("pos", Vec3Argument.vec3())
                     .executes(context -> execute(
                        context.getSource(),
                        EntityArgument.getPlayers(context, "targets"),
                        IntegerArgumentType.getInteger(context, "length"),
                        DoubleArgumentType.getDouble(context, "strength"),
                        Vec3Argument.getVec3(context, "pos")
                     ))
                  )
                  .then(Commands.literal("by")
                     .then(Commands.argument("entity", EntityArgument.entity())
                        .executes(context -> execute(
                           context.getSource(),
                           EntityArgument.getPlayers(context, "targets"),
                           IntegerArgumentType.getInteger(context, "length"),
                           DoubleArgumentType.getDouble(context, "strength"),
                           EntityArgument.getEntity(context, "entity").position()
                        ))
                     )
                  )
               )
            )
         );
   }

   private static int execute(CommandSourceStack source, Collection<ServerPlayer> targets, int lengthTicks, double strength, Vec3 sourcePos) throws CommandSyntaxException {
      int launched = 0;
      for (ServerPlayer target : targets) {
         if (RagdollAPI.isRagdolled(target)) continue;
         Vec3 dir = target.position().subtract(sourcePos);
         Vec3 velocity = dir.lengthSqr() < 1e-6 ? Vec3.ZERO : dir.normalize().scale(strength * BASE_SPEED_M_S);
         RagdollLaunchOptions options = RagdollLaunchOptions.builder()
            .autoSeat(true)
            .lockDismount(true)
            .despawnConditions(List.of(DespawnCondition.afterTicks(lengthTicks)))
            .build();
         RagdollAPI.launch(target, velocity, options);
         launched++;
      }
      if (launched == 0) {
         source.sendFailure(Component.literal("No valid targets (already ragdolled or unavailable)."));
         return 0;
      }
      int result = launched;
      source.sendSuccess(() -> Component.literal("Ragdolled " + result + " player(s)."), true);
      return result;
   }
}
