package com.replaymod.lib.de.johni0702.minecraft.gui.container;

import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Scrollable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.MouseUtils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.OpenGuiScreenCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.PostRenderScreenCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.lwjglx.input.Keyboard;

public class VanillaGuiScreen extends GuiScreen implements Draggable, KeyHandler, CharHandler, Scrollable, Tickable {
   private static final Map<net.minecraft.client.gui.GuiScreen, VanillaGuiScreen> WRAPPERS = Collections.synchronizedMap(new WeakHashMap<>());
   private final net.minecraft.client.gui.GuiScreen mcScreen;
   private final VanillaGuiScreen.EventHandler eventHandler = new VanillaGuiScreen.EventHandler();

   public static VanillaGuiScreen wrap(net.minecraft.client.gui.GuiScreen originalGuiScreen) {
      VanillaGuiScreen gui = WRAPPERS.get(originalGuiScreen);
      if (gui == null) {
         WRAPPERS.put(originalGuiScreen, gui = new VanillaGuiScreen(originalGuiScreen));
         gui.register();
      }

      return gui;
   }

   /**
    * Returns the active wrapper for the given vanilla screen, or null. Only
    * wrappers whose EventHandler is still registered (screen not closed yet)
    * may intercept input.
    */
   public static VanillaGuiScreen getWrapper(net.minecraft.client.gui.GuiScreen screen) {
      VanillaGuiScreen gui = WRAPPERS.get(screen);
      return gui != null && gui.eventHandler.active ? gui : null;
   }

   /**
    * Give the jGui elements of this wrapper first crack at the current mouse
    * event (already popped by the caller). Returns true if a jGui element
    * consumed it, in which case the wrapped vanilla screen must not process it.
    * Port of the original Forge {@code GuiScreenEvent.MouseInputEvent.Pre}
    * subscriber (lowest priority, cancels when the container handled it).
    */
   public boolean handleMouseEvent() throws IOException {
      this.eventHandler.mouseHandled = true;
      this.getSuperMcGui().handleMouseInput();
      // mouseHandled stays true only when a jGui child consumed the click
      // (the container's own mouseClick resets it to false). Mirror the
      // original Forge subscriber: consumed -> true (skip the wrapped screen),
      // not consumed -> false (let the wrapped vanilla screen process it).
      return this.eventHandler.mouseHandled;
   }

   /**
    * Same as {@link #handleMouseEvent()} for keyboard events. Port of the
    * original Forge {@code GuiScreenEvent.KeyboardInputEvent.Pre} subscriber.
    */
   public boolean handleKeyEvent() throws IOException {
      if (Keyboard.getEventKeyState()) {
         this.eventHandler.keyHandled = Keyboard.getEventKey() != 0;
         this.eventHandler.charHandled = Keyboard.getEventCharacter() != 0;
         this.getSuperMcGui().handleKeyboardInput();
         return this.eventHandler.keyHandled || this.eventHandler.charHandled;
      }

      return false;
   }

   @Deprecated
   public static VanillaGuiScreen setup(net.minecraft.client.gui.GuiScreen originalGuiScreen) {
      VanillaGuiScreen gui = new VanillaGuiScreen(originalGuiScreen);
      gui.register();
      return gui;
   }

   public VanillaGuiScreen(net.minecraft.client.gui.GuiScreen mcScreen) {
      this.mcScreen = mcScreen;
      this.suppressVanillaKeys = true;
      super.setBackground(AbstractGuiScreen.Background.NONE);
   }

   public void register() {
      if (!this.eventHandler.active) {
         this.eventHandler.active = true;
         this.eventHandler.register();
         this.getSuperMcGui().setWorldAndResolution(MCVer.getMinecraft(), this.mcScreen.width, this.mcScreen.height);
      }
   }

   @Override
   public void display() {
      this.getMinecraft().displayGuiScreen(this.mcScreen);
      this.register();
   }

   @Override
   public net.minecraft.client.gui.GuiScreen toMinecraft() {
      return this.mcScreen;
   }

   @Override
   public void setBackground(AbstractGuiScreen.Background background) {
      throw new UnsupportedOperationException("Cannot set background of vanilla gui screen.");
   }

   private net.minecraft.client.gui.GuiScreen getSuperMcGui() {
      return super.toMinecraft();
   }

   @Override
   public boolean mouseClick(Click click) {
      this.eventHandler.mouseHandled = false;
      return false;
   }

   @Override
   public boolean mouseDrag(Click click) {
      this.eventHandler.mouseHandled = false;
      return false;
   }

   @Override
   public boolean mouseRelease(Click click) {
      this.eventHandler.mouseHandled = false;
      return false;
   }

   @Override
   public boolean scroll(ReadablePoint mousePosition, int dWheel) {
      this.eventHandler.mouseHandled = false;
      return false;
   }

   @Override
   public boolean handleKey(KeyInput keyInput) {
      this.eventHandler.keyHandled = false;
      return false;
   }

   @Override
   public boolean handleChar(CharInput charInput) {
      this.eventHandler.charHandled = false;
      return false;
   }

   @Override
   public void tick() {
      if (this.getSuperMcGui() == this.getMinecraft().currentScreen) {
         this.getMinecraft().displayGuiScreen(null);
      }
   }

   private class EventHandler extends EventRegistrations {
      private boolean active;
      private boolean mouseHandled;
      private boolean keyHandled;
      private boolean charHandled;

      private EventHandler() {
         this.on(OpenGuiScreenCallback.EVENT, (OpenGuiScreenCallback)screen -> this.onGuiClosed());
         this.on(InitScreenCallback.Pre.EVENT, this::preGuiInit);
         this.on(PostRenderScreenCallback.EVENT, this::onGuiRender);
         this.on(PreTickCallback.EVENT, this::tickOverlay);
      }

      private void onGuiClosed() {
         this.unregister();
         if (this.active) {
            this.active = false;
            VanillaGuiScreen.this.getSuperMcGui().onGuiClosed();
            VanillaGuiScreen.WRAPPERS.remove(VanillaGuiScreen.this.mcScreen, VanillaGuiScreen.this);
         }
      }

      private void preGuiInit(net.minecraft.client.gui.GuiScreen screen) {
         if (screen == VanillaGuiScreen.this.mcScreen && this.active) {
            this.active = false;
            this.unregister();
            VanillaGuiScreen.this.getSuperMcGui().onGuiClosed();
            VanillaGuiScreen.WRAPPERS.remove(VanillaGuiScreen.this.mcScreen, VanillaGuiScreen.this);
         }
      }

      private void onGuiRender(MatrixStack stack, float partialTicks) {
         Point mousePos = MouseUtils.getMousePos();
         VanillaGuiScreen.this.getSuperMcGui().drawScreen(mousePos.getX(), mousePos.getY(), partialTicks);
      }

      private void tickOverlay() {
         VanillaGuiScreen.this.getSuperMcGui().updateScreen();
      }

   }
}
