package com.replaymod.lib.de.johni0702.minecraft.gui.container;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.MinecraftGuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.OffsetGuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
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
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.MouseUtils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import java.io.IOException;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.util.ReportedException;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

public abstract class AbstractGuiScreen<T extends AbstractGuiScreen<T>> extends AbstractGuiContainer<T> {
   private final AbstractGuiScreen<T>.MinecraftGuiScreen wrapped = new AbstractGuiScreen.MinecraftGuiScreen();
   private Dimension screenSize;
   private AbstractGuiScreen.Background background = AbstractGuiScreen.Background.DEFAULT;
   private net.minecraft.client.gui.GuiScreen previousScreen;
   private boolean enabledRepeatedKeyEvents = true;
   private GuiLabel title;
   protected boolean suppressVanillaKeys;

   public net.minecraft.client.gui.GuiScreen toMinecraft() {
      return this.wrapped;
   }

   @Override
   public void layout(ReadableDimension size, RenderInfo renderInfo) {
      if (size == null) {
         size = this.screenSize;
      }

      if (renderInfo.layer == 0 && this.title != null) {
         this.title.layout(this.title.getMinSize(), renderInfo);
      }

      super.layout(size, renderInfo);
      if (renderInfo.layer == this.getMaxLayer()) {
         GuiElement tooltip = this.forEach(GuiElement.class, e -> e.getTooltip(renderInfo));
         if (tooltip != null) {
            tooltip.layout(tooltip.getMinSize(), renderInfo);
         }
      }
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      if (renderInfo.layer == 0) {
         switch (this.background) {
            case NONE:
            default:
               break;
            case DEFAULT:
               this.wrapped.drawDefaultBackground();
               break;
            case TRANSPARENT:
               int top = -1072689136;
               int bottom = -804253680;
               renderer.drawRect(0, 0, size.getWidth(), size.getHeight(), top, top, bottom, bottom);
               break;
            case DIRT:
               this.wrapped.drawBackground(0);
         }

         if (this.title != null) {
            ReadableDimension titleSize = this.title.getMinSize();
            int x = this.screenSize.getWidth() / 2 - titleSize.getWidth() / 2;
            OffsetGuiRenderer eRenderer = new OffsetGuiRenderer(renderer, new Point(x, 10), new Dimension(0, 0));
            this.title.draw(eRenderer, titleSize, renderInfo);
         }
      }

      super.draw(renderer, size, renderInfo);
      if (renderInfo.layer == this.getMaxLayer()) {
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

   public void setEnabledRepeatedKeyEvents(boolean enableRepeatKeyEvents) {
      this.enabledRepeatedKeyEvents = enableRepeatKeyEvents;
      if (this.wrapped.active) {
         Keyboard.enableRepeatEvents(enableRepeatKeyEvents);
      }
   }

   public void display() {
      this.previousScreen = this.getMinecraft().currentScreen;
      this.getMinecraft().displayGuiScreen(this.toMinecraft());
   }

   public AbstractGuiScreen.Background getBackground() {
      return this.background;
   }

   public boolean isEnabledRepeatedKeyEvents() {
      return this.enabledRepeatedKeyEvents;
   }

   public GuiLabel getTitle() {
      return this.title;
   }

   public void setBackground(AbstractGuiScreen.Background background) {
      this.background = background;
   }

   public void setTitle(GuiLabel title) {
      this.title = title;
   }

   public enum Background {
      NONE,
      DEFAULT,
      TRANSPARENT,
      DIRT;
   }

   protected class MinecraftGuiScreen extends net.minecraft.client.gui.GuiScreen {
      private boolean active;

      public void drawScreen(int mouseX, int mouseY, float partialTicks) {
         MatrixStack stack = new MatrixStack();
         GlStateManager.disableTexture2D();
         GlStateManager.enableTexture2D();
         int layers = AbstractGuiScreen.this.getMaxLayer();
         RenderInfo renderInfo = new RenderInfo(partialTicks, mouseX, mouseY, 0);

         for (int layer = 0; layer <= layers; layer++) {
            AbstractGuiScreen.this.layout(AbstractGuiScreen.this.screenSize, renderInfo.layer(layer));
         }

         MinecraftGuiRenderer renderer = new MinecraftGuiRenderer(stack);

         for (int layer = 0; layer <= layers; layer++) {
            AbstractGuiScreen.this.draw(renderer, AbstractGuiScreen.this.screenSize, renderInfo.layer(layer));
         }
      }

      protected void keyTyped(char typedChar, int keyCode) throws IOException {
         boolean keyHandled = keyCode != 0 && AbstractGuiScreen.this.invokeHandlers(KeyHandler.class, e -> e.handleKey(new KeyInput(keyCode)));
         boolean charHandled = typedChar != 0 && AbstractGuiScreen.this.invokeHandlers(CharHandler.class, e -> e.handleChar(new CharInput(typedChar)));
         if (keyCode == 1 && !keyHandled) {
            // Esc: return to the screen this one was opened from (or null,
            // which reopens the main menu / returns to the game, like vanilla).
            net.minecraft.client.gui.GuiScreen previous = AbstractGuiScreen.this.previousScreen;
            AbstractGuiScreen.this.getMinecraft().displayGuiScreen(previous);
            if (previous == null && AbstractGuiScreen.this.getMinecraft().currentScreen == null) {
               AbstractGuiScreen.this.getMinecraft().setIngameFocus();
            }
            return;
         }

         if (!keyHandled && !charHandled) {
            if (AbstractGuiScreen.this.suppressVanillaKeys) {
               return;
            }

            super.keyTyped(typedChar, keyCode);
         }
      }

      protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
         Click click = new Click(mouseX, mouseY, mouseButton);
         AbstractGuiScreen.this.invokeHandlers(Clickable.class, e -> e.mouseClick(click));
      }

      protected void mouseReleased(int mouseX, int mouseY, int mouseButton) {
         Click click = new Click(mouseX, mouseY, mouseButton);
         AbstractGuiScreen.this.invokeHandlers(Draggable.class, e -> e.mouseRelease(click));
      }

      protected void mouseClickMove(int mouseX, int mouseY, int mouseButton, long timeSinceLastClick) {
         Click click = new Click(mouseX, mouseY, mouseButton);
         AbstractGuiScreen.this.invokeHandlers(Draggable.class, e -> e.mouseDrag(click));
      }

      public void updateScreen() {
         AbstractGuiScreen.this.invokeAll(Tickable.class, Tickable::tick);
      }

      public void handleMouseInput() throws IOException {
         super.handleMouseInput();
         if (Mouse.getEventDWheel() != 0) {
            Point mouse = MouseUtils.getMousePos();
            int wheel = Mouse.getEventDWheel();
            AbstractGuiScreen.this.invokeHandlers(Scrollable.class, e -> e.scroll(mouse, wheel));
         }
      }

      public void onGuiClosed() {
         AbstractGuiScreen.this.invokeAll(Closeable.class, Closeable::close);
         this.active = false;
         if (AbstractGuiScreen.this.enabledRepeatedKeyEvents) {
            Keyboard.enableRepeatEvents(false);
         }
      }

      public void initGui() {
         this.active = false;
         if (AbstractGuiScreen.this.enabledRepeatedKeyEvents) {
            Keyboard.enableRepeatEvents(true);
         }

         AbstractGuiScreen.this.screenSize = new Dimension(this.width, this.height);
         AbstractGuiScreen.this.invokeAll(Loadable.class, Loadable::load);
      }

      public T getWrapper() {
         return AbstractGuiScreen.this.getThis();
      }
   }
}
