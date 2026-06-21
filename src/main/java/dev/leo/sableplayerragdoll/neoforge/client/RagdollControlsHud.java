package dev.leo.sableplayerragdoll.neoforge.client;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;

public final class RagdollControlsHud {
   private static final int FADE_IN_TICKS = 10;
   private static final int HOLD_TICKS = 100;
   private static final int FADE_OUT_TICKS = 20;
   private static final int DISPLAY_TICKS = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;

   private static List<Component> lines = List.of();
   private static int ticksRemaining;

   private RagdollControlsHud() {
   }

   public static void show(List<Component> messageLines) {
      lines = messageLines;
      ticksRemaining = DISPLAY_TICKS;
   }

   public static void hide() {
      ticksRemaining = 0;
   }

   public static void tick() {
      if (ticksRemaining > 0) {
         ticksRemaining--;
      }
   }

   public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
      if (ticksRemaining <= 0 || lines.isEmpty()) {
         return;
      }
      float remaining = ticksRemaining - deltaTracker.getGameTimeDeltaPartialTick(false);
      float elapsed = DISPLAY_TICKS - remaining;
      float progress;
      if (elapsed < FADE_IN_TICKS) {
         progress = elapsed / FADE_IN_TICKS;
      } else if (remaining < FADE_OUT_TICKS) {
         progress = remaining / FADE_OUT_TICKS;
      } else {
         progress = 1.0F;
      }
      int alpha = (int) (progress * 255.0F);
      if (alpha <= 8) {
         return;
      }
      int color = FastColor.ARGB32.color(alpha, -1);

      Font font = Minecraft.getInstance().font;
      int lineHeight = font.lineHeight + 2;
      int count = lines.size();
      graphics.pose().pushPose();
      graphics.pose().translate((float) (graphics.guiWidth() / 2), (float) (graphics.guiHeight() - 68), 0.0F);
      for (int i = 0; i < count; i++) {
         Component line = lines.get(i);
         int width = font.width(line);
         int y = -4 - (count - 1 - i) * lineHeight;
         graphics.drawStringWithBackdrop(font, line, -width / 2, y, width, color);
      }
      graphics.pose().popPose();
   }
}
