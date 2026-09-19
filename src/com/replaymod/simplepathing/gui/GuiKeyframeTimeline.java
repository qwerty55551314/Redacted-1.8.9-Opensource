package com.replaymod.simplepathing.gui;

import com.replaymod.core.ReplayMod;
import com.replaymod.core.versions.MCVer;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.AbstractGuiTimeline;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadablePoint;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.vector.Vector2f;
import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MatrixStack;
import com.replaymod.lib.org.apache.commons.lang3.tuple.Pair;
import com.replaymod.pathing.properties.CameraProperties;
import com.replaymod.pathing.properties.SpectatorProperty;
import com.replaymod.pathing.properties.TimestampProperty;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.gui.overlay.GuiMarkerTimeline;
import com.replaymod.replaystudio.pathing.change.Change;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.Path;
import com.replaymod.replaystudio.pathing.path.PathSegment;
import com.replaymod.replaystudio.pathing.property.Property;
import com.replaymod.simplepathing.ReplayModSimplePathing;
import com.replaymod.simplepathing.SPTimeline;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class GuiKeyframeTimeline extends AbstractGuiTimeline<GuiKeyframeTimeline> implements Draggable {
   protected static final int KEYFRAME_SIZE = 5;
   protected static final int KEYFRAME_TEXTURE_X = 74;
   protected static final int KEYFRAME_TEXTURE_Y = 20;
   private static final int DOUBLE_CLICK_INTERVAL = 250;
   private static final int DRAGGING_THRESHOLD = 5;
   private final GuiPathing gui;
   private long lastClickedKeyframe;
   private SPTimeline.SPPath lastClickedPath;
   private long lastClickedTime;
   private boolean dragging;
   private boolean actuallyDragging;
   private int draggingStartX;
   private Change draggingChange;

   public GuiKeyframeTimeline(GuiPathing gui) {
      this.gui = gui;
   }

   @Override
   protected void drawTimelineCursor(GuiRenderer renderer, ReadableDimension size) {
      ReplayModSimplePathing mod = this.gui.getMod();
      int width = size.getWidth();
      int visibleWidth = width - 4 - 4;
      int startTime = this.getOffset();
      int visibleTime = (int)(this.getZoom() * this.getLength());
      int endTime = this.getOffset() + visibleTime;
      renderer.bindTexture(ReplayMod.TEXTURE);
      SPTimeline timeline = mod.getCurrentTimeline();
      ReadablePoint screenOffset = renderer.getOpenGlOffset();
      ReadableDimension screenSize = this.gui.overlay.getMaxSize();
      renderer.pushScissor(-screenOffset.getX(), -screenOffset.getY(), screenSize.getWidth(), screenSize.getHeight());
      timeline.getTimeline().getPaths().stream().flatMap(path -> path.getKeyframes().stream()).forEach(keyframe -> {
         if (keyframe.getTime() >= startTime && keyframe.getTime() <= endTime) {
            double relativeTime = keyframe.getTime() - startTime;
            int positonX = 4 + (int)(relativeTime / visibleTime * visibleWidth) - 2;
            int u = 74 + (mod.isSelected(keyframe) ? 5 : 0);
            int v = 20;
            if (keyframe.getValue(CameraProperties.POSITION).isPresent()) {
               if (keyframe.getValue(SpectatorProperty.PROPERTY).isPresent()) {
                  v += 10;
               }

               renderer.drawTexturedRect(positonX, 4, u, v, 5, 5);
            }

            Optional<Integer> timeProperty = keyframe.getValue(TimestampProperty.PROPERTY);
            if (timeProperty.isPresent()) {
               v += 5;
               renderer.drawTexturedRect(positonX, 9, u, v, 5, 5);
               GuiMarkerTimeline replayTimeline = this.gui.overlay.timeline;
               GuiKeyframeTimeline keyframeTimeline = this;
               ReadableDimension replayTimelineSize = replayTimeline.getLastSize();
               ReadableDimension keyframeTimelineSize = this.getLastSize();
               if (replayTimelineSize == null || keyframeTimelineSize == null) {
                  return;
               }

               Point replayTimelinePos = new Point(0, 0);
               Point keyframeTimelinePos = new Point(0, 0);
               replayTimeline.getContainer().convertFor(replayTimeline, replayTimelinePos);
               keyframeTimeline.getContainer().convertFor(keyframeTimeline, keyframeTimelinePos);
               replayTimelinePos.setLocation(-replayTimelinePos.getX(), -replayTimelinePos.getY());
               keyframeTimelinePos.setLocation(-keyframeTimelinePos.getX(), -keyframeTimelinePos.getY());
               int replayTimelineLeft = replayTimelinePos.getX();
               int replayTimelineRight = replayTimelinePos.getX() + replayTimelineSize.getWidth();
               int replayTimelineTop = replayTimelinePos.getY();
               int replayTimelineBottom = replayTimelinePos.getY() + replayTimelineSize.getHeight();
               int replayTimelineWidth = replayTimelineRight - replayTimelineLeft - 4 - 4;
               int keyframeTimelineLeft = keyframeTimelinePos.getX();
               int keyframeTimelineTop = keyframeTimelinePos.getY();
               float positionXReplayTimeline = 4.0F + (float)timeProperty.get().intValue() / replayTimeline.getLength() * replayTimelineWidth;
               float positionXKeyframeTimeline = positonX + 2.5F;
               int color = -16776961;
               float lineWidth = 2.0F;
               Tessellator tessellator = Tessellator.getInstance();
               WorldRenderer buffer = tessellator.getWorldRenderer();
               buffer.begin(3, DefaultVertexFormats.POSITION_COLOR);
               Vector2f p1 = new Vector2f(replayTimelineLeft + positionXReplayTimeline, replayTimelineTop + 4);
               Vector2f p2 = new Vector2f(replayTimelineLeft + positionXReplayTimeline, replayTimelineBottom);
               Vector2f p3 = new Vector2f(keyframeTimelineLeft + positionXKeyframeTimeline, keyframeTimelineTop);
               Vector2f p4 = new Vector2f(keyframeTimelineLeft + positionXKeyframeTimeline, keyframeTimelineTop + 4);
               MatrixStack matrixStack = renderer.getMatrixStack();
               MCVer.emitLine(matrixStack, buffer, p1, p2, -16776961, 2.0F);
               MCVer.emitLine(matrixStack, buffer, p2, p3, -16776961, 2.0F);
               MCVer.emitLine(matrixStack, buffer, p3, p4, -16776961, 2.0F);
               GL11.glEnable(2848);
               GL11.glDisable(3553);
               tessellator.draw();
               GL11.glEnable(3553);
               GL11.glDisable(2848);
            }
         }
      });
      renderer.popScissor();

      for (PathSegment segment : timeline.getPositionPath().getSegments()) {
         if (segment.getInterpolator() != null && segment.getInterpolator().getKeyframeProperties().contains(SpectatorProperty.PROPERTY)) {
            this.drawQuadOnSegment(renderer, visibleWidth, segment, 5, -16742145);
         }
      }

      for (PathSegment segment : timeline.getTimePath().getSegments()) {
         long startTimestamp = segment.getStartKeyframe().getValue(TimestampProperty.PROPERTY).orElseThrow(IllegalStateException::new).intValue();
         long endTimestamp = segment.getEndKeyframe().getValue(TimestampProperty.PROPERTY).orElseThrow(IllegalStateException::new).intValue();
         if (endTimestamp < startTimestamp) {
            this.drawQuadOnSegment(renderer, visibleWidth, segment, 10, -65536);
         }
      }

      super.drawTimelineCursor(renderer, size);
   }

   private void drawQuadOnSegment(GuiRenderer renderer, int visibleWidth, PathSegment segment, int y, int color) {
      int startTime = this.getOffset();
      int visibleTime = (int)(this.getZoom() * this.getLength());
      int endTime = this.getOffset() + visibleTime;
      long startFrameTime = segment.getStartKeyframe().getTime();
      long endFrameTime = segment.getEndKeyframe().getTime();
      if (startFrameTime < endTime && endFrameTime > startTime) {
         double relativeStart = startFrameTime - startTime;
         double relativeEnd = endFrameTime - startTime;
         int startX = 4 + Math.max(0, (int)(relativeStart / visibleTime * visibleWidth) + 2 + 1);
         int endX = 4 + Math.min(visibleWidth, (int)(relativeEnd / visibleTime * visibleWidth) - 2);
         if (startX < endX) {
            renderer.drawRect(startX + 1, y, endX - startX - 2, 3, color);
         }
      }
   }

   private Pair<SPTimeline.SPPath, Long> getKeyframe(ReadablePoint position) {
      int time = this.getTimeAt(position.getX(), position.getY());
      if (time != -1) {
         Point mouse = new Point(position);
         this.getContainer().convertFor(this, mouse);
         int mouseY = mouse.getY();
         if (mouseY > 4 && mouseY < 14) {
            SPTimeline.SPPath path;
            if (mouseY <= 9) {
               path = SPTimeline.SPPath.POSITION;
            } else {
               path = SPTimeline.SPPath.TIME;
            }

            int visibleTime = (int)(this.getZoom() * this.getLength());
            int tolerance = visibleTime * 5 / (this.getLastSize().getWidth() - 4 - 4) / 2;
            Optional<Keyframe> keyframe = this.gui
               .getMod()
               .getCurrentTimeline()
               .getPath(path)
               .getKeyframes()
               .stream()
               .filter(k -> Math.abs(k.getTime() - time) <= tolerance)
               .sorted(Comparator.comparing(k -> Math.abs(k.getTime() - time)))
               .findFirst();
            return Pair.of(path, keyframe.map(Keyframe::getTime).orElse(null));
         }
      }

      return Pair.of(null, null);
   }

   @Override
   public boolean mouseClick(Click click) {
      int time = this.getTimeAt(click.x, click.y);
      Pair<SPTimeline.SPPath, Long> pathKeyframePair = this.getKeyframe(click);
      if (pathKeyframePair.getRight() == null) {
         if (time != -1) {
            if (click.button == 0) {
               this.setCursorPosition(time);
               this.gui.getMod().setSelected(null, 0L);
            } else if (click.button == 1 && pathKeyframePair.getLeft() != null) {
               Path path = this.gui.getMod().getCurrentTimeline().getPath(pathKeyframePair.getLeft());
               path.getKeyframes().stream().flatMap(k -> k.getProperties().stream()).distinct().forEach(p -> this.applyPropertyToGame(p, path, time));
            }

            return true;
         } else {
            return false;
         }
      } else {
         SPTimeline.SPPath path = pathKeyframePair.getLeft();
         long keyframeTime = pathKeyframePair.getRight();
         if (click.button == 0) {
            long now = MCVer.milliTime();
            if (this.lastClickedKeyframe == keyframeTime && now - this.lastClickedTime < 250L) {
               this.gui.openEditKeyframePopup(path, keyframeTime);
               return true;
            }

            this.lastClickedTime = now;
            this.lastClickedKeyframe = keyframeTime;
            this.lastClickedPath = path;
            this.gui.getMod().setSelected(this.lastClickedPath, this.lastClickedKeyframe);
            this.draggingStartX = click.x;
            this.dragging = true;
         } else if (click.button == 1) {
            Keyframe keyframe = this.gui.getMod().getCurrentTimeline().getKeyframe(path, keyframeTime);

            for (Property property : keyframe.getProperties()) {
               this.applyPropertyToGame(property, keyframe);
            }
         }

         return true;
      }
   }

   private <T> void applyPropertyToGame(Property<T> property, Path path, long time) {
      Optional<T> value = path.getValue(property, time);
      if (value.isPresent()) {
         property.applyToGame(value.get(), ReplayModReplay.instance.getReplayHandler());
      }
   }

   private <T> void applyPropertyToGame(Property<T> property, Keyframe keyframe) {
      Optional<T> value = keyframe.getValue(property);
      if (value.isPresent()) {
         property.applyToGame(value.get(), ReplayModReplay.instance.getReplayHandler());
      }
   }

   @Override
   public boolean mouseDrag(Click click) {
      if (!this.dragging) {
         if (click.button == 0) {
            int time = this.getTimeAt(click.x, click.y);
            if (time != -1) {
               this.setCursorPosition(time);
               return true;
            }
         }

         return false;
      } else {
         if (!this.actuallyDragging && Math.abs(click.x - this.draggingStartX) >= 5) {
            this.actuallyDragging = true;
         }

         if (this.actuallyDragging) {
            if (!this.gui.loadEntityTracker(() -> this.mouseDrag(click))) {
               return true;
            }

            SPTimeline timeline = this.gui.getMod().getCurrentTimeline();
            Point mouse = new Point(click);
            this.getContainer().convertFor(this, mouse);
            int mouseX = mouse.getX();
            int width = this.getLastSize().getWidth();
            int bodyWidth = width - 4 - 4;
            double segmentLength = this.getLength() * this.getZoom();
            double segmentTime = segmentLength * (mouseX - 4) / bodyWidth;
            int newTime = Math.min(Math.max((int)Math.round(this.getOffset() + segmentTime), 0), this.getLength());
            if (newTime < 0) {
               return true;
            }

            while (timeline.getKeyframe(this.lastClickedPath, newTime) != null) {
               newTime++;
            }

            if (this.draggingChange != null) {
               this.draggingChange.undo(timeline.getTimeline());
            }

            this.draggingChange = timeline.moveKeyframe(this.lastClickedPath, this.lastClickedKeyframe, newTime);
            this.gui.getMod().setSelected(this.lastClickedPath, newTime);
         }

         return true;
      }
   }

   @Override
   public boolean mouseRelease(Click click) {
      if (this.dragging) {
         if (this.actuallyDragging) {
            this.gui.getMod().getCurrentTimeline().getTimeline().pushChange(this.draggingChange);
            this.draggingChange = null;
            this.actuallyDragging = false;
         }

         this.dragging = false;
         return true;
      } else {
         return false;
      }
   }

   protected GuiKeyframeTimeline getThis() {
      return this;
   }
}
