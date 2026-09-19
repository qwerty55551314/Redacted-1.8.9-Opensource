package com.replaymod.lib.de.johni0702.minecraft.gui.versions.forge;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.OpenGuiScreenCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.PostRenderScreenCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.RenderHudCallback;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * Ported from Forge event bus subscriptions to direct hook calls.
 * The MC source (GuiScreen.initGui/drawScreen, Minecraft.displayGuiScreen/
 * runTick, GuiIngame) calls these static methods at the equivalent points
 * where Forge fired its events.
 */
public class EventsAdapter {
   public static void preGuiInit(GuiScreen screen) {
      InitScreenCallback.Pre.EVENT.invoker().preInitScreen(screen);
   }

   public static void onGuiInit(GuiScreen screen, List<GuiButton> buttonList) {
      InitScreenCallback.EVENT.invoker().initScreen(screen, buttonList);
   }

   public static void onGuiClosed(GuiScreen gui) {
      OpenGuiScreenCallback.EVENT.invoker().openGuiScreen(gui);
   }

   public static void onGuiRender(GuiScreen screen, float partialTicks) {
      PostRenderScreenCallback.EVENT.invoker().postRenderScreen(new MatrixStack(), partialTicks);
   }

   public static void renderOverlay(float partialTicks) {
      RenderHudCallback.EVENT.invoker().renderHud(new MatrixStack(), partialTicks);
   }

   public static void tickOverlay() {
      PreTickCallback.EVENT.invoker().preTick();
   }

   /**
    * Called from GuiScreen.handleInput per mouse event (before the vanilla
    * screen processes it). Lets jGui elements of a wrapping VanillaGuiScreen
    * consume the event first. Returns true when the event was consumed.
    */
   public static boolean onScreenMouseInput(GuiScreen screen) throws IOException {
      VanillaGuiScreen wrapper = VanillaGuiScreen.getWrapper(screen);
      return wrapper != null && wrapper.handleMouseEvent();
   }

   /**
    * Called from GuiScreen.handleInput per keyboard event. See
    * {@link #onScreenMouseInput(GuiScreen)}.
    */
   public static boolean onScreenKeyInput(GuiScreen screen) throws IOException {
      VanillaGuiScreen wrapper = VanillaGuiScreen.getWrapper(screen);
      return wrapper != null && wrapper.handleKeyEvent();
   }
}
