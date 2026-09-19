package com.replaymod.lib.de.johni0702.minecraft.gui;

import com.replaymod.lib.de.johni0702.minecraft.gui.versions.MCVer;
import java.util.Objects;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;

public class RenderInfo {
   public final float partialTick;
   public final int mouseX;
   public final int mouseY;
   public final int layer;

   public RenderInfo(float partialTick, int mouseX, int mouseY, int layer) {
      this.partialTick = partialTick;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.layer = layer;
   }

   public RenderInfo offsetMouse(int minusX, int minusY) {
      return new RenderInfo(this.partialTick, this.mouseX - minusX, this.mouseY - minusY, this.layer);
   }

   public RenderInfo layer(int layer) {
      return this.layer == layer ? this : new RenderInfo(this.partialTick, this.mouseX, this.mouseY, layer);
   }

   public void addTo(CrashReport crashReport) {
      CrashReportCategory category = crashReport.makeCategory("Render info details");
      MCVer.addDetail(category, "Partial Tick", () -> "" + this.partialTick);
      MCVer.addDetail(category, "Mouse X", () -> "" + this.mouseX);
      MCVer.addDetail(category, "Mouse Y", () -> "" + this.mouseY);
      MCVer.addDetail(category, "Layer", () -> "" + this.layer);
   }

   public float getPartialTick() {
      return this.partialTick;
   }

   public int getMouseX() {
      return this.mouseX;
   }

   public int getMouseY() {
      return this.mouseY;
   }

   public int getLayer() {
      return this.layer;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         RenderInfo that = (RenderInfo)o;
         return Float.compare(that.partialTick, this.partialTick) == 0 && this.mouseX == that.mouseX && this.mouseY == that.mouseY && this.layer == that.layer;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.partialTick, this.mouseX, this.mouseY, this.layer);
   }

   @Override
   public String toString() {
      return "RenderInfo{partialTick=" + this.partialTick + ", mouseX=" + this.mouseX + ", mouseY=" + this.mouseY + ", layer=" + this.layer + '}';
   }
}
