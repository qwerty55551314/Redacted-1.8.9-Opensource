package com.replaymod.render.gui;

import com.replaymod.core.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiScreen;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiCheckbox;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import com.replaymod.render.rendering.VideoRenderer;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

public class GuiVideoRenderer extends GuiScreen implements Tickable {
   private static final ResourceLocation NO_PREVIEW_TEXTURE = MCVer.identifier("replaymod", "logo.png");
   private final VideoRenderer renderer;
   public final GuiLabel title = new GuiLabel().setI18nText("replaymod.gui.rendering.title");
   public final GuiPanel imagePanel = (new GuiPanel() {
      @Override
      public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
         if (GuiVideoRenderer.this.previewCheckbox.isChecked()) {
            GuiVideoRenderer.this.renderPreview(renderer, size);
         } else {
            GuiVideoRenderer.this.renderNoPreview(renderer, size);
         }
      }
   }).setSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
   public final GuiCheckbox previewCheckbox = new GuiCheckbox().setI18nLabel("replaymod.gui.rendering.preview");
   public final GuiLabel renderTime = new GuiLabel();
   public final GuiLabel remainingTime = new GuiLabel();
   public final GuiProgressBar progressBar = new GuiProgressBar();
   public final GuiPanel buttonPanel = new GuiPanel().setLayout(new HorizontalLayout().setSpacing(4));
   public final GuiButton pauseButton = new GuiButton(this.buttonPanel).onClick(new Runnable() {
      @Override
      public void run() {
         if (GuiVideoRenderer.this.renderer.isPaused()) {
            GuiVideoRenderer.this.pauseButton.setI18nLabel("replaymod.gui.rendering.pause");
            GuiVideoRenderer.this.renderer.setPaused(false);
         } else {
            GuiVideoRenderer.this.pauseButton.setI18nLabel("replaymod.gui.rendering.resume");
            GuiVideoRenderer.this.renderer.setPaused(true);
         }
      }
   }).setI18nLabel("replaymod.gui.rendering.pause").setSize(150, 20);
   public final GuiButton cancelButton = (new GuiButton(this.buttonPanel) {
      boolean waitingForConfirmation;

      @Override
      public boolean mouseClick(Click click) {
         boolean result = super.mouseClick(click);
         if (this.waitingForConfirmation && !result) {
            this.setI18nLabel("replaymod.gui.rendering.cancel");
            this.waitingForConfirmation = false;
         }

         return result;
      }

      @Override
      public void onClick(Click click) {
         super.onClick(click);
         if (!this.waitingForConfirmation) {
            this.setI18nLabel("replaymod.gui.rendering.cancel.callback");
            this.waitingForConfirmation = true;
         } else {
            GuiVideoRenderer.this.renderer.cancel();
         }
      }
   }).setI18nLabel("replaymod.gui.rendering.cancel").setSize(150, 20);
   private DynamicTexture previewTexture;
   private boolean previewTextureDirty;
   private int renderTimeTaken;
   private long prevTime;
   private long frameStartTime;
   private int prevRenderedFrames;
   private int renderTimeLeft;
   private int[] renderTimes;
   private int currentIndex;

   public GuiVideoRenderer(VideoRenderer renderer) {
      final GuiPanel contentPanel = new GuiPanel(this)
         .setLayout(
            new CustomLayout<GuiPanel>() {
               protected void layout(GuiPanel container, int width, int height) {
                  this.size(GuiVideoRenderer.this.progressBar, width, 20);
                  this.pos(GuiVideoRenderer.this.title, width / 2 - this.width(GuiVideoRenderer.this.title) / 2, 0);
                  this.pos(GuiVideoRenderer.this.imagePanel, 0, this.y(GuiVideoRenderer.this.title) + this.height(GuiVideoRenderer.this.title) + 5);
                  this.pos(
                     GuiVideoRenderer.this.buttonPanel,
                     width / 2 - this.width(GuiVideoRenderer.this.buttonPanel) / 2,
                     height - this.height(GuiVideoRenderer.this.buttonPanel)
                  );
                  this.pos(
                     GuiVideoRenderer.this.progressBar,
                     width / 2 - this.width(GuiVideoRenderer.this.progressBar) / 2,
                     this.y(GuiVideoRenderer.this.buttonPanel) - 5 - this.height(GuiVideoRenderer.this.progressBar)
                  );
                  this.pos(GuiVideoRenderer.this.renderTime, 0, this.y(GuiVideoRenderer.this.progressBar) - 2 - this.height(GuiVideoRenderer.this.renderTime));
                  this.pos(
                     GuiVideoRenderer.this.remainingTime,
                     width - this.width(GuiVideoRenderer.this.remainingTime),
                     this.y(GuiVideoRenderer.this.progressBar) - 2 - this.height(GuiVideoRenderer.this.renderTime)
                  );
                  this.pos(
                     GuiVideoRenderer.this.previewCheckbox,
                     width / 2 - this.width(GuiVideoRenderer.this.previewCheckbox) / 2,
                     this.y(GuiVideoRenderer.this.renderTime) - 10 - this.height(GuiVideoRenderer.this.previewCheckbox)
                  );
                  this.size(
                     GuiVideoRenderer.this.imagePanel, width, this.y(GuiVideoRenderer.this.previewCheckbox) - 5 - this.y(GuiVideoRenderer.this.imagePanel)
                  );
               }
            }
         )
         .addElements(null, this.title, this.imagePanel, this.previewCheckbox, this.renderTime, this.remainingTime, this.progressBar, this.buttonPanel);
      this.setLayout(new CustomLayout<GuiScreen>() {
         protected void layout(GuiScreen container, int width, int height) {
            this.pos(contentPanel, 5, 3);
            this.size(contentPanel, width - 10, height - 10);
         }
      });
      this.setBackground(AbstractGuiScreen.Background.DIRT);
      this.renderTimeTaken = 0;
      this.prevTime = -1L;
      this.frameStartTime = -1L;
      this.prevRenderedFrames = 0;
      this.renderTimeLeft = 0;
      this.renderTimes = new int[50];
      this.currentIndex = 0;
      this.renderer = renderer;
   }

   @Override
   public void tick() {
      long current = System.nanoTime() / 1000000L;
      if (!this.renderer.isPaused() && this.renderer.getFramesDone() > 0 && this.prevTime > -1L) {
         this.renderTimeTaken = (int)(this.renderTimeTaken + (current - this.prevTime));
      } else {
         this.frameStartTime = current;
      }

      this.prevTime = current;
      if (this.prevRenderedFrames < this.renderer.getFramesDone()) {
         if (this.prevRenderedFrames > 0) {
            int framesRendered = this.renderer.getFramesDone() - this.prevRenderedFrames;
            int renderTime = (int)(current - this.frameStartTime);
            int avgRenderTime = renderTime / framesRendered;

            for (int i = 0; i < framesRendered; i++) {
               this.renderTimes[this.currentIndex] = avgRenderTime;
               this.currentIndex++;
               if (this.currentIndex >= this.renderTimes.length) {
                  this.currentIndex = 0;
               }
            }

            int validValues = 0;
            int totalTime = 0;

            for (int i : this.renderTimes) {
               if (i > 0) {
                  totalTime += i;
                  validValues++;
               }
            }

            float averageRenderTime = validValues > 0 ? totalTime / validValues : 0.0F;
            this.renderTimeLeft = Math.round(averageRenderTime * (this.renderer.getTotalFrames() - this.renderer.getFramesDone()) / 1000.0F);
         }

         this.frameStartTime = current;
         this.prevRenderedFrames = this.renderer.getFramesDone();
      }

      this.renderTime.setText(I18n.format("replaymod.gui.rendering.timetaken", new Object[0]) + ": " + this.secToString(this.renderTimeTaken / 1000));
      this.remainingTime.setText(I18n.format("replaymod.gui.rendering.timeleft", new Object[0]) + ": " + this.secToString(this.renderTimeLeft));
      int framesDone = this.renderer.getFramesDone();
      int framesTotal = this.renderer.getTotalFrames();
      this.progressBar.setI18nLabel("replaymod.gui.rendering.progress", framesDone, framesTotal);
      this.progressBar.setProgress((float)framesDone / framesTotal);
   }

   private String secToString(int seconds) {
      int hours = seconds / 3600;
      int min = seconds / 60 - hours * 60;
      int sec = seconds - (min * 60 + hours * 60 * 60);
      StringBuilder builder = new StringBuilder();
      if (hours > 0) {
         builder.append(hours).append(I18n.format("replaymod.gui.hours", new Object[0]));
      }

      if (min > 0 || hours > 0) {
         builder.append(min).append(I18n.format("replaymod.gui.minutes", new Object[0]));
      }

      builder.append(sec).append(I18n.format("replaymod.gui.seconds", new Object[0]));
      return builder.toString();
   }

   private synchronized void renderPreview(GuiRenderer guiRenderer, ReadableDimension size) {
      ReadableDimension videoSize = this.renderer.getFrameSize();
      int videoWidth = videoSize.getWidth();
      int videoHeight = videoSize.getHeight();
      if (this.previewTexture == null) {
         this.previewTexture = new DynamicTexture(videoWidth, videoHeight);
      }

      if (this.previewTextureDirty) {
         this.previewTexture.updateDynamicTexture();
         this.previewTextureDirty = false;
      }

      guiRenderer.bindTexture(this.previewTexture);
      this.renderPreviewTexture(guiRenderer, size, videoWidth, videoHeight);
   }

   private void renderNoPreview(GuiRenderer guiRenderer, ReadableDimension size) {
      guiRenderer.bindTexture(NO_PREVIEW_TEXTURE);
      this.renderPreviewTexture(guiRenderer, size, 1280, 720);
   }

   private void renderPreviewTexture(GuiRenderer guiRenderer, ReadableDimension size, int videoWidth, int videoHeight) {
      Dimension dimension = Utils.fitIntoBounds(new Dimension(videoWidth, videoHeight), size);
      int width = dimension.getWidth();
      int height = dimension.getHeight();
      int x = (size.getWidth() - width) / 2;
      int y = (size.getHeight() - height) / 2;
      guiRenderer.drawTexturedRect(x, y, 0, 0, width, height, videoWidth, videoHeight, videoWidth, videoHeight);
   }

   public void updatePreview(ByteBuffer buffer, ReadableDimension size) {
      if (this.previewCheckbox.isChecked() && this.previewTexture != null) {
         ((Buffer)buffer).mark();
         synchronized (this) {
            int[] data = this.previewTexture.getTextureData();
            int width = size.getWidth();

            for (int y = 0; y < size.getHeight(); y++) {
               for (int x = 0; x < width; x++) {
                  int b = buffer.get() & 255;
                  int g = buffer.get() & 255;
                  int r = buffer.get() & 255;
                  buffer.get();
                  int value = 0xFF000000 | r << 16 | g << 8 | b;
                  data[y * width + x] = value;
               }
            }

            this.previewTextureDirty = true;
         }

         ((Buffer)buffer).reset();
      }
   }
}
