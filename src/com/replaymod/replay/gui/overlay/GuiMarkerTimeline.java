package com.replaymod.replay.gui.overlay;

import com.replaymod.core.ReplayMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.AbstractGuiTimeline;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Draggable;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.Utils;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replaystudio.data.Marker;
import com.replaymod.replaystudio.util.Location;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.resources.I18n;

public class GuiMarkerTimeline extends AbstractGuiTimeline<GuiMarkerTimeline> implements Draggable, KeyHandler {
   protected static final int TEXTURE_MARKER_X = 109;
   protected static final int TEXTURE_MARKER_Y = 20;
   protected static final int TEXTURE_MARKER_SELECTED_X = 114;
   protected static final int TEXTURE_MARKER_SELECTED_Y = 20;
   protected static final int MARKER_SIZE = 5;
   @Nullable
   private final ReplayHandler replayHandler;
   private final Consumer<Set<Marker>> saveMarkers;
   protected Set<Marker> markers;
   private ReadableDimension lastSize;
   private Marker selectedMarker;
   private int draggingStartX;
   private int draggingTimeDelta;
   private boolean dragging;
   private long lastClickTime;

   public GuiMarkerTimeline(@Nonnull ReplayHandler replayHandler) {
      this.replayHandler = replayHandler;

      try {
         this.markers = replayHandler.getReplayFile().getMarkers().or(HashSet::new);
      } catch (IOException e) {
         ReplayModReplay.LOGGER.error("Failed to get markers from replay", e);
         this.markers = new HashSet<>();
      }

      this.saveMarkers = markers -> {
         try {
            replayHandler.getReplayFile().writeMarkers(markers);
         } catch (IOException e) {
            ReplayModReplay.LOGGER.error("Failed to save markers to replay", e);
         }
      };
   }

   public GuiMarkerTimeline(Set<Marker> markers, Consumer<Set<Marker>> saveMarkers) {
      this.replayHandler = null;
      this.markers = markers;
      this.saveMarkers = saveMarkers;
   }

   protected GuiMarkerTimeline getThis() {
      return this;
   }

   @Override
   public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo renderInfo) {
      this.lastSize = size;
      super.draw(renderer, size, renderInfo);
      this.drawMarkers(renderer, size);
   }

   @Override
   protected void drawMarkers(GuiRenderer renderer, ReadableDimension size) {
      renderer.bindTexture(ReplayMod.TEXTURE);

      for (Marker marker : this.markers) {
         this.drawMarker(renderer, size, marker);
      }
   }

   protected void drawMarker(GuiRenderer renderer, ReadableDimension size, Marker marker) {
      int visibleLength = (int)(this.getLength() * this.getZoom());
      int markerPos = Utils.clamp(marker.getTime(), this.getOffset(), this.getOffset() + visibleLength);
      double positionInVisible = markerPos - this.getOffset();
      double fractionOfVisible = positionInVisible / visibleLength;
      int markerX = (int)(4.0 + fractionOfVisible * (size.getWidth() - 4 - 4));
      this.drawMarker(renderer, size, marker, markerX);
   }

   protected void drawMarker(GuiRenderer renderer, ReadableDimension size, Marker marker, int markerX) {
      int textureX;
      int textureY;
      if (marker.equals(this.selectedMarker)) {
         textureX = 114;
         textureY = 20;
      } else {
         textureX = 109;
         textureY = 20;
      }

      renderer.drawTexturedRect(markerX - 2, size.getHeight() - 3 - 5, textureX, textureY, 5, 5);
   }

   protected Marker getMarkerAt(int mouseX, int mouseY) {
      if (this.lastSize == null) {
         return null;
      }

      Point mouse = new Point(mouseX, mouseY);
      this.getContainer().convertFor(this, mouse);
      mouseX = mouse.getX();
      mouseY = mouse.getY();
      if (mouseX >= 0 && mouseY >= this.lastSize.getHeight() - 3 - 5 && mouseX <= this.lastSize.getWidth() && mouseY <= this.lastSize.getHeight() - 3) {
         int visibleLength = (int)(this.getLength() * this.getZoom());
         int contentWidth = this.lastSize.getWidth() - 4 - 4;

         for (Marker marker : this.markers) {
            int markerPos = Utils.clamp(marker.getTime(), this.getOffset(), this.getOffset() + visibleLength);
            double positionInVisible = markerPos - this.getOffset();
            double fractionOfVisible = positionInVisible / visibleLength;
            int markerX = (int)(4.0 + fractionOfVisible * contentWidth);
            if (Math.abs(markerX - mouseX) < 3) {
               return marker;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Override
   public boolean mouseClick(Click click) {
      Marker marker = this.getMarkerAt(click.x, click.y);
      if (marker != null) {
         if (click.button == 0) {
            long now = System.currentTimeMillis();
            this.selectedMarker = marker;
            if (Math.abs(this.lastClickTime - now) > 500L) {
               this.draggingStartX = click.x;
               this.draggingTimeDelta = marker.getTime() - this.getTimeAt(click.x, click.y);
            } else {
               new GuiEditMarkerPopup(this.getContainer(), marker, updatedMarker -> {
                  this.markers.remove(marker);
                  this.markers.add(updatedMarker);
                  this.saveMarkers.accept(this.markers);
               }).open();
            }

            this.lastClickTime = now;
         } else if (click.button == 1) {
            this.selectedMarker = null;
            if (this.replayHandler != null) {
               CameraEntity cameraEntity = this.replayHandler.getCameraEntity();
               if (cameraEntity != null) {
                  cameraEntity.setCameraPosRot(new Location(marker.getX(), marker.getY(), marker.getZ(), marker.getYaw(), marker.getPitch()));
               }

               this.replayHandler.doJump(marker.getTime(), true);
            } else {
               this.setCursorPosition(marker.getTime());
            }
         }

         return true;
      } else {
         this.selectedMarker = null;
         return super.mouseClick(click);
      }
   }

   @Override
   public boolean mouseDrag(Click click) {
      if (this.selectedMarker != null) {
         int diff = click.y - this.draggingStartX;
         if (Math.abs(diff) > 5) {
            this.dragging = true;
         }

         if (this.dragging) {
            int timeAt = this.getTimeAt(click.x, click.y);
            if (timeAt != -1) {
               this.selectedMarker.setTime(this.draggingTimeDelta + timeAt);
            }

            return true;
         }
      }

      return false;
   }

   @Override
   public boolean mouseRelease(Click click) {
      if (this.selectedMarker != null) {
         this.mouseDrag(click);
         if (this.dragging) {
            this.dragging = false;
            this.saveMarkers.accept(this.markers);
            return true;
         }
      }

      return false;
   }

   @Override
   protected String getTooltipText(RenderInfo renderInfo) {
      Marker marker = this.getMarkerAt(renderInfo.mouseX, renderInfo.mouseY);
      if (marker != null) {
         return marker.getName() != null ? marker.getName() : I18n.format("replaymod.gui.ingame.unnamedmarker", new Object[0]);
      } else {
         return super.getTooltipText(renderInfo);
      }
   }

   public void setSelectedMarker(Marker selectedMarker) {
      this.selectedMarker = selectedMarker;
   }

   public Marker getSelectedMarker() {
      return this.selectedMarker;
   }

   @Override
   public boolean handleKey(KeyInput keyInput) {
      if (keyInput.key == 211 && this.selectedMarker != null) {
         this.markers.remove(this.selectedMarker);
         this.saveMarkers.accept(this.markers);
         return true;
      } else {
         return false;
      }
   }

   public void addMarker(Marker marker) {
      this.markers.add(marker);
      this.saveMarkers.accept(this.markers);
   }

   @Override
   public ReadableDimension getLastSize() {
      return super.getLastSize();
   }
}
