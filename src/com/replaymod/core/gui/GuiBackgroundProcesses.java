package com.replaymod.core.gui;

import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.VanillaGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.callbacks.InitScreenCallback;
import net.minecraft.client.gui.GuiScreen;

public class GuiBackgroundProcesses extends EventRegistrations {
   private GuiPanel panel = new GuiPanel().setLayout(new VerticalLayout().setSpacing(10));
   private boolean reentrant;

   public GuiBackgroundProcesses() {
      this.on(InitScreenCallback.EVENT, (screen, buttons) -> this.onGuiInit(screen));
   }

   private void onGuiInit(GuiScreen guiScreen) {
      if (guiScreen == MCVer.getMinecraft().currentScreen) {
         if (!this.reentrant) {
            VanillaGuiScreen vanillaGui;
            try {
               this.reentrant = true;
               vanillaGui = VanillaGuiScreen.wrap(guiScreen);
            } finally {
               this.reentrant = false;
            }

            vanillaGui.setLayout(new CustomLayout<com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen>(vanillaGui.getLayout()) {
               protected void layout(com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen container, int width, int height) {
                  this.pos(GuiBackgroundProcesses.this.panel, width - 5 - this.width(GuiBackgroundProcesses.this.panel), 5);
               }
            }).addElements(null, this.panel);
         }
      }
   }

   public void addProcess(GuiElement<?> element) {
      this.panel.addElements(new VerticalLayout.Data(1.0), new GuiBackgroundProcesses.Element(element));
   }

   public void removeProcess(GuiElement<?> element) {
      for (GuiElement child : this.panel.getChildren()) {
         if (((GuiBackgroundProcesses.Element)child).inner == element) {
            this.panel.removeElement(child);
            return;
         }
      }
   }

   private static class Element extends AbstractGuiContainer<GuiBackgroundProcesses.Element> {
      private GuiElement inner;

      Element(GuiElement inner) {
         this.inner = inner;
         this.addElements(null, inner);
         this.setLayout(new CustomLayout<GuiBackgroundProcesses.Element>() {
            protected void layout(GuiBackgroundProcesses.Element container, int width, int height) {
               this.pos(inner, 10, 10);
               this.size(inner, width - 20, height - 20);
            }
         });
      }

      @Override
      public int getLayer() {
         return 1;
      }

      @Override
      public ReadableDimension getMinSize() {
         ReadableDimension minSize = this.inner.getMinSize();
         return new Dimension(minSize.getWidth() + 20, minSize.getHeight() + 20);
      }

      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         int u0 = 0;
         int v0 = 39;
         renderer.bindTexture(TEXTURE);
         int w = size.getWidth();
         int h = size.getHeight();
         renderer.drawTexturedRect(0, 0, 0, 39, 5, 5);
         renderer.drawTexturedRect(w - 5, 0, 12, 39, 5, 5);
         renderer.drawTexturedRect(0, h - 5, 0, 51, 5, 5);
         renderer.drawTexturedRect(w - 5, h - 5, 12, 51, 5, 5);

         for (int x = 5; x < w - 5; x += 5) {
            int rx = Math.min(5, w - 5 - x);
            renderer.drawTexturedRect(x, 0, 6, 39, rx, 5);
            renderer.drawTexturedRect(x, h - 5, 6, 51, rx, 5);
         }

         for (int y = 5; y < h - 5; y += 5) {
            int ry = Math.min(5, h - 5 - y);
            renderer.drawTexturedRect(0, y, 0, 45, 5, ry);
            renderer.drawTexturedRect(w - 5, y, 12, 45, 5, ry);
         }

         for (int x = 5; x < w - 5; x += 5) {
            for (int y = 5; y < h - 5; y += 5) {
               int rx = Math.min(5, w - 5 - x);
               int ry = Math.min(5, h - 5 - y);
               renderer.drawTexturedRect(x, y, 6, 45, rx, ry);
            }
         }

         super.draw(renderer, size, renderInfo);
      }

      protected GuiBackgroundProcesses.Element getThis() {
         return this;
      }
   }
}
