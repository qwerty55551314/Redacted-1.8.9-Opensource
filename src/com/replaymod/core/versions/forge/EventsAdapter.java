package com.replaymod.core.versions.forge;

import com.replaymod.core.events.KeyBindingEventCallback;
import com.replaymod.core.events.PostRenderCallback;
import com.replaymod.core.events.PostRenderWorldCallback;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.events.PreRenderHandCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import com.replaymod.replay.events.RenderHotbarCallback;
import com.replaymod.replay.events.RenderSpectatorCrosshairCallback;

/**
 * Ported from Forge event bus subscriptions to direct hook calls.
 * The MC source (Minecraft.runTick / EntityRenderer / GuiIngame) calls these
 * static methods at the equivalent points where Forge fired its events.
 */
public class EventsAdapter {
   public static void onKeyEvent() {
      KeyBindingEventCallback.EVENT.invoker().onKeybindingEvent();
   }

   public static void preRender() {
      PreRenderCallback.EVENT.invoker().preRender();
   }

   public static void postRender() {
      PostRenderCallback.EVENT.invoker().postRender();
   }

   public static void renderCameraPath() {
      PostRenderWorldCallback.EVENT.invoker().postRenderWorld(new MatrixStack());
   }

   public static boolean renderHand() {
      return PreRenderHandCallback.EVENT.invoker().preRenderHand();
   }

   public static Boolean preRenderGameOverlayCrosshairs() {
      return RenderSpectatorCrosshairCallback.EVENT.invoker().shouldRenderSpectatorCrosshair();
   }

   public static Boolean preRenderGameOverlayHotbar() {
      return RenderHotbarCallback.EVENT.invoker().shouldRenderHotbar();
   }
}
