package com.replaymod.editor.gui;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.utils.Utils;
import com.replaymod.editor.ReplayModEditor;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiHorizontalScrollbar;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiProgressBar;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiTimelineTime;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.HorizontalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.VerticalLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.replay.gui.overlay.GuiMarkerTimeline;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.replay.ReplayFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.crash.CrashReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuiEditReplay extends AbstractGuiPopup<GuiEditReplay> {
   private static final Logger LOGGER = LogManager.getLogger();
   private final Path inputPath;
   private final GuiEditReplay.EditTimeline timeline;
   private final GuiTimelineTime<GuiMarkerTimeline> timelineTime = new GuiTimelineTime<>();
   private final GuiHorizontalScrollbar scrollbar = new GuiHorizontalScrollbar().setSize(300, 9);
   private final GuiButton zoomInButton = new GuiButton()
      .setSize(9, 9)
      .onClick(() -> this.zoomTimeline(0.6666666666666666))
      .setTexture(ReplayMod.TEXTURE, 256)
      .setSpriteUV(40, 20)
      .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.ingame.menu.zoomin"));
   private final GuiButton zoomOutButton = new GuiButton()
      .setSize(9, 9)
      .onClick(() -> this.zoomTimeline(1.5))
      .setTexture(ReplayMod.TEXTURE, 256)
      .setSpriteUV(40, 30)
      .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.ingame.menu.zoomout"));
   private final GuiPanel zoomButtonPanel = new GuiPanel()
      .setLayout(new VerticalLayout(VerticalLayout.Alignment.CENTER).setSpacing(2))
      .addElements(null, this.zoomInButton, this.zoomOutButton);
   private Set<Marker> markers;

   protected GuiEditReplay(GuiContainer container, Path inputPath) throws IOException {
      super(container);
      this.inputPath = inputPath;
      LOGGER.info("Opening replay in editor: " + inputPath);

      try (ReplayFile replayFile = ReplayMod.instance.files.open(inputPath)) {
         this.markers = replayFile.getMarkers().or(HashSet::new);
         this.timeline = new GuiEditReplay.EditTimeline(new HashSet<>(this.markers), markers -> this.markers = markers);
         this.timeline.setSize(300, 20).setMarkers().setLength(replayFile.getMetaData().getDuration()).onClick(this.timeline::setCursorPosition);
      }

      this.timelineTime.setTimeline(this.timeline);
      this.scrollbar.onValueChanged(() -> {
         this.timeline.setOffset((int)(this.scrollbar.getPosition() * this.timeline.getLength()));
         this.timeline.setZoom(this.scrollbar.getZoom());
      }).setZoom(1.0);
      GuiPanel timelinePanel = new GuiPanel().setSize(300, 40).setLayout(new CustomLayout<GuiPanel>() {
         protected void layout(GuiPanel container, int width, int height) {
            this.pos(GuiEditReplay.this.zoomButtonPanel, width - this.width(GuiEditReplay.this.zoomButtonPanel), 10);
            this.pos(GuiEditReplay.this.timelineTime, 0, 2);
            this.size(GuiEditReplay.this.timelineTime, this.x(GuiEditReplay.this.zoomButtonPanel), 8);
            this.pos(GuiEditReplay.this.timeline, 0, this.y(GuiEditReplay.this.timelineTime) + this.height(GuiEditReplay.this.timelineTime));
            this.size(GuiEditReplay.this.timeline, this.x(GuiEditReplay.this.zoomButtonPanel) - 2, 20);
            this.pos(GuiEditReplay.this.scrollbar, 0, this.y(GuiEditReplay.this.timeline) + this.height(GuiEditReplay.this.timeline) + 1);
            this.size(GuiEditReplay.this.scrollbar, this.x(GuiEditReplay.this.zoomButtonPanel) - 2, 9);
         }
      }).addElements(null, this.timelineTime, this.timeline, this.scrollbar, this.zoomButtonPanel);
      GuiButton buttonAddSplit = new GuiButton()
         .setSize(100, 20)
         .setI18nLabel("replaymod.gui.edit.split")
         .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.edit.split.tooltip"))
         .onClick(() -> {
            Marker marker = new Marker();
            marker.setTime(this.timeline.getCursorPosition());
            marker.setName("_RM_SPLIT");
            this.timeline.addMarker(marker);
         });
      GuiButton buttonInsertCut = new GuiButton()
         .setSize(100, 20)
         .setI18nLabel("replaymod.gui.edit.cut.start")
         .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.edit.cut.tooltip"))
         .onClick(() -> {
            Marker marker = new Marker();
            marker.setTime(this.timeline.getCursorPosition());
            marker.setName("_RM_START_CUT");
            this.timeline.addMarker(marker);
         });
      GuiButton buttonEndCut = new GuiButton()
         .setSize(100, 20)
         .setI18nLabel("replaymod.gui.edit.cut.end")
         .setTooltip(new GuiTooltip().setI18nText("replaymod.gui.edit.cut.tooltip"))
         .onClick(() -> {
            Marker marker = new Marker();
            marker.setTime(this.timeline.getCursorPosition());
            marker.setName("_RM_END_CUT");
            this.timeline.addMarker(marker);
         });
      GuiPanel controlPanel = new GuiPanel().setLayout(new HorizontalLayout().setSpacing(4)).addElements(null, buttonAddSplit, buttonInsertCut, buttonEndCut);
      GuiButton applyButton = new GuiButton().setI18nLabel("replaymod.gui.edit.apply").setSize(150, 20).onClick(this::apply);
      GuiButton closeButton = new GuiButton().setI18nLabel("replaymod.gui.close").setSize(150, 20).onClick(this::close);
      GuiPanel buttonPanel = new GuiPanel().setLayout(new HorizontalLayout().setSpacing(8)).addElements(null, applyButton, closeButton);
      this.popup.setLayout(new VerticalLayout(VerticalLayout.Alignment.TOP).setSpacing(10));
      this.popup.addElements(new VerticalLayout.Data(0.5), timelinePanel, controlPanel, buttonPanel);
   }

   private void zoomTimeline(double factor) {
      this.scrollbar.setZoom(this.scrollbar.getZoom() * factor);
   }

   private void apply() {
      GuiEditReplay.ProgressPopup progressPopup = new GuiEditReplay.ProgressPopup(this);
      new Thread(() -> {
         try (ReplayFile replayFile = ReplayMod.instance.files.open(this.inputPath)) {
            replayFile.writeMarkers(this.markers);
            replayFile.save();
         } catch (IOException e) {
            Utils.error(ReplayModEditor.LOGGER, this, CrashReport.makeCrashReport(e, "Writing markers"), this::close);
         }

         try {
            MarkerProcessor.apply(this.inputPath, progressPopup.progressBar::setProgress);
            ReplayMod.instance.runLater(() -> {
               progressPopup.close();
               this.close();
            });
         } catch (Throwable e) {
            e.printStackTrace();
            CrashReport crashReport = CrashReport.makeCrashReport(e, "Running marker processor");
            ReplayMod.instance.runLater(() -> Utils.error(ReplayModEditor.LOGGER, this, crashReport, () -> {
               progressPopup.close();
               this.close();
            }));
         }
      }).start();
   }

   @Override
   public void open() {
      super.open();
   }

   protected GuiEditReplay getThis() {
      return this;
   }

   private class EditTimeline extends GuiMarkerTimeline {
      EditTimeline(Set<Marker> markers, Consumer<Set<Marker>> saveMarkers) {
         super(markers, saveMarkers);
      }

      @Override
      protected void drawMarkers(GuiRenderer renderer, ReadableDimension size) {
         this.drawCutQuads(renderer, size);
         super.drawMarkers(renderer, size);
      }

      @Override
      protected void drawMarker(GuiRenderer renderer, ReadableDimension size, Marker marker, int markerX) {
         if ("_RM_SPLIT".equals(marker.getName())) {
            int height = size.getHeight() - 3 - 4 - 5 + 1;

            for (int y = 0; y < height; y += 3) {
               renderer.drawRect(markerX, 4 + y, 1, 2, Color.WHITE);
            }
         }

         super.drawMarker(renderer, size, marker, markerX);
      }

      private void drawCutQuads(GuiRenderer renderer, ReadableDimension size) {
         boolean inCut = false;
         int startTime = 0;

         for (Marker marker : this.markers.stream().sorted(Comparator.comparing(Marker::getTime)).collect(Collectors.toList())) {
            if ("_RM_START_CUT".equals(marker.getName()) && !inCut) {
               inCut = true;
               startTime = marker.getTime();
            } else if ("_RM_END_CUT".equals(marker.getName()) && inCut) {
               this.drawCutQuad(renderer, size, startTime, marker.getTime());
               inCut = false;
            }
         }

         if (inCut) {
            this.drawCutQuad(renderer, size, startTime, this.getLength());
         }
      }

      private void drawCutQuad(GuiRenderer renderer, ReadableDimension size, int startFrameTime, int endFrameTime) {
         int visibleWidth = size.getWidth() - 4 - 4;
         int startTime = this.getOffset();
         int visibleTime = (int)(this.getZoom() * this.getLength());
         int endTime = this.getOffset() + visibleTime;
         if (startFrameTime < endTime && endFrameTime > startTime) {
            double relativeStart = startFrameTime - startTime;
            double relativeEnd = endFrameTime - startTime;
            int startX = 4 + Math.max(0, (int)(relativeStart / visibleTime * visibleWidth) + 2 + 1);
            int endX = 4 + Math.min(visibleWidth, (int)(relativeEnd / visibleTime * visibleWidth) - 2);
            if (startX < endX) {
               renderer.drawRect(startX + 1, size.getHeight() - 3 - 5, endX - startX - 2, 3, Color.RED);
            }
         }
      }
   }

   private class ProgressPopup extends AbstractGuiPopup<GuiEditReplay.ProgressPopup> {
      private final GuiProgressBar progressBar = new GuiProgressBar(this.popup).setSize(300, 20);

      ProgressPopup(GuiContainer container) {
         super(container);
         this.open();
      }

      @Override
      public void close() {
         super.close();
      }

      protected GuiEditReplay.ProgressPopup getThis() {
         return this;
      }
   }
}
