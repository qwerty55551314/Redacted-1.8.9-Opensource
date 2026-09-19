package com.replaymod.lib.de.johni0702.minecraft.gui.container;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.MinecraftGuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.OffsetGuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.CharInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Closeable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Loadable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Scrollable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Event;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.MouseUtils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.ScreenExt;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.PreTickCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.RenderHudCallback;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

public abstract class AbstractGuiOverlay<T extends AbstractGuiOverlay<T>> extends AbstractGuiContainer<T> {
   private final AbstractGuiOverlay<T>.UserInputGuiScreen userInputGuiScreen = new AbstractGuiOverlay.UserInputGuiScreen();
   private final AbstractGuiOverlay<T>.EventHandler eventHandler = new AbstractGuiOverlay.EventHandler();
   private boolean visible;
   private Dimension screenSize;
   private boolean mouseVisible;
   private boolean closeable = true;

   public boolean isVisible() {
      return this.visible;
   }

   public void setVisible(boolean visible) {
      if (this.visible != visible) {
         if (visible) {
            this.invokeAll(Loadable.class, Loadable::load);
            this.eventHandler.register();
         } else {
            this.invokeAll(Closeable.class, Closeable::close);
            this.eventHandler.unregister();
         }

         this.updateUserInputGui();
      }

      this.visible = visible;
   }

   public boolean isMouseVisible() {
      return this.mouseVisible;
   }

   public void setMouseVisible(boolean mouseVisible) {
      this.mouseVisible = mouseVisible;
      this.updateUserInputGui();
   }

   public boolean isCloseable() {
      return this.closeable;
   }

   public void setCloseable(boolean closeable) {
      this.closeable = closeable;
   }

   public boolean isAllowUserInput() {
      return ((ScreenExt)this.userInputGuiScreen).doesPassEvents();
   }

   public void setAllowUserInput(boolean allowUserInput) {
      ((ScreenExt)this.userInputGuiScreen).setPassEvents(allowUserInput);
   }

   private void updateUserInputGui() {
      Minecraft mc = this.getMinecraft();
      if (this.visible) {
         if (this.mouseVisible) {
            /* 
             * Only claim the input screen when nothing else is open.
             * Claiming when our own input screen is already current would re-run displayGuiScreen on it every frame, which fires onGuiClosed and flips mouseVisible back to false -> the overlay releases and the mouse gets re-grabbed a moment after the user released it.
             */
            if (mc.currentScreen == null) {
               mc.displayGuiScreen(this.userInputGuiScreen);
            }
         } else if (mc.currentScreen == this.userInputGuiScreen) {
            mc.displayGuiScreen(null);
         }
      }
   }

   @Override
   public void layout(ReadableDimension size, RenderInfo renderInfo) {
      if (size == null) {
         size = this.screenSize;
      }

      super.layout(size, renderInfo);
      if (this.mouseVisible && renderInfo.layer == this.getMaxLayer()) {
         GuiElement tooltip = this.forEach(GuiElement.class, e -> e.getTooltip(renderInfo));
         if (tooltip != null) {
            tooltip.layout(tooltip.getMinSize(), renderInfo);
         }
      }
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      super.draw(renderer, size, renderInfo);
      if (this.mouseVisible && renderInfo.layer == this.getMaxLayer()) {
         GuiElement tooltip = this.forEach(GuiElement.class, e -> e.getTooltip(renderInfo));
         if (tooltip != null) {
            ReadableDimension tooltipSize = tooltip.getMinSize();
            int x;
            if (renderInfo.mouseX + 8 + tooltipSize.getWidth() < this.screenSize.getWidth()) {
               x = renderInfo.mouseX + 8;
            } else {
               x = this.screenSize.getWidth() - tooltipSize.getWidth() - 1;
            }

            int y;
            if (renderInfo.mouseY + 8 + tooltipSize.getHeight() < this.screenSize.getHeight()) {
               y = renderInfo.mouseY + 8;
            } else {
               y = this.screenSize.getHeight() - tooltipSize.getHeight() - 1;
            }

            ReadablePoint position = new Point(x, y);

            try {
               OffsetGuiRenderer eRenderer = new OffsetGuiRenderer(renderer, position, tooltipSize);
               tooltip.draw(eRenderer, tooltipSize, renderInfo);
            } catch (Exception ex) {
               CrashReport crashReport = CrashReport.makeCrashReport(ex, "Rendering Gui Tooltip");
               renderInfo.addTo(crashReport);
               CrashReportCategory category = crashReport.makeCategory("Gui container details");
               MCVer.addDetail(category, "Container", this::toString);
               MCVer.addDetail(category, "Width", () -> "" + size.getWidth());
               MCVer.addDetail(category, "Height", () -> "" + size.getHeight());
               category = crashReport.makeCategory("Tooltip details");
               MCVer.addDetail(category, "Element", tooltip::toString);
               MCVer.addDetail(category, "Position", position::toString);
               MCVer.addDetail(category, "Size", tooltipSize::toString);
               throw new ReportedException(crashReport);
            }
         }
      }
   }

   @Override
   public ReadableDimension getMinSize() {
      return this.screenSize;
   }

   @Override
   public ReadableDimension getMaxSize() {
      return this.screenSize;
   }

   private class EventHandler extends EventRegistrations {
      private EventHandler() {
         this.on(RenderHudCallback.EVENT, this::renderOverlay);
         this.on(PreTickCallback.EVENT, () -> AbstractGuiOverlay.this.invokeAll(Tickable.class, Tickable::tick));
      }

      private void renderOverlay(MatrixStack stack, float partialTicks) {
         AbstractGuiOverlay.this.updateUserInputGui();
         this.updateRenderer();
         int layers = AbstractGuiOverlay.this.getMaxLayer();
         int mouseX = -1;
         int mouseY = -1;
         if (AbstractGuiOverlay.this.mouseVisible) {
            Point mouse = MouseUtils.getMousePos();
            mouseX = mouse.getX();
            mouseY = mouse.getY();
         }

         RenderInfo renderInfo = new RenderInfo(partialTicks, mouseX, mouseY, 0);

         for (int layer = 0; layer <= layers; layer++) {
            AbstractGuiOverlay.this.layout(AbstractGuiOverlay.this.screenSize, renderInfo.layer(layer));
         }

         MinecraftGuiRenderer renderer = new MinecraftGuiRenderer(stack);

         for (int layer = 0; layer <= layers; layer++) {
            AbstractGuiOverlay.this.draw(renderer, AbstractGuiOverlay.this.screenSize, renderInfo.layer(layer));
         }
      }

      private void updateRenderer() {
         Minecraft mc = AbstractGuiOverlay.this.getMinecraft();
         ScaledResolution res = MCVer.newScaledResolution(mc);
         if (AbstractGuiOverlay.this.screenSize == null
            || AbstractGuiOverlay.this.screenSize.getWidth() != res.getScaledWidth()
            || AbstractGuiOverlay.this.screenSize.getHeight() != res.getScaledHeight()) {
            AbstractGuiOverlay.this.screenSize = new Dimension(res.getScaledWidth(), res.getScaledHeight());
         }
      }
   }

   protected class UserInputGuiScreen extends net.minecraft.client.gui.GuiScreen {
      protected UserInputGuiScreen() {
         ((ScreenExt)this).setPassEvents(true);
      }

      protected void keyTyped(char typedChar, int keyCode) throws IOException {
         if (keyCode == 1) {
            /* 
             * Esc while the mouse is visible: return to camera control (hide the cursor, re-grab).
             * The pause menu is only opened by a second Esc pressed from the camera state (currentScreen == null -> Minecraft.displayInGameMenu).
             */
            Minecraft mc = AbstractGuiOverlay.this.getMinecraft();
            if (mc.currentScreen == this) {
               mc.displayGuiScreen(null);
            }
            return;
         }

         if (keyCode != 0) {
            AbstractGuiOverlay.this.invokeHandlers(KeyHandler.class, e -> e.handleKey(new KeyInput(keyCode)));
         }

         if (typedChar != 0) {
            AbstractGuiOverlay.this.invokeHandlers(CharHandler.class, e -> e.handleChar(new CharInput(typedChar)));
         }

         if (AbstractGuiOverlay.this.closeable) {
            super.keyTyped(typedChar, keyCode);
         }
      }

      protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
         Click click = new Click(mouseX, mouseY, mouseButton);
         boolean handled = AbstractGuiOverlay.this.invokeHandlers(Clickable.class, e -> e.mouseClick(click));
         if (!handled) {
            /* 
             * Click on empty overlay space (outside buttons/sliders/timeline): hand the mouse back to camera control.
             */
            AbstractGuiOverlay.this.setMouseVisible(false);
         }
      }

      public void handleKeyboardInput() throws IOException {
         /* 
          * Feed key presses into the ReplayMod keybinding system.
          * The vanilla runTick keyboard loop (the normal KeyBinding.onTick path) only sees events that currentScreen.handleInput did not drain; while this input screen is up it drains everything, so without this the replay keybinds (P play/pause, camera roll, keyframes, ...) die whenever the mouse is visible.
          */
         int keyCode = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
         if (Keyboard.getEventKeyState() && keyCode != 0) {
            com.replaymod.core.ReplayMod replayMod = com.replaymod.core.ReplayMod.instance;
            if (replayMod != null) {
               replayMod.getKeyBindingRegistry().handleKeyEvent(keyCode);
            }
         }

         super.handleKeyboardInput();
      }

      protected void mouseReleased(int mouseX, int mouseY, int mouseButton) {
         Click click = new Click(mouseX, mouseY, mouseButton);
         AbstractGuiOverlay.this.invokeHandlers(Draggable.class, e -> e.mouseRelease(click));
      }

      protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
         Click click = new Click(mouseX, mouseY, mouseButton);
         AbstractGuiOverlay.this.invokeHandlers(Draggable.class, e -> e.mouseDrag(click));
      }

      public void updateScreen() {
         AbstractGuiOverlay.this.invokeAll(Tickable.class, Tickable::tick);
      }

      public void handleMouseInput() throws IOException {
         super.handleMouseInput();
         if (Mouse.getEventDWheel() != 0) {
            Point mouse = MouseUtils.getMousePos();
            int wheel = Mouse.getEventDWheel();
            AbstractGuiOverlay.this.invokeHandlers(Scrollable.class, e -> e.scroll(mouse, wheel));
         }
      }

      public void onGuiClosed() {
         if (AbstractGuiOverlay.this.closeable) {
            AbstractGuiOverlay.this.mouseVisible = false;
         }
      }

      public AbstractGuiOverlay<T> getOverlay() {
         return AbstractGuiOverlay.this;
      }
   }
}
